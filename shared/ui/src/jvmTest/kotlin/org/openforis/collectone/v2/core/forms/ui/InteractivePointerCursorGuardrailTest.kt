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

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Guardrail ensuring production UI code imports interactive Material 3 components and click
 * modifiers from `org.openforis.collectone.v2.core.forms.ui.controls` rather than directly from
 * `androidx.compose.material3` or `androidx.compose.foundation`.
 *
 * Compose Multiplatform and Material 3 do not provide a theme-level hook for mouse hover cursors,
 * so Collect One routes buttons, clickable cards/surfaces, chips, toggles, menu items, tabs,
 * sliders, and `clickable`/`selectable`/`toggleable` modifiers through thin wrappers backed by
 * [LocalInteractivePointerIcon] in [AppTheme].
 */
class InteractivePointerCursorGuardrailTest {
  private val repoRoot = File("../..")
  private val scannedBuilds =
    listOf(
      "shared/ui",
      "shared/app",
      "shared/web",
      "shared/mobile",
      "shared/map",
      "webApp",
      "mobileWebApp",
      "balloonFormApp",
      "androidApp",
    )
  private val controlsDir =
    "shared/ui/src/commonMain/kotlin/org/openforis/collectone/v2/core/forms/ui/controls/"
  private val skippedDirectories = setOf("build", ".gradle", ".kotlin", "node_modules")

  private val wrappedImports: Set<String> =
    setOf(
      "androidx.compose.material3.AssistChip",
      "androidx.compose.material3.Button",
      "androidx.compose.material3.Card",
      "androidx.compose.material3.Checkbox",
      "androidx.compose.material3.DropdownMenuItem",
      "androidx.compose.material3.ElevatedButton",
      "androidx.compose.material3.ElevatedCard",
      "androidx.compose.material3.ExtendedFloatingActionButton",
      "androidx.compose.material3.FilledIconButton",
      "androidx.compose.material3.FilledTonalButton",
      "androidx.compose.material3.FilledTonalIconButton",
      "androidx.compose.material3.FilterChip",
      "androidx.compose.material3.FloatingActionButton",
      "androidx.compose.material3.IconButton",
      "androidx.compose.material3.IconToggleButton",
      "androidx.compose.material3.InputChip",
      "androidx.compose.material3.NavigationDrawerItem",
      "androidx.compose.material3.OutlinedButton",
      "androidx.compose.material3.OutlinedCard",
      "androidx.compose.material3.OutlinedIconButton",
      "androidx.compose.material3.OutlinedIconToggleButton",
      "androidx.compose.material3.RadioButton",
      "androidx.compose.material3.SegmentedButton",
      "androidx.compose.material3.Slider",
      "androidx.compose.material3.Surface",
      "androidx.compose.material3.Switch",
      "androidx.compose.material3.Tab",
      "androidx.compose.material3.TextButton",
      "androidx.compose.foundation.clickable",
      "androidx.compose.foundation.selection.selectable",
      "androidx.compose.foundation.selection.toggleable",
    )

  private val importLine = Regex("""^import\s+([A-Za-z0-9_.]+)(?:\s+as\s+[A-Za-z0-9_]+)?\s*$""")

  @Test
  fun productionCodeUsesThemeBackedInteractiveControls() {
    assertTrue(
      File(repoRoot, "shared/ui/build.gradle.kts").isFile,
      "Run from the shared/ui project directory",
    )
    val violations =
      sourceFiles().flatMap { (path, file) -> findViolationsInSource(path, file.readText()) }
    assertTrue(
      violations.isEmpty(),
      "Import interactive controls and modifiers from " +
        "org.openforis.collectone.v2.core.forms.ui.controls so hover cursors follow AppTheme " +
        "(or use backdropClickable for non-button click catchers):\n" +
        violations.joinToString("\n"),
    )
  }

  @Test
  fun detectorFlagsRawMaterial3AndFoundationInteractiveImports() {
    val sample =
      """
      package sample

      import androidx.compose.foundation.clickable
      import androidx.compose.material3.Button
      import androidx.compose.material3.MaterialTheme
      """
        .trimIndent()
    val violations = findViolationsInSource("Sample.kt", sample)
    assertTrue(violations.size == 2)
    assertContains(violations[0], "androidx.compose.foundation.clickable")
    assertContains(violations[1], "androidx.compose.material3.Button")
  }

  @Test
  fun detectorAllowsThemeBackedControlsImports() {
    val sample =
      """
      package sample

      import androidx.compose.material3.MaterialTheme
      import org.openforis.collectone.v2.core.forms.ui.controls.Button
      import org.openforis.collectone.v2.core.forms.ui.controls.backdropClickable
      import org.openforis.collectone.v2.core.forms.ui.controls.clickable
      """
        .trimIndent()
    assertTrue(findViolationsInSource("Sample.kt", sample).isEmpty())
  }

  internal fun findViolationsInSource(path: String, source: String): List<String> =
    source.lines().mapIndexedNotNull { index, line ->
      val match = importLine.matchEntire(line.trim()) ?: return@mapIndexedNotNull null
      val imported = match.groupValues[1]
      if (imported in wrappedImports) {
        "$path:${index + 1}: direct import $imported"
      } else {
        null
      }
    }

  private fun sourceFiles(): List<Pair<String, File>> = scannedBuilds.flatMap { build ->
    val root = File(repoRoot, build)
    if (!root.exists()) return@flatMap emptyList()
    root
      .walkTopDown()
      .onEnter { it.name !in skippedDirectories }
      .filter { it.isFile && it.extension == "kt" }
      .map { it.relativeTo(repoRoot).invariantSeparatorsPath to it }
      .filter { (path, _) -> !path.startsWith(controlsDir) && isProductionSource(path) }
      .toList()
  }

  private fun isProductionSource(path: String): Boolean =
    path.split('/').let { segments ->
      val sourceSet = segments.getOrNull(segments.indexOf("src") + 1).orEmpty()
      "src" in segments && sourceSet.endsWith("Main")
    }
}
