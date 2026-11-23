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
package org.openforis.collectone.v2.mobile.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.DeviceStorageInfo
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.forms.ui.controls.Card
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_device_storage_data
import org.openforis.collectone.v2.mobile.resources.mobile_device_storage_downloaded_imagery
import org.openforis.collectone.v2.mobile.resources.mobile_device_storage_free
import org.openforis.collectone.v2.mobile.resources.mobile_device_storage_free_of_total
import org.openforis.collectone.v2.mobile.resources.mobile_device_storage_other
import org.openforis.collectone.v2.mobile.resources.mobile_device_storage_safeguard_notice
import org.openforis.collectone.v2.mobile.resources.mobile_device_storage_title

/**
 * Clear, user-friendly device storage card displaying:
 * - Total device storage capacity (measured)
 * - Free / available device storage (measured)
 * - Storage occupied by the map areas saved for offline use
 * - Space taken up by data (surveys, forms, entities, submissions, mutations)
 * - Visual stacked horizontal proportional chart bar with color-coded legend
 */
@Composable
fun DeviceStorageBreakdownCard(storage: DeviceStorageInfo, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // Header row: Icon, Title & Free / Total headline
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = stringResource(Res.string.mobile_device_storage_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        Text(
          text =
            stringResource(
              Res.string.mobile_device_storage_free_of_total,
              storage.freeSpace.toUiText().resolve(),
              storage.totalSpace.toUiText().resolve(),
            ),
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.primary,
        )
      }

      // Proportional stacked horizontal bar chart
      DeviceStorageBreakdownChart(
        storage = storage,
        modifier = Modifier.fillMaxWidth().height(16.dp),
      )

      // Storage breakdown legend items (Downloaded imagery, Submitted forms, photos, etc., Free,
      // and Other)
      Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StorageLegendRowItem(
          color = StorageChartColors.downloadedImageryColor,
          label = stringResource(Res.string.mobile_device_storage_downloaded_imagery),
          value = storage.savedImagery.toUiText().resolve(),
        )
        StorageLegendRowItem(
          color = StorageChartColors.dataColor,
          label = stringResource(Res.string.mobile_device_storage_data),
          value = storage.data.toUiText().resolve(),
        )
        StorageLegendRowItem(
          color = StorageChartColors.otherColor,
          label = stringResource(Res.string.mobile_device_storage_other),
          value = storage.otherUsed.toUiText().resolve(),
        )
        StorageLegendRowItem(
          color = StorageChartColors.freeColor,
          label = stringResource(Res.string.mobile_device_storage_free),
          value = storage.freeSpace.toUiText().resolve(),
        )
      }

      // Safeguard threshold reassurance notice
      Text(
        text = stringResource(Res.string.mobile_device_storage_safeguard_notice),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Stacked horizontal proportional bar chart illustrating device storage distribution. */
@Composable
fun DeviceStorageBreakdownChart(storage: DeviceStorageInfo, modifier: Modifier = Modifier) {
  val imageryColor = StorageChartColors.downloadedImageryColor
  val dataColor = StorageChartColors.dataColor
  val otherColor = StorageChartColors.otherColor
  val freeColor = StorageChartColors.freeColor

  val imageryFrac = storage.imageryFraction
  val dataFrac = storage.dataFraction
  val otherFrac = storage.otherUsedFraction
  val freeFrac = storage.freeFraction

  Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(freeColor)) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val canvasWidth = size.width
      val canvasHeight = size.height

      var currentX = 0f

      // 1. Downloaded Imagery slice
      val imageryWidth = canvasWidth * imageryFrac
      if (imageryWidth > 0f) {
        drawRect(
          color = imageryColor,
          topLeft = Offset(currentX, 0f),
          size = androidx.compose.ui.geometry.Size(imageryWidth, canvasHeight),
        )
        currentX += imageryWidth
      }

      // 2. Survey & form Data slice
      val dataWidth = canvasWidth * dataFrac
      if (dataWidth > 0f) {
        drawRect(
          color = dataColor,
          topLeft = Offset(currentX, 0f),
          size = androidx.compose.ui.geometry.Size(dataWidth, canvasHeight),
        )
        currentX += dataWidth
      }

      // 3. System & Other apps slice
      val otherWidth = canvasWidth * otherFrac
      if (otherWidth > 0f) {
        drawRect(
          color = otherColor,
          topLeft = Offset(currentX, 0f),
          size = androidx.compose.ui.geometry.Size(otherWidth, canvasHeight),
        )
        currentX += otherWidth
      }

      // 4. Remaining width is Free storage
      val freeWidth = (canvasWidth - currentX).coerceAtLeast(0f)
      if (freeWidth > 0f) {
        drawRect(
          color = freeColor,
          topLeft = Offset(currentX, 0f),
          size = androidx.compose.ui.geometry.Size(freeWidth, canvasHeight),
        )
      }
    }
  }
}

/** Single color-coded legend entry for [DeviceStorageBreakdownCard]. */
@Composable
private fun StorageLegendRowItem(
  color: Color,
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
    Row(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}

/** Semantic, high-contrast Material 3 harmonious colors for the device storage chart segments. */
internal object StorageChartColors {
  /** Downloaded satellite & vector map imagery: deep teal/blue (`#0288D1`). */
  val downloadedImageryColor: Color = Color(0xFF0288D1)

  /** Survey definitions, master data & submissions: forest green (`#2E7D32`). */
  val dataColor: Color = Color(0xFF2E7D32)

  /** OS system files and other applications: slate gray (`#9E9E9E`). */
  val otherColor: Color = Color(0xFF9E9E9E)

  /** Free / available device storage: light neutral tint (`#E0E0E0`). */
  val freeColor: Color = Color(0xFFE0E0E0)
}
