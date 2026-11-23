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

import kotlin.math.roundToInt
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.model.DataSize
import org.openforis.collectone.v2.app.domain.model.OfflineAreaEstimate
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineAreaVerdict
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineMapArea
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.model.sum
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.map.LngLatBounds

/** Where the area selection is: framing an area, saving it, saved, or stopped by a failure. */
enum class OfflineAreaSelectionPhase {
  FRAMING,
  SAVING,
  SAVED,
  FAILED,
}

/**
 * Screen state of the area selection mode on the main map.
 *
 * @property isActive whether the mode shows (the main map hides its other controls meanwhile).
 * @property bounds the framed area, or null until the map reports it.
 * @property zoom the map's zoom when the frame settled, which picks how specific its name is.
 * @property name the framed place's name, or null while it's looked up.
 * @property selectedTypes the imagery to save; never empty.
 * @property estimate the estimate for [bounds] and [selectedTypes], or null while estimating.
 * @property savingAreaId the area being saved, from [OfflineAreaSelectionPhase.SAVING] on.
 * @property savingArea that area as saved so far, with its layers' progress.
 * @property saveFailure why saving stopped, in [OfflineAreaSelectionPhase.FAILED].
 */
data class OfflineAreaSelectionUiState(
  val isActive: Boolean = false,
  val regionMode: OfflineRegionMode = OfflineRegionMode.UNAVAILABLE,
  val phase: OfflineAreaSelectionPhase = OfflineAreaSelectionPhase.FRAMING,
  val bounds: LngLatBounds? = null,
  val zoom: Double = 0.0,
  val name: OfflineAreaName? = null,
  val selectedTypes: Set<OfflineImageryType> = OfflineImageryType.entries.toSet(),
  val estimate: OfflineAreaEstimate? = null,
  val savingAreaId: String? = null,
  val savingArea: OfflineMapArea? = null,
  val saveFailure: DomainError? = null,
) {
  /** Whether the frame is larger than an area can be (its outline turns to the error color). */
  val isTooLarge: Boolean
    get() = estimate?.verdict is OfflineAreaVerdict.TooLarge

  /** Whether Save for offline is enabled. */
  val canSave: Boolean
    get() =
      phase == OfflineAreaSelectionPhase.FRAMING &&
        bounds != null &&
        estimate?.verdict == OfflineAreaVerdict.Ready

  /** Whether downloads are only simulated here, which the card says. */
  val isSimulated: Boolean
    get() = regionMode == OfflineRegionMode.SIMULATED

  /** Whether the card offers Try again: an estimate that failed or couldn't reach the network. */
  val canTryAgain: Boolean
    get() =
      when (phase) {
        OfflineAreaSelectionPhase.FAILED -> true
        OfflineAreaSelectionPhase.FRAMING ->
          estimate?.verdict.let {
            it == OfflineAreaVerdict.Offline || it is OfflineAreaVerdict.Failed
          }
        else -> false
      }

  /** The estimated size of what's being saved, or null when unknown. */
  val savingTotal: DataSize?
    get() = savingArea?.layers?.mapNotNull { it.size }?.takeIf { it.isNotEmpty() }?.sum()

  /** What's saved so far. */
  val savedSoFar: DataSize
    get() = savingArea?.layers?.map { it.savedSize }?.sum() ?: DataSize.ZERO

  /** The combined progress of the layers being saved, from 0 to 1. */
  val progress: Float
    get() {
      val layers = savingArea?.layers.orEmpty()
      if (layers.isEmpty()) return 0f
      val total = savingTotal
      return if (total != null && total.bytes > 0) {
        (savedSoFar.bytes.toFloat() / total.bytes).coerceIn(0f, 1f)
      } else {
        layers.map { it.progress }.average().toFloat()
      }
    }

  /** [progress] as a whole percentage. */
  val progressPercent: Int
    get() = (progress * 100).roundToInt()

  /** The name of the framed place, or null while it's looked up. */
  val nameText: UiText?
    get() = name?.let(::offlineAreaNameText)

  /** The card's message while framing: the estimate, or why the area can't be saved. */
  val framingMessage: UiText
    get() = estimate?.let(::estimateText) ?: estimatingText()

  /** Why saving stopped, in [OfflineAreaSelectionPhase.FAILED]. */
  val failureMessage: UiText
    get() = saveFailureText(saveFailure)
}
