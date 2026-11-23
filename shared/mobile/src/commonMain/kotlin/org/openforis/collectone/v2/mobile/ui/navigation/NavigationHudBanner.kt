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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.NavigationTargetKind
import org.openforis.collectone.v2.app.domain.model.StraightLineNavigationState
import org.openforis.collectone.v2.app.ui.map.SurveyMapActions
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_arrived
import org.openforis.collectone.v2.mobile.resources.mobile_bearing
import org.openforis.collectone.v2.mobile.resources.mobile_compass_bearing_arrow
import org.openforis.collectone.v2.mobile.resources.mobile_stop
import org.openforis.collectone.v2.mobile.resources.mobile_stop_navigation_content_description
import org.openforis.collectone.v2.mobile.resources.mobile_walk_minutes
import org.openforis.collectone.v2.mobile.resources.mobile_wayfinding_entity
import org.openforis.collectone.v2.mobile.resources.mobile_wayfinding_place
import org.openforis.collectone.v2.mobile.resources.mobile_wayfinding_submission
import org.openforis.collectone.v2.mobile.resources.mobile_wayfinding_subtitle

/**
 * Interactive Straight-Line Navigation HUD Banner shown below the top search bar on the Map View
 * whenever straight-line navigation to a Geospatial Entity (`NavigationTargetKind.ENTITY`), Place
 * (`NavigationTargetKind.PLACE`), or Form Submission (`NavigationTargetKind.SUBMISSION`) is active.
 */
@Composable
internal fun StraightLineNavigationHudBanner(
  navState: StraightLineNavigationState,
  targetTypeLabel: String,
  isDarkTheme: Boolean,
  actions: SurveyMapActions,
) {
  val colors = MaterialTheme.colorScheme
  val topBarContainer =
    if (isDarkTheme) {
      colors.primaryContainer
    } else {
      colors.primary
    }
  val onTopBarContainer =
    if (isDarkTheme) {
      colors.onPrimaryContainer
    } else {
      colors.onPrimary
    }
  val accentColor =
    when (navState.targetKind) {
      NavigationTargetKind.ENTITY -> colors.tertiaryContainer
      NavigationTargetKind.PLACE -> colors.secondaryContainer
      NavigationTargetKind.SUBMISSION -> colors.inversePrimary
    }
  val kindLabel =
    when (navState.targetKind) {
      NavigationTargetKind.ENTITY ->
        stringResource(Res.string.mobile_wayfinding_entity, targetTypeLabel)
      NavigationTargetKind.PLACE -> stringResource(Res.string.mobile_wayfinding_place)
      NavigationTargetKind.SUBMISSION -> stringResource(Res.string.mobile_wayfinding_submission)
    }

  Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    shape = RoundedCornerShape(16.dp),
    color = topBarContainer,
    contentColor = onTopBarContainer,
    shadowElevation = 6.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Primary Wayfinding Row: Rotating Compass Arrow + Target Details + Distance/Bearing/Walk
        // Readout
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Rotating Compass Arrow Badge
          Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = colors.secondaryContainer,
            contentColor = colors.onSecondaryContainer,
            border = BorderStroke(1.5.dp, colors.outlineVariant),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Outlined.Navigation,
                contentDescription = stringResource(Res.string.mobile_compass_bearing_arrow),
                tint = colors.onSecondaryContainer,
                modifier =
                  Modifier.size(19.dp)
                    .graphicsLayer(rotationZ = navState.vector.bearingDegrees.toFloat()),
              )
            }
          }

          // Target Type Pill, Title & Subtitle
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Surface(
              shape = CircleShape,
              color = colors.secondaryContainer,
              contentColor = colors.onSecondaryContainer,
            ) {
              Text(
                text = kindLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }

            Text(
              text = navState.targetTitle,
              style = MaterialTheme.typography.titleSmall,
              color = onTopBarContainer,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )

            Text(
              text = navState.targetSubtitle,
              style = MaterialTheme.typography.labelSmall,
              color = onTopBarContainer.copy(alpha = 0.85f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          // Distance + Bearing + Walk Time Readout Column (never wraps vertically)
          Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = navState.vector.distance.toUiText().resolve(),
              style =
                MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.ExtraBold,
                  color = onTopBarContainer,
                ),
              maxLines = 1,
              softWrap = false,
            )
            Text(
              text = stringResource(Res.string.mobile_bearing, navState.vector.formattedBearing),
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = onTopBarContainer,
                  fontWeight = FontWeight.Bold,
                ),
              maxLines = 1,
              softWrap = false,
            )
            Text(
              text =
                if (navState.vector.isArrived) {
                  stringResource(Res.string.mobile_arrived)
                } else {
                  pluralStringResource(
                    Res.plurals.mobile_walk_minutes,
                    navState.vector.estimatedWalkMinutes,
                    navState.vector.estimatedWalkMinutes,
                  )
                },
              style = MaterialTheme.typography.labelSmall,
              color = onTopBarContainer.copy(alpha = 0.85f),
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false,
            )
          }
        }

        HorizontalDivider(color = onTopBarContainer.copy(alpha = 0.14f))

        // Bottom Control Strip: Geodesic Vector Status + High-Contrast "Walk Closer" & "Stop" Pills
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(Res.string.mobile_wayfinding_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = onTopBarContainer.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )

          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              onClick = { actions.stopNavigation() },
              shape = CircleShape,
              color = colors.errorContainer,
              contentColor = colors.onErrorContainer,
              border = BorderStroke(1.dp, colors.error.copy(alpha = 0.6f)),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription =
                    stringResource(Res.string.mobile_stop_navigation_content_description),
                  tint = colors.onErrorContainer,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = stringResource(Res.string.mobile_stop),
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.onErrorContainer,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  softWrap = false,
                )
              }
            }
          }
        }
      }

      // Bottom accent bar matching the active navigation target kind
      Box(
        modifier =
          Modifier.fillMaxWidth().height(2.5.dp).background(accentColor.copy(alpha = 0.85f))
      )
    }
  }
}
