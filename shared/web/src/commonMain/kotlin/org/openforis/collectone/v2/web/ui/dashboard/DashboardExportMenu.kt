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
package org.openforis.collectone.v2.web.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.usecase.ExportFile
import org.openforis.collectone.v2.app.domain.usecase.ProfileExportPlan
import org.openforis.collectone.v2.app.ui.dashboard.DashboardActions
import org.openforis.collectone.v2.app.ui.dashboard.DatasetExportOptions
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_cancel
import org.openforis.collectone.v2.web.resources.web_download
import org.openforis.collectone.v2.web.resources.web_download_csv
import org.openforis.collectone.v2.web.resources.web_download_geojson
import org.openforis.collectone.v2.web.resources.web_download_kml
import org.openforis.collectone.v2.web.resources.web_download_survey_kml
import org.openforis.collectone.v2.web.resources.web_export_anyway
import org.openforis.collectone.v2.web.resources.web_export_profile_for_survey_purposes
import org.openforis.collectone.v2.web.resources.web_export_profile_included
import org.openforis.collectone.v2.web.resources.web_export_profile_link_missing_hint
import org.openforis.collectone.v2.web.resources.web_export_profile_title
import org.openforis.collectone.v2.web.resources.web_export_profile_warnings_to_review
import org.openforis.collectone.v2.web.resources.web_open_in_google_earth
import org.openforis.collectone.v2.web.ui.common.downloadTextFile
import org.openforis.collectone.v2.web.ui.common.openGoogleEarthDeepLink
import org.openforis.collectone.v2.web.ui.common.platformBalloonAppUrl
import org.openforis.collectone.v2.web.ui.common.platformServiceUrl

/**
 * **Download** menu of a dashboard data table: CSV, GeoJSON, KML / Open in Google Earth (Map
 * layers), and the export profiles the survey's purposes enable for datasets with linked fields
 * (such as EUDR GeoJSON). A profile with unlinked fields lists them before exporting; the export
 * still runs. Every download is recorded as an export event.
 */
