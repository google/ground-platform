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
package org.openforis.collectone.v2.web.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollapseContent
import androidx.compose.material.icons.outlined.ExpandContent
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.app.ui.datacollection.EntityStatusChip
import org.openforis.collectone.v2.app.ui.map.LayerIcons
import org.openforis.collectone.v2.app.ui.map.contentColorOnArgb
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_by_status
import org.openforis.collectone.v2.web.resources.web_close_details
import org.openforis.collectone.v2.web.resources.web_collapse_details
import org.openforis.collectone.v2.web.resources.web_data_table
import org.openforis.collectone.v2.web.resources.web_expand_details
import org.openforis.collectone.v2.web.resources.web_map_features
import org.openforis.collectone.v2.web.resources.web_map_layer
import org.openforis.collectone.v2.web.resources.web_show_in_table

/** Number of map features in a layer sharing one status; [example] provides the chip styling. */
internal data class DashboardLayerStatusCount(val example: GeospatialEntityItem, val count: Int) {
  /** Status label shared by the counted map features (marker symbol and status text). */
  val label: String
    get() = example.mapStatusSummaryBadge
}

/** Summary of one entity dataset shown in the web dashboard's layer details card. */
internal data class DashboardLayerSummary(
  val datasetId: String,
  val title: String,
  val kind: DashboardDataTableKind,
  val featureCount: Int,
  val statusCounts: List<DashboardLayerStatusCount>,
) {
  /** User-facing kind label: `Map layer` or `Data table`. */
  val kindLabel: StringResource
    get() =
      when (kind) {
        DashboardDataTableKind.MAP_LAYER -> Res.string.web_map_layer
        DashboardDataTableKind.DATA_TABLE -> Res.string.web_data_table
      }
}

/**
 * Summarizes the entity dataset [datasetId] in [entities]: its title, kind, total record count, and
 * the number of records per status (most common first; ties keep order of first appearance).
 * Records without a workflow status aren't counted under any status. Returns `null` when the
 * dataset has no records.
 */
internal fun buildDashboardLayerSummary(
  entities: List<GeospatialEntityItem>,
  datasetId: String,
): DashboardLayerSummary? {
  val datasetEntities = entities.filter { it.datasetId == datasetId }
  if (datasetEntities.isEmpty()) return null
  return DashboardLayerSummary(
    datasetId = datasetId,
    title = datasetEntities.first().datasetName,
    kind =
      if (datasetEntities.any { it.hasGeometry }) {
        DashboardDataTableKind.MAP_LAYER
      } else {
        DashboardDataTableKind.DATA_TABLE
      },
    featureCount = datasetEntities.size,
    statusCounts =
      datasetEntities
        .filter { it.hasWorkflowStatus }
        .groupBy { it.mapStatusSummaryBadge }
        .values
        .map { DashboardLayerStatusCount(example = it.first(), count = it.size) }
        .sortedByDescending { it.count },
  )
}

/**
 * Swatch of a map layer: its pin icon on a circle of the layer color (matching its map pins) when
 * the layer has an icon, otherwise a color square matching the left-hand panel's layer headers.
 */
@Composable
private fun LayerSwatch(layer: MapLayerItem?, size: Int) {
  val color = layer?.colorHex?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
  val icon = LayerIcons.forName(layer?.pinIconName)
  if (icon != null) {
    Box(
      modifier = Modifier.size((size + 4).dp).background(color, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon.vector,
        contentDescription = null,
        tint = Color(contentColorOnArgb(color.toArgb().toLong() and 0xFFFFFFFFL)),
        modifier = Modifier.size((size - 2).dp),
      )
    }
    return
  }
  Box(
    modifier =
      Modifier.size(size.dp)
        .clip(MaterialTheme.shapes.extraSmall)
        .background(color.copy(alpha = 0.25f))
        .border(2.dp, color, MaterialTheme.shapes.extraSmall)
  )
}

/**
 * Web dashboard's floating card for the map layer or data table selected in the left-hand panel:
 * its name and kind, total number of map features, number of map features per status, and a "Show
 * in table" button that opens the dataset's tab in the bottom data table panel.
 *
 * [onClose] deselects the layer; [onShowInTable] opens its tab in the bottom data table panel.
 */
@Composable
internal fun WebLayerDetailsCard(
  summary: DashboardLayerSummary,
  layer: MapLayerItem?,
  onClose: () -> Unit,
  onShowInTable: () -> Unit,
  modifier: Modifier = Modifier,
  onCollapse: (() -> Unit)? = null,
) {
  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shadowElevation = 6.dp,
  ) {
    Column {
      Row(
        modifier =
          Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        LayerSwatch(layer = layer, size = 18)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
          Text(
            text = layer?.label ?: summary.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = stringResource(summary.kindLabel),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (onCollapse != null) {
          IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
            Icon(
              imageVector = Icons.Outlined.CollapseContent,
              contentDescription = stringResource(Res.string.web_collapse_details),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(22.dp),
            )
          }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
          Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(Res.string.web_close_details),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Column(
        modifier =
          Modifier.weight(1f, fill = false)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(Res.string.web_map_features),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "${summary.featureCount}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }

        if (summary.statusCounts.isNotEmpty()) {
          Text(
            text = stringResource(Res.string.web_by_status),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
          )
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            summary.statusCounts.forEach { statusCount ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                EntityStatusChip(entity = statusCount.example, compact = true)
                Text(
                  text = "${statusCount.count}",
                  style =
                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }
        }

        OutlinedButton(
          onClick = onShowInTable,
          contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
          Icon(
            imageVector = Icons.Outlined.TableRows,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
          )
          Text(
            text = stringResource(Res.string.web_show_in_table),
            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
          )
        }
      }
    }
  }
}

/**
 * Compact floating pill shown in the top-right corner when the details panel is collapsed while a
 * map layer or data table is selected.
 */
@Composable
internal fun CollapsedLayerDetailsPill(
  title: String,
  layer: MapLayerItem?,
  onExpand: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shadowElevation = 4.dp,
    tonalElevation = 2.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier =
        Modifier.clip(CircleShape)
          .clickable { onExpand() }
          .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.ExpandContent,
        contentDescription = stringResource(Res.string.web_expand_details),
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
      LayerSwatch(layer = layer, size = 14)
      Text(
        text = layer?.label ?: title,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 160.dp),
      )
      IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
        Icon(
          imageVector = Icons.Outlined.Close,
          contentDescription = stringResource(Res.string.web_close_details),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}
