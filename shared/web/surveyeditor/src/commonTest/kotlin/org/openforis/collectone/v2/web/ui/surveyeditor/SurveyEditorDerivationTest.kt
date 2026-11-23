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
package org.openforis.collectone.v2.web.ui.surveyeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.seed.SampleEntitiesData
import org.openforis.collectone.v2.app.data.seed.SampleMapLayersData
import org.openforis.collectone.v2.app.data.seed.SampleSurveysData
import org.openforis.collectone.v2.app.data.seed.SurveyEditorSamples
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDerivation
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorProjection

class SurveyEditorDerivationTest {
  private fun derive(surveyId: String): SurveyEditorDraft {
    val forms = SampleSurveysData.formsForSurvey(surveyId)
    return SurveyEditorDerivation.derive(
      surveyId = surveyId,
      survey = SampleSurveysData.defaultSampleSurveys().first { it.id == surveyId },
      forms = forms,
      formDefinition = { SampleSurveysData.builtInFallbackFormForForm(it) },
      mapLayers = SampleMapLayersData.mapLayersForSurvey(surveyId),
      entities = SampleEntitiesData.entitiesForSurvey(surveyId),
    )
  }

  @Test
  fun everySampleSurvey_derivesItsFormsAndMapLayers() {
    for (survey in SampleSurveysData.defaultSampleSurveys()) {
      val draft = derive(survey.id)
      val forms = SampleSurveysData.formsForSurvey(survey.id)
      val layers = SampleMapLayersData.mapLayersForSurvey(survey.id)
      assertTrue(draft.forms.isNotEmpty(), "${survey.id} derives at least one Form")
      assertEquals(forms.map { it.id }, draft.forms.map { it.form.formId }, survey.id)
      assertEquals(forms.map { it.title }, draft.forms.map { it.form.title }, survey.id)
      assertEquals(
        forms.map { it.ctaLabel },
        draft.forms.map { it.form.effectiveCtaLabel },
        survey.id,
      )
      assertEquals(
        layers.map { it.datasetId ?: it.id },
        draft.datasets.filter { it.kind == DatasetKind.MAP_LAYER }.map { it.id },
        survey.id,
      )
      assertEquals(survey.title, draft.details.title, survey.id)
      val state = surveyEditorViewModel(draft)
      val issues =
        state.ui.sharingIssues +
          state.ui.forms.flatMap { f ->
            state.formEditor(f.key).ui.issues.map { "${f.key}: $it" }
          } +
          state.ui.datasets.flatMap { d -> state.ui.datasetIssues(d).map { "${d.id}: $it" } }
      assertEquals(emptyList(), issues, "${survey.id} derives a valid draft")
    }
  }

  @Test
  fun householdPanelSurvey_hasItsForm() {
    val draft = derive("00000032-8d62-5717-8074-f4917e0cd76d")
    val form = draft.forms.single { it.form.formId == "form-household-past-individuals" }
    assertTrue(form.form.questions.isNotEmpty())
  }

  @Test
  fun googleEarthSampleSurvey_hasStrictlyEarthCompatibleFormAndPolygonPlots() {
    val surveyId = "00000037-7e4a-5b2c-9d18-3f8a61c4e902"
    val draft = derive(surveyId)
    val form = draft.forms.single().form
    assertEquals("form-google-earth-interpretation", form.formId)
    assertTrue(form.openInGoogleEarth)
    assertTrue(form.availability.includesWeb)
    assertEquals(7, form.questions.size)
    assertTrue(
      form.questions.all { it.type.isSupportedInGoogleEarth },
      "Expected all questions in Google Earth sample survey to be Earth-compatible: ${form.questions.map { it.name to it.type }}",
    )
    val dataset = draft.datasets.single { it.kind == DatasetKind.MAP_LAYER }
    assertEquals("interpretation_plots", dataset.id)
    assertEquals(5, dataset.rows.size)
    assertTrue(dataset.rows.all { it.geometry.size == 4 })
  }

  @Test
  fun mapLayerRows_mirrorTheMapFeatures() {
    val surveyId = "00000033-6b50-51cd-ac94-baea2ea06d65"
    val draft = derive(surveyId)
    val entities = SampleEntitiesData.entitiesForSurvey(surveyId)
    for (layer in SampleMapLayersData.mapLayersForSurvey(surveyId)) {
      val dataset = draft.datasets.first { it.id == (layer.datasetId ?: layer.id) }
      val layerEntities = entities.filter { it.layerId == layer.id }
      assertEquals(layerEntities.map { it.id }, dataset.rows.map { it.key })
      assertEquals(
        layerEntities.map { it.label },
        dataset.rows.map { it.values[dataset.labelProperty] },
      )
      assertEquals(layer.label, dataset.displayName)
      assertTrue(dataset.rows.all { it.geometry.isNotEmpty() }, "rows carry geometry")
    }
  }

