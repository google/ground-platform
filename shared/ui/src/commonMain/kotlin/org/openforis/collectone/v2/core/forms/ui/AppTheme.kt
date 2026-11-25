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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font as UiFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.Font
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.backdropClickable
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.google_sans_code
import org.openforis.collectone.v2.core.forms.ui.resources.google_sans_flex_bold
import org.openforis.collectone.v2.core.forms.ui.resources.google_sans_flex_extrabold
import org.openforis.collectone.v2.core.forms.ui.resources.google_sans_flex_medium
import org.openforis.collectone.v2.core.forms.ui.resources.google_sans_flex_regular
import org.openforis.collectone.v2.core.forms.ui.resources.google_sans_flex_semibold

/** Contrast levels defined in the Figma design system token modes. */
enum class AppContrast {
  STANDARD,
  MEDIUM,
  HIGH,
}

/** Brand color palettes supported by the shared form UI. */
enum class AppThemePalette {
  /** Collect One default forest-green palette. */
  COLLECT_ONE,
  /** Google Earth (Terrarium Design System) palette for embedded KML balloon forms. */
  GOOGLE_EARTH,
}

/**
 * Figma `Shadow` token (`#000000`, `VariableID:12230:23124`), shared across all six design system
 * theme and contrast modes.
 */
val AppShadowColor = Color(0xFF000000)

/**
 * Complete Material 3 `Light` ColorScheme using the exact tokens from the Figma design system:
 * - Primary: `primary` `#36693E`, `onPrimary` `#FFFFFF`, `primaryContainer` `#B7F1B9`,
 *   `onPrimaryContainer` `#1D5128`, `inversePrimary` `#9CD49F`, `surfaceTint` `#36693E`
 * - Secondary: `secondary` `#516351`, `onSecondary` `#FFFFFF`, `secondaryContainer` `#D4E8D1`,
 *   `onSecondaryContainer` `#3A4B3A`
 * - Tertiary: `tertiary` `#39656C`, `onTertiary` `#FFFFFF`, `tertiaryContainer` `#BDEAF3`,
 *   `onTertiaryContainer` `#1F4D54`
 * - Neutral & Neutral Variant: `background` `#F7FBF2`, `onBackground` `#181D18`, `surface`
 *   `#F7FBF2`, `onSurface` `#181D18`, `surfaceDim` `#D7DBD3`, `surfaceBright` `#F7FBF2`,
 *   `surfaceContainerLowest` `#FFFFFF`, `surfaceContainerLow` `#F1F5EC`, `surfaceContainer`
 *   `#EBEFE7`, `surfaceContainerHigh` `#E5E9E1`, `surfaceContainerHighest` `#E0E4DB`,
 *   `surfaceVariant` `#DDE5D9`, `onSurfaceVariant` `#424940`, `outline` `#727970`, `outlineVariant`
 *   `#C1C9BE`, `scrim` `#000000`, `inverseSurface` `#2D322C`, `inverseOnSurface` `#EEF2E9`
 * - Error: `error` `#BA1A1A`, `onError` `#FFFFFF`, `errorContainer` `#FFDAD6`, `onErrorContainer`
 *   `#93000A`
 * - Fixed: `primaryFixed` `#B7F1B9`, `primaryFixedDim` `#9CD49F`, `onPrimaryFixed` `#002108`,
 *   `onPrimaryFixedVariant` `#1D5128`, `secondaryFixed` `#D4E8D1`, `secondaryFixedDim` `#B8CCB5`,
 *   `onSecondaryFixed` `#0F1F11`, `onSecondaryFixedVariant` `#3A4B3A`, `tertiaryFixed` `#BDEAF3`,
 *   `tertiaryFixedDim` `#A1CED7`, `onTertiaryFixed` `#001F24`, `onTertiaryFixedVariant` `#1F4D54`
 */
val AppLightColorScheme =
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
    primaryFixed = Color(0xFFB7F1B9),
    primaryFixedDim = Color(0xFF9CD49F),
    onPrimaryFixed = Color(0xFF002108),
    onPrimaryFixedVariant = Color(0xFF1D5128),
    secondaryFixed = Color(0xFFD4E8D1),
    secondaryFixedDim = Color(0xFFB8CCB5),
    onSecondaryFixed = Color(0xFF0F1F11),
    onSecondaryFixedVariant = Color(0xFF3A4B3A),
    tertiaryFixed = Color(0xFFBDEAF3),
    tertiaryFixedDim = Color(0xFFA1CED7),
    onTertiaryFixed = Color(0xFF001F24),
    onTertiaryFixedVariant = Color(0xFF1F4D54),
  )

/**
 * Complete Material 3 `Light Medium Contrast` ColorScheme using the exact tokens from the Figma
 * design system:
 * - Primary: `primary` `#073F19`, `onPrimary` `#FFFFFF`, `primaryContainer` `#45784B`,
 *   `onPrimaryContainer` `#FFFFFF`, `inversePrimary` `#9CD49F`, `surfaceTint` `#36693E`
 * - Secondary: `secondary` `#293A2A`, `onSecondary` `#FFFFFF`, `secondaryContainer` `#5F725F`,
 *   `onSecondaryContainer` `#FFFFFF`
 * - Tertiary: `tertiary` `#083C43`, `onTertiary` `#FFFFFF`, `tertiaryContainer` `#48747B`,
 *   `onTertiaryContainer` `#FFFFFF`
 * - Neutral & Neutral Variant: `background` `#F7FBF2`, `onBackground` `#181D18`, `surface`
 *   `#F7FBF2`, `onSurface` `#0E120E`, `surfaceDim` `#C3C8C0`, `surfaceBright` `#F7FBF2`,
 *   `surfaceContainerLowest` `#FFFFFF`, `surfaceContainerLow` `#F1F5EC`, `surfaceContainer`
 *   `#E5E9E1`, `surfaceContainerHigh` `#DADED6`, `surfaceContainerHighest` `#CFD3CB`,
 *   `surfaceVariant` `#DDE5D9`, `onSurfaceVariant` `#313830`, `outline` `#4D544C`, `outlineVariant`
 *   `#686F66`, `scrim` `#000000`, `inverseSurface` `#2D322C`, `inverseOnSurface` `#EEF2E9`
 * - Error: `error` `#740006`, `onError` `#FFFFFF`, `errorContainer` `#CF2C27`, `onErrorContainer`
 *   `#FFFFFF`
 * - Fixed: `primaryFixed` `#45784B`, `primaryFixedDim` `#2C5F35`, `onPrimaryFixed` `#FFFFFF`,
 *   `onPrimaryFixedVariant` `#FFFFFF`, `secondaryFixed` `#5F725F`, `secondaryFixedDim` `#485947`,
 *   `onSecondaryFixed` `#FFFFFF`, `onSecondaryFixedVariant` `#FFFFFF`, `tertiaryFixed` `#48747B`,
 *   `tertiaryFixedDim` `#2F5B63`, `onTertiaryFixed` `#FFFFFF`, `onTertiaryFixedVariant` `#FFFFFF`
 */
