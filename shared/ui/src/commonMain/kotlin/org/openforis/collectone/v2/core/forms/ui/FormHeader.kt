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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import openforis.collectone.v1.forms.FormDef
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The title a form runner shows for [formDef]: its title, else its form ID, else the localized
 * [untitled] stand-in.
 */
@Composable
internal fun formDisplayTitle(formDef: FormDef, untitled: StringResource): String =
  formDef.title.takeIf { it.isNotBlank() }
    ?: formDef.form_id.takeIf { it.isNotBlank() }
    ?: stringResource(untitled)

/**
 * The form title and progress [subtitle] at the top of [MobileFormRunner] and [CompactFormRunner].
 * Sizes follow [LocalFormDensity]; the compact title wraps to at most two lines. Colors come from
 * the caller, since the mobile header sits on the primary-colored app bar.
 */
@Composable
internal fun FormHeaderTitle(
  title: String,
  subtitle: String,
  titleColor: Color,
  subtitleColor: Color,
  modifier: Modifier = Modifier,
) {
  val isCompact = LocalFormDensity.current == FormDensity.COMPACT
  val titleStyle =
    if (isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
  Column(modifier = modifier) {
    Text(
      text = title,
      style = titleStyle.copy(fontWeight = FontWeight.Bold),
      color = titleColor,
      maxLines = if (isCompact) 2 else Int.MAX_VALUE,
      overflow = if (isCompact) TextOverflow.Ellipsis else TextOverflow.Clip,
      modifier = Modifier.semantics { heading() },
    )
    Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = subtitleColor)
  }
}
