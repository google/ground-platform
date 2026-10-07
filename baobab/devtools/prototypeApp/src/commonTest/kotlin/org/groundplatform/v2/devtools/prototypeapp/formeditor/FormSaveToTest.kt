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
package org.groundplatform.v2.devtools.prototypeapp.formeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorDatasetProperty
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorDatasetRow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorFieldMapping
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorPropertyKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorSaveTo
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorStatusBadge
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorStatusConfig
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorStatusRule
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorXFormsGenerator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityIdSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.RelevanceOperator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToRules
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToValidator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.StatusConditionSubject
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.formEditorViewModel
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.ui

class FormSaveToTest {

  private val plots =
    EditorDataset(
      id = "plots",
      displayName = "Plots",
      isMapLayer = true,
      keyProperty = "plot_id",
      labelProperty = "plot_name",
      properties =
        listOf(
          EditorDatasetProperty("plot_id", "Plot ID", EditorPropertyKind.TEXT),
          EditorDatasetProperty("plot_name", "Plot name", EditorPropertyKind.TEXT),
          EditorDatasetProperty("canopy_pct", "Canopy", EditorPropertyKind.INTEGER),
          EditorDatasetProperty("notes", "Notes", EditorPropertyKind.TEXT),
        ),
      rows =
        listOf(
          EditorDatasetRow("P-1", "North plot", mapOf("plot_id" to "P-1", "canopy_pct" to "10")),
          EditorDatasetRow("P-2", "South plot", mapOf("plot_id" to "P-2", "canopy_pct" to "20")),
        ),
    )

  private val farmers =
    EditorDataset(
      id = "farmers",
      displayName = "Farmers",
      isMapLayer = false,
      keyProperty = "farmer_id",
      labelProperty = "name",
      properties =
        listOf(
          EditorDatasetProperty("farmer_id", "Farmer ID", EditorPropertyKind.TEXT),
          EditorDatasetProperty("name", "Name", EditorPropertyKind.TEXT),
          EditorDatasetProperty("cooperative", "Cooperative", EditorPropertyKind.TEXT),
          EditorDatasetProperty("notes", "Notes", EditorPropertyKind.TEXT),
        ),
      rows =
        listOf(
          EditorDatasetRow(
            "F-1",
            "Jane",
            mapOf("farmer_id" to "F-1", "name" to "Jane", "cooperative" to "othaya"),
          ),
          EditorDatasetRow(
            "F-2",
            "Joe",
            mapOf("farmer_id" to "F-2", "name" to "Joe", "cooperative" to "tetu"),
          ),
        ),
    )

  private fun q(key: String, name: String, type: EditorQuestionType) =
    EditorQuestion(key = key, name = name, type = type, label = name)

  private val visitForm =
    EditorForm(
      formId = "form_visit",
      title = "Visit",
      questions =
        listOf(
          q("q1", "location", EditorQuestionType.LOCATION),
          q("q2", "canopy_pct", EditorQuestionType.INTEGER),
          q("q3", "notes", EditorQuestionType.TEXT),
          q("q4", "farmer_id", EditorQuestionType.TEXT),
          q("q5", "intro", EditorQuestionType.NOTE),
        ),
    )

  private fun stateWith(
    form: EditorForm = visitForm,
    datasets: List<EditorDataset> = listOf(plots, farmers),
  ) = formEditorViewModel(form, datasets)

  private fun finalize(xml: String, answers: (FormWizardController) -> Unit) =
    FormWizardController(formDef = XFormsXmlSerializer.deserializeFormDef(xml))
      .also(answers)
      .finalizeForm()

  @Test
  fun createLabel_followsGeometry() {
    assertEquals("Add new map feature", SaveToRules.createLabel(visitForm.hasGeometry))
    val noLocation = visitForm.copy(questions = visitForm.questions.drop(1))
    assertFalse(noLocation.hasGeometry)
    assertEquals("Add new table row", SaveToRules.createLabel(noLocation.hasGeometry))
  }