val AppLightMediumContrastColorScheme =
  lightColorScheme(
    primary = Color(0xFF073F19),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF45784B),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF9CD49F),
    secondary = Color(0xFF293A2A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF5F725F),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF083C43),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF48747B),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFF7FBF2),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFF7FBF2),
    onSurface = Color(0xFF0E120E),
    surfaceVariant = Color(0xFFDDE5D9),
    onSurfaceVariant = Color(0xFF313830),
    surfaceTint = Color(0xFF36693E),
    inverseSurface = Color(0xFF2D322C),
    inverseOnSurface = Color(0xFFEEF2E9),
    error = Color(0xFF740006),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFCF2C27),
    onErrorContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF4D544C),
    outlineVariant = Color(0xFF686F66),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF7FBF2),
    surfaceDim = Color(0xFFC3C8C0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5EC),
    surfaceContainer = Color(0xFFE5E9E1),
    surfaceContainerHigh = Color(0xFFDADED6),
    surfaceContainerHighest = Color(0xFFCFD3CB),
    primaryFixed = Color(0xFF45784B),
    primaryFixedDim = Color(0xFF2C5F35),
    onPrimaryFixed = Color(0xFFFFFFFF),
    onPrimaryFixedVariant = Color(0xFFFFFFFF),
    secondaryFixed = Color(0xFF5F725F),
    secondaryFixedDim = Color(0xFF485947),
    onSecondaryFixed = Color(0xFFFFFFFF),
    onSecondaryFixedVariant = Color(0xFFFFFFFF),
    tertiaryFixed = Color(0xFF48747B),
    tertiaryFixedDim = Color(0xFF2F5B63),
    onTertiaryFixed = Color(0xFFFFFFFF),
    onTertiaryFixedVariant = Color(0xFFFFFFFF),
  )

/**
 * Complete Material 3 `Light High Contrast` ColorScheme using the exact tokens from the Figma
 * design system:
 * - Primary: `primary` `#003411`, `onPrimary` `#FFFFFF`, `primaryContainer` `#20532A`,
 *   `onPrimaryContainer` `#FFFFFF`, `inversePrimary` `#9CD49F`, `surfaceTint` `#36693E`
 * - Secondary: `secondary` `#203021`, `onSecondary` `#FFFFFF`, `secondaryContainer` `#3C4D3C`,
 *   `onSecondaryContainer` `#FFFFFF`
 * - Tertiary: `tertiary` `#003138`, `onTertiary` `#FFFFFF`, `tertiaryContainer` `#224F57`,
 *   `onTertiaryContainer` `#FFFFFF`
 * - Neutral & Neutral Variant: `background` `#F7FBF2`, `onBackground` `#181D18`, `surface`
 *   `#F7FBF2`, `onSurface` `#000000`, `surfaceDim` `#B6BAB2`, `surfaceBright` `#F7FBF2`,
 *   `surfaceContainerLowest` `#FFFFFF`, `surfaceContainerLow` `#EEF2E9`, `surfaceContainer`
 *   `#E0E4DB`, `surfaceContainerHigh` `#D1D6CD`, `surfaceContainerHighest` `#C3C8C0`,
 *   `surfaceVariant` `#DDE5D9`, `onSurfaceVariant` `#000000`, `outline` `#272E27`, `outlineVariant`
 *   `#444B43`, `scrim` `#000000`, `inverseSurface` `#2D322C`, `inverseOnSurface` `#FFFFFF`
 * - Error: `error` `#600004`, `onError` `#FFFFFF`, `errorContainer` `#98000A`, `onErrorContainer`
 *   `#FFFFFF`
 * - Fixed: `primaryFixed` `#20532A`, `primaryFixedDim` `#033C16`, `onPrimaryFixed` `#FFFFFF`,
 *   `onPrimaryFixedVariant` `#FFFFFF`, `secondaryFixed` `#3C4D3C`, `secondaryFixedDim` `#263727`,
 *   `onSecondaryFixed` `#FFFFFF`, `onSecondaryFixedVariant` `#FFFFFF`, `tertiaryFixed` `#224F57`,
 *   `tertiaryFixedDim` `#03383F`, `onTertiaryFixed` `#FFFFFF`, `onTertiaryFixedVariant` `#FFFFFF`
 */
val AppLightHighContrastColorScheme =
  lightColorScheme(
    primary = Color(0xFF003411),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF20532A),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF9CD49F),
    secondary = Color(0xFF203021),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF3C4D3C),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF003138),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF224F57),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFF7FBF2),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFF7FBF2),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFDDE5D9),
    onSurfaceVariant = Color(0xFF000000),
    surfaceTint = Color(0xFF36693E),
    inverseSurface = Color(0xFF2D322C),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFF600004),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF98000A),
    onErrorContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF272E27),
    outlineVariant = Color(0xFF444B43),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF7FBF2),
    surfaceDim = Color(0xFFB6BAB2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF2E9),
    surfaceContainer = Color(0xFFE0E4DB),
    surfaceContainerHigh = Color(0xFFD1D6CD),
    surfaceContainerHighest = Color(0xFFC3C8C0),
    primaryFixed = Color(0xFF20532A),
    primaryFixedDim = Color(0xFF033C16),
    onPrimaryFixed = Color(0xFFFFFFFF),
    onPrimaryFixedVariant = Color(0xFFFFFFFF),
    secondaryFixed = Color(0xFF3C4D3C),
    secondaryFixedDim = Color(0xFF263727),
    onSecondaryFixed = Color(0xFFFFFFFF),
    onSecondaryFixedVariant = Color(0xFFFFFFFF),
    tertiaryFixed = Color(0xFF224F57),
    tertiaryFixedDim = Color(0xFF03383F),
    onTertiaryFixed = Color(0xFFFFFFFF),
    onTertiaryFixedVariant = Color(0xFFFFFFFF),
  )

/**
 * Complete Material 3 `Dark` ColorScheme using the exact tokens from the Figma design system:
 * - Primary: `primary` `#9CD49F`, `onPrimary` `#003914`, `primaryContainer` `#1D5128`,
 *   `onPrimaryContainer` `#B7F1B9`, `inversePrimary` `#36693E`, `surfaceTint` `#9CD49F`
 * - Secondary: `secondary` `#B8CCB5`, `onSecondary` `#243425`, `secondaryContainer` `#3A4B3A`,
 *   `onSecondaryContainer` `#D4E8D1`
 * - Tertiary: `tertiary` `#A1CED7`, `onTertiary` `#00363D`, `tertiaryContainer` `#1F4D54`,
 *   `onTertiaryContainer` `#BDEAF3`
 * - Neutral & Neutral Variant: `background` `#101510`, `onBackground` `#E0E4DB`, `surface`
 *   `#101510`, `onSurface` `#E0E4DB`, `surfaceDim` `#101510`, `surfaceBright` `#363A35`,
 *   `surfaceContainerLowest` `#0B0F0B`, `surfaceContainerLow` `#181D18`, `surfaceContainer`
 *   `#1C211C`, `surfaceContainerHigh` `#272B26`, `surfaceContainerHighest` `#313630`,
 *   `surfaceVariant` `#424940`, `onSurfaceVariant` `#C1C9BE`, `outline` `#8B9389`, `outlineVariant`
 *   `#424940`, `scrim` `#000000`, `inverseSurface` `#E0E4DB`, `inverseOnSurface` `#2D322C`
 * - Error: `error` `#FFB4AB`, `onError` `#690005`, `errorContainer` `#93000A`, `onErrorContainer`
 *   `#FFDAD6`
 * - Fixed: `primaryFixed` `#B7F1B9`, `primaryFixedDim` `#9CD49F`, `onPrimaryFixed` `#002108`,
 *   `onPrimaryFixedVariant` `#1D5128`, `secondaryFixed` `#D4E8D1`, `secondaryFixedDim` `#B8CCB5`,
 *   `onSecondaryFixed` `#0F1F11`, `onSecondaryFixedVariant` `#3A4B3A`, `tertiaryFixed` `#BDEAF3`,
 *   `tertiaryFixedDim` `#A1CED7`, `onTertiaryFixed` `#001F24`, `onTertiaryFixedVariant` `#1F4D54`
 */
