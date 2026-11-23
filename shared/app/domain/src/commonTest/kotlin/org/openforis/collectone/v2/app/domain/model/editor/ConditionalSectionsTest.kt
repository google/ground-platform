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
import org.openforis.collectone.v2.app.domain.model.ConceptLink
import org.openforis.collectone.v2.app.domain.model.messageKey
import org.openforis.collectone.v2.core.forms.engine.FormSession
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer

/**
 * Singleton and repeated sections whose display logic shows or hides all their questions together,
 * from the editor model through generated XForms, the form engine, and re-import.
 */
class ConditionalSectionsTest {
  private val yesNo = listOf(EditorChoice("yes", "Yes"), EditorChoice("no", "No"))

  private fun question(
    key: String,
    name: String = key,
    type: EditorQuestionType = EditorQuestionType.TEXT,
    section: String = "",
    relevance: EditorRelevance? = null,
    required: Boolean = false,
  ) =
    EditorQuestion(
      key = key,
      name = name,
      type = type,
      label = "Label $key",
      choices = if (type.hasChoices) yesNo else emptyList(),
      sectionId = section,
      relevance = relevance,
      required = required,
    )

  /**
   * `has_trees` → section `trees` (shown if `has_trees = yes`) with `tree_kind` and `tree_notes`
   * (shown if `tree_kind = yes`) → `after` (shown if `tree_kind` is answered).
   */
  private fun treesForm(repeatable: Boolean = false) =
    EditorForm(
      formId = "trees_form",
      title = "Trees",
      questions =
        listOf(
          question("q1", "has_trees", EditorQuestionType.SELECT_ONE),
          question("q2", "tree_kind", EditorQuestionType.SELECT_ONE, section = "trees"),
          question(
            "q3",
            "tree_notes",
            section = "trees",
            relevance = EditorRelevance("q2", RelevanceOperator.EQUALS, "yes"),
            required = true,
          ),
          question("q4", "after", relevance = EditorRelevance("q2", RelevanceOperator.IS_ANSWERED)),
        ),
      sections =
        listOf(
          EditorSection(
            id = "trees",
            title = "Trees",
            repeatable = repeatable,
            relevance = EditorRelevance("q1", RelevanceOperator.EQUALS, "yes"),
          )
        ),
    )

  private fun session(form: EditorForm) =
    FormSession(XFormsXmlSerializer.deserializeFormDef(EditorXFormsGenerator.toXml(form)))

  private fun FormSession.isRelevant(path: String): Boolean? = state.fieldStates[path]?.isRelevant

  // --- Model ---

  @Test
  fun instancePath_includesTheSection() {
    val form = treesForm()
    assertEquals("/data/has_trees", form.instancePath(form.find("q1")!!))
    assertEquals("/data/trees/tree_kind", form.instancePath(form.find("q2")!!))
    assertEquals("trees/tree_kind", form.relativePath(form.find("q2")!!))
  }

  @Test
  fun isShownConditionally_coversSectionDisplayLogic() {
    val form = treesForm()
    assertFalse(form.isShownConditionally(form.find("q1")!!))
    // q2 has no display logic of its own, but its section does.
    assertTrue(form.isShownConditionally(form.find("q2")!!))
    assertTrue(form.isShownConditionally(form.find("q4")!!))
  }

  @Test
  fun flowGraph_questionsOfConditionalSectionAreConditional() {
    val form = treesForm()
    val edges = FormFlowGraph.edges(form)
    // Start → has_trees is unconditional; has_trees → tree_kind is conditional (section logic),
    // and has_trees can skip the whole section.
    assertTrue(FlowEdge(1, 2, FlowEdgeKind.CONDITIONAL) in edges)
    assertTrue(edges.any { it.from == 1 && it.kind == FlowEdgeKind.SKIP })
  }

  @Test
  fun eligibleRelevanceSources_excludeOtherRepeatedSections() {
    val form = treesForm(repeatable = true)
    // Inside the repeat, siblings in the same entry are fine.
    assertEquals(listOf("q1", "q2"), form.eligibleRelevanceSources("q3").map { it.key })
    // Outside it, a repeated answer is ambiguous.
    assertEquals(listOf("q1"), form.eligibleRelevanceSources("q4").map { it.key })
    // A singleton section's questions can drive later display logic.
    assertEquals(
      listOf("q1", "q2", "q3"),
      treesForm().eligibleRelevanceSources("q4").map { it.key },
    )
  }

