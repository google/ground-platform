/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeEntitiesData
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeMapLayersData
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeSurveysData
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SurveyEditorSamples
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.surveyEditorViewModel
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.ui

class SurveyEditorDerivationTest {
  private fun derive(surveyId: String): SurveyEditorDraft {
    val forms = PrototypeFakeSurveysData.formsForSurvey(surveyId)
    return SurveyEditorDerivation.derive(
      surveyId = surveyId,
      survey = PrototypeFakeSurveysData.defaultSampleSurveys().first { it.id == surveyId },
      forms = forms,
      formXml = { PrototypeFakeSurveysData.builtInFallbackXFormsXmlForForm(it) },
      mapLayers = PrototypeFakeMapLayersData.mapLayersForSurvey(surveyId),
      entities = PrototypeFakeEntitiesData.entitiesForSurvey(surveyId),
    )
  }

  @Test
  fun everySampleSurvey_derivesItsFormsAndMapLayers() {
    for (survey in PrototypeFakeSurveysData.defaultSampleSurveys()) {
      val draft = derive(survey.id)
      val forms = PrototypeFakeSurveysData.formsForSurvey(survey.id)
      val layers = PrototypeFakeMapLayersData.mapLayersForSurvey(survey.id)
      assertTrue(draft.forms.isNotEmpty(), "${survey.id} derives at least one Form")
      assertEquals(forms.map { it.id }, draft.forms.map { it.form.formId }, survey.id)
      assertEquals(forms.map { it.title }, draft.forms.map { it.form.title }, survey.id)
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
    val draft = derive("survey-household-past-individuals")
    val form = draft.forms.single { it.form.formId == "form-household-past-individuals" }
    assertTrue(form.form.questions.isNotEmpty())
  }

  @Test
  fun mapLayerRows_mirrorTheMapFeatures() {
    val surveyId = "survey-kenya-coffee"
    val draft = derive(surveyId)
    val entities = PrototypeFakeEntitiesData.entitiesForSurvey(surveyId)
    for (layer in PrototypeFakeMapLayersData.mapLayersForSurvey(surveyId)) {
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
    val surveyId = "survey-kenya-coffee"
    val layers = PrototypeFakeMapLayersData.mapLayersForSurvey(surveyId)
    val entities = PrototypeFakeEntitiesData.entitiesForSurvey(surveyId)
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
  fun refresh_keepsDataTablesUntouched() {
    val surveyId = "survey-kenya-coffee"
    val stored =
      derive(surveyId).let { it.copy(datasets = it.datasets + SurveyEditorSamples.farmers()) }
    val refreshed =
      SurveyEditorDerivation.refresh(
        stored,
        surveyId,
        PrototypeFakeMapLayersData.mapLayersForSurvey(surveyId),
        PrototypeFakeEntitiesData.entitiesForSurvey(surveyId),
      )
    assertEquals(SurveyEditorSamples.farmers(), refreshed.datasets.last())
  }
}
