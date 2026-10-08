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

import kotlin.math.roundToInt

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

  /**
   * True while [geoId] is a locally computed candidate rather than the GeoID assigned by the
   * AgStack Asset Registry. A field's registered GeoID depends on what the registry already holds
   * (its level-13 hash, or the level-20 hash when that is taken), so it is only confirmed once the
   * record has synced.
   */
  val isGeoIdPendingSync: Boolean
    get() = !isSynced

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
   * back to `stroke`, `fill`, or the layer default [colorHex]. Updated with the workflow status, so
   * it colors the status chip; map features use their layer's color instead ([mapColorHex]).
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
   * Color this feature is drawn with on the map and in list-row markers: the color of its map
   * [layer] (the layer that owns [layerId]), falling back to the entity's layer default [colorHex]
   * when the layer isn't known. Per-entity `marker-color` / `stroke` / `fill` properties reflect
   * workflow status and are only used by the status chip ([markerColorHex]).
   */
  fun mapColorHex(layer: MapLayerItem?): Long =
    0xFF000000L or ((layer?.takeIf { it.id == layerId }?.colorHex ?: colorHex) and 0xFFFFFFL)

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

  /**
   * True when `marker-symbol` is in the initial empty circle (`"○"`) workflow state. Unrelated to
   * upload state; see `PrototypeAppState.pendingUploadEntityIds` for unsynced changes.
   */
  val isNotStarted: Boolean
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

/**
 * Resolves the user-facing singular domain noun of the entity with [entityId] (e.g. `"Coffee
 * Parcel"`), or `"Location"` when [entityId] is `null` or unknown.
 */
fun List<GeospatialEntityItem>.singularTypeLabelOf(entityId: String?): String =
  entityId?.let { id -> firstOrNull { it.id == id }?.singularTypeLabel } ?: "Location"

/**
 * Resolves a property [value] of [entity] that references another record in this list (a foreign
 * key holding that record's ID or GeoID), or `null` when the value isn't a reference. Related
 * records are resolved regardless of whether they have geometry.
 */
fun List<GeospatialEntityItem>.relatedEntityForPropertyValue(
  entity: GeospatialEntityItem,
  value: String,
): GeospatialEntityItem? {
  val key = value.trim()
  if (key.isEmpty()) return null
  return firstOrNull {
    it.id != entity.id && (it.id == key || it.geoId.equals(key, ignoreCase = true))
  }
}

/**
 * The entity dataset layers ([LayerSourceType.ENTITY_DATASET]) among [layers] that own at least one
 * of these map features, or an empty list when there are no map features (so the map layers section
 * of the `Layers` sheet is hidden).
 */
fun List<GeospatialEntityItem>.entityDatasetLayersIn(
  layers: List<MapLayerItem>
): List<MapLayerItem> =
  if (isEmpty()) {
    emptyList()
  } else {
    layers.filter { layer ->
      layer.sourceType == LayerSourceType.ENTITY_DATASET && any { it.layerId == layer.id }
    }
  }

/** Geometry kind of a map feature, shown as the leading icon of list rows and details headers. */
enum class EntityGeometryKind(val label: String) {
  POINT("Point"),
  LINE("Line"),
  POLYGON("Polygon"),

  /** A record without geometry, i.e. a row in a Data table. */
  NONE("No geometry"),
}

/** Geometry kind derived from [GeospatialEntityItem.geometryTypeLabel]. */
val GeospatialEntityItem.geometryKind: EntityGeometryKind
  get() =
    when {
      geometryTypeLabel.contains("polygon", ignoreCase = true) -> EntityGeometryKind.POLYGON
      geometryTypeLabel.contains("line", ignoreCase = true) -> EntityGeometryKind.LINE
      geometryTypeLabel.contains("point", ignoreCase = true) -> EntityGeometryKind.POINT
      else -> EntityGeometryKind.NONE
    }

/** True when the record has a geometry the map can pan and zoom to. */
val GeospatialEntityItem.hasGeometry: Boolean
  get() = geometryKind != EntityGeometryKind.NONE

/**
 * `simplestyle-spec` presentation keys and the workflow `status`. They drive map styling and the
 * status icon, so they are left out of property listings and the map layer table.
 */
val EntityPresentationPropertyKeys =
  setOf(
    "marker-size",
    "marker-symbol",
    "marker-color",
    "stroke",
    "stroke-opacity",
    "stroke-width",
    "fill",
    "fill-opacity",
    "title",
    "description",
    "status",
  )

/** Organizer-defined properties of the entity, excluding presentation keys. */
val GeospatialEntityItem.displayProperties: List<Pair<String, String>>
  get() = properties.filterKeys { it !in EntityPresentationPropertyKeys }.toList()

/**
 * Maximum map camera zoom used when fitting a selected entity of this geometry kind into view, so
 * small features (and points, which have no extent) aren't framed too close.
 */
val EntityGeometryKind.maxFramingZoom: Float
  get() =
    when (this) {
      EntityGeometryKind.POINT -> 16f
      EntityGeometryKind.LINE,
      EntityGeometryKind.POLYGON -> 17f
      EntityGeometryKind.NONE -> 0f
    }

/** Human-readable geometry summary (kind plus measurements in the user's unit system). */
fun entityGeometrySummary(
  entity: GeospatialEntityItem,
  unitSystem: MeasurementUnitSystem,
): String {
  val isMetric = unitSystem == MeasurementUnitSystem.METRIC
  fun length(meters: Int) = if (isMetric) "$meters m" else "${(meters * 3.28084).roundToInt()} ft"
  return when (entity.geometryKind) {
    EntityGeometryKind.POLYGON -> {
      val area =
        if (isMetric) {
          "${entity.areaHectares} ha"
        } else {
          "${((entity.areaHectares * 2.47105) * 100.0).roundToInt() / 100.0} acres"
        }
      "Polygon • $area, ${length(entity.perimeterMeters)} perimeter"
    }
    EntityGeometryKind.LINE -> "Line • ${length(entity.perimeterMeters)}"
    EntityGeometryKind.POINT -> "Point • ${entity.coordinatesLabel}"
    EntityGeometryKind.NONE -> EntityGeometryKind.NONE.label
  }
}
