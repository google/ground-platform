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
package org.groundplatform.v2.mobile

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the dependency rules of `docs/technical/client/architecture.md` for the mobile app:
 * - Nothing here imports `app.data` (only `shared/app`'s own `data/` and `di/` may).
 * - Views (everything outside `ui/viewmodel/`) never import `app.data` or `app.client`.
 * - Mobile code never imports the web console, the workbench, or the developer tools.
 *
 * Imports are checked textually so the test needs no classpath scanning on any platform.
 */
class LayerDependencyGuardrailTest {
  private val kotlinRoot = File("src/commonMain/kotlin/org/groundplatform/v2/mobile")

  private fun offenders(forbidden: List<String>, skip: (String) -> Boolean = { false }) =
    kotlinRoot
      .walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .filterNot { skip(it.relativeTo(kotlinRoot).invariantSeparatorsPath) }
      .flatMap { file ->
        file
          .readLines()
          .filter { it.startsWith("import ") }
          .map { it.removePrefix("import ").trim() }
          .filter { import -> forbidden.any { import.startsWith(it) } }
          .map { "${file.relativeTo(kotlinRoot).invariantSeparatorsPath}: import $it" }
      }
      .toList()

  @Test
  fun doesNotImportDataLayer() {
    assertTrue(kotlinRoot.isDirectory, "Run from the shared/mobile project directory")
    val bad = offenders(listOf("org.groundplatform.v2.app.data."))
    assertTrue(
      bad.isEmpty(),
      "Only shared/app may import data.* packages:\n${bad.joinToString("\n")}",
    )
  }

  @Test
  fun viewsDoNotImportClients() {
    val bad =
      offenders(listOf("org.groundplatform.v2.app.client.")) { it.contains("ui/viewmodel/") }
    assertTrue(
      bad.isEmpty(),
      "Views reach past the ViewModel into client packages:\n${bad.joinToString("\n")}",
    )
  }

  @Test
  fun doesNotImportWebWorkbenchOrHost() {
    val bad =
      offenders(
        listOf(
          "org.groundplatform.v2.web.",
          "org.groundplatform.v2.workbench.",
          "org.groundplatform.v2.devtools.",
        )
      )
    assertTrue(bad.isEmpty(), "Mobile code depends on web or workbench:\n${bad.joinToString("\n")}")
  }
}
