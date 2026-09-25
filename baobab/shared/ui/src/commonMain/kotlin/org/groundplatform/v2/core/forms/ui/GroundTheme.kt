/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.core.forms.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.core.forms.ui.resources.Res
import org.groundplatform.v2.core.forms.ui.resources.google_sans_flex_bold
import org.groundplatform.v2.core.forms.ui.resources.google_sans_flex_extrabold
import org.groundplatform.v2.core.forms.ui.resources.google_sans_flex_medium
import org.groundplatform.v2.core.forms.ui.resources.google_sans_flex_regular
import org.groundplatform.v2.core.forms.ui.resources.google_sans_flex_semibold
import org.jetbrains.compose.resources.Font

/**
 * Complete Material 3 Light ColorScheme using the exact tokens from the Figma Ground Design System:
 * - Primary (Key #34A853): `primary` #36693E, `onPrimary` #FFFFFF, `primaryContainer` #B7F1B9, `onPrimaryContainer` #1D5128, `inversePrimary` #9CD49F
 * - Secondary (Key #526350): `secondary` #516351, `onSecondary` #FFFFFF, `secondaryContainer` #D4E8D1, `onSecondaryContainer` #3A4B3A
 * - Tertiary (Key #39656B): `tertiary` #39656C, `onTertiary` #FFFFFF, `tertiaryContainer` #BDEAF3, `onTertiaryContainer` #1F4D54
 * - Neutral (Key #5D5F5B) & Neutral Variant (Key #596057):
 *     `background` #F7FBF2, `onBackground` #181D18,
 *     `surface` #F7FBF2, `onSurface` #181D18,
 *     `surfaceDim` #D7DBD3, `surfaceBright` #F7FBF2,
 *     `surfaceContainerLowest` #FFFFFF, `surfaceContainerLow` #F1F5EC,
 *     `surfaceContainer` #EBEFE7, `surfaceContainerHigh` #E5E9E1, `surfaceContainerHighest` #E0E4DB,
 *     `surfaceVariant` #DDE5D9, `onSurfaceVariant` #424940,
 *     `outline` #727970, `outlineVariant` #C1C9BE,
 *     `inverseSurface` #2D322C, `inverseOnSurface` #EEF2E9
 * - Error (Key #BA1A1A): `error` #BA1A1A, `onError` #FFFFFF, `errorContainer` #FFDAD6, `onErrorContainer` #93000A
 */
val GroundLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF36693E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F1B9),
    onPrimaryContainer = Color(0xFF1D5128),
    inversePrimary = Color(0xFF9CD49F),
    secondary = Color(0xFF516351),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4E8D1),
    onSecondaryContainer = Color(0xFF3A4B3A),
    tertiary = Color(0xFF39656C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBDEAF3),
    onTertiaryContainer = Color(0xFF1F4D54),
    background = Color(0xFFF7FBF2),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFF7FBF2),
    onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDDE5D9),
    onSurfaceVariant = Color(0xFF424940),
    surfaceTint = Color(0xFF36693E),
    inverseSurface = Color(0xFF2D322C),
    inverseOnSurface = Color(0xFFEEF2E9),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF727970),
    outlineVariant = Color(0xFFC1C9BE),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF7FBF2),
    surfaceDim = Color(0xFFD7DBD3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5EC),
    surfaceContainer = Color(0xFFEBEFE7),
    surfaceContainerHigh = Color(0xFFE5E9E1),
    surfaceContainerHighest = Color(0xFFE0E4DB),
  )

/**
 * Complete Material 3 Dark ColorScheme using the exact tokens from the Figma Ground Design System:
 * - Primary: `primary` #9CD49F, `onPrimary` #003914, `primaryContainer` #1D5128, `onPrimaryContainer` #B7F1B9, `inversePrimary` #36693E
 * - Secondary: `secondary` #B8CCB5, `onSecondary` #243425, `secondaryContainer` #3A4B3A, `onSecondaryContainer` #D4E8D1
 * - Tertiary: `tertiary` #A1CED7, `onTertiary` #00363D, `tertiaryContainer` #1F4D54, `onTertiaryContainer` #BDEAF3
 * - Neutral & Neutral Variant:
 *     `background` #101510, `onBackground` #E0E4DB,
 *     `surface` #101510, `onSurface` #E0E4DB,
 *     `surfaceDim` #101510, `surfaceBright` #363A35,
 *     `surfaceContainerLowest` #0B0F0B, `surfaceContainerLow` #181D18,
 *     `surfaceContainer` #1C211C, `surfaceContainerHigh` #272B26, `surfaceContainerHighest` #313630,
 *     `surfaceVariant` #424940, `onSurfaceVariant` #C1C9BE,
 *     `outline` #8B9389, `outlineVariant` #424940,
 *     `inverseSurface` #E0E4DB, `inverseOnSurface` #2D322C
 * - Error: `error` #FFB4AB, `onError` #690005, `errorContainer` #93000A, `onErrorContainer` #FFDAD6
 */
val GroundDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF9CD49F),
    onPrimary = Color(0xFF003914),
    primaryContainer = Color(0xFF1D5128),
    onPrimaryContainer = Color(0xFFB7F1B9),
    inversePrimary = Color(0xFF36693E),
    secondary = Color(0xFFB8CCB5),
    onSecondary = Color(0xFF243425),
    secondaryContainer = Color(0xFF3A4B3A),
    onSecondaryContainer = Color(0xFFD4E8D1),
    tertiary = Color(0xFFA1CED7),
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF1F4D54),
    onTertiaryContainer = Color(0xFFBDEAF3),
    background = Color(0xFF101510),
    onBackground = Color(0xFFE0E4DB),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFE0E4DB),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC1C9BE),
    surfaceTint = Color(0xFF9CD49F),
    inverseSurface = Color(0xFFE0E4DB),
    inverseOnSurface = Color(0xFF2D322C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8B9389),
    outlineVariant = Color(0xFF424940),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF363A35),
    surfaceDim = Color(0xFF101510),
    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF181D18),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF272B26),
    surfaceContainerHighest = Color(0xFF313630),
  )

/** Standard Material Design 3 5-tier shape scale (`extraSmall` 4dp through `extraLarge` 28dp). */
val GroundShapes =
  Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

/** Baseline Material Design 3 15-role typography scale before Compose font resource resolution. */
val GroundTypography = Typography()

/** CompositionLocal providing the Google Sans Flex [FontFamily] for Ground brand and UI text. */
val LocalGroundBrandFontFamily = compositionLocalOf<FontFamily> { FontFamily.Default }

/**
 * Google Sans Flex font family (`400`..`800`) used across all brand, heading, title, body, and
 * label typography roles in Ground 2.0.
 */
@Composable
fun groundFontFamily(): FontFamily =
  FontFamily(
    Font(Res.font.google_sans_flex_regular, FontWeight.Normal),
    Font(Res.font.google_sans_flex_medium, FontWeight.Medium),
    Font(Res.font.google_sans_flex_semibold, FontWeight.SemiBold),
    Font(Res.font.google_sans_flex_bold, FontWeight.Bold),
    Font(Res.font.google_sans_flex_extrabold, FontWeight.ExtraBold),
  )

/** Google Sans Flex font family used for the "Ground" brand wordmark. */
@Composable fun groundBrandFontFamily(): FontFamily = groundFontFamily()

/** Google Sans Flex font family used for headings (`display*`, `headline*`, `title*`). */
@Composable fun groundHeadingFontFamily(): FontFamily = groundFontFamily()

/** Google Sans Flex font family used for normal text (`body*`, `label*`). */
@Composable fun groundBodyFontFamily(): FontFamily = groundFontFamily()

/**
 * Returns the Ground 2.0 Material Design 3 15-role [Typography] scale unified on **Google Sans
 * Flex** ([groundFontFamily]).
 */
@Composable
fun groundTypography(fontFamily: FontFamily = groundFontFamily()): Typography {
  val baseline = GroundTypography
  return Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = fontFamily),
    displayMedium = baseline.displayMedium.copy(fontFamily = fontFamily),
    displaySmall = baseline.displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = baseline.titleLarge.copy(fontFamily = fontFamily),
    titleMedium = baseline.titleMedium.copy(fontFamily = fontFamily),
    titleSmall = baseline.titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = baseline.bodySmall.copy(fontFamily = fontFamily),
    labelLarge = baseline.labelLarge.copy(fontFamily = fontFamily),
    labelMedium = baseline.labelMedium.copy(fontFamily = fontFamily),
    labelSmall = baseline.labelSmall.copy(fontFamily = fontFamily),
  )
}

/** Returns [baseStyle] styled with the Ground brand [FontFamily] (**Google Sans Flex**). */
@Composable
fun groundBrandTextStyle(baseStyle: TextStyle = MaterialTheme.typography.titleLarge): TextStyle =
  baseStyle.copy(fontFamily = LocalGroundBrandFontFamily.current)