val AppDarkColorScheme =
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
    primaryFixed = Color(0xFFB7F1B9),
    primaryFixedDim = Color(0xFF9CD49F),
    onPrimaryFixed = Color(0xFF002108),
    onPrimaryFixedVariant = Color(0xFF1D5128),
    secondaryFixed = Color(0xFFD4E8D1),
    secondaryFixedDim = Color(0xFFB8CCB5),
    onSecondaryFixed = Color(0xFF0F1F11),
    onSecondaryFixedVariant = Color(0xFF3A4B3A),
    tertiaryFixed = Color(0xFFBDEAF3),
    tertiaryFixedDim = Color(0xFFA1CED7),
    onTertiaryFixed = Color(0xFF001F24),
    onTertiaryFixedVariant = Color(0xFF1F4D54),
  )

/**
 * Complete Material 3 `Dark Medium Contrast` ColorScheme using the exact tokens from the Figma
 * design system:
 * - Primary: `primary` `#B1EAB4`, `onPrimary` `#002D0E`, `primaryContainer` `#689D6D`,
 *   `onPrimaryContainer` `#000000`, `inversePrimary` `#1E5229`, `surfaceTint` `#9CD49F`
 * - Secondary: `secondary` `#CEE2CB`, `onSecondary` `#19291A`, `secondaryContainer` `#839681`,
 *   `onSecondaryContainer` `#000000`
 * - Tertiary: `tertiary` `#B7E4ED`, `onTertiary` `#002A30`, `tertiaryContainer` `#6C98A0`,
 *   `onTertiaryContainer` `#000000`
 * - Neutral & Neutral Variant: `background` `#101510`, `onBackground` `#E0E4DB`, `surface`
 *   `#101510`, `onSurface` `#FFFFFF`, `surfaceDim` `#101510`, `surfaceBright` `#414640`,
 *   `surfaceContainerLowest` `#050805`, `surfaceContainerLow` `#1A1F1A`, `surfaceContainer`
 *   `#242924`, `surfaceContainerHigh` `#2F342E`, `surfaceContainerHighest` `#3A3F39`,
 *   `surfaceVariant` `#424940`, `onSurfaceVariant` `#D7DED3`, `outline` `#ADB4AA`, `outlineVariant`
 *   `#8B9289`, `scrim` `#000000`, `inverseSurface` `#E0E4DB`, `inverseOnSurface` `#272B26`
 * - Error: `error` `#FFD2CC`, `onError` `#540003`, `errorContainer` `#FF5449`, `onErrorContainer`
 *   `#000000`
 * - Fixed: `primaryFixed` `#B7F1B9`, `primaryFixedDim` `#9CD49F`, `onPrimaryFixed` `#001504`,
 *   `onPrimaryFixedVariant` `#073F19`, `secondaryFixed` `#D4E8D1`, `secondaryFixedDim` `#B8CCB5`,
 *   `onSecondaryFixed` `#051407`, `onSecondaryFixedVariant` `#293A2A`, `tertiaryFixed` `#BDEAF3`,
 *   `tertiaryFixedDim` `#A1CED7`, `onTertiaryFixed` `#001417`, `onTertiaryFixedVariant` `#083C43`
 */
val AppDarkMediumContrastColorScheme =
  darkColorScheme(
    primary = Color(0xFFB1EAB4),
    onPrimary = Color(0xFF002D0E),
    primaryContainer = Color(0xFF689D6D),
    onPrimaryContainer = Color(0xFF000000),
    inversePrimary = Color(0xFF1E5229),
    secondary = Color(0xFFCEE2CB),
    onSecondary = Color(0xFF19291A),
    secondaryContainer = Color(0xFF839681),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFFB7E4ED),
    onTertiary = Color(0xFF002A30),
    tertiaryContainer = Color(0xFF6C98A0),
    onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFF101510),
    onBackground = Color(0xFFE0E4DB),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFD7DED3),
    surfaceTint = Color(0xFF9CD49F),
    inverseSurface = Color(0xFFE0E4DB),
    inverseOnSurface = Color(0xFF272B26),
    error = Color(0xFFFFD2CC),
    onError = Color(0xFF540003),
    errorContainer = Color(0xFFFF5449),
    onErrorContainer = Color(0xFF000000),
    outline = Color(0xFFADB4AA),
    outlineVariant = Color(0xFF8B9289),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF414640),
    surfaceDim = Color(0xFF101510),
    surfaceContainerLowest = Color(0xFF050805),
    surfaceContainerLow = Color(0xFF1A1F1A),
    surfaceContainer = Color(0xFF242924),
    surfaceContainerHigh = Color(0xFF2F342E),
    surfaceContainerHighest = Color(0xFF3A3F39),
    primaryFixed = Color(0xFFB7F1B9),
    primaryFixedDim = Color(0xFF9CD49F),
    onPrimaryFixed = Color(0xFF001504),
    onPrimaryFixedVariant = Color(0xFF073F19),
    secondaryFixed = Color(0xFFD4E8D1),
    secondaryFixedDim = Color(0xFFB8CCB5),
    onSecondaryFixed = Color(0xFF051407),
    onSecondaryFixedVariant = Color(0xFF293A2A),
    tertiaryFixed = Color(0xFFBDEAF3),
    tertiaryFixedDim = Color(0xFFA1CED7),
    onTertiaryFixed = Color(0xFF001417),
    onTertiaryFixedVariant = Color(0xFF083C43),
  )

