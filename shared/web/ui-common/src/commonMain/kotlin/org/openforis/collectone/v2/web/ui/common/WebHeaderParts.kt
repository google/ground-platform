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
package org.openforis.collectone.v2.web.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton

/**
 * Page context for the app header (`WebAppHeader`): a [title] (and optional [titleTrailing] badge)
 * above a row of [supporting] content.
 */
@Composable
fun WebHeaderContext(
  title: String,
  titleTrailing: (@Composable RowScope.() -> Unit)? = null,
  supporting: @Composable RowScope.() -> Unit = {},
) {
  Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(lineHeight = 18.sp),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
      )
      titleTrailing?.invoke(this)
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      content = supporting,
    )
  }
}

/** Small secondary text for the supporting row of a [WebHeaderContext]. */
@Composable
fun WebHeaderSupportingText(text: String, color: Color = Color.Unspecified) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp),
    color = color.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

/**
 * Compact icon-and-label button for the app header (`WebAppHeader`) actions; outlined, or filled
 * tonal.
 */
@Composable
fun WebHeaderButton(
  text: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tonal: Boolean = false,
  trailing: (@Composable RowScope.() -> Unit)? = null,
) {
  val padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
  val content: @Composable RowScope.() -> Unit = {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
    )
    if (trailing != null) {
      Spacer(modifier = Modifier.width(6.dp))
      trailing()
    }
  }
  if (tonal) {
    FilledTonalButton(
      onClick = onClick,
      contentPadding = padding,
      modifier = modifier.height(36.dp),
      content = content,
    )
  } else {
    OutlinedButton(
      onClick = onClick,
      contentPadding = padding,
      modifier = modifier.height(36.dp),
      content = content,
    )
  }
}