/** Semantic tonal color roles for M3 pill badges and status indicators. */
enum class GroundBadgeTone {
  PRIMARY,
  SECONDARY,
  TERTIARY,
  WARNING,
  ERROR,
  NEUTRAL,
}

/**
 * Reusable Material 3 tonal pill badge backed by `MaterialTheme.colorScheme` container roles.
 */
@Composable
fun GroundTonalBadge(
  text: String,
  tone: GroundBadgeTone = GroundBadgeTone.PRIMARY,
  outlined: Boolean = false,
  monospace: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val (containerColor, contentColor, borderColor) =
    when (tone) {
      GroundBadgeTone.PRIMARY ->
        Triple(colors.primaryContainer, colors.onPrimaryContainer, colors.primary)
      GroundBadgeTone.SECONDARY ->
        Triple(colors.secondaryContainer, colors.onSecondaryContainer, colors.secondary)
      GroundBadgeTone.TERTIARY ->
        Triple(colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary)
      GroundBadgeTone.WARNING ->
        Triple(colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary)
      GroundBadgeTone.ERROR ->
        Triple(colors.errorContainer, colors.onErrorContainer, colors.error)
      GroundBadgeTone.NEUTRAL ->
        Triple(colors.surfaceContainerHigh, colors.onSurfaceVariant, colors.outlineVariant)
    }
  Surface(
    modifier = modifier,
    shape = CircleShape,
    color = containerColor,
    contentColor = contentColor,
    border = if (outlined) BorderStroke(1.dp, borderColor) else null,
  ) {
    Text(
      text = text,
      style =
        MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.SemiBold,
        ),
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

/**
 * Material 3 Modal Bottom Sheet rendered strictly within its parent container bounds (rather than a
 * root Window Popup), ensuring sheets stay inside `MobileDevicePreviewFrame` when running in the
 * Web UX Workbench.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroundModalBottomSheetOverlay(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Box(modifier = modifier.fillMaxSize()) {
    // M3 Modal Scrim confined to the mobile preview frame
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
          .clickable(onClick = onDismissRequest)
    )
    // M3 Bottom Sheet Container
    Surface(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .heightIn(max = 560.dp)
          .clickable(enabled = false) {},
      shape = BottomSheetDefaults.ExpandedShape,
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      contentColor = MaterialTheme.colorScheme.onSurface,
      tonalElevation = BottomSheetDefaults.Elevation,
      shadowElevation = 12.dp,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        BottomSheetDefaults.DragHandle()
        content()
      }
    }
  }
}

/**
 * Material 3 AlertDialog rendered strictly within its parent container bounds (rather than a root
 * Window Dialog), ensuring dialogs stay centered inside `MobileDevicePreviewFrame`.
 */
@Composable
fun GroundAlertDialogOverlay(
  onDismissRequest: () -> Unit,
  title: @Composable () -> Unit,
  text: @Composable () -> Unit,
  confirmButton: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  icon: (@Composable () -> Unit)? = null,
  dismissButton: (@Composable () -> Unit)? = null,
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    // M3 Modal Scrim confined to the mobile preview frame
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
          .clickable(onClick = onDismissRequest)
    )
    // M3 Dialog Container
    Surface(
      modifier =
        Modifier.padding(horizontal = 24.dp)
          .widthIn(min = 280.dp, max = 340.dp)
          .clickable(enabled = false) {},
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      contentColor = MaterialTheme.colorScheme.onSurface,
      tonalElevation = 6.dp,
      shadowElevation = 8.dp,
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (icon != null) {
          Box(modifier = Modifier.padding(bottom = 16.dp)) {
            icon()
          }
        }
        Box(modifier = Modifier.padding(bottom = 16.dp)) {
          title()
        }
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
          text()
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (dismissButton != null) {
            dismissButton()
            Spacer(modifier = Modifier.width(8.dp))
          }
          confirmButton()
        }
      }
    }
  }
}

/**
 * Shared Ground 2.0 Material 3 theme for Mobile and Web surfaces unified on **Google Sans Flex**
 * ([groundFontFamily]) across all brand, heading, title, body, and label typography roles.
 */
@Composable
fun GroundTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
  val colorScheme = if (darkTheme) GroundDarkColorScheme else GroundLightColorScheme
  val fontFamily = groundFontFamily()
  val typography = groundTypography(fontFamily)
  CompositionLocalProvider(LocalGroundBrandFontFamily provides fontFamily) {
    MaterialTheme(
      colorScheme = colorScheme,
      shapes = GroundShapes,
      typography = typography,
      content = content,
    )
  }
}