  @Test
  fun eligibleSectionRelevanceSources_onlyEarlierQuestionsOutsideTheSection() {
    val form =
      treesForm().let { f ->
        f.copy(questions = listOf(question("n0", type = EditorQuestionType.NOTE)) + f.questions)
      }
    assertEquals(listOf("q1"), form.eligibleSectionRelevanceSources("trees").map { it.key })
    // An empty section may depend on any question outside it.
    val empty = form.copy(sections = form.sections + EditorSection("empty", "Empty"))
    assertEquals(
      listOf("q1", "q2", "q3", "q4"),
      empty.eligibleSectionRelevanceSources("empty").map { it.key },
    )
  }

  @Test
  fun relevanceSummary_describesSectionDisplayLogic() {
    val form = treesForm()
    val summary = assertNotNull(form.relevanceSummary(form.sections.single()))
    assertEquals(DisplayLogicMessage.SHOWN_IF, summary.messageKey)
    assertNull(form.relevanceSummary(EditorSection("s", "S")))
  }

  // --- XForms generation ---

  @Test
  fun xml_conditionalSingletonSection_bindsTheGroupNodeOnce() {
    val xml = EditorXFormsGenerator.toXml(treesForm())
    assertTrue("<bind nodeset=\"/data/trees\" relevant=\"/data/has_trees = 'yes'\"/>" in xml, xml)
    assertTrue("<group ref=\"/data/trees\"" in xml, xml)
    assertTrue("nodeset=\"/data/trees/tree_notes\"" in xml, xml)
    assertFalse("/data/data/" in xml, xml)
    // The questions inside don't repeat the section's logic: they inherit it.
    val bindings = EditorXFormsGenerator.compile(treesForm()).proto.model!!.bindings
    assertEquals(
      listOf("trees"),
      bindings.filter { "has_trees" in it.relevant_expression }.map { it.field_path },
    )
  }

  @Test
  fun relevantExpression_singletonSectionSource_usesItsFullPath() {
    val form = treesForm()
    assertEquals(
      "/data/trees/tree_kind = 'yes'",
      EditorXFormsGenerator.relevantExpression(form, form.find("q3")!!),
    )
    assertEquals(
      "string-length(/data/trees/tree_kind) > 0",
      EditorXFormsGenerator.relevantExpression(form, form.find("q4")!!),
    )
  }

  @Test
  fun relevantExpression_sameRepeatSource_isRelativeToTheEntry() {
    val form = treesForm(repeatable = true)
    assertEquals(
      "../tree_kind = 'yes'",
      EditorXFormsGenerator.relevantExpression(form, form.find("q3")!!),
    )
    assertEquals(
      "/data/has_trees = 'yes'",
      EditorXFormsGenerator.relevantExpression(form, form.sections.single()),
    )
  }

  @Test
  fun relevantExpression_sectionSourceInsideOrAfterIt_isDropped() {
    val form = treesForm()
    val inside =
      form.sections.single().copy(relevance = EditorRelevance("q2", RelevanceOperator.IS_ANSWERED))
    assertNull(EditorXFormsGenerator.relevantExpression(form, inside))
    val after =
      form.sections.single().copy(relevance = EditorRelevance("q4", RelevanceOperator.IS_ANSWERED))
    assertNull(EditorXFormsGenerator.relevantExpression(form, after))
  }

  @Test
  fun compile_nonContiguousSection_isEmittedOnceAtItsFirstQuestion() {
    val form =
      treesForm().let { f ->
        f.copy(questions = listOf(f.questions[1], f.questions[0], f.questions[2], f.questions[3]))
      }
    val components = EditorXFormsGenerator.compile(form).proto.view!!.components
    assertEquals(1, components.count { it.group?.field_ref == "trees" })
    assertEquals(2, components.single { it.group != null }.group!!.components.size)
  }

  // --- Form engine ---

  @Test
  fun engine_singletonSection_showsAndHidesAllItsQuestionsTogether() {
    val session = session(treesForm())
    assertEquals(false, session.isRelevant("/data/trees"))
    assertEquals(false, session.isRelevant("/data/trees/tree_kind"))

    session.updateString("/data/has_trees", "yes")
    assertEquals(true, session.isRelevant("/data/trees/tree_kind"))
    assertEquals(false, session.isRelevant("/data/trees/tree_notes"))
    assertEquals(false, session.isRelevant("/data/after"))

    session.updateString("/data/trees/tree_kind", "yes")
    assertEquals(true, session.isRelevant("/data/trees/tree_notes"))
    assertEquals(true, session.isRelevant("/data/after"))

    // Hiding the section hides everything in it, and what depends on it.
    session.updateString("/data/has_trees", "no")
    assertEquals(false, session.isRelevant("/data/trees/tree_kind"))
    assertEquals(false, session.isRelevant("/data/trees/tree_notes"))
    assertEquals(false, session.isRelevant("/data/after"))
    // A required question in a hidden section doesn't block submission.
    assertTrue(
      session.state.validationErrors.none { "tree_notes" in it.fieldPath },
      "${session.state.validationErrors}",
    )
  }

