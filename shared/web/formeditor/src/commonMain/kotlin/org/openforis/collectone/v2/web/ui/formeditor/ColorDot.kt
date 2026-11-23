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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceColors

/** Filled swatch for [colorHex], or an outlined empty circle when there is no color. */
@Composable
internal fun ColorDot(
  colorHex: String?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  isSelected: Boolean = false,
  size: Dp = 20.dp,
) {
  val colors = MaterialTheme.colorScheme
  val fill = ChoiceColors.argb(colorHex)?.let { Color(it) }
  Box(
    modifier =
      modifier
        .size(size)
        .clip(CircleShape)
        .background(fill ?: Color.Transparent)
        .border(
          width = if (isSelected) 2.dp else 1.dp,
          color = if (isSelected) colors.onSurface else colors.outline,
          shape = CircleShape,
        )
        .semantics { contentDescription?.let { this.contentDescription = it } },
    contentAlignment = Alignment.Center,
  ) {
    if (isSelected) {
      val checkTint =
        when {
          fill == null -> colors.onSurface
          fill.luminance() > 0.6f -> Color(0xFF181D18)
          else -> Color.White
        }
      Icon(
        Icons.Outlined.Check,
        contentDescription = null,
        tint = checkTint,
        modifier = Modifier.size(size * 0.7f),
      )
    }
  }
}
