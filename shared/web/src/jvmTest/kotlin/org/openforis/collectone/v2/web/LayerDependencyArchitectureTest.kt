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
package org.openforis.collectone.v2.web

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The layer rules of `docs/technical/client/architecture.md` for the web console, checked with
 * Konsist over the common source sets of every web module (`shared/web` and the modules under it,
 * whose module graph [ModuleGraphTest] checks):
 * - Nothing here imports `app.data` (only `shared/app`'s own `data/` and `di/` may).
 * - `domain/` (the web-only domain models and use cases) imports no client, di, or UI package, no
 *   `shared/ui` form widgets (`core.forms.ui`), and no Compose (including Compose resources and the
 *   generated `Res` classes).
 * - Views (everything outside `client/`, `domain/`, `di/`, and `ui/viewmodel/`, except
 *   `*ViewModel.kt` files and the `*Handler.kt` files of their actions) never import `app.data` or
 *   `app.client`.
 * - Views never import an `*Importer`: imports run through a ViewModel.
 * - Web console code never imports the mobile app, the workbench, or the developer tools.
 * - Each ViewModel and UI state lives in its feature's package (`ui.<feature>`): there are no
 *   shared `ui.viewmodel` or `ui.state` packages.
 */
class LayerDependencyArchitectureTest {
  private val root = "org.openforis.collectone.v2.web"

  /** The web modules: the console shell and the modules under it. */
  private val modules =
    listOf(
      "shared/web",
      "shared/web/domain",
      "shared/web/ui-common",
      "shared/web/timeseries",
      "shared/web/impact",
      "shared/web/surveys",
      "shared/web/organization",
      "shared/web/formeditor",
      "shared/web/surveyeditor",
      "shared/web/wizard",
    )

  /** Kotlin files of the common source sets of the web modules. */
  private val commonFiles: List<KoFileDeclaration> = modules.flatMap { module ->
    Konsist.scopeFromDirectory("$module/src/commonMain/kotlin").files
  }

  private fun KoFileDeclaration.isIn(layer: String): Boolean =
    packagee?.name?.let { it == "$root.$layer" || it.startsWith("$root.$layer.") } == true

  private fun KoFileDeclaration.importsAny(prefixes: List<String>): Boolean =
    imports.any { import ->
      prefixes.any { import.name.startsWith(it) }
    }

  /**
   * A view: outside the non-view layers and neither a ViewModel nor one of the action handlers a
   * ViewModel delegates to (`*Handler.kt`, next to it in its feature's package).
   */
  private fun KoFileDeclaration.isView(): Boolean =
    listOf("client", "domain", "di", "ui.viewmodel").none { isIn(it) } &&
      !name.endsWith("ViewModel") &&
      !name.endsWith("Handler")

  @Test
  fun scopeCoversEveryLayer() {
    listOf("client", "domain", "di", "ui").forEach { layer ->
      assertTrue(commonFiles.any { it.isIn(layer) }, "No production files found in $layer/")
    }
  }

  @Test
  fun doesNotImportDataLayer() {
    commonFiles.assertFalse(additionalMessage = "Only shared/app may import data.* packages") {
      it.importsAny(listOf("org.openforis.collectone.v2.app.data."))
    }
  }

  @Test
  fun domainImportsOnlyDomainAndSharedCore() {
    commonFiles
      .filter { it.isIn("domain") }
      .assertFalse(additionalMessage = "domain/ depends on outer layers or presentation") {
        it.importsAny(
          listOf(
            "org.openforis.collectone.v2.app.client.",
            "org.openforis.collectone.v2.app.di.",
            "org.openforis.collectone.v2.app.ui.",
            "org.openforis.collectone.v2.app.resources.",
            "$root.client.",
            "$root.di.",
            "$root.ui.",
            "$root.resources.",
            "androidx.compose.",
            "org.jetbrains.compose.",
            "org.openforis.collectone.v2.core.forms.ui.",
          )
        )
      }
  }

  @Test
  fun viewsDoNotImportClients() {
    commonFiles
      .filter { it.isView() }
      .assertFalse(additionalMessage = "Views reach past the ViewModel into client packages") {
        it.importsAny(listOf("org.openforis.collectone.v2.app.client."))
      }
  }

  @Test
  fun viewsDoNotImportImporters() {
    commonFiles
      .filter { it.isIn("ui") && it.isView() }
      .assertFalse(
        additionalMessage = "Views run imports directly instead of through their ViewModel"
      ) {
        it.imports.any { import -> import.name.substringAfterLast('.').endsWith("Importer") }
      }
  }

  @Test
  fun doesNotImportMobileWorkbenchOrHost() {
    commonFiles.assertFalse(
      additionalMessage = "Web console code depends on mobile, the workbench, or the host"
    ) {
      it.importsAny(
        listOf(
          "org.openforis.collectone.v2.mobile.",
          "org.openforis.collectone.v2.workbench.",
          "org.openforis.collectone.v2.devtools.",
        )
      )
    }
  }

  @Test
  fun viewModelsAndUiStatesLiveInTheirFeaturePackages() {
    commonFiles
      .filter { it.name.endsWith("ViewModel") || it.name.endsWith("UiState") }
      .assertFalse(
        additionalMessage = "Put a ViewModel and its UI state in its feature's package"
      ) {
        val feature = it.packagee?.name?.removePrefix("$root.ui.")
        feature == null ||
          feature == it.packagee?.name ||
          feature.contains('.') ||
          feature in setOf("viewmodel", "state")
      }
  }
}
