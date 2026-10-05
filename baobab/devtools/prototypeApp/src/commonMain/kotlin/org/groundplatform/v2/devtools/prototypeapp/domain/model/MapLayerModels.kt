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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

/** Specification for the horizontal map scale bar widget. */
data class MapScaleBarSpec(val label: String, val distanceMeters: Int, val barWidthDp: Float)

/** Camera lock / GPS tracking mode on the survey map (`LOCKED`, `LOCKED_3D`, or `PANNED`). */
enum class LocationLockState(val label: String) {
  LOCKED("GPS Auto-Center"),
  LOCKED_3D("GPS 3D"),
  PANNED("Panned"),
}

/**
 * Distinguishes `LayerDef.source` in `SurveyDef.map_config.layers` (per `03-maps.md`):
 * - [ENTITY_DATASET]: `entity_dataset_id` (rendered with solid polygon/marker outlines)
 */
enum class LayerSourceType(val badgeLabel: String) {
  ENTITY_DATASET("Survey Layer")
}

/** Primary map basemap mode selectable by the user in the `Layers` sheet (`Map` vs `Satellite`). */
enum class BasemapType(val label: String, val description: String) {
  NORMAL(label = "Map", description = "Standard vector terrain, roads & contour basemap"),
  SATELLITE(
    label = "Satellite",
    description = "Mapbox Standard Satellite imagery with reference labels",
  ),
}

/** Offline basemap rendering style toggleable in the `Layers` sheet. */
enum class OfflineBasemapStyle(val label: String, val tileDescription: String) {
  SATELLITE_HYBRID(
    label = "Satellite + Contours",
    tileDescription = "Satellite Raster + Vector Contours (Nyeri 82.7 MB cached)",
  ),
  VECTOR_TOPO(
    label = "Vector Topographic",
    tileDescription = "Vector Terrain & Hydrology (Nyeri 14.2 MB cached)",
  ),
}

/**
 * Represents a toggleable map layer (`LayerDef` in `SurveyDef.map_config.layers`).
 *
 * The layer's style ([colorHex], [iconName]) is how its map features are drawn: pins are filled
 * with [colorHex] and show [iconName] inside, and lines and polygons are stroked and filled with
 * [colorHex]. A feature's workflow status is shown in its status chip, not on the map.
 */
data class MapLayerItem(
  val id: String,
  val label: String,
  val sourceDescription: String,
  /**
   * Layer color (`GeometryStyle.color`) as an ARGB `Long`, used for all of the layer's features.
   */
  val colorHex: Long,
  val geometryTypeLabel: String,
  val isVisible: Boolean,
  val sourceType: LayerSourceType = LayerSourceType.ENTITY_DATASET,
  val formId: String? = null,
  val formTitle: String? = null,
  val fieldPath: String? = null,
  val singularItemLabel: String = "location",
  val pluralItemLabel: String = "locations",
  /** ID of the entity dataset this layer shows (`LayerDef.entity_dataset_id`), if known. */
  val datasetId: String? = null,
  /**
   * Name of the icon drawn inside this layer's point pins (a Material Symbols name such as
   * `"park"`), or `null` for a plain pin. See `map/LayerIcons.kt` for the available icons.
   */
  val iconName: String? = null,
  val pluralDomainLabel: String =
    when (id) {
      "layer-coffee-parcels" -> "Coffee Parcels"
      "layer-shade-transects" -> "Monitoring Plots"
      "layer-water-points" -> "Washing Stations"
      else -> label
    },
  val singularNoun: String =
    when (id) {
      "layer-coffee-parcels" -> "parcel"
      "layer-shade-transects" -> "plot"
      "layer-water-points" -> "station"
      else -> singularItemLabel
    },
  val pluralNoun: String =
    when (id) {
      "layer-coffee-parcels" -> "parcels"
      "layer-shade-transects" -> "plots"
      "layer-water-points" -> "stations"
      else -> pluralItemLabel
    },
) {
  /**
   * The icon to display for this layer: [iconName] for point layers, `null` for line and polygon
   * layers, whose features have no pins (their chips and list markers use the geometry glyph).
   */
  val pinIconName: String?
    get() = iconName.takeIf { geometryTypeLabel.endsWith("Point", ignoreCase = true) }

  /** Formats a user-friendly domain item count for this layer (e.g. `"2 parcels"`, `"1 plot"`). */
  fun itemCountLabel(count: Int): String = "$count ${if (count == 1) singularNoun else pluralNoun}"

  fun formatCountLabel(count: Int): String =
    if (count == 1) "1 $singularItemLabel" else "$count $pluralItemLabel"
}

/** Pre-cached Mapbox vector/raster offline basemap tile package (`Offline maps`). */
data class OfflineTilePackageItem(
  val id: String,
  val regionName: String,
  val tileTypeLabel: String,
  val zoomRangeLabel: String,
  val sizeLabel: String,
  val isDownloaded: Boolean,
)

/**
 * Parses a CSS hex color string (e.g. `"#1E8E3E"` or `"1E8E3E"`) into an ARGB `Long`
 * (`0xFF1E8E3E`).
 */
fun parseHexColorOrDefault(cssColor: String?, fallbackHex: Long): Long {
  val cleaned = cssColor?.trim()?.removePrefix("#") ?: return fallbackHex
  if (cleaned.length != 6) return fallbackHex
  val rgb = cleaned.toLongOrNull(16) ?: return fallbackHex
  return 0xFF000000L or rgb
}

/** Formats an ARGB `Long` color (`0xFFRRGGBB`) into a 6-digit CSS hex string (`"#RRGGBB"`). */
fun formatHexColorCss(colorHex: Long): String =
  "#" + (colorHex and 0xFFFFFFL).toString(16).padStart(6, '0').uppercase()
