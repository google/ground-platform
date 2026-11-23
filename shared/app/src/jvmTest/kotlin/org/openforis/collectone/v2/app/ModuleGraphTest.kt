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

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The module graph of `docs/technical/client/modules.md`: which modules `:shared:app:domain`,
 * `:shared:app:data`, and the presentation modules split out of `:shared:app` build on, read from
 * their build scripts.
 *
 * A dependency that points back up (`:shared:app:domain` on `:shared:app:data` or `:shared:app`) is
 * a cycle, which Gradle rejects. This test catches the ones that aren't, such as
 * `:shared:app:domain` on `:shared:ui`, and keeps Compose out of both modules. The test fixtures
 * (`:shared:app:testing`, `:shared:app:ui-testing`) are test-only.
 */
class ModuleGraphTest {
  private val projectReference = Regex("""projects\.([A-Za-z.]+)""")
  private val sourceSetBlock = Regex("""val (\w+) by getting""")

  /**
   * The project dependencies a build script declares, e.g. `shared.core`, without comments, by the
   * source set (`commonMain`, `commonTest`, …) whose block declares them.
   */
  private fun projectDependenciesBySourceSet(buildScript: File): Map<String, Set<String>> {
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

  /** Every project dependency a build script declares. */
  private fun projectDependencies(buildScript: File): Set<String> =
    projectDependenciesBySourceSet(buildScript).values.flatten().toSet()

  /** The project dependencies of a build script's production source sets (not `*Test`). */
  private fun mainDependencies(buildScript: File): Set<String> =
    projectDependenciesBySourceSet(buildScript)
      .filterKeys { !it.endsWith("Test") }
      .values
      .flatten()
      .toSet()

  private fun buildScript(module: String): File =
    File(module, "build.gradle.kts").also {
      assertTrue(it.isFile, "Run from the shared/app project directory: $it")
    }

  @Test
  fun domainBuildsOnlyOnCoreAndGeometry() {
    assertEquals(
      setOf("shared.core", "shared.map.geometry", TEST_FIXTURES),
      projectDependencies(buildScript("domain")),
    )
  }

  @Test
  fun dataBuildsOnlyOnDomain() {
    assertEquals(
      setOf("shared.app.domain", TEST_FIXTURES),
      projectDependencies(buildScript("data")),
    )
  }

  @Test
  fun domainAndDataDoNotApplyCompose() {
    listOf("domain", "data").forEach { module ->
      val script = buildScript(module).readText()
      assertFalse("collectone.kmp.compose" in script, "$module applies Compose")
      assertFalse("compose." in script, "$module depends on Compose")
    }
  }

  @Test
  fun resourcesBuildOnNoOtherModule() {
    assertEquals(emptySet(), projectDependencies(buildScript("resources")))
  }

  /**
   * `:shared:app:ui-common` builds on the domain, the strings, and the shared UI, not on the data
   * layer (its profile images come through the domain's `ProfileImageLoader`) or the features.
   */
  @Test
  fun uiCommonBuildsOnlyOnDomainResourcesAndUi() {
    assertEquals(
      setOf("shared.app.domain", "shared.app.resources", "shared.ui"),
      projectDependencies(buildScript("ui-common")),
    )
  }

  /** The survey map feature builds on the shared presentation code, not on other features. */
  @Test
  fun mapBuildsOnlyOnUiCommonAndTheMapRenderer() {
    assertEquals(
      setOf("shared.app.uiCommon", "shared.map", TEST_FIXTURES),
      projectDependencies(buildScript("map")),
    )
  }

  /** Data collection builds on the survey map feature and the data layer's PDF export client. */
  @Test
  fun dataCollectionBuildsOnlyOnMapUiCommonAndData() {
    assertEquals(
      setOf("shared.app.map", "shared.app.uiCommon", "shared.app.data", APP_TEST_FIXTURES),
      projectDependencies(buildScript("datacollection")),
    )
  }

  /** The settings feature builds on the shared presentation code, not on data or other features. */
  @Test
  fun settingsBuildsOnlyOnUiCommon() {
    assertEquals(
      setOf("shared.app.uiCommon", TEST_FIXTURES),
      projectDependencies(buildScript("settings")),
    )
  }

  /**
   * The list search feature builds on the shared presentation code and the data layer (whether a
   * live places geocoder is configured), not on other features.
   */
  @Test
  fun listSearchBuildsOnlyOnUiCommonAndData() {
    assertEquals(
      setOf("shared.app.uiCommon", "shared.app.data", APP_TEST_FIXTURES),
      projectDependencies(buildScript("listsearch")),
    )
  }

  /**
   * The onboarding feature builds on the shared presentation code and, for its Koin module's
   * initial sync state, the data layer; not on other features.
   */
  @Test
  fun onboardingBuildsOnlyOnUiCommonAndData() {
    assertEquals(
      setOf("shared.app.uiCommon", "shared.app.data", TEST_FIXTURES),
      projectDependencies(buildScript("onboarding")),
    )
  }

  /**
   * The dashboard feature builds on the shared presentation code, not on data or other features.
   */
  @Test
  fun dashboardBuildsOnlyOnUiCommon() {
    assertEquals(
      setOf("shared.app.uiCommon", TEST_FIXTURES),
      projectDependencies(buildScript("dashboard")),
    )
  }

  /** The data-level fixtures, which the domain and data tests use, don't bring the presentation. */
  @Test
  fun dataTestFixturesBuildOnlyOnData() {
    assertEquals(
      setOf("shared.app.data", "shared.ui.testing"),
      projectDependencies(buildScript("testing")),
    )
  }

  /** No production code builds on test fixtures; only the fixture modules themselves do. */
  @Test
  fun testFixturesAreTestOnly() {
    listOf(
        ".",
        "domain",
        "data",
        "resources",
        "ui-common",
        "map",
        "datacollection",
        "dashboard",
        "onboarding",
        "listsearch",
        "settings",
      )
      .forEach { module ->
        assertEquals(
          emptySet(),
          mainDependencies(buildScript(module)) intersect setOf(TEST_FIXTURES, APP_TEST_FIXTURES),
          ":shared:app:$module builds production code on test fixtures",
        )
      }
  }

  @Test
  fun appBuildsOnData() {
    assertTrue("shared.app.data" in projectDependencies(buildScript(".")))
  }

  private companion object {
    /** `:shared:app:testing`, the data-level fixtures the tests of every module may use. */
    const val TEST_FIXTURES = "shared.app.testing"

    /**
     * `:shared:app:ui-testing`, the app-level fixtures (the assembled session) for presentation.
     */
    const val APP_TEST_FIXTURES = "shared.app.uiTesting"
  }
}
