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

import kotlin.math.abs
import kotlin.math.ln

/** Specification for the Google Maps-style horizontal map scale bar widget. */
data class MapScaleBarSpec(val label: String, val distanceMeters: Int, val barWidthDp: Float)

/** Screens in the Ground 2.0 Mobile UI onboarding and survey workflow. */
enum class PrototypeScreen(val stepNumber: Int, val title: String, val subtitle: String) {
  SIGN_IN(stepNumber = 1, title = "Sign In", subtitle = "Google authentication entry point"),
  TERMS_OF_SERVICE(
    stepNumber = 2,
    title = "Terms of Service",
    subtitle = "Data governance & platform terms acceptance",
  ),
  DOWNLOAD_SURVEY(
    stepNumber = 3,
    title = "Download Survey",
    subtitle = "Browse shared surveys or search by name/location",
  ),
  MAIN_SURVEY(
    stepNumber = 4,
    title = "Main Survey (Map & List)",
    subtitle =
      "Survey map, map layers, location & submission bottom sheets, searchable list & drawer",
  ),
}

/** Primary view mode inside the Main Survey screen (`Map` vs `List`). */
enum class MainSurveyViewMode(val label: String) {
  MAP("Map"),
  LIST("List"),
}

/**
 * Category filter tabs inside the Main Survey searchable bottom sheet (`All`, `Places`, and `Map
 * features`).
 */
enum class ListFilterTab(val label: String) {
  ALL("All"),
  PLACES("Places"),
  ENTITIES("Map features"),
}

/** Camera lock / GPS tracking mode on the survey map (`LOCKED`, `LOCKED_3D`, or `PANNED`). */
enum class LocationLockState(val label: String) {
  LOCKED("GPS Auto-Center"),
  LOCKED_3D("GPS 3D"),
  PANNED("Panned"),
}

/**
 * Parses a place coordinate string (such as `"0.5012° S, 36.9324° E"`, `"0.4160°S, 36.9465°E"`, or
 * `"-0.5012, 36.9324"`) into a `(latitude, longitude)` pair of decimal degrees.
 */
fun parsePlaceCoordinates(coordinatesLabel: String): Pair<Double, Double>? {
  val cleaned = coordinatesLabel.trim()
  if (cleaned.isEmpty()) return null
  val parts = cleaned.split(Regex("""\s*[,;/]\s*|\s{2,}""")).filter { it.isNotBlank() }
  if (parts.size < 2) return null

  fun parseComponent(raw: String): Pair<Double, Char?>? {
    val upper = raw.trim().uppercase()
    val dirMatch = Regex("""([NSEW])\b""").find(upper)
    val dir = dirMatch?.groupValues?.get(1)?.firstOrNull()
    val numMatch = Regex("""[+-]?\d+(?:\.\d+)?""").find(upper) ?: return null
    val rawValue = numMatch.value.toDoubleOrNull() ?: return null
    val signedValue =
      when (dir) {
        'S',
        'W' -> -abs(rawValue)
        'N',
        'E' -> abs(rawValue)
        else -> rawValue
      }
    return signedValue to dir
  }

  val first = parseComponent(parts[0]) ?: return null
  val second = parseComponent(parts[1]) ?: return null
  val (lat, lng) =
    if (first.second == 'E' || first.second == 'W' || second.second == 'N' || second.second == 'S') {
      second.first to first.first
    } else {
      first.first to second.first
    }
  if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
  return lat to lng
}

/**
 * Computes an appropriate Mapbox camera target zoom level (`[2.2f, 16.8f]`) from a place's
 * geographic bounding box (`[bboxMinLng, bboxMinLat, bboxMaxLng, bboxMaxLat]`) or its
 * [categoryLabel] (e.g. a Country zooms out to ~`5.2f`, a Region/County to ~`9.5f`, a Town to
 * ~`12.5f`, a Village to ~`14.4f`, and a specific POI/Parcel to ~`16.2f`).
 */