  @Test
  fun toXml_withoutTarget_emitsNoEntities() {
    val xml = EditorXFormsGenerator.toXml(visitForm)
    assertFalse(xml.contains("entities"))
    assertTrue(XFormsXmlSerializer.deserializeFormDef(xml).model!!.entities.isEmpty())
  }

  @Test
  fun toXml_create_declaresEntityAndSavesEveryAnswer() {
    val linked = plots.copy(isLinkedToThisForm = true)
    val xml = EditorXFormsGenerator.toXml(visitForm, linked)
    val entity = XFormsXmlSerializer.deserializeFormDef(xml).model!!.entities.single()
    assertEquals("plots", entity.dataset)
    assertEquals("1", entity.create_condition)
    assertEquals(
      setOf("geometry", "canopy_pct", "notes", "farmer_id"),
      entity.property_mappings.map { it.entity_property }.toSet(),
    )

    val result = finalize(xml) { it.updateString("/data/notes", "Healthy") }
    val state = assertIs<FinalizationResult.Success>(result).entityStates.single()
    assertTrue(state.shouldCreate)
    assertTrue(state.entityId.isNotBlank())
    assertEquals("Healthy", state.label)
  }

  @Test
  fun toXml_updateSelectedFeature_previewResolvesFeatureAndVersion() {
    val form =
      visitForm.copy(
        saveTo =
          EditorSaveTo(
            mode = SaveToMode.UPDATE,
            targetDatasetId = "plots",
            mappings = listOf(EditorFieldMapping("q2", "canopy_pct")),
          )
      )
    val exported = EditorXFormsGenerator.toXml(form, plots)
    assertTrue(exported.contains("""src="jr://file-csv/plots.csv"/>"""))

    val xml = EditorXFormsGenerator.toXml(form, plots, inlineRows = true)
    val entity = XFormsXmlSerializer.deserializeFormDef(xml).model!!.entities.single()
    assertEquals("1", entity.update_condition)
    assertEquals(listOf("canopy_pct"), entity.property_mappings.map { it.entity_property })

    val result =
      finalize(xml) {
        it.updateString("/data/target_entity", "P-2")
        it.updateInt("/data/canopy_pct", 45)
      }
    val state = assertIs<FinalizationResult.Success>(result).entityStates.single()
    assertTrue(state.shouldUpdate)
    assertFalse(state.shouldCreate)
    assertEquals("P-2", state.entityId)
    assertEquals(1, state.baseVersion)
    assertEquals(setOf("canopy_pct"), state.properties.keys)
  }

  @Test
  fun toXml_updateByQuestion_looksUpFeatureByProperty() {
    val form =
      visitForm.copy(
        saveTo =
          EditorSaveTo(
            mode = SaveToMode.UPDATE,
            targetDatasetId = "farmers",
            idSource = EntityIdSource.QUESTION,
            idQuestionKey = "q3",
            idMatchProperty = "cooperative",
            mappings = listOf(EditorFieldMapping("q2", "notes")),
          )
      )
    assertEquals(
      "instance('farmers')/root/item[cooperative = /data/notes]/name",
      SaveToRules.entityIdExpression(form, farmers),
    )
    val xml = EditorXFormsGenerator.toXml(form, farmers, inlineRows = true)
    assertFalse(xml.contains("target_entity"))

    val result = finalize(xml) { it.updateString("/data/notes", "tetu") }
    val state = assertIs<FinalizationResult.Success>(result).entityStates.single()
    assertEquals("F-2", state.entityId)
  }

  @Test
  fun entityIdExpression_matchingKeyPropertyUsesAnswerDirectly() {
    val form =
      visitForm.copy(
        saveTo =
          EditorSaveTo(
            mode = SaveToMode.UPDATE,
            targetDatasetId = "farmers",
            idSource = EntityIdSource.QUESTION,
            idQuestionKey = "q4",
            idMatchProperty = "farmer_id",
          )
      )
    assertEquals("/data/farmer_id", SaveToRules.entityIdExpression(form, farmers))
  }