/**
 * Complete Material 3 `Dark High Contrast` ColorScheme using the exact tokens from the Figma design
 * system:
 * - Primary: `primary` `#C4FEC6`, `onPrimary` `#000000`, `primaryContainer` `#98D09B`,
 *   `onPrimaryContainer` `#000F03`, `inversePrimary` `#1E5229`, `surfaceTint` `#9CD49F`
 * - Secondary: `secondary` `#E1F6DE`, `onSecondary` `#000000`, `secondaryContainer` `#B4C8B2`,
 *   `onSecondaryContainer` `#020E03`
 * - Tertiary: `tertiary` `#CEF7FF`, `onTertiary` `#000000`, `tertiaryContainer` `#9DCAD3`,
 *   `onTertiaryContainer` `#000E10`
 * - Neutral & Neutral Variant: `background` `#101510`, `onBackground` `#E0E4DB`, `surface`
 *   `#101510`, `onSurface` `#FFFFFF`, `surfaceDim` `#101510`, `surfaceBright` `#4D514B`,
 *   `surfaceContainerLowest` `#000000`, `surfaceContainerLow` `#1C211C`, `surfaceContainer`
 *   `#2D322C`, `surfaceContainerHigh` `#383D37`, `surfaceContainerHighest` `#434842`,
 *   `surfaceVariant` `#424940`, `onSurfaceVariant` `#FFFFFF`, `outline` `#EBF2E7`, `outlineVariant`
 *   `#BDC5BA`, `scrim` `#000000`, `inverseSurface` `#E0E4DB`, `inverseOnSurface` `#000000`
 * - Error: `error` `#FFECE9`, `onError` `#000000`, `errorContainer` `#FFAEA4`, `onErrorContainer`
 *   `#220001`
 * - Fixed: `primaryFixed` `#B7F1B9`, `primaryFixedDim` `#9CD49F`, `onPrimaryFixed` `#000000`,
 *   `onPrimaryFixedVariant` `#001504`, `secondaryFixed` `#D4E8D1`, `secondaryFixedDim` `#B8CCB5`,
 *   `onSecondaryFixed` `#000000`, `onSecondaryFixedVariant` `#051407`, `tertiaryFixed` `#BDEAF3`,
 *   `tertiaryFixedDim` `#A1CED7`, `onTertiaryFixed` `#000000`, `onTertiaryFixedVariant` `#001417`
 */
val AppDarkHighContrastColorScheme =
  darkColorScheme(
    primary = Color(0xFFC4FEC6),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF98D09B),
    onPrimaryContainer = Color(0xFF000F03),
    inversePrimary = Color(0xFF1E5229),
    secondary = Color(0xFFE1F6DE),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFB4C8B2),
    onSecondaryContainer = Color(0xFF020E03),
    tertiary = Color(0xFFCEF7FF),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF9DCAD3),
    onTertiaryContainer = Color(0xFF000E10),
    background = Color(0xFF101510),
    onBackground = Color(0xFFE0E4DB),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFFFFFFF),
    surfaceTint = Color(0xFF9CD49F),
    inverseSurface = Color(0xFFE0E4DB),
    inverseOnSurface = Color(0xFF000000),
    error = Color(0xFFFFECE9),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFFFFAEA4),
    onErrorContainer = Color(0xFF220001),
    outline = Color(0xFFEBF2E7),
    outlineVariant = Color(0xFFBDC5BA),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF4D514B),
    surfaceDim = Color(0xFF101510),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF1C211C),
    surfaceContainer = Color(0xFF2D322C),
    surfaceContainerHigh = Color(0xFF383D37),
    surfaceContainerHighest = Color(0xFF434842),
    primaryFixed = Color(0xFFB7F1B9),
    primaryFixedDim = Color(0xFF9CD49F),
    onPrimaryFixed = Color(0xFF000000),
    onPrimaryFixedVariant = Color(0xFF001504),
    secondaryFixed = Color(0xFFD4E8D1),
    secondaryFixedDim = Color(0xFFB8CCB5),
    onSecondaryFixed = Color(0xFF000000),
    onSecondaryFixedVariant = Color(0xFF051407),
    tertiaryFixed = Color(0xFFBDEAF3),
    tertiaryFixedDim = Color(0xFFA1CED7),
    onTertiaryFixed = Color(0xFF000000),
    onTertiaryFixedVariant = Color(0xFF001417),
  )

/** Returns the Collect One [ColorScheme] for the given [darkTheme] and [contrast] mode. */
fun appColorScheme(
  darkTheme: Boolean = false,
  contrast: AppContrast = AppContrast.STANDARD,
): ColorScheme =
  when (contrast) {
    AppContrast.STANDARD -> if (darkTheme) AppDarkColorScheme else AppLightColorScheme
    AppContrast.MEDIUM ->
      if (darkTheme) AppDarkMediumContrastColorScheme else AppLightMediumContrastColorScheme
    AppContrast.HIGH ->
      if (darkTheme) AppDarkHighContrastColorScheme else AppLightHighContrastColorScheme
  }

/**
 * Google Earth (Terrarium Design System `v0.3.6`) `Light` ColorScheme for embedded KML balloon
 * forms:
 * - Primary: `primary` `#0B57D0`, `onPrimary` `#FFFFFF`, `primaryContainer` `#D3E3FD`,
 *   `onPrimaryContainer` `#041E49`, `inversePrimary` `#A8C7FA`
 * - Secondary: `secondary` `#00639B`, `onSecondary` `#FFFFFF`, `secondaryContainer` `#C2E7FF`,
 *   `onSecondaryContainer` `#001D35`
 * - Tertiary: `tertiary` `#146C2E`, `onTertiary` `#FFFFFF`, `tertiaryContainer` `#C4EED0`,
 *   `onTertiaryContainer` `#072711`
 * - Neutral & Surface: `background` `#FFFFFF`, `onBackground` `#1F1F1F`, `surface` `#FFFFFF`,
 *   `onSurface` `#1F1F1F`, `surfaceDim` `#D3DBE5`, `surfaceBright` `#FFFFFF`,
 *   `surfaceContainerLowest` `#FFFFFF`, `surfaceContainerLow` `#F8FAFD`, `surfaceContainer`
 *   `#F0F4F9`, `surfaceContainerHigh` `#E9EEF6`, `surfaceContainerHighest` `#DDE3EA`,
 *   `surfaceVariant` `#E1E3E1`, `onSurfaceVariant` `#444746`, `outline` `#747775`, `outlineVariant`
 *   `#C4C7C5`, `scrim` `#000000`, `inverseSurface` `#303030`, `inverseOnSurface` `#F2F2F2`
 * - Error: `error` `#B3261E`, `onError` `#FFFFFF`, `errorContainer` `#F9DEDC`, `onErrorContainer`
 *   `#410E0B`
 */
val EarthLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    inversePrimary = Color(0xFFA8C7FA),
    secondary = Color(0xFF00639B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC2E7FF),
    onSecondaryContainer = Color(0xFF001D35),
    tertiary = Color(0xFF146C2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC4EED0),
    onTertiaryContainer = Color(0xFF072711),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE1E3E1),
    onSurfaceVariant = Color(0xFF444746),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF303030),
    inverseOnSurface = Color(0xFFF2F2F2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF747775),
    outlineVariant = Color(0xFFC4C7C5),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD3DBE5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFF0F4F9),
    surfaceContainerHigh = Color(0xFFE9EEF6),
    surfaceContainerHighest = Color(0xFFDDE3EA),
    primaryFixed = Color(0xFFD3E3FD),
    primaryFixedDim = Color(0xFFA8C7FA),
    onPrimaryFixed = Color(0xFF041E49),
    onPrimaryFixedVariant = Color(0xFF0842A0),
    secondaryFixed = Color(0xFFC2E7FF),
    secondaryFixedDim = Color(0xFF7FCFFF),
    onSecondaryFixed = Color(0xFF001D35),
    onSecondaryFixedVariant = Color(0xFF004A77),
    tertiaryFixed = Color(0xFFC4EED0),
    tertiaryFixedDim = Color(0xFF6DD58C),
    onTertiaryFixed = Color(0xFF072711),
    onTertiaryFixedVariant = Color(0xFF0F5223),
  )

