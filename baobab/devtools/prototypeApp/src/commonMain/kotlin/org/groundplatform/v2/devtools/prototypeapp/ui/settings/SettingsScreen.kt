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
package org.groundplatform.v2.devtools.prototypeapp.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.core.forms.ui.resources.Res
import org.groundplatform.v2.core.forms.ui.resources.general_title
import org.groundplatform.v2.core.forms.ui.resources.help_title
import org.groundplatform.v2.core.forms.ui.resources.length_imperial
import org.groundplatform.v2.core.forms.ui.resources.length_metric
import org.groundplatform.v2.core.forms.ui.resources.over_wifi_summary
import org.groundplatform.v2.core.forms.ui.resources.select_language_title
import org.groundplatform.v2.core.forms.ui.resources.select_units_title
import org.groundplatform.v2.core.forms.ui.resources.settings_title
import org.groundplatform.v2.core.forms.ui.resources.upload_media_title
import org.groundplatform.v2.core.forms.ui.resources.visit_website_title
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceStorageInfo
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LANGUAGE_OPTIONS
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UserSettings
import org.groundplatform.v2.devtools.prototypeapp.domain.model.WEBSITE_URL
import org.groundplatform.v2.devtools.prototypeapp.ui.navigation.DeviceStorageBreakdownCard
import org.groundplatform.v2.devtools.prototypeapp.ui.onboarding.SignInScreen
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SettingsUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SettingsActions
import org.jetbrains.compose.resources.stringResource

/** Option item for [SettingsSelectItem], matching `Option` in `ground-android`. */
data class SettingsOption(val label: String, val value: String)

/**
 * Settings screen ported from `org.groundplatform.android.ui.settings.SettingsScreen` in
 * `github.com/google/ground-android`.
 */
@Composable
fun SettingsScreen(uiState: SettingsUiState, actions: SettingsActions, onBack: () -> Unit) {
  SettingsScreen(
    settings = uiState.settings,
    storage = uiState.storage,
    onUploadMediaOverUnmeteredConnectionOnlyChange =
      actions::updateUploadMediaOverUnmeteredConnectionOnly,
    onLanguageChange = actions::updateLanguage,
    onMeasurementUnitsChange = actions::updateMeasurementUnits,
    onVisitWebsiteClick = { actions.visitWebsite(WEBSITE_URL) },
    onBack = onBack,
    visitedWebsiteNotice = uiState.visitedWebsiteUrl,
  )
}

/** Backward-compatible alias for [SettingsScreen] bound to the app shell. */
@Composable
fun GroundSettingsScreen(
  state: PrototypeAppState,
  onBack: () -> Unit = { state.closeDrawerSubView() },
) {
  val uiState by state.settings.uiState.collectAsState()
  SettingsScreen(uiState = uiState, actions = state.settings, onBack = onBack)
}

/**
 * Stateless `SettingsScreen` composable ported from
 * `app/src/main/java/org/groundplatform/android/ui/settings/SettingsScreen.kt` in
 * `github.com/google/ground-android`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  settings: UserSettings,
  storage: DeviceStorageInfo = DeviceStorageInfo(),
  onUploadMediaOverUnmeteredConnectionOnlyChange: (Boolean) -> Unit,
  onLanguageChange: (String) -> Unit,
  onMeasurementUnitsChange: (MeasurementUnitSystem) -> Unit,
  onVisitWebsiteClick: () -> Unit,
  onBack: () -> Unit,
  visitedWebsiteNotice: String? = null,
) {
  val languageOptions = remember {
    LANGUAGE_OPTIONS.map { SettingsOption(label = it.label, value = it.code) }
  }
  val metricLabel = stringResource(Res.string.length_metric)
  val imperialLabel = stringResource(Res.string.length_imperial)
  val lengthOptions =
    remember(metricLabel, imperialLabel) {
      listOf(
        SettingsOption(label = metricLabel, value = MeasurementUnitSystem.METRIC.name),
        SettingsOption(label = imperialLabel, value = MeasurementUnitSystem.IMPERIAL.name),
      )
    }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(Res.string.settings_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())
    ) {
      // General Section
      SettingsCategory(title = stringResource(Res.string.general_title)) {
        // Upload Media (Over Wi-Fi only)
        SettingsSwitchItem(
          icon = Icons.Outlined.CloudUpload,
          title = stringResource(Res.string.upload_media_title),
          summary = stringResource(Res.string.over_wifi_summary),
          checked = settings.shouldUploadPhotosOnWifiOnly,
          onCheckedChange = onUploadMediaOverUnmeteredConnectionOnlyChange,
        )

        // Select Language
        SettingsSelectItem(
          icon = Icons.Outlined.Language,
          title = stringResource(Res.string.select_language_title),
          options = languageOptions,
          currentValue = settings.language,
          onValueChanged = onLanguageChange,
        )

        // Measurement Units
        SettingsSelectItem(
          icon = Icons.Outlined.Straighten,
          title = stringResource(Res.string.select_units_title),
          options = lengthOptions,
          currentValue = settings.measurementUnits.name,
          onValueChanged = { onMeasurementUnitsChange(MeasurementUnitSystem.valueOf(it)) },
        )
      }

      HorizontalDivider()

      // Device Storage Section
      SettingsCategory(title = "Storage") {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
          DeviceStorageBreakdownCard(storage = storage)
        }
      }

      HorizontalDivider()

      // Help Section
      SettingsCategory(title = stringResource(Res.string.help_title)) {
        SettingsItem(
          icon = Icons.AutoMirrored.Outlined.OpenInNew,
          title = stringResource(Res.string.visit_website_title),
          summary = WEBSITE_URL,
          onClick = onVisitWebsiteClick,
        )
      }

      if (visitedWebsiteNotice != null) {
        Surface(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Outlined.Check,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Opened $visitedWebsiteNotice",
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
      }
    }
  }
}

/**
 * A composable that groups related settings under a labeled category. Ported from
 * `SettingsCategory.kt` in `github.com/google/ground-android`.
 */