  @Test
  fun validator_flagsIncompleteUpdateLogic() {
    val datasets = listOf(plots, farmers)
    val update = EditorSaveTo(mode = SaveToMode.UPDATE)
    assertEquals(
      listOf("Choose the map layer or data table this form updates."),
      SaveToValidator.validate(visitForm.copy(saveTo = update), datasets).map { it.message },
    )

    val byQuestion =
      update.copy(
        targetDatasetId = "farmers",
        idSource = EntityIdSource.QUESTION,
        mappings =
          listOf(
            EditorFieldMapping("q3", "geometry"),
            EditorFieldMapping("q2", "notes"),
            EditorFieldMapping("q4", "notes"),
          ),
      )
    val issues = SaveToValidator.validate(visitForm.copy(saveTo = byQuestion), datasets)
    assertTrue(
      issues.any { it.questionKey == null && it.message.startsWith("Choose the question") }
    )
    assertTrue(
      issues.any { it.questionKey == null && it.message.startsWith("Choose the property") }
    )
    assertTrue(issues.any { it.questionKey == "q3" && it.message.contains("geometry") })
    assertTrue(issues.any { it.questionKey == "q4" && it.message.contains("already updates") })
  }

  @Test
  fun validator_reservesTargetEntityNameForSelectedFeature() {
    val form =
      visitForm.copy(
        questions = visitForm.questions + q("q9", "target_entity", EditorQuestionType.TEXT),
        saveTo =
          EditorSaveTo(
            mode = SaveToMode.UPDATE,
            targetDatasetId = "plots",
            mappings = listOf(EditorFieldMapping("q2", "canopy_pct")),
          ),
      )
    val issues = SaveToValidator.validate(form, listOf(plots))
    assertEquals(listOf("q9"), issues.map { it.questionKey })
  }

  @Test
  fun setSaveToMode_update_defaultsByTargetKind() {
    val state = stateWith()
    state.setSaveToMode(SaveToMode.UPDATE)
    val saveTo = state.ui.form.saveTo
    assertEquals("plots", saveTo.targetDatasetId)
    assertEquals(EntityIdSource.SELECTED_FEATURE, saveTo.idSource)
    assertEquals("geometry", saveTo.propertyFor("q1"))
    assertEquals("canopy_pct", saveTo.propertyFor("q2"))
    assertEquals("notes", saveTo.propertyFor("q3"))
    assertNull(saveTo.propertyFor("q4"))
    assertTrue(state.ui.issues.isEmpty(), state.ui.issues.toString())

    state.setTargetDataset("farmers")
    val table = state.ui.form.saveTo
    assertEquals(EntityIdSource.QUESTION, table.idSource)
    assertEquals("q4", table.idQuestionKey)
    assertEquals("farmer_id", table.idMatchProperty)
    assertEquals(null, table.propertyFor("q1"))
    assertEquals("notes", table.propertyFor("q3"))
    assertTrue(state.ui.issues.isEmpty(), state.ui.issues.toString())
  }

  @Test
  fun updateTargets_excludeLinkedDataset() {
    val state = stateWith(datasets = listOf(plots.copy(isLinkedToThisForm = true), farmers))
    assertEquals(listOf("farmers"), state.ui.updateTargets.map { it.id })
    assertEquals("plots", state.ui.saveTarget?.id)
    state.setSaveToMode(SaveToMode.UPDATE)
    assertEquals("farmers", state.ui.saveTarget?.id)
  }

  @Test
  fun deleteAndRetype_cleanUpSaveToReferences() {
    val state = stateWith()
    state.setSaveToMode(SaveToMode.UPDATE)
    state.setTargetDataset("farmers")
    state.deleteQuestion("q4")
    assertNull(state.ui.form.saveTo.idQuestionKey)

    state.setTargetDataset("plots")
    state.changeType("q1", EditorQuestionType.TEXT)
    assertNull(state.ui.form.saveTo.propertyFor("q1"))
    state.changeType("q3", EditorQuestionType.NOTE)
    assertTrue(state.ui.form.saveTo.mappings.none { it.questionKey == "q3" })
  }

  @Test
  fun selectForm_clearsQuestionSelection() {
    val state = stateWith()
    assertFalse(state.ui.isFormSelected)
    state.selectForm()
    assertTrue(state.ui.isFormSelected)
    assertNull(state.ui.selectedQuestion)
  }

