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
package org.groundplatform.v2.devtools.prototypeapp

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Straighten
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Localized strings for the Settings screen and Login screen ported directly from
 * `github.com/google/ground-android` (`app/src/main/res/values-<lang>/strings.xml`).
 */
data class GroundLocalizedStrings(
  val settingsTitle: String,
  val generalTitle: String,
  val uploadMediaTitle: String,
  val overWifiSummary: String,
  val selectLanguageTitle: String,
  val selectUnitsTitle: String,
  val lengthMetric: String,
  val lengthImperial: String,
  val helpTitle: String,
  val visitWebsiteTitle: String,
  val signInWithGoogle: String,
)

/** Returns the [GroundLocalizedStrings] catalog for the given ISO language [code]. */
fun groundLocalizedStringsFor(code: String): GroundLocalizedStrings =
  when (code.lowercase()) {
    "fr" ->
      GroundLocalizedStrings(
        settingsTitle = "Paramètres",
        generalTitle = "Configuration générale",
        uploadMediaTitle = "Télécharger des médias",
        overWifiSummary = "Uniquement par Wi-Fi",
        selectLanguageTitle = "Choix de la langue",
        selectUnitsTitle = "Sélectionner les unités",
        lengthMetric = "Métrique",
        lengthImperial = "Impérial",
        helpTitle = "Aide",
        visitWebsiteTitle = "Visiter le site web",
        signInWithGoogle = "Se connecter avec Google",
      )
    "es" ->
      GroundLocalizedStrings(
        settingsTitle = "Configuración",
        generalTitle = "General",
        uploadMediaTitle = "Subir fotos",
        overWifiSummary = "Sólo a través de Wi-Fi",
        selectLanguageTitle = "Seleccionar idioma",
        selectUnitsTitle = "Seleccionar unidades",
        lengthMetric = "Métrica",
        lengthImperial = "Imperial",
        helpTitle = "Ayuda",
        visitWebsiteTitle = "Visitar sitio web",
        signInWithGoogle = "Iniciar sesión con Google",
      )
    "pt" ->
      GroundLocalizedStrings(
        settingsTitle = "Configurações",
        generalTitle = "Geral",
        uploadMediaTitle = "Enviar fotos",
        overWifiSummary = "Somente via Wi-Fi",
        selectLanguageTitle = "Selecionar idioma",
        selectUnitsTitle = "Selecionar unidades",
        lengthMetric = "Métrica",
        lengthImperial = "Imperial",
        helpTitle = "Ajuda",
        visitWebsiteTitle = "Visitar site",
        signInWithGoogle = "Faça login com o Google",
      )
    "vi" ->
      GroundLocalizedStrings(
        settingsTitle = "Cài đặt",
        generalTitle = "Chung",
        uploadMediaTitle = "Tải ảnh lên",
        overWifiSummary = "Chỉ khi có Wi-Fi",
        selectLanguageTitle = "Chọn ngôn ngữ",
        selectUnitsTitle = "Chọn đơn vị",
        lengthMetric = "Hệ mét",
        lengthImperial = "Hệ Anh",
        helpTitle = "Trợ giúp",
        visitWebsiteTitle = "Truy cập trang web",
        signInWithGoogle = "Đăng nhập bằng Google",
      )
    "th" ->
      GroundLocalizedStrings(
        settingsTitle = "การตั้งค่า",
        generalTitle = "ทั่วไป",
        uploadMediaTitle = "อัปโหลดรูปภาพ",
        overWifiSummary = "ผ่าน Wi-Fi เท่านั้น",
        selectLanguageTitle = "เลือกภาษา",
        selectUnitsTitle = "เลือกหน่วยวัด",
        lengthMetric = "เมตริก",
        lengthImperial = "อิมพีเรียล",
        helpTitle = "ช่วยเหลือ",
        visitWebsiteTitle = "เยี่ยมชมเว็บไซต์",
        signInWithGoogle = "ลงชื่อเข้าใช้ด้วย Google",
      )
    "lo" ->
      GroundLocalizedStrings(
        settingsTitle = "ການຕັ້ງຄ່າ",
        generalTitle = "ທົ່ວໄປ",
        uploadMediaTitle = "ອັບໂຫຼດຮູບພາບ",
        overWifiSummary = "ໃຊ້ໄດ້ສະເພາະ Wi-Fi ເທົ່ານັ້ນ",
        selectLanguageTitle = "ເລືອກພາສາ",
        selectUnitsTitle = "ເລືອກຫົວໜ່ວຍ",
        lengthMetric = "ເມັດຕຣິກ",
        lengthImperial = "ອິມພີເຣຍວ",
        helpTitle = "ຊ່ວຍເຫຼືອ",
        visitWebsiteTitle = "ເຂົ້າເບິ່ງເວັບໄຊ",
        signInWithGoogle = "ເຂົ້າລະບົບດ້ວຍ Google",
      )
    "km" ->
      GroundLocalizedStrings(
        settingsTitle = "ការកំណត់",
        generalTitle = "ទូទៅ",
        uploadMediaTitle = "ផ្ទុករូបថតឡើង",
        overWifiSummary = "តាមរយះការប្រើ Wi-Fi តែប៉ុណ្ណោះ",
        selectLanguageTitle = "ជ្រើសរើសភាសា",
        selectUnitsTitle = "ជ្រើសរើសឯកតា",
        lengthMetric = "ម៉ែត្រ",
        lengthImperial = "អ៊ីមភេរីយ៉ាល់",
        helpTitle = "ជំនួយ",
        visitWebsiteTitle = "ចូលមើលគេហទំព័រ",
        signInWithGoogle = "ចូលដោយប្រើ Google",
      )
    "sw" ->
      GroundLocalizedStrings(
        settingsTitle = "Mipangilio",
        generalTitle = "Jumla",
        uploadMediaTitle = "Pakia picha",
        overWifiSummary = "Kupitia Wi-Fi pekee",
        selectLanguageTitle = "Chagua lugha",
        selectUnitsTitle = "Chagua vipimo",
        lengthMetric = "Metriki",
        lengthImperial = "Imperiali",
        helpTitle = "Msaada",
        visitWebsiteTitle = "Tembelea tovuti",
        signInWithGoogle = "Ingia kwa kutumia Google",
      )
    else ->
      GroundLocalizedStrings(
        settingsTitle = "Settings",
        generalTitle = "General",
        uploadMediaTitle = "Upload photos",
        overWifiSummary = "Over Wi-Fi only",
        selectLanguageTitle = "Select language",
        selectUnitsTitle = "Select units",
        lengthMetric = "Metric",
        lengthImperial = "Imperial",
        helpTitle = "Help",
        visitWebsiteTitle = "Visit website",
        signInWithGoogle = "Sign in with Google",
      )
  }

