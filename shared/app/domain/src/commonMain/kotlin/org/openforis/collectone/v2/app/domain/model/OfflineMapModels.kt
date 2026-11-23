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
package org.openforis.collectone.v2.app.domain.model

import kotlin.time.Instant
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * The basemap imagery a saved map area keeps for offline use. Each type is a separate layer of the
 * area, with its own download, status, and size.
 */
enum class OfflineImageryType {
  /** Satellite imagery with streets and labels (the Satellite basemap). */
  SATELLITE,

  /** The outdoors map with contours, hillshade, and roads (the Terrain basemap). */
  TERRAIN,
}

/** Where a layer of a saved map area is in its life: saving, saved, paused, or failed. */
enum class OfflineAreaLayerStatus {
  /** Downloading now. */
  SAVING,

  /** Saved: usable offline. */
  READY,

  /**
   * Stopped before it finished, because the app closed or the connection was lost; the user can
   * resume it.
   */
  PAUSED,

  /** Stopped by an error that resuming alone may not fix (a limit, storage, or access error). */
  FAILED,
}

/**
 * How this device saves map areas for offline use, so the UI can say what to expect:
 * - [NATIVE]: the map SDK downloads imagery to the device (Android and iOS).
 * - [SIMULATED]: downloads are only simulated and area names are placeholders (the mobile app in
 *   the browser, whose map has no offline storage); the UI says so.
 * - [UNAVAILABLE]: this build can't save map areas, so the UI hides the entry points.
 */
enum class OfflineRegionMode {
  NATIVE,
  SIMULATED,
  UNAVAILABLE,
}

/** The name of a saved map area: a place name, or a position the UI names ("Area near …"). */
sealed interface OfflineAreaName {
  /** A place name from a geocoder (or a placeholder name), e.g. `Othaya, Nyeri`. */
  data class Named(val text: String) : OfflineAreaName

  /** No place name was found; the UI names the area after [position], its center. */
  data class NearCoordinates(val position: LatLng) : OfflineAreaName
}

/**
 * One imagery [type] of a saved map area.
 *
 * @property progress the share of the download done, from 0 to 1 (1 once [status] is READY).
 * @property size what the layer takes on the device once saved, or its estimate while saving; null
 *   when unknown. Layers share some data (street tiles), so removing one frees up to this size.
 * @property savedSize what was downloaded so far.
 * @property failure why the download stopped, for PAUSED and FAILED layers.
 */
data class OfflineAreaLayer(
  val type: OfflineImageryType,
  val status: OfflineAreaLayerStatus,
  val progress: Float = if (status == OfflineAreaLayerStatus.READY) 1f else 0f,
  val size: DataSize? = null,
  val savedSize: DataSize = DataSize.ZERO,
  val failure: DomainError? = null,
)

/**
 * A map area saved on this device for offline use: the [bounds] the user framed, and a layer per
 * imagery type they chose. Saved areas belong to the device, not to the signed-in account.
 *
 * @property name the place the area is named after.
 * @property createdAt when the user saved it.
 * @property layers the imagery saved for it, at most one per [OfflineImageryType].
 */
data class OfflineMapArea(
  val id: String,
  val name: OfflineAreaName,
  val bounds: LngLatBounds,
  val createdAt: Instant,
  val layers: List<OfflineAreaLayer>,
) {
  /** The sum of the layers' known sizes: what removing the whole area frees, at most. */
  val totalSize: DataSize
    get() = layers.mapNotNull { it.size }.sum()

  /** Whether any layer is downloading. */
  val isSaving: Boolean
    get() = layers.any { it.status == OfflineAreaLayerStatus.SAVING }

  /** The layer of imagery [type], or null when the area doesn't keep it. */
  fun layer(type: OfflineImageryType): OfflineAreaLayer? = layers.firstOrNull { it.type == type }
}

/**
 * The limits every offline map area save follows (see the offline imagery design's "Guards").
 * - Every save covers zoom [MIN_ZOOM] to [MAX_ZOOM], so detail is always zoom 16 (satellite imagery
 *   beyond it is upscaled), with zoomed-out context; the global zoom 0–5 band is skipped.
 * - An area covers at most [MAX_AREA_KM2] (about 7 × 7 km), a day's work area, which keeps each
 *   save to tens of MB and a small share of the device's tile pack budget.
 * - Saving needs room for the estimate plus [ESTIMATE_ERROR_MARGIN] (estimates are approximate) and
 *   a [FREE_SPACE_RESERVE] left free for the rest of the app and the device.
 */