/** Google Earth (Terrarium Design System `v0.3.6`) `Light High Contrast` ColorScheme. */
val EarthLightHighContrastColorScheme =
  lightColorScheme(
    primary = Color(0xFF041E49),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFD3E3FD),
    secondary = Color(0xFF001D35),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF004A77),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF072711),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF0F5223),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE1E3E1),
    onSurfaceVariant = Color(0xFF1F1F1F),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF303030),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFF410E0B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF444746),
    outlineVariant = Color(0xFF444746),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFC4C7C5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F9),
    surfaceContainer = Color(0xFFE9EEF6),
    surfaceContainerHigh = Color(0xFFDDE3EA),
    surfaceContainerHighest = Color(0xFFD3DBE5),
    primaryFixed = Color(0xFF0842A0),
    primaryFixedDim = Color(0xFF041E49),
    onPrimaryFixed = Color(0xFFFFFFFF),
    onPrimaryFixedVariant = Color(0xFFFFFFFF),
    secondaryFixed = Color(0xFF004A77),
    secondaryFixedDim = Color(0xFF001D35),
    onSecondaryFixed = Color(0xFFFFFFFF),
    onSecondaryFixedVariant = Color(0xFFFFFFFF),
    tertiaryFixed = Color(0xFF0F5223),
    tertiaryFixedDim = Color(0xFF072711),
    onTertiaryFixed = Color(0xFFFFFFFF),
    onTertiaryFixedVariant = Color(0xFFFFFFFF),
  )

/**
 * Google Earth (Terrarium Design System `v0.3.6`) `Dark` ColorScheme for embedded KML balloon
 * forms:
 * - Primary: `primary` `#A8C7FA`, `onPrimary` `#062E6F`, `primaryContainer` `#0842A0`,
 *   `onPrimaryContainer` `#D3E3FD`, `inversePrimary` `#0B57D0`
 * - Secondary: `secondary` `#7FCFFF`, `onSecondary` `#003355`, `secondaryContainer` `#004A77`,
 *   `onSecondaryContainer` `#C2E7FF`
 * - Tertiary: `tertiary` `#6DD58C`, `onTertiary` `#0A3818`, `tertiaryContainer` `#0F5223`,
 *   `onTertiaryContainer` `#C4EED0`
 * - Neutral & Surface: `background` `#131314`, `onBackground` `#E3E3E3`, `surface` `#131314`,
 *   `onSurface` `#E3E3E3`, `surfaceDim` `#131314`, `surfaceBright` `#37393B`,
 *   `surfaceContainerLowest` `#0E0E0E`, `surfaceContainerLow` `#1B1B1B`, `surfaceContainer`
 *   `#1E1F20`, `surfaceContainerHigh` `#282A2C`, `surfaceContainerHighest` `#333537`,
 *   `surfaceVariant` `#444746`, `onSurfaceVariant` `#C4C7C5`, `outline` `#8E918F`, `outlineVariant`
 *   `#444746`, `scrim` `#000000`, `inverseSurface` `#E3E3E3`, `inverseOnSurface` `#303030`
 * - Error: `error` `#F2B8B5`, `onError` `#601410`, `errorContainer` `#8C1D18`, `onErrorContainer`
 *   `#F9DEDC`
 */
val EarthDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    inversePrimary = Color(0xFF0B57D0),
    secondary = Color(0xFF7FCFFF),
    onSecondary = Color(0xFF003355),
    secondaryContainer = Color(0xFF004A77),
    onSecondaryContainer = Color(0xFFC2E7FF),
    tertiary = Color(0xFF6DD58C),
    onTertiary = Color(0xFF0A3818),
    tertiaryContainer = Color(0xFF0F5223),
    onTertiaryContainer = Color(0xFFC4EED0),
    background = Color(0xFF131314),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF131314),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF444746),
    onSurfaceVariant = Color(0xFFC4C7C5),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFE3E3E3),
    inverseOnSurface = Color(0xFF303030),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF8E918F),
    outlineVariant = Color(0xFF444746),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF37393B),
    surfaceDim = Color(0xFF131314),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1B1B1B),
    surfaceContainer = Color(0xFF1E1F20),
    surfaceContainerHigh = Color(0xFF282A2C),
    surfaceContainerHighest = Color(0xFF333537),
    primaryFixed = Color(0xFFD3E3FD),
    primaryFixedDim = Color(0xFFA8C7FA),
    onPrimaryFixed = Color(0xFF041E49),
    onPrimaryFixedVariant = Color(0xFF0842A0),
    secondaryFixed = Color(0xFFC2E7FF),
    secondaryFixedDim = Color(0xFF7FCFFF),
    onSecondaryFixed = Color(0xFF001D35),
    onSecondaryFixedVariant = Color(0xFF004A77),
    tertiaryFixed = Color(0xFFC4EED0),
    tertiaryFixedDim = Color(0xFF6DD58C),
    onTertiaryFixed = Color(0xFF072711),
    onTertiaryFixedVariant = Color(0xFF0F5223),
  )

/** Google Earth (Terrarium Design System `v0.3.6`) `Dark High Contrast` ColorScheme. */
val EarthDarkHighContrastColorScheme =
  darkColorScheme(
    primary = Color(0xFFECF3FE),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFA8C7FA),
    onPrimaryContainer = Color(0xFF000000),
    inversePrimary = Color(0xFF0842A0),
    secondary = Color(0xFFE5F4FF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF7FCFFF),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFFE7F8ED),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF6DD58C),
    onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFF131314),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF131314),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF444746),
    onSurfaceVariant = Color(0xFFF2F2F2),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFE3E3E3),
    inverseOnSurface = Color(0xFF000000),
    error = Color(0xFFFCEEEE),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFFF2B8B5),
    onErrorContainer = Color(0xFF000000),
    outline = Color(0xFFC4C7C5),
    outlineVariant = Color(0xFFC4C7C5),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF444746),
    surfaceDim = Color(0xFF131314),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF1E1F20),
    surfaceContainer = Color(0xFF282A2C),
    surfaceContainerHigh = Color(0xFF333537),
    surfaceContainerHighest = Color(0xFF37393B),
    primaryFixed = Color(0xFF0842A0),
    primaryFixedDim = Color(0xFF041E49),
    onPrimaryFixed = Color(0xFFFFFFFF),
    onPrimaryFixedVariant = Color(0xFFFFFFFF),
    secondaryFixed = Color(0xFF004A77),
    secondaryFixedDim = Color(0xFF001D35),
    onSecondaryFixed = Color(0xFFFFFFFF),
    onSecondaryFixedVariant = Color(0xFFFFFFFF),
    tertiaryFixed = Color(0xFF0F5223),
    tertiaryFixedDim = Color(0xFF072711),
    onTertiaryFixed = Color(0xFFFFFFFF),
    onTertiaryFixedVariant = Color(0xFFFFFFFF),
  )

