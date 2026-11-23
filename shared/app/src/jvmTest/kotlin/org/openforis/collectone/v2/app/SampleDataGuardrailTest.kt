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
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.seed.SampleSurveysData

/**
 * Guards the server as the single source of sample data: the sample datasets are test fixtures
 * (`shared/app/testing`), so production code must not special-case the sample forms by ID.
 *
 * The fixtures can't leak into production code by import (they aren't on its classpath), so this
 * checks for the sample forms' IDs as string literals in every main source set of this module and
 * the modules it is split into instead.
 */
class SampleDataGuardrailTest {
  /** The `src` directories of this module and of its split modules, relative to shared/app. */
  private val srcRoots = listOf("src", "domain/src", "data/src").map(::File)

  private val sampleFormIds: Set<String> =
    (SampleSurveysData.defaultForms() +
        SampleSurveysData.defaultSampleSurveys().flatMap {
          SampleSurveysData.formsForSurvey(it.id)
        })
      .map { it.id }
      .toSet()

  @Test
  fun mainCodeDoesNotHardcodeSampleFormIds() {
    srcRoots.forEach { assertTrue(it.isDirectory, "Run from the shared/app project directory") }
    assertTrue(sampleFormIds.size >= 2, "Expected sample forms in the fixtures: $sampleFormIds")
    val offenders =
      srcRoots
        .asSequence()
        .flatMap { srcRoot ->
          srcRoot
            .listFiles { dir -> dir.isDirectory && dir.name.endsWith("Main") }
            .orEmpty()
            .asSequence()
        }
        .flatMap { sourceSet -> sourceSet.walkTopDown() }
        .filter { it.isFile && it.extension == "kt" }
        .associate { file ->
          val text = file.readText()
          file.invariantSeparatorsPath to sampleFormIds.filter { "\"$it\"" in text }
        }
        .filterValues { it.isNotEmpty() }

    assertTrue(
      offenders.isEmpty(),
      "Main code hardcodes sample form IDs; read forms from the store instead:\n" +
        offenders.entries.joinToString("\n") { (path, ids) -> "$path: $ids" },
    )
  }
}
