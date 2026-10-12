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
package org.groundplatform.v2.devtools.prototypeapp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the inward dependency rule of `docs/technical/client/architecture.md` and the module
 * boundaries the prototype is being split along.
 *
 * The `app` layers live in `shared/app` (see its own guardrail test). Here:
 * - Nothing here imports `app.data` (only `app`'s own `data/` and `di/` may).
 * - Views (everything outside `ui/viewmodel/`) never import `app.data` or `app.client`.
 *
 * Modules (future `shared/mobile`, `shared/web`, and the Wasm mobile workbench):
 * - `mobile` and `web` import only `app` (never each other, `workbench`, or the host).
 * - `workbench` imports `app` and `mobile`, never `web` or the host.
 *
 * Imports are checked textually so the test needs no classpath scanning on any platform.
 */
class LayerDependencyGuardrailTest {
  private val kotlinRoot = File("src/commonMain/kotlin/org/groundplatform/v2")
  private val appRoot = File(kotlinRoot, "app")

  private val app = "org.groundplatform.v2.app"
  private val mobile = "org.groundplatform.v2.mobile"
  private val web = "org.groundplatform.v2.web"
  private val workbench = "org.groundplatform.v2.workbench"
  private val host = "org.groundplatform.v2.devtools.prototypeapp"

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
        "workbench/ui/WorkbenchXFormsPresets.kt",
        "workbench/ui/viewmodel/WorkbenchViewModel.kt",
      )
    val files =
      kotlinFiles(kotlinRoot).filterNot { file ->
        file.relativeTo(kotlinRoot).invariantSeparatorsPath in allowedFiles
      }
    val forbidden =
      listOf(
        "org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer",
        "org.groundplatform.v2.core.forms.serialization.FormDefXmlSerializer",
        "org.groundplatform.v2.core.forms.serialization.FormDefXmlDeserializer",
        "org.groundplatform.v2.core.forms.serialization.InstanceXmlSerializer",
      )
    val bad = offenders(files, forbidden)
    assertTrue(
      bad.isEmpty(),
      "XML serialization must only be used at import/export and XForms workbench boundaries:\n" +
        bad.joinToString("\n"),
    )
  }

  @Test
  fun modulesOnlyDependOnAllowedModules() {
    val rules =
      mapOf(
        "mobile" to listOf("$web.", "$workbench.", "$host."),
        "web" to listOf("$mobile.", "$workbench.", "$host."),
        "workbench" to listOf("$web.", "$host."),
      )
    val actual = rules.flatMap { (module, forbidden) ->
      offenders(kotlinFiles(File(kotlinRoot, module)), forbidden)
    }
    val unexpected = actual.filterNot { it in KNOWN_MODULE_VIOLATIONS }
    val stale = KNOWN_MODULE_VIOLATIONS.filterNot { it in actual }
    assertTrue(
      unexpected.isEmpty(),
      "New cross-module imports (see the module rules above):\n${unexpected.joinToString("\n")}",
    )
    assertTrue(
      stale.isEmpty(),
      "Fixed cross-module imports; remove them from KNOWN_MODULE_VIOLATIONS:\n" +
        stale.joinToString("\n"),
    )
  }

  private companion object {
    /**
     * Cross-module imports that predate the module split. Shrink this list as code moves to the
     * right module; the test fails on additions and on entries that no longer occur.
     */
    val KNOWN_MODULE_VIOLATIONS: Set<String> = emptySet()
  }
}
