/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorStatusRule
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator
import org.openforis.collectone.v2.app.domain.model.editor.StatusConditionSubject

class StatusRuleTextTest {
  private val form =
    EditorForm(
      formId = "f",
      title = "Form",
      questions =
        listOf(
          EditorQuestion(
            key = "k",
            name = "species",
            type = EditorQuestionType.TEXT,
            label = "Species",
          )
        ),
    )

  private fun english(rule: EditorStatusRule): String = runBlocking {
    statusRuleSummaryText(form, target = null, rule).resolveString()
  }

  @Test
  fun submissionRules_countSubmissions() {
    assertEquals("When form is submitted", english(EditorStatusRule()))
    assertEquals("At least 3 submissions", english(EditorStatusRule(minSubmissions = 3)))
  }

  @Test
  fun questionRules_nameTheQuestionAndOperator() {
    val rule = EditorStatusRule(subject = StatusConditionSubject.QUESTION, questionKey = "k")
    assertEquals("species equals \"oak\"", english(rule.copy(value = "oak")))
    assertEquals(
      "species is answered",
      english(rule.copy(operator = RelevanceOperator.IS_ANSWERED)),
    )
    assertEquals("Missing question", english(rule.copy(questionKey = "gone")))
  }

  @Test
  fun propertyRules_fallBackToAGenericName() {
    val rule =
      EditorStatusRule(
        subject = StatusConditionSubject.ENTITY_PROPERTY,
        operator = RelevanceOperator.IS_ANSWERED,
      )
    assertEquals("property is answered", english(rule))
    assertEquals("height is answered", english(rule.copy(property = "height")))
  }
}
