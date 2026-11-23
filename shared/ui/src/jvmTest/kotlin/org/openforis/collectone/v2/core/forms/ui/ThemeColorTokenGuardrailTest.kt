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

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Fails when UI code spells out a theme color as a literal instead of using its
 * `MaterialTheme.colorScheme` token name.
 *
 * Scans the production source sets of every UI build (`shared/ui`, `shared/app`, `shared/web`,
 * `shared/mobile`, `shared/map`, and `mobileWebApp/workbench`) for `Color(0x…)` and `fromArgb(0x…)`
 * literals whose ARGB equals a role of [AppLightColorScheme] or [AppDarkColorScheme]. One-off
 * colors (accents, data series, map overlays, logos, and illustrations) don't match a role and stay
 * allowed. Opaque white and black are skipped: they are neutral constants as often as they are
 * theme roles.
 *
 * Literals that match a role but can't use it without changing how they render are listed in
 * [allowlist]. The list may only shrink: the test also fails when an entry no longer matches, so
 * replacing a literal with its token means deleting its entry.
 */
class ThemeColorTokenGuardrailTest {
  private val repoRoot = File("../..")
  private val scannedBuilds =
    listOf(
      "shared/ui",
      "shared/app",
      "shared/web",
      "shared/mobile",
      "shared/map",
      "mobileWebApp/workbench",
    )
  private val themeFile =
    "shared/ui/src/commonMain/kotlin/org/openforis/collectone/v2/core/forms/ui/AppTheme.kt"
  private val skippedDirectories = setOf("build", ".gradle", ".kotlin", "node_modules")
  private val neutralArgbs = setOf(0xFFFFFFFFL, 0xFF000000L)
  private val literal = Regex("""(?:Color|Argb)\(\s*0x([0-9A-Fa-f]{8})L?\s*\)""")

  /** Literals that match a theme role but keep their value, keyed `path: 0xAARRGGBB`. */
  private val allowlist =
    setOf(
      // Dark ink on light marker and swatch fills. It stays the same in the dark theme, where
      // `onSurface` is light, so the token would change how it renders there.
      "shared/app/map/src/commonMain/kotlin/org/openforis/collectone/v2/app/ui/map/MapOverlays.kt: " +
        "0xFF181D18",
      "shared/web/formeditor/src/commonMain/kotlin/org/openforis/collectone/v2/web/ui/formeditor/" +
        "ColorDot.kt: 0xFF181D18",
      "shared/web/formeditor/src/commonMain/kotlin/org/openforis/collectone/v2/web/ui/formeditor/" +
        "FormStatusMarkerEditor.kt: 0xFF181D18",
    )

  @Test
  fun themeColorsAreReferencedByTokenName() {
    assertTrue(File(repoRoot, themeFile).isFile, "Run from the shared/ui project directory")
    val roles = themeRolesByArgb()
    val violations =
      sourceFiles().flatMap { (path, file) ->
        literal
          .findAll(file.readText())
          .mapNotNull { match ->
            val argb = match.groupValues[1].toLong(16)
            roles[argb]?.let { Violation(path, "0x${match.groupValues[1].uppercase()}", it) }
          }
          .toList()
      }
    assertOnlyAllowlistedOffenders(violations.distinct(), allowlist)
  }

  @Test
  fun allowlistCheckFailsOnNewOffenders() {
    val failure =
      assertFailsWith<AssertionError> {
        assertOnlyAllowlistedOffenders(
          listOf(Violation("shared/app/New.kt", "0xFF36693E", listOf("light primary"))),
          allowlist = emptySet(),
        )
      }
    assertContains(failure.message.orEmpty(), "shared/app/New.kt: 0xFF36693E is light primary")
  }

  @Test
  fun allowlistCheckFailsOnStaleEntries() {
    val failure =
      assertFailsWith<AssertionError> {
        assertOnlyAllowlistedOffenders(
          emptyList(),
          allowlist = setOf("shared/app/Old.kt: 0xFF36693E"),
        )
      }
    assertContains(failure.message.orEmpty(), "shared/app/Old.kt: 0xFF36693E")
  }

