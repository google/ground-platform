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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.material3.Typography
import androidx.compose.material3.contentColorFor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AppThemeTest {

  @Test
  fun lightColorScheme_matchesFigmaDesignSpecTokens() {
    val s = AppLightColorScheme
    assertEquals(Color(0xFF36693E), s.primary)
    assertEquals(Color(0xFFFFFFFF), s.onPrimary)
    assertEquals(Color(0xFFB7F1B9), s.primaryContainer)
    assertEquals(Color(0xFF1D5128), s.onPrimaryContainer)
    assertEquals(Color(0xFF9CD49F), s.inversePrimary)
    assertEquals(Color(0xFF516351), s.secondary)
    assertEquals(Color(0xFFFFFFFF), s.onSecondary)
    assertEquals(Color(0xFFD4E8D1), s.secondaryContainer)
    assertEquals(Color(0xFF3A4B3A), s.onSecondaryContainer)
    assertEquals(Color(0xFF39656C), s.tertiary)
    assertEquals(Color(0xFFFFFFFF), s.onTertiary)
    assertEquals(Color(0xFFBDEAF3), s.tertiaryContainer)
    assertEquals(Color(0xFF1F4D54), s.onTertiaryContainer)
    assertEquals(Color(0xFFF7FBF2), s.background)
    assertEquals(Color(0xFF181D18), s.onBackground)
    assertEquals(Color(0xFFF7FBF2), s.surface)
    assertEquals(Color(0xFF181D18), s.onSurface)
    assertEquals(Color(0xFFDDE5D9), s.surfaceVariant)
    assertEquals(Color(0xFF424940), s.onSurfaceVariant)
    assertEquals(Color(0xFF36693E), s.surfaceTint)
    assertEquals(Color(0xFF2D322C), s.inverseSurface)
    assertEquals(Color(0xFFEEF2E9), s.inverseOnSurface)
    assertEquals(Color(0xFFBA1A1A), s.error)
    assertEquals(Color(0xFFFFFFFF), s.onError)
    assertEquals(Color(0xFFFFDAD6), s.errorContainer)
    assertEquals(Color(0xFF93000A), s.onErrorContainer)
    assertEquals(Color(0xFF727970), s.outline)
    assertEquals(Color(0xFFC1C9BE), s.outlineVariant)
    assertEquals(Color(0xFF000000), s.scrim)
    assertEquals(Color(0xFFF7FBF2), s.surfaceBright)
    assertEquals(Color(0xFFD7DBD3), s.surfaceDim)
    assertEquals(Color(0xFFFFFFFF), s.surfaceContainerLowest)
    assertEquals(Color(0xFFF1F5EC), s.surfaceContainerLow)
    assertEquals(Color(0xFFEBEFE7), s.surfaceContainer)
    assertEquals(Color(0xFFE5E9E1), s.surfaceContainerHigh)
    assertEquals(Color(0xFFE0E4DB), s.surfaceContainerHighest)
    assertEquals(Color(0xFFB7F1B9), s.primaryFixed)
    assertEquals(Color(0xFF9CD49F), s.primaryFixedDim)
    assertEquals(Color(0xFF002108), s.onPrimaryFixed)
    assertEquals(Color(0xFF1D5128), s.onPrimaryFixedVariant)
    assertEquals(Color(0xFFD4E8D1), s.secondaryFixed)
    assertEquals(Color(0xFFB8CCB5), s.secondaryFixedDim)
    assertEquals(Color(0xFF0F1F11), s.onSecondaryFixed)
    assertEquals(Color(0xFF3A4B3A), s.onSecondaryFixedVariant)
    assertEquals(Color(0xFFBDEAF3), s.tertiaryFixed)
    assertEquals(Color(0xFFA1CED7), s.tertiaryFixedDim)
    assertEquals(Color(0xFF001F24), s.onTertiaryFixed)
    assertEquals(Color(0xFF1F4D54), s.onTertiaryFixedVariant)

    val f = AppStandardFixedColors
    assertEquals(Color(0xFFB7F1B9), f.primaryFixed)
    assertEquals(Color(0xFF9CD49F), f.primaryFixedDim)
    assertEquals(Color(0xFF002108), f.onPrimaryFixed)
    assertEquals(Color(0xFF1D5128), f.onPrimaryFixedVariant)
    assertEquals(Color(0xFFD4E8D1), f.secondaryFixed)
    assertEquals(Color(0xFFB8CCB5), f.secondaryFixedDim)
    assertEquals(Color(0xFF0F1F11), f.onSecondaryFixed)
    assertEquals(Color(0xFF3A4B3A), f.onSecondaryFixedVariant)
    assertEquals(Color(0xFFBDEAF3), f.tertiaryFixed)
    assertEquals(Color(0xFFA1CED7), f.tertiaryFixedDim)
    assertEquals(Color(0xFF001F24), f.onTertiaryFixed)
    assertEquals(Color(0xFF1F4D54), f.onTertiaryFixedVariant)
    assertEquals(Color(0xFF000000), AppShadowColor)
  }

  @Test
  fun lightMediumContrastColorScheme_matchesFigmaDesignSpecTokens() {
    val s = AppLightMediumContrastColorScheme
    assertEquals(Color(0xFF073F19), s.primary)
    assertEquals(Color(0xFFFFFFFF), s.onPrimary)
    assertEquals(Color(0xFF45784B), s.primaryContainer)
    assertEquals(Color(0xFFFFFFFF), s.onPrimaryContainer)
    assertEquals(Color(0xFF9CD49F), s.inversePrimary)
    assertEquals(Color(0xFF293A2A), s.secondary)
    assertEquals(Color(0xFFFFFFFF), s.onSecondary)
    assertEquals(Color(0xFF5F725F), s.secondaryContainer)
    assertEquals(Color(0xFFFFFFFF), s.onSecondaryContainer)
    assertEquals(Color(0xFF083C43), s.tertiary)
    assertEquals(Color(0xFFFFFFFF), s.onTertiary)
    assertEquals(Color(0xFF48747B), s.tertiaryContainer)
    assertEquals(Color(0xFFFFFFFF), s.onTertiaryContainer)
    assertEquals(Color(0xFFF7FBF2), s.background)
    assertEquals(Color(0xFF181D18), s.onBackground)
    assertEquals(Color(0xFFF7FBF2), s.surface)
    assertEquals(Color(0xFF0E120E), s.onSurface)
    assertEquals(Color(0xFFDDE5D9), s.surfaceVariant)
    assertEquals(Color(0xFF313830), s.onSurfaceVariant)
    assertEquals(Color(0xFF36693E), s.surfaceTint)
    assertEquals(Color(0xFF2D322C), s.inverseSurface)
    assertEquals(Color(0xFFEEF2E9), s.inverseOnSurface)
    assertEquals(Color(0xFF740006), s.error)
    assertEquals(Color(0xFFFFFFFF), s.onError)
    assertEquals(Color(0xFFCF2C27), s.errorContainer)
    assertEquals(Color(0xFFFFFFFF), s.onErrorContainer)
    assertEquals(Color(0xFF4D544C), s.outline)
    assertEquals(Color(0xFF686F66), s.outlineVariant)
    assertEquals(Color(0xFF000000), s.scrim)
    assertEquals(Color(0xFFF7FBF2), s.surfaceBright)
    assertEquals(Color(0xFFC3C8C0), s.surfaceDim)
    assertEquals(Color(0xFFFFFFFF), s.surfaceContainerLowest)
    assertEquals(Color(0xFFF1F5EC), s.surfaceContainerLow)
    assertEquals(Color(0xFFE5E9E1), s.surfaceContainer)
    assertEquals(Color(0xFFDADED6), s.surfaceContainerHigh)
    assertEquals(Color(0xFFCFD3CB), s.surfaceContainerHighest)
    assertEquals(Color(0xFF45784B), s.primaryFixed)
    assertEquals(Color(0xFF2C5F35), s.primaryFixedDim)
    assertEquals(Color(0xFFFFFFFF), s.onPrimaryFixed)
    assertEquals(Color(0xFFFFFFFF), s.onPrimaryFixedVariant)
    assertEquals(Color(0xFF5F725F), s.secondaryFixed)
    assertEquals(Color(0xFF485947), s.secondaryFixedDim)
    assertEquals(Color(0xFFFFFFFF), s.onSecondaryFixed)
    assertEquals(Color(0xFFFFFFFF), s.onSecondaryFixedVariant)
    assertEquals(Color(0xFF48747B), s.tertiaryFixed)
    assertEquals(Color(0xFF2F5B63), s.tertiaryFixedDim)
    assertEquals(Color(0xFFFFFFFF), s.onTertiaryFixed)
    assertEquals(Color(0xFFFFFFFF), s.onTertiaryFixedVariant)

    val f = AppLightMediumContrastFixedColors
    assertEquals(Color(0xFF45784B), f.primaryFixed)
    assertEquals(Color(0xFF2C5F35), f.primaryFixedDim)
    assertEquals(Color(0xFFFFFFFF), f.onPrimaryFixed)
    assertEquals(Color(0xFFFFFFFF), f.onPrimaryFixedVariant)
    assertEquals(Color(0xFF5F725F), f.secondaryFixed)
    assertEquals(Color(0xFF485947), f.secondaryFixedDim)
    assertEquals(Color(0xFFFFFFFF), f.onSecondaryFixed)
    assertEquals(Color(0xFFFFFFFF), f.onSecondaryFixedVariant)
    assertEquals(Color(0xFF48747B), f.tertiaryFixed)
    assertEquals(Color(0xFF2F5B63), f.tertiaryFixedDim)
    assertEquals(Color(0xFFFFFFFF), f.onTertiaryFixed)
    assertEquals(Color(0xFFFFFFFF), f.onTertiaryFixedVariant)
    assertEquals(Color(0xFF000000), AppShadowColor)
  }

  @Test
  fun lightHighContrastColorScheme_matchesFigmaDesignSpecTokens() {
    val s = AppLightHighContrastColorScheme
    assertEquals(Color(0xFF003411), s.primary)
    assertEquals(Color(0xFFFFFFFF), s.onPrimary)
    assertEquals(Color(0xFF20532A), s.primaryContainer)
    assertEquals(Color(0xFFFFFFFF), s.onPrimaryContainer)
    assertEquals(Color(0xFF9CD49F), s.inversePrimary)
    assertEquals(Color(0xFF203021), s.secondary)
    assertEquals(Color(0xFFFFFFFF), s.onSecondary)
    assertEquals(Color(0xFF3C4D3C), s.secondaryContainer)
    assertEquals(Color(0xFFFFFFFF), s.onSecondaryContainer)
    assertEquals(Color(0xFF003138), s.tertiary)
    assertEquals(Color(0xFFFFFFFF), s.onTertiary)
    assertEquals(Color(0xFF224F57), s.tertiaryContainer)
    assertEquals(Color(0xFFFFFFFF), s.onTertiaryContainer)
    assertEquals(Color(0xFFF7FBF2), s.background)
    assertEquals(Color(0xFF181D18), s.onBackground)
    assertEquals(Color(0xFFF7FBF2), s.surface)
    assertEquals(Color(0xFF000000), s.onSurface)
    assertEquals(Color(0xFFDDE5D9), s.surfaceVariant)
    assertEquals(Color(0xFF000000), s.onSurfaceVariant)
    assertEquals(Color(0xFF36693E), s.surfaceTint)
    assertEquals(Color(0xFF2D322C), s.inverseSurface)
    assertEquals(Color(0xFFFFFFFF), s.inverseOnSurface)
    assertEquals(Color(0xFF600004), s.error)
    assertEquals(Color(0xFFFFFFFF), s.onError)
    assertEquals(Color(0xFF98000A), s.errorContainer)
    assertEquals(Color(0xFFFFFFFF), s.onErrorContainer)
    assertEquals(Color(0xFF272E27), s.outline)
    assertEquals(Color(0xFF444B43), s.outlineVariant)
    assertEquals(Color(0xFF000000), s.scrim)
    assertEquals(Color(0xFFF7FBF2), s.surfaceBright)
    assertEquals(Color(0xFFB6BAB2), s.surfaceDim)
    assertEquals(Color(0xFFFFFFFF), s.surfaceContainerLowest)
    assertEquals(Color(0xFFEEF2E9), s.surfaceContainerLow)
    assertEquals(Color(0xFFE0E4DB), s.surfaceContainer)
    assertEquals(Color(0xFFD1D6CD), s.surfaceContainerHigh)
    assertEquals(Color(0xFFC3C8C0), s.surfaceContainerHighest)
    assertEquals(Color(0xFF20532A), s.primaryFixed)
    assertEquals(Color(0xFF033C16), s.primaryFixedDim)
    assertEquals(Color(0xFFFFFFFF), s.onPrimaryFixed)
    assertEquals(Color(0xFFFFFFFF), s.onPrimaryFixedVariant)
    assertEquals(Color(0xFF3C4D3C), s.secondaryFixed)
    assertEquals(Color(0xFF263727), s.secondaryFixedDim)
    assertEquals(Color(0xFFFFFFFF), s.onSecondaryFixed)
    assertEquals(Color(0xFFFFFFFF), s.onSecondaryFixedVariant)
    assertEquals(Color(0xFF224F57), s.tertiaryFixed)
    assertEquals(Color(0xFF03383F), s.tertiaryFixedDim)
    assertEquals(Color(0xFFFFFFFF), s.onTertiaryFixed)
    assertEquals(Color(0xFFFFFFFF), s.onTertiaryFixedVariant)

    val f = AppLightHighContrastFixedColors
    assertEquals(Color(0xFF20532A), f.primaryFixed)
    assertEquals(Color(0xFF033C16), f.primaryFixedDim)
    assertEquals(Color(0xFFFFFFFF), f.onPrimaryFixed)
    assertEquals(Color(0xFFFFFFFF), f.onPrimaryFixedVariant)
    assertEquals(Color(0xFF3C4D3C), f.secondaryFixed)
    assertEquals(Color(0xFF263727), f.secondaryFixedDim)
    assertEquals(Color(0xFFFFFFFF), f.onSecondaryFixed)
    assertEquals(Color(0xFFFFFFFF), f.onSecondaryFixedVariant)
    assertEquals(Color(0xFF224F57), f.tertiaryFixed)
    assertEquals(Color(0xFF03383F), f.tertiaryFixedDim)
    assertEquals(Color(0xFFFFFFFF), f.onTertiaryFixed)
    assertEquals(Color(0xFFFFFFFF), f.onTertiaryFixedVariant)
    assertEquals(Color(0xFF000000), AppShadowColor)
  }

  @Test
  fun darkColorScheme_matchesFigmaDesignSpecTokens() {
    val s = AppDarkColorScheme
    assertEquals(Color(0xFF9CD49F), s.primary)
    assertEquals(Color(0xFF003914), s.onPrimary)
    assertEquals(Color(0xFF1D5128), s.primaryContainer)
    assertEquals(Color(0xFFB7F1B9), s.onPrimaryContainer)
    assertEquals(Color(0xFF36693E), s.inversePrimary)
    assertEquals(Color(0xFFB8CCB5), s.secondary)
    assertEquals(Color(0xFF243425), s.onSecondary)
    assertEquals(Color(0xFF3A4B3A), s.secondaryContainer)
    assertEquals(Color(0xFFD4E8D1), s.onSecondaryContainer)
    assertEquals(Color(0xFFA1CED7), s.tertiary)
    assertEquals(Color(0xFF00363D), s.onTertiary)
    assertEquals(Color(0xFF1F4D54), s.tertiaryContainer)
    assertEquals(Color(0xFFBDEAF3), s.onTertiaryContainer)
    assertEquals(Color(0xFF101510), s.background)
    assertEquals(Color(0xFFE0E4DB), s.onBackground)
    assertEquals(Color(0xFF101510), s.surface)
    assertEquals(Color(0xFFE0E4DB), s.onSurface)
    assertEquals(Color(0xFF424940), s.surfaceVariant)
    assertEquals(Color(0xFFC1C9BE), s.onSurfaceVariant)
    assertEquals(Color(0xFF9CD49F), s.surfaceTint)
    assertEquals(Color(0xFFE0E4DB), s.inverseSurface)
    assertEquals(Color(0xFF2D322C), s.inverseOnSurface)
    assertEquals(Color(0xFFFFB4AB), s.error)
    assertEquals(Color(0xFF690005), s.onError)
    assertEquals(Color(0xFF93000A), s.errorContainer)
    assertEquals(Color(0xFFFFDAD6), s.onErrorContainer)
    assertEquals(Color(0xFF8B9389), s.outline)
    assertEquals(Color(0xFF424940), s.outlineVariant)
    assertEquals(Color(0xFF000000), s.scrim)
    assertEquals(Color(0xFF363A35), s.surfaceBright)
    assertEquals(Color(0xFF101510), s.surfaceDim)
    assertEquals(Color(0xFF0B0F0B), s.surfaceContainerLowest)
    assertEquals(Color(0xFF181D18), s.surfaceContainerLow)
    assertEquals(Color(0xFF1C211C), s.surfaceContainer)
    assertEquals(Color(0xFF272B26), s.surfaceContainerHigh)
    assertEquals(Color(0xFF313630), s.surfaceContainerHighest)
    assertEquals(Color(0xFFB7F1B9), s.primaryFixed)
    assertEquals(Color(0xFF9CD49F), s.primaryFixedDim)
    assertEquals(Color(0xFF002108), s.onPrimaryFixed)
    assertEquals(Color(0xFF1D5128), s.onPrimaryFixedVariant)
    assertEquals(Color(0xFFD4E8D1), s.secondaryFixed)
    assertEquals(Color(0xFFB8CCB5), s.secondaryFixedDim)
    assertEquals(Color(0xFF0F1F11), s.onSecondaryFixed)
    assertEquals(Color(0xFF3A4B3A), s.onSecondaryFixedVariant)
    assertEquals(Color(0xFFBDEAF3), s.tertiaryFixed)
    assertEquals(Color(0xFFA1CED7), s.tertiaryFixedDim)
    assertEquals(Color(0xFF001F24), s.onTertiaryFixed)
    assertEquals(Color(0xFF1F4D54), s.onTertiaryFixedVariant)

    val f = AppStandardFixedColors
    assertEquals(Color(0xFFB7F1B9), f.primaryFixed)
    assertEquals(Color(0xFF9CD49F), f.primaryFixedDim)
    assertEquals(Color(0xFF002108), f.onPrimaryFixed)
    assertEquals(Color(0xFF1D5128), f.onPrimaryFixedVariant)
    assertEquals(Color(0xFFD4E8D1), f.secondaryFixed)
    assertEquals(Color(0xFFB8CCB5), f.secondaryFixedDim)
    assertEquals(Color(0xFF0F1F11), f.onSecondaryFixed)
    assertEquals(Color(0xFF3A4B3A), f.onSecondaryFixedVariant)
    assertEquals(Color(0xFFBDEAF3), f.tertiaryFixed)
    assertEquals(Color(0xFFA1CED7), f.tertiaryFixedDim)
    assertEquals(Color(0xFF001F24), f.onTertiaryFixed)
    assertEquals(Color(0xFF1F4D54), f.onTertiaryFixedVariant)
    assertEquals(Color(0xFF000000), AppShadowColor)
  }

  @Test
  fun darkMediumContrastColorScheme_matchesFigmaDesignSpecTokens() {
    val s = AppDarkMediumContrastColorScheme
    assertEquals(Color(0xFFB1EAB4), s.primary)
    assertEquals(Color(0xFF002D0E), s.onPrimary)
    assertEquals(Color(0xFF689D6D), s.primaryContainer)
    assertEquals(Color(0xFF000000), s.onPrimaryContainer)
    assertEquals(Color(0xFF1E5229), s.inversePrimary)
    assertEquals(Color(0xFFCEE2CB), s.secondary)
    assertEquals(Color(0xFF19291A), s.onSecondary)
    assertEquals(Color(0xFF839681), s.secondaryContainer)
    assertEquals(Color(0xFF000000), s.onSecondaryContainer)
    assertEquals(Color(0xFFB7E4ED), s.tertiary)
    assertEquals(Color(0xFF002A30), s.onTertiary)
    assertEquals(Color(0xFF6C98A0), s.tertiaryContainer)
    assertEquals(Color(0xFF000000), s.onTertiaryContainer)
    assertEquals(Color(0xFF101510), s.background)
    assertEquals(Color(0xFFE0E4DB), s.onBackground)
    assertEquals(Color(0xFF101510), s.surface)
    assertEquals(Color(0xFFFFFFFF), s.onSurface)
    assertEquals(Color(0xFF424940), s.surfaceVariant)
    assertEquals(Color(0xFFD7DED3), s.onSurfaceVariant)
    assertEquals(Color(0xFF9CD49F), s.surfaceTint)
    assertEquals(Color(0xFFE0E4DB), s.inverseSurface)
    assertEquals(Color(0xFF272B26), s.inverseOnSurface)
    assertEquals(Color(0xFFFFD2CC), s.error)
    assertEquals(Color(0xFF540003), s.onError)
    assertEquals(Color(0xFFFF5449), s.errorContainer)
    assertEquals(Color(0xFF000000), s.onErrorContainer)
    assertEquals(Color(0xFFADB4AA), s.outline)
    assertEquals(Color(0xFF8B9289), s.outlineVariant)
    assertEquals(Color(0xFF000000), s.scrim)
    assertEquals(Color(0xFF414640), s.surfaceBright)
    assertEquals(Color(0xFF101510), s.surfaceDim)
    assertEquals(Color(0xFF050805), s.surfaceContainerLowest)
    assertEquals(Color(0xFF1A1F1A), s.surfaceContainerLow)
    assertEquals(Color(0xFF242924), s.surfaceContainer)
    assertEquals(Color(0xFF2F342E), s.surfaceContainerHigh)
    assertEquals(Color(0xFF3A3F39), s.surfaceContainerHighest)
    assertEquals(Color(0xFFB7F1B9), s.primaryFixed)
    assertEquals(Color(0xFF9CD49F), s.primaryFixedDim)
    assertEquals(Color(0xFF001504), s.onPrimaryFixed)
    assertEquals(Color(0xFF073F19), s.onPrimaryFixedVariant)
    assertEquals(Color(0xFFD4E8D1), s.secondaryFixed)
    assertEquals(Color(0xFFB8CCB5), s.secondaryFixedDim)
    assertEquals(Color(0xFF051407), s.onSecondaryFixed)
    assertEquals(Color(0xFF293A2A), s.onSecondaryFixedVariant)
    assertEquals(Color(0xFFBDEAF3), s.tertiaryFixed)
    assertEquals(Color(0xFFA1CED7), s.tertiaryFixedDim)
    assertEquals(Color(0xFF001417), s.onTertiaryFixed)
    assertEquals(Color(0xFF083C43), s.onTertiaryFixedVariant)

    val f = AppDarkMediumContrastFixedColors
    assertEquals(Color(0xFFB7F1B9), f.primaryFixed)
    assertEquals(Color(0xFF9CD49F), f.primaryFixedDim)
    assertEquals(Color(0xFF001504), f.onPrimaryFixed)
    assertEquals(Color(0xFF073F19), f.onPrimaryFixedVariant)
    assertEquals(Color(0xFFD4E8D1), f.secondaryFixed)
    assertEquals(Color(0xFFB8CCB5), f.secondaryFixedDim)
    assertEquals(Color(0xFF051407), f.onSecondaryFixed)
    assertEquals(Color(0xFF293A2A), f.onSecondaryFixedVariant)
    assertEquals(Color(0xFFBDEAF3), f.tertiaryFixed)
    assertEquals(Color(0xFFA1CED7), f.tertiaryFixedDim)
    assertEquals(Color(0xFF001417), f.onTertiaryFixed)
    assertEquals(Color(0xFF083C43), f.onTertiaryFixedVariant)
    assertEquals(Color(0xFF000000), AppShadowColor)
  }

  @Test
  fun darkHighContrastColorScheme_matchesFigmaDesignSpecTokens() {
    val s = AppDarkHighContrastColorScheme
    assertEquals(Color(0xFFC4FEC6), s.primary)
    assertEquals(Color(0xFF000000), s.onPrimary)
    assertEquals(Color(0xFF98D09B), s.primaryContainer)
    assertEquals(Color(0xFF000F03), s.onPrimaryContainer)
    assertEquals(Color(0xFF1E5229), s.inversePrimary)
    assertEquals(Color(0xFFE1F6DE), s.secondary)
    assertEquals(Color(0xFF000000), s.onSecondary)
    assertEquals(Color(0xFFB4C8B2), s.secondaryContainer)
    assertEquals(Color(0xFF020E03), s.onSecondaryContainer)
    assertEquals(Color(0xFFCEF7FF), s.tertiary)
    assertEquals(Color(0xFF000000), s.onTertiary)
    assertEquals(Color(0xFF9DCAD3), s.tertiaryContainer)
    assertEquals(Color(0xFF000E10), s.onTertiaryContainer)
    assertEquals(Color(0xFF101510), s.background)
    assertEquals(Color(0xFFE0E4DB), s.onBackground)
    assertEquals(Color(0xFF101510), s.surface)
    assertEquals(Color(0xFFFFFFFF), s.onSurface)
    assertEquals(Color(0xFF424940), s.surfaceVariant)
    assertEquals(Color(0xFFFFFFFF), s.onSurfaceVariant)
    assertEquals(Color(0xFF9CD49F), s.surfaceTint)
    assertEquals(Color(0xFFE0E4DB), s.inverseSurface)
    assertEquals(Color(0xFF000000), s.inverseOnSurface)
    assertEquals(Color(0xFFFFECE9), s.error)
    assertEquals(Color(0xFF000000), s.onError)
    assertEquals(Color(0xFFFFAEA4), s.errorContainer)
    assertEquals(Color(0xFF220001), s.onErrorContainer)
    assertEquals(Color(0xFFEBF2E7), s.outline)
    assertEquals(Color(0xFFBDC5BA), s.outlineVariant)
    assertEquals(Color(0xFF000000), s.scrim)
    assertEquals(Color(0xFF4D514B), s.surfaceBright)
    assertEquals(Color(0xFF101510), s.surfaceDim)
    assertEquals(Color(0xFF000000), s.surfaceContainerLowest)
    assertEquals(Color(0xFF1C211C), s.surfaceContainerLow)
    assertEquals(Color(0xFF2D322C), s.surfaceContainer)
    assertEquals(Color(0xFF383D37), s.surfaceContainerHigh)
    assertEquals(Color(0xFF434842), s.surfaceContainerHighest)
    assertEquals(Color(0xFFB7F1B9), s.primaryFixed)
    assertEquals(Color(0xFF9CD49F), s.primaryFixedDim)
    assertEquals(Color(0xFF000000), s.onPrimaryFixed)
    assertEquals(Color(0xFF001504), s.onPrimaryFixedVariant)
    assertEquals(Color(0xFFD4E8D1), s.secondaryFixed)
    assertEquals(Color(0xFFB8CCB5), s.secondaryFixedDim)
    assertEquals(Color(0xFF000000), s.onSecondaryFixed)
    assertEquals(Color(0xFF051407), s.onSecondaryFixedVariant)
    assertEquals(Color(0xFFBDEAF3), s.tertiaryFixed)
    assertEquals(Color(0xFFA1CED7), s.tertiaryFixedDim)
    assertEquals(Color(0xFF000000), s.onTertiaryFixed)
    assertEquals(Color(0xFF001417), s.onTertiaryFixedVariant)

    val f = AppDarkHighContrastFixedColors
    assertEquals(Color(0xFFB7F1B9), f.primaryFixed)
    assertEquals(Color(0xFF9CD49F), f.primaryFixedDim)
    assertEquals(Color(0xFF000000), f.onPrimaryFixed)
    assertEquals(Color(0xFF001504), f.onPrimaryFixedVariant)
    assertEquals(Color(0xFFD4E8D1), f.secondaryFixed)
    assertEquals(Color(0xFFB8CCB5), f.secondaryFixedDim)
    assertEquals(Color(0xFF000000), f.onSecondaryFixed)
    assertEquals(Color(0xFF051407), f.onSecondaryFixedVariant)
    assertEquals(Color(0xFFBDEAF3), f.tertiaryFixed)
    assertEquals(Color(0xFFA1CED7), f.tertiaryFixedDim)
    assertEquals(Color(0xFF000000), f.onTertiaryFixed)
    assertEquals(Color(0xFF001417), f.onTertiaryFixedVariant)
    assertEquals(Color(0xFF000000), AppShadowColor)
  }

  @Test
  fun fixedAndStaticColors_matchFigmaDesignSpecTokens() {
    assertEquals(AppStandardFixedColors.primaryFixed, AppFixedColors.primaryFixed)
    assertEquals(AppStandardFixedColors.primaryFixedDim, AppFixedColors.primaryFixedDim)
    assertEquals(AppStandardFixedColors.onPrimaryFixed, AppFixedColors.onPrimaryFixed)
    assertEquals(
      AppStandardFixedColors.onPrimaryFixedVariant,
      AppFixedColors.onPrimaryFixedVariant,
    )

    assertEquals(AppStandardFixedColors.secondaryFixed, AppFixedColors.secondaryFixed)
    assertEquals(AppStandardFixedColors.secondaryFixedDim, AppFixedColors.secondaryFixedDim)
    assertEquals(AppStandardFixedColors.onSecondaryFixed, AppFixedColors.onSecondaryFixed)
    assertEquals(
      AppStandardFixedColors.onSecondaryFixedVariant,
      AppFixedColors.onSecondaryFixedVariant,
    )

    assertEquals(AppStandardFixedColors.tertiaryFixed, AppFixedColors.tertiaryFixed)
    assertEquals(AppStandardFixedColors.tertiaryFixedDim, AppFixedColors.tertiaryFixedDim)
    assertEquals(AppStandardFixedColors.onTertiaryFixed, AppFixedColors.onTertiaryFixed)
    assertEquals(
      AppStandardFixedColors.onTertiaryFixedVariant,
      AppFixedColors.onTertiaryFixedVariant,
    )

    assertEquals(
      listOf("#F37C22", "#D13135", "#7A279F", "#2278CF", "#3C8D40", "#F9BF40"),
      AppStaticColors.hexList,
    )
  }

  @Test
  fun schemeSelectors_returnMatchingColorAndFixedSchemesForEveryMode() {
    assertSame(AppLightColorScheme, appColorScheme(false, AppContrast.STANDARD))
    assertSame(
      AppLightMediumContrastColorScheme,
      appColorScheme(false, AppContrast.MEDIUM),
    )
    assertSame(AppLightHighContrastColorScheme, appColorScheme(false, AppContrast.HIGH))
    assertSame(AppDarkColorScheme, appColorScheme(true, AppContrast.STANDARD))
    assertSame(AppDarkMediumContrastColorScheme, appColorScheme(true, AppContrast.MEDIUM))
    assertSame(AppDarkHighContrastColorScheme, appColorScheme(true, AppContrast.HIGH))

    assertSame(
      AppStandardFixedColors,
      AppFixedColors.forScheme(false, AppContrast.STANDARD),
    )
    assertSame(
      AppLightMediumContrastFixedColors,
      AppFixedColors.forScheme(false, AppContrast.MEDIUM),
    )
    assertSame(
      AppLightHighContrastFixedColors,
      AppFixedColors.forScheme(false, AppContrast.HIGH),
    )
    assertSame(
      AppStandardFixedColors,
      AppFixedColors.forScheme(true, AppContrast.STANDARD),
    )
    assertSame(
      AppDarkMediumContrastFixedColors,
      AppFixedColors.forScheme(true, AppContrast.MEDIUM),
    )
    assertSame(
      AppDarkHighContrastFixedColors,
      AppFixedColors.forScheme(true, AppContrast.HIGH),
    )
  }

  @Test
  fun contentColorFor_resolvesExpectedOnColorsForAllSurfaceRoles() {
    for (scheme in listOf(AppLightColorScheme, AppDarkColorScheme)) {
      assertEquals(scheme.onPrimary, scheme.contentColorFor(scheme.primary))
      assertEquals(scheme.onPrimaryContainer, scheme.contentColorFor(scheme.primaryContainer))
      assertEquals(scheme.onSecondary, scheme.contentColorFor(scheme.secondary))
      assertEquals(scheme.onSecondaryContainer, scheme.contentColorFor(scheme.secondaryContainer))
      assertEquals(scheme.onTertiary, scheme.contentColorFor(scheme.tertiary))
      assertEquals(scheme.onTertiaryContainer, scheme.contentColorFor(scheme.tertiaryContainer))
      assertEquals(scheme.onError, scheme.contentColorFor(scheme.error))
      assertEquals(scheme.onErrorContainer, scheme.contentColorFor(scheme.errorContainer))
      assertEquals(scheme.onBackground, scheme.contentColorFor(scheme.background))
      assertEquals(scheme.onSurface, scheme.contentColorFor(scheme.surface))
      assertEquals(scheme.onSurfaceVariant, scheme.contentColorFor(scheme.surfaceVariant))
      assertEquals(scheme.inverseOnSurface, scheme.contentColorFor(scheme.inverseSurface))
      assertEquals(scheme.onSurface, scheme.contentColorFor(scheme.surfaceDim))
      assertEquals(scheme.onSurface, scheme.contentColorFor(scheme.surfaceBright))
      assertEquals(scheme.onSurface, scheme.contentColorFor(scheme.surfaceContainerLowest))
      assertEquals(scheme.onSurface, scheme.contentColorFor(scheme.surfaceContainerLow))
      assertEquals(scheme.onSurface, scheme.contentColorFor(scheme.surfaceContainer))
      assertEquals(scheme.onSurface, scheme.contentColorFor(scheme.surfaceContainerHigh))
      assertEquals(scheme.onSurface, scheme.contentColorFor(scheme.surfaceContainerHighest))
    }
    assertEquals(
      AppLightColorScheme.onPrimaryFixed,
      AppLightColorScheme.contentColorFor(AppLightColorScheme.primaryFixedDim),
    )
    assertEquals(
      AppLightColorScheme.onSecondaryFixed,
      AppLightColorScheme.contentColorFor(AppLightColorScheme.secondaryFixedDim),
    )
    assertEquals(
      AppLightColorScheme.onTertiaryFixed,
      AppLightColorScheme.contentColorFor(AppLightColorScheme.tertiaryFixedDim),
    )
    assertEquals(
      AppDarkColorScheme.onPrimaryFixed,
      AppDarkColorScheme.contentColorFor(AppDarkColorScheme.primaryFixed),
    )
    assertEquals(
      AppDarkColorScheme.onSecondaryFixed,
      AppDarkColorScheme.contentColorFor(AppDarkColorScheme.secondaryFixed),
    )
    assertEquals(
      AppDarkColorScheme.onTertiaryFixed,
      AppDarkColorScheme.contentColorFor(AppDarkColorScheme.tertiaryFixed),
    )
  }

  @Test
  fun labelSmall_isMediumWeightAt11Sp() {
    // appTypography() only swaps the font family of the Material 3 baseline roles, so restating
    // these values in a copy() yields the role itself.
    val labelSmall = Typography().labelSmall
    assertEquals(labelSmall, labelSmall.copy(fontWeight = FontWeight.Medium))
    assertEquals(labelSmall, labelSmall.copy(fontSize = 11.sp))
  }
}
