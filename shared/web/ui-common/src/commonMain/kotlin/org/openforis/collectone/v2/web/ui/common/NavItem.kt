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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.ui.controls.NavigationDrawerItem
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_nav_has_issues

/**
 * One row of a left-hand navigation panel: icon, label, optional trailing count, and an issue
 * marker. Shared by the survey editor and the organization page so both panels look the same.
 */
@Composable
fun NavItem(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  trailing: String? = null,
  hasIssues: Boolean = false,
  nested: Boolean = false,
) {
  NavigationDrawerItem(
    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    selected = selected,
    onClick = onClick,
    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
    badge = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (hasIssues) {
          Icon(
            Icons.Outlined.Warning,
            contentDescription = stringResource(Res.string.surveyeditor_nav_has_issues),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(6.dp))
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium)
      }
    },
    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
    modifier = Modifier.height(44.dp).padding(start = if (nested) 12.dp else 0.dp),
  )
}
