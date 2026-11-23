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
package org.openforis.collectone.v2.mobile.ui.offlinemaps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.OfflineAreaVerdict
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.ui.common.FlatFilterChip
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.Card
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.map.LngLatBounds
import org.openforis.collectone.v2.map.MapCameraState
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_cancel
import org.openforis.collectone.v2.mobile.resources.mobile_done
import org.openforis.collectone.v2.mobile.resources.mobile_hide
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_close
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_finding_name
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_hint
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_save
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_saved
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_saving
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_saving_size
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_simulated_notice
import org.openforis.collectone.v2.mobile.resources.mobile_offline_area_title
import org.openforis.collectone.v2.mobile.resources.mobile_try_again

/** Test tag of the area selection mode's bottom card. */
internal const val OFFLINE_AREA_CARD_TAG = "offline-area-card"

/** Space between the frame and the screen's sides, the top bar, and the bottom card. */
private val FrameMargin = 24.dp

/**
 * The area selection mode drawn over the live main map: a top bar with the title, a hint and Close
 * ([onClose]), a scrim with a frame cut out of it, and a bottom card with the framed place, the
 * imagery to save, its estimate, and the actions of the current phase.
 *
 * The frame is drawn on a `Canvas` that takes no input, so the map below still pans and zooms.
 * Whenever [camera] moves, the frame's corners are unprojected into the bounds the actions get
 * ([OfflineAreaSelectionActions.onFrameSettled]); while the user drags, they hear only that the
 * frame moves.
 *
 * @param topInset the status bar height the top bar keeps clear of.
 */
@Composable
internal fun OfflineAreaSelectionOverlay(
  uiState: OfflineAreaSelectionUiState,
  actions: OfflineAreaSelectionActions,
  camera: MapCameraState,
  onClose: () -> Unit,
  topInset: Dp,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(0.dp) }
    var cardHeight by remember { mutableStateOf(0.dp) }
    val frame =
      FrameRect(
        left = FrameMargin,
        top = topBarHeight + FrameMargin,
        right = maxWidth - FrameMargin,
        bottom = (maxHeight - cardHeight - FrameMargin).coerceAtLeast(topBarHeight + FrameMargin),
      )
    ReportFrameBounds(camera, frame, actions)
    FrameScrim(frame = frame, isTooLarge = uiState.isTooLarge)

    Column(
      modifier =
        Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged {
          topBarHeight = with(density) { it.height.toDp() }
        }
    ) {
      SelectionTopBar(onClose = onClose, topInset = topInset)
    }

    OfflineAreaSelectionCard(
      uiState = uiState,
      actions = actions,
      onClose = onClose,
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(12.dp)
          .onSizeChanged { cardHeight = with(density) { it.height.toDp() } + 24.dp }
          .testTag(OFFLINE_AREA_CARD_TAG),
    )
  }
}

/** The frame on screen, in the map's coordinates. */
private data class FrameRect(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp) {
  val isEmpty: Boolean
    get() = right <= left || bottom <= top
}

/** Unprojects [frame] whenever [camera] moves, and reports the bounds it frames to [actions]. */
@Composable
private fun ReportFrameBounds(
  camera: MapCameraState,
  frame: FrameRect,
  actions: OfflineAreaSelectionActions,
) {
  val currentActions by rememberUpdatedState(actions)
  LaunchedEffect(camera, frame) {
    if (frame.isEmpty) return@LaunchedEffect
    snapshotFlow { Triple(camera.position, camera.isMovingByGesture, camera.viewportSize) }
      .collectLatest { (position, isMoving, viewport) ->
        when {
          viewport == DpSize.Zero -> Unit
          isMoving -> currentActions.onFrameMoving()
          else -> currentActions.onFrameSettled(camera.boundsOf(frame), position.zoom)
        }
      }
  }
}

/** The bounds [frame] covers on the map. */
private fun MapCameraState.boundsOf(frame: FrameRect): LngLatBounds {
  val northWest = unproject(DpOffset(frame.left, frame.top))
  val southEast = unproject(DpOffset(frame.right, frame.bottom))
  return LngLatBounds(
    west = northWest.longitude,
    south = southEast.latitude,
    east = southEast.longitude,
    north = northWest.latitude,
  )
}

/**
 * A scrim over the map with [frame] cut out of it and outlined, in the error color when the framed
 * area is too large.
 */
@Composable
private fun FrameScrim(frame: FrameRect, isTooLarge: Boolean) {
  val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
  val outline =
    if (isTooLarge) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
  Canvas(modifier = Modifier.fillMaxSize()) {
    if (frame.isEmpty) return@Canvas
    val radius = CornerRadius(12.dp.toPx())
    val cutout =
      RoundRect(
        left = frame.left.toPx(),
        top = frame.top.toPx(),
        right = frame.right.toPx(),
        bottom = frame.bottom.toPx(),
        cornerRadius = radius,
      )
    val path =
      Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, size))
        addRoundRect(cutout)
      }
    drawPath(path, scrim)
    drawRoundRect(
      color = outline,
      topLeft = Offset(cutout.left, cutout.top),
      size = Size(cutout.width, cutout.height),
      cornerRadius = radius,
      style = Stroke(width = 3.dp.toPx()),
    )
  }
}

