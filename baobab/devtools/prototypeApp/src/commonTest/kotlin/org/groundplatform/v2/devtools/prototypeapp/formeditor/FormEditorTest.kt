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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.FormWizardStep

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
    val state = FormEditorState()
    val initialSize = state.form.questions.size
    state.select("q3")
    state.addQuestion(EditorQuestionType.SELECT_ONE)
    val added = assertNotNull(state.selectedQuestion)
    assertEquals(3, state.selectedIndex)
    assertEquals(initialSize + 1, state.form.questions.size)
    assertEquals(2, added.choices.size)

    state.moveQuestion(added.key, -1)
    assertEquals(2, state.form.indexOf(added.key))

    state.deleteQuestion(added.key)
    assertEquals(initialSize, state.form.questions.size)
    assertEquals(-1, state.form.indexOf(added.key))
  }

  @Test
  fun state_deletingSourceClearsDependentDisplayLogic() {
    val state = FormEditorState()
    state.deleteQuestion("q3") // has_shade_trees drives q4 and q5
    assertNull(state.form.find("q4")?.relevance)
    assertNull(state.form.find("q5")?.relevance)
    assertEquals(emptyList(), state.issues)
  }

  @Test
  fun state_renamingChoiceValueUpdatesDependents() {
    val state = FormEditorState()
    state.updateChoiceLabel("q3", 0, "Yes, many")
    assertEquals("yes_many", state.form.find("q3")!!.choices[0].value)
    assertEquals("yes_many", state.form.find("q4")!!.relevance!!.value)
  }

  @Test
  fun state_previewStartsForValidForm() {
    val state = FormEditorState()
    state.startPreview()
    assertNotNull(state.previewController)
    assertNull(state.previewError)
    state.closePreview()
    assertNull(state.previewController)
  }

  @Test
  fun slugify_producesValidNames() {
    assertEquals("yes_many", slugify("Yes, many!"))
    assertEquals("_2nd_visit", slugify("2nd visit"))
    assertEquals("item", slugify("***"))
    assertTrue(FormEditorValidator.isValidName(slugify("Árbol grande")))
  }
}
