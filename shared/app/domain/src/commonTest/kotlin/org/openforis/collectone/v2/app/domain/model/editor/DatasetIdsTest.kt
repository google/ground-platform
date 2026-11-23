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
package org.openforis.collectone.v2.app.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class DatasetIdsTest {
  @Test
  fun latinName_becomesItsSlug() {
    assertEquals("survey_sites", DatasetIds.forName("Survey sites", takenIds = emptyList()))
  }

  @Test
  fun takenSlug_getsANumberedSuffix() {
    assertEquals("plots_2", DatasetIds.forName("Plots", takenIds = listOf("plots")))
  }

  @Test
  fun khmerName_getsTheFallbackId() {
    assertEquals("dataset_1", DatasetIds.forName("ស្រទាប់ផែនទីថ្មី", takenIds = listOf("plots")))
  }

  @Test
  fun thaiName_getsTheFallbackId() {
    assertEquals("dataset_1", DatasetIds.forName("ชั้นแผนที่ใหม่", takenIds = emptyList()))
  }

  @Test
  fun fallbackId_isUniqueWithinTheSurvey() {
    assertEquals(
      "dataset_3",
      DatasetIds.forName("ตารางข้อมูลใหม่", takenIds = listOf("dataset_1", "dataset_2", "plots")),
    )
  }

  @Test
  fun fallbackId_takesTheFirstFreeNumber() {
    assertEquals(
      "dataset_2",
      DatasetIds.forName("ตาราง", takenIds = listOf("dataset_1", "dataset_3")),
    )
  }

  @Test
  fun nameInNonLatinDigitsAndPunctuation_getsTheFallbackId() {
    assertEquals("dataset_1", DatasetIds.forName(" ១២ — ! ", takenIds = emptyList()))
  }

  @Test
  fun mixedScriptName_keepsItsLatinPart() {
    assertEquals("plots_2026", DatasetIds.forName("Plots ចំណុច 2026", takenIds = emptyList()))
  }
}
