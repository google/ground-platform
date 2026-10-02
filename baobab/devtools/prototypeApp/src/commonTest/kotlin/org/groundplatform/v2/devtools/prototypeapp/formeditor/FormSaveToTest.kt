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
  ) = FormEditorState(form) { datasets }

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
    val saveTo = state.form.saveTo
    assertEquals("plots", saveTo.targetDatasetId)
    assertEquals(EntityIdSource.SELECTED_FEATURE, saveTo.idSource)
    assertEquals("geometry", saveTo.propertyFor("q1"))
    assertEquals("canopy_pct", saveTo.propertyFor("q2"))
    assertEquals("notes", saveTo.propertyFor("q3"))
    assertNull(saveTo.propertyFor("q4"))
    assertTrue(state.issues.isEmpty(), state.issues.toString())

    state.setTargetDataset("farmers")
    val table = state.form.saveTo
    assertEquals(EntityIdSource.QUESTION, table.idSource)
    assertEquals("q4", table.idQuestionKey)
    assertEquals("farmer_id", table.idMatchProperty)
    assertEquals(null, table.propertyFor("q1"))
    assertEquals("notes", table.propertyFor("q3"))
    assertTrue(state.issues.isEmpty(), state.issues.toString())
  }

  @Test
  fun updateTargets_excludeLinkedDataset() {
    val state = stateWith(datasets = listOf(plots.copy(isLinkedToThisForm = true), farmers))
    assertEquals(listOf("farmers"), state.updateTargets.map { it.id })
    assertEquals("plots", state.saveTarget?.id)
    state.setSaveToMode(SaveToMode.UPDATE)
    assertEquals("farmers", state.saveTarget?.id)
  }

  @Test
  fun deleteAndRetype_cleanUpSaveToReferences() {
    val state = stateWith()
    state.setSaveToMode(SaveToMode.UPDATE)
    state.setTargetDataset("farmers")
    state.deleteQuestion("q4")
    assertNull(state.form.saveTo.idQuestionKey)

    state.setTargetDataset("plots")
    state.changeType("q1", EditorQuestionType.TEXT)
    assertNull(state.form.saveTo.propertyFor("q1"))
    state.changeType("q3", EditorQuestionType.NOTE)
    assertTrue(state.form.saveTo.mappings.none { it.questionKey == "q3" })
  }

  @Test
  fun selectForm_clearsQuestionSelection() {
    val state = stateWith()
    assertFalse(state.isFormSelected)
    state.selectForm()
    assertTrue(state.isFormSelected)
    assertNull(state.selectedQuestion)
  }

  @Test
  fun startPreview_runsUpdateFormWithEmbeddedFeatures() {
    val state = stateWith()
    state.setSaveToMode(SaveToMode.UPDATE)
    state.startPreview()
    assertNull(state.previewError)
    val controller = state.previewController!!
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
}
