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
import kotlin.test.assertNull
import org.groundplatform.v2.devtools.prototypeapp.data.seed.FormEditorSamples
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeSurveysData
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SurveyEditorSamples

/** `EditorForm` → published XForms XML → [FormImport] → the same Form. */
class FormImportRoundTripTest {
  private fun roundTrip(form: EditorForm): EditorForm {
    val draft = SurveyEditorSamples.draft()
    val entry = SurveyEditorForm("f1", form)
    val xml = draft.copy(forms = listOf(entry)).publishedFormXml(entry)
    return assertNotNull(FormImport.fromXml(xml, form.formId, availability = form.availability))
      .form
  }

  private fun assertSameQuestions(expected: EditorForm, actual: EditorForm) {
    assertEquals(expected.formId, actual.formId)
    assertEquals(expected.title, actual.title)
    assertEquals(expected.questions.map { it.name }, actual.questions.map { it.name })
    assertEquals(expected.questions.map { it.label }, actual.questions.map { it.label })
    assertEquals(expected.questions.map { it.type }, actual.questions.map { it.type })
    assertEquals(expected.questions.map { it.required }, actual.questions.map { it.required })
    assertEquals(
      expected.questions.map { q -> q.choices.map { it.value to it.label } },
      actual.questions.map { q -> q.choices.map { it.value to it.label } },
    )
  }

  @Test
  fun shadeTreeVisit_survivesTheRoundTrip() {
    val expected = FormEditorSamples.shadeTreeVisit()
    val actual = roundTrip(expected)
    assertSameQuestions(expected, actual)
    // Relevance is re-linked by question name → key.
    val byName = actual.questions.associateBy { it.name }
    val count = byName.getValue("shade_tree_count")
    assertEquals(byName.getValue("has_shade_trees").key, count.relevance?.sourceQuestionKey)
    assertEquals(RelevanceOperator.EQUALS, count.relevance?.operator)
    assertEquals("yes", count.relevance?.value)
    val pestNotes = byName.getValue("pest_notes")
    assertEquals(byName.getValue("observed_issues").key, pestNotes.relevance?.sourceQuestionKey)
    assertEquals(RelevanceOperator.INCLUDES, pestNotes.relevance?.operator)
    assertEquals("pests", pestNotes.relevance?.value)
    assertNull(byName.getValue("visit_date").relevance)
    assertEquals("Stand near the center of the plot.", byName.getValue("farm_location").hint)
  }

  @Test
  fun parcelBoundaryCheck_survivesTheRoundTrip() {
    val expected = FormEditorSamples.parcelBoundaryCheck()
    assertSameQuestions(expected, roundTrip(expected))
  }

  @Test
  fun derivedSampleForms_areStableAcrossPublishAndImport() {
    for (survey in PrototypeFakeSurveysData.defaultSampleSurveys()) {
      for (form in PrototypeFakeSurveysData.formsForSurvey(survey.id)) {
        val xml = PrototypeFakeSurveysData.builtInFallbackXFormsXmlForForm(form)
        val imported = assertNotNull(FormImport.fromXml(xml, form.id, form.title), form.id).form
        assertSameQuestions(imported, roundTrip(imported))
      }
    }
  }
}
