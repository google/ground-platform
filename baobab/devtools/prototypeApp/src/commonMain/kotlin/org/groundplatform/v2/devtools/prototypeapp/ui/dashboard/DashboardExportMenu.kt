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
package org.groundplatform.v2.devtools.prototypeapp.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ExportFile
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ProfileExportPlan
import org.groundplatform.v2.devtools.prototypeapp.ui.common.downloadTextFile
import org.groundplatform.v2.devtools.prototypeapp.ui.state.DatasetExportOptions
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.DashboardActions

/**
 * **Export** menu of a dashboard data table: CSV, GeoJSON (Map layers), and the export profiles the
 * survey's purposes enable (such as EUDR GeoJSON). A profile with unlinked fields lists them before
 * exporting; the export still runs. Every download is recorded as an export event.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardExportButton(
  table: DashboardDataTable,
  options: DatasetExportOptions?,
  actions: DashboardActions,
  download: (ExportFile) -> Unit = { file ->
    downloadTextFile(fileName = file.fileName, mimeType = file.mimeType, content = file.content)
  },
) {
  var expanded by remember { mutableStateOf(false) }
  var confirming by remember { mutableStateOf<ProfileExportPlan?>(null) }

  fun exportProfile(plan: ProfileExportPlan) {
    actions.exportWithProfile(table.datasetId, plan.profile.id)?.let(download)
  }

  Box {
    TooltipBox(
      positionProvider =
        TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
      tooltip = { PlainTooltip { Text("Export") } },
      state = rememberTooltipState(),
    ) {
      IconButton(onClick = { expanded = true }) {
        Icon(imageVector = Icons.Outlined.Download, contentDescription = "Export")
      }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      DropdownMenuItem(
        text = { Text("Download CSV") },
        leadingIcon = { Icon(Icons.Outlined.TableChart, contentDescription = null) },
        onClick = {
          expanded = false
          download(
            ExportFile(
              fileName = dashboardTableCsvFileName(table),
              mimeType = "text/csv;charset=utf-8",
              content = buildDashboardTableCsv(table),
            )
          )
          actions.recordCsvExport(table.datasetId)
        },
      )
      if (options?.hasGeometry == true) {
        DropdownMenuItem(
          text = { Text("Download GeoJSON") },
          leadingIcon = { Icon(Icons.Outlined.Map, contentDescription = null) },
          onClick = {
            expanded = false
            actions.exportGeoJson(table.datasetId)?.let(download)
          },
        )
      }
      val plans = options?.profilePlans.orEmpty()
      if (plans.isNotEmpty()) {
        HorizontalDivider()
        plans.forEach { plan ->
          DropdownMenuItem(
            text = {
              Column {
                Text(plan.profile.title.text)
                Text(
                  if (plan.warnings.isEmpty()) "For your survey's purposes"
                  else "${plan.warnings.size} to review before export",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            },
            leadingIcon = { Icon(Icons.Outlined.Verified, contentDescription = null) },
            onClick = {
              expanded = false
              if (plan.warnings.isEmpty()) exportProfile(plan) else confirming = plan
            },
          )
        }
      }
    }
  }

  confirming?.let { plan ->
    ProfileExportWarningsDialog(
      plan = plan,
      onExport = {
        confirming = null
        exportProfile(plan)
      },
      onDismiss = { confirming = null },
    )
  }
}

/** Lists what an export profile couldn't fill in, before exporting anyway. */
@Composable
private fun ProfileExportWarningsDialog(
  plan: ProfileExportPlan,
  onExport: () -> Unit,
  onDismiss: () -> Unit,
) {
  val mapped = plan.fields.filter { it.isMapped }
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(Icons.Outlined.Warning, contentDescription = null) },
    title = { Text("Export ${plan.profile.title.text}?") },
    text = {
      Column(
        modifier = Modifier.widthIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        plan.warnings.forEach { warning ->
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Text("•", style = MaterialTheme.typography.bodyMedium)
            Text(warning, style = MaterialTheme.typography.bodyMedium)
          }
        }
        if (mapped.isNotEmpty()) {
          Text(
            "Included: " + mapped.joinToString { it.label } + ".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          "Link the missing questions to standard fields in the Form editor to include them " +
            "next time.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = { Button(onClick = onExport) { Text("Export anyway") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
