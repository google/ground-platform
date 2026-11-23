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
package org.openforis.collectone.v2.app.data.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import openforis.collectone.v1.survey.EntityDatasetDef
import openforis.collectone.v1.survey.ImportProvenance
import openforis.collectone.v1.survey.SamplingDesign
import openforis.collectone.v1.survey.SurveyDef
import org.openforis.collectone.v2.app.domain.model.EffortComparison
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.MapThumbnailTheme
import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.QuotaTier
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcome
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyThumbnailGenerator
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.core.sampling.PlotLayout
import org.openforis.collectone.v2.core.sampling.PlotShape
import org.openforis.collectone.v2.core.sampling.SampleDesign
import org.openforis.collectone.v2.core.sampling.SamplingDesignProtos
import org.openforis.collectone.v2.core.sampling.SubPlotLayout
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

class ProtoSurveyMapperRoundTripTest {
  private val mapLayers =
    listOf(
      MapLayerItem(
        id = "layer-plots",
        label = "Monitoring plots",
        colorHex = 0xFF1565C0L,
        geometryType = GeometryType.POLYGON,
        isVisible = false,
        singularNoun = "plot",
        pluralNoun = "plots",
        datasetId = "monitoring_plots",
        iconName = "park",
      ),
      // A layer draws either a dataset or a Form's geometry answers, never both.
      MapLayerItem(
        id = "layer-boundaries",
        label = "Plot boundaries",
        colorHex = 0xFFF9A825L,
        geometryType = GeometryType.POLYGON,
        isVisible = true,
        formId = "plot-boundary",
        formTitle = "Plot boundary",
        fieldPath = "/data/boundary",
      ),
    )

  private val forms =
    listOf(
      FormPreviewItem(
        id = "plot-visit",
        title = "Plot visit",
        description = "Record a visit to a monitoring plot",
        version = "4",
        targetDatasetId = "monitoring_plots",
        targetDatasetName = "Monitoring plots",
        questionCount = 12,
        ctaLabel = "Visit plot",
        requiresEntity = true,
        availability = FormAvailability.MOBILE,
        // Derived on read from the target dataset of a Form that requires a map feature.
        hasEntitySelectQuestion = true,
      )
    )

  private val surveyArea =
    SurveyArea(
      name = "Othaya",
      parts =
        listOf(
          listOf(
            LatLng(-0.396, 36.922),
            LatLng(-0.396, 36.978),
            LatLng(-0.442, 36.978),
            LatLng(-0.442, 36.922),
          )
        ),
    )

  private val survey =
    SurveyPreviewItem(
      id = "survey-1",
      title = "Shade tree monitoring",
      description = "Yearly shade tree census",
      location = "Nyeri, Kenya",
      isDownloaded = true,
      thumbnailTheme = MapThumbnailTheme.HIGHLAND_AGRI,
      entityCount = 3,
      surveyArea = surveyArea,
      // Thumbnails are derived on read from the survey's area, layers, and entities.
      thumbnail =
        SurveyThumbnailGenerator.fromRuntime(
          surveyId = "survey-1",
          surveyArea = surveyArea,
          mapLayers = mapLayers,
          entities = emptyList(),
        ),
      ownerEmail = "owner@example.org",
      organizationId = "org-1",
      state = SurveyLifecycleState.CLOSED,
      closedAt = "2026-05-04T10:20:30Z",
      outcome =
        SurveyOutcome(
          outcomes = setOf(SurveyOutcomeKind.SHARED_WITH_BUYERS, SurveyOutcomeKind.LAND_TITLING),
          effortComparison = EffortComparison.LESS,
          answeredAt = "2026-05-05T08:00:00Z",
          answeredBy = "owner@example.org",
        ),
      sharingPolicy = SharingPolicy.ORGANIZATION,
      peerDataVisibility = PeerDataVisibility.OWN_ONLY,
      collectorConsentRequired = true,
      collectorConsentText = "I agree to share my data.",
      quotaLimits = QuotaLimitsConfig(tier = QuotaTier.BASIC, maxFormsPerSurvey = 5),
      defaultRole = CollaboratorRole.VIEWER,
    )

  private fun surveyToProto() =
    ProtoSurveyMapper.surveyToProto(
      survey = survey,
      forms = forms,
      mapLayers = mapLayers,
      config = null,
      editorDraft = null,
    )

  @Test
  fun surveyPreview_roundTrips() {
    val (surveyDef, acl) = surveyToProto()

    assertEquals(
      survey,
      ProtoSurveyMapper.surveyPreviewFromProto(surveyDef, acl, mapLayers = mapLayers),
    )
  }

  @Test
  fun editorDraft_decidesTheSurveyArea_evenWhenItHasNone() {
    val draft = SurveyEditorDraft.blank(survey.id)

    val (surveyDef, _) =
      ProtoSurveyMapper.surveyToProto(
        survey = survey,
        forms = forms,
        mapLayers = mapLayers,
        config = null,
        editorDraft = draft,
      )

    assertEquals(null, surveyDef.map_config?.survey_area)
  }

  @Test
  fun forms_roundTrip() {
    val (surveyDef, _) = surveyToProto()

    assertEquals(forms, ProtoSurveyMapper.formsFromProto(surveyDef))
  }

  @Test
  fun mapLayers_roundTrip() {
    val (surveyDef, _) = surveyToProto()

    assertEquals(mapLayers, ProtoSurveyMapper.mapLayersFromProto(surveyDef))
  }