@Composable
fun SettingsCategory(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
    content()
  }
}

/**
 * A reusable UI component representing a single row in a settings screen. Ported from
 * `SettingsItem.kt` in `github.com/google/ground-android`.
 */
@Composable
fun SettingsItem(
  modifier: Modifier = Modifier,
  icon: ImageVector,
  title: String,
  summary: String? = null,
  trailingContent: (@Composable () -> Unit)? = null,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      modifier.fillMaxWidth().clickable(onClick = onClick, role = Role.Button).padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      modifier = Modifier.padding(end = 16.dp).size(24.dp),
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (summary != null) {
        Text(
          text = summary,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (trailingContent != null) {
      trailingContent()
    }
  }
}

/**
 * A settings item that allows users to select a single value from a list of options. When clicked,
 * it displays a dropdown menu with options populated from [options]. Ported from
 * `SettingsSelectItem.kt` in `github.com/google/ground-android`.
 */
@Composable
fun SettingsSelectItem(
  icon: ImageVector,
  title: String,
  options: List<SettingsOption>,
  currentValue: String,
  onValueChanged: (String) -> Unit,
  modifier: Modifier = Modifier,
  trailingContent: (@Composable () -> Unit)? = null,
) {
  val selectedOption = options.find { it.value == currentValue } ?: options.firstOrNull()
  var expanded by remember { mutableStateOf(false) }

  Box(modifier = modifier.fillMaxWidth()) {
    SettingsItem(
      icon = icon,
      title = title,
      summary = selectedOption?.label ?: "",
      trailingContent = trailingContent,
      onClick = { expanded = true },
    )

    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      offset = DpOffset(16.dp, 0.dp),
      modifier = Modifier.widthIn(min = 200.dp),
    ) {
      options.forEach { option ->
        val isSelected = option.value == selectedOption?.value
        DropdownMenuItem(
          text = {
            Text(
              text = option.label,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
          },
          trailingIcon =
            if (isSelected) {
              {
                Icon(
                  imageVector = Icons.Outlined.Check,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
              }
            } else {
              null
            },
          onClick = {
            onValueChanged(option.value)
            expanded = false
          },
        )
      }
    }
  }
}

/**
 * A reusable settings item component with a title, optional summary, and a switch toggle. Ported
 * from `SettingsSwitchItem.kt` in `github.com/google/ground-android`.
 */
@Composable
fun SettingsSwitchItem(
  modifier: Modifier = Modifier,
  icon: ImageVector,
  title: String,
  summary: String? = null,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
        .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      modifier = Modifier.padding(end = 16.dp).size(24.dp),
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (summary != null) {
        Text(
          text = summary,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Switch(checked = checked, onCheckedChange = null)
  }
}

/**
 * Language selector component for the Sign In / Login screen ([SignInScreen]), built using the same
 * [SettingsSelectItem] and `ic_language` icon ([Icons.Outlined.Language]) as the `ground-android`
 * Settings screen.
 */
@Composable
fun SignInLanguageSelector(
  languageCode: String,
  onLanguageChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val languageOptions = remember {
    LANGUAGE_OPTIONS.map { SettingsOption(label = it.label, value = it.code) }
  }

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    SettingsSelectItem(
      icon = Icons.Outlined.Language,
      title = stringResource(Res.string.select_language_title),
      options = languageOptions,
      currentValue = languageCode,
      onValueChanged = onLanguageChange,
      trailingContent = {
        Icon(
          imageVector = Icons.Outlined.KeyboardArrowDown,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
    )
  }
}