  @Test
  fun engine_repeatedSection_evaluatesDisplayLogicPerEntry() {
    val session = session(treesForm(repeatable = true))
    session.updateString("/data/has_trees", "yes")
    session.addRepeatInstance("/data/trees")
    session.updateString("/data/trees[1]/tree_kind", "yes")
    session.updateString("/data/trees[2]/tree_kind", "no")
    assertEquals(true, session.isRelevant("/data/trees[1]/tree_notes"))
    assertEquals(false, session.isRelevant("/data/trees[2]/tree_notes"))

    session.updateString("/data/has_trees", "no")
    assertTrue(session.isRelevant("/data/trees[1]/tree_kind") != true)
    assertTrue(session.isRelevant("/data/trees[2]/tree_notes") != true)
  }

  // --- Re-import ---

  @Test
  fun xmlRoundTrip_keepsSectionAndQuestionDisplayLogic() {
    val form = treesForm()
    val imported = assertNotNull(FormImport.fromXml(EditorXFormsGenerator.toXml(form))).form
    assertSectionsKept(imported)
  }

  @Test
  fun formDefinitionRoundTrip_keepsSectionAndQuestionDisplayLogic() {
    val imported =
      assertNotNull(FormImport.fromFormDefinition(EditorXFormsGenerator.compile(treesForm()))).form
    assertSectionsKept(imported)
    assertTrue(imported.questions.single { it.name == "tree_notes" }.required)
  }

  @Test
  fun formDefinitionImport_legacyAbsoluteSectionPaths_keepsBindings() {
    // Earlier editor versions saved sectioned paths as `/data/<section>/<name>`.
    val proto = EditorXFormsGenerator.compile(treesForm()).proto
    fun absolute(path: String) = if ('/' in path) "/data/$path" else path
    val legacy =
      proto.copy(
        model =
          proto.model!!.copy(
            bindings =
              proto.model!!.bindings.map {
                it.copy(
                  field_path =
                    if (it.field_path == "trees") "/data/trees" else absolute(it.field_path)
                )
              }
          ),
        view =
          proto.view!!.copy(
            components =
              proto.view!!.components.map { c ->
                val group = c.group ?: return@map c
                c.copy(
                  group =
                    group.copy(
                      field_ref = "/data/trees",
                      components =
                        group.components.map {
                          it.copy(
                            control =
                              it.control!!.copy(field_ref = absolute(it.control!!.field_ref))
                          )
                        },
                    )
                )
              }
          ),
      )
    val imported = FormImport.fromFormDef(legacy).form
    assertSectionsKept(imported)
    assertTrue(imported.questions.single { it.name == "tree_notes" }.required)
  }

  private fun assertSectionsKept(imported: EditorForm) {
    val section = imported.sections.single()
    assertEquals("trees", section.id)
    val hasTrees = imported.questions.single { it.name == "has_trees" }
    assertEquals(EditorRelevance(hasTrees.key, RelevanceOperator.EQUALS, "yes"), section.relevance)
    val treeKind = imported.questions.single { it.name == "tree_kind" }
    assertEquals("trees", treeKind.sectionId)
    val notes = imported.questions.single { it.name == "tree_notes" }
    assertEquals(EditorRelevance(treeKind.key, RelevanceOperator.EQUALS, "yes"), notes.relevance)
    val after = imported.questions.single { it.name == "after" }
    assertEquals("", after.sectionId)
    assertEquals(treeKind.key, after.relevance?.sourceQuestionKey)
  }

  // --- Validation ---

  @Test
  fun validate_validConditionalSections_hasNoIssues() {
    assertEquals(emptyList(), FormEditorValidator.validate(treesForm()))
    // q4 refers into the repeat, so only drop that for the repeated variant.
    val repeated = treesForm(repeatable = true)
    val fixed =
      repeated.copy(
        questions = repeated.questions.map { if (it.key == "q4") it.copy(relevance = null) else it }
      )
    assertEquals(emptyList(), FormEditorValidator.validate(fixed))
  }

  @Test
  fun validate_sectionDependingOnItsOwnQuestion() {
    val form =
      treesForm().let { f ->
        f.copy(
          sections =
            f.sections.map {
              it.copy(relevance = EditorRelevance("q2", RelevanceOperator.IS_ANSWERED))
            }
        )
      }
    val issue = FormEditorValidator.validate(form).single()
    assertEquals("trees", issue.sectionId)
    assertNull(issue.questionKey)
    assertEquals(EditorIssueMessage.SECTION_DISPLAY_LOGIC_SOURCE_INSIDE, issue.message.messageKey)
  }

