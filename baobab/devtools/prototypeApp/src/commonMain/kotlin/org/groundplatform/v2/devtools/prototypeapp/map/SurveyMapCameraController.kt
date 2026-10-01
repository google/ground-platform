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
package org.groundplatform.v2.devtools.prototypeapp.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.groundplatform.v2.map.CameraPosition
import org.groundplatform.v2.map.MapCameraState
import org.groundplatform.v2.map.MapEvent
import org.groundplatform.v2.map.MapInsets
import org.groundplatform.v2.map.rememberMapCameraState

/**
 * Keeps a map's [camera] and the app state that describes it in step.
 *
 * App state (pan offset, zoom delta, GPS following) says where the camera *should* be ([desired]);
 * the controller animates there whenever that changes. User gestures and explicit camera moves
 * ([run]) go the other way: once the camera settles, [onSettled] writes the result back to state.
 * If state then disagrees (for example because it clamps the zoom), the camera eases back.
 */
@Stable
internal class SurveyMapCameraController(
  val camera: MapCameraState,
  private val scope: CoroutineScope,
  private val desired: () -> CameraPosition,
  private val onSettled: (CameraPosition) -> Unit,
) {
  private var explicitMove: Job? = null
  private var gestureInProgress = false

  /**
   * Runs [block] (for example `fitBounds`) instead of following state until it finishes, then syncs
   * state to where the camera ended up. A newer [run] cancels an unfinished one.
   */
  fun run(block: suspend (MapCameraState) -> Unit) {
    explicitMove?.cancel()
    explicitMove =
      scope.launch {
        // Fitting needs the viewport size, which is zero until the map is laid out.
        snapshotFlow { camera.viewportSize }.first { it.width > 0.dp && it.height > 0.dp }
        block(camera)
        onSettled(camera.position)
      }
  }

  /** Forward the map's [MapEvent.CameraIdle] events here. */
  fun onCameraIdle(event: MapEvent.CameraIdle) {
    if (!event.byGesture) return
    gestureInProgress = false
    onSettled(event.camera)
    scope.launch { followDesired() }
  }

  /** Follows [desired] until cancelled; launched by [rememberSurveyMapCamera]. */
  internal suspend fun follow() {
    launchGestureTracking()
    snapshotFlow { desired() }.collectLatest { followDesired() }
  }

  private fun launchGestureTracking() {
    scope.launch {
      snapshotFlow { camera.position to camera.isMovingByGesture }
        .collect { (_, byGesture) -> if (byGesture) gestureInProgress = true }
    }
  }

  private suspend fun followDesired() {
    // Wait a frame so an explicit move started by the same state change takes precedence.
    withFrameNanos {}
    if (gestureInProgress || explicitMove?.isActive == true) return
    val target = desired()
    val current = camera.position
    if (isSameView(current, target)) return
    if (isFarAway(target)) camera.move(target) else camera.animateTo(target)
  }

  private fun isSameView(a: CameraPosition, b: CameraPosition): Boolean {
    if (abs(a.zoom - b.zoom) > 0.01 || abs(a.bearing - b.bearing) > 0.1) return false
    val p = camera.project(b.center)
    val center = centerOf(camera.viewportSize)
    return hypot((p.x - center.x).value, (p.y - center.y).value) < 0.5f
  }

  /** Targets several screens away (such as another survey) jump rather than animate. */
  private fun isFarAway(target: CameraPosition): Boolean {
    val size = camera.viewportSize
    if (size.width <= 0.dp || size.height <= 0.dp) return true
    val p = camera.project(target.center)
    val center = centerOf(size)
    val distance = hypot((p.x - center.x).value, (p.y - center.y).value)
    return distance > 3 * hypot(size.width.value, size.height.value) ||
      abs(target.zoom - camera.position.zoom) > 4
  }

  private fun centerOf(size: DpSize) = DpOffset(size.width / 2, size.height / 2)
}

/**
 * Remembers a [SurveyMapCameraController] starting at [desired], following it while in composition.
 */
@Composable
internal fun rememberSurveyMapCamera(
  desired: () -> CameraPosition,
  onSettled: (CameraPosition) -> Unit,
): SurveyMapCameraController {
  val currentDesired by rememberUpdatedState(desired)
  val currentOnSettled by rememberUpdatedState(onSettled)
  val camera = rememberMapCameraState(desired())
  val scope = rememberCoroutineScope()
  val controller =
    remember(camera, scope) {
      SurveyMapCameraController(camera, scope, { currentDesired() }, { currentOnSettled(it) })
    }
  LaunchedEffect(controller) { controller.follow() }
  return controller
}

/**
 * Insets that keep a framed feature clear of a panel at the [bottom] and one on the [right], plus a
 * margin all round. Each axis's insets are scaled down to at most 80% of the viewport, so small
 * maps still show the feature.
 */
internal fun framingInsets(viewport: DpSize, bottom: Dp, right: Dp = 0.dp): MapInsets {
  val margin = 48.dp
  fun fit(start: Dp, end: Dp, total: Dp): Pair<Dp, Dp> {
    val sum = start + end
    val max = total * 0.8f
    if (sum <= max || sum <= 0.dp) return start to end
    val scale = max / sum
    return start * scale to end * scale
  }
  val (left, r) = fit(margin, margin + right, viewport.width)
  val (top, b) = fit(margin, margin + bottom, viewport.height)
  return MapInsets(left = left, top = top, right = r, bottom = b)
}