  @Test
  fun allowlistCheckPassesWhenEveryEntryStillOffends() {
    assertOnlyAllowlistedOffenders(
      listOf(Violation("shared/app/Old.kt", "0xFF36693E", listOf("light primary"))),
      allowlist = setOf("shared/app/Old.kt: 0xFF36693E"),
    )
  }

  /** Every non-neutral role color across all six theme/contrast modes, mapped to its roles. */
  private fun themeRolesByArgb(): Map<Long, List<String>> =
    (rolesOf(AppLightColorScheme).map { (role, argb) -> argb to "light $role" } +
        rolesOf(AppLightMediumContrastColorScheme).map { (role, argb) ->
          argb to "light medium contrast $role"
        } +
        rolesOf(AppLightHighContrastColorScheme).map { (role, argb) ->
          argb to "light high contrast $role"
        } +
        rolesOf(AppDarkColorScheme).map { (role, argb) -> argb to "dark $role" } +
        rolesOf(AppDarkMediumContrastColorScheme).map { (role, argb) ->
          argb to "dark medium contrast $role"
        } +
        rolesOf(AppDarkHighContrastColorScheme).map { (role, argb) ->
          argb to "dark high contrast $role"
        } +
        fixedAndStaticTokens())
      .filterNot { (argb, _) -> argb in neutralArgbs }
      .groupBy({ it.first }, { it.second })

  /**
   * Fixed color roles across standard, medium, and high contrast modes plus the app's static accent
   * colors (Orange, Red, Purple, Blue, Green, Yellow).
   */
  private fun fixedAndStaticTokens(): List<Pair<Long, String>> =
    (fixedRolesOf("AppFixedColors", AppStandardFixedColors) +
        fixedRolesOf(
          "AppLightMediumContrastFixedColors",
          AppLightMediumContrastFixedColors,
        ) +
        fixedRolesOf("AppLightHighContrastFixedColors", AppLightHighContrastFixedColors) +
        fixedRolesOf("AppDarkMediumContrastFixedColors", AppDarkMediumContrastFixedColors) +
        fixedRolesOf("AppDarkHighContrastFixedColors", AppDarkHighContrastFixedColors) +
        listOf(
          "AppStaticColors.Orange" to AppStaticColors.Orange,
          "AppStaticColors.Red" to AppStaticColors.Red,
          "AppStaticColors.Purple" to AppStaticColors.Purple,
          "AppStaticColors.Blue" to AppStaticColors.Blue,
          "AppStaticColors.Green" to AppStaticColors.Green,
          "AppStaticColors.Yellow" to AppStaticColors.Yellow,
        ))
      .map { (token, color) -> (color.toArgb().toLong() and 0xFFFFFFFFL) to token }

  private fun fixedRolesOf(
    prefix: String,
    fixed: AppFixedColorScheme,
  ): List<Pair<String, androidx.compose.ui.graphics.Color>> =
    listOf(
      "$prefix.primaryFixed" to fixed.primaryFixed,
      "$prefix.primaryFixedDim" to fixed.primaryFixedDim,
      "$prefix.onPrimaryFixed" to fixed.onPrimaryFixed,
      "$prefix.onPrimaryFixedVariant" to fixed.onPrimaryFixedVariant,
      "$prefix.secondaryFixed" to fixed.secondaryFixed,
      "$prefix.secondaryFixedDim" to fixed.secondaryFixedDim,
      "$prefix.onSecondaryFixed" to fixed.onSecondaryFixed,
      "$prefix.onSecondaryFixedVariant" to fixed.onSecondaryFixedVariant,
      "$prefix.tertiaryFixed" to fixed.tertiaryFixed,
      "$prefix.tertiaryFixedDim" to fixed.tertiaryFixedDim,
      "$prefix.onTertiaryFixed" to fixed.onTertiaryFixed,
      "$prefix.onTertiaryFixedVariant" to fixed.onTertiaryFixedVariant,
    )

