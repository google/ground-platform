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

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the inward dependency rule of `docs/technical/client/architecture.md` across the
 * prototype's packages:
 * - `domain/` imports nothing from `data/`, `client/`, `di/`, `ui/`, or Compose.
 * - `data/` imports nothing from `di/`, `ui/`, or Compose.
 * - Only `data/` and `di/` may import `data/` packages (neither Views nor ViewModels in `ui/` may
 *   import `data.*`).
 * - Views (everything outside `data/`, `client/`, `domain/`, `di/`, and `ui/viewmodel/`) never
 *   import `data/` or `client/` packages.
 *
 * Imports are checked textually so the test needs no classpath scanning on any platform.
 */
class LayerDependencyGuardrailTest {
  private val mainRoot = File("src/commonMain/kotlin/org/groundplatform/v2/devtools/prototypeapp")
  private val pkg = "org.groundplatform.v2.devtools.prototypeapp"

  private fun kotlinFiles(subdir: String? = null): Sequence<File> =
    (subdir?.let { File(mainRoot, it) } ?: mainRoot).walkTopDown().filter {
      it.isFile && it.extension == "kt"
    }

  private fun importsOf(file: File): List<String> =
    file.readLines().filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() }

  private fun offenders(files: Sequence<File>, forbidden: List<String>): List<String> =
    files
      .flatMap { file ->
        importsOf(file)
          .filter { import -> forbidden.any { import.startsWith(it) } }
          .map { "${file.relativeTo(mainRoot).invariantSeparatorsPath}: import $it" }
      }
      .toList()

  @Test
  fun domainImportsOnlyDomainAndSharedCore() {
    assertTrue(mainRoot.isDirectory, "Run from the prototypeApp project directory")
    val forbidden =
      listOf(
        "$pkg.data.",
        "$pkg.client.",
        "$pkg.di.",
        "$pkg.ui.",
        "androidx.compose.",
      )
    val bad = offenders(kotlinFiles("domain"), forbidden)
    assertTrue(bad.isEmpty(), "domain/ depends on outer layers:\n${bad.joinToString("\n")}")
  }

  @Test
  fun dataLayerDoesNotImportPresentationOrDi() {
    val forbidden =
      listOf(
        "$pkg.di.",
        "$pkg.ui.",
        "androidx.compose.",
      )
    val bad = offenders(kotlinFiles("data"), forbidden)
    assertTrue(bad.isEmpty(), "data/ depends on presentation or di:\n${bad.joinToString("\n")}")
  }

  @Test
  fun uiDoesNotImportDataLayer() {
    val allowedRoots = listOf("data/", "di/")
    val files =
      kotlinFiles().filterNot { file ->
        val rel = file.relativeTo(mainRoot).invariantSeparatorsPath
        allowedRoots.any { rel.startsWith(it) }
      }
    val bad = offenders(files, listOf("$pkg.data."))
    assertTrue(
      bad.isEmpty(),
      "Only data/ and di/ may import data.* packages:\n${bad.joinToString("\n")}",
    )
  }

  @Test
  fun viewsDoNotImportDataSourcesOrClients() {
    val allowedRoots = listOf("data/", "client/", "domain/", "di/", "ui/viewmodel/")
    val files =
      kotlinFiles().filterNot { file ->
        val rel = file.relativeTo(mainRoot).invariantSeparatorsPath
        allowedRoots.any { rel.startsWith(it) }
      }
    val bad = offenders(files, listOf("$pkg.data.", "$pkg.client."))
    assertTrue(
      bad.isEmpty(),
      "Views / state holders reach past the ViewModel into data or client packages:\n" +
        bad.joinToString("\n"),
    )
  }
}