/** Option item for [SettingsSelectItem], matching `Option` in `ground-android`. */
data class SettingsOption(
  val label: String,
  val value: String,
)

/**
 * Settings screen ported from `org.groundplatform.android.ui.settings.SettingsScreen` in
 * `github.com/google/ground-android`.
 *
 * Connected to [PrototypeAppState] for interactive KMP Compose prototyping.
 */
@Composable
fun GroundSettingsScreen(
  state: PrototypeAppState,
  onBack: () -> Unit = { state.closeDrawerSubView() },
) {
  SettingsScreen(
    settings = state.userSettings,
    onUploadMediaOverUnmeteredConnectionOnlyChange = {
      state.updateUploadMediaOverUnmeteredConnectionOnly(it)
    },
    onLanguageChange = { state.updateSelectedLanguage(it) },
    onMeasurementUnitsChange = { state.updateUnitSystem(it) },
    onVisitWebsiteClick = { state.visitGroundWebsite(GROUND_WEBSITE_URL) },
    onBack = onBack,
    visitedWebsiteNotice = state.visitedWebsiteUrl,
  )
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
  onUploadMediaOverUnmeteredConnectionOnlyChange: (Boolean) -> Unit,
  onLanguageChange: (String) -> Unit,
  onMeasurementUnitsChange: (MeasurementUnitSystem) -> Unit,
  onVisitWebsiteClick: () -> Unit,
  onBack: () -> Unit,
  visitedWebsiteNotice: String? = null,
) {
  val strings = remember(settings.language) { groundLocalizedStringsFor(settings.language) }
  val languageOptions = remember {
    GROUND_LANGUAGE_OPTIONS.map { SettingsOption(label = it.label, value = it.code) }
  }
  val lengthOptions =
    remember(strings) {
      listOf(
        SettingsOption(label = strings.lengthMetric, value = MeasurementUnitSystem.METRIC.name),
        SettingsOption(label = strings.lengthImperial, value = MeasurementUnitSystem.IMPERIAL.name),
      )
    }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = strings.settingsTitle) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
            )
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
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
    ) {
      // General Section
      SettingsCategory(title = strings.generalTitle) {
        // Upload Media (Over Wi-Fi only)
        SettingsSwitchItem(
          icon = Icons.Filled.CloudUpload,
          title = strings.uploadMediaTitle,
          summary = strings.overWifiSummary,
          checked = settings.shouldUploadPhotosOnWifiOnly,
          onCheckedChange = onUploadMediaOverUnmeteredConnectionOnlyChange,
        )

        // Select Language
        SettingsSelectItem(
          icon = Icons.Filled.Language,
          title = strings.selectLanguageTitle,
          options = languageOptions,
          currentValue = settings.language,
          onValueChanged = onLanguageChange,
        )

        // Measurement Units
        SettingsSelectItem(
          icon = Icons.Filled.Straighten,
          title = strings.selectUnitsTitle,
          options = lengthOptions,
          currentValue = settings.measurementUnits.name,
          onValueChanged = { onMeasurementUnitsChange(MeasurementUnitSystem.valueOf(it)) },
        )
      }

      HorizontalDivider()

      // Help Section
      SettingsCategory(title = strings.helpTitle) {
        SettingsItem(
          icon = Icons.Filled.OpenInNew,
          title = strings.visitWebsiteTitle,
          summary = GROUND_WEBSITE_URL,
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
              imageVector = Icons.Filled.Check,
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
 * A composable that groups related settings under a labeled category.
 * Ported from `SettingsCategory.kt` in `github.com/google/ground-android`.
 */
@Composable
fun SettingsCategory(
  title: String,
  content: @Composable ColumnScope.() -> Unit,
) {
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
 * A reusable UI component representing a single row in a settings screen.
 * Ported from `SettingsItem.kt` in `github.com/google/ground-android`.
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
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick, role = Role.Button)
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
 * A settings item that allows users to select a single value from a list of options.
 * When clicked, it displays a dropdown menu with options populated from [options].
 * Ported from `SettingsSelectItem.kt` in `github.com/google/ground-android`.
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
                  imageVector = Icons.Filled.Check,
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
 * A reusable settings item component with a title, optional summary, and a switch toggle.
 * Ported from `SettingsSwitchItem.kt` in `github.com/google/ground-android`.
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
 * Language selector component for the Sign In / Login screen (`GroundSignInScreen`), built using
 * the same [SettingsSelectItem] and `ic_language` icon (`[Icons.Filled.Language]`) as the
 * `ground-android` Settings screen.
 */
@Composable
fun SignInLanguageSelector(
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
) {
  val strings =
    remember(state.selectedLanguageCode) {
      groundLocalizedStringsFor(state.selectedLanguageCode)
    }
  val languageOptions = remember {
    GROUND_LANGUAGE_OPTIONS.map { SettingsOption(label = it.label, value = it.code) }
  }

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .border(
          1.dp,
          MaterialTheme.colorScheme.outlineVariant,
          MaterialTheme.shapes.medium,
        ),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    SettingsSelectItem(
      icon = Icons.Filled.Language,
      title = strings.selectLanguageTitle,
      options = languageOptions,
      currentValue = state.selectedLanguageCode,
      onValueChanged = { state.updateSelectedLanguage(it) },
      trailingContent = {
        Icon(
          imageVector = Icons.Default.KeyboardArrowDown,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
    )
  }
}