  @Test
  fun refresh_keepsStoredRowsAndFollowsRuntimeChanges() {
    val surveyId = "00000033-6b50-51cd-ac94-baea2ea06d65"
    val layers = SampleMapLayersData.mapLayersForSurvey(surveyId)
    val entities = SampleEntitiesData.entitiesForSurvey(surveyId)
    val stored = derive(surveyId)
    val layer = layers.first()
    val dataset = stored.datasets.first { it.id == (layer.datasetId ?: layer.id) }
    val first = entities.first { it.layerId == layer.id }
    val removed = entities.last { it.layerId == layer.id }
    val added = first.copy(id = "entity-new", label = "Brand new")
    val renamed = first.copy(label = "Renamed at runtime")
    val runtime = entities.filter { it.id != removed.id && it.id != first.id } + renamed + added

    val refreshed = SurveyEditorDerivation.refresh(stored, surveyId, layers, runtime)

    val rows = refreshed.datasets.first { it.key == dataset.key }.rows
    assertEquals("Renamed at runtime", rows.first { it.key == first.id }.values["label"])
    assertTrue(rows.none { it.key == removed.id })
    assertNotNull(rows.firstOrNull { it.key == "entity-new" })
    assertEquals(
      dataset.rows.first { it.key == first.id }.geometry,
      rows.first { it.key == first.id }.geometry,
      "stored geometry is kept",
    )
    assertEquals(stored.forms, refreshed.forms)
  }

  @Test
  fun featureGeometryKey_isNotAProperty() {
    val surveyId = "00000033-6b50-51cd-ac94-baea2ea06d65"
    val layers = SampleMapLayersData.mapLayersForSurvey(surveyId)
    val layer = layers.first()
    // A feature created by a Form with `saveto="geometry"` carries its geometry as a property.
    val entities =
      SampleEntitiesData.entitiesForSurvey(surveyId).map {
        if (it.layerId == layer.id)
          it.copy(properties = it.properties + ("geometry" to "-1.29 36.82"))
        else it
      }
    val derived =
      SurveyEditorDerivation.derive(
        surveyId = surveyId,
        survey = SampleSurveysData.defaultSampleSurveys().first { it.id == surveyId },
        forms = SampleSurveysData.formsForSurvey(surveyId),
        formDefinition = { SampleSurveysData.builtInFallbackFormForForm(it) },
        mapLayers = layers,
        entities = entities,
      )
    val datasetId = layer.datasetId ?: layer.id
    assertTrue(
      derived.datasets.first { it.id == datasetId }.properties.none { it.name == "geometry" }
    )

    // Drafts saved while the geometry key was still derived as a property drop it on refresh.
    val stale =
      derived.copy(
        datasets =
          derived.datasets.map { d ->
            if (d.id != datasetId) d
            else d.copy(properties = d.properties + EntityProperty("geometry", "geometry"))
          }
      )
    val refreshed = SurveyEditorDerivation.refresh(stale, surveyId, layers, entities)
    val dataset = refreshed.datasets.first { it.id == datasetId }
    assertTrue(dataset.properties.none { it.name.startsWith("geometry") }, "${dataset.properties}")
    assertTrue(surveyEditorViewModel(refreshed).ui.datasetIssues(dataset).isEmpty())
  }

  @Test
  fun refresh_keepsDataTablesUntouched() {
    val surveyId = "00000033-6b50-51cd-ac94-baea2ea06d65"
    val stored =
      derive(surveyId).let { it.copy(datasets = it.datasets + SurveyEditorSamples.farmers()) }
    val refreshed =
      SurveyEditorDerivation.refresh(
        stored,
        surveyId,
        SampleMapLayersData.mapLayersForSurvey(surveyId),
        SampleEntitiesData.entitiesForSurvey(surveyId),
      )
    assertEquals(SurveyEditorSamples.farmers(), refreshed.datasets.last())
  }

  @Test
  fun whispEnrichmentKeys_remainVisibleWhenPresentLocally_andArePreservedOnProject() {
    val surveyId = "00000033-6b50-51cd-ac94-baea2ea06d65"
    val layers = SampleMapLayersData.mapLayersForSurvey(surveyId)
    val layer = layers.first()
    val whispProps =
      mapOf(
        "whisp_Country" to "KEN",
        "whisp_TMF_def_2020" to "0.0",
        "whisp_GLAD-L_before_2020" to "0.0",
        "whisp_risk_timber" to "low",
      )
    val enrichedEntities =
      SampleEntitiesData.entitiesForSurvey(surveyId).map { entity ->
        if (entity.layerId == layer.id) entity.copy(properties = entity.properties + whispProps)
        else entity
      }

    // Derive keeps whisp_* columns visible (not hidden) when entities carry them locally.
    val derived =
      SurveyEditorDerivation.derive(
        surveyId = surveyId,
        survey = SampleSurveysData.defaultSampleSurveys().first { it.id == surveyId },
        forms = SampleSurveysData.formsForSurvey(surveyId),
        formDefinition = { SampleSurveysData.builtInFallbackFormForForm(it) },
        mapLayers = layers,
        entities = enrichedEntities,
      )
    val datasetId = layer.datasetId ?: layer.id
    val derivedDataset = derived.datasets.first { it.id == datasetId }
    assertTrue(
      derivedDataset.properties.any { it.label == "whisp_Country" },
      "Derived schema must keep whisp_* fields visible: ${derivedDataset.properties}",
    )

    // Publishing edits from the Survey editor preserves all whisp_* properties on runtime entities.
    val projected =
      SurveyEditorProjection.project(
        draft = derived,
        previous = derived,
        surveyId = surveyId,
        forms = SampleSurveysData.formsForSurvey(surveyId),
        mapLayers = layers,
        entities = enrichedEntities,
      )
    val projectedLayerEntities = projected.entities.filter { it.layerId == layer.id }
    assertTrue(projectedLayerEntities.isNotEmpty())
    for (entity in projectedLayerEntities) {
      for ((k, v) in whispProps) {
        assertEquals(v, entity.properties[k], "Expected $k preserved on ${entity.id}")
      }
    }
  }
}
