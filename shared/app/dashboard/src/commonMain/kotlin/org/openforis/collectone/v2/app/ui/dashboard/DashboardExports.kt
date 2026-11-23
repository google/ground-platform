/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openforis.collectone.v2.app.ui.dashboard

import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.publishedForm
import org.openforis.collectone.v2.app.domain.usecase.ExportFile
import org.openforis.collectone.v2.app.domain.usecase.ExportSurveyDataUseCase
import org.openforis.collectone.v2.app.domain.usecase.KmlBalloonSession
import org.openforis.collectone.v2.app.domain.usecase.KmlEmbeddedForm
import org.openforis.collectone.v2.app.domain.usecase.KmlLayerExport
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.app.domain.usecase.ResolvedLibrary
import org.openforis.collectone.v2.core.geo.io.GoogleEarthDeepLinks

/**
 * The dashboard's dataset exports, as functions of a [DashboardData] snapshot: which export
 * profiles each dataset of the active survey offers, and the files they produce. Stateless; the
 * [DashboardViewModel] records each export as an event once the file is built.
 *
 * Exports need the active survey's editor draft (its dataset schemas, forms, and purposes), so
 * without one nothing is offered and nothing is exported.
 */
internal class DashboardExports(
  private val exportSurveyData: ExportSurveyDataUseCase,
  private val resolveLibraryUseCase: ResolveLibraryUseCase,
) {
  /**
   * Export choices for each dataset of the active survey, by dataset ID. Profiles come from the
   * active survey's organization library, filtered to the survey's purposes and the templates its
   * forms came from; each dataset lists only the profiles whose fields it can fill.
   */
  fun options(data: DashboardData): Map<String, DatasetExportOptions> {
    val draft = data.draft ?: return emptyMap()
    val library = activeLibrary(data)
    val profiles =
      exportSurveyData.enabledProfiles(
        library,
        draft.details.purposeIds,
        draft.forms.mapNotNull { it.form.templateOrigin?.templateId }.toSet(),
      )
    return draft.datasets.associate { dataset ->
      val hasGeometry = dataset.kind == DatasetKind.MAP_LAYER
      dataset.id to
        DatasetExportOptions(
          datasetId = dataset.id,
          hasGeometry = hasGeometry,
          hasGoogleEarthForms =
            hasGeometry &&
              data.content.forms.any {
                it.isGoogleEarthEnabled && it.targetDatasetId == dataset.id
              },
          profilePlans =
            exportSurveyData.applicablePlans(
              profiles = profiles,
              dataset = dataset,
              forms = draft.forms,
              library = library,
              entities = data.content.entities,
              language = draft.details.defaultLanguage,
            ),
        )
    }
  }

  /**
   * Whether the active survey has at least one map layer targeted by a form with **Open in Google
   * Earth** enabled.
   */
  fun hasGoogleEarthForms(data: DashboardData): Boolean {
    val mapLayerDatasetIds =
      data.content.mapLayers.mapNotNull { it.datasetId?.takeIf(String::isNotBlank) }.toSet() +
        data.draft?.datasets.orEmpty().filter { it.kind == DatasetKind.MAP_LAYER }.map { it.id }
    if (mapLayerDatasetIds.isEmpty()) return false
    return data.content.forms.any {
      it.isGoogleEarthEnabled && it.targetDatasetId in mapLayerDatasetIds
    }
  }

  /** Dataset [datasetId] as GeoJSON, or `null` when it isn't a Map layer (no geometry). */
  fun geoJson(data: DashboardData, datasetId: String): ExportFile? {
    val dataset = data.dataset(datasetId) ?: return null
    if (dataset.kind != DatasetKind.MAP_LAYER) return null
    return exportSurveyData.geoJson(dataset, data.datasetEntities(datasetId))
  }

  /**
   * Dataset [datasetId] as a KML 2.2 document whose `<Folder>` embeds the dataset's web-enabled
   * Collect One forms via [balloonAppUrl], or `null` when it isn't a Map layer.
   */
  fun kml(
    data: DashboardData,
    datasetId: String,
    balloonAppUrl: String,
    serviceUrl: String = "",
  ): ExportFile? {
    val dataset = data.dataset(datasetId) ?: return null
    if (dataset.kind != DatasetKind.MAP_LAYER) return null
    return exportSurveyData.kml(
      surveyId = data.activeSurveyId,
      surveyTitle = activeSurveyTitle(data),
      dataset = dataset,
      entities = data.datasetEntities(datasetId),
      forms = embeddedWebFormsFor(data, dataset),
      session = balloonSession(balloonAppUrl, serviceUrl),
    )
  }

  /**
   * Exports all Map layers of the active survey as a single KML 2.2 document with one `<Folder>`
   * per map layer, or `null` when the survey has no map layers.
   */
  fun surveyKml(
    data: DashboardData,
    balloonAppUrl: String,
    serviceUrl: String = "",
  ): ExportFile? {
    val draft = data.draft ?: return null
    val mapDatasets = draft.datasets.filter { it.kind == DatasetKind.MAP_LAYER }
    if (mapDatasets.isEmpty()) return null
    val layersByDatasetId =
      data.content.mapLayers.mapNotNull { l -> l.datasetId?.let { it to l } }.toMap()
    val layerExports = mapDatasets.map { dataset ->
      KmlLayerExport(
        dataset = dataset,
        entities = data.datasetEntities(dataset.id),
        forms = embeddedWebFormsFor(data, dataset),
        defaultColorHex = layersByDatasetId[dataset.id]?.colorHex ?: 0xFF2E7D32L,
      )
    }
    return exportSurveyData.surveyKml(
      surveyId = data.activeSurveyId,
      surveyTitle = activeSurveyTitle(data),
      surveyDescription = draft.details.description,
      layers = layerExports,
      session = balloonSession(balloonAppUrl, serviceUrl),
      fallbackCenter = draft.details.surveyArea?.center,
    )
  }

  /**
   * Builds a `https://earth.google.com/web/c/<OpenKmlDocument>` URL that opens the active survey's
   * live multi-layer KML endpoint (`GET /kml/surveys/{surveyId}.kml`) in Google Earth Web, or
   * `null` when no survey, backend [serviceUrl], or Google Earth-enabled form is active.
   */
  fun googleEarthOpenUrl(
    data: DashboardData,
    balloonAppUrl: String,
    serviceUrl: String,
  ): String? {
    val surveyId = data.activeSurveyId.trim()
    val baseServiceUrl = serviceUrl.trim().trimEnd('/')
    if (surveyId.isEmpty() || baseServiceUrl.isEmpty() || !hasGoogleEarthForms(data)) return null

    val encodedSurveyId = GoogleEarthDeepLinks.encodeUriComponent(surveyId)
    val queryString =
      if (balloonAppUrl.isBlank()) {
        "?mode=networklink"
      } else {
        "?mode=networklink&balloonUrl=${GoogleEarthDeepLinks.encodeUriComponent(balloonAppUrl.trim())}"
      }
    val kmlUrl = "$baseServiceUrl/kml/surveys/$encodedSurveyId.kml$queryString"
    return GoogleEarthDeepLinks.openKmlUrl(kmlUrl)
  }

  private fun activeSurveyTitle(data: DashboardData): String =
    data.surveys.firstOrNull { it.id == data.activeSurveyId }?.title
      ?: data.draft?.details?.title
      ?: ""

  private fun balloonSession(
    balloonAppUrl: String,
    serviceUrl: String,
  ): KmlBalloonSession =
    KmlBalloonSession(
      balloonAppUrl = balloonAppUrl,
      serviceUrl = serviceUrl,
    )

  private fun embeddedWebFormsFor(
    data: DashboardData,
    dataset: EntityDataset,
  ): List<KmlEmbeddedForm> {
    val chosenForms =
      data.content.forms.filter { it.isGoogleEarthEnabled && it.targetDatasetId == dataset.id }

    val draft = data.draft
    val configForms = data.content.config?.formsById.orEmpty()
    return chosenForms.mapNotNull { form ->
      val proto =
        configForms[form.id]?.proto
          ?: draft
            ?.forms
            ?.firstOrNull { it.key == form.id || it.form.formId == form.id }
            ?.let { draft.publishedForm(it).proto }
          ?: return@mapNotNull null
      KmlEmbeddedForm(
        id = form.id,
        title = form.title,
        version = form.version,
        formDef = proto,
      )
    }
  }

  /** Dataset [datasetId] through export profile [profileId], or `null` if it doesn't offer it. */
  fun withProfile(data: DashboardData, datasetId: String, profileId: String): ExportFile? {
    val dataset = data.dataset(datasetId) ?: return null
    val plan =
      options(data)[datasetId]?.profilePlans?.firstOrNull { it.profile.id == profileId }
        ?: return null
    return exportSurveyData.profileGeoJson(plan, dataset, data.datasetEntities(datasetId))
  }

  /** Records an export of [entities] (through [profileId] when made with a profile). */
  suspend fun record(surveyId: String, entities: List<GeospatialEntityItem>, profileId: String?) =
    exportSurveyData.recordExport(surveyId, entities, profileId)

  private fun activeLibrary(data: DashboardData): ResolvedLibrary {
    val organizationId = data.surveys.firstOrNull { it.id == data.activeSurveyId }?.organizationId
    return resolveLibraryUseCase.forOrganization(data.libraries, organizationId)
  }

  private fun DashboardData.dataset(datasetId: String): EntityDataset? =
    draft?.datasets?.firstOrNull { it.id == datasetId }
}
