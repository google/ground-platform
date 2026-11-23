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
package org.openforis.collectone.v2.web.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.ui.dashboard.DashboardUiState.Companion.DEFAULT_SIDE_PANEL_WIDTH_DP
import org.openforis.collectone.v2.app.ui.dashboard.DashboardUiState.Companion.MAX_SIDE_PANEL_WIDTH_DP
import org.openforis.collectone.v2.app.ui.dashboard.DashboardUiState.Companion.MIN_SIDE_PANEL_WIDTH_DP
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.formeditor.FormEditorUiState
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorUiState
import org.openforis.collectone.v2.web.ui.surveyeditor.formEditorViewModel
import org.openforis.collectone.v2.web.ui.surveyeditor.surveyEditorViewModel
import org.openforis.collectone.v2.web.ui.surveyeditor.ui

/** Width of the web dashboard's, Survey editor's, and Form editor's resizable side panels. */
class SidePanelWidthTest {

  @Test
  fun sidePanelWidth_startsAtDefault() {
    assertEquals(
      DEFAULT_SIDE_PANEL_WIDTH_DP,
      WebAppState(dataHolder = sampleDataHolder())
        .viewModels
        .dashboard
        .uiState
        .value
        .sidePanelWidthDp,
    )
  }

  @Test
  fun updateSidePanelWidth_acceptsWidthWithinBounds() {
    val state = WebAppState(dataHolder = sampleDataHolder())

    state.viewModels.dashboard.updateSidePanelWidth(420f)

    assertEquals(420f, state.viewModels.dashboard.uiState.value.sidePanelWidthDp)
  }

  @Test
  fun updateSidePanelWidth_clampsToBounds() {
    val state = WebAppState(dataHolder = sampleDataHolder())

    state.viewModels.dashboard.updateSidePanelWidth(MIN_SIDE_PANEL_WIDTH_DP - 100f)
    assertEquals(MIN_SIDE_PANEL_WIDTH_DP, state.viewModels.dashboard.uiState.value.sidePanelWidthDp)

    state.viewModels.dashboard.updateSidePanelWidth(MAX_SIDE_PANEL_WIDTH_DP + 100f)
    assertEquals(MAX_SIDE_PANEL_WIDTH_DP, state.viewModels.dashboard.uiState.value.sidePanelWidthDp)
  }

  @Test
  fun updateSidePanelWidth_ignoresNonFiniteValues() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.viewModels.dashboard.updateSidePanelWidth(400f)

    state.viewModels.dashboard.updateSidePanelWidth(Float.NaN)
    state.viewModels.dashboard.updateSidePanelWidth(Float.POSITIVE_INFINITY)

    assertEquals(400f, state.viewModels.dashboard.uiState.value.sidePanelWidthDp)
  }

  @Test
  fun sidePanelWidth_isKeptWhileCollapsed() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.viewModels.dashboard.updateSidePanelWidth(480f)

    state.viewModels.dashboard.collapseSidePanel()
    assertFalse(state.viewModels.dashboard.uiState.value.isSidePanelExpanded)
    state.viewModels.dashboard.expandSidePanel()

    assertEquals(480f, state.viewModels.dashboard.uiState.value.sidePanelWidthDp)
  }

  @Test
  fun reset_restoresDefaultWidth() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.viewModels.dashboard.updateSidePanelWidth(480f)

    state.reset()

    assertEquals(
      DEFAULT_SIDE_PANEL_WIDTH_DP,
      state.viewModels.dashboard.uiState.value.sidePanelWidthDp,
    )
  }

  @Test
  fun surveyEditorSidePanelWidth_startsAtDefault() {
    assertEquals(
      SurveyEditorUiState.DEFAULT_SIDE_PANEL_WIDTH_DP,
      surveyEditorViewModel().ui.sidePanelWidthDp,
    )
  }

  @Test
  fun surveyEditorUpdateSidePanelWidth_acceptsAndClampsWidth() {
    val state = surveyEditorViewModel()

    state.updateSidePanelWidth(380f)
    assertEquals(380f, state.ui.sidePanelWidthDp)

    state.updateSidePanelWidth(SurveyEditorUiState.MIN_SIDE_PANEL_WIDTH_DP - 100f)
    assertEquals(SurveyEditorUiState.MIN_SIDE_PANEL_WIDTH_DP, state.ui.sidePanelWidthDp)

    state.updateSidePanelWidth(SurveyEditorUiState.MAX_SIDE_PANEL_WIDTH_DP + 100f)
    assertEquals(SurveyEditorUiState.MAX_SIDE_PANEL_WIDTH_DP, state.ui.sidePanelWidthDp)

    state.updateSidePanelWidth(Float.NaN)
    state.updateSidePanelWidth(Float.NEGATIVE_INFINITY)
    assertEquals(SurveyEditorUiState.MAX_SIDE_PANEL_WIDTH_DP, state.ui.sidePanelWidthDp)
    assertFalse(state.ui.hasUnpublishedChanges)
  }

  @Test
  fun formEditorSidePanelWidth_startsAtDefault() {
    assertEquals(
      FormEditorUiState.DEFAULT_SIDE_PANEL_WIDTH_DP,
      formEditorViewModel().ui.sidePanelWidthDp,
    )
  }

  @Test
  fun formEditorUpdateSidePanelWidth_acceptsAndClampsWidth() {
    val state = formEditorViewModel()

    state.updateSidePanelWidth(480f)
    assertEquals(480f, state.ui.sidePanelWidthDp)

    state.updateSidePanelWidth(FormEditorUiState.MIN_SIDE_PANEL_WIDTH_DP - 100f)
    assertEquals(FormEditorUiState.MIN_SIDE_PANEL_WIDTH_DP, state.ui.sidePanelWidthDp)

    state.updateSidePanelWidth(FormEditorUiState.MAX_SIDE_PANEL_WIDTH_DP + 100f)
    assertEquals(FormEditorUiState.MAX_SIDE_PANEL_WIDTH_DP, state.ui.sidePanelWidthDp)

    state.updateSidePanelWidth(Float.NaN)
    state.updateSidePanelWidth(Float.NEGATIVE_INFINITY)
    assertEquals(FormEditorUiState.MAX_SIDE_PANEL_WIDTH_DP, state.ui.sidePanelWidthDp)
  }
}
