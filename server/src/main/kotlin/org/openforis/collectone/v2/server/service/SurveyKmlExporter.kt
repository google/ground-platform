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
package org.openforis.collectone.v2.server.service

import io.grpc.Status
import io.grpc.StatusException
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.survey.DataTableRow
import openforis.collectone.v1.survey.EntityDatasetDef
import openforis.collectone.v1.survey.EntityType
import openforis.collectone.v1.survey.FormLaunchConfig
import openforis.collectone.v1.survey.SurveyDef
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.geo.io.GeoGeometry
import org.openforis.collectone.v2.core.geo.io.GeoJsonReader
import org.openforis.collectone.v2.core.geo.io.KmlBalloonWriter
import org.openforis.collectone.v2.core.sampling.SamplingDesignProtos
import org.openforis.collectone.v2.server.data.UserDataStore

/** Rendered KML document for a survey, served by `GET /kml/surveys/{surveyId}.kml`. */
data class ExportedSurveyKml(
  val fileName: String,
  val content: String,
)

/**
 * Builds a multi-layer OGC KML 2.2 document (one `<Folder>` per map layer) from a stored survey's
 * `SurveyDef` and live `EntityRecord`s. KML exports contain only map geometries and form schemas
 * (never user credentials); write access is authenticated when a collector submits a form inside
 * the balloon.
 */
internal class SurveyKmlExporter(private val store: UserDataStore) {

  suspend fun export(
    surveyId: String,
    balloonAppUrl: String,
    serviceUrl: String,
    forNetworkLink: Boolean = false,
  ): ExportedSurveyKml = store.read { r ->
    val surveyDef =
      r.survey(surveyId)
        ?: throw StatusException(Status.NOT_FOUND.withDescription("Survey $surveyId not found"))
    r.checkNotTrashed(surveyId)

    val visibleEntities = r.entities(surveyId, includeDeleted = false).sortedBy { it.entity_id }
    val workingDef = r.workingCopy(surveyId)

    val layers = buildLayerFolders(surveyDef, workingDef, visibleEntities)
    val surveyTitle = surveyDef.title.ifBlank { "Collect One Survey" }
    val fallbackLookAt =
      surveyDef.map_config?.survey_area?.center ?: surveyDef.map_config?.initial_center

    val content =
      KmlBalloonWriter.writeDocument(
        surveyId = surveyId,
        surveyTitle = surveyTitle,
        documentName = if (forNetworkLink) "Map Layers" else surveyTitle,
        documentDescription = if (forNetworkLink) "" else surveyDef.description,
        layers = layers,
        session =
          KmlBalloonWriter.SessionConfig(
            balloonAppUrl = balloonAppUrl,
            serviceUrl = serviceUrl,
          ),
        fallbackLookAt = fallbackLookAt,
        flattenSingleLayer = forNetworkLink,
      )

    ExportedSurveyKml(
      fileName = safeFileName(surveyTitle),
      content = content,
    )
  }

  suspend fun exportNetworkLink(
    surveyId: String,
    targetKmlUrl: String,
    refreshIntervalSeconds: Int = KmlBalloonWriter.DEFAULT_REFRESH_INTERVAL_SECONDS,
  ): ExportedSurveyKml = store.read { r ->
    val surveyDef =
      r.survey(surveyId)
        ?: throw StatusException(Status.NOT_FOUND.withDescription("Survey $surveyId not found"))
    r.checkNotTrashed(surveyId)

    val visibleEntities = r.entities(surveyId, includeDeleted = false).sortedBy { it.entity_id }
    val surveyTitle = surveyDef.title.ifBlank { "Collect One Survey" }
    val firstEntityPosition = visibleEntities.firstNotNullOfOrNull { entity ->
      GeometryProtos.of(entity)?.let(GeometryProtos::representativePosition)
    }
    val lookAt =
      firstEntityPosition
        ?: surveyDef.map_config?.survey_area?.center
        ?: surveyDef.map_config?.initial_center

    val content =
      KmlBalloonWriter.writeNetworkLinkDocument(
        surveyId = surveyId,
        documentName = surveyTitle,
        documentDescription = surveyDef.description,
        targetKmlUrl = targetKmlUrl,
        refreshIntervalSeconds = refreshIntervalSeconds,
        networkLinkName = "Live Layers",
        lookAt = lookAt,
      )

    ExportedSurveyKml(
      fileName = safeFileName(surveyTitle),
      content = content,
    )
  }

