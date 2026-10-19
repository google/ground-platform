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
package org.openforis.collectone.v2.app.ui.map

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openforis.collectone.v2.app.domain.model.MapFeatureCluster
import org.openforis.collectone.v2.app.domain.model.MapScaleBarSpec
import org.openforis.collectone.v2.app.domain.model.formatClusterSitesCountLabel
import org.openforis.collectone.v2.app.ui.common.horizontalScrollWithMouseDrag
import org.openforis.collectone.v2.app.ui.state.SurveyMapUiState
import org.openforis.collectone.v2.app.ui.viewmodel.SurveyMapActions

/**
 * Renders the expanded callout card for the currently selected [MapFeatureCluster] balloon when a
 * geographically pinned Mapbox cluster balloon is tapped on the map.
 */
@Composable
fun MapClusterBalloonsOverlay(uiState: SurveyMapUiState, actions: SurveyMapActions) {
  val selectedCluster = uiState.selectedCluster ?: return
  SelectedClusterBalloonDetailCard(
    sitesCountLabel = uiState.formatClusterSitesCountLabel(selectedCluster.siteCount),
    cluster = selectedCluster,
    onZoomIn = { actions.zoomIntoCluster(selectedCluster.id) },
    onDismiss = { actions.selectCluster(null) },
  )
}

/**
 * Expanded callout card for the currently selected [MapFeatureCluster] balloon showing the count of
 * map features and their workflow states.
 */
@Composable
internal fun SelectedClusterBalloonDetailCard(
  sitesCountLabel: String,
  cluster: MapFeatureCluster,
  onZoomIn: () -> Unit,
  onDismiss: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.96f),
    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.inversePrimary),
    shadowElevation = 6.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = sitesCountLabel,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.inversePrimary,
          fontWeight = FontWeight.Bold,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          TextButton(
            onClick = onZoomIn,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(26.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.ZoomIn,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.inversePrimary,
              modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Zoom in",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.inversePrimary,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false,
            )
          }
          IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = "Dismiss cluster balloon",
              tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f),
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }

      // Features without a marker symbol get no status chip; they're counted in the header total.
      if (cluster.siteStatusChipGroups.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScrollWithMouseDrag(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          cluster.siteStatusChipGroups.forEach { group ->
            val groupColor = Color(group.colorHex)
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.10f),
              contentColor = MaterialTheme.colorScheme.inverseOnSurface,
              border = BorderStroke(1.dp, groupColor.copy(alpha = 0.85f)),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Box(
                  modifier = Modifier.size(14.dp).clip(CircleShape).background(groupColor),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = group.markerSymbol,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = clusterContentColorOnArgb(group.colorHex),
                    fontWeight = FontWeight.ExtraBold,
                  )
                }
                Text(
                  text = "${group.statusLabel}: ${group.count}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.inverseOnSurface,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun clusterContentColorOnArgb(argb: Long): Color {
  val r = ((argb shr 16) and 0xFF) / 255f
  val g = ((argb shr 8) and 0xFF) / 255f
  val b = (argb and 0xFF) / 255f
  val luminance = 0.299f * r + 0.587f * g + 0.114f * b
  return if (luminance > 0.6f) Color(0xFF181D18) else Color.White
}

/**
 * Google Maps-style horizontal scale bar widget rendered in the bottom-left corner of the map.
 * Displays the current ground distance label (`100 m`, `200 m`, `1 km`, etc.) alongside a
 * dynamically sized horizontal scale bracket (`|_________|`) that adapts to Map vs Satellite tiles.
 */
@Composable
fun GoogleMapsScaleBarWidget(
  scaleSpec: MapScaleBarSpec,
  isSatellite: Boolean,
  modifier: Modifier = Modifier,
) {
  val animatedWidthDp by
    animateDpAsState(
      targetValue = scaleSpec.barWidthDp.dp,
      animationSpec = tween(durationMillis = 180),
      label = "googleMapsScaleBarWidth",
    )
  val bgColor =
    if (isSatellite) {
      MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.90f)
    } else {
      MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }
  val primaryColor = MaterialTheme.colorScheme.onSurface
  val haloColor = MaterialTheme.colorScheme.surface

  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.small,
    color = bgColor,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shadowElevation = 3.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      Text(
        text = scaleSpec.label,
        style = MaterialTheme.typography.labelSmall,
        color = primaryColor,
        fontWeight = FontWeight.Bold,
      )

      Canvas(modifier = Modifier.width(animatedWidthDp).height(9.dp)) {
        val leftX = 1.dp.toPx()
        val rightX = (size.width - 1.dp.toPx()).coerceAtLeast(leftX + 4f)
        val topY = 1.dp.toPx()
        val bottomY = size.height - 1.5.dp.toPx()
        val haloStroke = 3.2.dp.toPx()
        val mainStroke = 1.6.dp.toPx()

        drawLine(
          color = haloColor,
          start = Offset(leftX, bottomY),
          end = Offset(rightX, bottomY),
          strokeWidth = haloStroke,
        )
        drawLine(
          color = haloColor,
          start = Offset(leftX, topY),
          end = Offset(leftX, bottomY),
          strokeWidth = haloStroke,
        )
        drawLine(
          color = haloColor,
          start = Offset(rightX, topY),
          end = Offset(rightX, bottomY),
          strokeWidth = haloStroke,
        )

        drawLine(
          color = primaryColor,
          start = Offset(leftX, bottomY),
          end = Offset(rightX, bottomY),
          strokeWidth = mainStroke,
        )
        drawLine(
          color = primaryColor,
          start = Offset(leftX, topY),
          end = Offset(leftX, bottomY),
          strokeWidth = mainStroke,
        )
        drawLine(
          color = primaryColor,
          start = Offset(rightX, topY),
          end = Offset(rightX, bottomY),
          strokeWidth = mainStroke,
        )
      }
    }
  }
}
