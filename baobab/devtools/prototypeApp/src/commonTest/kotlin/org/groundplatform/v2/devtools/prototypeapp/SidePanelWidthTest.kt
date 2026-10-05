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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState.Companion.DEFAULT_SIDE_PANEL_WIDTH_DP
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState.Companion.MAX_SIDE_PANEL_WIDTH_DP
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState.Companion.MIN_SIDE_PANEL_WIDTH_DP
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.SurveyEditorState

/** Width of the web dashboard's and Survey editor's resizable left-hand panels. */
class SidePanelWidthTest {

  @Test
  fun sidePanelWidth_startsAtDefault() {
    assertEquals(DEFAULT_SIDE_PANEL_WIDTH_DP, PrototypeAppState().sidePanelWidthDp)
  }

  @Test
  fun updateSidePanelWidth_acceptsWidthWithinBounds() {
    val state = PrototypeAppState()

    state.updateSidePanelWidth(420f)

    assertEquals(420f, state.sidePanelWidthDp)
  }

  @Test
  fun updateSidePanelWidth_clampsToBounds() {
    val state = PrototypeAppState()

    state.updateSidePanelWidth(MIN_SIDE_PANEL_WIDTH_DP - 100f)
    assertEquals(MIN_SIDE_PANEL_WIDTH_DP, state.sidePanelWidthDp)

    state.updateSidePanelWidth(MAX_SIDE_PANEL_WIDTH_DP + 100f)
    assertEquals(MAX_SIDE_PANEL_WIDTH_DP, state.sidePanelWidthDp)
  }

  @Test
  fun updateSidePanelWidth_ignoresNonFiniteValues() {
    val state = PrototypeAppState()
    state.updateSidePanelWidth(400f)

    state.updateSidePanelWidth(Float.NaN)
    state.updateSidePanelWidth(Float.POSITIVE_INFINITY)

    assertEquals(400f, state.sidePanelWidthDp)
  }

  @Test
  fun sidePanelWidth_isKeptWhileCollapsed() {
    val state = PrototypeAppState()
    state.updateSidePanelWidth(480f)

    state.collapseSidePanel()
    assertFalse(state.isSidePanelExpanded)
    state.expandSidePanel()

    assertEquals(480f, state.sidePanelWidthDp)
  }

  @Test
  fun resetPrototypeFlow_restoresDefaultWidth() {
    val state = PrototypeAppState()
    state.updateSidePanelWidth(480f)

    state.resetPrototypeFlow()

    assertEquals(DEFAULT_SIDE_PANEL_WIDTH_DP, state.sidePanelWidthDp)
  }

  @Test
  fun surveyEditorSidePanelWidth_startsAtDefault() {
    assertEquals(
      SurveyEditorState.DEFAULT_SIDE_PANEL_WIDTH_DP,
      SurveyEditorState().sidePanelWidthDp,
    )
  }

  @Test
  fun surveyEditorUpdateSidePanelWidth_acceptsAndClampsWidth() {
    val state = SurveyEditorState()

    state.updateSidePanelWidth(380f)
    assertEquals(380f, state.sidePanelWidthDp)

    state.updateSidePanelWidth(SurveyEditorState.MIN_SIDE_PANEL_WIDTH_DP - 100f)
    assertEquals(SurveyEditorState.MIN_SIDE_PANEL_WIDTH_DP, state.sidePanelWidthDp)

    state.updateSidePanelWidth(SurveyEditorState.MAX_SIDE_PANEL_WIDTH_DP + 100f)
    assertEquals(SurveyEditorState.MAX_SIDE_PANEL_WIDTH_DP, state.sidePanelWidthDp)

    state.updateSidePanelWidth(Float.NaN)
    state.updateSidePanelWidth(Float.NEGATIVE_INFINITY)
    assertEquals(SurveyEditorState.MAX_SIDE_PANEL_WIDTH_DP, state.sidePanelWidthDp)
    assertFalse(state.hasUnpublishedChanges)
  }
}