/** The top bar: Close, the mode's title, and the framing hint. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(onClose: () -> Unit, topInset: Dp) {
  Surface(color = MaterialTheme.colorScheme.surface) {
    Column {
      TopAppBar(
        title = { Text(stringResource(Res.string.mobile_offline_area_title)) },
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = stringResource(Res.string.mobile_offline_area_close),
            )
          }
        },
        windowInsets = WindowInsets(top = topInset),
      )
      Text(
        text = stringResource(Res.string.mobile_offline_area_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
      )
    }
  }
}

/** The bottom card, by phase: framing, saving, saved, or failed. */
@Composable
internal fun OfflineAreaSelectionCard(
  uiState: OfflineAreaSelectionUiState,
  actions: OfflineAreaSelectionActions,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text =
          uiState.nameText?.resolve()
            ?: stringResource(Res.string.mobile_offline_area_finding_name),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      when (uiState.phase) {
        OfflineAreaSelectionPhase.FRAMING -> FramingContent(uiState, actions, onClose)
        OfflineAreaSelectionPhase.SAVING -> SavingContent(uiState, actions, onClose)
        OfflineAreaSelectionPhase.SAVED -> SavedContent(uiState, onClose)
        OfflineAreaSelectionPhase.FAILED -> FailedContent(uiState, actions)
      }
    }
  }
}

@Composable
private fun FramingContent(
  uiState: OfflineAreaSelectionUiState,
  actions: OfflineAreaSelectionActions,
  onClose: () -> Unit,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OfflineImageryType.entries.forEach { type ->
      val selected = type in uiState.selectedTypes
      FlatFilterChip(
        selected = selected,
        onClick = { actions.toggleType(type) },
        label = { Text(stringResource(type.labelRes)) },
        leadingIcon =
          if (selected) {
            {
              Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
              )
            }
          } else {
            null
          },
      )
    }
  }
  val verdict = uiState.estimate?.verdict
  Text(
    text = uiState.framingMessage.resolve(),
    style = MaterialTheme.typography.bodyMedium,
    color =
      if (verdict == null || verdict == OfflineAreaVerdict.Ready) {
        MaterialTheme.colorScheme.onSurfaceVariant
      } else {
        MaterialTheme.colorScheme.error
      },
  )
  if (uiState.isSimulated && verdict == OfflineAreaVerdict.Ready) SimulatedNotice()
  ActionRow {
    TextButton(onClick = onClose) { Text(stringResource(Res.string.mobile_cancel)) }
    if (uiState.canTryAgain) {
      TextButton(onClick = actions::tryAgain) { Text(stringResource(Res.string.mobile_try_again)) }
    }
    Button(onClick = actions::save, enabled = uiState.canSave) {
      Text(stringResource(Res.string.mobile_offline_area_save))
    }
  }
}

@Composable
private fun SavingContent(
  uiState: OfflineAreaSelectionUiState,
  actions: OfflineAreaSelectionActions,
  onClose: () -> Unit,
) {
  Text(
    text = stringResource(Res.string.mobile_offline_area_saving, uiState.progressPercent),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurface,
  )
  LinearProgressIndicator(progress = { uiState.progress }, modifier = Modifier.fillMaxWidth())
  uiState.savingTotal?.let { total ->
    Text(
      text =
        stringResource(
          Res.string.mobile_offline_area_saving_size,
          uiState.savedSoFar.toUiText().resolve(),
          total.toUiText().resolve(),
        ),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  if (uiState.isSimulated) SimulatedNotice()
  ActionRow {
    TextButton(onClick = actions::cancelSave) { Text(stringResource(Res.string.mobile_cancel)) }
    Button(onClick = onClose) { Text(stringResource(Res.string.mobile_hide)) }
  }
}

@Composable
private fun SavedContent(uiState: OfflineAreaSelectionUiState, onClose: () -> Unit) {
  Text(
    text = stringResource(Res.string.mobile_offline_area_saved),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurface,
  )
  if (uiState.isSimulated) SimulatedNotice()
  ActionRow { Button(onClick = onClose) { Text(stringResource(Res.string.mobile_done)) } }
}

@Composable
private fun FailedContent(
  uiState: OfflineAreaSelectionUiState,
  actions: OfflineAreaSelectionActions,
) {
  Text(
    text = uiState.failureMessage.resolve(),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.error,
  )
  ActionRow {
    TextButton(onClick = actions::cancelSave) { Text(stringResource(Res.string.mobile_cancel)) }
    Button(onClick = actions::tryAgain) { Text(stringResource(Res.string.mobile_try_again)) }
  }
}

@Composable
private fun ActionRow(content: @Composable () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    content()
  }
}

/**
 * A calm, non-blocking info row saying downloads are only simulated here (the mobile app in a
 * browser), with [text] (the selection card's notice by default).
 */
@Composable
internal fun SimulatedNotice(
  text: String = stringResource(Res.string.mobile_offline_area_simulated_notice),
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    shape = MaterialTheme.shapes.small,
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Icon(
        imageVector = Icons.Outlined.Info,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
  }
}
