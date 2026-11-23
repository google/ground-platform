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
package org.openforis.collectone.v2.web.ui.formeditor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pentagon
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.type
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType

/** Standard icon representing each [EditorQuestionType]. */
internal fun questionTypeIcon(type: EditorQuestionType): ImageVector =
  when (type) {
    EditorQuestionType.TEXT -> Icons.Outlined.Edit
    EditorQuestionType.LONG_TEXT -> Icons.Outlined.Description
    EditorQuestionType.NUMBER -> Icons.AutoMirrored.Outlined.List
    EditorQuestionType.SELECT_ONE -> Icons.Outlined.CheckCircle
    EditorQuestionType.SELECT_MULTIPLE -> Icons.Outlined.Check
    EditorQuestionType.DATE -> Icons.Outlined.DateRange
    EditorQuestionType.LOCATION -> Icons.Outlined.LocationOn
    EditorQuestionType.LINE -> Icons.Outlined.Timeline
    EditorQuestionType.POLYGON -> Icons.Outlined.Pentagon
    EditorQuestionType.PHOTO -> Icons.Outlined.AccountCircle
    EditorQuestionType.VIDEO -> Icons.Outlined.Videocam
    EditorQuestionType.AUDIO -> Icons.Outlined.Mic
    EditorQuestionType.NOTE -> Icons.Outlined.Info
  }
