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
import org.openforis.collectone.v2.core.ids.RecordIds

/** Which dataset IDs the Survey editor's Advanced section shows ([showsDatasetIdField]). */
class DatasetIdFieldTest {
  private val editor = surveyEditorViewModel()
  private val dataset = editor.ui.draft.datasets.first()

  @Test
  fun readableId_isShown() {
    assertTrue(showsDatasetIdField(dataset.copy(id = "farm_plots")))
  }

  @Test
  fun generatedUuid_isNotShown() {
    assertFalse(showsDatasetIdField(dataset.copy(id = RecordIds.newId())))
    assertFalse(showsDatasetIdField(dataset.copy(id = "3F2504E0-4F89-41D3-9A0C-0305E82C3301")))
  }
}
