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

/**
 * Represents a Ground 2.0 Geospatial Entity (`EntityRecord` in an `EntityDatasetDef` with
 * `EntityType.GEOSPATIAL`) displayed on the map and in the searchable list view.
 */
data class GeospatialEntityItem(
  val id: String,
  val label: String,
  val datasetId: String,
  val datasetName: String,
  val layerId: String,
  val geoId: String,
  val geometryTypeLabel: String,
  val areaHectares: Double,
  val perimeterMeters: Int,
  val coordinatesLabel: String,
  val normalizedX: Float,
  val normalizedY: Float,
  val colorHex: Long,
  val properties: Map<String, String>,
  val submissions: List<SubmissionPreviewItem>,
  val singularTypeLabel: String =
    when (datasetId) {
      "coffee_parcels" -> "Coffee Parcel"
      "shade_monitoring_plots" -> "Shade Tree Monitoring Plot"
      "washing_stations" -> "Cooperative Washing Station"
      else -> datasetName.removeSuffix("s").ifBlank { "Location" }
    },
  val syncStatus: SyncStatus = deriveEntitySyncStatus(submissions),
) {
  val isUploading: Boolean
    get() = syncStatus == SyncStatus.UPLOADING

  val isSynced: Boolean
    get() = syncStatus == SyncStatus.SYNCED

  val isFailed: Boolean
    get() = syncStatus == SyncStatus.FAILED

  /** Total number of recorded form submissions linked to this map feature (`1:N`). */
  val submissionCount: Int
    get() = submissions.size

  /**
   * Raw `simplestyle-spec` marker symbol (`properties["marker-symbol"]`), or `""` when no marker
   * symbol is configured on this entity.
   */
  val rawMarkerSymbol: String
    get() = properties["marker-symbol"]?.trim().orEmpty()

  /** True when this entity has a non-blank `simplestyle-spec` `marker-symbol` configured. */
  val hasMarkerSymbol: Boolean
    get() = rawMarkerSymbol.isNotEmpty()

  /**
   * Per-entity `simplestyle-spec` marker symbol (`properties["marker-symbol"]`), updated via
   * XLSForm `save_to` (`entity_saveto`) across workflow states (e.g. `"○"` -> `"◐"` -> `"✓"`), or
   * `""` when no marker symbol is configured.
   */
  val markerSymbol: String
    get() = rawMarkerSymbol

  /**
   * Per-entity `simplestyle-spec` marker color CSS string (`properties["marker-color"]`), falling
   * back to `stroke`, `fill`, or the layer default [colorHex].
   */
  val markerColorCss: String
    get() =
      properties["marker-color"]
        ?: properties["stroke"]
        ?: properties["fill"]
        ?: formatHexColorCss(colorHex)

  /** Per-entity `simplestyle-spec` marker color parsed as a Compose ARGB `Long`. */
  val markerColorHex: Long
    get() =
      parseHexColorOrDefault(
        properties["marker-color"] ?: properties["stroke"] ?: properties["fill"],
        colorHex,
      )

  /** Per-entity `simplestyle-spec` stroke color CSS string (`properties["stroke"]`). */
  val strokeColorCss: String
    get() = properties["stroke"] ?: properties["marker-color"] ?: formatHexColorCss(colorHex)

  /** Per-entity `simplestyle-spec` stroke color parsed as a Compose ARGB `Long`. */
  val strokeColorHex: Long
    get() = parseHexColorOrDefault(properties["stroke"] ?: properties["marker-color"], colorHex)

  /** Per-entity `simplestyle-spec` fill color CSS string (`properties["fill"]`). */
  val fillColorCss: String
    get() = properties["fill"] ?: properties["marker-color"] ?: formatHexColorCss(colorHex)

  /** Per-entity `simplestyle-spec` fill color parsed as a Compose ARGB `Long`. */
  val fillColorHex: Long
    get() = parseHexColorOrDefault(properties["fill"] ?: properties["marker-color"], colorHex)

  /**
   * Organizer-defined workflow status label stored in `properties["status"]` (updated via
   * `save_to`), e.g., `"Pending"`, `"In progress"`, or `"Completed"`.
   */
  val workflowStatus: String
    get() =
      properties["status"]?.takeIf { it.isNotBlank() }
        ?: when (markerSymbol) {
          "✓" -> "Completed"
          "◐" -> "In progress"
          "○" -> "Pending"
          else -> "Unmarked"
        }

  /**
   * True when `marker-symbol` (`"✓"`) or `status` (`"Completed"`) marks the final completed state.
   */
  val isCompleted: Boolean
    get() = markerSymbol == "✓" || workflowStatus.equals("Completed", ignoreCase = true)

  /** True when `marker-symbol` is in the initial empty circle (`"○"`) state. */
  val isPending: Boolean
    get() = markerSymbol == "○"

  /**
   * Compact map marker indicator badge showing the entity's `marker-symbol` (`"○"`, `"◐"`, `"✓"`).
   */
  val mapIndicatorBadge: String
    get() = markerSymbol

  /**
   * Descriptive badge label combining `marker-symbol` and `status` (e.g. `"○ Pending"`, `"◐ In
   * progress"`, or `"✓ Completed"`).
   */
  val mapStatusSummaryBadge: String
    get() = if (markerSymbol.isNotEmpty()) "$markerSymbol $workflowStatus" else workflowStatus
}

/**
 * Grouping of [GeospatialEntityItem]s under a map feature dataset ([MapLayerItem]) in the Main
 * Survey `List` view (`Map features` category).
 */
data class EntityDatasetFeaturesGroup(
  val layer: MapLayerItem?,
  val datasetName: String,
  val entities: List<GeospatialEntityItem>,
)
