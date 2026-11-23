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
package org.openforis.collectone.v2.web.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.searchableList

/** Tests collapsing entity datasets in the web dashboard's left-hand panel list. */
class ListDatasetCollapseTest {

  @Test
  fun datasets_startExpanded() {
    val state = WebAppState(dataHolder = sampleDataHolder())

    assertTrue(state.viewModels.dashboard.uiState.value.collapsedListDatasetIds.isEmpty())
    assertFalse("plots" in state.searchableList.collapsedDatasetIds)
  }

  @Test
  fun toggleListDatasetCollapsed_collapsesAndExpandsOnlyThatDataset() {
    val state = WebAppState(dataHolder = sampleDataHolder())

    state.searchableListActions.toggleListDatasetCollapsed("plots")
    assertTrue("plots" in state.searchableList.collapsedDatasetIds)
    assertFalse("trees" in state.searchableList.collapsedDatasetIds)
    assertEquals(setOf("plots"), state.viewModels.dashboard.uiState.value.collapsedListDatasetIds)

    state.searchableListActions.toggleListDatasetCollapsed("trees")
    assertEquals(
      setOf("plots", "trees"),
      state.viewModels.dashboard.uiState.value.collapsedListDatasetIds,
    )

    state.searchableListActions.toggleListDatasetCollapsed("plots")
    assertFalse("plots" in state.searchableList.collapsedDatasetIds)
    assertTrue("trees" in state.searchableList.collapsedDatasetIds)
    assertEquals(setOf("trees"), state.viewModels.dashboard.uiState.value.collapsedListDatasetIds)
  }

  @Test
  fun activeSearchQuery_showsCollapsedDatasetsExpandedUntilCleared() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.searchableListActions.toggleListDatasetCollapsed("plots")

    state.searchableListActions.updateListSearchQuery("oak")
    assertFalse("plots" in state.searchableList.collapsedDatasetIds)
    assertEquals(setOf("plots"), state.viewModels.dashboard.uiState.value.collapsedListDatasetIds)

    state.searchableListActions.clearListSearchQuery()
    assertTrue("plots" in state.searchableList.collapsedDatasetIds)
  }
}
