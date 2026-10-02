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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FormIdsTest {

  private val formIdPattern =
    Regex("^form_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

  @Test
  fun newFormId_isPrefixedUuid() {
    val id = FormIds.newFormId()
    assertTrue(formIdPattern.matches(id), id)
  }

  @Test
  fun newFormId_isUniquePerCall() {
    assertNotEquals(FormIds.newFormId(), FormIds.newFormId())
  }

  @Test
  fun formId_isStableAcrossEdits() {
    val state = FormEditorState()
    val id = state.form.formId
    state.updateTitle("Renamed form")
    state.addQuestion(EditorQuestionType.INTEGER)
    assertEquals(id, state.form.formId)
  }

  @Test
  fun generatedXml_usesFormIdVerbatim() {
    val id = FormIds.newFormId()
    val xml = EditorXFormsGenerator.toXml(EditorFormTemplates.blank(id, "Title"))
    assertTrue(xml.contains("""<data id="$id" version="1">"""), xml)
  }

  @Test
  fun validator_acceptsArbitraryNonBlankFormIds() {
    // IDs from imported XForms are opaque and need not follow the generated format.
    listOf("household_survey", "123-starts-with-digit", FormIds.newFormId()).forEach { id ->
      val issues = FormEditorValidator.validate(EditorFormTemplates.blank(id, "Title"))
      assertEquals(emptyList(), issues.filter { it.questionKey == null }, id)
    }
  }

  @Test
  fun validator_rejectsBlankFormId() {
    val issues = FormEditorValidator.validate(EditorFormTemplates.blank("", "Title"))
    assertTrue(issues.any { it.questionKey == null }, issues.toString())
  }
}