  private fun buildLayerFolders(
    surveyDef: SurveyDef,
    workingDef: SurveyDef?,
    entities: List<EntityRecord>,
  ): List<KmlBalloonWriter.LayerFolderSpec> {
    val extraDatasetsById =
      workingDef?.entity_datasets.orEmpty().associateBy { it.id } +
        surveyDef.entity_datasets.associateBy { it.id }
    val formsById = resolveFormsById(surveyDef)
    val claimedEntityIds = mutableSetOf<String>()
    val claimedDatasetIds = mutableSetOf<String>()

    val configuredFolders =
      buildConfiguredFolders(
        surveyDef = surveyDef,
        entities = entities,
        extraDatasetsById = extraDatasetsById,
        formsById = formsById,
        claimedEntityIds = claimedEntityIds,
        claimedDatasetIds = claimedDatasetIds,
      )
    val fallbackFolders =
      buildFallbackFolders(
        surveyDef = surveyDef,
        entities = entities,
        extraDatasetsById = extraDatasetsById,
        formsById = formsById,
        claimedEntityIds = claimedEntityIds,
        claimedDatasetIds = claimedDatasetIds,
      )
    val inlineDatasetFolders =
      buildInlineDatasetFolders(
        surveyDef = surveyDef,
        extraDatasetsById = extraDatasetsById,
        formsById = formsById,
        claimedDatasetIds = claimedDatasetIds,
      )

    return configuredFolders + fallbackFolders + inlineDatasetFolders
  }

  private fun buildConfiguredFolders(
    surveyDef: SurveyDef,
    entities: List<EntityRecord>,
    extraDatasetsById: Map<String, EntityDatasetDef>,
    formsById: Map<String, FormDef>,
    claimedEntityIds: MutableSet<String>,
    claimedDatasetIds: MutableSet<String>,
  ): List<KmlBalloonWriter.LayerFolderSpec> =
    surveyDef.map_config?.layers.orEmpty().map { layer ->
      val datasetId = layer.entity_dataset_id?.takeIf { it.isNotBlank() } ?: layer.id
      claimedDatasetIds += datasetId
      val extraDs = extraDatasetsById[datasetId]
      val layerEntities = entities.filter { entity ->
        entity.entity_id !in claimedEntityIds &&
          (entity.layer_id == layer.id ||
            (entity.dataset_id.isNotBlank() && entity.dataset_id == datasetId))
      }
      layerEntities.forEach { claimedEntityIds += it.entity_id }
      val fallbackColor =
        layerEntities.firstOrNull()?.color_hex?.takeIf { it != 0L }
          ?: KmlBalloonWriter.parseCssHexColor(
            extraDs?.default_style?.color,
            KmlBalloonWriter.DEFAULT_LAYER_COLOR_HEX,
          )
      val colorHex = KmlBalloonWriter.parseCssHexColor(layer.default_style?.color, fallbackColor)
      val placemarks =
        if (layerEntities.isNotEmpty()) {
          layerEntities.map(::toPlacemarkSpec)
        } else {
          extraDs?.let(::rowsToPlacemarkSpecs).orEmpty()
        }
      KmlBalloonWriter.LayerFolderSpec(
        layerId = layer.id.ifBlank { datasetId },
        datasetId = datasetId,
        name = layer.label.ifBlank { extraDs?.display_name?.ifBlank { null } ?: datasetId },
        description = extraDs?.description.orEmpty(),
        colorHex = colorHex,
        isVisible = layer.visible_by_default,
        forms = embeddedFormsFor(surveyDef, formsById, datasetId),
        placemarks = placemarks,
        sampleDesign = extraDs?.sampling?.let(SamplingDesignProtos::fromProto),
      )
    }

