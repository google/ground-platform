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

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The layer rules of `docs/technical/client/architecture.md` that the module graph doesn't already
 * enforce, checked with Konsist over the common source sets of `shared/app` and the modules it is
 * split into (see `docs/technical/client/modules.md`).
 *
 * The compiler keeps `:shared:app:domain` from importing `data/`, `client/`, `di/`, `ui/`, or the
 * `Res` strings, and `:shared:app:data` from importing `ui/` or the strings: they aren't on those
 * modules' classpaths. [ModuleGraphTest] keeps it that way. What is left to check here:
 * - `domain/` and `data/` (and `client/`) import no Compose and no `shared/ui` form widgets
 *   (`core.forms.ui`), even where a dependency brings them along.
 * - `data/` imports nothing from `di/`.
 * - In the presentation modules, only `di/` imports `data/`; views (everything outside `client/`,
 *   `di/`, and `*ViewModel.kt` files) import neither `data/` nor `client/`.
 * - Only the XForms import, export, and generation boundaries use the XML serializers.
 */
class LayerDependencyArchitectureTest {
  private val app = "org.openforis.collectone.v2.app"

  private fun commonFiles(module: String): List<KoFileDeclaration> =
    Konsist.scopeFromDirectory("$module/src/commonMain/kotlin").files

  /** Kotlin files of the common source set of each module. */
  private val presentationFiles = PRESENTATION_MODULES.flatMap(::commonFiles)
  private val dataFiles = commonFiles("shared/app/data")
  private val domainFiles = commonFiles("shared/app/domain")
  private val allFiles = presentationFiles + dataFiles + domainFiles

  private fun KoFileDeclaration.isIn(layer: String): Boolean =
    packagee?.name?.let { it == "$app.$layer" || it.startsWith("$app.$layer.") } == true

  private fun KoFileDeclaration.importsAny(prefixes: List<String>): Boolean =
    imports.any { import ->
      prefixes.any { import.name.startsWith(it) }
    }

  @Test
  fun scopeCoversEveryModuleAndLayer() {
    mapOf(
        presentationFiles to listOf("ui", "di"),
        dataFiles to listOf("data", "client", "di"),
        domainFiles to listOf("domain"),
      )
      .forEach { (files, layers) ->
        layers.forEach { layer ->
          assertTrue(files.any { it.isIn(layer) }, "No production files found in $layer/")
        }
      }
  }

  @Test
  fun domainAndDataImportNoCompose() {
    (domainFiles + dataFiles).assertFalse(
      additionalMessage = "domain/ or data/ depends on Compose or the shared/ui form widgets"
    ) {
      it.importsAny(
        listOf(
          "androidx.compose.",
          "org.jetbrains.compose.",
          "org.openforis.collectone.v2.core.forms.ui.",
        )
      )
    }
  }

  @Test
  fun dataLayerDoesNotImportDi() {
    dataFiles
      .filter { it.isIn("data") }
      .assertFalse(additionalMessage = "data/ depends on di") { it.importsAny(listOf("$app.di.")) }
  }

  @Test
  fun onlyDiImportsDataLayerInPresentationModule() {
    presentationFiles
      .filterNot { it.isIn("di") }
      .assertFalse(additionalMessage = "Only di/ may import data.* packages") {
        it.importsAny(listOf("$app.data."))
      }
  }

  @Test
  fun viewsDoNotImportDataSourcesOrClients() {
    presentationFiles
      .filterNot { file ->
        listOf("client", "di").any { file.isIn(it) } || file.name.endsWith("ViewModel")
      }
      .assertFalse(
        additionalMessage = "Views reach past the ViewModel into data or client packages"
      ) {
        it.importsAny(listOf("$app.data.", "$app.client."))
      }
  }

  @Test
  fun xmlSerializationIsRestrictedToImportExportAndGenerationBoundaries() {
    val boundaries = setOf("EditorXFormsGenerator", "FormPublishing", "FormImport")
    allFiles
      .filterNot { it.isIn("domain.model.editor") && it.name in boundaries }
      .assertFalse(
        additionalMessage =
          "XML serialization must only be used at the XForms import, export, and generation " +
            "boundaries"
      ) {
        it.importsAny(XML_SERIALIZERS)
      }
  }

  private companion object {
    /** The presentation modules: `:shared:app` and the modules split out of it. */
    val PRESENTATION_MODULES =
      listOf(
        "shared/app",
        "shared/app/ui-common",
        "shared/app/map",
        "shared/app/datacollection",
        "shared/app/settings",
        "shared/app/listsearch",
        "shared/app/onboarding",
        "shared/app/dashboard",
      )

    val XML_SERIALIZERS =
      listOf(
          "XFormsXmlSerializer",
          "FormDefXmlSerializer",
          "FormDefXmlDeserializer",
          "InstanceXmlSerializer",
        )
        .map { "org.openforis.collectone.v2.core.forms.serialization.$it" }
  }
}
