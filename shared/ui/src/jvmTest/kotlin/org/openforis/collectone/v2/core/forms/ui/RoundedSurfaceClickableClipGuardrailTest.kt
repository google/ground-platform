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
 * Guardrail ensuring rounded chips, pills, badges, and surfaces clip interactive modifiers
 * (`.clickable`, `.hoverable`, `.selectable`, `.toggleable`, `.combinedClickable`) to their rounded
 * contour before applying the interactive modifier.
 *
 * In Material 3's non-`onClick` `Surface(shape = ..., modifier = ...)`, `modifier` is applied
 * *before* `Surface`'s internal `.clip(shape)`, and `Modifier.background(color, shape)` draws a
 * shaped fill without clipping the graphics layer. An unclipped interactive modifier in either
 * position paints a sharp rectangular hover/focus/ripple overlay extending past the rounded
 * corners.
 */
class RoundedSurfaceClickableClipGuardrailTest {
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
    )
  private val skippedDirectories = setOf("build", ".gradle", ".kotlin", "node_modules")
  private val interactiveCall =
    Regex("""\.\s*(?:clickable|hoverable|selectable|toggleable|combinedClickable)\b""")

  @Test
  fun roundedSurfacesAndBackgroundsClipInteractiveModifiers() {
    assertTrue(
      File(repoRoot, "shared/ui/build.gradle.kts").isFile,
      "Run from the shared/ui project directory",
    )
    val violations =
      sourceFiles().flatMap { (path, file) -> findViolationsInSource(path, file.readText()) }
    assertTrue(
      violations.isEmpty(),
      "Clip interactive modifiers (.clip(shape)) before .clickable / .hoverable on rounded " +
        "surfaces and shaped backgrounds so hover and ripple overlays follow the rounded " +
        "contour:\n" +
        violations.joinToString("\n"),
    )
  }

  @Test
  fun detectorFlagsUnclippedRoundedSurfaceClickable() {
    val sample =
      """
      Surface(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.clickable { onSelect() },
      ) { Text("Overview") }
      """
        .trimIndent()
    val violations = findViolationsInSource("Sample.kt", sample)
    assertTrue(violations.isNotEmpty())
    assertContains(violations.single(), "Sample.kt:1")
  }

  @Test
  fun detectorAllowsClippedRoundedSurfaceClickable() {
    val sample =
      """
      val stepChipShape = RoundedCornerShape(18.dp)
      Surface(
        shape = stepChipShape,
        modifier = Modifier.clip(stepChipShape).clickable { onSelect() },
      ) { Text("Overview") }
      """
        .trimIndent()
    assertTrue(findViolationsInSource("Sample.kt", sample).isEmpty())
  }

  @Test
  fun detectorFlagsUnclippedShapedBackgroundClickable() {
    val sample =
      """
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(color, MaterialTheme.shapes.small)
            .clickable { onSelect() }
      )
      """
        .trimIndent()
    val violations = findViolationsInSource("Sample.kt", sample)
    assertTrue(violations.isNotEmpty())
    assertContains(violations.single(), "Sample.kt")
  }

  @Test
  fun detectorAllowsClippedShapedBackgroundClickable() {
    val sample =
      """
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(color, MaterialTheme.shapes.small)
            .clickable { onSelect() }
      )
      """
        .trimIndent()
    assertTrue(findViolationsInSource("Sample.kt", sample).isEmpty())
  }

  internal fun findViolationsInSource(path: String, source: String): List<String> {
    val violations = mutableListOf<String>()

    // 1. Check `Surface(...)` and `TonalBadge(...)` calls with a non-rectangle `shape` and an
    // interactive `modifier`.
    val surfaceOrBadgeHead = Regex("""\b(Surface|TonalBadge)\s*\(""")
    for (match in surfaceOrBadgeHead.findAll(source)) {
      val callName = match.groupValues[1]
      val argsStart = match.range.last
      val argsEnd = findMatchingParen(source, argsStart) ?: continue
      val argsBlock = source.substring(argsStart + 1, argsEnd)
      val topArgs = splitTopLevelArgs(argsBlock)
      val shapeArg =
        if (callName == "TonalBadge") {
          "CircleShape"
        } else {
          topArgs
            .firstOrNull { it.trimStart().startsWith("shape") && '=' in it }
            ?.substringAfter('=') ?: continue
        }
      if ("RectangleShape" in shapeArg) continue
      val modifierArg =
        topArgs
          .firstOrNull { it.trimStart().startsWith("modifier") && '=' in it }
          ?.substringAfter('=') ?: continue
      val interactiveMatch = interactiveCall.find(modifierArg) ?: continue
      val prefixBeforeInteractive = modifierArg.substring(0, interactiveMatch.range.first)
      if (!Regex("""\.\s*clip\s*\(""").containsMatchIn(prefixBeforeInteractive)) {
        val line = 1 + source.substring(0, match.range.first).count { it == '\n' }
        violations +=
          "$path:$line: $callName with shape=${shapeArg.trim()} has unclipped interactive modifier"
      }
    }

    // 2. Check `.background(..., shape)` chains followed by an interactive modifier without
    // `.clip`.
    val backgroundHead = Regex("""\.\s*background\s*\(""")
    for (match in backgroundHead.findAll(source)) {
      val argsStart = match.range.last
      val argsEnd = findMatchingParen(source, argsStart) ?: continue
      val bgArgs = splitTopLevelArgs(source.substring(argsStart + 1, argsEnd))
      val hasShapeArg =
        bgArgs.size >= 2 || bgArgs.any { it.trimStart().startsWith("shape") && '=' in it }
      if (!hasShapeArg) continue
      if (bgArgs.any { "RectangleShape" in it }) continue

      // Look back to the start of the `Modifier` chain and forward through chained calls.
      val chainStart =
        source.lastIndexOf("Modifier", match.range.first).takeIf { it >= 0 } ?: match.range.first
      val chainTail = extractModifierChainTail(source, argsEnd + 1)
      val interactiveMatch = interactiveCall.find(chainTail) ?: continue
      val fullChainBeforeInteractive =
        source.substring(chainStart, argsEnd + 1) +
          chainTail.substring(0, interactiveMatch.range.first)
      if (!Regex("""\.\s*clip\s*\(""").containsMatchIn(fullChainBeforeInteractive)) {
        val line = 1 + source.substring(0, match.range.first).count { it == '\n' }
        violations +=
          "$path:$line: .background(..., shape) followed by unclipped interactive modifier"
      }
    }

    return violations
  }

  private fun findMatchingParen(text: String, openIndex: Int): Int? {
    var depth = 0
    var i = openIndex
    while (i < text.length) {
      when (text[i]) {
        '(' -> depth++
        ')' -> {
          depth--
          if (depth == 0) return i
        }
      }
      i++
    }
    return null
  }

  private fun splitTopLevelArgs(argsBlock: String): List<String> {
    val result = mutableListOf<String>()
    var depth = 0
    var start = 0
    for (i in argsBlock.indices) {
      when (argsBlock[i]) {
        '(',
        '{',
        '[' -> depth++
        ')',
        '}',
        ']' -> if (depth > 0) depth--
        ',' ->
          if (depth == 0) {
            result += argsBlock.substring(start, i)
            start = i + 1
          }
      }
    }
    if (start <= argsBlock.length) {
      val tail = argsBlock.substring(start)
      if (tail.isNotBlank()) result += tail
    }
    return result
  }

  private fun extractModifierChainTail(text: String, startIndex: Int): String {
    val sb = StringBuilder()
    var i = startIndex
    var depth = 0
    while (i < text.length) {
      val c = text[i]
      when {
        c == '(' || c == '{' -> {
          depth++
          sb.append(c)
        }
        c == ')' || c == '}' -> {
          if (depth == 0) break
          depth--
          sb.append(c)
        }
        c == ',' && depth == 0 -> break
        else -> sb.append(c)
      }
      i++
    }
    return sb.toString()
  }

  private fun sourceFiles(): List<Pair<String, File>> = scannedBuilds.flatMap { build ->
    val root = File(repoRoot, build)
    if (!root.exists()) return@flatMap emptyList()
    root
      .walkTopDown()
      .onEnter { it.name !in skippedDirectories }
      .filter { it.isFile && it.extension == "kt" }
      .map { it.relativeTo(repoRoot).invariantSeparatorsPath to it }
      .filter { (path, _) -> isProductionSource(path) }
      .toList()
  }

  private fun isProductionSource(path: String): Boolean =
    path.split('/').let { segments ->
      val sourceSet = segments.getOrNull(segments.indexOf("src") + 1).orEmpty()
      "src" in segments && sourceSet.endsWith("Main")
    }
}
