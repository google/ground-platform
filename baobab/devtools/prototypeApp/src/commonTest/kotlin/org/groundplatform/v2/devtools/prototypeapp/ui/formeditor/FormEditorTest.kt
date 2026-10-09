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
package org.groundplatform.v2.devtools.prototypeapp.ui.formeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.CompactFormItem
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.FormWizardStep
import org.groundplatform.v2.core.forms.ui.buildCompactFormItems
import org.groundplatform.v2.devtools.prototypeapp.data.seed.FormEditorSamples
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormAvailability
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ChoiceSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DateRule
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorChoiceImage
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorDatasetProperty
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorDatasetRow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorPropertyKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorRelevance
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorValidation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorXFormsGenerator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FlowEdge
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FlowEdgeKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormEditorValidator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormFlowGraph
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormPreviewTarget
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryCapture
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.MediaSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.RelevanceOperator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.TextPattern
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.ValidationRules
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.friendlyDate
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.isoDateToUtcMillis
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.slugify
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.utcMillisToIsoDate
import org.groundplatform.v2.devtools.prototypeapp.domain.model.hasGeometry
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.formEditorViewModel
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.ui

class FormEditorTest {

  private fun q(key: String, conditional: Boolean = false) =
    EditorQuestion(
      key = key,
      name = key,
      type = EditorQuestionType.TEXT,
      label = key,
      relevance = if (conditional) EditorRelevance("a", RelevanceOperator.IS_ANSWERED) else null,
    )

  @Test
  fun flowGraph_linearFormHasOnlyNextEdges() {
    val form = EditorForm("f", "F", listOf(q("a"), q("b"), q("c")))
    val edges = FormFlowGraph.edges(form)
    assertEquals(4, edges.size)
    assertTrue(edges.all { it.kind == FlowEdgeKind.NEXT && it.span == 1 })
    assertEquals(1L, FormFlowGraph.countPaths(form))
  }

  @Test
  fun flowGraph_conditionalQuestionsAddSkipEdgesAndPaths() {
    // a → (b?) → (c?) → d
    val form = EditorForm("f", "F", listOf(q("a"), q("b", true), q("c", true), q("d")))
    val edges = FormFlowGraph.edges(form).toSet()
    // From slot 1 (a): may show b, may show c, or skip both to d.
    assertTrue(FlowEdge(1, 2, FlowEdgeKind.CONDITIONAL) in edges)
    assertTrue(FlowEdge(1, 3, FlowEdgeKind.CONDITIONAL) in edges)
    assertTrue(FlowEdge(1, 4, FlowEdgeKind.SKIP) in edges)
    // From b: show c or skip to d.
    assertTrue(FlowEdge(2, 3, FlowEdgeKind.CONDITIONAL) in edges)
    assertTrue(FlowEdge(2, 4, FlowEdgeKind.SKIP) in edges)
    assertTrue(FlowEdge(3, 4, FlowEdgeKind.NEXT) in edges)
    assertEquals(4L, FormFlowGraph.countPaths(form))
  }

  @Test
  fun flowGraph_trailingConditionalCanSkipToEnd() {
    val form = EditorForm("f", "F", listOf(q("a"), q("b", true)))
    val edges = FormFlowGraph.edges(form).toSet()
    assertTrue(FlowEdge(1, 3, FlowEdgeKind.SKIP) in edges)
    assertEquals(2L, FormFlowGraph.countPaths(form))
  }

  @Test
  fun validator_flagsDuplicateNamesAndForwardDependencies() {
    val form =
      EditorForm(
        "f",
        "F",
        listOf(
          q("a").copy(relevance = EditorRelevance("b", RelevanceOperator.IS_ANSWERED)),
          q("b"),
          q("c").copy(name = "b"),
        ),
      )
    val issues = FormEditorValidator.validate(form)
    assertTrue(issues.any { it.questionKey == "a" && "later" in it.message })
    assertTrue(issues.any { it.questionKey == "c" && "more than once" in it.message })
  }

  @Test
  fun sampleForm_isValidAndParsesWithFormEngine() {
    val form = FormEditorSamples.shadeTreeVisit()
    assertEquals(emptyList(), FormEditorValidator.validate(form))
    val formDef = XFormsXmlSerializer.deserializeFormDef(EditorXFormsGenerator.toXml(form))
    assertEquals("Shade Tree Farm Visit", formDef.title)
  }

  @Test
  fun generatedXForms_relevanceDrivesPreviewSteps() {
    val form = FormEditorSamples.shadeTreeVisit()
    val controller =
      FormWizardController(
        formDef = XFormsXmlSerializer.deserializeFormDef(EditorXFormsGenerator.toXml(form))
      )
    fun stepKeys() =
      controller.steps.filterIsInstance<FormWizardStep.QuestionStep>().map {
        it.control.canonicalPath.substringAfterLast('/')
      }
    assertFalse("shade_tree_count" in stepKeys())
    controller.updateString("/data/has_shade_trees", "yes")
    assertTrue("shade_tree_count" in stepKeys())
    assertTrue("canopy_photo" in stepKeys())
    controller.updateString("/data/has_shade_trees", "no")
    assertFalse("shade_tree_count" in stepKeys())
  }

