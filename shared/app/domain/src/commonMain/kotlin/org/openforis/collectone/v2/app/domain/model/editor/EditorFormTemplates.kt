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

/** Templates for new Forms created in the editor. */
object EditorFormTemplates {
  /** A new Form with a single text question, used by "+" in the Survey editor. */
  fun blank(formId: String, title: String): EditorForm =
    EditorForm(
      formId = formId,
      title = title,
      questions =
        listOf(
          EditorQuestion(
            key = "q1",
            name = "text",
            type = EditorQuestionType.TEXT,
            label = "New text question",
          )
        ),
    )
}
