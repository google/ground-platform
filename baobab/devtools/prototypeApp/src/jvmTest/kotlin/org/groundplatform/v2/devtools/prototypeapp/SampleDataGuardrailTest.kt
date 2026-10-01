/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the local data store as the single source of truth: only the seeder package may read the
 * hardcoded sample datasets. Everything else must read sample data from the store.
 */
class SampleDataGuardrailTest {
  @Test
  fun onlySeedPackageReadsSampleDatasets() {
    val mainRoot = File("src/commonMain/kotlin")
    assertTrue(mainRoot.isDirectory, "Run from the prototypeApp project directory")
    val seedDir = "/data/seed/"
    val sampleData = Regex("""\b(PrototypeFake\w+Data|SurveyEditorSamples|FormEditorSamples)\b""")
    val offenders =
      mainRoot
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .filterNot { it.invariantSeparatorsPath.contains(seedDir) }
        .filter { sampleData.containsMatchIn(it.readText()) }
        .map { it.invariantSeparatorsPath }
        .toList()
    assertTrue(offenders.isEmpty(), "Sample datasets read outside data/seed: $offenders")
  }
}