  @Test
  fun startPreview_runsUpdateFormWithEmbeddedFeatures() {
    val state = stateWith()
    state.setSaveToMode(SaveToMode.UPDATE)
    state.startPreview()
    assertNull(state.ui.previewError)
    val controller = state.ui.previewController!!
    assertTrue(controller.formState.fieldStates.containsKey("/data/target_entity"))
  }

  @Test
  fun advancedDisclosure_isSharedAndStartsFromAutoExpand() {
    AdvancedDisclosure.expanded = null
    assertFalse(AdvancedDisclosure.isExpanded(autoExpand = false))
    assertTrue(AdvancedDisclosure.isExpanded(autoExpand = true))
    AdvancedDisclosure.toggle(autoExpand = false)
    assertTrue(AdvancedDisclosure.isExpanded(autoExpand = false))
    AdvancedDisclosure.toggle(autoExpand = true)
    assertFalse(AdvancedDisclosure.isExpanded(autoExpand = true))
    AdvancedDisclosure.expanded = null
  }

  @Test
  fun statusMarker_disabledByDefault_andSensibleDefaultsWhenEnabled() {
    val linked = plots.copy(isLinkedToThisForm = true)
    val state = stateWith(datasets = listOf(linked, farmers))
    assertFalse(state.ui.form.saveTo.status.enabled)
    assertFalse(state.ui.form.saveTo.isCustomized)
    assertFalse(state.ui.xformsXml.contains(SaveToRules.STATUS_FIELD))

    state.setStatusEnabled(true)
    assertTrue(state.ui.form.saveTo.status.enabled)
    assertTrue(state.ui.form.saveTo.isCustomized)
    assertTrue(state.ui.issues.isEmpty(), state.ui.issues.toString())

    val result = finalize(state.ui.xformsXml) { it.updateString("/data/notes", "Checked") }
    val entityState = assertIs<FinalizationResult.Success>(result).entityStates.single()
    assertEquals("Surveyed", entityState.properties["status"]?.string_value)
    assertEquals("✓", entityState.properties["marker-symbol"]?.string_value)
    assertEquals("#3C8D40", entityState.properties["marker-color"]?.string_value)
  }