fun inferTargetZoomForPlace(
  categoryLabel: String,
  bboxMinLng: Double? = null,
  bboxMinLat: Double? = null,
  bboxMaxLng: Double? = null,
  bboxMaxLat: Double? = null,
  fallbackZoomDelta: Float = 0.75f,
): Float {
  if (bboxMinLng != null && bboxMinLat != null && bboxMaxLng != null && bboxMaxLat != null) {
    val spanLng = abs(bboxMaxLng - bboxMinLng)
    val spanLat = abs(bboxMaxLat - bboxMinLat)
    val maxSpan = maxOf(spanLng, spanLat)
    if (maxSpan > 0.0002) {
      val computed = (ln(360.0 / maxSpan) / ln(2.0) - 0.65).toFloat()
      return computed.coerceIn(2.2f, 16.8f)
    }
  }
  val cat = categoryLabel.lowercase()
  return when {
    cat.contains("country") -> 5.2f
    cat.contains("state") ||
      cat.contains("province") ||
      (cat.contains("region") && !cat.contains("regional hub")) -> 7.6f
    cat.contains("county") || cat.contains("district") || cat.contains("national park") -> 9.6f
    cat.contains("city") || cat.contains("regional hub") || cat.contains("municipality") -> 11.8f
    cat.contains("town") || cat.contains("sub-county") -> 12.8f
    cat.contains("forest") ||
      cat.contains("reserve") ||
      cat.contains("dam") ||
      cat.contains("reservoir") ||
      cat.contains("hydrology") -> 13.6f
    cat.contains("village") ||
      cat.contains("locality") ||
      cat.contains("hamlet") ||
      cat.contains("sub-location") ||
      cat.contains("market") -> 14.4f
    cat.contains("neighborhood") ||
      cat.contains("suburb") ||
      cat.contains("river") ||
      cat.contains("crossing") ||
      cat.contains("junction") -> 15.3f
    else -> (15.3f + fallbackZoomDelta).coerceIn(2.2f, 16.8f)
  }
}

/**
 * Represents a geographic place, landmark, town, road junction, or hydrology feature returned by
 * the Mapbox Places API (`mapbox.places`) and searchable via `"Search places or map features..."`.
 */
data class SurveyPlaceItem(
  val id: String,
  val name: String,
  val categoryLabel: String,
  val regionSubtitle: String,
  val coordinatesLabel: String,
  val normalizedX: Float,
  val normalizedY: Float,
  val zoomDelta: Float = 0.75f,
  val longitude: Double = parsePlaceCoordinates(coordinatesLabel)?.second ?: 36.9512,
  val latitude: Double = parsePlaceCoordinates(coordinatesLabel)?.first ?: -0.4198,
  val bboxMinLng: Double? = null,
  val bboxMinLat: Double? = null,
  val bboxMaxLng: Double? = null,
  val bboxMaxLat: Double? = null,
  val targetZoom: Float =
    inferTargetZoomForPlace(
      categoryLabel = categoryLabel,
      bboxMinLng = bboxMinLng,
      bboxMinLat = bboxMinLat,
      bboxMaxLng = bboxMaxLng,
      bboxMaxLat = bboxMaxLat,
      fallbackZoomDelta = zoomDelta,
    ),
  val mapboxPlaceId: String = id,
  val sourceLabel: String = "Places API",
)

/**
 * Screen orientation of the simulated device in the Prototype App wrapper (`Portrait` vs
 * `Landscape`).
 */
enum class DeviceOrientation(val label: String) {
  PORTRAIT("Portrait"),
  LANDSCAPE("Landscape"),
}

/**
 * Device hardware bezel form factor selectable in the Prototype App wrapper page (`Mobile` vs
 * `Tablet`).
 */
