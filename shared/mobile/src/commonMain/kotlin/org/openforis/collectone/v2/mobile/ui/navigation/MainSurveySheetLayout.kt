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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.openforis.collectone.v2.app.ui.navigation.EntityDetailsPane

// How the Main Survey's persistent bottom sheet sits over the map, and where its Back button goes.
// Kept out of `SurveyMapView` so the rules can be tested without composing the map.

/** Where the bottom sheet's Back button goes while a record is selected. */
internal enum class SelectedRecordBack {
  /** From a submission of the selected map feature, back to the feature. */
  CLOSE_SUBMISSION,
  /** From the selected feature's submissions, back to its properties. */
  SHOW_PROPERTIES,
  /** Back to the searchable list, clearing the selection. */
  RETURN_TO_LIST,
}

/**
 * Where Back goes in the sheet, one level at a time: a submission opened from a map feature returns
 * to that feature, the feature's submissions return to its properties, and anything else (the
 * feature's properties, or a standalone submission, which has no feature to return to) returns to
 * the list.
 */
internal fun selectedRecordBack(
  hasSelectedEntity: Boolean,
  hasSelectedSubmission: Boolean,
  entityDetailsPane: EntityDetailsPane,
): SelectedRecordBack =
  when {
    hasSelectedSubmission && hasSelectedEntity -> SelectedRecordBack.CLOSE_SUBMISSION
    entityDetailsPane == EntityDetailsPane.SUBMISSIONS && hasSelectedEntity ->
      SelectedRecordBack.SHOW_PROPERTIES
    else -> SelectedRecordBack.RETURN_TO_LIST
  }

/**
 * How high the sheet peeks over a map [maxHeight] tall. With a record selected it peeks at about
 * half the screen (never less than [MIN_SELECTED_PEEK]): enough to show the record's details while
 * the map above frames the feature itself. The area selection mode hides it.
 */
internal fun mainSurveySheetPeekHeight(
  maxHeight: Dp,
  isSelectingArea: Boolean,
  hasSelectedRecord: Boolean,
): Dp =
  when {
    isSelectingArea -> 0.dp
    hasSelectedRecord -> (maxHeight * SELECTED_PEEK_FRACTION).coerceAtLeast(MIN_SELECTED_PEEK)
    else -> LIST_PEEK
  }

/**
 * The vertical screen position (0 is the top, 1 the bottom) to center a newly selected feature at:
 * the middle of the map left visible above a sheet [peekHeight] tall, kept between 10% and 50% of a
 * map [maxHeight] tall so the feature never hides under the search bar or below the middle. Mobile
 * has no side details panel, so the feature stays centered horizontally.
 */
internal fun selectedFeatureScreenY(maxHeight: Dp, peekHeight: Dp): Float {
  if (maxHeight <= 0.dp) return MAX_FEATURE_SCREEN_Y
  val visibleHeight = (maxHeight - peekHeight).coerceAtLeast(0.dp)
  return ((visibleHeight / 2) / maxHeight).coerceIn(MIN_FEATURE_SCREEN_Y, MAX_FEATURE_SCREEN_Y)
}

/**
 * How far below the top edge of the sheet its drag handle sits, in pixels, so that the handle and
 * the content under it never slide behind the floating search bar, whose bottom edge is
 * [searchBarBottomPx] below the top of the screen. The sheet's top edge is [sheetTopPx] below the
 * top of the screen: while it is under the search bar the handle sits right on the edge, and once
 * it rises behind the bar the handle is pushed down by exactly as much, so it comes to rest under
 * the bar and moves with the finger all the way, without jumping when the sheet settles.
 *
 * Before the sheet has been laid out ([sheetTopPx] is `null`), the handle goes where it will rest:
 * under the search bar when the sheet [isExpanded], on the edge otherwise.
 */
internal fun sheetHandleTopInsetPx(
  searchBarBottomPx: Int,
  sheetTopPx: Float?,
  isExpanded: Boolean,
): Int =
  if (sheetTopPx == null || sheetTopPx.isNaN()) {
    if (isExpanded) searchBarBottomPx else 0
  } else {
    (searchBarBottomPx - sheetTopPx).roundToInt().coerceIn(0, searchBarBottomPx)
  }

private const val SELECTED_PEEK_FRACTION = 0.45f
private val MIN_SELECTED_PEEK = 152.dp
private val LIST_PEEK = 104.dp
private const val MIN_FEATURE_SCREEN_Y = 0.10f
private const val MAX_FEATURE_SCREEN_Y = 0.50f
