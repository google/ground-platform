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
package org.openforis.collectone.v2.app.ui.datacollection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.StraightLineVector
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_navigate
import org.openforis.collectone.v2.app.ui.text.wayfindingBadge
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton

/**
 * Mobile wayfinding action of a map feature or place: one outlined button that starts straight-line
 * navigation to it (`Navigate`) or stops it ([stopLabel] while [isNavigating]), with the distance
 * and heading to it ([wayfinding]) beside the button in the theme's green (the primary color).
 */
@Composable
internal fun NavigateAction(
  isNavigating: Boolean,
  wayfinding: StraightLineVector?,
  stopLabel: String,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    OutlinedButton(
      onClick = onToggle,
      contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
      Icon(
        imageVector = Icons.Outlined.Navigation,
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
      )
      Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
      Text(
        text = if (isNavigating) stopLabel else stringResource(Res.string.app_navigate),
        maxLines = 1,
      )
    }
    val distanceAndHeading = wayfindingBadge(wayfinding)
    if (distanceAndHeading.isNotEmpty()) {
      Text(
        text = distanceAndHeading,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}