  @Test
  fun validate_sectionDependingOnLaterOrDeletedQuestion() {
    fun issueFor(source: String): EditorIssue =
      treesForm()
        .let { f ->
          f.copy(
            sections =
              f.sections.map {
                it.copy(relevance = EditorRelevance(source, RelevanceOperator.IS_ANSWERED))
              }
          )
        }
        .let { FormEditorValidator.validate(it).single() }
    assertEquals(EditorIssueMessage.DISPLAY_LOGIC_SOURCE_LATER, issueFor("q4").message.messageKey)
    assertEquals(
      EditorIssueMessage.DISPLAY_LOGIC_SOURCE_DELETED,
      issueFor("gone").message.messageKey,
    )
  }

  @Test
  fun validate_sectionValueMissing() {
    val form =
      treesForm().let { f ->
        f.copy(
          sections =
            f.sections.map {
              it.copy(relevance = EditorRelevance("q1", RelevanceOperator.EQUALS, ""))
            }
        )
      }
    assertEquals(
      EditorIssueMessage.DISPLAY_LOGIC_VALUE_MISSING,
      FormEditorValidator.validate(form).single().message.messageKey,
    )
  }

  @Test
  fun validate_displayLogicOutsideRepeatDependingOnRepeatedQuestion() {
    val issue = FormEditorValidator.validate(treesForm(repeatable = true)).single()
    assertEquals("q4", issue.questionKey)
    assertEquals(EditorIssueMessage.DISPLAY_LOGIC_SOURCE_IN_REPEAT, issue.message.messageKey)
  }

  @Test
  fun validate_questionNamedLikeASection() {
    val form =
      treesForm().let { f ->
        f.copy(questions = f.questions.map { if (it.key == "q4") it.copy(name = "trees") else it })
      }
    val issue = FormEditorValidator.validate(form).single()
    assertEquals("q4", issue.questionKey)
    assertEquals(EditorIssueMessage.NAME_DUPLICATE, issue.message.messageKey)
  }

  @Test
  fun describeRelevance_combinesSectionAndQuestionConditions() {
    val form = treesForm()
    assertNull(form.describeRelevance(form.find("q1")!!))
    assertEquals("if has_trees = Yes", form.describeRelevance(form.find("q2")!!))
    assertEquals("if has_trees = Yes, tree_kind = Yes", form.describeRelevance(form.find("q3")!!))
    assertEquals("if has_trees = Yes", form.describeRelevance(form.sections.single()))
  }

  @Test
  fun emptyConditionalSection_omitsOrphanGroupBindAndRunsInFormEngine() {
    val form =
      EditorForm(
        formId = "empty_sec",
        title = "Empty Section",
        questions = listOf(question("q1", "has_trees", EditorQuestionType.SELECT_ONE)),
        sections =
          listOf(
            EditorSection(
              id = "trees",
              title = "Trees",
              repeatable = true,
              relevance = EditorRelevance("q1", RelevanceOperator.EQUALS, "yes"),
            )
          ),
      )
    val xml = EditorXFormsGenerator.toXml(form)
    assertFalse("nodeset=\"/data/trees\"" in xml, xml)
    val s = session(form)
    assertEquals(true, s.isRelevant("/data/has_trees"))
  }

  @Test
  fun multiLanguageAndConceptLinks_inSectionedForm_roundTripCleanly() {
    val base = treesForm()
    val translated =
      base.copy(
        questions =
          base.questions.map { q ->
            if (q.key == "q2") {
              q.copy(
                labelTranslations = mapOf("es" to "Tipo de árbol"),
                hint = "Pick kind",
                hintTranslations = mapOf("es" to "Elige el tipo"),
                choices =
                  q.choices.map { c ->
                    c.copy(labelTranslations = mapOf("es" to if (c.value == "yes") "Sí" else "No"))
                  },
                conceptLink = ConceptLink("forestry.tree_kind", 1),
              )
            } else {
              q
            }
          }
      )
    val xml = EditorXFormsGenerator.toXml(translated, defaultLanguage = "en")
    assertTrue("/data/trees/tree_kind:label" in xml, xml)
    assertTrue("/data/trees/tree_kind:hint" in xml, xml)
    val imported = assertNotNull(FormImport.fromXml(xml)).form
    val q2 = imported.questions.single { it.name == "tree_kind" }
    assertEquals("Tipo de árbol", q2.labelIn("es", "en"))
    assertEquals("Elige el tipo", q2.hintIn("es", "en"))
    assertEquals("Sí", q2.choices.first().labelIn("es", "en"))

    val links = assertNotNull(FormConceptLinkSync.toFormConceptLinks(translated))
    assertTrue("/data/trees/tree_kind" in links.field_concepts)
    val unlinked =
      translated.copy(questions = translated.questions.map { it.copy(conceptLink = null) })
    assertEquals(translated, FormConceptLinkSync.applyLinks(unlinked, links))
  }
}