enum class DeviceFormFactor(
  val label: String,
  val dimensionsLabel: String,
  val frameWidthDp: Int,
  val frameHeightDp: Int,
  val outerCornerRadiusDp: Int,
  val innerCornerRadiusDp: Int,
  val defaultOrientation: DeviceOrientation,
) {
  MOBILE(
    label = "Mobile",
    dimensionsLabel = "404 × 764 dp",
    frameWidthDp = 404,
    frameHeightDp = 764,
    outerCornerRadiusDp = 40,
    innerCornerRadiusDp = 32,
    defaultOrientation = DeviceOrientation.PORTRAIT,
  ),
  TABLET(
    label = "Tablet",
    dimensionsLabel = "780 × 620 dp",
    frameWidthDp = 780,
    frameHeightDp = 620,
    outerCornerRadiusDp = 28,
    innerCornerRadiusDp = 20,
    defaultOrientation = DeviceOrientation.LANDSCAPE,
  );

  /** Returns the device bezel width in `dp` for the given [orientation]. */
  fun widthForOrientation(orientation: DeviceOrientation): Int =
    when (orientation) {
      DeviceOrientation.PORTRAIT -> minOf(frameWidthDp, frameHeightDp)
      DeviceOrientation.LANDSCAPE -> maxOf(frameWidthDp, frameHeightDp)
    }

  /** Returns the device bezel height in `dp` for the given [orientation]. */
  fun heightForOrientation(orientation: DeviceOrientation): Int =
    when (orientation) {
      DeviceOrientation.PORTRAIT -> maxOf(frameWidthDp, frameHeightDp)
      DeviceOrientation.LANDSCAPE -> minOf(frameWidthDp, frameHeightDp)
    }

  /** Returns the formatted `W × H dp` label for the given [orientation]. */
  fun dimensionsLabelForOrientation(orientation: DeviceOrientation): String =
    "${widthForOrientation(orientation)} × ${heightForOrientation(orientation)} dp"
}

/** Sub-screens opened from the Hamburger Navigation Drawer inside the Main Survey UI. */
enum class MainDrawerSubView {
  NONE,
  SWITCH_SURVEYS,
  UPLOADS,
  OUTBOX,
  UPLOADED,
  MANAGE_OFFLINE_MAPS,
  SETTINGS,
}

/**
 * Filter chips displayed on the unified `Uploads` screen (`Pending`, `In progress`, `Uploaded`, and
 * `Failed`).
 */
enum class UploadStatusFilter(val label: String) {
  PENDING("Pending"),
  IN_PROGRESS("In progress"),
  UPLOADED("Uploaded"),
  FAILED("Failed"),
}

/**
 * Synchronization lifecycle state of a client mutation (`EntityMutation` or `SubmissionMutation`)
 * displayed in the unified `Uploads` navigation drawer view.
 */
enum class MutationSyncState(
  val label: String,
  val isOutbox: Boolean,
  val statusFilter: UploadStatusFilter,
) {
  UPLOADING(label = "In progress", isOutbox = true, statusFilter = UploadStatusFilter.IN_PROGRESS),
  QUEUED(label = "Pending", isOutbox = true, statusFilter = UploadStatusFilter.PENDING),
  RETRYING(label = "In progress", isOutbox = true, statusFilter = UploadStatusFilter.IN_PROGRESS),
  FAILED(label = "Failed", isOutbox = true, statusFilter = UploadStatusFilter.FAILED),
  UPLOADED(label = "Uploaded", isOutbox = false, statusFilter = UploadStatusFilter.UPLOADED),
}

/**
 * User-friendly category of mutation operation (`EntityMutation` vs `SubmissionMutation`) in the
 * unified `Uploads` view (e.g., `Form submitted`, `Form modified`, `Form deleted`).
 */
enum class MutationOperationKind(val label: String, val protoOperationLabel: String) {
  CREATE_SUBMISSION(label = "Form submitted", protoOperationLabel = "SubmissionMutation.update"),
  UPDATE_SUBMISSION(label = "Form modified", protoOperationLabel = "SubmissionMutation.update"),
  DELETE_SUBMISSION(label = "Form deleted", protoOperationLabel = "SubmissionMutation.delete"),
  CREATE_ENTITY(label = "Map feature created", protoOperationLabel = "EntityMutation.update"),
  UPDATE_ENTITY(label = "Map feature modified", protoOperationLabel = "EntityMutation.update"),
  DELETE_ENTITY(label = "Map feature deleted", protoOperationLabel = "EntityMutation.delete"),
  UPLOAD_MEDIA(label = "Photo attached", protoOperationLabel = "SubmissionMutation.update (media)"),
}

/**
 * Represents a local data mutation (`DataMutation` in `MutateDataRequest`) tracked in the unified
 * `Uploads` view.
 */
