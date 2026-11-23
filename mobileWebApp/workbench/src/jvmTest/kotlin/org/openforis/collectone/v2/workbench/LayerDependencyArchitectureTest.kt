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
package org.openforis.collectone.v2.workbench

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The layer rules of `docs/technical/client/architecture.md` for the workbench, checked with
 * Konsist over the common source set (`src/commonMain`):
 * - Nothing here imports `app.data` (only `shared/app`'s own `data/` and `di/` may).
 * - Views (everything outside `ui/viewmodel/`) never import `app.data` or `app.client`.
 * - Workbench code never imports the web console or the developer tools.
 */
class LayerDependencyArchitectureTest {
  private val root = "org.openforis.collectone.v2.workbench"

  /** Kotlin files of the workbench's common source set (paths are relative to mobileWebApp/). */
  private val commonFiles: List<KoFileDeclaration> =
    Konsist.scopeFromDirectory("mobileWebApp/workbench/src/commonMain/kotlin").files

  private fun KoFileDeclaration.isIn(layer: String): Boolean =
    packagee?.name?.let { it == "$root.$layer" || it.startsWith("$root.$layer.") } == true

  private fun KoFileDeclaration.importsAny(prefixes: List<String>): Boolean =
    imports.any { import ->
      prefixes.any { import.name.startsWith(it) }
    }

  @Test
  fun scopeCoversTheModule() {
    assertTrue(
      commonFiles.any { it.isIn("ui") },
      "No common source files found in ui/",
    )
  }

  @Test
  fun doesNotImportDataLayer() {
    commonFiles.assertFalse(additionalMessage = "Only shared/app may import data.* packages") {
      it.importsAny(listOf("org.openforis.collectone.v2.app.data."))
    }
  }

  @Test
  fun viewsDoNotImportClients() {
    commonFiles
      .filterNot { it.isIn("ui.viewmodel") }
      .assertFalse(additionalMessage = "Views reach past the ViewModel into client packages") {
        it.importsAny(listOf("org.openforis.collectone.v2.app.client."))
      }
  }

  @Test
  fun doesNotImportWebOrHost() {
    commonFiles.assertFalse(
      additionalMessage = "Workbench code never imports the web console or the developer tools."
    ) {
      it.importsAny(
        listOf(
          "org.openforis.collectone.v2.web.",
          "org.openforis.collectone.v2.devtools.",
        )
      )
    }
  }
}