  private fun rolesOf(scheme: ColorScheme): List<Pair<String, Long>> =
    listOf(
        "primary" to scheme.primary,
        "onPrimary" to scheme.onPrimary,
        "primaryContainer" to scheme.primaryContainer,
        "onPrimaryContainer" to scheme.onPrimaryContainer,
        "inversePrimary" to scheme.inversePrimary,
        "secondary" to scheme.secondary,
        "onSecondary" to scheme.onSecondary,
        "secondaryContainer" to scheme.secondaryContainer,
        "onSecondaryContainer" to scheme.onSecondaryContainer,
        "tertiary" to scheme.tertiary,
        "onTertiary" to scheme.onTertiary,
        "tertiaryContainer" to scheme.tertiaryContainer,
        "onTertiaryContainer" to scheme.onTertiaryContainer,
        "background" to scheme.background,
        "onBackground" to scheme.onBackground,
        "surface" to scheme.surface,
        "onSurface" to scheme.onSurface,
        "surfaceVariant" to scheme.surfaceVariant,
        "onSurfaceVariant" to scheme.onSurfaceVariant,
        "surfaceTint" to scheme.surfaceTint,
        "inverseSurface" to scheme.inverseSurface,
        "inverseOnSurface" to scheme.inverseOnSurface,
        "error" to scheme.error,
        "onError" to scheme.onError,
        "errorContainer" to scheme.errorContainer,
        "onErrorContainer" to scheme.onErrorContainer,
        "outline" to scheme.outline,
        "outlineVariant" to scheme.outlineVariant,
        "scrim" to scheme.scrim,
        "surfaceBright" to scheme.surfaceBright,
        "surfaceDim" to scheme.surfaceDim,
        "surfaceContainerLowest" to scheme.surfaceContainerLowest,
        "surfaceContainerLow" to scheme.surfaceContainerLow,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest,
        "primaryFixed" to scheme.primaryFixed,
        "primaryFixedDim" to scheme.primaryFixedDim,
        "onPrimaryFixed" to scheme.onPrimaryFixed,
        "onPrimaryFixedVariant" to scheme.onPrimaryFixedVariant,
        "secondaryFixed" to scheme.secondaryFixed,
        "secondaryFixedDim" to scheme.secondaryFixedDim,
        "onSecondaryFixed" to scheme.onSecondaryFixed,
        "onSecondaryFixedVariant" to scheme.onSecondaryFixedVariant,
        "tertiaryFixed" to scheme.tertiaryFixed,
        "tertiaryFixedDim" to scheme.tertiaryFixedDim,
        "onTertiaryFixed" to scheme.onTertiaryFixed,
        "onTertiaryFixedVariant" to scheme.onTertiaryFixedVariant,
      )
      .map { (role, color) -> role to (color.toArgb().toLong() and 0xFFFFFFFFL) }

  /** Production Kotlin files of [scannedBuilds], keyed by repository-relative path. */
  private fun sourceFiles(): List<Pair<String, File>> = scannedBuilds.flatMap { build ->
    File(repoRoot, build)
      .walkTopDown()
      .onEnter { it.name !in skippedDirectories }
      .filter { it.isFile && it.extension == "kt" }
      .map { it.relativeTo(repoRoot).invariantSeparatorsPath to it }
      .filter { (path, _) -> path != themeFile && isProductionSource(path) }
      .toList()
  }

  private fun isProductionSource(path: String): Boolean =
    path.split('/').let { segments ->
      val sourceSet = segments.getOrNull(segments.indexOf("src") + 1).orEmpty()
      "src" in segments && sourceSet.endsWith("Main")
    }

  private data class Violation(val path: String, val literal: String, val roles: List<String>) {
    val key = "$path: $literal"

    override fun toString() = "$key is ${roles.joinToString(", ")}"
  }

  /**
   * Fails on violations missing from [allowlist], and on [allowlist] entries that no longer match a
   * theme role (so the list can only shrink).
   */
  private fun assertOnlyAllowlistedOffenders(violations: List<Violation>, allowlist: Set<String>) {
    val unexpected = violations.filterNot { it.key in allowlist }
    assertTrue(
      unexpected.isEmpty(),
      "Use the MaterialTheme.colorScheme token instead of the literal:\n" +
        unexpected.joinToString("\n"),
    )
    val stale = allowlist - violations.map { it.key }.toSet()
    assertTrue(
      stale.isEmpty(),
      "These allowlisted literals no longer match a theme color; remove them from the allowlist:\n" +
        stale.joinToString("\n"),
    )
  }
}
