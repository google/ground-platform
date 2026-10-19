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
package org.openforis.collectone.v2.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the inward dependency rule of `docs/technical/client/architecture.md` inside this module.
 *
 * Layers (all under `org.openforis.collectone.v2.app`):
 * - `domain/` imports nothing from `data/`, `client/`, `di/`, `ui/`, or Compose.
 * - `data/` imports nothing from `di/`, `ui/`, or Compose.
 * - Only `data/` and `di/` may import `data/` packages (neither Views nor ViewModels may import
 *   `data.*`).
 * - Views (everything outside `data/`, `client/`, `domain/`, `di/`, and `ui/viewmodel/`) never
 *   import `data/` or `client/` packages.
 *
 * This module can't import `mobile`, `web`, or the hosts at all: they aren't on its classpath.
 * Modules built on it check their own Views with the same rules.
 *
 * Imports are checked textually so the test needs no classpath scanning on any platform.
 */
class LayerDependencyGuardrailTest {
  private val kotlinRoot = File("src/commonMain/kotlin/org/openforis/collectone/v2")
  private val appRoot = File(kotlinRoot, "app")

  private val app = "org.openforis.collectone.v2.app"
  private val mobile = "org.openforis.collectone.v2.mobile"
  private val web = "org.openforis.collectone.v2.web"
  private val workbench = "org.openforis.collectone.v2.workbench"
  private val host = "org.openforis.collectone.v2.devtools"

  private fun kotlinFiles(root: File): Sequence<File> =
    root.walkTopDown().filter { it.isFile && it.extension == "kt" }

  private fun importsOf(file: File): List<String> =
    file.readLines().filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() }

  private fun offenders(files: Sequence<File>, forbidden: List<String>): List<String> =
    files
      .flatMap { file ->
        importsOf(file)
          .filter { import -> forbidden.any { import.startsWith(it) } }
          .map { "${file.relativeTo(kotlinRoot).invariantSeparatorsPath}: import $it" }
      }
      .toList()

  private fun File.relToApp(): String = relativeTo(appRoot).invariantSeparatorsPath

  @Test
  fun domainImportsOnlyDomainAndSharedCore() {
    assertTrue(appRoot.isDirectory, "Run from the shared/app project directory")
    val forbidden =
      listOf(
        "$app.data.",
        "$app.client.",
        "$app.di.",
        "$app.ui.",
        "$mobile.",
        "$web.",
        "$workbench.",
        "$host.",
        "androidx.compose.",
      )
    val bad = offenders(kotlinFiles(File(appRoot, "domain")), forbidden)
    assertTrue(bad.isEmpty(), "domain/ depends on outer layers:\n${bad.joinToString("\n")}")
  }

  @Test
  fun dataLayerDoesNotImportPresentationOrDi() {
    val forbidden = listOf("$app.di.", "$app.ui.", "androidx.compose.")
    val bad = offenders(kotlinFiles(File(appRoot, "data")), forbidden)
    assertTrue(bad.isEmpty(), "data/ depends on presentation or di:\n${bad.joinToString("\n")}")
  }

  @Test
  fun uiDoesNotImportDataLayer() {
    val allowedRoots = listOf("data/", "di/")
    val files =
      kotlinFiles(kotlinRoot).filterNot { file ->
        file.startsWith(appRoot) && allowedRoots.any { file.relToApp().startsWith(it) }
      }
    val bad = offenders(files, listOf("$app.data."))
    assertTrue(
      bad.isEmpty(),
      "Only data/ and di/ may import data.* packages:\n${bad.joinToString("\n")}",
    )
  }

  @Test
  fun viewsDoNotImportDataSourcesOrClients() {
    val allowedAppRoots = listOf("data/", "client/", "domain/", "di/")
    val files =
      kotlinFiles(kotlinRoot).filterNot { file ->
        val rel = file.relativeTo(kotlinRoot).invariantSeparatorsPath
        rel.contains("/ui/viewmodel/") ||
          (file.startsWith(appRoot) && allowedAppRoots.any { file.relToApp().startsWith(it) })
      }
    val bad = offenders(files, listOf("$app.data.", "$app.client."))
    assertTrue(
      bad.isEmpty(),
      "Views / state holders reach past the ViewModel into data or client packages:\n" +
        bad.joinToString("\n"),
    )
  }

  @Test
  fun xmlSerializationIsRestrictedToImportExportAndWorkbenchBoundaries() {
    val allowedFiles =
      setOf(
        "app/domain/model/editor/FormEditorModels.kt",
        "app/domain/model/editor/FormPublishing.kt",
        "app/domain/model/editor/FormImport.kt",
      )
    val files =
      kotlinFiles(kotlinRoot).filterNot { file ->
        file.relativeTo(kotlinRoot).invariantSeparatorsPath in allowedFiles
      }
    val forbidden =
      listOf(
        "org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer",
        "org.openforis.collectone.v2.core.forms.serialization.FormDefXmlSerializer",
        "org.openforis.collectone.v2.core.forms.serialization.FormDefXmlDeserializer",
        "org.openforis.collectone.v2.core.forms.serialization.InstanceXmlSerializer",
      )
    val bad = offenders(files, forbidden)
    assertTrue(
      bad.isEmpty(),
      "XML serialization must only be used at import/export and XForms workbench boundaries:\n" +
        bad.joinToString("\n"),
    )
  }
}
