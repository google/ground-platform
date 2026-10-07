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
package org.groundplatform.v2.core.forms.ui

import androidx.compose.material3.contentColorFor
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class GroundThemeTest {

  @Test
  fun lightColorScheme_matchesFigmaDesignSpecTokens() {
    val s = GroundLightColorScheme
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

    assertEquals(Color(0xFFBA1A1A), s.error)
    assertEquals(Color(0xFFFFFFFF), s.onError)
    assertEquals(Color(0xFFFFDAD6), s.errorContainer)
    assertEquals(Color(0xFF93000A), s.onErrorContainer)

    assertEquals(Color(0xFFF7FBF2), s.background)
    assertEquals(Color(0xFF181D18), s.onBackground)
    assertEquals(Color(0xFFF7FBF2), s.surface)
    assertEquals(Color(0xFF181D18), s.onSurface)
    assertEquals(Color(0xFFDDE5D9), s.surfaceVariant)
    assertEquals(Color(0xFF424940), s.onSurfaceVariant)
    assertEquals(Color(0xFF2D322C), s.inverseSurface)
    assertEquals(Color(0xFFEEF2E9), s.inverseOnSurface)
    assertEquals(Color(0xFF727970), s.outline)
    assertEquals(Color(0xFFC1C9BE), s.outlineVariant)
    assertEquals(Color(0xFF000000), s.scrim)

    assertEquals(Color(0xFFD7DBD3), s.surfaceDim)
    assertEquals(Color(0xFFF7FBF2), s.surfaceBright)
    assertEquals(Color(0xFFFFFFFF), s.surfaceContainerLowest)
    assertEquals(Color(0xFFF1F5EC), s.surfaceContainerLow)
    assertEquals(Color(0xFFEBEFE7), s.surfaceContainer)
    assertEquals(Color(0xFFE5E9E1), s.surfaceContainerHigh)
    assertEquals(Color(0xFFE0E4DB), s.surfaceContainerHighest)
  }

  @Test
  fun darkColorScheme_matchesFigmaDesignSpecTokens() {
    val s = GroundDarkColorScheme
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

    assertEquals(Color(0xFFFFB4AB), s.error)
    assertEquals(Color(0xFF690005), s.onError)
    assertEquals(Color(0xFF93000A), s.errorContainer)
    assertEquals(Color(0xFFFFDAD6), s.onErrorContainer)

    assertEquals(Color(0xFF101510), s.background)
    assertEquals(Color(0xFFE0E4DB), s.onBackground)
    assertEquals(Color(0xFF101510), s.surface)
    assertEquals(Color(0xFFE0E4DB), s.onSurface)
    assertEquals(Color(0xFF424940), s.surfaceVariant)
    assertEquals(Color(0xFFC1C9BE), s.onSurfaceVariant)
    assertEquals(Color(0xFFE0E4DB), s.inverseSurface)
    assertEquals(Color(0xFF2D322C), s.inverseOnSurface)
    assertEquals(Color(0xFF8B9389), s.outline)
    assertEquals(Color(0xFF424940), s.outlineVariant)
    assertEquals(Color(0xFF000000), s.scrim)

    assertEquals(Color(0xFF101510), s.surfaceDim)
    assertEquals(Color(0xFF363A35), s.surfaceBright)
    assertEquals(Color(0xFF0B0F0B), s.surfaceContainerLowest)
    assertEquals(Color(0xFF181D18), s.surfaceContainerLow)
    assertEquals(Color(0xFF1C211C), s.surfaceContainer)
    assertEquals(Color(0xFF272B26), s.surfaceContainerHigh)
    assertEquals(Color(0xFF313630), s.surfaceContainerHighest)
  }

  @Test
  fun fixedAndStaticColors_matchFigmaDesignSpecTokens() {
    assertEquals(Color(0xFFB7F1B9), GroundFixedColors.primaryFixed)
    assertEquals(Color(0xFF9CD49F), GroundFixedColors.primaryFixedDim)
    assertEquals(Color(0xFF002108), GroundFixedColors.onPrimaryFixed)
    assertEquals(Color(0xFF1D5128), GroundFixedColors.onPrimaryFixedVariant)

    assertEquals(Color(0xFFD4E8D1), GroundFixedColors.secondaryFixed)
    assertEquals(Color(0xFFB8CCB5), GroundFixedColors.secondaryFixedDim)
    assertEquals(Color(0xFF0F1F11), GroundFixedColors.onSecondaryFixed)
    assertEquals(Color(0xFF3A4B3A), GroundFixedColors.onSecondaryFixedVariant)

    assertEquals(Color(0xFFBDEAF3), GroundFixedColors.tertiaryFixed)
    assertEquals(Color(0xFFA1CED7), GroundFixedColors.tertiaryFixedDim)
    assertEquals(Color(0xFF001F24), GroundFixedColors.onTertiaryFixed)
    assertEquals(Color(0xFF1F4D54), GroundFixedColors.onTertiaryFixedVariant)

    assertEquals(
      listOf("#F37C22", "#D13135", "#7A279F", "#2278CF", "#3C8D40", "#F9BF40"),
      GroundStaticColors.hexList,
    )
  }

  @Test
  fun contentColorFor_resolvesExpectedOnColorsForAllSurfaceRoles() {
    for (scheme in listOf(GroundLightColorScheme, GroundDarkColorScheme)) {
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
  }
}
