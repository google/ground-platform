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
package org.groundplatform.v2.devtools.prototypeapp.data.repository

import groundplatform.v2.library.ConceptRef
import groundplatform.v2.survey.FormConceptLinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptLink
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormAvailability
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityRow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft

class SurveyEditorRepositoryTest {
  private val store = seededStore()
  private val repository = SurveyEditorRepositoryImpl(store)

  private fun draft(surveyId: String): SurveyEditorDraft = runNow { repository.getDraft(surveyId) }

  @Test
  fun getDraft_derivesFormsForSurveysWithoutAStoredDraft() {
    val surveyId = "survey-household-past-individuals"
    assertNull(runNow { store.transaction { surveyEditorDraft(surveyId) } })
    val draft = draft(surveyId)
    val forms = runNow { store.transaction { forms(surveyId) } }
    assertEquals(forms.map { it.id }, draft.forms.map { it.form.formId })
    assertTrue(draft.forms.all { it.form.questions.isNotEmpty() })
    assertEquals(draft, runNow { repository.observeDraft(surveyId).first() })
  }

  @Test
  fun saveDraft_projectsLayerStyleAndRowEditsOntoTheSurvey() {
    val surveyId = "survey-kenya-coffee"
    val initial = draft(surveyId)
    val entitiesBefore = runNow { store.transaction { entities(surveyId) } }
    val dataset = initial.datasets.first { it.kind == DatasetKind.MAP_LAYER }
    val renamed = dataset.rows.first()
    val removed = dataset.rows.last()
    val renamedEntity = entitiesBefore.first { it.id == renamed.key }
    assertTrue(renamedEntity.submissions.isNotEmpty(), "fixture entity has Submissions")

    val edited =
      initial.copy(
        datasets =
          initial.datasets.map { d ->
            if (d.key != dataset.key) {
              d
            } else {
              d.copy(
                style = d.style.copy(colorHex = "#AD1457", iconName = "flag"),
                rows =
                  d.rows
                    .filter { it.key != removed.key }
                    .map { row ->
                      if (row.key == renamed.key) {
                        row.copy(values = row.values + (d.labelProperty to "Renamed parcel"))
                      } else {
                        row
                      }
                    } +
                    EntityRow(
                      key = "new-row",
                      values = mapOf(d.labelProperty to "Added in the editor"),
                      geometry = listOf(LatLng(-0.4150, 36.9500)),
                    ),
              )
            }
          },
        forms =
          initial.forms.mapIndexed { index, entry ->
            if (index == 0) {
              entry.copy(form = entry.form.copy(availability = FormAvailability.WEB))
            } else {
              entry
            }
          },
      )
    runNow { repository.saveDraft(surveyId, edited) }

    val layer = runNow {
      store.transaction { mapLayers(surveyId) }
    }
      .first { it.datasetId == dataset.id }
    assertEquals(0xFFAD1457, layer.colorHex)
    assertEquals("flag", layer.iconName)
    val entitiesAfter = runNow { store.transaction { entities(surveyId) } }
    val renamedAfter = entitiesAfter.first { it.id == renamed.key }
    assertEquals("Renamed parcel", renamedAfter.label)
    assertEquals(renamedEntity.submissions, renamedAfter.submissions)
    assertNull(
      entitiesAfter.firstOrNull { it.id == removed.key },
      "removed row's map feature is gone",
    )
    val added = assertNotNull(entitiesAfter.firstOrNull { it.label == "Added in the editor" })
    assertEquals(layer.id, added.layerId)
    assertEquals(entitiesBefore.size, entitiesAfter.size)
    // Map features of other layers are untouched.
    assertEquals(
      entitiesBefore.filter { it.layerId != layer.id },
      entitiesAfter.filter { it.layerId != layer.id },
    )
    val forms = runNow { store.transaction { forms(surveyId) } }
    assertEquals(FormAvailability.WEB, forms.first().availability)

    // Reopening shows the saved edits, with the new row keyed by its map feature.
    val reopened = draft(surveyId)
    val reopenedRows = reopened.datasets.first { it.key == dataset.key }.rows
    assertEquals(
      "Renamed parcel",
      reopenedRows.first { it.key == renamed.key }.values[dataset.labelProperty],
    )
    assertNotNull(reopenedRows.firstOrNull { it.key == added.id })
    assertTrue(reopenedRows.none { it.key == removed.key })
  }

