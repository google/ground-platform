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
package org.openforis.collectone.v2.app.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

private const val DEFAULT_SCROLL_BOTTOM_TOLERANCE_PX = 2
private const val MIN_THUMB_HEIGHT_FRACTION = 0.12f

/**
 * Returns true once [ScrollState] has laid out (`maxValue != Int.MAX_VALUE`) and the viewport has
 * reached the bottom of its content (including when all content fits with `maxValue == 0`).
 */
fun ScrollState.hasReachedBottom(tolerancePx: Int = DEFAULT_SCROLL_BOTTOM_TOLERANCE_PX): Boolean =
  maxValue != Int.MAX_VALUE &&
    (!canScrollForward || (maxValue > tolerancePx && value >= maxValue - tolerancePx))

/**
 * Remembers whether [scrollState] has reached the bottom at least once for [resetKey]. Latches
 * `true` once reached so scrolling back up does not re-lock dependent controls.
 */
@Composable
fun rememberHasScrolledToBottom(scrollState: ScrollState, resetKey: Any? = Unit): Boolean {
  var hasScrolledToBottom by rememberSaveable(resetKey) { mutableStateOf(false) }
  LaunchedEffect(scrollState, hasScrolledToBottom) {
    if (!hasScrolledToBottom) {
      snapshotFlow { scrollState.hasReachedBottom() }.first { it }
      hasScrolledToBottom = true
    }
  }
  return hasScrolledToBottom
}

/**
 * Slim vertical scrollbar paired with [scrollState]. Hides itself when content fits without
 * scrolling and supports dragging the thumb vertically.
 */
@Composable
fun VerticalScrollBar(scrollState: ScrollState, modifier: Modifier = Modifier) {
  val maxScroll = scrollState.maxValue
  if (maxScroll <= 0 || maxScroll == Int.MAX_VALUE) return

  val colors = MaterialTheme.colorScheme
  val density = LocalDensity.current

  BoxWithConstraints(
    modifier = modifier.width(12.dp).fillMaxHeight(),
    contentAlignment = Alignment.TopCenter,
  ) {
    val trackHeightPx = with(density) { maxHeight.toPx() }
    if (trackHeightPx <= 0f) return@BoxWithConstraints
    val totalContentPx = trackHeightPx + maxScroll.toFloat()
    val thumbHeightFraction =
      (trackHeightPx / totalContentPx).coerceIn(MIN_THUMB_HEIGHT_FRACTION, 1f)
    val thumbHeightPx = trackHeightPx * thumbHeightFraction
    val scrollableTrackPx = trackHeightPx - thumbHeightPx
    val scrollFraction = (scrollState.value.toFloat() / maxScroll).coerceIn(0f, 1f)
    val thumbOffsetDp = with(density) { (scrollFraction * scrollableTrackPx).toDp() }
    val thumbHeightDp = with(density) { thumbHeightPx.toDp() }

    Box(
      modifier =
        Modifier.width(6.dp)
          .fillMaxHeight()
          .clip(CircleShape)
          .background(colors.surfaceContainerHighest.copy(alpha = 0.5f))
    )
    Box(
      modifier =
        Modifier.offset(y = thumbOffsetDp).width(12.dp).height(thumbHeightDp).pointerInput(
          scrollState,
          maxScroll,
          scrollableTrackPx,
        ) {
          detectVerticalDragGestures { change, dragAmount ->
            change.consume()
            if (scrollableTrackPx > 0f) {
              scrollState.dispatchRawDelta((dragAmount / scrollableTrackPx) * maxScroll)
            }
          }
        },
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier =
          Modifier.width(6.dp)
            .fillMaxHeight()
            .clip(CircleShape)
            .background(colors.outline.copy(alpha = 0.65f))
      )
    }
  }
}
