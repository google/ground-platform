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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every string resource exists in all 9 locales: each `values/` file of every UI build's Compose
 * resources has a counterpart in each `values-<locale>/` directory with exactly the same `<string>`
 * and `<plurals>` names, so no locale silently falls back to English (and no locale keeps keys that
 * English dropped).
 */
class LocaleKeyParityTest {
  private val repoRoot = File("../..")
  private val builds =
    listOf("shared/ui", "shared/app/resources", "shared/web/resources", "shared/mobile")
  private val locales = listOf("es", "fr", "km", "lo", "pt", "sw", "th", "vi")
  private val resourceName = Regex("""<(?:string|plurals)\s+name="([^"]+)"""")

  private fun resourcesDir(build: String) = File(repoRoot, "$build/src/commonMain/composeResources")

  private fun keysOf(file: File): Set<String> =
    resourceName.findAll(file.readText()).map { it.groupValues[1] }.toSet()

  @Test
  fun everyBuildHasEveryLocale() {
    builds.forEach { build ->
      val dir = resourcesDir(build)
      assertTrue(File(dir, "values").isDirectory, "No values/ in $dir (run from shared/app)")
      locales.forEach { locale ->
        assertTrue(File(dir, "values-$locale").isDirectory, "$build has no values-$locale/")
      }
    }
  }

  @Test
  fun everyStringKeyExistsInEveryLocale() {
    val problems = builds.flatMap { build ->
      val dir = resourcesDir(build)
      val englishFiles =
        File(dir, "values").listFiles { file -> file.extension == "xml" }.orEmpty().sorted()
      englishFiles.flatMap { english ->
        val englishKeys = keysOf(english)
        locales.flatMap { locale ->
          val translated = File(dir, "values-$locale/${english.name}")
          if (!translated.isFile) {
            listOf("$build values-$locale/${english.name} is missing")
          } else {
            val keys = keysOf(translated)
            (englishKeys - keys).map { "$build values-$locale/${english.name} lacks $it" } +
              (keys - englishKeys).map { "$build values-$locale/${english.name} has extra $it" }
          }
        }
      }
    }
    assertEquals(emptyList(), problems, problems.joinToString("\n"))
  }
}