object OfflineMapLimits {
  /** The lowest zoom level every save covers. */
  const val MIN_ZOOM: Int = 6

  /** The highest zoom level every save covers: full zoom 16 detail. */
  const val MAX_ZOOM: Int = 16

  /** The largest area one save covers, in km². */
  const val MAX_AREA_KM2: Double = 50.0

  /** The share added to a size estimate before checking it fits (0.2 = 20%). */
  const val ESTIMATE_ERROR_MARGIN: Double = 0.2

  /** The space a save always leaves free on the device. */
  val FREE_SPACE_RESERVE: DataSize = DataSize.ofMegabytes(500)

  /** The free space saving [estimate] needs: the estimate with its margin, plus the reserve. */
  fun requiredFreeSpace(estimate: DataSize): DataSize =
    estimate * (1 + ESTIMATE_ERROR_MARGIN) + FREE_SPACE_RESERVE
}

/** Whether saving a framed area can go ahead, or why not. */
sealed interface OfflineAreaVerdict {
  /** The area can be saved. */
  data object Ready : OfflineAreaVerdict

  /** The area is larger than [maxAreaKm2]; the user should zoom in. */
  data class TooLarge(val maxAreaKm2: Double, val areaKm2: Double) : OfflineAreaVerdict

  /** The device lacks [shortfall] of the space saving needs (see [OfflineMapLimits]). */
  data class NotEnoughSpace(val shortfall: DataSize) : OfflineAreaVerdict

  /** The device is offline (or the estimate couldn't reach the network). */
  data object Offline : OfflineAreaVerdict

  /** The frame crosses the ±180° meridian, which saved areas don't support. */
  data object CrossesAntimeridian : OfflineAreaVerdict

  /** This build can't save map areas ([OfflineRegionMode.UNAVAILABLE]). */
  data object Unavailable : OfflineAreaVerdict

  /** The estimate failed for a reason other than the network. */
  data class Failed(val error: DomainError) : OfflineAreaVerdict
}

/**
 * The estimate for saving a framed area: its area, the size of each imagery type, the device's free
 * space, and the [verdict] for the selected [types].
 *
 * Sizes are estimated for every imagery type at once, so changing the selection ([withTypes])
 * updates the total and the space check without another request.
 *
 * @property areaKm2 the framed area in km².
 * @property sizes the estimated size of each imagery type; empty when a guard stopped the estimate
 *   before sizes were requested (too large, offline, unavailable).
 * @property freeSpace the device's free space, or null when it can't be measured (then the space
 *   check is skipped).
 */
data class OfflineAreaEstimate(
  val areaKm2: Double,
  val types: Set<OfflineImageryType>,
  val verdict: OfflineAreaVerdict,
  val sizes: Map<OfflineImageryType, DataSize> = emptyMap(),
  val freeSpace: DataSize? = null,
) {
  /** The estimated size of [types] together. */
  fun total(types: Set<OfflineImageryType> = this.types): DataSize =
    types.mapNotNull { sizes[it] }.sum()

  /**
   * This estimate for another selection of [types]: the space check runs again for their total, and
   * any other verdict stays.
   */
  fun withTypes(types: Set<OfflineImageryType>): OfflineAreaEstimate =
    when (verdict) {
      OfflineAreaVerdict.Ready,
      is OfflineAreaVerdict.NotEnoughSpace ->
        copy(types = types, verdict = spaceVerdict(total(types), freeSpace))
      else -> copy(types = types)
    }

  companion object {
    /** [OfflineAreaVerdict.Ready] when [estimate] fits in [freeSpace] (or it's unknown). */
    fun spaceVerdict(estimate: DataSize, freeSpace: DataSize?): OfflineAreaVerdict {
      val required = OfflineMapLimits.requiredFreeSpace(estimate)
      return if (freeSpace == null || required <= freeSpace) OfflineAreaVerdict.Ready
      else OfflineAreaVerdict.NotEnoughSpace(required - freeSpace)
    }
  }
}

/**
 * Whether a frame spans the ±180° meridian, which a saved area can't: it
 * [crosses it][LngLatBounds.crossesAntimeridian] (west edge east of the east edge), or an edge lies
 * beyond ±180° (a camera unprojected past it).
 */
internal val LngLatBounds.spansAntimeridian: Boolean
  get() = crossesAntimeridian || west < -180.0 || east > 180.0
