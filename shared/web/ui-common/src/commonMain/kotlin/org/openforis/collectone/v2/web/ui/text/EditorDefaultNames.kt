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
package org.openforis.collectone.v2.web.ui.text

import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType

/**
 * Starting names the editors give new questions, datasets, forms, and properties, e.g. `New photo
 * question` or `New map layer`.
 *
 * They're saved in the draft as the user's own data (which they're expected to rename), in the
 * language the editor shows when the item is created: the view resolves them from string resources
 * (`editorDefaultNames()`) and hands them to `SurveyEditorViewModel.useDefaultNames`. A new
 * dataset's ID doesn't depend on its name (`DatasetIds`), so names in any script are safe.
 */
data class EditorDefaultNames(
  /** Starting label of a new question, by type; has an entry for every [EditorQuestionType]. */
  val questions: Map<EditorQuestionType, String>,
  val mapLayer: String,
  val dataTable: String,
  val form: String,
  val property: String,
) {
  init {
    require(questions.keys == EditorQuestionType.entries.toSet()) {
      "Missing default question names: ${EditorQuestionType.entries - questions.keys}"
    }
  }

  /** Starting label of a new question of [type]. */
  fun question(type: EditorQuestionType): String = questions.getValue(type)

  /** Starting name of a new dataset of [kind]. */
  fun dataset(kind: DatasetKind): String =
    when (kind) {
      DatasetKind.MAP_LAYER -> mapLayer
      DatasetKind.DATA_TABLE -> dataTable
    }

  companion object {
    /** The English names, until (and where) the view provides the shown language's. */
    val ENGLISH =
      EditorDefaultNames(
        questions =
          mapOf(
            EditorQuestionType.TEXT to "New short text question",
            EditorQuestionType.LONG_TEXT to "New paragraph question",
            EditorQuestionType.NUMBER to "New number question",
            EditorQuestionType.SELECT_ONE to "New select one question",
            EditorQuestionType.SELECT_MULTIPLE to "New select multiple question",
            EditorQuestionType.DATE to "New date question",
            EditorQuestionType.LOCATION to "New point question",
            EditorQuestionType.LINE to "New line question",
            EditorQuestionType.POLYGON to "New polygon question",
            EditorQuestionType.PHOTO to "New photo question",
            EditorQuestionType.VIDEO to "New video question",
            EditorQuestionType.AUDIO to "New audio question",
            EditorQuestionType.NOTE to "New note",
          ),
        mapLayer = "New map layer",
        dataTable = "New data table",
        form = "New form",
        property = "New property",
      )
  }
}