  @Test
  fun saveDraft_keepsMapFeaturesAddedSinceTheDraftWasOpened() {
    val surveyId = "survey-kenya-coffee"
    val opened = draft(surveyId)
    val template = runNow { store.transaction { entities(surveyId) } }.first()
    val added = template.copy(id = "entity-added-in-field", label = "Collected in the field")
    runNow { store.transaction { upsertEntity(surveyId, added) } }

    runNow { repository.saveDraft(surveyId, opened) }

    val after = runNow { store.transaction { entities(surveyId) } }
    assertEquals("Collected in the field", after.first { it.id == added.id }.label)
  }

  @Test
  fun saveDraft_removesFormsDroppedFromTheDraft() {
    val surveyId = "survey-kenya-coffee"
    val opened = draft(surveyId)
    val dropped = opened.forms.last()
    runNow { repository.saveDraft(surveyId, opened.copy(forms = opened.forms.dropLast(1))) }
    val forms = runNow { store.transaction { forms(surveyId) } }
    assertTrue(forms.none { it.id == dropped.form.formId })
    val config = runNow { store.transaction { surveyConfig(surveyId) } }
    assertNull(config?.formsById?.get(dropped.form.formId))
  }

  @Test
  fun saveDraft_writesSurveyLevelConceptLinks_andPublishesGroundConcept() {
    val surveyId = "survey-kenya-coffee"
    val opened = draft(surveyId)
    val entry = opened.forms.first()
    val question = entry.form.questions.first()
    val linked =
      opened.copy(
        forms =
          opened.forms.map { f ->
            if (f.key != entry.key) {
              f
            } else {
              f.copy(
                form =
                  f.form.copy(
                    questions =
                      f.form.questions.map {
                        if (it.key == question.key)
                          it.copy(conceptLink = ConceptLink("eudr.commodity", 2))
                        else it
                      }
                  )
              )
            }
          }
      )
    runNow { repository.saveDraft(surveyId, linked) }

    val config = assertNotNull(runNow { store.transaction { surveyConfig(surveyId) } })
    val links = config.formConceptLinks.single()
    assertEquals(entry.form.formId, links.form_id)
    assertEquals(
      mapOf("/data/${question.name}" to ConceptRef("eudr.commodity", 2)),
      links.field_concepts,
    )
    val binding =
      assertNotNull(config.formsById[entry.form.formId]).proto.model?.bindings.orEmpty().first {
        it.field_path.substringAfterLast('/') == question.name
      }
    assertEquals(
      listOf("ground:concept" to "eudr.commodity@2"),
      binding.foreign_attributes.map { it.qualified_name to it.value_ },
    )
  }

  @Test
  fun getDraft_appliesSurveyLevelConceptLinksToDerivedForms() {
    val surveyId = "survey-household-past-individuals"
    val derived = draft(surveyId)
    val form = derived.forms.first().form
    val question = form.questions.first()
    val fresh = seededStore()
    runNow {
      fresh.transaction {
        val config = surveyConfig(surveyId) ?: SurveyConfig()
        putSurveyConfig(
          surveyId,
          config.copy(
            formConceptLinks =
              listOf(
                FormConceptLinks(
                  form_id = form.formId,
                  field_concepts = mapOf("/data/${question.name}" to ConceptRef("core.notes", 1)),
                )
              )
          ),
        )
      }
    }
    val reopened = runNow { SurveyEditorRepositoryImpl(fresh).getDraft(surveyId) }
    val questions = reopened.forms.first { it.form.formId == form.formId }.form.questions
    assertEquals(
      ConceptLink("core.notes", 1),
      questions.first { it.name == question.name }.conceptLink,
    )
    assertTrue(questions.filter { it.name != question.name }.all { it.conceptLink == null })
  }
}