  private fun buildFallbackFolders(
    surveyDef: SurveyDef,
    entities: List<EntityRecord>,
    extraDatasetsById: Map<String, EntityDatasetDef>,
    formsById: Map<String, FormDef>,
    claimedEntityIds: Set<String>,
    claimedDatasetIds: MutableSet<String>,
  ): List<KmlBalloonWriter.LayerFolderSpec> =
    entities
      .filter { it.entity_id !in claimedEntityIds }
      .groupBy { it.dataset_id.ifBlank { "features" } }
      .map { (datasetId, group) ->
        claimedDatasetIds += datasetId
        val first = group.first()
        val extraDs = extraDatasetsById[datasetId]
        val name =
          first.dataset_name.ifBlank {
            extraDs?.display_name?.ifBlank { null } ?: datasetId.replace('_', ' ')
          }
        val color =
          first.color_hex.takeIf { it != 0L }
            ?: KmlBalloonWriter.parseCssHexColor(extraDs?.default_style?.color)
        KmlBalloonWriter.LayerFolderSpec(
          layerId = first.layer_id.ifBlank { "layer-$datasetId" },
          datasetId = datasetId,
          name = name,
          description = extraDs?.description.orEmpty(),
          colorHex = color,
          isVisible = true,
          forms = embeddedFormsFor(surveyDef, formsById, datasetId),
          placemarks = group.map(::toPlacemarkSpec),
          sampleDesign = extraDs?.sampling?.let(SamplingDesignProtos::fromProto),
        )
      }

  private fun buildInlineDatasetFolders(
    surveyDef: SurveyDef,
    extraDatasetsById: Map<String, EntityDatasetDef>,
    formsById: Map<String, FormDef>,
    claimedDatasetIds: Set<String>,
  ): List<KmlBalloonWriter.LayerFolderSpec> =
    extraDatasetsById.values
      .filter { ds ->
        ds.id !in claimedDatasetIds && ds.type == EntityType.GEOSPATIAL && ds.rows.isNotEmpty()
      }
      .map { ds ->
        KmlBalloonWriter.LayerFolderSpec(
          layerId = ds.editor_key.ifBlank { "layer-${ds.id}" },
          datasetId = ds.id,
          name = ds.display_name.ifBlank { ds.id.replace('_', ' ') },
          description = ds.description,
          colorHex = KmlBalloonWriter.parseCssHexColor(ds.default_style?.color),
          isVisible = ds.visible_by_default || ds.default_style == null,
          forms = embeddedFormsFor(surveyDef, formsById, ds.id),
          placemarks = rowsToPlacemarkSpecs(ds),
          sampleDesign = ds.sampling?.let(SamplingDesignProtos::fromProto),
        )
      }

  private fun resolveFormsById(surveyDef: SurveyDef): Map<String, FormDef> {
    val map = LinkedHashMap<String, FormDef>()
    surveyDef.forms.forEach { form ->
      if (form.form_id == PRIMARY_FORM_PROTO_ID) {
        val realId = surveyDef.primary_form_id.ifBlank { PRIMARY_FORM_PROTO_ID }
        map[realId] = form.copy(form_id = realId)
      } else if (form.form_id.isNotBlank()) {
        map[form.form_id] = form
      }
    }
    return map
  }

  private fun embeddedFormsFor(
    surveyDef: SurveyDef,
    formsById: Map<String, FormDef>,
    datasetId: String,
  ): List<KmlBalloonWriter.EmbeddedForm> {
    val configs = surveyDef.form_launch_configs
    if (configs.isEmpty()) {
      return formsById.values.map { form ->
        KmlBalloonWriter.EmbeddedForm(
          id = form.form_id,
          title = form.title.ifBlank { form.form_id },
          version = form.version.ifBlank { surveyDef.version.ifBlank { "1" } },
          formDef = form,
        )
      }
    }
    val unmigratedSeed =
      surveyDef.record_version <= 5L &&
        surveyDef.survey_id in LEGACY_SEED_SURVEY_IDS &&
        configs.none { it.open_in_google_earth }
    val chosen = configs.filter { lc ->
      lc.includesWeb() &&
        lc.requires_entity &&
        lc.target_entity_dataset_id == datasetId &&
        (lc.open_in_google_earth || unmigratedSeed)
    }
    return chosen.mapNotNull { lc ->
      val formDef =
        formsById[lc.form_id] ?: formsById.values.firstOrNull() ?: return@mapNotNull null
      KmlBalloonWriter.EmbeddedForm(
        id = lc.form_id,
        title = lc.title.ifBlank { formDef.title.ifBlank { lc.form_id } },
        version = lc.version.ifBlank { formDef.version.ifBlank { "1" } },
        formDef = formDef,
      )
    }
  }