  @Test
  fun relevantExpression_escapesAndQuotesValues() {
    val form = FormEditorSamples.shadeTreeVisit()
    val pestNotes = form.questions.first { it.name == "pest_notes" }
    assertEquals(
      "selected(/data/observed_issues, 'pests')",
      EditorXFormsGenerator.relevantExpression(form, pestNotes),
    )
    val count = form.questions.first { it.name == "shade_tree_count" }
    assertEquals(
      "/data/has_shade_trees = 'yes'",
      EditorXFormsGenerator.relevantExpression(form, count),
    )
    assertNull(EditorXFormsGenerator.relevantExpression(form, form.questions.first()))
  }

  @Test
  fun state_addDeleteAndReorderQuestions() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    val initialSize = state.ui.form.questions.size
    state.select("q3")
    state.addQuestion(EditorQuestionType.SELECT_ONE)
    val added = assertNotNull(state.ui.selectedQuestion)
    assertEquals(3, state.ui.selectedIndex)
    assertEquals(initialSize + 1, state.ui.form.questions.size)
    assertEquals(2, added.choices.size)

    state.moveQuestion(added.key, -1)
    assertEquals(2, state.ui.form.indexOf(added.key))

    state.deleteQuestion(added.key)
    assertEquals(initialSize, state.ui.form.questions.size)
    assertEquals(-1, state.ui.form.indexOf(added.key))
  }

  @Test
  fun state_deletingSourceClearsDependentDisplayLogic() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    state.deleteQuestion("q3") // has_shade_trees drives q4 and q5
    assertNull(state.ui.form.find("q4")?.relevance)
    assertNull(state.ui.form.find("q5")?.relevance)
    assertEquals(emptyList(), state.ui.issues)
  }

  @Test
  fun state_renamingChoiceValueUpdatesDependents() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    state.updateChoiceLabel("q3", 0, "Yes, many")
    assertEquals("yes_many", state.ui.form.find("q3")!!.choices[0].value)
    assertEquals("yes_many", state.ui.form.find("q4")!!.relevance!!.value)
  }

  @Test
  fun state_previewStartsForValidForm() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    state.startPreview()
    assertNotNull(state.ui.previewController)
    assertNull(state.ui.previewError)
    state.closePreview()
    assertNull(state.ui.previewController)
  }

  @Test
  fun state_formSettingsSelectionClearsQuestionSelection() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    state.select("q3")
    assertFalse(state.ui.isFormSettingsSelected)
    state.selectFormSettings()
    assertTrue(state.ui.isFormSettingsSelected)
    assertNull(state.ui.selectedQuestion)
    state.updateTitle("Renamed")
    assertEquals("Renamed", state.ui.form.title)
  }

  @Test
  fun state_setChoiceColorValidatesAndClears() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    state.setChoiceColor("q3", 0, "#1a73e8")
    assertEquals("#1A73E8", state.ui.form.find("q3")!!.choices[0].colorHex)
    state.setChoiceColor("q3", 0, "blue")
    assertEquals("#1A73E8", state.ui.form.find("q3")!!.choices[0].colorHex)
    state.setChoiceColor("q3", 0, null)
    assertNull(state.ui.form.find("q3")!!.choices[0].colorHex)
  }

  @Test
  fun state_setChoiceImageEnforcesSizeLimit() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    val small = EditorChoiceImage("image/png", "iVBORw0KGgo=")
    assertTrue(state.setChoiceImage("q3", 1, small))
    assertEquals(small, state.ui.form.find("q3")!!.choices[1].image)

    val tooBigBase64 = "A".repeat(EditorChoiceImage.MAX_BYTES / 3 * 4 + 8)
    val tooBig = EditorChoiceImage("image/png", tooBigBase64)
    assertFalse(state.setChoiceImage("q3", 1, tooBig))
    assertFalse(state.setChoiceImage("q3", 1, EditorChoiceImage("text/plain", "AAAA")))
    assertFalse(state.setChoiceImage("q3", 9, small))
    assertEquals(small, state.ui.form.find("q3")!!.choices[1].image)

    assertTrue(state.setChoiceImage("q3", 1, null))
    assertNull(state.ui.form.find("q3")!!.choices[1].image)
  }

  @Test
  fun state_labelEditsKeepChoiceImageAndColor() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    state.setChoiceColor("q3", 0, "#1E8E3E")
    state.setChoiceImage("q3", 0, EditorChoiceImage("image/png", "iVBORw0KGgo="))
    state.updateChoiceLabel("q3", 0, "Yes, many")
    val choice = state.ui.form.find("q3")!!.choices[0]
    assertEquals("#1E8E3E", choice.colorHex)
    assertNotNull(choice.image)
  }

  private fun shadeTreeStep(controller: FormWizardController) =
    controller.steps.filterIsInstance<FormWizardStep.QuestionStep>().first {
      it.control.canonicalPath.endsWith("/has_shade_trees")
    }

  @Test
  fun xforms_choiceColorIsExportedAsSecondaryInstanceColumn() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    state.setChoiceColor("q3", 0, "#1E8E3E")
    val xml = state.ui.xformsXml

    // pyxform shape: <instance id="list"><root><item><name/><label/><extra/></item></root>.
    assertTrue("""<instance id="has_shade_trees">""" in xml)
    assertTrue("<name>yes</name>" in xml)
    assertTrue("<label>Yes</label>" in xml)
    assertTrue("<color>#1E8E3E</color>" in xml)
    assertTrue("""<itemset nodeset="instance('has_shade_trees')/root/item">""" in xml)
    assertTrue("""<value ref="name"/>""" in xml)
    assertTrue("""<label ref="label"/>""" in xml)
    assertFalse("<itext>" in xml)
    // No Ground-only attributes or namespaces: color exists only as instance data.
    assertFalse("color=" in xml)
    assertEquals(4, Regex("xmlns").findAll(xml).count())

    val formDef = XFormsXmlSerializer.deserializeFormDef(xml)
    assertTrue(formDef.model!!.secondary_instances.any { it.id == "has_shade_trees" })
    state.startPreview()
    assertNull(state.ui.previewError)
    val options = shadeTreeStep(assertNotNull(state.ui.previewController)).control.options
    assertEquals(listOf("yes", "no"), options.map { it.value })
    assertEquals(listOf("Yes", "No"), options.map { it.label.text })
    assertEquals("#1E8E3E", options[0].properties["color"])
    assertNull(options[1].properties["color"])
  }

  @Test
  fun xforms_choiceImagesUseItextIdLikePyxform() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    state.setChoiceImage("q3", 0, EditorChoiceImage("image/jpeg", "/9j/4AAQ"))
    val xml = state.ui.xformsXml

    assertTrue("<itextId>has_shade_trees-0</itextId>" in xml)
    assertTrue("<itextId>has_shade_trees-1</itextId>" in xml)
    assertTrue("""<text id="has_shade_trees-0">""" in xml)
    assertTrue("""<value form="image">jr://images/has_shade_trees-yes.jpg</value>""" in xml)
    assertTrue("""<label ref="jr:itext(itextId)"/>""" in xml)
    assertFalse("<label>Yes</label>" in xml)

    state.startPreview()
    assertNull(state.ui.previewError)
    val options = shadeTreeStep(assertNotNull(state.ui.previewController)).control.options
    assertEquals(listOf("Yes", "No"), options.map { it.label.text })
    assertEquals("jr://images/has_shade_trees-yes.jpg", options[0].label.media?.image_uri)
    assertNull(options[1].label.media?.image_uri?.takeIf { it.isNotEmpty() })
  }

  @Test
  fun xforms_listsWithoutColorOrImageKeepInlineItems() {
    val form = FormEditorSamples.shadeTreeVisit()
    val before = EditorXFormsGenerator.toXml(form)
    assertFalse("<itemset" in before)
    assertFalse("<instance id=" in before)
    assertTrue("<item>" in before)

    // Coloring one list leaves every other list's inline items byte-for-byte unchanged.
    val state = formEditorViewModel(form)
    state.setChoiceColor("q3", 0, "#1E8E3E")
    val after = state.ui.xformsXml
    fun bodyAfterQ3(xml: String) =
      xml.substringAfter("""<select1 ref="/data/has_shade_trees">""").substringAfter("</select1>")
    assertEquals(bodyAfterQ3(before), bodyAfterQ3(after))
  }

  @Test
  fun slugify_producesValidNames() {
    assertEquals("yes_many", slugify("Yes, many!"))
    assertEquals("_2nd_visit", slugify("2nd visit"))
    assertEquals("item", slugify("***"))
    assertTrue(FormEditorValidator.isValidName(slugify("Árbol grande")))
  }

  // Validation rules (Advanced section): XForms bind `constraint` + `jr:constraintMsg`.

  @Test
  fun validation_compilesEachRuleKindToXPathAndSummary() {
    val int = EditorQuestionType.INTEGER
    val range = EditorValidation(min = "0", max = "120")
    assertEquals(". >= 0 and . <= 120", ValidationRules.constraintExpression(int, range))
    assertEquals("Must be between 0 and 120.", ValidationRules.summary(int, range))
    assertEquals(
      ". <= 5.5",
      ValidationRules.constraintExpression(
        EditorQuestionType.DECIMAL,
        EditorValidation(max = "5.5"),
      ),
    )

    val text = EditorValidation(min = "3", max = "40", pattern = TextPattern.DIGITS)
    assertEquals(
      "string-length(.) >= 3 and string-length(.) <= 40 and regex(., '^[0-9]+$')",
      ValidationRules.constraintExpression(EditorQuestionType.TEXT, text),
    )
    assertEquals(
      "Must be 3–40 characters long and contain only digits.",
      ValidationRules.summary(EditorQuestionType.TEXT, text),
    )

    val date = EditorQuestionType.DATE
    assertEquals(
      ". <= today()",
      ValidationRules.constraintExpression(
        date,
        EditorValidation(dateRule = DateRule.NOT_IN_FUTURE),
      ),
    )
    assertEquals(
      ". >= date('2026-01-01') and . <= date('2026-12-31')",
      ValidationRules.constraintExpression(
        date,
        EditorValidation(dateRule = DateRule.BETWEEN, min = "2026-01-01", max = "2026-12-31"),
      ),
    )

    val multi = EditorQuestionType.SELECT_MULTIPLE
    val count = EditorValidation(min = "1")
    assertEquals("count-selected(.) >= 1", ValidationRules.constraintExpression(multi, count))
    assertEquals("Select at least 1 option.", ValidationRules.summary(multi, count))

    // Types without rules and empty rules produce nothing.
    assertNull(ValidationRules.constraintExpression(EditorQuestionType.PHOTO, range))
    assertNull(ValidationRules.constraintExpression(int, EditorValidation()))
    // A custom message replaces the generated one.
    assertEquals(
      "Too many trees",
      ValidationRules.message(int, range.copy(message = " Too many trees ")),
    )
    assertEquals("Must be between 0 and 120.", ValidationRules.message(int, range))
  }

  @Test
  fun validation_badInputsAreValidatorIssues() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    fun issues(key: String) =
      FormEditorValidator.validate(state.ui.form)
        .filter { it.questionKey == key }
        .map { it.message }

    state.updateValidation("q4") { it.copy(min = "10", max = "2") }
    assertTrue("Minimum can't be more than maximum." in issues("q4"))
    state.updateValidation("q4") { it.copy(min = "ten", max = "1e3") }
    assertTrue("Minimum must be a number." in issues("q4"))
    assertTrue("Maximum must be a number." in issues("q4"))
    assertNull(
      ValidationRules.constraintExpression(
        EditorQuestionType.INTEGER,
        state.ui.form.find("q4")!!.validation,
      )
    )

    state.updateValidation("q7") { it.copy(pattern = TextPattern.CUSTOM, customPattern = "[a-") }
    assertTrue("Custom pattern isn't valid." in issues("q7"))

    state.updateValidation("q1") { it.copy(dateRule = DateRule.BETWEEN) }
    assertTrue("Pick an earliest or latest date." in issues("q1"))
    state.updateValidation("q1") { it.copy(min = "2026-12-31", max = "2026-01-01") }
    assertTrue("Earliest date can't be after latest date." in issues("q1"))

    state.updateValidation("q6") { it.copy(min = "5") }
    assertTrue("Minimum number of selections is more than the number of choices." in issues("q6"))

    // Clearing every setting removes the rule entirely.
    state.updateValidation("q6") { EditorValidation() }
    assertNull(state.ui.form.find("q6")!!.validation)
  }

  @Test
  fun validation_changeTypeDropsRulesThatNoLongerApply() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    state.updateValidation("q4") { it.copy(min = "0", max = "120") }
    state.changeType("q4", EditorQuestionType.DECIMAL)
    assertEquals(EditorValidation(min = "0", max = "120"), state.ui.form.find("q4")!!.validation)
    state.changeType("q4", EditorQuestionType.TEXT)
    assertNull(state.ui.form.find("q4")!!.validation)
  }

  @Test
  fun xforms_exportsEscapedConstraintAndMessage() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    state.updateValidation("q4") {
      it.copy(min = "0", max = "120", message = "Count <= 120 & \"real\"")
    }
    val bind =
      state.ui.xformsXml.lines().first { "/data/shade_tree_count\"" in it && "<bind" in it }
    assertTrue("""constraint=". &gt;= 0 and . &lt;= 120"""" in bind, bind)
    assertTrue("""jr:constraintMsg="Count &lt;= 120 &amp; &quot;real&quot;"""" in bind, bind)

    // Without a custom message, the generated summary is exported.
    state.updateValidation("q4") { it.copy(message = "") }
    assertTrue("""jr:constraintMsg="Must be between 0 and 120."""" in state.ui.xformsXml)
    // Questions without rules export no constraint.
    assertEquals(1, Regex("constraint=").findAll(state.ui.xformsXml).count())
  }

  @Test
  fun preview_enforcesNumberRangeConstraint() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    state.updateValidation("q4") { it.copy(min = "0", max = "120") }
    state.startPreview()
    assertNull(state.ui.previewError)
    val controller = assertNotNull(state.ui.previewController)
    val path = "/data/shade_tree_count"
    controller.updateString("/data/has_shade_trees", "yes")
    controller.jumpToField(path)

    controller.updateInt(path, 500)
    val error = controller.currentStepErrors.single()
    assertEquals(path, error.fieldPath)
    assertEquals("Must be between 0 and 120.", error.message)
    assertFalse(controller.nextStep())

    controller.updateInt(path, 50)
    assertTrue(controller.currentStepErrors.isEmpty())
    assertTrue(controller.nextStep())
  }

  @Test
  fun preview_enforcesDateTextAndSelectionConstraints() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    state.updateValidation("q1") { it.copy(dateRule = DateRule.NOT_IN_FUTURE) }
    state.updateValidation("q6") { it.copy(max = "2", message = "Pick up to two") }
    state.updateValidation("q7") { it.copy(min = "5") }
    state.startPreview()
    assertNull(state.ui.previewError)
    val controller = assertNotNull(state.ui.previewController)

    controller.jumpToField("/data/visit_date")
    controller.updateDate("/data/visit_date", 2999, 1, 1)
    assertEquals("Must not be in the future.", controller.currentStepErrors.single().message)
    controller.updateDate("/data/visit_date", 2000, 1, 1)
    assertTrue(controller.currentStepErrors.isEmpty())

    controller.jumpToField("/data/observed_issues")
    controller.updateMultiSelect("/data/observed_issues", listOf("pests", "disease", "erosion"))
    assertEquals("Pick up to two", controller.currentStepErrors.single().message)
    controller.updateMultiSelect("/data/observed_issues", listOf("pests"))
    assertTrue(controller.currentStepErrors.isEmpty())

    controller.jumpToField("/data/pest_notes")
    controller.updateString("/data/pest_notes", "ants")
    assertEquals(
      "Must be at least 5 characters long.",
      controller.currentStepErrors.single().message,
    )
    controller.updateString("/data/pest_notes", "aphids")
    assertTrue(controller.currentStepErrors.isEmpty())
  }

  @Test
  fun dates_convertBetweenIsoAndDatePickerUtcMillis() {
    assertEquals(0L, isoDateToUtcMillis("1970-01-01"))
    assertEquals(1_772_668_800_000L, isoDateToUtcMillis("2026-03-05"))
    assertEquals("2026-03-05", utcMillisToIsoDate(1_772_668_800_000L))
    // Any instant within the UTC day maps back to that day (no time-zone off-by-one).
    assertEquals("2026-03-05", utcMillisToIsoDate(1_772_668_800_000L + 86_399_999L))
    assertEquals("1969-12-31", utcMillisToIsoDate(-1L))
    assertEquals("2024-02-29", utcMillisToIsoDate(isoDateToUtcMillis("2024-02-29")!!))
    assertNull(isoDateToUtcMillis("2026-02-29"))
    assertNull(isoDateToUtcMillis("2026-13-01"))
    assertEquals("Mar 5, 2026", friendlyDate("2026-03-05"))
    assertEquals(
      "Must be on or after Jan 1, 2026.",
      ValidationRules.summary(
        EditorQuestionType.DATE,
        EditorValidation(dateRule = DateRule.BETWEEN, min = "2026-01-01"),
      ),
    )
  }

  @Test
  fun displayLogic_plainLanguageSummaryUsesLabels() {
    val form = FormEditorSamples.shadeTreeVisit()
    val q3 = form.find("q3")!!
    assertEquals(
      "Shown only if \"${q3.label}\" equals Yes.",
      form.relevanceSummary(form.find("q4")!!),
    )
    assertNull(form.relevanceSummary(q3))
  }

  @Test
  fun state_advancedExpansionPersistsAcrossSelection() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    assertFalse(state.ui.isAdvancedExpanded)
    state.setAdvancedExpanded(true)
    state.select("q5")
    assertTrue(state.ui.isAdvancedExpanded)
  }

  @Test
  fun availability_defaultsToMobileOnlyAndTogglesIndependently() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    assertEquals(FormAvailability.MOBILE, state.ui.form.availability)
    assertTrue(state.ui.isEnabledOnPreviewTarget)
    state.selectPreviewTarget(FormPreviewTarget.WEB)
    assertFalse(state.ui.isEnabledOnPreviewTarget)
    state.selectPreviewTarget(FormPreviewTarget.MOBILE)

    // Turning web on makes the form available on both platforms.
    state.updateAvailability(state.ui.form.availability.withWeb(true))
    assertEquals(FormAvailability.BOTH, state.ui.form.availability)

    // Switching mobile off leaves web on; the mobile canvas now shows the banner.
    state.updateAvailability(state.ui.form.availability.withMobile(false))
    assertEquals(FormAvailability.WEB, state.ui.form.availability)
    assertFalse(state.ui.isEnabledOnPreviewTarget)
    state.selectPreviewTarget(FormPreviewTarget.WEB)
    assertTrue(state.ui.isEnabledOnPreviewTarget)

    // Both off is allowed (hidden everywhere); the banner's Enable restores the previewed one.
    state.updateAvailability(state.ui.form.availability.withWeb(false))
    assertEquals(FormAvailability.NONE, state.ui.form.availability)
    assertFalse(state.ui.isEnabledOnPreviewTarget)
    state.enableOnPreviewTarget()
    assertEquals(FormAvailability.WEB, state.ui.form.availability)

    assertEquals(FormAvailability.BOTH, FormAvailability.of(mobile = true, web = true))
    assertEquals(FormAvailability.MOBILE, FormAvailability.NONE.withMobile(true))
  }

  @Test
  fun previewTarget_defaultsToMobileAndToggles() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    assertEquals(FormPreviewTarget.MOBILE, state.ui.previewTarget)
    state.selectPreviewTarget(FormPreviewTarget.WEB)
    assertEquals(FormPreviewTarget.WEB, state.ui.previewTarget)
    // The Preview overlay is driven by the same session regardless of target.
    state.startPreview()
    assertNotNull(state.ui.previewController)
    state.closePreview()
    assertEquals(FormPreviewTarget.WEB, state.ui.previewTarget)
  }

  @Test
  fun webCanvas_parsesCurrentFormAndMapsCardsToQuestions() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    val controller = state.ui.parsePreviewController().getOrThrow()
    val cardPaths =
      buildCompactFormItems(controller.steps).filterIsInstance<CompactFormItem.Question>().map {
        it.step.stepKey
      }

    // Every relevant editor question has a card, and each card maps back to its question.
    val firstKey = state.ui.form.questions.first().key
    val firstPath = assertNotNull(state.ui.pathOf(firstKey))
    assertTrue(firstPath in cardPaths)
    assertEquals(firstKey, state.ui.keyForPath(firstPath))
    assertNull(state.ui.keyForPath("/data/not_a_question"))

    // Edits are reflected after re-parsing.
    state.select(firstKey)
    state.updateQuestion(firstKey) { it.copy(label = "Renamed question") }
    val renamed = state.ui.parsePreviewController().getOrThrow()
    val card =
      buildCompactFormItems(renamed.steps).filterIsInstance<CompactFormItem.Question>().first {
        it.step.stepKey == firstPath
      }
    assertEquals("Renamed question", card.step.title)
  }

  private fun geometryQuestion(
    key: String,
    type: EditorQuestionType,
    capture: GeometryCapture = GeometryCapture.GPS_ONLY,
  ) = EditorQuestion(key = key, name = key, type = type, label = "Where is it?", capture = capture)

  @Test
  fun geometry_lineAndPolygonBindToGeotraceAndGeoshape() {
    val form =
      EditorForm(
        "f",
        "F",
        listOf(
          geometryQuestion("pt", EditorQuestionType.LOCATION),
          geometryQuestion("ln", EditorQuestionType.LINE),
          geometryQuestion("pg", EditorQuestionType.POLYGON),
        ),
      )
    val xml = EditorXFormsGenerator.toXml(form)
    assertTrue("""nodeset="/data/pt" type="geopoint"""" in xml)
    assertTrue("""nodeset="/data/ln" type="geotrace"""" in xml)
    assertTrue("""nodeset="/data/pg" type="geoshape"""" in xml)
    // GPS only is the default and adds no appearance, so existing output is unchanged.
    assertFalse("placement-map" in xml)
    assertTrue(
      EditorQuestionType.entries.filter { it.isGeometry }.all { it.bindType.startsWith("geo") }
    )
    assertTrue(form.hasGeometry)
    assertEquals("pt", form.primaryGeometryQuestion?.key)
    // The generated XForms load in the form engine.
    XFormsXmlSerializer.deserializeFormDef(xml)
  }

  @Test
  fun geometry_isGeometryDrivesHasGeometryAndRelevanceOperators() {
    val lineOnly =
      EditorForm("f", "F", listOf(q("a"), geometryQuestion("ln", EditorQuestionType.LINE)))
    assertTrue(lineOnly.hasGeometry)
    assertEquals("ln", lineOnly.primaryGeometryQuestion?.key)
    assertFalse(EditorForm("f", "F", listOf(q("a"))).hasGeometry)
    EditorQuestionType.entries
      .filter { it.isGeometry }
      .forEach {
        assertEquals(listOf(RelevanceOperator.IS_ANSWERED), RelevanceOperator.availableFor(it))
      }
  }

  @Test
  fun geometry_gpsOrMapCaptureEmitsPlacementMapAppearance() {
    val form =
      EditorForm(
        "f",
        "F",
        listOf(
          geometryQuestion("pt", EditorQuestionType.LOCATION, GeometryCapture.GPS_OR_MAP),
          geometryQuestion("pg", EditorQuestionType.POLYGON, GeometryCapture.GPS_OR_MAP),
        ),
      )
    val xml = EditorXFormsGenerator.toXml(form)
    assertTrue("""<input ref="/data/pt" appearance="placement-map">""" in xml)
    assertTrue("""<input ref="/data/pg" appearance="placement-map">""" in xml)
    XFormsXmlSerializer.deserializeFormDef(xml)
    // Capture is ignored for non-geometry types.
    val text = q("a").copy(capture = GeometryCapture.GPS_OR_MAP)
    assertFalse("placement-map" in EditorXFormsGenerator.toXml(EditorForm("f", "F", listOf(text))))
    assertNull(EditorXFormsGenerator.bodyAppearance(text))
  }

  @Test
  fun geometry_gpsOnlyIsAnErrorOnlyWhenAvailableOnWeb() {
    val mobileOnly =
      EditorForm("f", "F", listOf(q("a"), geometryQuestion("pt", EditorQuestionType.LOCATION)))
    assertTrue(mobileOnly.webIncompatibleGeometryQuestions().isEmpty())
    assertTrue(FormEditorValidator.validate(mobileOnly).isEmpty())

    val onWeb = mobileOnly.copy(availability = FormAvailability.BOTH)
    assertEquals(listOf("pt"), onWeb.webIncompatibleGeometryQuestions().map { it.key })
    val issue = FormEditorValidator.validate(onWeb).single()
    assertEquals("pt", issue.questionKey)
    assertTrue("GPS only" in issue.message && "Where is it?" in issue.message, issue.message)

    // GPS or draw on map is fine on web.
    val drawable =
      onWeb.copy(questions = onWeb.questions.map { it.copy(capture = GeometryCapture.GPS_OR_MAP) })
    assertTrue(FormEditorValidator.validate(drawable).isEmpty())
  }

  @Test
  fun state_makeGeometryQuestionsWebCompatibleClearsTheError() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    val geometryKey = assertNotNull(state.ui.form.primaryGeometryQuestion).key
    assertTrue(state.ui.issues.isEmpty())

    state.updateAvailability(state.ui.form.availability.withWeb(true))
    assertEquals(listOf(geometryKey), state.ui.webIncompatibleGeometryQuestions.map { it.key })
    assertEquals(1, state.ui.issuesFor(geometryKey).size)

    state.makeGeometryQuestionsWebCompatible()
    assertTrue(state.ui.webIncompatibleGeometryQuestions.isEmpty())
    assertTrue(state.ui.issues.isEmpty())
    assertEquals(GeometryCapture.GPS_OR_MAP, state.ui.form.find(geometryKey)?.capture)
    assertTrue("placement-map" in state.ui.xformsXml)

    // updateCapture round-trips and the error returns while web stays on.
    state.updateCapture(geometryKey, GeometryCapture.GPS_ONLY)
    assertEquals(1, state.ui.issuesFor(geometryKey).size)
    state.updateAvailability(state.ui.form.availability.withWeb(false))
    assertTrue(state.ui.issues.isEmpty())
  }

  @Test
  fun state_changeTypeBetweenGeometryTypesKeepsGeometryMapping() {
    val state = formEditorViewModel(FormEditorSamples.shadeTreeVisit())
    val key = assertNotNull(state.ui.form.primaryGeometryQuestion).key
    state.changeType(key, EditorQuestionType.POLYGON)
    assertEquals(EditorQuestionType.POLYGON, state.ui.form.find(key)?.type)
    assertEquals(key, state.ui.form.primaryGeometryQuestion?.key)
    assertTrue("geoshape" in state.ui.xformsXml)
  }

  private fun mediaQuestion(
    key: String,
    type: EditorQuestionType,
    source: MediaSource = MediaSource.CAPTURE_OR_UPLOAD,
  ) = EditorQuestion(key = key, name = key, type = type, label = "Show it", mediaSource = source)

  @Test
  fun media_photoVideoAndAudioBindToBinaryUploads() {
    val form =
      EditorForm(
        "f",
        "F",
        listOf(
          mediaQuestion("ph", EditorQuestionType.PHOTO),
          mediaQuestion("vd", EditorQuestionType.VIDEO),
          mediaQuestion("au", EditorQuestionType.AUDIO),
        ),
      )
    val xml = EditorXFormsGenerator.toXml(form)
    assertTrue("""nodeset="/data/ph" type="binary"""" in xml)
    assertTrue("""nodeset="/data/vd" type="binary"""" in xml)
    assertTrue("""nodeset="/data/au" type="binary"""" in xml)
    assertTrue("""<upload ref="/data/ph" mediatype="image/*">""" in xml)
    assertTrue("""<upload ref="/data/vd" mediatype="video/*">""" in xml)
    assertTrue("""<upload ref="/data/au" mediatype="audio/*">""" in xml)
    // Capture or upload is the ODK default and adds no appearance, so existing output is unchanged.
    assertFalse("appearance" in xml)
    assertEquals(
      setOf(EditorQuestionType.PHOTO, EditorQuestionType.VIDEO, EditorQuestionType.AUDIO),
      EditorQuestionType.entries.filter { it.isMedia }.toSet(),
    )
    assertTrue(EditorQuestionType.entries.filter { it.isMedia }.all { it.bindType == "binary" })
    XFormsXmlSerializer.deserializeFormDef(xml)
  }

  @Test
  fun media_captureOnlyEmitsNewAppearance() {
    val form =
      EditorForm(
        "f",
        "F",
        listOf(
          mediaQuestion("ph", EditorQuestionType.PHOTO, MediaSource.CAPTURE_ONLY),
          mediaQuestion("vd", EditorQuestionType.VIDEO, MediaSource.CAPTURE_ONLY),
          mediaQuestion("au", EditorQuestionType.AUDIO),
        ),
      )
    val xml = EditorXFormsGenerator.toXml(form)
    assertTrue("""<upload ref="/data/ph" appearance="new" mediatype="image/*">""" in xml)
    assertTrue("""<upload ref="/data/vd" appearance="new" mediatype="video/*">""" in xml)
    assertTrue("""<upload ref="/data/au" mediatype="audio/*">""" in xml)
    XFormsXmlSerializer.deserializeFormDef(xml)
    // Media source is ignored for non-media types.
    val text = q("a").copy(mediaSource = MediaSource.CAPTURE_ONLY)
    assertNull(EditorXFormsGenerator.bodyAppearance(text))
    assertFalse("appearance" in EditorXFormsGenerator.toXml(EditorForm("f", "F", listOf(text))))
  }

  @Test
  fun media_captureOnlyVideoRendersInPreview() {
    val state =
      formEditorViewModel(
        EditorForm(
          "f",
          "F",
          listOf(q("a"), mediaQuestion("vd", EditorQuestionType.VIDEO, MediaSource.CAPTURE_ONLY)),
        )
      )
    assertTrue(state.ui.issues.isEmpty())
    val controller = state.ui.parsePreviewController().getOrThrow()
    val path = assertNotNull(state.ui.pathOf("vd"))
    assertTrue(
      buildCompactFormItems(controller.steps).filterIsInstance<CompactFormItem.Question>().any {
        it.step.stepKey == path
      }
    )
  }

  @Test
  fun state_changeTypeKeepsMediaSourceOnlyBetweenMediaTypes() {
    val state =
      formEditorViewModel(
        EditorForm("f", "F", listOf(mediaQuestion("m", EditorQuestionType.PHOTO)))
      )
    state.updateMediaSource("m", MediaSource.CAPTURE_ONLY)
    assertTrue("""appearance="new"""" in state.ui.xformsXml)

    state.changeType("m", EditorQuestionType.VIDEO)
    assertEquals(MediaSource.CAPTURE_ONLY, state.ui.form.find("m")?.mediaSource)
    assertTrue("""mediatype="video/*"""" in state.ui.xformsXml)
    assertTrue("""appearance="new"""" in state.ui.xformsXml)

    state.changeType("m", EditorQuestionType.TEXT)
    assertEquals(MediaSource.CAPTURE_OR_UPLOAD, state.ui.form.find("m")?.mediaSource)
    assertFalse("appearance" in state.ui.xformsXml)
  }

  @Test
  fun choiceSource_switchesBetweenManualAndDatasetAndExportsItemset() {
    val plotsDataset =
      EditorDataset(
        id = "sample_plots",
        displayName = "Sample plots",
        isMapLayer = true,
        keyProperty = "plot_id",
        labelProperty = "plot_name",
        properties =
          listOf(
            EditorDatasetProperty("plot_id", "Plot ID", EditorPropertyKind.TEXT),
            EditorDatasetProperty("plot_name", "Plot name", EditorPropertyKind.TEXT),
          ),
        rows =
          listOf(
            EditorDatasetRow("P-01", "Plot 01 — Ridge"),
            EditorDatasetRow("P-02", "Plot 02 — Valley"),
          ),
      )
    val speciesDataset =
      EditorDataset(
        id = "tree_species",
        displayName = "Tree species",
        isMapLayer = false,
        keyProperty = "code",
        labelProperty = "common_name",
        properties =
          listOf(
            EditorDatasetProperty("code", "Code", EditorPropertyKind.TEXT),
            EditorDatasetProperty("common_name", "Common name", EditorPropertyKind.TEXT),
          ),
        rows =
          listOf(
            EditorDatasetRow("grevillea", "Silky oak"),
            EditorDatasetRow("cordia", "Cordia"),
          ),
      )
    val state =
      formEditorViewModel(
        form = FormEditorSamples.shadeTreeVisit(),
        datasets = listOf(plotsDataset, speciesDataset),
      )

    // Switch q3 (SELECT_ONE) to Map layer and q6 (SELECT_MULTIPLE) to Data table.
    state.setChoiceSource("q3", ChoiceSource.MAP_LAYER)
    assertEquals(ChoiceSource.MAP_LAYER, state.ui.form.find("q3")?.choiceSource)
    assertEquals("sample_plots", state.ui.form.find("q3")?.choiceDatasetId)
    assertEquals(listOf(plotsDataset), state.ui.choiceDatasetsFor(ChoiceSource.MAP_LAYER))

    state.setChoiceSource("q6", ChoiceSource.DATA_TABLE)
    assertEquals(ChoiceSource.DATA_TABLE, state.ui.form.find("q6")?.choiceSource)
    assertEquals("tree_species", state.ui.form.find("q6")?.choiceDatasetId)
    assertEquals(listOf(speciesDataset), state.ui.choiceDatasetsFor(ChoiceSource.DATA_TABLE))

    // Exported XML has external secondary instances and itemset controls.
    val xml = state.ui.xformsXml
    assertTrue("""<instance id="sample_plots" src="jr://file-csv/sample_plots.csv"/>""" in xml, xml)
    assertTrue("""<instance id="tree_species" src="jr://file-csv/tree_species.csv"/>""" in xml, xml)
    assertTrue("""<itemset nodeset="instance('sample_plots')/root/item">""" in xml, xml)
    assertTrue("""<itemset nodeset="instance('tree_species')/root/item">""" in xml, xml)

    // Preview XML embeds dataset rows so the form engine can evaluate itemset options.
    state.startPreview()
    assertNull(state.ui.previewError)
    assertNotNull(state.ui.previewController)

    // Renaming a dataset updates choiceDatasetId on questions referencing it.
    state.renameTargetDataset("tree_species", "shade_species")
    assertEquals("shade_species", state.ui.form.find("q6")?.choiceDatasetId)

    // Switching back to manual clears choiceDatasetId.
    state.setChoiceSource("q3", ChoiceSource.MANUAL)
    assertEquals(ChoiceSource.MANUAL, state.ui.form.find("q3")?.choiceSource)
    assertNull(state.ui.form.find("q3")?.choiceDatasetId)
  }

  @Test
  fun allowAddEntity_emitsAppearanceAndValidatesDatasetCreationForm() {
    val unlinkedDataset =
      EditorDataset(
        id = "tree_species",
        displayName = "Tree species",
        isMapLayer = false,
        keyProperty = "code",
        labelProperty = "common_name",
        properties = emptyList(),
        rows = listOf(EditorDatasetRow("grevillea", "Silky oak")),
        key = "d_species",
        linkedFormKey = null,
      )
    val linkedDataset =
      unlinkedDataset.copy(
        linkedFormKey = "f_species",
        linkedFormTitle = "Tree species form",
      )
    val generatedPlots =
      EditorDataset(
        id = "sample_plots",
        displayName = "Sample plots",
        isMapLayer = true,
        keyProperty = "plot_id",
        labelProperty = "plot_id",
        properties = emptyList(),
        key = "d_plots",
        isGenerated = true,
      )

    // With a dataset that has a creation form, allowAddEntity is valid and emits
    // appearance="add-entity".
    val state =
      formEditorViewModel(
        form = FormEditorSamples.shadeTreeVisit(),
        datasets = listOf(linkedDataset, generatedPlots),
      )
    state.setChoiceSource("q3", ChoiceSource.DATA_TABLE)
    state.setAllowAddEntity("q3", true)
    assertTrue(state.ui.form.find("q3")?.allowAddEntity == true)
    assertTrue(state.ui.issues.isEmpty())
    assertTrue(
      """<select1 ref="/data/has_shade_trees" appearance="add-entity">""" in state.ui.xformsXml
    )

    // Switching to a generated dataset clears allowAddEntity; if forced on, validator reports an
    // issue.
    state.setChoiceSource("q3", ChoiceSource.MAP_LAYER)
    assertEquals(false, state.ui.form.find("q3")?.allowAddEntity)
    state.setAllowAddEntity("q3", true)
    assertTrue(
      state.ui.issues.any { it.questionKey == "q3" && "generated and can't be added" in it.message }
    )

    // If the dataset has no creation form, validator reports an issue.
    val unlinkedForm =
      state.ui.form.copy(
        questions =
          state.ui.form.questions.map {
            if (it.key == "q3") {
              it.copy(
                choiceSource = ChoiceSource.DATA_TABLE,
                choiceDatasetId = "tree_species",
                allowAddEntity = true,
              )
            } else {
              it
            }
          }
      )
    val unlinkedIssues = FormEditorValidator.validate(unlinkedForm, listOf(unlinkedDataset))
    assertTrue(
      unlinkedIssues.any {
        it.questionKey == "q3" && "has no form to add new table rows" in it.message
      }
    )

    // Switching back to Manual list clears allowAddEntity.
    state.setChoiceSource("q3", ChoiceSource.MANUAL)
    assertEquals(false, state.ui.form.find("q3")?.allowAddEntity)
  }
}
