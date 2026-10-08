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
package org.groundplatform.v2.devtools.prototypeapp.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.core.forms.ui.MapDrawingKind

/**
 * Floating instructions over the dashboard map while a geometry is being drawn: what to click, how
 * many vertices are placed versus needed, and **Undo** (lines and polygons), **Done** (once enough
 * vertices are placed), and **Cancel** actions.
 */
@Composable
internal fun WebMapDrawingHint(
  draft: DraftGeometry,
  onUndo: () -> Unit,
  onDone: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val kind = draft.kind
  val count = draft.vertices.size
  val progress =
    when (kind) {
      MapDrawingKind.POINT -> "Click the map to place the point"
      else -> "Click the map to add vertices • $count of at least ${kind.minVertices}"
    }
  Surface(
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    tonalElevation = 3.dp,
    shadowElevation = 4.dp,
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.Edit,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Text(text = progress, style = MaterialTheme.typography.labelLarge)
      if (kind != MapDrawingKind.POINT) {
        TextButton(onClick = onUndo, enabled = count > 0) { Text("Undo") }
        Button(onClick = onDone, enabled = draft.isComplete) { Text("Done") }
      }
      TextButton(onClick = onCancel) { Text("Cancel drawing") }
    }
  }
}
