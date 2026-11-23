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

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.editor_default_name_audio_question
import org.openforis.collectone.v2.web.resources.editor_default_name_data_table
import org.openforis.collectone.v2.web.resources.editor_default_name_date_question
import org.openforis.collectone.v2.web.resources.editor_default_name_form
import org.openforis.collectone.v2.web.resources.editor_default_name_line_question
import org.openforis.collectone.v2.web.resources.editor_default_name_long_text_question
import org.openforis.collectone.v2.web.resources.editor_default_name_map_layer
import org.openforis.collectone.v2.web.resources.editor_default_name_note
import org.openforis.collectone.v2.web.resources.editor_default_name_number_question
import org.openforis.collectone.v2.web.resources.editor_default_name_photo_question
import org.openforis.collectone.v2.web.resources.editor_default_name_point_question
import org.openforis.collectone.v2.web.resources.editor_default_name_polygon_question
import org.openforis.collectone.v2.web.resources.editor_default_name_property
import org.openforis.collectone.v2.web.resources.editor_default_name_select_multiple_question
import org.openforis.collectone.v2.web.resources.editor_default_name_select_one_question
import org.openforis.collectone.v2.web.resources.editor_default_name_text_question
import org.openforis.collectone.v2.web.resources.editor_default_name_video_question

/** The string resource of the starting label of a new question of this type. */
val EditorQuestionType.defaultNameRes: StringResource
  get() =
    when (this) {
      EditorQuestionType.TEXT -> Res.string.editor_default_name_text_question
      EditorQuestionType.LONG_TEXT -> Res.string.editor_default_name_long_text_question
      EditorQuestionType.NUMBER -> Res.string.editor_default_name_number_question
      EditorQuestionType.SELECT_ONE -> Res.string.editor_default_name_select_one_question
      EditorQuestionType.SELECT_MULTIPLE -> Res.string.editor_default_name_select_multiple_question
      EditorQuestionType.DATE -> Res.string.editor_default_name_date_question
      EditorQuestionType.LOCATION -> Res.string.editor_default_name_point_question
      EditorQuestionType.LINE -> Res.string.editor_default_name_line_question
      EditorQuestionType.POLYGON -> Res.string.editor_default_name_polygon_question
      EditorQuestionType.PHOTO -> Res.string.editor_default_name_photo_question
      EditorQuestionType.VIDEO -> Res.string.editor_default_name_video_question
      EditorQuestionType.AUDIO -> Res.string.editor_default_name_audio_question
      EditorQuestionType.NOTE -> Res.string.editor_default_name_note
    }

/**
 * The editors' [EditorDefaultNames], each name resolved by [text] from its string resource (with
 * `stringResource` in composition, or `getString`).
 */
inline fun editorDefaultNamesOf(text: (StringResource) -> String): EditorDefaultNames =
  EditorDefaultNames(
    questions = EditorQuestionType.entries.associateWith { text(it.defaultNameRes) },
    mapLayer = text(Res.string.editor_default_name_map_layer),
    dataTable = text(Res.string.editor_default_name_data_table),
    form = text(Res.string.editor_default_name_form),
    property = text(Res.string.editor_default_name_property),
  )

/** The editors' [EditorDefaultNames] in the language the app shows. */
@Composable
fun editorDefaultNames(): EditorDefaultNames = editorDefaultNamesOf { stringResource(it) }
