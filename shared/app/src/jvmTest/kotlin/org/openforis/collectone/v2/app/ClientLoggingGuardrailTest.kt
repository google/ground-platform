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
package org.openforis.collectone.v2.app

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The client apps log only through Kermit (see `configureAppLogging` in `:shared:app:data`), which
 * filters by severity and removes email addresses and tokens. Production code of the client modules
 * doesn't write to standard output or the console directly: no `println`, `print`, `System.out` or
 * `System.err`, `printStackTrace()`, `console.log` (and its siblings, including in `@JsFun` code),
 * or `android.util.Log`.
 *
 * Scans every production source set (`src/<platform>Main/kotlin`) of the client modules; tests and
 * the server (which logs with SLF4J) are out of scope. Comment lines are skipped.
 */
class ClientLoggingGuardrailTest {
  private val clientModules =
    listOf(
      "shared/core",
      "shared/ui",
      "shared/map/geometry",
      "shared/map",
      "shared/app/domain",
      "shared/app/data",
      "shared/app/ui-common",
      "shared/app/map",
      "shared/app/datacollection",
      "shared/app/settings",
      "shared/app/listsearch",
      "shared/app/onboarding",
      "shared/app/dashboard",
      "shared/app",
      "shared/web/domain",
      "shared/web/ui-common",
      "shared/web/wizard",
      "shared/web/surveyeditor",
      "shared/web/formeditor",
      "shared/web/organization",
      "shared/web/surveys",
      "shared/web/impact",
      "shared/web/timeseries",
      "shared/web",
      "shared/mobile",
      "mobileWebApp/workbench",
      "mobileWebApp",
      "androidApp",
      "webApp",
    )

  private val directOutput =
    Regex(
      """\bprintln\(|\bprint\(|\bSystem\.(out|err)\b|\.printStackTrace\(|""" +
        """\bconsole\.(log|info|warn|error|debug|trace)\b|\bandroid\.util\.Log\b"""
    )

  private val productionSourceSet = Regex("""/src/[A-Za-z]+Main/kotlin/""")

  /** Production Kotlin files of [module]. */
  private fun productionFiles(module: String): List<KoFileDeclaration> =
    Konsist.scopeFromDirectory("$module/src").files.filter { file ->
      productionSourceSet.containsMatchIn(file.path.replace('\\', '/'))
    }

  private fun KoFileDeclaration.directOutputLines(): List<String> =
    text
      .lines()
      .withIndex()
      .filterNot { (_, line) -> line.trimStart().let { it.startsWith("//") || it.startsWith("*") } }
      .filter { (_, line) -> directOutput.containsMatchIn(line) }
      .map { (index, line) -> "$path:${index + 1}: ${line.trim()}" }

  @Test
  fun scopeCoversEveryClientModule() {
    clientModules.forEach { module ->
      assertTrue(productionFiles(module).isNotEmpty(), "No production files found in $module")
    }
  }

  @Test
  fun clientProductionCodeLogsOnlyThroughKermit() {
    val offenders = clientModules.flatMap { productionFiles(it) }.flatMap { it.directOutputLines() }

    assertEquals(
      emptyList(),
      offenders,
      "Log with Kermit (Logger.withTag(...)) instead of writing to the console",
    )
  }
}
