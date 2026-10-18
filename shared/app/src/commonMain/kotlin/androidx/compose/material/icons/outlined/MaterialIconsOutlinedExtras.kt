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
package androidx.compose.material.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Material Symbols Outlined `collapse_content`: two corner brackets pointing inward. Not available
 * in material-icons-extended; paths converted from the 960×960 Material Symbols viewport to 24×24.
 */
public val Icons.Outlined.CollapseContent: ImageVector
  get() {
    if (_collapseContent != null) return _collapseContent!!
    _collapseContent =
      materialIcon(name = "Outlined.CollapseContent") {
        materialPath {
          moveTo(11.0f, 13.0f)
          verticalLineToRelative(6.0f)
          horizontalLineToRelative(-2.0f)
          verticalLineToRelative(-4.0f)
          horizontalLineTo(5.0f)
          verticalLineToRelative(-2.0f)
          horizontalLineToRelative(6.0f)
          close()
          moveTo(15.0f, 5.0f)
          verticalLineToRelative(4.0f)
          horizontalLineToRelative(4.0f)
          verticalLineToRelative(2.0f)
          horizontalLineTo(13.0f)
          verticalLineToRelative(-6.0f)
          horizontalLineToRelative(2.0f)
          close()
        }
      }
    return _collapseContent!!
  }

private var _collapseContent: ImageVector? = null

/**
 * Material Symbols Outlined `expand_content`: two corner brackets pointing outward. Not available
 * in material-icons-extended; paths converted from the 960×960 Material Symbols viewport to 24×24.
 */
public val Icons.Outlined.ExpandContent: ImageVector
  get() {
    if (_expandContent != null) return _expandContent!!
    _expandContent =
      materialIcon(name = "Outlined.ExpandContent") {
        materialPath {
          moveTo(5.0f, 19.0f)
          verticalLineToRelative(-6.0f)
          horizontalLineToRelative(2.0f)
          verticalLineToRelative(4.0f)
          horizontalLineToRelative(4.0f)
          verticalLineToRelative(2.0f)
          horizontalLineTo(5.0f)
          close()
          moveTo(17.0f, 11.0f)
          verticalLineToRelative(-4.0f)
          horizontalLineTo(13.0f)
          verticalLineToRelative(-2.0f)
          horizontalLineToRelative(6.0f)
          verticalLineToRelative(6.0f)
          horizontalLineToRelative(-2.0f)
          close()
        }
      }
    return _expandContent!!
  }

private var _expandContent: ImageVector? = null