/** Returns the Google Earth (Terrarium) [ColorScheme] for the given [darkTheme] and [contrast]. */
fun earthColorScheme(
  darkTheme: Boolean = false,
  contrast: AppContrast = AppContrast.STANDARD,
): ColorScheme =
  when (contrast) {
    AppContrast.STANDARD,
    AppContrast.MEDIUM -> if (darkTheme) EarthDarkColorScheme else EarthLightColorScheme
    AppContrast.HIGH ->
      if (darkTheme) EarthDarkHighContrastColorScheme else EarthLightHighContrastColorScheme
  }

/**
 * Material 3 Fixed color roles for a specific contrast mode of the design system. Standard `Light`
 * and `Dark` share [AppStandardFixedColors], whereas `Medium` and `High` contrast modes adjust
 * fixed container and on-fixed tones for elevated legibility.
 */
@Immutable
data class AppFixedColorScheme(
  val primaryFixed: Color,
  val primaryFixedDim: Color,
  val onPrimaryFixed: Color,
  val onPrimaryFixedVariant: Color,
  val secondaryFixed: Color,
  val secondaryFixedDim: Color,
  val onSecondaryFixed: Color,
  val onSecondaryFixedVariant: Color,
  val tertiaryFixed: Color,
  val tertiaryFixedDim: Color,
  val onTertiaryFixed: Color,
  val onTertiaryFixedVariant: Color,
)

/** Material 3 Fixed color roles for the `Light` and `Dark` standard-contrast modes. */
val AppStandardFixedColors =
  AppFixedColorScheme(
    primaryFixed = Color(0xFFB7F1B9),
    primaryFixedDim = Color(0xFF9CD49F),
    onPrimaryFixed = Color(0xFF002108),
    onPrimaryFixedVariant = Color(0xFF1D5128),
    secondaryFixed = Color(0xFFD4E8D1),
    secondaryFixedDim = Color(0xFFB8CCB5),
    onSecondaryFixed = Color(0xFF0F1F11),
    onSecondaryFixedVariant = Color(0xFF3A4B3A),
    tertiaryFixed = Color(0xFFBDEAF3),
    tertiaryFixedDim = Color(0xFFA1CED7),
    onTertiaryFixed = Color(0xFF001F24),
    onTertiaryFixedVariant = Color(0xFF1F4D54),
  )

/** Material 3 Fixed color roles for the `Light Medium Contrast` mode. */
val AppLightMediumContrastFixedColors =
  AppFixedColorScheme(
    primaryFixed = Color(0xFF45784B),
    primaryFixedDim = Color(0xFF2C5F35),
    onPrimaryFixed = Color(0xFFFFFFFF),
    onPrimaryFixedVariant = Color(0xFFFFFFFF),
    secondaryFixed = Color(0xFF5F725F),
    secondaryFixedDim = Color(0xFF485947),
    onSecondaryFixed = Color(0xFFFFFFFF),
    onSecondaryFixedVariant = Color(0xFFFFFFFF),
    tertiaryFixed = Color(0xFF48747B),
    tertiaryFixedDim = Color(0xFF2F5B63),
    onTertiaryFixed = Color(0xFFFFFFFF),
    onTertiaryFixedVariant = Color(0xFFFFFFFF),
  )

/** Material 3 Fixed color roles for the `Light High Contrast` mode. */
val AppLightHighContrastFixedColors =
  AppFixedColorScheme(
    primaryFixed = Color(0xFF20532A),
    primaryFixedDim = Color(0xFF033C16),
    onPrimaryFixed = Color(0xFFFFFFFF),
    onPrimaryFixedVariant = Color(0xFFFFFFFF),
    secondaryFixed = Color(0xFF3C4D3C),
    secondaryFixedDim = Color(0xFF263727),
    onSecondaryFixed = Color(0xFFFFFFFF),
    onSecondaryFixedVariant = Color(0xFFFFFFFF),
    tertiaryFixed = Color(0xFF224F57),
    tertiaryFixedDim = Color(0xFF03383F),
    onTertiaryFixed = Color(0xFFFFFFFF),
    onTertiaryFixedVariant = Color(0xFFFFFFFF),
  )

/** Material 3 Fixed color roles for the `Dark Medium Contrast` mode. */
val AppDarkMediumContrastFixedColors =
  AppFixedColorScheme(
    primaryFixed = Color(0xFFB7F1B9),
    primaryFixedDim = Color(0xFF9CD49F),
    onPrimaryFixed = Color(0xFF001504),
    onPrimaryFixedVariant = Color(0xFF073F19),
    secondaryFixed = Color(0xFFD4E8D1),
    secondaryFixedDim = Color(0xFFB8CCB5),
    onSecondaryFixed = Color(0xFF051407),
    onSecondaryFixedVariant = Color(0xFF293A2A),
    tertiaryFixed = Color(0xFFBDEAF3),
    tertiaryFixedDim = Color(0xFFA1CED7),
    onTertiaryFixed = Color(0xFF001417),
    onTertiaryFixedVariant = Color(0xFF083C43),
  )

/** Material 3 Fixed color roles for the `Dark High Contrast` mode. */
val AppDarkHighContrastFixedColors =
  AppFixedColorScheme(
    primaryFixed = Color(0xFFB7F1B9),
    primaryFixedDim = Color(0xFF9CD49F),
    onPrimaryFixed = Color(0xFF000000),
    onPrimaryFixedVariant = Color(0xFF001504),
    secondaryFixed = Color(0xFFD4E8D1),
    secondaryFixedDim = Color(0xFFB8CCB5),
    onSecondaryFixed = Color(0xFF000000),
    onSecondaryFixedVariant = Color(0xFF051407),
    tertiaryFixed = Color(0xFFBDEAF3),
    tertiaryFixedDim = Color(0xFFA1CED7),
    onTertiaryFixed = Color(0xFF000000),
    onTertiaryFixedVariant = Color(0xFF001417),
  )

/** Google Earth (Terrarium Design System `v0.3.6`) standard-contrast Fixed color roles. */
val EarthStandardFixedColors =
  AppFixedColorScheme(
    primaryFixed = Color(0xFFD3E3FD),
    primaryFixedDim = Color(0xFFA8C7FA),
    onPrimaryFixed = Color(0xFF041E49),
    onPrimaryFixedVariant = Color(0xFF0842A0),
    secondaryFixed = Color(0xFFC2E7FF),
    secondaryFixedDim = Color(0xFF7FCFFF),
    onSecondaryFixed = Color(0xFF001D35),
    onSecondaryFixedVariant = Color(0xFF004A77),
    tertiaryFixed = Color(0xFFC4EED0),
    tertiaryFixedDim = Color(0xFF6DD58C),
    onTertiaryFixed = Color(0xFF072711),
    onTertiaryFixedVariant = Color(0xFF0F5223),
  )

/** Google Earth (Terrarium Design System `v0.3.6`) high-contrast Fixed color roles. */
val EarthHighContrastFixedColors =
  AppFixedColorScheme(
    primaryFixed = Color(0xFF0842A0),
    primaryFixedDim = Color(0xFF041E49),
    onPrimaryFixed = Color(0xFFFFFFFF),
    onPrimaryFixedVariant = Color(0xFFFFFFFF),
    secondaryFixed = Color(0xFF004A77),
    secondaryFixedDim = Color(0xFF001D35),
    onSecondaryFixed = Color(0xFFFFFFFF),
    onSecondaryFixedVariant = Color(0xFFFFFFFF),
    tertiaryFixed = Color(0xFF0F5223),
    tertiaryFixedDim = Color(0xFF072711),
    onTertiaryFixed = Color(0xFFFFFFFF),
    onTertiaryFixedVariant = Color(0xFFFFFFFF),
  )

