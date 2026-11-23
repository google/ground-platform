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

import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Material Design 3 Undo icon. */
val UndoIcon: ImageVector by lazy {
  ImageVector.Builder(
      name = "Undo",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    )
    .apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(12.5f, 8.0f)
        curveToRelative(-2.65f, 0.0f, -5.05f, 0.99f, -6.9f, 2.6f)
        lineTo(2.0f, 7.0f)
        verticalLineToRelative(9.0f)
        horizontalLineToRelative(9.0f)
        lineToRelative(-3.62f, -3.62f)
        curveToRelative(1.39f, -1.16f, 3.16f, -1.88f, 5.12f, -1.88f)
        curveToRelative(3.54f, 0.0f, 6.55f, 2.31f, 7.6f, 5.5f)
        lineToRelative(2.37f, -0.78f)
        curveTo(21.08f, 11.03f, 17.15f, 8.0f, 12.5f, 8.0f)
        close()
      }
    }
    .build()
}

/** Material Design 3 Redo icon. */
val RedoIcon: ImageVector by lazy {
  ImageVector.Builder(
      name = "Redo",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    )
    .apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(18.4f, 10.6f)
        curveTo(16.55f, 8.99f, 14.15f, 8.0f, 11.5f, 8.0f)
        curveToRelative(-4.65f, 0.0f, -8.58f, 3.03f, -9.96f, 7.22f)
        lineTo(3.9f, 16.0f)
        curveToRelative(1.05f, -3.19f, 4.05f, -5.5f, 7.6f, -5.5f)
        curveToRelative(1.95f, 0.0f, 3.73f, 0.72f, 5.12f, 1.88f)
        lineTo(13.0f, 16.0f)
        horizontalLineToRelative(9.0f)
        verticalLineTo(7.0f)
        lineToRelative(-3.6f, 3.6f)
        close()
      }
    }
    .build()
}

internal val RemoveIcon: ImageVector by lazy {
  ImageVector.Builder(
      name = "App.Remove",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    )
    .path(fill = SolidColor(Color.Black)) {
      moveTo(19f, 13f)
      horizontalLineTo(5f)
      verticalLineTo(11f)
      horizontalLineTo(19f)
      close()
    }
    .build()
}