  @Test
  fun mapLayerWithADatasetAndAFormGeometry_keepsTheDataset() {
    // The map only draws map features (entities), so a layer's dataset is its source; the Form
    // geometry it was drawn from is dropped (`LayerDef.source` holds one or the other).
    val layer = mapLayers[1].copy(id = "layer-both", datasetId = "plot_boundaries")
    val (surveyDef, _) =
      ProtoSurveyMapper.surveyToProto(
        survey = survey,
        forms = forms,
        mapLayers = listOf(layer),
        config = null,
        editorDraft = null,
      )

    assertEquals(
      listOf(layer.copy(formId = null, fieldPath = null)),
      ProtoSurveyMapper.mapLayersFromProto(surveyDef),
    )
  }

  @Test
  fun generatedDatasetIds_areTheSampledDatasets() {
    val surveyDef =
      SurveyDef(
        entity_datasets =
          listOf(
            EntityDatasetDef(id = "sample_plots", sampling = SamplingDesign()),
            EntityDatasetDef(id = "imported_parcels", imported = ImportProvenance()),
            EntityDatasetDef(id = "hand_drawn"),
          )
      )

    assertEquals(setOf("sample_plots"), ProtoSurveyMapper.generatedDatasetIds(surveyDef))
  }

  @Test
  fun mapLayersFromProto_attachesDatasetSampleDesign() {
    val design =
      SampleDesign(
        layout = PlotLayout.SystematicGrid(spacingM = 500.0),
        plotShape = PlotShape.SQUARE,
        plotSizeM = 30.0,
        subPlot = SubPlotLayout.Grid(n = 3, spacingM = 10.0),
        seed = 7L,
      )
    val layer =
      MapLayerItem(
        id = "layer-sample-plots",
        label = "Sample plots",
        colorHex = 0xFFF9A825L,
        geometryType = GeometryType.POLYGON,
        isVisible = true,
        datasetId = "sample_plots",
      )
    val (baseDef, _) =
      ProtoSurveyMapper.surveyToProto(
        survey = survey,
        forms = forms,
        mapLayers = listOf(layer),
        config = null,
        editorDraft = null,
      )
    val surveyDef =
      baseDef.copy(
        entity_datasets =
          listOf(
            EntityDatasetDef(
              id = "sample_plots",
              sampling = SamplingDesignProtos.toProto(design),
            )
          )
      )

    val restored = ProtoSurveyMapper.mapLayersFromProto(surveyDef)
    assertEquals(listOf(layer.copy(sampleDesign = design)), restored)
  }

  @Test
  fun surveyOverview_populatesThumbnailFeaturesAndReconstructsCatalogPreviewBeforeDownload() {
    val visibleLayer =
      MapLayerItem(
        id = "layer-mangrove-plots",
        label = "Mangrove plots",
        colorHex = 0xFF00897BL,
        geometryType = GeometryType.POLYGON,
        isVisible = true,
        datasetId = "mangrove_plots",
      )
    val entities =
      (0 until 160).map { index ->
        val baseLat = -0.4190 + (index % 10) * 0.001
        val baseLng = 36.9500 + (index / 10) * 0.001
        GeospatialEntityItem(
          id = "plot-$index",
          label = "Plot $index",
          datasetId = "mangrove_plots",
          datasetName = "Mangrove plots",
          layerId = "layer-mangrove-plots",
          geoId = "G-$index",
          geometry =
            Geometry.Polygon(
              listOf(
                listOf(
                  LatLng(baseLat, baseLng),
                  LatLng(baseLat, baseLng + 0.0005),
                  LatLng(baseLat - 0.0005, baseLng + 0.0005),
                  LatLng(baseLat - 0.0005, baseLng),
                )
              )
            ),
          areaHectares = 0.25,
          perimeterMeters = 200,
          colorHex = 0xFF2E7D32L,
          properties = emptyMap(),
          submissions = emptyList(),
        )
      }
    val (surveyDef, acl) =
      ProtoSurveyMapper.surveyToProto(
        survey = survey,
        forms = forms,
        mapLayers = listOf(visibleLayer),
        config = null,
        editorDraft = null,
      )

    val overview =
      ProtoSurveyMapper.surveyOverviewFromDef(
        surveyDef = surveyDef,
        acl = acl,
        mapLayers = listOf(visibleLayer),
        entities = entities,
      )
    assertEquals(
      SurveyThumbnailGenerator.MAX_THUMBNAIL_FEATURES,
      overview.thumbnail_features.size,
    )
    assertTrue(overview.thumbnail_features.all { it.color_hex == 0xFF00897BL })

    // Non-downloaded catalog preview builds map features directly from overview.thumbnail_features.
    val catalogPreview = ProtoSurveyMapper.surveyPreviewFromOverviewProto(overview)
    val downloadedPreview =
      ProtoSurveyMapper.surveyPreviewFromProto(
        surveyDef = surveyDef,
        acl = acl,
        mapLayers = listOf(visibleLayer),
        entities = entities,
      )
    assertEquals(downloadedPreview.thumbnail.features, catalogPreview.thumbnail.features)
    assertEquals(downloadedPreview.thumbnail.bounds, catalogPreview.thumbnail.bounds)
    assertTrue(
      catalogPreview.thumbnail.staticImageUrl("pk.test").contains("/geojson("),
      "Expected catalog preview staticImageUrl to render map features",
    )
  }
}