/**
 * Material 3 Fixed color roles from the design system specification. Direct properties expose the
 * standard-contrast fixed tokens (identical across `Light` and `Dark`); use [forScheme] or
 * [LocalAppFixedColors] when resolving contrast-adjusted fixed tokens.
 */
object AppFixedColors {
  val primaryFixed = AppStandardFixedColors.primaryFixed
  val primaryFixedDim = AppStandardFixedColors.primaryFixedDim
  val onPrimaryFixed = AppStandardFixedColors.onPrimaryFixed
  val onPrimaryFixedVariant = AppStandardFixedColors.onPrimaryFixedVariant

  val secondaryFixed = AppStandardFixedColors.secondaryFixed
  val secondaryFixedDim = AppStandardFixedColors.secondaryFixedDim
  val onSecondaryFixed = AppStandardFixedColors.onSecondaryFixed
  val onSecondaryFixedVariant = AppStandardFixedColors.onSecondaryFixedVariant

  val tertiaryFixed = AppStandardFixedColors.tertiaryFixed
  val tertiaryFixedDim = AppStandardFixedColors.tertiaryFixedDim
  val onTertiaryFixed = AppStandardFixedColors.onTertiaryFixed
  val onTertiaryFixedVariant = AppStandardFixedColors.onTertiaryFixedVariant

  fun forScheme(
    darkTheme: Boolean = false,
    contrast: AppContrast = AppContrast.STANDARD,
    palette: AppThemePalette = AppThemePalette.COLLECT_ONE,
  ): AppFixedColorScheme =
    when (palette) {
      AppThemePalette.COLLECT_ONE ->
        when (contrast) {
          AppContrast.STANDARD -> AppStandardFixedColors
          AppContrast.MEDIUM ->
            if (darkTheme) AppDarkMediumContrastFixedColors else AppLightMediumContrastFixedColors
          AppContrast.HIGH ->
            if (darkTheme) AppDarkHighContrastFixedColors else AppLightHighContrastFixedColors
        }
      AppThemePalette.GOOGLE_EARTH ->
        when (contrast) {
          AppContrast.STANDARD,
          AppContrast.MEDIUM -> EarthStandardFixedColors
          AppContrast.HIGH -> EarthHighContrastFixedColors
        }
    }
}

/** CompositionLocal providing the active [AppFixedColorScheme] for the current theme/contrast. */
val LocalAppFixedColors = staticCompositionLocalOf { AppStandardFixedColors }

/**
 * Static categorical/semantic accent colors from the design system specification (`Static Colors`),
 * used for map layer pins, choice badges, and status indicators.
 */
object AppStaticColors {
  val Orange = Color(0xFFF37C22)
  val Red = Color(0xFFD13135)
  val Purple = Color(0xFF7A279F)
  val Blue = Color(0xFF2278CF)
  val Green = Color(0xFF3C8D40)
  val Yellow = Color(0xFFF9BF40)

  val palette: List<Pair<String, String>> =
    listOf(
      "Orange" to "#F37C22",
      "Red" to "#D13135",
      "Purple" to "#7A279F",
      "Blue" to "#2278CF",
      "Green" to "#3C8D40",
      "Yellow" to "#F9BF40",
    )

  val hexList: List<String> = palette.map { it.second }
}

/** Standard Material Design 3 5-tier shape scale (`extraSmall` 4dp through `extraLarge` 28dp). */
val AppShapes =
  Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

/**
 * CompositionLocal providing the Google Sans Flex [FontFamily] for Collect One brand and UI text.
 */
val LocalAppBrandFontFamily = compositionLocalOf<FontFamily> { FontFamily.Default }

/**
 * CompositionLocal providing the Google Sans Code [FontFamily] ([appMonospaceFontFamily]). Its
 * default, the platform's monospace font, stands for "not provided".
 */
val LocalAppMonospaceFontFamily = compositionLocalOf<FontFamily> { FontFamily.Monospace }

/**
 * CompositionLocal providing the hover [PointerIcon] applied to enabled interactive controls
 * (buttons, clickable cards and surfaces, chips, toggles, menu items, tabs, sliders, and clickable
 * modifiers) within [AppTheme]. Defaults to [PointerIcon.Hand] so mouse users see a pointing hand
 * across click targets without repeating `.pointerHoverIcon(...)` at every call site.
 */
val LocalInteractivePointerIcon = staticCompositionLocalOf { PointerIcon.Hand }

private const val DEFAULT_RESOURCE_FONT_IDENTITY = "org.jetbrains.compose.resources.defaultFont"

private fun UiFont.isPlaceholderResourceFont(): Boolean =
  toString().contains(DEFAULT_RESOURCE_FONT_IDENTITY)

/**
 * Google Sans Flex font family (`400`..`800`) used across all brand, heading, title, body, and
 * label typography roles in Collect One.
 */
@Composable
fun appFontFamily(): FontFamily {
  val existing = LocalAppBrandFontFamily.current
  if (existing !== FontFamily.Default) return existing
  return FontFamily(
    Font(Res.font.google_sans_flex_regular, FontWeight.Normal),
    Font(Res.font.google_sans_flex_medium, FontWeight.Medium),
    Font(Res.font.google_sans_flex_semibold, FontWeight.SemiBold),
    Font(Res.font.google_sans_flex_bold, FontWeight.Bold),
    Font(Res.font.google_sans_flex_extrabold, FontWeight.ExtraBold),
  )
}

/** The weights monospace text is drawn in; all come from the one variable font file. */
private val MonospaceWeights =
  listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

@Composable
private fun monospaceFonts(): List<UiFont> = MonospaceWeights.map { weight ->
  Font(Res.font.google_sans_code, weight)
}

/**
 * Google Sans Code: the monospace font for text that is a value rather than prose (identifiers,
 * URLs, coordinates, regular expressions, hex colors). Use it instead of `FontFamily.Monospace`,
 * which is a different font on every platform.
 */
@Composable
fun appMonospaceFontFamily(): FontFamily {
  val existing = LocalAppMonospaceFontFamily.current
  if (existing !== FontFamily.Monospace) return existing
  val fonts = monospaceFonts()
  return remember(fonts) { FontFamily(fonts) }
}

/**
 * Returns the Collect One Material Design 3 15-role [Typography] scale unified on **Google Sans
 * Flex** ([appFontFamily]).
 */