  private fun FormLaunchConfig.includesWeb(): Boolean = availability?.web ?: true

  private fun toPlacemarkSpec(entity: EntityRecord): KmlBalloonWriter.PlacemarkSpec {
    val props = entity.properties.mapValues { (_, v) -> v.string_value.orEmpty() }
    val displayTitle = entity.label.ifBlank { entity.geo_id.ifBlank { entity.entity_id } }
    return KmlBalloonWriter.PlacemarkSpec(
      id = entity.entity_id,
      title = displayTitle,
      geoId = entity.geo_id,
      status = props["status"].orEmpty(),
      markerSymbol = props["marker-symbol"].orEmpty(),
      geometry = GeometryProtos.of(entity),
      properties = props,
    )
  }

  private fun rowsToPlacemarkSpecs(
    dataset: EntityDatasetDef
  ): List<KmlBalloonWriter.PlacemarkSpec> =
    if (dataset.type != EntityType.GEOSPATIAL) {
      emptyList()
    } else {
      dataset.rows.mapIndexedNotNull { index, row -> rowToPlacemarkSpec(dataset, index, row) }
    }

  private fun rowToPlacemarkSpec(
    dataset: EntityDatasetDef,
    index: Int,
    row: DataTableRow,
  ): KmlBalloonWriter.PlacemarkSpec? {
    val geometry = rowGeometry(row) ?: return null
    val keyValue =
      dataset.key_property
        .takeIf { it.isNotBlank() }
        ?.let { row.values[it]?.takeIf(String::isNotBlank) }
    val labelValue =
      dataset.label_property
        .takeIf { it.isNotBlank() }
        ?.let { row.values[it]?.takeIf(String::isNotBlank) }
    val entityId = row.row_key.ifBlank { keyValue ?: "${dataset.id}_${index + 1}" }
    val geoId = keyValue ?: entityId
    val displayTitle = labelValue ?: geoId
    return KmlBalloonWriter.PlacemarkSpec(
      id = entityId,
      title = displayTitle,
      geoId = geoId,
      status = row.values["status"].orEmpty(),
      markerSymbol = row.values["marker-symbol"].orEmpty(),
      geometry = geometry,
      properties = row.values,
    )
  }

  private fun rowGeometry(row: DataTableRow): Geometry? =
    row.geometry?.takeIf { GeometryProtos.typeOf(it) != null }
      ?: row.values["geometry"]
        ?.takeIf { it.isNotBlank() }
        ?.let { GeoJsonReader.read(it).features.firstOrNull()?.geometry }
        ?.let(::geoGeometryToProto)

  private fun geoGeometryToProto(parsed: GeoGeometry): Geometry =
    when (parsed) {
      is GeoGeometry.Point ->
        GeometryProtos.point(latitude = parsed.coord.lat, longitude = parsed.coord.lng)
      is GeoGeometry.LineString ->
        GeometryProtos.lineString(parsed.coords.map { GeometryProtos.position(it.lat, it.lng) })
      is GeoGeometry.Polygon ->
        GeometryProtos.polygon(listOf(parsed.ring.map { GeometryProtos.position(it.lat, it.lng) }))
    }

  private fun safeFileName(title: String): String {
    val base =
      title.replace(Regex("""[\\/:*?"<>|\x00-\x1F]"""), "_").trim().trim('.').ifEmpty { "survey" }
    return "$base.kml"
  }

  private companion object {
    const val PRIMARY_FORM_PROTO_ID = "__primary_form__"
    val LEGACY_SEED_SURVEY_IDS =
      setOf(
        "00000032-8d62-5717-8074-f4917e0cd76d",
        "00000033-6b50-51cd-ac94-baea2ea06d65",
      )
  }
}
