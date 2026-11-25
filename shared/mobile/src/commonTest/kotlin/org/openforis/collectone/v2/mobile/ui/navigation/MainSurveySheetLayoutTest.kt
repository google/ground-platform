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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.ui.navigation.EntityDetailsPane

/**
 * The Main Survey's sheet: how high it peeks, where a selected feature is centered above it, and
 * where its Back button goes.
 */
class MainSurveySheetLayoutTest {
  @Test
  fun peekHeight_withNothingSelected_showsTheListHeading() {
    assertEquals(
      104.dp,
      mainSurveySheetPeekHeight(800.dp, isSelectingArea = false, hasSelectedRecord = false),
    )
  }

  @Test
  fun peekHeight_withARecordSelected_isAboutHalfTheScreen_butNeverTooLow() {
    assertEquals(
      360.dp,
      mainSurveySheetPeekHeight(800.dp, isSelectingArea = false, hasSelectedRecord = true),
    )
    // 45% of 300 dp is 135 dp, below the 152 dp the record's heading needs.
    assertEquals(
      152.dp,
      mainSurveySheetPeekHeight(300.dp, isSelectingArea = false, hasSelectedRecord = true),
    )
  }

  @Test
  fun peekHeight_whileSelectingAnArea_hidesTheSheet() {
    assertEquals(
      0.dp,
      mainSurveySheetPeekHeight(800.dp, isSelectingArea = true, hasSelectedRecord = true),
    )
  }

  @Test
  fun featureScreenY_centersInTheMapLeftAboveTheSheet() {
    // 800 dp tall, 360 dp sheet: the visible map is 440 dp, its middle 220 dp from the top.
    assertEquals(0.275f, selectedFeatureScreenY(800.dp, 360.dp), absoluteTolerance = 1e-6f)
  }

  @Test
  fun featureScreenY_staysBetweenTenAndFiftyPercent() {
    // A sheet over almost the whole map would put the feature under the search bar.
    assertEquals(0.10f, selectedFeatureScreenY(800.dp, 790.dp))
    // No sheet: the middle of the screen, never lower.
    assertEquals(0.50f, selectedFeatureScreenY(800.dp, 0.dp))
    // A sheet taller than the map counts as no visible map at all.
    assertEquals(0.10f, selectedFeatureScreenY(800.dp, 900.dp))
  }

  @Test
  fun featureScreenY_beforeTheMapIsMeasured_isTheMiddle() {
    assertEquals(0.50f, selectedFeatureScreenY(0.dp, 104.dp))
  }

  @Test
  fun back_fromAFeaturesSubmission_returnsToTheFeature() {
    assertEquals(
      SelectedRecordBack.CLOSE_SUBMISSION,
      selectedRecordBack(
        hasSelectedEntity = true,
        hasSelectedSubmission = true,
        entityDetailsPane = EntityDetailsPane.SUBMISSIONS,
      ),
    )
  }

  @Test
  fun back_fromAFeaturesSubmissions_returnsToItsProperties() {
    assertEquals(
      SelectedRecordBack.SHOW_PROPERTIES,
      selectedRecordBack(
        hasSelectedEntity = true,
        hasSelectedSubmission = false,
        entityDetailsPane = EntityDetailsPane.SUBMISSIONS,
      ),
    )
  }

  @Test
  fun back_fromAFeaturesProperties_orAStandaloneSubmission_returnsToTheList() {
    val panes = EntityDetailsPane.entries
    for (pane in panes.filter { it != EntityDetailsPane.SUBMISSIONS }) {
      assertEquals(
        SelectedRecordBack.RETURN_TO_LIST,
        selectedRecordBack(
          hasSelectedEntity = true,
          hasSelectedSubmission = false,
          entityDetailsPane = pane,
        ),
        "from $pane",
      )
    }
    for (pane in panes) {
      assertEquals(
        SelectedRecordBack.RETURN_TO_LIST,
        selectedRecordBack(
          hasSelectedEntity = false,
          hasSelectedSubmission = true,
          entityDetailsPane = pane,
        ),
        "standalone submission from $pane",
      )
    }
  }

  @Test
  fun handle_sitsOnTheSheetsEdgeWhileTheSheetIsBelowTheSearchBar() {
    assertEquals(0, sheetHandleTopInsetPx(searchBarBottomPx = 66, sheetTopPx = 700f, false))
    assertEquals(0, sheetHandleTopInsetPx(searchBarBottomPx = 66, sheetTopPx = 66f, false))
  }

  @Test
  fun handle_isPushedDownByAsMuchAsTheSheetRisesBehindTheSearchBar() {
    assertEquals(16, sheetHandleTopInsetPx(searchBarBottomPx = 66, sheetTopPx = 50f, false))
    assertEquals(33, sheetHandleTopInsetPx(searchBarBottomPx = 66, sheetTopPx = 33f, true))
    assertEquals(66, sheetHandleTopInsetPx(searchBarBottomPx = 66, sheetTopPx = 0f, true))
  }

  @Test
  fun handle_staysOnScreenAtTheSamePlaceOnceTheSheetIsBehindTheSearchBar() {
    // The sheet's edge plus the inset is where the handle shows: constant under the search bar.
    for (sheetTop in listOf(66f, 48f, 20f, 0f)) {
      val inset = sheetHandleTopInsetPx(searchBarBottomPx = 66, sheetTopPx = sheetTop, true)
      assertEquals(66f, sheetTop + inset, "sheet top at $sheetTop")
    }
  }

  @Test
  fun handle_neverLeavesTheRangeOfTheSearchBarsHeight() {
    assertEquals(66, sheetHandleTopInsetPx(searchBarBottomPx = 66, sheetTopPx = -40f, true))
    assertEquals(0, sheetHandleTopInsetPx(searchBarBottomPx = 66, sheetTopPx = 5_000f, false))
  }

  @Test
  fun handle_beforeTheSheetIsLaidOut_goesWhereItWillRest() {
    assertEquals(66, sheetHandleTopInsetPx(searchBarBottomPx = 66, sheetTopPx = null, true))
    assertEquals(0, sheetHandleTopInsetPx(searchBarBottomPx = 66, sheetTopPx = null, false))
    assertEquals(66, sheetHandleTopInsetPx(searchBarBottomPx = 66, sheetTopPx = Float.NaN, true))
  }
}
