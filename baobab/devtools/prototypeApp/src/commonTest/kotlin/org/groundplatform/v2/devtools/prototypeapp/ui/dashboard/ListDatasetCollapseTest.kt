/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState

/** Tests collapsing entity datasets in the web dashboard's left-hand panel list. */
class ListDatasetCollapseTest {

  @Test
  fun datasets_startExpanded() {
    val state = PrototypeAppState()

    assertTrue(state.collapsedListDatasetIds.isEmpty())
    assertFalse(state.isListDatasetCollapsed("plots"))
  }

  @Test
  fun toggleListDatasetCollapsed_collapsesAndExpandsOnlyThatDataset() {
    val state = PrototypeAppState()

    state.toggleListDatasetCollapsed("plots")
    assertTrue(state.isListDatasetCollapsed("plots"))
    assertFalse(state.isListDatasetCollapsed("trees"))
    assertEquals(setOf("plots"), state.collapsedListDatasetIds)

    state.toggleListDatasetCollapsed("trees")
    assertEquals(setOf("plots", "trees"), state.collapsedListDatasetIds)

    state.toggleListDatasetCollapsed("plots")
    assertFalse(state.isListDatasetCollapsed("plots"))
    assertTrue(state.isListDatasetCollapsed("trees"))
    assertEquals(setOf("trees"), state.collapsedListDatasetIds)
  }

  @Test
  fun activeSearchQuery_showsCollapsedDatasetsExpandedUntilCleared() {
    val state = PrototypeAppState()
    state.toggleListDatasetCollapsed("plots")

    state.updateListSearchQuery("oak")
    assertFalse(state.isListDatasetCollapsed("plots"))
    assertEquals(setOf("plots"), state.collapsedListDatasetIds)

    state.clearListSearchQuery()
    assertTrue(state.isListDatasetCollapsed("plots"))
  }
}
