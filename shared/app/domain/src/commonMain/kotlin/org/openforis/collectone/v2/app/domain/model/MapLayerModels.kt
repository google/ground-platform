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
package org.openforis.collectone.v2.app.domain.model

import org.openforis.collectone.v2.core.sampling.SampleDesign
import org.openforis.collectone.v2.map.GeometryType

/** Specification for the horizontal map scale bar widget. */
data class MapScaleBarSpec(val length: Measurement, val distanceMeters: Int, val barWidthDp: Float)

/** Camera lock / GPS tracking mode on the survey map (`LOCKED`, `LOCKED_3D`, or `PANNED`). */
enum class LocationLockState {
  LOCKED,
  LOCKED_3D,
  PANNED,
}

/**
 * Distinguishes `LayerDef.source` in `SurveyDef.map_config.layers` (per `03-maps.md`):
 * - [ENTITY_DATASET]: `entity_dataset_id` (rendered with solid polygon/marker outlines)
 */
enum class LayerSourceType {
  ENTITY_DATASET
}

/**
 * Primary map basemap mode selectable by the user in the `Layers` sheet: [NORMAL] is the Terrain
 * basemap (outdoors map with contours, hillshade, and roads), [SATELLITE] satellite imagery.
 */
enum class BasemapType {
  NORMAL,
  SATELLITE,
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
  /**
   * Layer color (`GeometryStyle.color`) as an ARGB `Long`, used for all of the layer's features.
   */
  val colorHex: Long,
  /** Type of the geometries this layer draws (`LayerDef.geometry_type`), if known. */
  val geometryType: GeometryType?,
  val isVisible: Boolean,
  val sourceType: LayerSourceType = LayerSourceType.ENTITY_DATASET,
  val formId: String? = null,
  val formTitle: String? = null,
  val fieldPath: String? = null,
  /**
   * The survey author's noun for one of this layer's features (`LayerDef.singular_item_label`, e.g.
   * `"coffee parcel"`), or blank when the author gave none; the UI then uses a generic noun in the
   * user's language.
   */
  val singularNoun: String = "",
  /**
   * The author's noun for several of this layer's features (`LayerDef.plural_item_label`, e.g.
   * `"coffee parcels"`), or blank when the author gave none.
   */
  val pluralNoun: String = "",
  /** ID of the entity dataset this layer shows (`LayerDef.entity_dataset_id`), if known. */
  val datasetId: String? = null,
  /**
   * Name of the icon drawn inside this layer's point pins (a Material Symbols name such as
   * `"park"`), or `null` for a plain pin. See `map/LayerIcons.kt` for the available icons.
   */
  val iconName: String? = null,
  /**
   * Statistical sampling design of this layer's dataset (`EntityDatasetDef.sampling`), if it was
   * generated from one; used to resolve and scale sub-plot sample rings when a plot is selected.
   */
  val sampleDesign: SampleDesign? = null,
) {
  /**
   * The icon to display for this layer: [iconName] for point layers, `null` for line and polygon
   * layers, whose features have no pins (their chips and list markers use the geometry glyph).
   */
  val pinIconName: String?
    get() = iconName.takeIf { geometryType == GeometryType.POINT }
}

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
