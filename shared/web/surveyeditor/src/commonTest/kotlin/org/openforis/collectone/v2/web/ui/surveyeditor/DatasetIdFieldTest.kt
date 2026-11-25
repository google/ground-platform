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
package org.openforis.collectone.v2.web.ui.surveyeditor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When the Survey editor's Advanced section shows a dataset's ID ([showsDatasetIdField]). */
class DatasetIdFieldTest {
  private val editor = surveyEditorViewModel()
  private val dataset = editor.ui.draft.datasets.first()

  @Test
  fun validUniqueId_isNotShown() {
    assertFalse(showsDatasetIdField(dataset.copy(id = "farm_plots"), listOf("farm_plots", "trees")))
    assertFalse(
      showsDatasetIdField(
        dataset.copy(id = "c9bf9e57-1685-4c89-bafb-ff5af830be8a"),
        listOf("c9bf9e57-1685-4c89-bafb-ff5af830be8a"),
      )
    )
  }

  @Test
  fun invalidId_isShownSoItCanBeFixed() {
    assertTrue(showsDatasetIdField(dataset.copy(id = "farm plots"), listOf("farm plots")))
    assertTrue(showsDatasetIdField(dataset.copy(id = ""), listOf("")))
  }

  @Test
  fun duplicateId_isShownSoItCanBeFixed() {
    assertTrue(
      showsDatasetIdField(dataset.copy(id = "farm_plots"), listOf("farm_plots", "farm_plots"))
    )
  }
}
