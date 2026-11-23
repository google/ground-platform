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
import openforis.collectone.v1.forms.FormDef
import org.openforis.collectone.v2.app.domain.model.CachedProfile
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.InvitationStatus
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.SurveyConfig
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.Collaborator
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityDatasetValidator
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.GenerationRecord
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SamplePlotProperties
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDerivation
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorProjection
import org.openforis.collectone.v2.core.forms.model.FormDefinition
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

/** A survey's [SurveyConfig] and Survey editor draft survive a trip through `SurveyDef`. */
class ProtoSurveyEditorRoundTripTest {
  private val plotVisit = FormDefinition(FormDef(form_id = "plot-visit", title = "Plot visit"))

  private val forms =
    listOf(
      FormPreviewItem(
        id = "plot-visit",
        title = "Plot visit",
        description = "Record a visit to a monitoring plot",
        version = "4",
        targetDatasetId = "monitoring_plots",
        targetDatasetName = "Monitoring plots",
        questionCount = 0,
        ctaLabel = "Visit plot",
      )
    )

  private val mapLayers =
    listOf(
      MapLayerItem(
        id = "layer-plots",
        label = "Monitoring plots",
        colorHex = 0xFF1565C0L,
        geometryType = GeometryType.POLYGON,
        isVisible = true,
        singularNoun = "plot",
        pluralNoun = "plots",
        datasetId = "monitoring_plots",
      )
    )

  private val survey =
    SurveyPreviewItem(
      id = "survey-1",
      title = "Shade tree monitoring",
      description = "Yearly shade tree census",
      location = "Nyeri, Kenya",
      isDownloaded = false,
      entityCount = 0,
      ownerEmail = "owner@example.org",
    )

  private val config =
    SurveyConfig(
      primaryForm = plotVisit,
      formsById = mapOf("plot-visit" to plotVisit),
      purposeIds = listOf("eudr"),
      programIds = listOf("program-1"),
      collaborators =
        listOf(
          Collaborator(
            email = "collector@example.org",
            role = CollaboratorRole.DATA_COLLECTOR,
            status = InvitationStatus.ACCEPTED,
            userId = "user-2",
            profile = CachedProfile(displayName = "Amina Otieno", cachedOn = "2026-04-01"),
          )
        ),
      ownerProfile = CachedProfile(displayName = "Owner", photoUrl = "avatar:3"),
    )

  @Test
  fun surveyConfig_roundTrips() {
    val (surveyDef, acl) =
      ProtoSurveyMapper.surveyToProto(
        survey = survey,
        forms = forms,
        mapLayers = mapLayers,
        config = config,
        editorDraft = null,
      )

    assertEquals(config, ProtoSurveyMapper.surveyConfigFromProto(surveyDef, acl))
  }

  @Test
  fun surveyEditorDraft_isUnchangedByAnotherTripThroughTheProto() {
    val (surveyDef, acl) =
      ProtoSurveyMapper.surveyToProto(
        survey = survey,
        forms = forms,
        mapLayers = mapLayers,
        config = config,
        editorDraft = null,
      )
    val draft = ProtoSurveyMapper.surveyEditorDraftFromWorkingCopyProto(surveyDef, acl, emptyList())

    val (savedDef, savedAcl) =
      ProtoSurveyMapper.surveyToProto(
        survey = survey,
        forms = forms,
        mapLayers = mapLayers,
        config = config,
        editorDraft = draft,
      )

    assertEquals(
      draft,
      ProtoSurveyMapper.surveyEditorDraftFromWorkingCopyProto(savedDef, savedAcl, emptyList()),
    )
  }

  @Test
  fun generatedSamplePlotsDataset_roundTripsWithPlotIdSchemaAndZeroValidationIssues() {
    val surveyId = "00000099-56d7-5c1d-9457-7457267120a3"
    val samplePlots =
      EntityDataset(
        key = "d1",
        kind = DatasetKind.MAP_LAYER,
        id = "sample_plots",
        displayName = "Sample plots",
        geometryType = GeometryType.POLYGON,
        keyProperty = SamplePlotProperties.PLOT_ID,
        labelProperty = SamplePlotProperties.PLOT_ID,
        properties = SamplePlotProperties.schema,
        rows =
          listOf(
            EntityRow(
              key = "d1-P000001",
              values =
                mapOf(
                  SamplePlotProperties.PLOT_ID to "P000001",
                  SamplePlotProperties.STRATUM to "Forest",
                  SamplePlotProperties.SAMPLE_ORDER to "1",
                  SamplePlotProperties.INCLUSION_WEIGHT to "25",
                ),
              geometry =
                listOf(
                  LatLng(-1.290, 36.820),
                  LatLng(-1.290, 36.821),
                  LatLng(-1.291, 36.821),
                  LatLng(-1.291, 36.820),
                ),
            )
          ),
        generator =
          SampleDesignConfig(
            gridSpacingM = 500.0,
            lastRun = GenerationRecord("2026-04-01T00:00:00Z", "hash-1", 1),
          ),
      )
    val initialDraft =
      SurveyEditorDraft.blank(surveyId, title = survey.title).copy(datasets = listOf(samplePlots))
    val projected =
      SurveyEditorProjection.project(
        draft = initialDraft,
        previous = SurveyEditorDraft.blank(surveyId),
        surveyId = surveyId,
        forms = emptyList(),
        mapLayers = emptyList(),
        entities = emptyList(),
      )
    val (surveyDef, acl) =
      ProtoSurveyMapper.surveyToProto(
        survey = survey.copy(id = surveyId),
        forms = projected.forms,
        mapLayers = projected.mapLayers,
        config = SurveyConfig(),
        editorDraft = initialDraft,
      )

    val restoredDraft =
      ProtoSurveyMapper.surveyEditorDraftFromProto(
        surveyDef = surveyDef,
        acl = acl,
        surveyPreview = survey.copy(id = surveyId),
        forms = projected.forms,
        config = SurveyConfig(),
        mapLayers = projected.mapLayers,
        entities = projected.entities,
      )
    val refreshedDraft =
      SurveyEditorDerivation.refresh(
        draft = restoredDraft,
        surveyId = surveyId,
        mapLayers = projected.mapLayers,
        entities = projected.entities,
      )
    val restoredPlots = refreshedDraft.datasets.single()
    assertEquals(SamplePlotProperties.PLOT_ID, restoredPlots.keyProperty)
    assertEquals(SamplePlotProperties.PLOT_ID, restoredPlots.labelProperty)
    assertEquals(SamplePlotProperties.schema, restoredPlots.properties)
    assertEquals(samplePlots.generator, restoredPlots.generator)
    assertEquals(
      listOf("P000001"),
      restoredPlots.rows.map { it.values[SamplePlotProperties.PLOT_ID] },
    )
    assertEquals(emptyList(), EntityDatasetValidator.validate(restoredPlots, emptyList()))
  }
}
