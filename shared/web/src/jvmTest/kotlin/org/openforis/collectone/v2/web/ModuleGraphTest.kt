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
package org.openforis.collectone.v2.web

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The module graph of `docs/technical/client/modules.md` for the web console, read from the build
 * scripts of the modules under `shared/web`: `:shared:web:domain` builds only on the shared domain
 * and doesn't apply Compose, `:shared:web:resources` builds on nothing, and each feature module
 * builds only on the modules below it, never on the console shell (`:shared:web`). The test
 * fixtures (`:shared:web:testing`, `:shared:app:testing`, `:shared:app:ui-testing`) are test-only.
 */
class ModuleGraphTest {
  private val projectReference = Regex("""projects\.([A-Za-z.]+)""")
  private val sourceSetBlock = Regex("""val (\w+) by getting""")

  /**
   * The project dependencies the build script of [module] declares, e.g. `shared.app.domain`, by
   * the source set (`commonMain`, `commonTest`, …) whose block declares them.
   */
  private fun projectDependenciesBySourceSet(module: String): Map<String, Set<String>> {
    val buildScript = File("$module/build.gradle.kts")
    assertTrue(buildScript.isFile, "Run from the shared/web project directory ($module)")
    var sourceSet = ""
    val dependencies = mutableMapOf<String, MutableSet<String>>()
    buildScript
      .readLines()
      .filterNot { it.trim().startsWith("//") }
      .forEach { line ->
        sourceSetBlock.find(line)?.let { sourceSet = it.groupValues[1] }
        projectReference.findAll(line).forEach {
          dependencies.getOrPut(sourceSet) { mutableSetOf() } += it.groupValues[1]
        }
      }
    return dependencies
  }

  /** Every project dependency the build script of [module] declares. */
  private fun projectDependencies(module: String): Set<String> =
    projectDependenciesBySourceSet(module).values.flatten().toSet()

  /** The project dependencies of [module]'s production source sets (not `*Test`). */
  private fun mainDependencies(module: String): Set<String> =
    projectDependenciesBySourceSet(module)
      .filterKeys { !it.endsWith("Test") }
      .values
      .flatten()
      .toSet()

  @Test
  fun domainBuildsOnlyOnTheSharedDomain() {
    // :shared:app:testing: the shared fixtures, for the tests only.
    assertEquals(setOf("shared.app.domain", "shared.app.testing"), projectDependencies("domain"))
  }

  @Test
  fun domainDoesNotApplyCompose() {
    assertFalse("compose" in File("domain/build.gradle.kts").readText().substringAfter("plugins"))
  }

  @Test
  fun resourcesBuildOnNothing() {
    assertEquals(emptySet(), projectDependencies("resources"))
  }

  @Test
  fun uiCommonBuildsOnTheDomainAndStringsButNotOnData() {
    assertEquals(
      setOf(
        "shared.app.uiCommon",
        "shared.web.domain",
        "shared.web.resources",
        "shared.app.testing",
      ),
      projectDependencies("ui-common"),
    )
  }

  /**
   * What each feature module builds on. Test-only dependencies are listed too: the shared test
   * fixtures (`shared.app.testing`, `shared.app.uiTesting`, and `shared.web.testing`, which builds
   * on the Survey editor).
   */
  @Test
  fun featureModulesBuildOnlyOnTheModulesBelowThem() {
    val expected =
      mapOf(
        "timeseries" to setOf("shared.web.uiCommon", "shared.app.data", "shared.app.testing"),
        "impact" to
          setOf(
            "shared.web.uiCommon",
            "shared.app.data",
            "shared.app.datacollection",
            "shared.app.testing",
            "shared.app.uiTesting",
          ),
        "surveys" to
          setOf(
            "shared.web.uiCommon",
            "shared.app.dashboard",
            "shared.app.map",
            "shared.app.testing",
            "shared.web.testing",
          ),
        "organization" to
          setOf("shared.web.surveys", "shared.web.formeditor", "shared.app.testing"),
        "formeditor" to setOf("shared.web.uiCommon", "shared.app.testing", "shared.web.testing"),
        "surveyeditor" to
          setOf(
            "shared.web.formeditor",
            "shared.app.map",
            "shared.app.testing",
            "shared.web.testing",
            "shared.app.uiTesting",
          ),
        "wizard" to setOf("shared.web.surveyeditor", "shared.app.testing"),
      )
    expected.forEach { (module, dependencies) ->
      assertEquals(dependencies, projectDependencies(module), "Dependencies of :shared:web:$module")
    }
  }

  /** No production code builds on test fixtures; only the fixture modules themselves do. */
  @Test
  fun testFixturesAreTestOnly() {
    val fixtures = setOf("shared.web.testing", "shared.app.testing", "shared.app.uiTesting")
    listOf(
        ".",
        "domain",
        "resources",
        "ui-common",
        "timeseries",
        "impact",
        "surveys",
        "organization",
        "formeditor",
        "surveyeditor",
        "wizard",
      )
      .forEach { module ->
        assertEquals(
          emptySet(),
          mainDependencies(module) intersect fixtures,
          ":shared:web:$module builds production code on test fixtures",
        )
      }
  }

  @Test
  fun onlyTheHostsBuildOnTheShell() {
    listOf(
        "domain",
        "resources",
        "ui-common",
        "timeseries",
        "impact",
        "surveys",
        "organization",
        "formeditor",
        "surveyeditor",
        "wizard",
        "testing",
      )
      .forEach { module ->
        assertFalse(
          "shared.web" in projectDependencies(module),
          ":shared:web:$module builds on the console shell",
        )
      }
  }
}