@Composable
fun appTypography(fontFamily: FontFamily = appFontFamily()): Typography {
  val baseline = Typography()
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

/** Returns [baseStyle] styled with the Collect One brand [FontFamily] (**Google Sans Flex**). */
@Composable
fun appBrandTextStyle(baseStyle: TextStyle = MaterialTheme.typography.titleLarge): TextStyle =
  baseStyle.copy(fontFamily = LocalAppBrandFontFamily.current)

/** Semantic tonal color roles for M3 pill badges and status indicators. */
enum class BadgeTone {
  PRIMARY,
  SECONDARY,
  TERTIARY,
  WARNING,
  ERROR,
  NEUTRAL,
}

/** Reusable Material 3 tonal pill badge backed by `MaterialTheme.colorScheme` container roles. */
@Composable
fun TonalBadge(
  text: String,
  tone: BadgeTone = BadgeTone.PRIMARY,
  outlined: Boolean = false,
  monospace: Boolean = false,
  icon: ImageVector? = null,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val (containerColor, contentColor, borderColor) =
    when (tone) {
      BadgeTone.PRIMARY ->
        Triple(colors.primaryContainer, colors.onPrimaryContainer, colors.primary)
      BadgeTone.SECONDARY ->
        Triple(colors.secondaryContainer, colors.onSecondaryContainer, colors.secondary)
      BadgeTone.TERTIARY ->
        Triple(colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary)
      BadgeTone.WARNING ->
        Triple(colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary)
      BadgeTone.ERROR -> Triple(colors.errorContainer, colors.onErrorContainer, colors.error)
      BadgeTone.NEUTRAL ->
        Triple(colors.surfaceContainerHigh, colors.onSurfaceVariant, colors.outlineVariant)
    }
  Surface(
    modifier = Modifier.clip(CircleShape).then(modifier),
    shape = CircleShape,
    color = containerColor,
    contentColor = contentColor,
    border = if (outlined) BorderStroke(1.dp, borderColor) else null,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(13.dp),
        )
      }
      Text(
        text = text,
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (monospace) appMonospaceFontFamily() else null,
          ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
      )
    }
  }
}

/**
 * Material 3 Modal Bottom Sheet rendered strictly within its parent container bounds (rather than a
 * root Window Popup), ensuring sheets stay inside `DevicePreviewFrame` when running in the Web UX
 * Workbench.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalBottomSheetOverlay(
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
          .backdropClickable(onClick = onDismissRequest)
    )
    // M3 Bottom Sheet Container
    Surface(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .heightIn(max = 560.dp)
          .clip(BottomSheetDefaults.ExpandedShape)
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

/** The widest an [AlertDialogOverlay] gets unless its caller asks for more. */
val AlertDialogOverlayDefaultMaxWidth = 340.dp

/**
 * Material 3 AlertDialog rendered strictly within its parent container bounds (rather than a root
 * Window Dialog), ensuring dialogs stay centered inside `DevicePreviewFrame`.
 *
 * @param maxWidth the widest the dialog gets; it is narrower in a narrower parent. Material 3
 *   allows up to 560 dp, for dialogs whose text or buttons need the room on wide screens.
 */
@Composable
fun AlertDialogOverlay(
  onDismissRequest: () -> Unit,
  title: @Composable () -> Unit,
  text: @Composable () -> Unit,
  confirmButton: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  icon: (@Composable () -> Unit)? = null,
  dismissButton: (@Composable () -> Unit)? = null,
  maxWidth: Dp = AlertDialogOverlayDefaultMaxWidth,
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
          .backdropClickable(onClick = onDismissRequest)
    )
    // M3 Dialog Container
    Surface(
      modifier =
        Modifier.padding(horizontal = 24.dp)
          .widthIn(min = 280.dp, max = maxWidth)
          .clip(MaterialTheme.shapes.extraLarge)
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
          Box(modifier = Modifier.padding(bottom = 16.dp)) { icon() }
        }
        Box(modifier = Modifier.padding(bottom = 16.dp)) { title() }
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) { text() }
        // Like Material 3's own dialog, the buttons share a row while they fit and stack, still
        // end-aligned, once they don't, so a long label never has to wrap inside its button.
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          itemVerticalAlignment = Alignment.CenterVertically,
        ) {
          if (dismissButton != null) dismissButton()
          confirmButton()
        }
      }
    }
  }
}

/**
 * Shared Collect One Material 3 theme for Mobile and Web surfaces unified on **Google Sans Flex**
 * ([appFontFamily]) across all brand, heading, title, body, and label typography roles.
 */
@Composable
fun AppTheme(
  darkTheme: Boolean = false,
  contrast: AppContrast = AppContrast.STANDARD,
  palette: AppThemePalette = AppThemePalette.COLLECT_ONE,
  interactivePointerIcon: PointerIcon = LocalInteractivePointerIcon.current,
  content: @Composable () -> Unit,
) {
  val existingFontFamily = LocalAppBrandFontFamily.current
  val fontFamily =
    if (existingFontFamily !== FontFamily.Default) {
      existingFontFamily
    } else {
      val regular = Font(Res.font.google_sans_flex_regular, FontWeight.Normal)
      val medium = Font(Res.font.google_sans_flex_medium, FontWeight.Medium)
      val semibold = Font(Res.font.google_sans_flex_semibold, FontWeight.SemiBold)
      val bold = Font(Res.font.google_sans_flex_bold, FontWeight.Bold)
      val extrabold = Font(Res.font.google_sans_flex_extrabold, FontWeight.ExtraBold)

      if (
        regular.isPlaceholderResourceFont() ||
          medium.isPlaceholderResourceFont() ||
          semibold.isPlaceholderResourceFont() ||
          bold.isPlaceholderResourceFont() ||
          extrabold.isPlaceholderResourceFont()
      ) {
        return
      }

      remember(regular, medium, semibold, bold, extrabold) {
        FontFamily(regular, medium, semibold, bold, extrabold)
      }
    }

  val existingMonospaceFontFamily = LocalAppMonospaceFontFamily.current
  val monospaceFontFamily =
    if (existingMonospaceFontFamily !== FontFamily.Monospace) {
      existingMonospaceFontFamily
    } else {
      val fonts = monospaceFonts()
      if (fonts.any { it.isPlaceholderResourceFont() }) return
      remember(fonts) { FontFamily(fonts) }
    }

  val colorScheme =
    when (palette) {
      AppThemePalette.COLLECT_ONE -> appColorScheme(darkTheme = darkTheme, contrast = contrast)
      AppThemePalette.GOOGLE_EARTH -> earthColorScheme(darkTheme = darkTheme, contrast = contrast)
    }
  val fixedColors =
    AppFixedColors.forScheme(darkTheme = darkTheme, contrast = contrast, palette = palette)
  val typography = appTypography(fontFamily)
  CompositionLocalProvider(
    LocalAppBrandFontFamily provides fontFamily,
    LocalAppMonospaceFontFamily provides monospaceFontFamily,
    LocalAppFixedColors provides fixedColors,
    LocalInteractivePointerIcon provides interactivePointerIcon,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      shapes = AppShapes,
      typography = typography,
      content = content,
    )
  }
}

/**
 * Alternate Material 3 theme using Google Earth's Terrarium Design System tokens (`#0B57D0` primary
 * blue, `#FFFFFF` / `#131314` surfaces, `#E1E3E1` / `#444746` variants) for forms embedded inside
 * Google Earth KML balloons.
 */
@Composable
fun GoogleEarthTheme(
  darkTheme: Boolean = false,
  contrast: AppContrast = AppContrast.STANDARD,
  content: @Composable () -> Unit,
) {
  AppTheme(
    darkTheme = darkTheme,
    contrast = contrast,
    palette = AppThemePalette.GOOGLE_EARTH,
    content = content,
  )
}