  @Test
  fun statusMarker_conditionalRules_evaluateQuestionPropertyAndSubmissionCountInOrder() {
    val plotsWithVersion =
      plots.copy(
        rows =
          listOf(
            EditorDatasetRow(
              "P-1",
              "North plot",
              mapOf("plot_id" to "P-1", "canopy_pct" to "10", "notes" to "ok", "__version" to "1"),
            ),
            EditorDatasetRow(
              "P-2",
              "South plot",
              mapOf(
                "plot_id" to "P-2",
                "canopy_pct" to "20",
                "notes" to "flagged",
                "__version" to "1",
              ),
            ),
            EditorDatasetRow(
              "P-3",
              "East plot",
              mapOf("plot_id" to "P-3", "canopy_pct" to "30", "notes" to "ok", "__version" to "2"),
            ),
          )
      )
    val form =
      visitForm.copy(
        saveTo =
          EditorSaveTo(
            mode = SaveToMode.UPDATE,
            targetDatasetId = "plots",
            mappings = listOf(EditorFieldMapping("q2", "canopy_pct")),
            status =
              EditorStatusConfig(
                enabled = true,
                rules =
                  listOf(
                    EditorStatusRule(
                      subject = StatusConditionSubject.QUESTION,
                      questionKey = "q2",
                      operator = RelevanceOperator.LESS_THAN,
                      value = "15",
                      badge = EditorStatusBadge.NEEDS_REVIEW,
                    ),
                    EditorStatusRule(
                      subject = StatusConditionSubject.ENTITY_PROPERTY,
                      property = "notes",
                      operator = RelevanceOperator.EQUALS,
                      value = "flagged",
                      badge = EditorStatusBadge.FLAGGED,
                    ),
                    EditorStatusRule(
                      subject = StatusConditionSubject.SUBMISSIONS,
                      minSubmissions = 2,
                      badge = EditorStatusBadge.SURVEYED,
                    ),
                  ),
                defaultBadge = EditorStatusBadge.IN_PROGRESS,
              ),
          )
      )
    assertTrue(SaveToValidator.validate(form, listOf(plotsWithVersion)).isEmpty())
    val xml = EditorXFormsGenerator.toXml(form, plotsWithVersion, inlineRows = true)

    // 1. Question condition matches (canopy_pct < 15) -> Needs review (!)
    val r1 =
      finalize(xml) {
        it.updateString("/data/target_entity", "P-1")
        it.updateInt("/data/canopy_pct", 10)
      }
    val s1 = assertIs<FinalizationResult.Success>(r1).entityStates.single()
    assertEquals("Needs review", s1.properties["status"]?.string_value)
    assertEquals("!", s1.properties["marker-symbol"]?.string_value)
    assertEquals("#F37C22", s1.properties["marker-color"]?.string_value)

    // 2. Entity property matches (notes = 'flagged' on P-2) -> Flagged (✕)
    val r2 =
      finalize(xml) {
        it.updateString("/data/target_entity", "P-2")
        it.updateInt("/data/canopy_pct", 40)
      }
    val s2 = assertIs<FinalizationResult.Success>(r2).entityStates.single()
    assertEquals("Flagged", s2.properties["status"]?.string_value)
    assertEquals("✕", s2.properties["marker-symbol"]?.string_value)
    assertEquals("#D13135", s2.properties["marker-color"]?.string_value)

    // 3. Submission count matches (__version >= 2 on P-3) -> Surveyed (✓)
    val r3 =
      finalize(xml) {
        it.updateString("/data/target_entity", "P-3")
        it.updateInt("/data/canopy_pct", 40)
      }
    val s3 = assertIs<FinalizationResult.Success>(r3).entityStates.single()
    assertEquals("Surveyed", s3.properties["status"]?.string_value)
    assertEquals("✓", s3.properties["marker-symbol"]?.string_value)
    assertEquals("#3C8D40", s3.properties["marker-color"]?.string_value)

    // 4. Fallback ("Otherwise") when no rule matches (P-1 with canopy_pct = 40, __version = 1) ->
    // In progress (◐)
    val r4 =
      finalize(xml) {
        it.updateString("/data/target_entity", "P-1")
        it.updateInt("/data/canopy_pct", 40)
      }
    val s4 = assertIs<FinalizationResult.Success>(r4).entityStates.single()
    assertEquals("In progress", s4.properties["status"]?.string_value)
    assertEquals("◐", s4.properties["marker-symbol"]?.string_value)
    assertEquals("#F9BF40", s4.properties["marker-color"]?.string_value)
  }

  @Test
  fun statusMarker_mutationsAndValidation_stayInSync() {
    val state = stateWith()
    state.setSaveToMode(SaveToMode.UPDATE)
    state.setStatusEnabled(true)
    assertEquals(1, state.ui.form.saveTo.status.rules.size)

    // Adding a rule inserts it before the trailing catch-all submission rule.
    state.addStatusRule()
    assertEquals(2, state.ui.form.saveTo.status.rules.size)
    assertEquals(StatusConditionSubject.QUESTION, state.ui.form.saveTo.status.rules[0].subject)
    assertEquals(StatusConditionSubject.SUBMISSIONS, state.ui.form.saveTo.status.rules[1].subject)

    // Reordering and updating rules works cleanly.
    state.moveStatusRule(0, 1)
    assertEquals(StatusConditionSubject.SUBMISSIONS, state.ui.form.saveTo.status.rules[0].subject)
    state.updateStatusRule(1) {
      it.copy(
        subject = StatusConditionSubject.ENTITY_PROPERTY,
        property = "notes",
        operator = RelevanceOperator.IS_ANSWERED,
      )
    }
    state.renameTargetProperty("plots", "notes", "plot_notes")
    assertEquals("plot_notes", state.ui.form.saveTo.status.rules[1].property)

    // Blank default label is flagged by validator.
    state.updateDefaultStatusBadge { it.copy(label = "") }
    assertTrue(state.ui.issues.any { it.message.contains("default status label") })
  }
}