data class MutationLogItem(
  val id: String,
  val surveyId: String,
  val operationKind: MutationOperationKind,
  val title: String,
  val targetLabel: String,
  val entityId: String,
  val submissionId: String? = null,
  val actorName: String,
  val state: MutationSyncState,
  val stateDetail: String,
  val operationTimestamp: String,
  val startedTimestamp: String? = null,
  val completedTimestamp: String? = null,
  val payloadSummary: String = "",
) {
  /** Filter chip category (`Pending`, `In progress`, `Uploaded`, or `Failed`) for this item. */
  val uploadStatusFilter: UploadStatusFilter
    get() = state.statusFilter

  /** True when this mutation is not yet uploaded (`state.isOutbox == true`). */
  val isOutbox: Boolean
    get() = state.isOutbox

  /** True when this mutation has completed uploading (`state == MutationSyncState.UPLOADED`). */
  val isUploaded: Boolean
    get() = state == MutationSyncState.UPLOADED

  /** Concise `YYYY-MM-DD HH:MM` timestamp for compact list display. */
  val compactTimestamp: String
    get() =
      operationTimestamp.removeSuffix(" UTC").let { trimmed ->
        if (trimmed.length >= 16) trimmed.substring(0, 16) else trimmed
      }

  /** Formatted "time started or completed" summary string for display in mutation summaries. */
  val startedOrCompletedSummary: String
    get() =
      if (isUploaded) {
        val completed = completedTimestamp ?: operationTimestamp
        if (!startedTimestamp.isNullOrBlank()) {
          "Started: $startedTimestamp • Completed: $completed"
        } else {
          "Completed: $completed"
        }
      } else {
        if (!startedTimestamp.isNullOrBlank()) {
          "Started: $startedTimestamp"
        } else {
          "Started: Pending network connection"
        }
      }
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

/**
 * Measurement unit preference (per `docs/design/00-index.md` and `ground-android`
 * `MeasurementUnits`).
 */
enum class MeasurementUnitSystem(
  val label: String,
  val shortLabel: String,
  val areaUnit: String,
  val distanceUnit: String,
) {
  METRIC("Metric (ha, m)", "Metric", "ha", "m"),
  IMPERIAL("Imperial (acres, ft)", "Imperial", "acres", "ft"),
}

/**
 * Supported language option matching `arrays.xml` & `strings-untranslated.xml` in
 * `github.com/google/ground-android`.
 */
data class GroundLanguageOption(val code: String, val label: String)

/**
 * User settings model ported from `org.groundplatform.domain.model.settings.UserSettings` in
 * `github.com/google/ground-android`.
 */
data class UserSettings(
  val language: String = "en",
  val measurementUnits: MeasurementUnitSystem = MeasurementUnitSystem.METRIC,
  val shouldUploadPhotosOnWifiOnly: Boolean = true,
)

const val GROUND_WEBSITE_URL = "https://groundplatform.org/"

/**
 * Official language entries and entry values from `github.com/google/ground-android`
 * (`app/src/main/res/values/arrays.xml` and `strings-untranslated.xml`), plus Kiswahili (`sw`).
 */
val GROUND_LANGUAGE_OPTIONS: List<GroundLanguageOption> =
  listOf(
    GroundLanguageOption(code = "en", label = "English"),
    GroundLanguageOption(code = "fr", label = "Français"),
    GroundLanguageOption(code = "es", label = "Español"),
    GroundLanguageOption(code = "pt", label = "Português"),
    GroundLanguageOption(code = "vi", label = "Tiếng Việt"),
    GroundLanguageOption(code = "th", label = "ไทย"),
    GroundLanguageOption(code = "lo", label = "ພາສາລາວ"),
    GroundLanguageOption(code = "km", label = "ភាសាខ្មែរ"),
    GroundLanguageOption(code = "sw", label = "Kiswahili"),
  )

/** Visual color/feature theme for a survey's placeholder map thumbnail. */
enum class MapThumbnailTheme(
  val primaryTerrainHex: Long,
  val secondaryWaterHex: Long,
  val accentPolygonHex: Long,
  val badgeLabel: String,
) {
  RAINFOREST(
    primaryTerrainHex = 0xFF1B5E20,
    secondaryWaterHex = 0xFF0277BD,
    accentPolygonHex = 0xFFA5D6A7,
    badgeLabel = "CANOPY",
  ),
  SAVANNA(
    primaryTerrainHex = 0xFF6D4C41,
    secondaryWaterHex = 0xFF00838F,
    accentPolygonHex = 0xFFFFE082,
    badgeLabel = "CORRIDOR",
  ),
  HIGHLAND_AGRI(
    primaryTerrainHex = 0xFF2E7D32,
    secondaryWaterHex = 0xFF1565C0,
    accentPolygonHex = 0xFFC5E1A5,
    badgeLabel = "PARCELS",
  ),
  COASTAL_DELTA(
    primaryTerrainHex = 0xFF00695C,
    secondaryWaterHex = 0xFF0288D1,
    accentPolygonHex = 0xFF80CBC4,
    badgeLabel = "ESTUARY",
  ),
  WATERSHED(
    primaryTerrainHex = 0xFF33691E,
    secondaryWaterHex = 0xFF039BE5,
    accentPolygonHex = 0xFFE6EE9C,
    badgeLabel = "BASIN",
  ),
  PEATLAND(
    primaryTerrainHex = 0xFF3E2723,
    secondaryWaterHex = 0xFF006064,
    accentPolygonHex = 0xFF80DEEA,
    badgeLabel = "WETLAND",
  ),
}

/** Represents a survey shared with the current user on the "Download survey" screen. */
data class SurveyPreviewItem(
  val id: String,
  val title: String,
  val description: String,
  val location: String,
  val coordinatesLabel: String,
  val offlineSizeLabel: String,
  val isDownloaded: Boolean,
  val thumbnailTheme: MapThumbnailTheme,
  val entityCount: Int,
)

/**
 * Distinguishes `LayerDef.source` in `SurveyDef.map_config.layers` (per `03-maps.md`):
 * - [ENTITY_DATASET]: `entity_dataset_id` (rendered with solid polygon/marker outlines)
 */
enum class LayerSourceType(val badgeLabel: String) {
  ENTITY_DATASET("Survey Layer"),
}

/**
 * Primary map basemap mode selectable by the user in the `Layers` sheet (`Map` vs `Satellite`).
 */
enum class BasemapType(val label: String, val description: String) {
  NORMAL(label = "Map", description = "Standard vector terrain, roads & contour basemap"),
  SATELLITE(label = "Satellite", description = "High-resolution satellite & aerial canopy imagery"),
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

/** Represents a toggleable map layer (`LayerDef` in `SurveyDef.map_config.layers`). */
data class MapLayerItem(
  val id: String,
  val label: String,
  val sourceDescription: String,
  val colorHex: Long,
  val geometryTypeLabel: String,
  val isVisible: Boolean,
  val sourceType: LayerSourceType = LayerSourceType.ENTITY_DATASET,
  val formId: String? = null,
  val formTitle: String? = null,
  val fieldPath: String? = null,
  val singularItemLabel: String = "location",
  val pluralItemLabel: String = "locations",
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
  /** Formats a user-friendly domain item count for this layer (e.g. `"2 parcels"`, `"1 plot"`). */
  fun itemCountLabel(count: Int): String = "$count ${if (count == 1) singularNoun else pluralNoun}"

  fun formatCountLabel(count: Int): String =
    if (count == 1) "1 $singularItemLabel" else "$count $pluralItemLabel"
}

/**
 * Represents a polygon geometry recorded as an answer to a geometry question/field
 * (`FormGeometrySource { form_id, field_path }`) inside a form submission (`SubmissionRecord`),
 * rendered on the map with a dotted polygon outline.
 */
data class SubmissionGeometryPolygon(
  val id: String,
  val submissionId: String,
  val entityId: String,
  val layerId: String,
  val formId: String,
  val formTitle: String,
  val fieldPath: String,
  val questionLabel: String,
  val shortMapBadge: String,
  val collectorName: String,
  val timestamp: String,
  val areaHectares: Double,
  val vertexCount: Int,
  val normalizedX: Float,
  val normalizedY: Float,
  val widthFraction: Float,
  val heightFraction: Float,
  val colorHex: Long,
)

/** Question-and-answer pair inside a recorded `SubmissionRecord`. */
data class SubmissionFieldEntry(
  val questionName: String,
  val questionLabel: String,
  val answerValue: String,
)

/**
 * Synchronization state of a Geospatial Entity (`EntityRecord`) or Form Submission
 * (`SubmissionRecord`) between local offline storage and the Ground cloud server.
 */
enum class SyncStatus(val label: String, val description: String) {
  UPLOADING(label = "Uploading", description = "Uploading local changes to server"),
  SYNCED(label = "Synced", description = "Synchronized with cloud server"),
  FAILED(label = "Failed", description = "Upload failed — tap to retry");

  /**
   * Returns the next [SyncStatus] in the cycle (`UPLOADING` -> `SYNCED` -> `FAILED` ->
   * `UPLOADING`).
   */
  fun next(): SyncStatus =
    when (this) {
      UPLOADING -> SYNCED
      SYNCED -> FAILED
      FAILED -> UPLOADING
    }
}

/**
 * Derives the aggregate [SyncStatus] of a [GeospatialEntityItem] from its recorded [submissions],
 * prioritizing [SyncStatus.FAILED], then [SyncStatus.UPLOADING], and falling back to [fallback].
 */
fun deriveEntitySyncStatus(
  submissions: List<SubmissionPreviewItem>,
  fallback: SyncStatus = SyncStatus.SYNCED,
): SyncStatus =
  when {
    submissions.any { it.syncStatus == SyncStatus.FAILED } -> SyncStatus.FAILED
    submissions.any { it.syncStatus == SyncStatus.UPLOADING } -> SyncStatus.UPLOADING
    else -> fallback
  }

/**
 * Represents a completed form submission (`SubmissionRecord`), either linked to a Geospatial Entity
 * (`entityId.isNotBlank()`) or recorded as a standalone submission without an attached entity
 * (`entityId.isEmpty()`).
 */
data class SubmissionPreviewItem(
  val id: String,
  val entityId: String = "",
  val entityLabel: String = "",
  val formId: String,
  val formTitle: String,
  val formVersion: String,
  val collectorName: String,
  val collectorEmail: String,
  val timestamp: String,
  val fields: List<SubmissionFieldEntry>,
  val targetTypeLabel: String = "Location",
  val syncStatus: SyncStatus = SyncStatus.SYNCED,
  val coordinatesLabel: String = "",
  val normalizedX: Float? = null,
  val normalizedY: Float? = null,
) {
  /** True when this submission is linked to a persistent Geospatial Entity (`entityId != ""`). */
  val hasAttachedEntity: Boolean
    get() = entityId.isNotBlank()

  val isUploading: Boolean
    get() = syncStatus == SyncStatus.UPLOADING

  val isSynced: Boolean
    get() = syncStatus == SyncStatus.SYNCED

  val isFailed: Boolean
    get() = syncStatus == SyncStatus.FAILED
}

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

/** Category of a feature rendered on the survey map that participates in zoomed-out clustering. */
enum class MapFeatureKind {
  ENTITY,
  SUBMISSION_GEOMETRY,
}

/**
 * Unified map feature representation (either a [GeospatialEntityItem] or a
 * [SubmissionGeometryPolygon]) used for spatial clustering when the map is zoomed out.
 */
data class MapClusterFeatureItem(
  val id: String,
  val kind: MapFeatureKind,
  val label: String,
  val markerSymbol: String,
  val colorHex: Long,
  val colorCss: String,
  val normalizedX: Float,
  val normalizedY: Float,
  val entityId: String = "",
  val submissionId: String? = null,
) {
  /** True when this map feature has a non-blank `marker-symbol` (`"○"`, `"◐"`, `"✓"`, etc.). */
  val hasMarkerSymbol: Boolean
    get() = markerSymbol.isNotBlank()
}

/**
 * Represents a group of clustered map features sharing the same `marker-symbol` (including the `"no
 * marker symbol"` group where [markerSymbol] is `""`) inside a [MapFeatureCluster] balloon.
 */
data class ClusterMarkerSymbolGroup(
  val markerSymbol: String,
  val count: Int,
  val colorHex: Long,
  val colorCss: String,
  val statusLabel: String,
  val features: List<MapClusterFeatureItem>,
) {
  /** True when this group represents map features that have no marker symbol (`""`). */
  val isNoSymbolGroup: Boolean
    get() = markerSymbol.isBlank()

  /**
   * Formatted segment displayed in the cluster balloon (e.g. `"✓ 2"`, `"◐ 2"`, `"○ 1"`, or `"No
   * symbol 5"`).
   */
  val balloonSegmentLabel: String
    get() = if (isNoSymbolGroup) "No symbol $count" else "$markerSymbol $count"
}

/**
 * Groups [features] inside a cluster by `marker-symbol` (including `""` for features with no marker
 * symbol as one group), returning ordered [ClusterMarkerSymbolGroup] entries with their counts.
 */
fun groupClusterFeaturesByMarkerSymbol(
  features: List<MapClusterFeatureItem>
): List<ClusterMarkerSymbolGroup> {
  if (features.isEmpty()) return emptyList()
  val grouped = features.groupBy { it.markerSymbol.trim() }
  val canonicalOrder = listOf("✓", "◐", "○")
  val customSymbols = grouped.keys.filter { it.isNotEmpty() && it !in canonicalOrder }.sorted()
  val orderedKeys = buildList {
    canonicalOrder.forEach { sym -> if (sym in grouped) add(sym) }
    addAll(customSymbols)
    if ("" in grouped) add("")
  }

  return orderedKeys.map { symbol ->
    val members = grouped[symbol].orEmpty()
    val (defaultColorHex, statusLabel) =
      when (symbol) {
        "✓" -> 0xFF1E8E3EL to "Completed"
        "◐" -> 0xFFF9AB00L to "In progress"
        "○" -> 0xFFE65100L to "Pending"
        "" -> (members.firstOrNull()?.colorHex ?: 0xFF66BB6AL) to "No marker symbol"
        else -> (members.firstOrNull()?.colorHex ?: 0xFF2E7D32L) to "Symbol $symbol"
      }
    ClusterMarkerSymbolGroup(
      markerSymbol = symbol,
      count = members.size,
      colorHex = defaultColorHex,
      colorCss = formatHexColorCss(defaultColorHex),
      statusLabel = statusLabel,
      features = members,
    )
  }
}

/**
 * Spatial cluster of map features formed when the map is zoomed out, grouping member features by
 * `marker-symbol` (including no marker symbol as one group) and exposing the count of each for the
 * cluster balloon.
 */
data class MapFeatureCluster(
  val id: String,
  val normalizedX: Float,
  val normalizedY: Float,
  val features: List<MapClusterFeatureItem>,
  val symbolGroups: List<ClusterMarkerSymbolGroup>,
) {
  /** Total number of map features in this cluster across all marker symbol groups. */
  val totalCount: Int
    get() = features.size

  /** Map feature (`MapFeatureKind.ENTITY`) features in this cluster. */
  val entityFeatures: List<MapClusterFeatureItem>
    get() = features.filter { it.kind == MapFeatureKind.ENTITY }

  /** Form submission geometry (`MapFeatureKind.SUBMISSION_GEOMETRY`) features in this cluster. */
  val submissionGeometryFeatures: List<MapClusterFeatureItem>
    get() = features.filter { it.kind == MapFeatureKind.SUBMISSION_GEOMETRY }

  /** Count of map features (`MapFeatureKind.ENTITY`) in this cluster. */
  val siteCount: Int
    get() = entityFeatures.size

  /** Count of form submission geometries (`MapFeatureKind.SUBMISSION_GEOMETRY`) in this cluster. */
  val submissionGeometryCount: Int
    get() = submissionGeometryFeatures.size

  /** Marker-symbol groups for **map features** (`MapFeatureKind.ENTITY`) only. */
  val siteSymbolGroups: List<ClusterMarkerSymbolGroup>
    get() = groupClusterFeaturesByMarkerSymbol(entityFeatures)

  /** Summary of map feature workflow states in this cluster (e.g. `"✓ 2 • ◐ 2 • ○ 1"`). */
  val siteStatesSummaryLabel: String
    get() = siteSymbolGroups.joinToString(" • ") { it.balloonSegmentLabel }

  /**
   * Map from `marker-symbol` (with `""` representing the no-marker-symbol group) to the count of
   * features in that group within this cluster.
   */
  val countsByMarkerSymbol: Map<String, Int>
    get() = symbolGroups.associate { it.markerSymbol to it.count }

  /** Count of features in this cluster that have no marker symbol (`markerSymbol == ""`). */
  val noMarkerSymbolCount: Int
    get() = countsByMarkerSymbol[""] ?: 0

  /** Returns the count of features in this cluster for [symbol] (`""` or `null` for no symbol). */
  fun countForSymbol(symbol: String?): Int = countsByMarkerSymbol[symbol?.trim().orEmpty()] ?: 0

  /**
   * Summary string of all marker symbol groups and their counts shown in the cluster balloon (e.g.
   * `"✓ 2 • ◐ 2 • ○ 1 • No symbol 5"`).
   */
  val balloonSummaryLabel: String
    get() = symbolGroups.joinToString(" • ") { it.balloonSegmentLabel }
}

/** Represents a hierarchical Form (`FormDef` + `FormLaunchConfig`) in the active survey. */
data class FormPreviewItem(
  val id: String,
  val title: String,
  val description: String,
  val version: String,
  val targetDatasetId: String,
  val targetDatasetName: String,
  val questionCount: Int,
  val ctaLabel: String,
  val requiresEntity: Boolean = targetDatasetId.isNotBlank(),
) {

  /**
   * Singular domain label for the target dataset required by this form (e.g. `"Coffee Parcel"`).
   */
  val targetSingularTypeLabel: String
    get() =
      when (targetDatasetId) {
        "coffee_parcels" -> "Coffee Parcel"
        "shade_monitoring_plots" -> "Shade Tree Monitoring Plot"
        "washing_stations" -> "Cooperative Washing Station"
        else -> targetDatasetName.removeSuffix("s").ifBlank { "Location" }
      }
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

/** Grouping of [SubmissionPreviewItem]s under a [FormPreviewItem] in submission lists. */
data class FormSubmissionsGroup(
  val form: FormPreviewItem,
  val submissions: List<SubmissionPreviewItem>,
) {
  val formTitle: String
    get() = form.title
}

/**
 * Active PDF export & app-share sheet state for either a Geospatial Entity or a Form Submission.
 */
data class SharedPdfSheetState(
  val targetId: String,
  val title: String,
  val subtitle: String,
  val pdfFileName: String,
  val targetKindLabel: String,
)

/**
 * Distinguishes whether straight-line navigation is targeting a Geospatial Entity, a Submission, or
 * a searched Place.
 */
enum class NavigationTargetKind(val badgeLabel: String) {
  ENTITY("ENTITY"),
  SUBMISSION("SUBMISSION"),
  PLACE("PLACE"),
}

/**
 * Computed straight-line geodesic vector from the collector's current GPS position
 * (`userGpsNormalizedX`, `userGpsNormalizedY`) to a target entity or submission.
 */
data class StraightLineVector(
  val fromNormalizedX: Float,
  val fromNormalizedY: Float,
  val toNormalizedX: Float,
  val toNormalizedY: Float,
  val distanceMeters: Int,
  val formattedDistance: String,
  val bearingDegrees: Int,
  val cardinalDirection: String,
  val estimatedWalkMinutes: Int,
  val hasArrived: Boolean,
) {
  val isArrived: Boolean
    get() = hasArrived

  val formattedBearing: String
    get() = "${bearingDegrees}° $cardinalDirection"
}

/**
 * Active straight-line wayfinding navigation state guiding the collector from their current GPS
 * position to either a [GeospatialEntityItem] or a [SubmissionPreviewItem].
 */
data class StraightLineNavigationState(
  val targetKind: NavigationTargetKind,
  val targetId: String,
  val entityId: String,
  val submissionId: String?,
  val geometryId: String?,
  val targetTitle: String,
  val targetSubtitle: String,
  val targetCoordinatesLabel: String,
  val colorHex: Long,
  val vector: StraightLineVector,
) {
  val formattedDistance: String
    get() = vector.formattedDistance
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

/** Distinguishes how the user navigated to the "Download surveys" screen. */
enum class DownloadSurveyEntryOrigin {
  /** Shown after accepting the Terms of Service during onboarding (Back prompts to sign out). */
  AFTER_TOS,
  /** Accessed from the Downloaded Surveys list in the Main Survey UI (Back returns to the list). */
  SURVEY_LIST,
}