@Composable
internal fun DashboardExportButton(
  datasetId: String,
  buildLocalCsvTable: () -> DashboardDataTable,
  buildCsvTableFromEntities:
    (List<org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem>) -> DashboardDataTable,
  options: DatasetExportOptions?,
  actions: DashboardActions,
  download: (ExportFile) -> Unit = { file ->
    downloadTextFile(fileName = file.fileName, mimeType = file.mimeType, content = file.content)
  },
) {
  var expanded by remember { mutableStateOf(false) }
  var confirming by remember { mutableStateOf<ProfileExportPlan?>(null) }
  val scope = rememberCoroutineScope()
  val lazyEnabled = actions.tableRepository()?.isLazyLoadingEnabled == true

  fun exportProfile(plan: ProfileExportPlan) {
    if (lazyEnabled) {
      scope.launch { actions.exportWithProfileAsync(datasetId, plan.profile.id)?.let(download) }
    } else {
      actions.exportWithProfile(datasetId, plan.profile.id)?.let(download)
    }
  }

  fun exportCsv() {
    if (lazyEnabled) {
      scope.launch {
        val fullEntities = actions.loadEntitiesForExport(datasetId)
        val csvTable =
          if (fullEntities.isNotEmpty()) buildCsvTableFromEntities(fullEntities)
          else buildLocalCsvTable()
        downloadCsvTable(csvTable, datasetId, actions, download)
      }
    } else {
      downloadCsvTable(buildLocalCsvTable(), datasetId, actions, download)
    }
  }

  Box {
    OutlinedButton(
      onClick = { expanded = true },
      modifier = Modifier.height(32.dp),
      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.Download,
        contentDescription = stringResource(Res.string.web_download),
        modifier = Modifier.size(16.dp),
      )
      Spacer(Modifier.width(6.dp))
      Text(stringResource(Res.string.web_download), style = MaterialTheme.typography.labelMedium)
      Spacer(Modifier.width(4.dp))
      Icon(
        imageVector = Icons.Outlined.ArrowDropDown,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      DropdownMenuItem(
        text = { Text(stringResource(Res.string.web_download_csv)) },
        leadingIcon = { Icon(Icons.Outlined.TableChart, contentDescription = null) },
        onClick = {
          expanded = false
          exportCsv()
        },
      )
      if (options?.hasGeometry == true) {
        DashboardGeospatialExportItems(
          datasetId = datasetId,
          showOpenInGoogleEarth = options.hasGoogleEarthForms,
          actions = actions,
          lazyEnabled = lazyEnabled,
          scope = scope,
          download = download,
          onDismiss = { expanded = false },
        )
      }
      val plans = options?.profilePlans.orEmpty().filter { plan -> plan.fields.any { it.isMapped } }
      if (plans.isNotEmpty()) {
        HorizontalDivider()
        plans.forEach { plan ->
          ProfileExportMenuItem(
            plan = plan,
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

private fun downloadCsvTable(
  csvTable: DashboardDataTable,
  datasetId: String,
  actions: DashboardActions,
  download: (ExportFile) -> Unit,
) {
  download(
    ExportFile(
      fileName = dashboardTableCsvFileName(csvTable),
      mimeType = "text/csv;charset=utf-8",
      content = buildDashboardTableCsv(csvTable),
    )
  )
  actions.recordCsvExport(datasetId)
}

@Composable
private fun DashboardGeospatialExportItems(
  datasetId: String,
  showOpenInGoogleEarth: Boolean,
  actions: DashboardActions,
  lazyEnabled: Boolean,
  scope: CoroutineScope,
  download: (ExportFile) -> Unit,
  onDismiss: () -> Unit,
) {
  DropdownMenuItem(
    text = { Text(stringResource(Res.string.web_download_geojson)) },
    leadingIcon = { Icon(Icons.Outlined.Map, contentDescription = null) },
    onClick = {
      onDismiss()
      if (lazyEnabled) {
        scope.launch { actions.exportGeoJsonAsync(datasetId)?.let(download) }
      } else {
        actions.exportGeoJson(datasetId)?.let(download)
      }
    },
  )
  if (showOpenInGoogleEarth) {
    DropdownMenuItem(
      text = { Text(stringResource(Res.string.web_open_in_google_earth)) },
      leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null) },
      trailingIcon = {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
      },
      onClick = {
        onDismiss()
        actions
          .googleEarthOpenUrl(
            balloonAppUrl = platformBalloonAppUrl(),
            serviceUrl = platformServiceUrl(),
          )
          ?.let { earthUrl -> openGoogleEarthDeepLink(earthUrl = earthUrl) }
      },
    )
  }
  DropdownMenuItem(
    text = { Text(stringResource(Res.string.web_download_kml)) },
    leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
    onClick = {
      onDismiss()
      val balloon = platformBalloonAppUrl()
      val service = platformServiceUrl()
      if (lazyEnabled) {
        scope.launch { actions.exportKmlAsync(datasetId, balloon, service)?.let(download) }
      } else {
        actions
          .exportKml(datasetId = datasetId, balloonAppUrl = balloon, serviceUrl = service)
          ?.let(download)
      }
    },
  )
  DropdownMenuItem(
    text = { Text(stringResource(Res.string.web_download_survey_kml)) },
    leadingIcon = { Icon(Icons.Outlined.Layers, contentDescription = null) },
    onClick = {
      onDismiss()
      val balloon = platformBalloonAppUrl()
      val service = platformServiceUrl()
      if (lazyEnabled) {
        scope.launch { actions.exportSurveyKmlAsync(balloon, service)?.let(download) }
      } else {
        actions.exportSurveyKml(balloonAppUrl = balloon, serviceUrl = service)?.let(download)
      }
    },
  )
}

@Composable
private fun ProfileExportMenuItem(plan: ProfileExportPlan, onClick: () -> Unit) {
  DropdownMenuItem(
    text = {
      Column {
        Text(plan.profile.title.text)
        Text(
          if (plan.warnings.isEmpty()) {
            stringResource(Res.string.web_export_profile_for_survey_purposes)
          } else {
            pluralStringResource(
              Res.plurals.web_export_profile_warnings_to_review,
              plan.warnings.size,
              plan.warnings.size,
            )
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    leadingIcon = { Icon(Icons.Outlined.Verified, contentDescription = null) },
    onClick = onClick,
  )
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
    title = { Text(stringResource(Res.string.web_export_profile_title, plan.profile.title.text)) },
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
            stringResource(
              Res.string.web_export_profile_included,
              mapped.joinToString { it.label },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          stringResource(Res.string.web_export_profile_link_missing_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Button(onClick = onExport) { Text(stringResource(Res.string.web_export_anyway)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.web_cancel)) }
    },
  )
}
