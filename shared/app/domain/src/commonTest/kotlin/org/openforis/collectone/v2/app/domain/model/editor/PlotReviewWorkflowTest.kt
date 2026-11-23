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
package org.openforis.collectone.v2.app.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow.DESK_CONFIDENCE
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow.DESK_LAND_COVER
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow.FIELD_LAND_COVER
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow.FIELD_LOCATION
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow.FIELD_NOTES
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow.FLAG_REASON
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow.NEEDS_FIELD_CHECK
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow.PLOT_REACHED
import org.openforis.collectone.v2.map.GeometryType

/** Setting up the desk and field Forms that review generated sample plots. */
class PlotReviewWorkflowTest {
  private val layer =
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
        (1..30).map { n ->
          EntityRow(
            key = "r$n",
            values =
              mapOf(
                SamplePlotProperties.PLOT_ID to "P$n",
                SamplePlotProperties.SAMPLE_ORDER to "${31 - n}",
              ),
          )
        },
      generator = SampleDesignConfig(),
    )

  private val draft =
    SurveyEditorDraft.blank("survey-x", title = "X").copy(datasets = listOf(layer))

  private var formIds = 0

  private fun apply(draft: SurveyEditorDraft, options: PlotReviewOptions) =
    PlotReviewWorkflow.apply(draft, layer.key, options) { "form_${++formIds}" }

  private val fieldOptions =
    PlotReviewOptions(
      fieldValidation = true,
      autoFlagBelowConfidence = 40,
      spotCheckCount = 5,
      showDeskAnswersInField = true,
    )

  private fun SurveyEditorDraft.form(key: String?) = forms.single { it.key == key }.form

  private fun SurveyEditorDraft.layer() = datasets.single { it.key == layer.key }

  private fun EditorForm.key(name: String) = questions.single { it.name == name }.key

  private fun issues(draft: SurveyEditorDraft, formKey: String?): List<EditorIssue> {
    val form = draft.form(formKey)
    val catalog = draft.datasets.map { it.toEditorDataset(formKey.orEmpty(), draft.forms) }
    return FormEditorValidator.validate(form, catalog) + SaveToValidator.validate(form, catalog)
  }

  @Test
  fun setup_withoutReviewForms_isOff_andSuggestsSpotChecks() {
    val setup = PlotReviewWorkflow.setup(draft, layer.key)
    assertFalse(setup.isEnabled)
    assertNull(setup.fieldFormKey)
    assertEquals(PlotReviewOptions(spotCheckCount = 3), setup.options)
  }

  @Test
  fun suggestedSpotCheckCount_isTenPercentOfThePlots() {
    assertEquals(10, PlotReviewWorkflow.suggestedSpotCheckCount(0))
    assertEquals(1, PlotReviewWorkflow.suggestedSpotCheckCount(3))
    assertEquals(5, PlotReviewWorkflow.suggestedSpotCheckCount(50))
    assertEquals(100, PlotReviewWorkflow.suggestedSpotCheckCount(5_000))
  }

  @Test
  fun deskOnly_addsAWebFormThatUpdatesEachPlot() {
    val reviewed = apply(draft, PlotReviewOptions())
    val setup = PlotReviewWorkflow.setup(reviewed, layer.key)
    assertTrue(setup.isEnabled)
    assertNull(setup.fieldFormKey)
    assertFalse(setup.options.fieldValidation)

    val desk = reviewed.form(setup.deskFormKey)
    assertEquals("form_1", desk.formId)
    assertEquals(PlotReviewWorkflow.DESK_FORM_TITLE, desk.title)
    assertEquals(FormAvailability.WEB, desk.availability)
    assertEquals(SaveToMode.UPDATE, desk.saveTo.mode)
    assertEquals(layer.id, desk.saveTo.targetDatasetId)
    assertEquals(EntityIdSource.SELECTED_FEATURE, desk.saveTo.idSource)
    assertEquals(listOf(DESK_LAND_COVER, DESK_CONFIDENCE), desk.questions.map { it.name })
    assertEquals(DESK_LAND_COVER, desk.saveTo.propertyFor(desk.key(DESK_LAND_COVER)))
    assertEquals(DESK_CONFIDENCE, desk.saveTo.propertyFor(desk.key(DESK_CONFIDENCE)))
    assertEquals(
      listOf(PlotReviewWorkflow.DESK_VERIFIED),
      desk.saveTo.status.rules.map { it.badge },
    )
    assertTrue(desk.saveTo.status.enabled)

    // The plots get the properties the Form writes.
    val properties = reviewed.layer().properties.map { it.name }
    assertTrue(properties.containsAll(listOf(DESK_LAND_COVER, DESK_CONFIDENCE)))
    assertEquals(PropertyType.INTEGER, reviewed.layer().property(DESK_CONFIDENCE)?.type)
    assertTrue(
      issues(reviewed, setup.deskFormKey).isEmpty(),
      issues(reviewed, setup.deskFormKey).toString(),
    )
  }

  @Test
  fun fieldValidation_addsAMobileForm_andSendsPlotsToTheField() {
    val reviewed = apply(draft, fieldOptions)
    val setup = PlotReviewWorkflow.setup(reviewed, layer.key)
    assertEquals(fieldOptions, setup.options)

    val desk = reviewed.form(setup.deskFormKey)
    assertEquals(
      listOf(DESK_LAND_COVER, DESK_CONFIDENCE, NEEDS_FIELD_CHECK, FLAG_REASON),
      desk.questions.map { it.name },
    )
    val reason = desk.questions.single { it.name == FLAG_REASON }
    assertEquals(desk.key(NEEDS_FIELD_CHECK), reason.relevance?.sourceQuestionKey)
    assertEquals(FLAG_REASON, desk.saveTo.propertyFor(reason.key))
    assertNull(desk.saveTo.propertyFor(desk.key(NEEDS_FIELD_CHECK)))

    // Flagged, low confidence, and spot-check plots need a field visit; the rest are done.
    val rules = desk.saveTo.status.rules
    assertEquals(
      listOf(
        StatusConditionSubject.QUESTION to "yes",
        StatusConditionSubject.QUESTION to "40",
        StatusConditionSubject.ENTITY_PROPERTY to "6",
        StatusConditionSubject.SUBMISSIONS to "",
      ),
      rules.map { it.subject to it.value },
    )
    assertEquals(SamplePlotProperties.SAMPLE_ORDER, rules[2].property)
    assertEquals(
      listOf(PlotReviewWorkflow.NEEDS_FIELD_VALIDATION, PlotReviewWorkflow.DESK_VERIFIED),
      rules.map { it.badge }.distinct(),
    )

    val field = reviewed.form(setup.fieldFormKey)
    assertEquals("form_2", field.formId)
    assertEquals(PlotReviewWorkflow.FIELD_FORM_TITLE, field.title)
    assertEquals(FormAvailability.MOBILE, field.availability)
    assertEquals(layer.id, field.saveTo.targetDatasetId)
    assertEquals(PLOT_REACHED, field.saveTo.propertyFor(field.key(PLOT_REACHED)))
    assertEquals(FIELD_LAND_COVER, field.saveTo.propertyFor(field.key(FIELD_LAND_COVER)))
    assertEquals(FIELD_NOTES, field.saveTo.propertyFor(field.key(FIELD_NOTES)))
    // The crew's location doesn't replace the plot's geometry.
    assertNull(field.saveTo.propertyFor(field.key(FIELD_LOCATION)))
    assertEquals(
      listOf(PlotReviewWorkflow.NOT_REACHABLE, PlotReviewWorkflow.FIELD_VERIFIED),
      field.saveTo.status.rules.map { it.badge },
    )

    val properties = reviewed.layer().properties.map { it.name }
    assertTrue(properties.containsAll(listOf(FLAG_REASON, PLOT_REACHED, FIELD_LAND_COVER)))
    assertTrue(
      issues(reviewed, setup.deskFormKey).isEmpty(),
      issues(reviewed, setup.deskFormKey).toString(),
    )
    assertTrue(
      issues(reviewed, setup.fieldFormKey).isEmpty(),
      issues(reviewed, setup.fieldFormKey).toString(),
    )
  }

  @Test
  fun apply_isIdempotent() {
    val once = apply(draft, fieldOptions)
    assertEquals(once, apply(once, fieldOptions))
  }

  @Test
  fun optionsOff_dropTheirRules() {
    val reviewed = apply(draft, fieldOptions.copy(autoFlagBelowConfidence = 0, spotCheckCount = 0))
    val setup = PlotReviewWorkflow.setup(reviewed, layer.key)
    assertEquals(0, setup.options.autoFlagBelowConfidence)
    assertEquals(0, setup.options.spotCheckCount)
    assertEquals(
      listOf(StatusConditionSubject.QUESTION, StatusConditionSubject.SUBMISSIONS),
      reviewed.form(setup.deskFormKey).saveTo.status.rules.map { it.subject },
    )
  }

  @Test
  fun blindFieldChecks_keepDeskAnswersOffThePlots() {
    val shown = apply(draft, fieldOptions)
    val blind = apply(shown, fieldOptions.copy(showDeskAnswersInField = false))
    val setup = PlotReviewWorkflow.setup(blind, layer.key)
    assertFalse(setup.options.showDeskAnswersInField)
    val desk = blind.form(setup.deskFormKey)
    assertNull(desk.saveTo.propertyFor(desk.key(DESK_LAND_COVER)))
    assertNull(desk.saveTo.propertyFor(desk.key(DESK_CONFIDENCE)))
    // Crews still see why a plot was flagged.
    assertEquals(FLAG_REASON, desk.saveTo.propertyFor(desk.key(FLAG_REASON)))
    // Unused, empty properties are dropped from the plots.
    val properties = blind.layer().properties.map { it.name }
    assertFalse(DESK_LAND_COVER in properties)
    assertFalse(DESK_CONFIDENCE in properties)
    assertTrue(FLAG_REASON in properties)
  }

  @Test
  fun turningFieldValidationOff_removesTheFieldFormAndFlagging() {
    val withField = apply(draft, fieldOptions)
    val deskOnly = apply(withField, fieldOptions.copy(fieldValidation = false))
    val setup = PlotReviewWorkflow.setup(deskOnly, layer.key)
    assertNull(setup.fieldFormKey)
    assertEquals(1, deskOnly.forms.size)
    val desk = deskOnly.form(setup.deskFormKey)
    assertEquals(listOf(DESK_LAND_COVER, DESK_CONFIDENCE), desk.questions.map { it.name })
    assertEquals(
      listOf(StatusConditionSubject.SUBMISSIONS),
      desk.saveTo.status.rules.map { it.subject },
    )
    val properties = deskOnly.layer().properties.map { it.name }
    assertTrue(properties.none { it in setOf(FLAG_REASON, PLOT_REACHED, FIELD_LAND_COVER) })

    // Turning it back on brings the field Form back, with a new ID.
    val again = apply(deskOnly, fieldOptions)
    assertEquals(2, again.forms.size)
    assertNotNull(PlotReviewWorkflow.setup(again, layer.key).fieldFormKey)
  }

  @Test
  fun apply_keepsOrganizerEditsToTheForms() {
    val reviewed = apply(draft, fieldOptions)
    val deskKey = PlotReviewWorkflow.setup(reviewed, layer.key).deskFormKey
    val edited =
      reviewed.copy(
        forms =
          reviewed.forms.map { entry ->
            if (entry.key != deskKey) entry
            else {
              entry.copy(
                form =
                  entry.form.copy(
                    title = "Imagery check",
                    questions =
                      entry.form.questions +
                        EditorQuestion("q99", "cloud_cover", EditorQuestionType.TEXT, "Clouds?"),
                  )
              )
            }
          }
      )
    val updated = apply(edited, fieldOptions.copy(autoFlagBelowConfidence = 70))
    val desk = updated.form(deskKey)
    assertEquals("Imagery check", desk.title)
    assertTrue(desk.questions.any { it.name == "cloud_cover" })
    assertEquals(70, PlotReviewWorkflow.setup(updated, layer.key).options.autoFlagBelowConfidence)
    // Desk answers are saved to plot properties of the same names, new questions included.
    assertEquals("cloud_cover", desk.saveTo.propertyFor("q99"))
    assertNotNull(updated.layer().property("cloud_cover"))
  }

  /** The create-survey wizard's interpretation Form, which updates the plots. */
  private val interpretation =
    SurveyEditorForm(
      "f1",
      EditorForm(
        formId = "form_interpretation",
        title = "Plot interpretation",
        questions =
          listOf(
            EditorQuestion(
              "q1",
              "land_cover",
              EditorQuestionType.SELECT_ONE,
              "What is the primary land cover?",
              required = true,
              choices =
                listOf(EditorChoice("forest", "Forest"), EditorChoice("non_forest", "Other")),
            ),
            EditorQuestion(
              "q2",
              "center",
              EditorQuestionType.LOCATION,
              "Plot center",
              capture = GeometryCapture.GPS_ONLY,
            ),
          ),
        saveTo =
          EditorSaveTo(
            mode = SaveToMode.UPDATE,
            targetDatasetId = layer.id,
            mappings = listOf(EditorFieldMapping("q1", "land_cover")),
          ),
        availability = FormAvailability.BOTH,
      ),
    )

  private val wizardDraft = draft.copy(forms = listOf(interpretation))

  @Test
  fun adoptedDeskCheck_becomesTheWebDeskCheck_withoutANewForm() {
    val reviewed = apply(wizardDraft, fieldOptions)
    val adopted =
      PlotReviewWorkflow.apply(wizardDraft, layer.key, fieldOptions, deskFormKey = "f1") {
        "form_new"
      }
    val setup = PlotReviewWorkflow.setup(adopted, layer.key)
    assertEquals("f1", setup.deskFormKey)
    assertEquals(fieldOptions, setup.options)
    // The interpretation Form plus the field Form; no Desk visual check Form.
    assertEquals(2, adopted.forms.size)
    assertTrue(adopted.forms.none { it.form.title == PlotReviewWorkflow.DESK_FORM_TITLE })

    val desk = adopted.form("f1")
    assertEquals("Plot interpretation", desk.title)
    assertEquals(FormAvailability.WEB, desk.availability)
    assertEquals(
      listOf("land_cover", "center", DESK_CONFIDENCE, NEEDS_FIELD_CHECK, FLAG_REASON),
      desk.questions.map { it.name },
    )
    // Web interpreters draw geometry on the map directly without GPS lock, and plots keep their
    // own geometry.
    assertEquals(GeometryCapture.GPS_ONLY, desk.questions.single { it.name == "center" }.capture)
    assertNull(desk.saveTo.propertyFor("q2"))
    assertEquals("land_cover", desk.saveTo.propertyFor("q1"))
    assertEquals(DESK_CONFIDENCE, desk.saveTo.propertyFor(desk.key(DESK_CONFIDENCE)))
    assertTrue(desk.saveTo.status.enabled)
    assertTrue(issues(adopted, "f1").isEmpty(), issues(adopted, "f1").toString())

    // Without a desk Form key, the review adds its own Desk visual check Form instead.
    assertEquals(3, reviewed.forms.size)
  }

  @Test
  fun adoptedDeskCheck_blindFieldChecks_hideAllItsAnswers() {
    val blind =
      PlotReviewWorkflow.apply(
        wizardDraft,
        layer.key,
        fieldOptions.copy(showDeskAnswersInField = false),
        deskFormKey = "f1",
      ) {
        "form_new"
      }
    val desk = blind.form("f1")
    assertNull(desk.saveTo.propertyFor("q1"))
    assertNull(desk.saveTo.propertyFor(desk.key(DESK_CONFIDENCE)))
    assertEquals(FLAG_REASON, desk.saveTo.propertyFor(desk.key(FLAG_REASON)))
    assertFalse(PlotReviewWorkflow.setup(blind, layer.key).options.showDeskAnswersInField)

    // Showing them again saves them back to the plots.
    val shown = PlotReviewWorkflow.apply(blind, layer.key, fieldOptions, deskFormKey = "f1")
    assertEquals("land_cover", shown.form("f1").saveTo.propertyFor("q1"))
  }

  @Test
  fun removingAnAdoptedReview_keepsTheForm_withoutTheReviewQuestions() {
    val adopted =
      PlotReviewWorkflow.apply(wizardDraft, layer.key, fieldOptions, deskFormKey = "f1") {
        "form_new"
      }
    val removed = PlotReviewWorkflow.remove(adopted, layer.key)
    assertEquals(listOf("f1"), removed.forms.map { it.key })
    val form = removed.form("f1")
    assertEquals(listOf("land_cover", "center"), form.questions.map { it.name })
    assertFalse(form.saveTo.status.enabled)
    assertEquals("land_cover", form.saveTo.propertyFor("q1"))
    assertFalse(PlotReviewWorkflow.setup(removed, layer.key).isEnabled)
    val properties = removed.layer().properties.map { it.name }
    assertTrue(properties.none { it in setOf(DESK_CONFIDENCE, FLAG_REASON, PLOT_REACHED) })
  }

  @Test
  fun newForms_getUniqueTitles() {
    val withDeskTitle =
      draft.copy(
        forms =
          listOf(
            SurveyEditorForm(
              "f9",
              EditorFormTemplates.blank("other", PlotReviewWorkflow.DESK_FORM_TITLE),
            )
          )
      )
    val reviewed = apply(withDeskTitle, PlotReviewOptions())
    val desk = reviewed.form(PlotReviewWorkflow.setup(reviewed, layer.key).deskFormKey)
    assertEquals("${PlotReviewWorkflow.DESK_FORM_TITLE} 2", desk.title)
  }

  @Test
  fun remove_dropsTheReviewFormsAndTheirProperties() {
    val reviewed = apply(draft, fieldOptions)
    val removed = PlotReviewWorkflow.remove(reviewed, layer.key)
    assertTrue(removed.forms.isEmpty())
    assertEquals(layer.properties, removed.layer().properties)
    assertFalse(PlotReviewWorkflow.setup(removed, layer.key).isEnabled)
  }
}
