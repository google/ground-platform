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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.RecordInstance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import org.groundplatform.v2.core.forms.model.EntityState
import kotlin.math.pow
import kotlin.math.roundToInt
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm

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
    subtitle = "Survey map, layers, location & submission bottom sheets, searchable list & drawer",
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

/**
 * Camera lock / GPS tracking mode on the survey map (`LOCKED`, `LOCKED_3D`, or `PANNED`).
 */
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
        'W' -> -kotlin.math.abs(rawValue)
        'N',
        'E' -> kotlin.math.abs(rawValue)
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
internal fun inferTargetZoomForPlace(
  categoryLabel: String,
  bboxMinLng: Double? = null,
  bboxMinLat: Double? = null,
  bboxMaxLng: Double? = null,
  bboxMaxLat: Double? = null,
  fallbackZoomDelta: Float = 0.75f,
): Float {
  if (bboxMinLng != null && bboxMinLat != null && bboxMaxLng != null && bboxMaxLat != null) {
    val spanLng = kotlin.math.abs(bboxMaxLng - bboxMinLng)
    val spanLat = kotlin.math.abs(bboxMaxLat - bboxMinLat)
    val maxSpan = maxOf(spanLng, spanLat)
    if (maxSpan > 0.0002) {
      val computed = (kotlin.math.ln(360.0 / maxSpan) / kotlin.math.ln(2.0) - 0.65).toFloat()
      return computed.coerceIn(2.2f, 16.8f)
    }
  }
  val cat = categoryLabel.lowercase()
  return when {
    cat.contains("country") -> 5.2f
    cat.contains("state") || cat.contains("province") || (cat.contains("region") && !cat.contains("regional hub")) -> 7.6f
    cat.contains("county") || cat.contains("district") || cat.contains("national park") -> 9.6f
    cat.contains("city") || cat.contains("regional hub") || cat.contains("municipality") -> 11.8f
    cat.contains("town") || cat.contains("sub-county") -> 12.8f
    cat.contains("forest") || cat.contains("reserve") || cat.contains("dam") || cat.contains("reservoir") || cat.contains("hydrology") -> 13.6f
    cat.contains("village") || cat.contains("locality") || cat.contains("hamlet") || cat.contains("sub-location") || cat.contains("market") -> 14.4f
    cat.contains("neighborhood") || cat.contains("suburb") || cat.contains("river") || cat.contains("crossing") || cat.contains("junction") -> 15.3f
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
 *
 * Mutations are listed in reverse chronological order ([operationTimestamp] descending) and expose:
 * - [state]: Current synchronization state (`In progress`, `Pending`, `Failed`, or `Uploaded`).
 * - [operationTimestamp]: Time when the mutation operation was recorded in the field.
 * - [startedTimestamp]: Time when upload/synchronization started (if started).
 * - [completedTimestamp]: Time when upload/synchronization completed (for `UPLOADED` mutations).
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
 * - [FORM_GEOMETRY]: `FormGeometrySource { form_id, field_path }` (submission geometries from form
 *   geometry questions/fields, rendered with dotted polygon outlines)
 */
enum class LayerSourceType(val badgeLabel: String) {
  ENTITY_DATASET("Survey Layer"),
  FORM_GEOMETRY("Submission Geometry"),
}

/**
 * Primary map basemap mode selectable by the user in the `Layers` dialog (`Map` vs `Satellite`).
 */
enum class BasemapType(val label: String, val description: String) {
  NORMAL(label = "Map", description = "Standard vector terrain, roads & contour basemap"),
  SATELLITE(label = "Satellite", description = "High-resolution satellite & aerial canopy imagery"),
}

/** Offline basemap rendering style toggleable in the `Layers` dialog. */
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
  val isDottedOutline: Boolean = sourceType == LayerSourceType.FORM_GEOMETRY,
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

  /**
   * Marker-symbol groups for **map features** (`MapFeatureKind.ENTITY`) only.
   */
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
 * Grouping of [GeospatialEntityItem]s under a map feature dataset ([MapLayerItem]) in the
 * Main Survey `List` view (`Map features` category).
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
 * Grouping of submission [MapLayerItem]s and [SubmissionPreviewItem]s under a [FormPreviewItem] in
 * the `Layers` dialog, headed by the form's title (`form.title`).
 */
data class FormSubmissionLayersGroup(
  val form: FormPreviewItem,
  val layers: List<MapLayerItem>,
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

/**
 * State controller for the Ground 2.0 Mobile UI Prototype workbench (`devtools/prototypeApp`).
 *
 * Manages both the onboarding screens (`Sign In` -> `Terms of Service` -> `Download
 * survey`) and the **Main Survey UI** (`Map` view with geospatial entities, `Layers` filter
 * popover, `1:1` and `1:N` entity bottom sheets, `List` view with searchable forms, entities, and
 * submissions, and the Hamburger Navigation Drawer).
 */
class PrototypeAppState(
  initialScreen: PrototypeScreen = PrototypeScreen.SIGN_IN,
  initialSurveys: List<SurveyPreviewItem> = defaultSampleSurveys(),
) {
  var currentScreen by mutableStateOf(initialScreen)
    private set

  var isDarkTheme by mutableStateOf(false)
    private set

  /** Active hardware preview bezel form factor in the wrapper workbench (`Mobile` vs `Tablet`). */
  var deviceFormFactor by mutableStateOf(DeviceFormFactor.MOBILE)
    private set

  /** Active screen orientation of the hardware preview bezel (`Portrait` vs `Landscape`). */
  var deviceOrientation by mutableStateOf(deviceFormFactor.defaultOrientation)
    private set

  /** True when the active device is rotated away from its form factor's default orientation. */
  val isDeviceRotated: Boolean
    get() = deviceOrientation != deviceFormFactor.defaultOrientation

  /**
   * Effective width in `dp` of the hardware bezel for the current [deviceFormFactor] and
   * [deviceOrientation].
   */
  val effectiveFrameWidthDp: Int
    get() = deviceFormFactor.widthForOrientation(deviceOrientation)

  /**
   * Effective height in `dp` of the hardware bezel for the current [deviceFormFactor] and
   * [deviceOrientation].
   */
  val effectiveFrameHeightDp: Int
    get() = deviceFormFactor.heightForOrientation(deviceOrientation)

  /** Formatted `W × H dp` label for the current [deviceFormFactor] and [deviceOrientation]. */
  val effectiveDimensionsLabel: String
    get() = deviceFormFactor.dimensionsLabelForOrientation(deviceOrientation)

  var isSignedIn by mutableStateOf(false)
    private set

  var signedInUserName by mutableStateOf("Maya Lin")
    private set

  var signedInUserEmail by mutableStateOf("maya.lin@groundplatform.org")
    private set

  /** Tracks whether the Download surveys screen was reached after ToS or from the Survey list. */
  var downloadSurveyEntryOrigin by mutableStateOf(DownloadSurveyEntryOrigin.AFTER_TOS)
    private set

  /** True when the sign-out confirmation prompt is open on the Download surveys screen. */
  var isDownloadSurveySignOutPromptOpen by mutableStateOf(false)
    private set

  /** True when the Download surveys screen was opened from the Downloaded Surveys list. */
  val isDownloadSurveyAccessedFromSurveyList: Boolean
    get() = downloadSurveyEntryOrigin == DownloadSurveyEntryOrigin.SURVEY_LIST

  var signedInOrganization by mutableStateOf("Open Foris • East Africa Field Team")
    private set

  var termsCheckboxChecked by mutableStateOf(true)
    private set

  var hasAcceptedTerms by mutableStateOf(false)
    private set

  var searchQuery by mutableStateOf("")
    private set

  var surveys by mutableStateOf(initialSurveys)
    private set

  var activeSurveyNotice by mutableStateOf<String?>(null)
    private set

  // --- Main Survey UI State ---
  var activeSurveyId by mutableStateOf("survey-kenya-coffee")
    private set

  var mainViewMode by mutableStateOf(MainSurveyViewMode.MAP)
    private set

  var isDrawerOpen by mutableStateOf(false)
    private set

  var activeDrawerSubView by mutableStateOf(MainDrawerSubView.NONE)
    private set

  var isLayersSheetOpen by mutableStateOf(false)
    private set

  var selectedBasemapType by mutableStateOf(BasemapType.SATELLITE)
    private set

  var isOfflineBasemapVisible by mutableStateOf(true)
    private set

  var offlineBasemapStyle by mutableStateOf(OfflineBasemapStyle.SATELLITE_HYBRID)
    private set

  var mapLayers by mutableStateOf(defaultMapLayers())
    private set

  var submissionGeometries by mutableStateOf(defaultSubmissionGeometries())
    private set

  var forms by mutableStateOf(defaultForms())
    private set

  var entities by mutableStateOf(defaultGeospatialEntities())
    internal set

  /** Geographic places and landmarks searchable via Place search in the active survey region. */
  var places by mutableStateOf(defaultSurveyPlaces())
    private set

  /** ID of the currently selected [SurveyPlaceItem] from Place search (if any). */
  var selectedPlaceId by mutableStateOf<String?>(null)
    private set

  /**
   * Standalone form submissions recorded without an attached Geospatial Entity (`entityId == ""`).
   */
  var standaloneSubmissions by mutableStateOf(defaultStandaloneSubmissions())
    private set

  var selectedEntityId by mutableStateOf<String?>(null)
    private set

  /**
   * Whether the Entity Bottom Sheet is expanded (`true`) to show full properties/submissions or
   * collapsed (`false`, default) into a compact single-row peek bar at the bottom of the map so it
   * does not obscure the map viewport.
   */
  var isEntityBottomSheetExpanded by mutableStateOf(false)
    private set

  var selectedSubmissionId by mutableStateOf<String?>(null)
    private set

  var listSearchQuery by mutableStateOf("")
    private set

  var listFilterTab by mutableStateOf(ListFilterTab.ALL)
    private set

  var offlineTilePackages by mutableStateOf(defaultOfflineTilePackages())
    private set

  /** Local mutation log (`DataMutation` items across all upload states) for the active survey. */
  var mutations by mutableStateOf(defaultMutations())
    private set

  /**
   * Active status filter chip on the unified `Uploads` screen (`Pending`, `In progress`,
   * `Uploaded`, or `Failed`), or `null` when all uploads are shown.
   */
  var selectedUploadStatusFilter by mutableStateOf<UploadStatusFilter?>(null)
    private set

  /**
   * All local mutations sorted in strict reverse chronological order
   * ([MutationLogItem.operationTimestamp] descending).
   */
  val allMutationsSorted: List<MutationLogItem>
    get() =
      mutations.sortedWith(
        compareByDescending<MutationLogItem> { it.operationTimestamp }
          .thenByDescending { it.completedTimestamp ?: it.startedTimestamp ?: "" }
          .thenByDescending { it.id }
      )

  /**
   * Mutations displayed in the unified `Uploads` screen filtered by [selectedUploadStatusFilter]
   * (or all mutations when [selectedUploadStatusFilter] is `null`), in reverse chronological order.
   */
  val filteredUploadMutations: List<MutationLogItem>
    get() {
      val filter = selectedUploadStatusFilter ?: return allMutationsSorted
      return allMutationsSorted.filter { it.uploadStatusFilter == filter }
    }

  /** Returns the total count of mutations matching [filter] on the `Uploads` screen. */
  fun uploadCountForFilter(filter: UploadStatusFilter): Int = mutations.count {
    it.uploadStatusFilter == filter
  }

  /**
   * Pending, in-progress, or failed mutations (`isOutbox == true`), listed in strict reverse
   * chronological order ([MutationLogItem.operationTimestamp] descending).
   */
  val outboxMutations: List<MutationLogItem>
    get() =
      mutations
        .filter { it.isOutbox }
        .sortedWith(
          compareByDescending<MutationLogItem> { it.operationTimestamp }
            .thenByDescending { it.startedTimestamp ?: "" }
            .thenByDescending { it.id }
        )

  /**
   * Completed mutations (`isUploaded == true`), listed in strict reverse chronological order
   * ([MutationLogItem.operationTimestamp] descending).
   */
  val uploadedMutations: List<MutationLogItem>
    get() =
      mutations
        .filter { it.isUploaded }
        .sortedWith(
          compareByDescending<MutationLogItem> { it.operationTimestamp }
            .thenByDescending { it.completedTimestamp ?: "" }
            .thenByDescending { it.id }
        )

  /** Total count of pending/active/failed mutations not yet uploaded. */
  val outboxMutationCount: Int
    get() = outboxMutations.size

  /** Total count of completed mutations in the `Uploaded` state. */
  val uploadedMutationCount: Int
    get() = uploadedMutations.size

  var unitSystem by mutableStateOf(MeasurementUnitSystem.METRIC)
    private set

  var selectedLanguageCode by mutableStateOf("en")
    private set

  var selectedLanguageLocale by mutableStateOf("en (English)")
    private set

  var shouldUploadPhotosOnWifiOnly by mutableStateOf(false)
    private set

  var visitedWebsiteUrl by mutableStateOf<String?>(null)
    private set

  var mediaCacheCleared by mutableStateOf(false)
    private set

  /** Display label of the currently selected language (e.g. `"English"`, `"Français"`). */
  val selectedLanguageDisplayName: String
    get() =
      GROUND_LANGUAGE_OPTIONS.firstOrNull { it.code == selectedLanguageCode }?.label ?: "English"

  /** Snapshot of user settings matching `UserSettings` in `github.com/google/ground-android`. */
  val userSettings: UserSettings
    get() =
      UserSettings(
        language = selectedLanguageCode,
        measurementUnits = unitSystem,
        shouldUploadPhotosOnWifiOnly = shouldUploadPhotosOnWifiOnly,
      )

  // --- User GPS Location & Auto-Centering Map Camera State ---
  /** Normalized world X coordinate `[0, 1]` of the collector's current GPS location. */
  var userGpsNormalizedX by mutableStateOf(0.50f)
    private set

  /** Normalized world Y coordinate `[0, 1]` of the collector's current GPS location. */
  var userGpsNormalizedY by mutableStateOf(0.50f)
    private set

  /** Formatted GPS coordinates & accuracy badge for the user's current field position. */
  var userGpsCoordinatesLabel by mutableStateOf("-0.4198°, 36.9512° (±3.2m GPS)")
    private set

  /**
   * Whether the map camera automatically pans to keep the user's current GPS location at the center
   * of the screen (`true` by default). Becomes `false` when the user drags/pans the map or selects
   * a place, which sets [locationLockState] to [LocationLockState.PANNED] and reveals the Google
   * Maps-style `"Recenter"` button.
   */
  var isCameraFollowingUser by mutableStateOf(true)
    private set

  /** Current map camera lock state (`LOCKED`, `LOCKED_3D`, or `PANNED`). */
  var locationLockState by mutableStateOf(LocationLockState.LOCKED)
    private set

  private var lastSelectedPlace: SurveyPlaceItem? by mutableStateOf(null)

  private fun activeSurveyBaseLngLat(): Pair<Double, Double> =
    when (activeSurveyId) {
      "survey-amazon-canopy" -> -62.2159 to -3.4653
      "survey-serengeti-corridor" -> 34.8328 to -2.3333
      "survey-mekong-mangroves" -> 106.3422 to 9.8249
      "survey-andean-watershed" -> -71.9675 to -13.5320
      "survey-borneo-peatland" -> 113.9213 to -2.2136
      else -> 36.9512 to -0.4198
    }

  /** Normalized horizontal viewport offset applied when the user manually drags/pans the map. */
  var mapPanOffsetX by mutableStateOf(0f)
    private set

  /** Normalized vertical viewport offset applied when the user manually drags/pans the map. */
  var mapPanOffsetY by mutableStateOf(0f)
    private set

  /** Zoom delta relative to the active survey's default Mapbox zoom level (`[-5.0f, +3.7f]`). */
  var mapZoomDelta by mutableStateOf(0f)
    private set

  /** Formatted current Mapbox zoom level badge (e.g. `"15.3z"`). */
  val effectiveMapZoomLabel: String
    get() {
      val rawZoom = (15.3f + mapZoomDelta).coerceIn(10.0f, 19.0f)
      val tenths = (rawZoom * 10f + 0.5f).toInt()
      return "${tenths / 10}.${tenths % 10}z"
    }

  /**
   * Computes a Google Maps-style horizontal scale bar specification (`label`, `distanceMeters`,
   * `barWidthDp`) based on the active survey's latitude and current Mapbox zoom level.
   */
  val mapScaleBarSpec: MapScaleBarSpec
    get() {
      val (baseZoom, latitudeDeg) =
        when (activeSurveyId) {
          "survey-amazon-bio" -> 14.8f to -3.1190
          "survey-tanzania-water" -> 15.1f to -6.1659
          "survey-california-fire" -> 14.9f to 38.5449
          else -> 15.3f to -0.4198
        }
      val effectiveZoom = (baseZoom + mapZoomDelta).coerceIn(10.0f, 19.0f).toDouble()
      val metersPerDp =
        (40075016.686 * cos(latitudeDeg * PI / 180.0)) / (512.0 * 2.0.pow(effectiveZoom))
      val candidateMeters =
        listOf(5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000)
      val targetWidthDp = 72.0
      val chosenMeters =
        candidateMeters.minByOrNull { dist ->
          val widthDp = dist / metersPerDp
          val outOfRangePenalty =
            when {
              widthDp < 44.0 -> (44.0 - widthDp) * 4.0
              widthDp > 112.0 -> (widthDp - 112.0) * 4.0
              else -> 0.0
            }
          abs(widthDp - targetWidthDp) + outOfRangePenalty
        } ?: 100
      val barWidthDp = (chosenMeters / metersPerDp).toFloat().coerceIn(44f, 114f)
      val label =
        if (chosenMeters >= 1000) {
          "${chosenMeters / 1000} km"
        } else {
          "$chosenMeters m"
        }
      return MapScaleBarSpec(label = label, distanceMeters = chosenMeters, barWidthDp = barWidthDp)
    }

  /**
   * Total horizontal world-to-viewport shift (`(0.50f - userGpsNormalizedX) + mapPanOffsetX`).
   * Ensures `(userGpsNormalizedX, userGpsNormalizedY)` is always centered at `(0.50f, 0.50f)`
   * whenever [isCameraFollowingUser] is `true` (`mapPanOffsetX == 0f`).
   */
  val mapWorldToScreenShiftX: Float
    get() = (0.50f - userGpsNormalizedX) + mapPanOffsetX

  /**
   * Total vertical world-to-viewport shift (`(0.50f - userGpsNormalizedY) + mapPanOffsetY`).
   * Ensures `(userGpsNormalizedX, userGpsNormalizedY)` is always centered at `(0.50f, 0.50f)`
   * whenever [isCameraFollowingUser] is `true` (`mapPanOffsetY == 0f`).
   */
  val mapWorldToScreenShiftY: Float
    get() = (0.50f - userGpsNormalizedY) + mapPanOffsetY

  /** Normalized screen X coordinate of the user's GPS blue dot (`0.50f` when centered). */
  val userScreenNormalizedX: Float
    get() = 0.50f + mapPanOffsetX

  /** Normalized screen Y coordinate of the user's GPS blue dot (`0.50f` when centered). */
  val userScreenNormalizedY: Float
    get() = 0.50f + mapPanOffsetY

  /** Number of GNSS (GPS/Galileo/GLONASS) satellites currently locked by the device receiver. */
  var gnssSatelliteCount by mutableStateOf(18)
    private set

  /** Current horizontal GNSS accuracy in meters (`±2.1 m`). */
  var gnssAccuracyMeters by mutableStateOf(2.1)
    private set

  /** Formatted horizontal GNSS accuracy (`±2.1 m` or `±6.8 ft`). */
  val gnssAccuracyFormatted: String
    get() =
      if (unitSystem == MeasurementUnitSystem.METRIC) {
        "±$gnssAccuracyMeters m"
      } else {
        val feet = ((gnssAccuracyMeters * 3.28084) * 10.0).toInt() / 10.0
        "±$feet ft"
      }

  /** Formatted GPS accuracy badge displayed on the chip over the map (`±2.1 m` or `±6.8 ft`). */
  val gnssStatusChipLabel: String
    get() = gnssAccuracyFormatted

  /**
   * Entity ID whose scannable GeoID QR code modal dialog is currently open (`null` when closed).
   */
  var activeQrCodeEntityId by mutableStateOf<String?>(null)
    private set

  /** The [GeospatialEntityItem] whose QR code modal dialog is currently open (if any). */
  val activeQrCodeEntity: GeospatialEntityItem?
    get() = activeQrCodeEntityId?.let { id -> entities.firstOrNull { it.id == id } }

  /**
   * Active PDF export & app-sharing modal state for an entity or submission (`null` when closed).
   */
  var activeSharedPdfSheet by mutableStateOf<SharedPdfSheetState?>(null)
    private set

  // --- Straight-Line Wayfinding Navigation State (Entities & Submissions) ---
  /**
   * Target kind (`ENTITY` or `SUBMISSION`) for active straight-line navigation (`null` when
   * inactive).
   */
  var navigationTargetKind by mutableStateOf<NavigationTargetKind?>(null)
    private set

  /**
   * Target ID (`entityId` or `submissionId`) for active straight-line navigation (`null` when
   * inactive).
   */
  var navigationTargetId by mutableStateOf<String?>(null)
    private set

  /**
   * Computes a [StraightLineVector] from the collector's current GPS position
   * (`userGpsNormalizedX`, `userGpsNormalizedY`) to `(targetNormalizedX, targetNormalizedY)` using
   * the active survey's geographic scale and the user's [unitSystem] (`METRIC` vs `IMPERIAL`).
   */
  fun computeStraightLineVector(
    targetNormalizedX: Float,
    targetNormalizedY: Float,
  ): StraightLineVector {
    val eastMeters = (targetNormalizedX - userGpsNormalizedX) * 1558.48
    val northMeters = (userGpsNormalizedY - targetNormalizedY) * 2000.38
    val distMeters = hypot(eastMeters, northMeters).roundToInt().coerceAtLeast(0)
    val rawBearingDeg =
      if (distMeters == 0) {
        0.0
      } else {
        atan2(eastMeters, northMeters) * (180.0 / PI)
      }
    val bearingDeg = (((rawBearingDeg.roundToInt()) % 360) + 360) % 360
    val cardinalDirections = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val cardinalIdx = (((bearingDeg + 22.5) / 45.0).toInt()) % 8
    val cardinal = cardinalDirections[cardinalIdx]
    val formattedDist =
      if (unitSystem == MeasurementUnitSystem.METRIC) {
        if (distMeters >= 1000) {
          val km = ((distMeters / 100.0).roundToInt()) / 10.0
          "$km km"
        } else {
          "$distMeters m"
        }
      } else {
        val feet = (distMeters * 3.28084).roundToInt()
        if (feet >= 5280) {
          val miles = ((feet / 528.0).roundToInt()) / 10.0
          "$miles mi"
        } else {
          "$feet ft"
        }
      }
    val walkMinutes =
      if (distMeters <= 8) {
        0
      } else {
        (distMeters / 65.0).roundToInt().coerceAtLeast(1)
      }
    return StraightLineVector(
      fromNormalizedX = userGpsNormalizedX,
      fromNormalizedY = userGpsNormalizedY,
      toNormalizedX = targetNormalizedX,
      toNormalizedY = targetNormalizedY,
      distanceMeters = distMeters,
      formattedDistance = formattedDist,
      bearingDegrees = bearingDeg,
      cardinalDirection = cardinal,
      estimatedWalkMinutes = walkMinutes,
      hasArrived = distMeters <= 8,
    )
  }

  /**
   * Resolves the normalized map coordinates `(normalizedX, normalizedY)` and optional
   * [SubmissionGeometryPolygon] for any [SubmissionPreviewItem] (both entity-attached and
   * standalone submissions without an attached entity).
   */
  fun resolveSubmissionTargetGeometry(
    submissionId: String
  ): Triple<Float, Float, SubmissionGeometryPolygon?>? {
    val sub = allSubmissions.firstOrNull { it.id == submissionId } ?: return null
    val geom = submissionGeometries.firstOrNull { it.submissionId == sub.id }
    if (geom != null) {
      return Triple(geom.normalizedX, geom.normalizedY, geom)
    }
    if (sub.normalizedX != null && sub.normalizedY != null) {
      return Triple(
        sub.normalizedX.coerceIn(0.08f, 0.92f),
        sub.normalizedY.coerceIn(0.08f, 0.92f),
        null,
      )
    }
    val parentEntity = entities.firstOrNull { it.id == sub.entityId } ?: return null
    val subIndex = parentEntity.submissions.indexOfFirst { it.id == sub.id }.coerceAtLeast(0)
    val offsetX = (subIndex * 0.014f)
    val offsetY = (subIndex * 0.012f)
    return Triple(
      (parentEntity.normalizedX + offsetX).coerceIn(0.08f, 0.92f),
      (parentEntity.normalizedY + offsetY).coerceIn(0.08f, 0.92f),
      null,
    )
  }

  /** Returns the live [StraightLineVector] from the user's GPS position to [entityId]. */
  fun distanceAndBearingToEntity(entityId: String): StraightLineVector? {
    val entity = entities.firstOrNull { it.id == entityId } ?: return null
    return computeStraightLineVector(entity.normalizedX, entity.normalizedY)
  }

  /** Returns the live [StraightLineVector] from the user's GPS position to [submissionId]. */
  fun distanceAndBearingToSubmission(submissionId: String): StraightLineVector? {
    val (tx, ty, _) = resolveSubmissionTargetGeometry(submissionId) ?: return null
    return computeStraightLineVector(tx, ty)
  }

  /** Resolves a [SurveyPlaceItem] by [placeId] across built-in places, API results, and active selection. */
  fun findPlaceById(placeId: String): SurveyPlaceItem? =
    (mapboxPlacesApiResults + places + filteredListPlaces + listOfNotNull(lastSelectedPlace))
      .firstOrNull { it.id == placeId }

  /** Returns the live [StraightLineVector] from the user's GPS position to [placeId]. */
  fun distanceAndBearingToPlace(placeId: String): StraightLineVector? {
    val place = findPlaceById(placeId) ?: return null
    return computeStraightLineVector(place.normalizedX, place.normalizedY)
  }

  /** Formatted distance & compass bearing badge for [entityId] (e.g. `"495 m • 319° NW"`). */
  fun formattedWayfindingBadgeForEntity(entityId: String): String {
    val v = distanceAndBearingToEntity(entityId) ?: return ""
    return "${v.formattedDistance} • ${v.bearingDegrees}° ${v.cardinalDirection}"
  }

  /** Formatted distance & compass bearing badge for [placeId] (e.g. `"340 m • 142° SE"`). */
  fun formattedWayfindingBadgeForPlace(placeId: String): String {
    val v = distanceAndBearingToPlace(placeId) ?: return ""
    return "${v.formattedDistance} • ${v.bearingDegrees}° ${v.cardinalDirection}"
  }

  /** Formatted distance & compass bearing badge for [submissionId] (e.g. `"452 m • 321° NW"`). */
  fun formattedWayfindingBadgeForSubmission(submissionId: String): String {
    val v = distanceAndBearingToSubmission(submissionId) ?: return ""
    return "${v.formattedDistance} • ${v.bearingDegrees}° ${v.cardinalDirection}"
  }

  /**
   * Returns `true` if [field] in [submissionId] represents a geometry question/field (either linked
   * to a [SubmissionGeometryPolygon] in [submissionGeometries] or having a `geopoint` / `geotrace`
   * / `geoshape` question or geometry value).
   */
  fun isSubmissionFieldGeometry(submissionId: String, field: SubmissionFieldEntry): Boolean {
    if (
      submissionGeometries.any {
        it.submissionId == submissionId &&
          (it.fieldPath == field.questionName || it.questionLabel == field.questionLabel)
      }
    ) {
      return true
    }
    val labelLower = field.questionLabel.lowercase()
    val nameLower = field.questionName.lowercase()
    val valTrimmed = field.answerValue.trim()
    return labelLower.contains("geoshape") ||
      labelLower.contains("geotrace") ||
      labelLower.contains("geopoint") ||
      nameLower.contains("geoshape") ||
      nameLower.contains("geotrace") ||
      nameLower.contains("geopoint") ||
      valTrimmed.startsWith("Polygon (", ignoreCase = true) ||
      valTrimmed.startsWith("LineString (", ignoreCase = true) ||
      valTrimmed.startsWith("Point (", ignoreCase = true)
  }

  /**
   * Formatted distance & compass bearing badge for a geometry [field] inside [submissionId] (e.g.
   * `"452 m • 321° NW"`), or `""` if [field] is not a geometry field.
   */
  fun formattedWayfindingBadgeForSubmissionField(
    submissionId: String,
    field: SubmissionFieldEntry,
  ): String {
    if (!isSubmissionFieldGeometry(submissionId, field)) return ""
    val geom = submissionGeometries.firstOrNull {
      it.submissionId == submissionId &&
        (it.fieldPath == field.questionName || it.questionLabel == field.questionLabel)
    }
    val vector =
      if (geom != null) {
        computeStraightLineVector(geom.normalizedX, geom.normalizedY)
      } else {
        distanceAndBearingToSubmission(submissionId) ?: return ""
      }
    return "${vector.formattedDistance} • ${vector.bearingDegrees}° ${vector.cardinalDirection}"
  }

  /** True when straight-line navigation is currently active and targeting [entityId]. */
  fun isNavigatingToEntity(entityId: String): Boolean =
    navigationTargetKind == NavigationTargetKind.ENTITY && navigationTargetId == entityId

  /** True when straight-line navigation is currently active and targeting [submissionId]. */
  fun isNavigatingToSubmission(submissionId: String): Boolean =
    navigationTargetKind == NavigationTargetKind.SUBMISSION && navigationTargetId == submissionId

  /** True when straight-line navigation is currently active and targeting [placeId]. */
  fun isNavigatingToPlace(placeId: String): Boolean =
    navigationTargetKind == NavigationTargetKind.PLACE && navigationTargetId == placeId

  /**
   * Currently active straight-line navigation session ([StraightLineNavigationState]) to a
   * Geospatial Entity, a Form Submission, or a Place, dynamically updated as the user's GPS
   * position or measurement unit preference changes (`null` when navigation is inactive).
   */
  val activeNavigation: StraightLineNavigationState?
    get() {
      val kind = navigationTargetKind ?: return null
      val targetId = navigationTargetId ?: return null
      return when (kind) {
        NavigationTargetKind.ENTITY -> {
          val entity = entities.firstOrNull { it.id == targetId } ?: return null
          val vector = computeStraightLineVector(entity.normalizedX, entity.normalizedY)
          StraightLineNavigationState(
            targetKind = NavigationTargetKind.ENTITY,
            targetId = entity.id,
            entityId = entity.id,
            submissionId = null,
            geometryId = null,
            targetTitle = entity.label,
            targetSubtitle = "${entity.datasetName} • GeoID: ${entity.geoId}",
            targetCoordinatesLabel = entity.coordinatesLabel,
            colorHex = entity.colorHex,
            vector = vector,
          )
        }
        NavigationTargetKind.SUBMISSION -> {
          val sub = allSubmissions.firstOrNull { it.id == targetId } ?: return null
          val parentEntity = entities.firstOrNull { it.id == sub.entityId }
          val (tx, ty, geom) = resolveSubmissionTargetGeometry(sub.id) ?: return null
          val vector = computeStraightLineVector(tx, ty)
          StraightLineNavigationState(
            targetKind = NavigationTargetKind.SUBMISSION,
            targetId = sub.id,
            entityId = parentEntity?.id.orEmpty(),
            submissionId = sub.id,
            geometryId = geom?.id,
            targetTitle =
              geom?.shortMapBadge
                ?: if (parentEntity != null) {
                  "${sub.formTitle} (${parentEntity.label.substringBefore(" •")})"
                } else {
                  sub.formTitle
                },
            targetSubtitle =
              if (sub.hasAttachedEntity) {
                "${sub.entityLabel} • ${sub.collectorName} (${sub.timestamp})"
              } else {
                "Standalone submission • ${sub.collectorName} (${sub.timestamp})"
              },
            targetCoordinatesLabel =
              parentEntity?.coordinatesLabel
                ?: sub.coordinatesLabel.ifBlank { userGpsCoordinatesLabel },
            colorHex = geom?.colorHex ?: parentEntity?.colorHex ?: 0xFFAB47BC,
            vector = vector,
          )
        }
        NavigationTargetKind.PLACE -> {
          val place = findPlaceById(targetId) ?: return null
          val vector = computeStraightLineVector(place.normalizedX, place.normalizedY)
          StraightLineNavigationState(
            targetKind = NavigationTargetKind.PLACE,
            targetId = place.id,
            entityId = "",
            submissionId = null,
            geometryId = null,
            targetTitle = place.name,
            targetSubtitle = "${place.categoryLabel} • ${place.regionSubtitle}",
            targetCoordinatesLabel = place.coordinatesLabel,
            colorHex = 0xFF0288D1,
            vector = vector,
          )
        }
      }
    }

  // --- Data Collection Form & XForms FormDef Chrome State ---
  /**
   * Currently selected swappable example form in the Prototype App Workbench
   * ([WorkbenchExampleForm]), or `null` when custom XML has been manually edited.
   */
  var selectedWorkbenchExampleForm by
    mutableStateOf<WorkbenchExampleForm?>(WorkbenchExampleForm.ALL_FIELD_TYPES)
    private set

  /**
   * Custom XForms `<h:html>` definition editable in the Prototype App Chrome
   * (`UxDesignerInspectorPanel`). Initialized to a rich EUDR / Shade-Tree Field Survey XForms XML
   * that parses cleanly with [XFormsXmlSerializer.deserializeFormDef].
   */
  var customXFormsXml by mutableStateOf(DEFAULT_PROTOTYPE_XFORMS_XML)
    private set

  /**
   * Parse error message from [XFormsXmlSerializer.deserializeFormDef] when [customXFormsXml] is
   * invalid, or `null` when valid.
   */
  var xformsXmlError by mutableStateOf<String?>(null)
    private set

  /**
   * Parsed [FormDef] from [customXFormsXml] (`null` when [customXFormsXml] is blank or invalid;
   * when blank, built-in fallback XForms [FormDef]s per form ID are used automatically).
   */
  var customFormDef by mutableStateOf<FormDef?>(parseDefaultPrototypeFormDef())
    private set

  /**
   * Active [FormWizardController] driving the embedded
   * [org.groundplatform.v2.core.forms.ui.MobileFormRunner] when data collection is triggered for a
   * Geospatial Entity (`null` when closed).
   */
  var activeFormWizardController by mutableStateOf<FormWizardController?>(null)
    private set

  /**
   * Target Geospatial Entity ID for the currently active data collection form (`null` when closed).
   */
  var activeDataCollectionEntityId by mutableStateOf<String?>(null)
    private set

  /** Target Form ID for the currently active data collection form (`null` when closed). */
  var activeDataCollectionFormId by mutableStateOf<String?>(null)
    private set

  /** True when the embedded [org.groundplatform.v2.core.forms.ui.MobileFormRunner] is open. */
  val isDataCollectionFormOpen: Boolean
    get() = activeFormWizardController != null

  /**
   * The target [GeospatialEntityItem] for the currently active data collection session (if any).
   */
  val activeDataCollectionEntity: GeospatialEntityItem?
    get() = activeDataCollectionEntityId?.let { id -> entities.firstOrNull { it.id == id } }

  /** The target [FormPreviewItem] for the currently active data collection session (if any). */
  val activeDataCollectionForm: FormPreviewItem?
    get() = activeDataCollectionFormId?.let { id -> forms.firstOrNull { it.id == id } }

  /**
   * True when the Available Forms modal bottom sheet (triggered by the bottom-centered floating
   * action button on the Main Survey screen) is currently open.
   */
  var isAvailableFormsSheetOpen by mutableStateOf(false)
    private set

  /**
   * True when the active data collection form was launched from the bottom-centered FAB without
   * pre-selecting a geospatial entity on the map (`activeDataCollectionEntityId` starts `null`, and
   * the `entityref` step presents the Map or List selector).
   */
  var wasFormLaunchedWithoutEntity by mutableStateOf(false)
    private set

  /**
   * Active view mode (`MainSurveyViewMode.MAP` vs `MainSurveyViewMode.LIST`) for the in-form
   * `entityref` step selector when a form is launched without a pre-selected geospatial entity.
   */
  var entityRefSelectorViewMode by mutableStateOf(MainSurveyViewMode.MAP)
    private set

  /** Search query used to filter candidate entities in the `entityref` step's `List` mode. */
  var entityRefSearchQuery by mutableStateOf("")
    private set

  /**
   * Returns all [GeospatialEntityItem]s in the active survey belonging to [form]'s target dataset
   * (`form.targetDatasetId`).
   */
  fun allDatasetEntitiesForForm(form: FormPreviewItem): List<GeospatialEntityItem> =
    if (form.requiresEntity) {
      entities.filter { it.datasetId == form.targetDatasetId }
    } else {
      emptyList()
    }

  /**
   * Returns the eligible [GeospatialEntityItem]s in [form]'s target dataset that can accept a new
   * submission for [form] (`isFormButtonEnabled(entity, form) == true`).
   */
  fun eligibleEntitiesForForm(form: FormPreviewItem): List<GeospatialEntityItem> =
    allDatasetEntitiesForForm(form).filter { isFormButtonEnabled(it, form) }

  /** Eligible [GeospatialEntityItem]s for the currently active data collection form. */
  val eligibleEntitiesForActiveForm: List<GeospatialEntityItem>
    get() = activeDataCollectionForm?.let { eligibleEntitiesForForm(it) } ?: emptyList()

  /**
   * Candidate entities for the active form's target dataset filtered by [entityRefSearchQuery] when
   * the collector is using the `List` view at the `entityref` step.
   */
  val filteredEntityRefCandidates: List<GeospatialEntityItem>
    get() {
      val form = activeDataCollectionForm ?: return emptyList()
      val base = allDatasetEntitiesForForm(form)
      val q = entityRefSearchQuery.trim()
      if (q.isEmpty()) return base
      return base.filter { entity ->
        entity.label.contains(q, ignoreCase = true) ||
          entity.geoId.contains(q, ignoreCase = true) ||
          entity.datasetName.contains(q, ignoreCase = true) ||
          entity.properties.values.any { it.contains(q, ignoreCase = true) }
      }
    }

  /**
   * True when the embedded form runner is open and the current step in [activeFormWizardController]
   * is an `entityref` step (`/data/target_entity` / `appearance="map-select"`) requiring the user
   * to select a geospatial entity via Map or List.
   */
  val isCurrentFormStepEntityRef: Boolean
    get() = isWizardStepEntityRef(activeFormWizardController?.currentStep)

  /** The currently active survey loaded in the Main Survey UI. */
  val activeSurvey: SurveyPreviewItem
    get() = surveys.firstOrNull { it.id == activeSurveyId } ?: surveys.first()

  /**
   * Surveys that have already been downloaded onto the device (shown on the `"Surveys"` screen
   * accessible from the navigation drawer).
   */
  val downloadedSurveys: List<SurveyPreviewItem>
    get() = surveys.filter { it.isDownloaded }

  /**
   * Surveys matching the current [searchQuery] on the "Download survey" screen by survey name,
   * description, or location.
   */
  val filteredSurveys: List<SurveyPreviewItem>
    get() {
      val trimmed = searchQuery.trim()
      if (trimmed.isEmpty()) return surveys
      return surveys.filter { survey ->
        survey.title.contains(trimmed, ignoreCase = true) ||
          survey.description.contains(trimmed, ignoreCase = true) ||
          survey.location.contains(trimmed, ignoreCase = true) ||
          survey.coordinatesLabel.contains(trimmed, ignoreCase = true)
      }
    }

  val downloadedSurveyCount: Int
    get() = surveys.count { it.isDownloaded }

  /**
   * Layers backed by `LayerDef.entity_dataset_id` (rendered with solid outlines) when the survey
   * has geospatial entities. Returns `emptyList()` when there are no geospatial entities so the Map
   * features section of the Layers dialog is hidden.
   */
  val entityDatasetLayers: List<MapLayerItem>
    get() =
      if (entities.isEmpty()) {
        emptyList()
      } else {
        mapLayers.filter { layer ->
          layer.sourceType == LayerSourceType.ENTITY_DATASET &&
            entities.any { it.layerId == layer.id }
        }
      }

  /**
   * True when the active survey has geospatial entities to display under the "Map features" section
   * of the Layers dialog.
   */
  val hasGeospatialEntities: Boolean
    get() = entities.isNotEmpty() && entityDatasetLayers.isNotEmpty()

  /**
   * Layers backed by `LayerDef.form_geometry` (`FormGeometrySource { form_id, field_path }`,
   * rendered with dotted polygon outlines).
   */
  val formGeometryLayers: List<MapLayerItem>
    get() = mapLayers.filter { it.sourceType == LayerSourceType.FORM_GEOMETRY }

  /** Set of currently visible map layer IDs based on the "Layers" sheet toggles. */
  val visibleLayerIds: Set<String>
    get() = mapLayers.filter { it.isVisible }.map { it.id }.toSet()

  /** Geospatial entities currently visible on the map according to active layer visibility. */
  val visibleMapEntities: List<GeospatialEntityItem>
    get() = entities.filter { it.layerId in visibleLayerIds }

  /** Currently visible entity dataset layers (`LayerSourceType.ENTITY_DATASET`). */
  val visibleEntityDatasetLayers: List<MapLayerItem>
    get() = entityDatasetLayers.filter { it.isVisible }

  /**
   * User-facing plural category label for the `Map features` tab and list section header:
   * - When exactly 1 entity dataset layer is visible on the map, returns its plural domain label
   *   (e.g. `"Coffee Parcels"`, `"Monitoring Plots"`, `"Washing Stations"`).
   * - When multiple entity dataset layers are visible (or none), falls back to `"Map features"`.
   */
  val activeEntitiesTabLabel: String
    get() =
      visibleEntityDatasetLayers.singleOrNull()?.pluralDomainLabel ?: ListFilterTab.ENTITIES.label

  /**
   * User-facing lowercase plural count noun for map counters, search hints, and empty states:
   * - When 1 entity dataset layer is visible, returns its lowercase plural domain label (e.g.
   *   `"coffee parcels"`, `"monitoring plots"`, `"washing stations"`).
   * - Otherwise falls back to `"map features"`.
   */
  val activeEntitiesCountNoun: String
    get() =
      visibleEntityDatasetLayers.singleOrNull()?.pluralDomainLabel?.lowercase()
        ?: "map features"

  /**
   * Resolves the user-facing singular domain noun for [entityId] (e.g. `"Coffee Parcel"`, or
   * `"Location"`).
   */
  fun entitySingularTypeLabel(entityId: String?): String =
    entityId?.let { id -> entities.firstOrNull { it.id == id }?.singularTypeLabel } ?: "Location"

  /** Resolves the dynamic display label for a [ListFilterTab] chip. */
  fun tabLabelFor(tab: ListFilterTab): String =
    when (tab) {
      ListFilterTab.ENTITIES -> activeEntitiesTabLabel
      else -> tab.label
    }

  /**
   * Form submission geometries are not displayed on the map; only entity geometries (map features)
   * are shown.
   */
  val visibleSubmissionGeometries: List<SubmissionGeometryPolygon>
    get() = emptyList()

  /** ID of the currently selected map feature cluster when the map is zoomed out. */
  var selectedClusterId by mutableStateOf<String?>(null)
    private set

  /**
   * True when the map is zoomed out past the clustering threshold (`mapZoomDelta <= -0.35f`),
   * causing visible map features (`visibleMapEntities`) to be grouped into spatial clusters with
   * cluster balloons showing counts per marker symbol.
   */
  val isMapClusteringActive: Boolean
    get() = mapZoomDelta <= -0.35f

  /**
   * Normalized world-space clustering radius (`[0.20, 12.0]`) scaled exponentially as the user
   * zooms out (`2^(-mapZoomDelta)`). Returns `0f` when clustering is inactive.
   */
  val mapClusterRadiusNormalized: Float
    get() =
      if (!isMapClusteringActive) {
        0f
      } else {
        (0.175f * 2.0.pow(-mapZoomDelta.toDouble()).toFloat()).coerceIn(0.20f, 12.0f)
      }

  /**
   * All visible map features (`visibleMapEntities`) normalized into [MapClusterFeatureItem]
   * instances for clustering.
   *
   * Geospatial entities carry their `simplestyle-spec` `marker-symbol` (`"✓"`, `"◐"`, `"○"`, or
   * `""` if no marker symbol is set). Form submission geometries are not displayed on the map or in
   * cluster chips.
   */
  val visibleMapClusterFeatures: List<MapClusterFeatureItem>
    get() =
      visibleMapEntities.map { ent ->
        MapClusterFeatureItem(
          id = ent.id,
          kind = MapFeatureKind.ENTITY,
          label = ent.label.substringBefore(" •"),
          markerSymbol = ent.rawMarkerSymbol,
          colorHex = ent.markerColorHex,
          colorCss = ent.markerColorCss,
          normalizedX = ent.normalizedX,
          normalizedY = ent.normalizedY,
          entityId = ent.id,
        )
      }

  private var cachedClustersKey: String = ""
  private var cachedClustersResult: List<MapFeatureCluster> = emptyList()

  /**
   * Spatial clusters of visible map features when [isMapClusteringActive] is `true`.
   *
   * Within each cluster, features are grouped by `marker-symbol` (including `""` for features with
   * no marker symbol as one group) and ordered canonically (`"✓"`, `"◐"`, `"○"`, custom symbols,
   * and `""` for no marker symbol) so the cluster balloon displays the count of each group.
   */
  val mapFeatureClusters: List<MapFeatureCluster>
    get() {
      if (!isMapClusteringActive) return emptyList()
      val key = "${entities.hashCode()}:${visibleLayerIds.hashCode()}:$mapZoomDelta"
      if (key == cachedClustersKey) {
        return cachedClustersResult
      }
      val computed =
        computeMapFeatureClusters(
          features = visibleMapClusterFeatures,
          radiusNormalized = mapClusterRadiusNormalized,
        )
      cachedClustersKey = key
      cachedClustersResult = computed
      return computed
    }

  /** Currently selected [MapFeatureCluster] (if any and if clustering is active). */
  val selectedCluster: MapFeatureCluster?
    get() =
      if (!isMapClusteringActive) {
        null
      } else {
        selectedClusterId?.let { id -> mapFeatureClusters.firstOrNull { it.id == id } }
      }

  /** The currently selected Geospatial Entity shown in the bottom sheet (if any). */
  val selectedEntity: GeospatialEntityItem?
    get() = selectedEntityId?.let { id ->
      entities.firstOrNull { it.id == id && it.layerId in visibleLayerIds }
    }

  /** All submissions (both entity-attached and standalone) in the active survey. */
  val allSubmissions: List<SubmissionPreviewItem>
    get() = entities.flatMap { it.submissions } + standaloneSubmissions

  /** The currently selected individual submission for full submission detail inspection. */
  val selectedSubmission: SubmissionPreviewItem?
    get() = selectedSubmissionId?.let { id -> allSubmissions.firstOrNull { it.id == id } }

  /** Returns all forms in the active survey that request entities of [entity]'s dataset type. */
  fun formsForEntity(entity: GeospatialEntityItem): List<FormPreviewItem> = forms.filter {
    it.targetDatasetId == entity.datasetId
  }

  /**
   * Returns whether the organizer-defined action button for [form] is enabled on [entity]. Because
   * all forms are `1:N` with entities, any form targeting [entity]'s dataset is enabled.
   */
  fun isFormButtonEnabled(entity: GeospatialEntityItem, form: FormPreviewItem): Boolean =
    !form.requiresEntity || entity.datasetId == form.targetDatasetId

  /** Number of entities currently in [SyncStatus.UPLOADING] state. */
  val uploadingEntityCount: Int
    get() = entities.count { it.syncStatus == SyncStatus.UPLOADING }

  /** Number of entities currently in [SyncStatus.SYNCED] state. */
  val syncedEntityCount: Int
    get() = entities.count { it.syncStatus == SyncStatus.SYNCED }

  /** Number of entities currently in [SyncStatus.FAILED] state. */
  val failedEntityCount: Int
    get() = entities.count { it.syncStatus == SyncStatus.FAILED }

  /** Number of form submissions currently in [SyncStatus.UPLOADING] state. */
  val uploadingSubmissionCount: Int
    get() = allSubmissions.count { it.syncStatus == SyncStatus.UPLOADING }

  /** Number of form submissions currently in [SyncStatus.SYNCED] state. */
  val syncedSubmissionCount: Int
    get() = allSubmissions.count { it.syncStatus == SyncStatus.SYNCED }

  /** Number of form submissions currently in [SyncStatus.FAILED] state. */
  val failedSubmissionCount: Int
    get() = allSubmissions.count { it.syncStatus == SyncStatus.FAILED }

  /**
   * Simulates device Airplane mode (`Offline`) in the UX Workbench.
   *
   * When `true`, Mapbox Places API search is disabled (`filteredListPlaces` returns `emptyList()`)
   * and the bottom sheet shows an offline notice explaining that search is restricted to local
   * map features and that Places search is not available offline.
   */
  var isAirplaneMode by mutableStateOf(false)
    private set

  /** Whether online Mapbox Places API search is currently available (`!isAirplaneMode`). */
  val isPlacesSearchAvailable: Boolean
    get() = !isAirplaneMode

  /** Live place results returned from `window.GroundMapboxBridge.searchPlaces` (Mapbox Places API). */
  var mapboxPlacesApiResults by mutableStateOf<List<SurveyPlaceItem>>(emptyList())
    private set

  /** Whether an asynchronous Mapbox Places API request is currently in flight. */
  var isMapboxPlacesSearching by mutableStateOf(false)
    private set

  /** Currently selected [SurveyPlaceItem] from Mapbox Places search (if any). */
  val selectedPlace: SurveyPlaceItem?
    get() = selectedPlaceId?.let { id -> findPlaceById(id) }

  /**
   * Filtered Places ([SurveyPlaceItem]s) in the Main Survey searchable bottom sheet returned by the
   * Mapbox Places API (`mapbox.places`) and regional place gazetteer matching [listSearchQuery].
   *
   * Returns `emptyList()` when [listSearchQuery] is blank or when [isAirplaneMode] is `true`
   * because Places results only appear when the user enters a search query while online.
   */
  val filteredListPlaces: List<SurveyPlaceItem>
    get() {
      if (isAirplaneMode) {
        return emptyList()
      }
      if (listFilterTab != ListFilterTab.ALL && listFilterTab != ListFilterTab.PLACES) {
        return emptyList()
      }
      val q = listSearchQuery.trim()
      if (q.isEmpty()) return emptyList()
      val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
      val localMatched = places.filter { place ->
        val hay =
          "${place.name} ${place.categoryLabel} ${place.regionSubtitle} ${place.coordinatesLabel}"
        place.name.contains(q, ignoreCase = true) ||
          place.categoryLabel.contains(q, ignoreCase = true) ||
          place.regionSubtitle.contains(q, ignoreCase = true) ||
          place.coordinatesLabel.contains(q, ignoreCase = true) ||
          (tokens.size > 1 && tokens.all { hay.contains(it, ignoreCase = true) })
      }
      val combined = LinkedHashMap<String, SurveyPlaceItem>()
      for (apiItem in mapboxPlacesApiResults) {
        combined[apiItem.id] = apiItem
      }
      for (item in localMatched) {
        if (combined.values.none { it.name.equals(item.name, ignoreCase = true) }) {
          combined[item.id] = item
        }
      }
      val matched = combined.values.toList()
      val coordPlace = parseCoordinateQueryToPlace(q)
      return if (
        coordPlace != null && matched.none { it.coordinatesLabel == coordPlace.coordinatesLabel }
      ) {
        listOf(coordPlace) + matched
      } else {
        matched
      }
    }

  /**
   * Parses a coordinate string (e.g. `"0.5012° S, 36.9324° E"`, `"0.4160°S, 36.9465°E"`, or
   * `"-0.5012, 36.9324"`) into a `(latitude, longitude)` pair.
   */
  fun parsePlaceCoordinates(coordinatesLabel: String): Pair<Double, Double>? {
    val parts = coordinatesLabel.split(",").map { it.trim() }
    if (parts.size < 2) return null
    fun parseComponent(raw: String, negativeCardinals: Set<Char>): Double? {
      val cleaned = raw.substringBefore("(").trim().uppercase().replace("°", " ").trim()
      if (cleaned.isEmpty()) return null
      val lastChar = cleaned.last()
      val isCardinalNeg = lastChar in negativeCardinals
      val numericPart = cleaned.trimEnd('N', 'S', 'E', 'W', ' ').trim()
      val value = numericPart.toDoubleOrNull() ?: return null
      return if (isCardinalNeg && value > 0.0) -value else value
    }
    val lat = parseComponent(parts[0], setOf('S')) ?: return null
    val lng = parseComponent(parts[1], setOf('W')) ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return lat to lng
  }

  /**
   * Parses a decimal coordinate query (e.g. `"-0.4210, 36.9505"` or `"0.4210° S, 36.9505° E"`) into
   * an ad-hoc [SurveyPlaceItem] within the survey map bounds, or returns `null` if not a coordinate
   * pair.
   */
  private fun parseCoordinateQueryToPlace(query: String): SurveyPlaceItem? {
    val (lat, lng) = parsePlaceCoordinates(query) ?: return null
    val (surveyLng, surveyLat) = activeSurveyBaseLngLat()
    val nx = (0.50f + ((lng - surveyLng) / 0.014).toFloat()).coerceIn(0.05f, 0.95f)
    val ny = (0.50f + ((surveyLat - lat) / 0.018).toFloat()).coerceIn(0.05f, 0.95f)
    val latDir = if (lat < 0) "S" else "N"
    val lngDir = if (lng < 0) "W" else "E"
    val formattedLat = (abs(lat) * 10000.0).roundToInt() / 10000.0
    val formattedLng = (abs(lng) * 10000.0).roundToInt() / 10000.0
    val coordLabel = "$formattedLat°$latDir, $formattedLng°$lngDir"
    return SurveyPlaceItem(
      id = "place-coord-query",
      name = "Coordinates ($coordLabel)",
      categoryLabel = "GPS Coordinates",
      regionSubtitle = "${activeSurvey.location} • Direct coordinate lookup",
      coordinatesLabel = coordLabel,
      normalizedX = nx,
      normalizedY = ny,
      zoomDelta = 1.15f,
      longitude = lng,
      latitude = lat,
    )
  }

  /** Filtered Geospatial Entities in the Main Survey `List` view matching [listSearchQuery]. */
  val filteredListEntities: List<GeospatialEntityItem>
    get() {
      if (listFilterTab != ListFilterTab.ALL && listFilterTab != ListFilterTab.ENTITIES) {
        return emptyList()
      }
      val q = listSearchQuery.trim()
      if (q.isEmpty()) return entities
      return entities.filter { entity ->
        entity.label.contains(q, ignoreCase = true) ||
          entity.geoId.contains(q, ignoreCase = true) ||
          entity.datasetName.contains(q, ignoreCase = true) ||
          entity.syncStatus.label.contains(q, ignoreCase = true) ||
          entity.properties.values.any { it.contains(q, ignoreCase = true) }
      }
    }

  /**
   * Form submissions are not searchable via the bottom-sheet search bar because submissions have no
   * unique key to identify them with; returns an empty list.
   */
  val filteredListSubmissions: List<SubmissionPreviewItem>
    get() = emptyList()

  /**
   * Map features ([GeospatialEntityItem]s) in the Main Survey `List` view grouped by their
   * entity dataset (`Map features` category).
   */
  val groupedFilteredListEntities: List<EntityDatasetFeaturesGroup>
    get() {
      val matchingEntities = filteredListEntities
      if (matchingEntities.isEmpty()) return emptyList()
      return matchingEntities
        .groupBy { it.datasetName }
        .map { (datasetName, entities) ->
          val layer = entityDatasetLayers.firstOrNull { it.id == entities.first().layerId }
          EntityDatasetFeaturesGroup(layer = layer, datasetName = datasetName, entities = entities)
        }
    }

  /**
   * Form submissions are excluded from the searchable bottom sheet list since there is no unique
   * key to identify them with; returns an empty list.
   */
  val groupedFilteredListSubmissions: List<FormSubmissionsGroup>
    get() = emptyList()

  /**
   * Submissions for a specific [entity] grouped by their parent [FormPreviewItem], using the form's
   * title (`form.title`) as the group heading.
   */
  fun groupedSubmissionsForEntity(entity: GeospatialEntityItem): List<FormSubmissionsGroup> {
    if (entity.submissions.isEmpty()) return emptyList()
    val groupedByFormId = entity.submissions.groupBy { it.formId }
    return groupedByFormId.map { (formId, subs) ->
      val baseForm =
        forms.firstOrNull { it.id == formId }
          ?: FormPreviewItem(
            id = formId,
            title = subs.first().formTitle,
            description = "",
            version = subs.first().formVersion,
            targetDatasetId = entity.datasetId,
            targetDatasetName = entity.datasetName,
            questionCount = subs.first().fields.size,
            ctaLabel = subs.first().formTitle,
          )
      FormSubmissionsGroup(form = baseForm, submissions = subs)
    }
  }

  /**
   * Submission map layers grouped by their [FormPreviewItem] for the `Layers` dialog, using the
   * form's title (`form.title`) as the group heading instead of the word "Form".
   */
  val groupedSubmissionLayersByForm: List<FormSubmissionLayersGroup>
    get() = forms.mapNotNull { form ->
      val matchingLayers = formGeometryLayers.filter { it.formId == form.id }
      if (matchingLayers.isNotEmpty()) {
        val formSubs = allSubmissions.filter { it.formId == form.id }
        FormSubmissionLayersGroup(form = form, layers = matchingLayers, submissions = formSubs)
      } else {
        null
      }
    }

  /** Toggles visibility of all submission layers belonging to [formId] in the `Layers` dialog. */
  fun toggleFormSubmissionLayersVisibility(formId: String) {
    val targetLayers = formGeometryLayers.filter { it.formId == formId }
    if (targetLayers.isEmpty()) return
    val nextVisible = !targetLayers.all { it.isVisible }
    val targetIds = targetLayers.map { it.id }.toSet()
    mapLayers = mapLayers.map { layer ->
      if (layer.id in targetIds) layer.copy(isVisible = nextVisible) else layer
    }
  }

  /** Directly switches the active mobile screen (used by both flow buttons and UX workbench). */
  fun navigateTo(screen: PrototypeScreen) {
    if (screen == PrototypeScreen.DOWNLOAD_SURVEY) {
      downloadSurveyEntryOrigin =
        if (
          currentScreen == PrototypeScreen.MAIN_SURVEY &&
            activeDrawerSubView == MainDrawerSubView.SWITCH_SURVEYS
        ) {
          DownloadSurveyEntryOrigin.SURVEY_LIST
        } else {
          DownloadSurveyEntryOrigin.AFTER_TOS
        }
    }
    isDownloadSurveySignOutPromptOpen = false
    currentScreen = screen
    activeSurveyNotice = null
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
  }

  /** Authenticates with Google and advances to the Terms of Service screen. */
  fun signInWithGoogle() {
    isSignedIn = true
    isDownloadSurveySignOutPromptOpen = false
    currentScreen =
      if (hasAcceptedTerms) {
        downloadSurveyEntryOrigin = DownloadSurveyEntryOrigin.AFTER_TOS
        PrototypeScreen.DOWNLOAD_SURVEY
      } else {
        PrototypeScreen.TERMS_OF_SERVICE
      }
  }

  /** Updates the Terms of Service agreement checkbox state. */
  fun setTermsChecked(checked: Boolean) {
    termsCheckboxChecked = checked
  }

  /** Accepts the Terms of Service and advances to the Download Survey screen. */
  fun acceptTermsOfService() {
    termsCheckboxChecked = true
    hasAcceptedTerms = true
    downloadSurveyEntryOrigin = DownloadSurveyEntryOrigin.AFTER_TOS
    isDownloadSurveySignOutPromptOpen = false
    currentScreen = PrototypeScreen.DOWNLOAD_SURVEY
  }

  /** Declines the Terms of Service and returns to the Sign In page. */
  fun declineTermsOfService() {
    isSignedIn = false
    hasAcceptedTerms = false
    isDownloadSurveySignOutPromptOpen = false
    currentScreen = PrototypeScreen.SIGN_IN
  }

  /**
   * Handles the Back escape hatch on the Download surveys screen:
   * - When accessed from the Survey list (`SURVEY_LIST`), returns the user to the Survey list.
   * - When shown after Terms of Service (`AFTER_TOS`), opens a confirmation prompt before signing
   *   the user out.
   */
  fun navigateBackFromDownloadSurvey() {
    if (downloadSurveyEntryOrigin == DownloadSurveyEntryOrigin.SURVEY_LIST) {
      isDownloadSurveySignOutPromptOpen = false
      isDrawerOpen = false
      activeSurveyNotice = null
      currentScreen = PrototypeScreen.MAIN_SURVEY
      activeDrawerSubView = MainDrawerSubView.SWITCH_SURVEYS
    } else {
      isDownloadSurveySignOutPromptOpen = true
    }
  }

  /** Confirms signing out from the Download surveys screen back-action confirmation prompt. */
  fun confirmDownloadSurveySignOut() {
    isDownloadSurveySignOutPromptOpen = false
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    activeSurveyNotice = null
    isSignedIn = false
    hasAcceptedTerms = false
    currentScreen = PrototypeScreen.SIGN_IN
  }

  /** Cancels/dismisses the sign-out confirmation prompt on the Download surveys screen. */
  fun dismissDownloadSurveySignOutPrompt() {
    isDownloadSurveySignOutPromptOpen = false
  }

  /** Updates the search bar query used to filter surveys by name or location. */
  fun updateSearchQuery(query: String) {
    searchQuery = query
  }

  /** Clears the search bar query to show all shared surveys. */
  fun clearSearchQuery() {
    searchQuery = ""
  }

  /** Marks the specified survey as downloaded onto the device for offline field use. */
  fun downloadSurvey(surveyId: String) {
    surveys = surveys.map { item ->
      if (item.id == surveyId) {
        activeSurveyNotice =
          "Downloaded \"${item.title}\" (${item.offlineSizeLabel}) for offline use."
        item.copy(isDownloaded = true)
      } else {
        item
      }
    }
  }

  /** Opens a survey in the Main Survey UI (downloading it first if not already downloaded). */
  fun openSurvey(surveyId: String) {
    downloadSurvey(surveyId)
    activeSurveyId = surveyId
    val exampleForm = exampleFormForSurveyId(surveyId)
    selectedWorkbenchExampleForm = exampleForm
    customXFormsXml = exampleForm.xformsXml
    customFormDef = cachedExampleFormDef(exampleForm)
    xformsXmlError = null
    forms = formsForSurvey(surveyId)
    entities = entitiesForSurvey(surveyId)
    standaloneSubmissions = standaloneSubmissionsForSurvey(surveyId)
    submissionGeometries = submissionGeometriesForSurvey(surveyId)
    mapLayers = mapLayersForSurvey(surveyId)
    selectedEntityId = null
    selectedSubmissionId = null
    activeDataCollectionEntityId = null
    activeDataCollectionFormId = null
    activeFormWizardController = null
    isAvailableFormsSheetOpen = false
    isEntityBottomSheetExpanded = false
    currentScreen = PrototypeScreen.MAIN_SURVEY
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    activeSurveyNotice =
      "Loaded survey \"${activeSurvey.title}\" (${entities.size} entities, ${allSubmissions.size} preloaded submissions)."
  }

  /** Toggles the downloaded status of a survey (for UX prototyping & testing). */
  fun toggleSurveyDownloaded(surveyId: String) {
    surveys = surveys.map { item ->
      if (item.id == surveyId) {
        val nextState = !item.isDownloaded
        activeSurveyNotice =
          if (nextState) {
            "Downloaded \"${item.title}\" (${item.offlineSizeLabel}) for offline use."
          } else {
            "Removed offline copy of \"${item.title}\"."
          }
        item.copy(isDownloaded = nextState)
      } else {
        item
      }
    }
  }

  // --- Main Survey UI Actions ---

  /**
   * Switches between the collapsed Map peek state (`MAP`) and the expanded Searchable List state
   * (`LIST`) inside the unified persistent bottom sheet on the Main Survey screen.
   */
  fun setMainSurveyViewMode(mode: MainSurveyViewMode) {
    mainViewMode = mode
    activeDrawerSubView = MainDrawerSubView.NONE
    if (mode == MainSurveyViewMode.LIST) {
      selectedEntityId = null
      selectedSubmissionId = null
      isEntityBottomSheetExpanded = true
      isLayersSheetOpen = false
    } else {
      isEntityBottomSheetExpanded = false
    }
  }

  /** Opens or closes the Hamburger Navigation Drawer. */
  fun updateDrawerOpen(open: Boolean) {
    isDrawerOpen = open
  }

  /** Toggles the "Layers" visibility popover sheet on the Map view. */
  fun updateLayersSheetOpen(open: Boolean) {
    isLayersSheetOpen = open
  }

  /**
   * Selects between `Normal` (`BasemapType.NORMAL`) and `Satellite` (`BasemapType.SATELLITE`)
   * basemap.
   */
  fun selectBasemapType(type: BasemapType) {
    selectedBasemapType = type
    offlineBasemapStyle =
      if (type == BasemapType.SATELLITE) {
        OfflineBasemapStyle.SATELLITE_HYBRID
      } else {
        OfflineBasemapStyle.VECTOR_TOPO
      }
  }

  /** Toggles between `Normal` and `Satellite` basemap. */
  fun toggleBasemapType() {
    selectBasemapType(
      if (selectedBasemapType == BasemapType.SATELLITE) {
        BasemapType.NORMAL
      } else {
        BasemapType.SATELLITE
      }
    )
  }

  /** Toggles visibility of the downloaded Mapbox offline basemap in the `Layers` dialog. */
  fun toggleOfflineBasemapVisibility() {
    isOfflineBasemapVisible = !isOfflineBasemapVisible
  }

  /** Updates the offline basemap rendering style (`SATELLITE_HYBRID` vs `VECTOR_TOPO`). */
  fun updateOfflineBasemapStyle(style: OfflineBasemapStyle) {
    offlineBasemapStyle = style
    selectedBasemapType =
      if (style == OfflineBasemapStyle.SATELLITE_HYBRID) {
        BasemapType.SATELLITE
      } else {
        BasemapType.NORMAL
      }
    isOfflineBasemapVisible = true
  }

  /**
   * Selects a submission geometry polygon on the map and opens its submission (and parent entity if
   * attached, or standalone submission card if unattached).
   */
  fun selectSubmissionGeometry(geometryId: String) {
    val geom = submissionGeometries.firstOrNull { it.id == geometryId } ?: return
    clearSelectedPlace()
    selectedEntityId = geom.entityId.takeIf { it.isNotBlank() }
    selectedSubmissionId = geom.submissionId
    isEntityBottomSheetExpanded = true
    isLayersSheetOpen = false
  }

  /** Toggles visibility of a specific `LayerDef` on the survey map. */
  fun toggleLayerVisibility(layerId: String) {
    mapLayers = mapLayers.map { layer ->
      if (layer.id == layerId) {
        val nextVisible = !layer.isVisible
        if (!nextVisible && selectedEntity?.layerId == layerId) {
          selectedEntityId = null
          selectedSubmissionId = null
          isEntityBottomSheetExpanded = false
        }
        layer.copy(isVisible = nextVisible)
      } else {
        layer
      }
    }
  }

  /** Selects a Geospatial Entity on the map to open its bottom sheet in collapsed/peek state. */
  fun selectEntity(entityId: String?) {
    clearSelectedPlace()
    selectedEntityId = entityId
    selectedSubmissionId = null
    isEntityBottomSheetExpanded = false
    mainViewMode = MainSurveyViewMode.MAP
    if (entityId != null) {
      isLayersSheetOpen = false
    }
  }

  /**
   * Selects a Geospatial Entity from the expanded bottom sheet list, keeping the bottom sheet
   * expanded so the user can immediately inspect its properties, form actions, and 1:N submissions.
   */
  fun selectEntityFromList(entityId: String) {
    clearSelectedPlace()
    selectedEntityId = entityId
    selectedSubmissionId = null
    isEntityBottomSheetExpanded = true
    mainViewMode = MainSurveyViewMode.MAP
    isLayersSheetOpen = false
  }

  /**
   * Selects a [SurveyPlaceItem] from Mapbox Places search, panning and zooming the map camera to
   * fit the geographic bounds/extent of the identified place (`[targetZoom]`) in
   * [LocationLockState.PANNED] mode, flying the live `mapboxgl.Map` to the place's bounds, and
   * collapsing the bottom sheet so the map viewport is visible.
   */
  fun selectPlace(placeId: String) {
    val place = findPlaceById(placeId) ?: return
    val parsedCoords = parsePlaceCoordinates(place.coordinatesLabel)
    val hasDefaultFallbackCoords =
      kotlin.math.abs(place.longitude - 36.9512) < 1e-6 &&
        kotlin.math.abs(place.latitude - (-0.4198)) < 1e-6
    val lat = if (hasDefaultFallbackCoords && parsedCoords != null) parsedCoords.first else place.latitude
    val lng = if (hasDefaultFallbackCoords && parsedCoords != null) parsedCoords.second else place.longitude
    val (surveyLng, surveyLat) = activeSurveyBaseLngLat()
    val resolvedZoom =
      place.targetZoom.coerceIn(2.0f, 18.5f)
    val resolvedPlace = place.copy(longitude = lng, latitude = lat, targetZoom = resolvedZoom)
    lastSelectedPlace = resolvedPlace
    selectedPlaceId = resolvedPlace.id
    selectedEntityId = null
    selectedSubmissionId = null
    isCameraFollowingUser = false
    locationLockState = LocationLockState.PANNED
    mapPanOffsetX = ((userGpsNormalizedX - 0.5f) + ((surveyLng - lng) / 0.014).toFloat())
    mapPanOffsetY = ((userGpsNormalizedY - 0.5f) + ((lat - surveyLat) / 0.018).toFloat())
    mapZoomDelta = (resolvedZoom - 15.3f).coerceIn(-13.0f, 3.2f)
    isEntityBottomSheetExpanded = false
    mainViewMode = MainSurveyViewMode.MAP
    isLayersSheetOpen = false
    flyPlatformMapboxToPlace(
      lng = lng,
      lat = lat,
      zoom = resolvedZoom,
      name = resolvedPlace.name,
      category = resolvedPlace.categoryLabel,
      coordinatesLabel = resolvedPlace.coordinatesLabel,
    )
    activeSurveyNotice = "Centered map on ${resolvedPlace.name} (${resolvedPlace.coordinatesLabel})"
  }

  /** Clears the currently selected place on the map. */
  fun clearSelectedPlace() {
    selectedPlaceId = null
    lastSelectedPlace = null
    clearPlatformMapboxPlace()
  }

  /**
   * Clears the selected entity or submission and returns to the expanded searchable list inside the
   * persistent bottom sheet.
   */
  fun returnToBottomSheetList() {
    selectedEntityId = null
    selectedSubmissionId = null
    isEntityBottomSheetExpanded = true
    mainViewMode = MainSurveyViewMode.LIST
    isLayersSheetOpen = false
  }

  /** Toggles the Entity Bottom Sheet between expanded and collapsed (peek) state. */
  fun toggleEntityBottomSheetExpanded() {
    isEntityBottomSheetExpanded = !isEntityBottomSheetExpanded
  }

  /** Explicitly expands or collapses the Entity Bottom Sheet. */
  fun updateEntityBottomSheetExpanded(expanded: Boolean) {
    isEntityBottomSheetExpanded = expanded
  }

  /** Opens full details for a specific submission (from a 1:N entity bottom sheet or List view). */
  fun selectSubmissionDetail(submissionId: String?) {
    selectedSubmissionId = submissionId
    if (submissionId != null) {
      isEntityBottomSheetExpanded = true
      val parentEntity = entities.firstOrNull { e -> e.submissions.any { it.id == submissionId } }
      selectedEntityId = parentEntity?.id
    }
  }

  /**
   * Updates the search query in the Main Survey searchable bottom sheet and queries the Mapbox
   * Places API (`mapbox.places`) when online (`!isAirplaneMode`).
   */
  fun updateListSearchQuery(query: String) {
    listSearchQuery = query
    val trimmed = query.trim()
    if (isAirplaneMode || trimmed.isEmpty()) {
      isMapboxPlacesSearching = false
      mapboxPlacesApiResults = emptyList()
    } else {
      triggerMapboxPlacesApiSearch(trimmed)
    }
  }

  /** Clears the search query in the Main Survey `List` view. */
  fun clearListSearchQuery() {
    listSearchQuery = ""
    isMapboxPlacesSearching = false
    mapboxPlacesApiResults = emptyList()
  }

  /**
   * Updates the active category filter tab (`All`, `Places`, `Map features`).
   *
   * When [isAirplaneMode] is `true` (Offline), selecting [ListFilterTab.PLACES] is blocked and
   * surfaces an offline notice.
   */
  fun selectListFilterTab(tab: ListFilterTab) {
    if (isAirplaneMode && tab == ListFilterTab.PLACES) {
      activeSurveyNotice =
        "Offline (Airplane mode): Places search is not available offline. Searching local $activeEntitiesCountNoun only."
      return
    }
    listFilterTab = tab
  }

  /**
   * Enables or disables **Airplane mode** (`Offline` simulator) in the UX Workbench.
   *
   * When enabled (`true`):
   * - Disables Mapbox Places API search (`filteredListPlaces` becomes empty).
   * - Switches [listFilterTab] away from [ListFilterTab.PLACES] if currently selected.
   * - Shows a message in the bottom sheet that search is only in local map features and Places search is
   *   not available offline.
   */
  fun updateAirplaneMode(enabled: Boolean) {
    isAirplaneMode = enabled
    if (enabled) {
      isMapboxPlacesSearching = false
      mapboxPlacesApiResults = emptyList()
      if (listFilterTab == ListFilterTab.PLACES) {
        listFilterTab = ListFilterTab.ALL
      }
      activeSurveyNotice =
        "Airplane mode ON (Offline): Places search disabled. Searching local $activeEntitiesCountNoun only."
    } else {
      activeSurveyNotice = "Airplane mode OFF (Online): Places API search enabled."
      val trimmed = listSearchQuery.trim()
      if (trimmed.isNotEmpty()) {
        triggerMapboxPlacesApiSearch(trimmed)
      }
    }
  }

  /** Toggles [isAirplaneMode] between Online (`false`) and Offline (`true`). */
  fun toggleAirplaneMode() {
    updateAirplaneMode(!isAirplaneMode)
  }

  private fun triggerMapboxPlacesApiSearch(query: String) {
    if (isAirplaneMode || query.isBlank()) {
      isMapboxPlacesSearching = false
      mapboxPlacesApiResults = emptyList()
      return
    }
    isMapboxPlacesSearching = true
    searchPlatformMapboxPlaces(
      surveyId = activeSurveyId,
      query = query,
      isAirplaneMode = isAirplaneMode,
    ) { json ->
      onMapboxPlacesSearchResponse(query, json)
    }
  }

  /** Receives JSON place features from `window.GroundMapboxBridge.searchPlaces` (`mapbox.places`). */
  internal fun onMapboxPlacesSearchResponse(query: String, resultsJson: String) {
    if (isAirplaneMode || listSearchQuery.trim() != query.trim()) {
      isMapboxPlacesSearching = false
      return
    }
    mapboxPlacesApiResults = parseMapboxPlacesResultsJson(resultsJson)
    isMapboxPlacesSearching = false
  }

  private fun parseMapboxPlacesResultsJson(json: String): List<SurveyPlaceItem> {
    val trimmed = json.trim()
    if (!trimmed.startsWith("[") || trimmed == "[]") return emptyList()
    val objRegex = Regex("\\{([^{}]*)\\}")
    fun extractStr(body: String, key: String): String {
      val m = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(body) ?: return ""
      return m.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
    }
    fun extractDoubleOrNull(body: String, key: String): Double? {
      val m = Regex("\"$key\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").find(body) ?: return null
      return m.groupValues[1].toDoubleOrNull()
    }
    val (surveyLng, surveyLat) = activeSurveyBaseLngLat()
    return objRegex
      .findAll(trimmed)
      .mapNotNull { match ->
        val body = match.groupValues[1]
        val id = extractStr(body, "id").ifEmpty { return@mapNotNull null }
        val name = extractStr(body, "name").ifEmpty { return@mapNotNull null }
        val categoryLabel = extractStr(body, "categoryLabel").ifEmpty { "Place" }
        val regionSubtitle =
          extractStr(body, "regionSubtitle")
            .ifEmpty { extractStr(body, "subtitle") }
            .ifEmpty { activeSurvey.location }
        val coordinatesLabel = extractStr(body, "coordinatesLabel")
        val parsedCoords = parsePlaceCoordinates(coordinatesLabel)
        val lng =
          extractDoubleOrNull(body, "lng")
            ?: extractDoubleOrNull(body, "longitude")
            ?: parsedCoords?.second
            ?: surveyLng
        val lat =
          extractDoubleOrNull(body, "lat")
            ?: extractDoubleOrNull(body, "latitude")
            ?: parsedCoords?.first
            ?: surveyLat
        val bboxMinLng = extractDoubleOrNull(body, "bboxMinLng")
        val bboxMinLat = extractDoubleOrNull(body, "bboxMinLat")
        val bboxMaxLng = extractDoubleOrNull(body, "bboxMaxLng")
        val bboxMaxLat = extractDoubleOrNull(body, "bboxMaxLat")
        val targetZoom =
          extractDoubleOrNull(body, "targetZoom")?.toFloat()
            ?: inferTargetZoomForPlace(
              categoryLabel = categoryLabel,
              bboxMinLng = bboxMinLng,
              bboxMinLat = bboxMinLat,
              bboxMaxLng = bboxMaxLng,
              bboxMaxLat = bboxMaxLat,
              fallbackZoomDelta = 0.85f,
            )
        val computedNx = (0.50f + ((lng - surveyLng) / 0.014).toFloat())
        val computedNy = (0.50f + ((surveyLat - lat) / 0.018).toFloat())
        val nx = extractDoubleOrNull(body, "nx")?.toFloat() ?: computedNx
        val ny = extractDoubleOrNull(body, "ny")?.toFloat() ?: computedNy
        SurveyPlaceItem(
          id = id,
          name = name,
          categoryLabel = categoryLabel,
          regionSubtitle = regionSubtitle,
          coordinatesLabel = coordinatesLabel,
          normalizedX = nx,
          normalizedY = ny,
          zoomDelta = (targetZoom - 15.3f).coerceIn(-13.0f, 3.2f),
          longitude = lng,
          latitude = lat,
          bboxMinLng = bboxMinLng,
          bboxMinLat = bboxMinLat,
          bboxMaxLng = bboxMaxLng,
          bboxMaxLat = bboxMaxLat,
          targetZoom = targetZoom,
          mapboxPlaceId = id,
          sourceLabel = "Places API",
        )
      }
      .toList()
  }

  /** Opens the Available Forms modal bottom sheet triggered by the bottom-centered FAB. */
  fun openAvailableFormsSheet() {
    isAvailableFormsSheetOpen = true
    isLayersSheetOpen = false
    isDrawerOpen = false
  }

  /** Closes the Available Forms modal bottom sheet. */
  fun closeAvailableFormsSheet() {
    isAvailableFormsSheetOpen = false
  }

  /** Toggles the Available Forms modal bottom sheet open or closed. */
  fun toggleAvailableFormsSheet() {
    if (isAvailableFormsSheetOpen) {
      closeAvailableFormsSheet()
    } else {
      openAvailableFormsSheet()
    }
  }

  /**
   * Switches the `entityref` step picker between `MainSurveyViewMode.MAP` and
   * `MainSurveyViewMode.LIST`.
   */
  fun updateEntityRefSelectorViewMode(mode: MainSurveyViewMode) {
    entityRefSelectorViewMode = mode
  }

  /** Updates the search query in the `entityref` step's `List` selector. */
  fun updateEntityRefSearchQuery(query: String) {
    entityRefSearchQuery = query
  }

  /** Clears the search query in the `entityref` step's `List` selector. */
  fun clearEntityRefSearchQuery() {
    entityRefSearchQuery = ""
  }

  /**
   * Selects a target Geospatial Entity ([entityId]) at the `entityref` step (`/data/target_entity`)
   * during data collection when the form was launched without a pre-selected entity from the map.
   *
   * Updates [activeDataCollectionEntityId], [selectedEntityId], and populates
   * [ENTITY_REF_FIELD_PATH] (`/data/target_entity`) in [activeFormWizardController].
   */
  fun selectEntityRefForActiveForm(entityId: String) {
    val entity = entities.firstOrNull { it.id == entityId } ?: return
    val form = activeDataCollectionForm
    if (form != null && form.requiresEntity) {
      if (entity.datasetId != form.targetDatasetId || !isFormButtonEnabled(entity, form)) return
    }
    activeDataCollectionEntityId = entity.id
    selectedEntityId = entity.id
    activeFormWizardController?.updateString(ENTITY_REF_FIELD_PATH, entity.id)
    if (
      activeFormWizardController
        ?.formState
        ?.fieldStates
        ?.containsKey("/data/sample_plot_entity") == true
    ) {
      activeFormWizardController?.updateString("/data/sample_plot_entity", entity.id)
    }
    if (
      activeFormWizardController
        ?.formState
        ?.fieldStates
        ?.containsKey("/data/past_individual_id") == true
    ) {
      activeFormWizardController?.updateString("/data/past_individual_id", entity.id)
    }
    if (
      activeFormWizardController
        ?.formState
        ?.fieldStates
        ?.containsKey("/data/primary_respondent_id") == true
    ) {
      activeFormWizardController?.updateString("/data/primary_respondent_id", entity.id)
    }
  }

  /**
   * Updates [customXFormsXml], parses [FormDef] via [XFormsXmlSerializer.deserializeFormDef], and
   * updates [customFormDef] and [xformsXmlError]. If a form runner is currently open and the new
   * [FormDef] is valid, refreshes [activeFormWizardController] with the new [FormDef].
   */
  fun updateCustomXFormsXml(xml: String) {
    customXFormsXml = xml
    selectedWorkbenchExampleForm =
      WorkbenchExampleForm.entries.firstOrNull { it.xformsXml.trim() == xml.trim() }
        ?: if (xml.trim() == DEFAULT_PROTOTYPE_XFORMS_XML.trim()) {
          WorkbenchExampleForm.ALL_FIELD_TYPES
        } else {
          null
        }
    if (xml.isBlank()) {
      customFormDef = null
      xformsXmlError = null
      if (activeFormWizardController != null) {
        val currentForm = activeDataCollectionForm ?: forms.first()
        val fallbackFormDef =
          resolveFormDefForLaunch(
            customFormDef = null,
            form = currentForm,
            candidateEntities = eligibleEntitiesForForm(currentForm),
            defaultSelectedEntityId = activeDataCollectionEntityId.orEmpty(),
            includeEntityRefStep = wasFormLaunchedWithoutEntity && currentForm.requiresEntity,
          )
        activeFormWizardController = FormWizardController(formDef = fallbackFormDef)
      }
      return
    }
    try {
      val parsed = XFormsXmlSerializer.deserializeFormDef(xml)
      customFormDef = parsed
      xformsXmlError = null
      if (activeFormWizardController != null) {
        val currentForm = activeDataCollectionForm ?: forms.first()
        val shouldIncludeEntityRefStep =
          wasFormLaunchedWithoutEntity &&
            currentForm.requiresEntity &&
            (selectedWorkbenchExampleForm == null)
        val refreshedFormDef =
          resolveFormDefForLaunch(
            customFormDef = parsed,
            form = currentForm,
            candidateEntities = eligibleEntitiesForForm(currentForm),
            defaultSelectedEntityId = activeDataCollectionEntityId.orEmpty(),
            includeEntityRefStep = shouldIncludeEntityRefStep,
          )
        activeFormWizardController = FormWizardController(formDef = refreshedFormDef)
      }
    } catch (e: Exception) {
      customFormDef = null
      xformsXmlError = e.message ?: "Invalid XForms XML"
    }
  }

  /**
   * Swaps the active example survey & form in the workbench to [example], switching to the
   * corresponding survey (`surveyIdForExampleForm(example)`) so the survey's preloaded `entities`,
   * `standaloneSubmissions`, `submissionGeometries`, `mapLayers`, and `forms` are loaded together.
   * When [launchImmediately] is `true`, opens the embedded `MobileFormRunner` wizard immediately.
   */
  fun selectWorkbenchExampleForm(
    example: WorkbenchExampleForm,
    launchImmediately: Boolean = false,
  ) {
    val targetSurveyId = surveyIdForExampleForm(example)
    openSurvey(targetSurveyId)
    selectedWorkbenchExampleForm = example
    activeSurveyNotice =
      "Switched to survey \"${activeSurvey.title}\" (${entities.size} entities, ${allSubmissions.size} preloaded submissions)."
    if (launchImmediately) {
      launchActiveOrDefaultFormForTesting()
    }
  }

  /** Restores the default sample XForms XML definition (`DEFAULT_PROTOTYPE_XFORMS_XML`). */
  fun resetDefaultXFormsXml() {
    if (activeSurveyId != "survey-kenya-coffee") {
      openSurvey("survey-kenya-coffee")
    }
    updateCustomXFormsXml(DEFAULT_PROTOTYPE_XFORMS_XML)
    selectedWorkbenchExampleForm = WorkbenchExampleForm.ALL_FIELD_TYPES
  }

  /**
   * Launches data collection for [formId] from the bottom-centered Floating Action Button (FAB)
   * list of available forms (**with no entity pre-selected from the map**).
   *
   * Because no geospatial entity was selected on the map prior to triggering the form, if the form
   * requires a geospatial entity (`form.requiresEntity == true`), [activeDataCollectionEntityId]
   * starts as `null` and the form wizard includes the required `entityref` step
   * (`[ENTITY_REF_FIELD_PATH] = "/data/target_entity"`). At that step in the data collection
   * process, the user is presented with the interactive **Map or List** selector to choose the
   * target entity.
   */
  fun launchFormFromFab(formId: String) {
    val form = forms.firstOrNull { it.id == formId } ?: return
    val candidates = eligibleEntitiesForForm(form)
    val resolvedFormDef =
      resolveFormDefForLaunch(
        customFormDef = customFormDef,
        form = form,
        candidateEntities = candidates,
        defaultSelectedEntityId = "",
        includeEntityRefStep = form.requiresEntity,
      )
    val controller = FormWizardController(formDef = resolvedFormDef)

    isAvailableFormsSheetOpen = false
    wasFormLaunchedWithoutEntity = true
    entityRefSelectorViewMode = mainViewMode
    entityRefSearchQuery = ""
    activeDataCollectionEntityId = null
    activeDataCollectionFormId = form.id
    activeFormWizardController = controller
    isLayersSheetOpen = false
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    if (currentScreen != PrototypeScreen.MAIN_SURVEY) {
      currentScreen = PrototypeScreen.MAIN_SURVEY
    }
  }

  /**
   * Launches the embedded [org.groundplatform.v2.core.forms.ui.MobileFormRunner]
   * (`[FormWizardController]`) for [formId] on [entityId] when the organizer-defined form button
   * (`ctaLabel`) is tapped in the entity bottom sheet or triggered from the UX Chrome tester.
   */
  fun launchFormForEntity(entityId: String, formId: String) {
    val entity = entities.firstOrNull { it.id == entityId } ?: return
    val form = forms.firstOrNull { it.id == formId } ?: return
    if (!isFormButtonEnabled(entity, form)) return

    val resolvedFormDef = resolveFormDefForLaunch(customFormDef, form)
    val controller = FormWizardController(formDef = resolvedFormDef)
    if (controller.formState.fieldStates.containsKey(ENTITY_REF_FIELD_PATH)) {
      controller.updateString(ENTITY_REF_FIELD_PATH, entity.id)
    }
    if (controller.formState.fieldStates.containsKey("/data/sample_plot_entity")) {
      controller.updateString("/data/sample_plot_entity", entity.id)
    }
    if (controller.formState.fieldStates.containsKey("/data/past_individual_id")) {
      controller.updateString("/data/past_individual_id", entity.id)
    }
    if (controller.formState.fieldStates.containsKey("/data/primary_respondent_id")) {
      controller.updateString("/data/primary_respondent_id", entity.id)
    }

    isAvailableFormsSheetOpen = false
    wasFormLaunchedWithoutEntity = false
    selectedEntityId = entity.id
    activeDataCollectionEntityId = entity.id
    activeDataCollectionFormId = form.id
    activeFormWizardController = controller
    isLayersSheetOpen = false
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    if (currentScreen != PrototypeScreen.MAIN_SURVEY) {
      currentScreen = PrototypeScreen.MAIN_SURVEY
    }
  }

  /**
   * Convenience trigger used by the `▶ Test / Launch Form Now` button in the Prototype App Chrome
   * (`XFormsFormDefChromeSection`) to launch the active XForms `FormDef` on the currently selected
   * entity (or as a standalone form when the survey has no predefined entities).
   */
  fun launchActiveOrDefaultFormForTesting() {
    if (entities.isEmpty()) {
      val standaloneForm = forms.firstOrNull { !it.requiresEntity } ?: forms.firstOrNull() ?: return
      launchFormFromFab(standaloneForm.id)
      return
    }
    val currentEntity =
      selectedEntity
        ?: entities.firstOrNull { it.id == "entity-shade-201" }
        ?: entities.firstOrNull()
    if (currentEntity != null) {
      val enabledFormOnCurrent =
        formsForEntity(currentEntity).firstOrNull { isFormButtonEnabled(currentEntity, it) }
      if (enabledFormOnCurrent != null) {
        launchFormForEntity(currentEntity.id, enabledFormOnCurrent.id)
        return
      }
    }
    val fallbackEntity =
      entities.firstOrNull { ent -> formsForEntity(ent).any { isFormButtonEnabled(ent, it) } }
    if (fallbackEntity != null) {
      val fallbackForm =
        formsForEntity(fallbackEntity).firstOrNull { isFormButtonEnabled(fallbackEntity, it) }
          ?: return
      launchFormForEntity(fallbackEntity.id, fallbackForm.id)
      return
    }
    val standaloneFallback = forms.firstOrNull() ?: return
    launchFormFromFab(standaloneFallback.id)
  }

  /**
   * Completes the active form submission when the user clicks `Submit ✓` in `MobileFormRunner` (or
   * when invoked programmatically with a finalized [recordInstance]).
   *
   * Extracts all answered fields from [recordInstance] (and
   * `activeFormWizardController?.formState`) using `formatFieldValueForDisplay` into
   * `List<SubmissionFieldEntry>`, appends a new [SubmissionPreviewItem] to the target entity's
   * `submissions` list, updates [activeSurveyNotice], and closes the active form runner.
   */
  fun completeActiveFormSubmission(
    recordInstance: RecordInstance =
      activeFormWizardController?.formState?.recordInstance ?: RecordInstance(),
    entityStates: List<EntityState> =
      activeFormWizardController?.formState?.entityStates ?: emptyList(),
  ) {
    val controller = activeFormWizardController
    val resolvedEntityStates =
      if (entityStates.isEmpty() && controller != null) {
        val fin = controller.finalizeForm()
        if (fin is FinalizationResult.Success) fin.entityStates else controller.formState.entityStates
      } else {
        entityStates
      }
    val entityRefFieldState =
      controller?.formState?.fieldStates?.get(ENTITY_REF_FIELD_PATH)
        ?: controller?.formState?.fieldStates?.get("/data/sample_plot_entity")
        ?: controller?.formState?.fieldStates?.get("/data/past_individual_id")
        ?: controller?.formState?.fieldStates?.get("/data/primary_respondent_id")
    val entityRefFromForm =
      entityRefFieldState
        ?.takeIf { !it.isEmpty }
        ?.let {
          org.groundplatform.v2.core.forms.ui
            .formatFieldValueForDisplay(it.value, it.dataType)
            .trim()
        }
        ?.takeIf { it.isNotBlank() }
    val formId =
      activeDataCollectionFormId
        ?: selectedEntity?.let { formsForEntity(it).firstOrNull()?.id }
        ?: forms.firstOrNull()?.id
        ?: return
    val form = forms.firstOrNull { it.id == formId } ?: forms.first()

    val resolvedFormDef =
      controller?.formState?.formDef ?: resolveFormDefForLaunch(customFormDef, form)

    val extractedFields =
      extractSubmissionFieldsFromRecord(
        recordInstance = recordInstance,
        controller = controller,
        resolvedFormDef = resolvedFormDef,
        form = form,
        gnssBadge = gnssStatusChipLabel,
      )

    val resolvedTitle = resolvedFormDef.title.ifBlank { form.title }
    val resolvedVersion = resolvedFormDef.version.ifBlank { form.version }

    // Collect evaluated save_to properties from resolvedEntityStates (or directly from extracted fields)
    val saveToProps = mutableMapOf<String, String>()
    resolvedEntityStates.forEach { es ->
      es.properties.forEach { (k, v) ->
        val strVal = v.string_value
        val dblVal = v.double_value
        val i64Val = v.int64_value
        val i32Val = v.int32_value
        val boolVal = v.bool_value
        val gpVal = v.geopoint_value
        val shapeVal = v.geoshape_value
        val traceVal = v.geotrace_value
        val formatted: String =
          when {
            strVal != null -> strVal
            dblVal != null -> dblVal.toString()
            i64Val != null -> i64Val.toString()
            i32Val != null -> i32Val.toString()
            boolVal != null -> boolVal.toString()
            gpVal != null -> "${gpVal.latitude} ${gpVal.longitude}"
            shapeVal != null -> shapeVal.points.joinToString("; ") { "${it.latitude} ${it.longitude}" }
            traceVal != null -> traceVal.points.joinToString("; ") { "${it.latitude} ${it.longitude}" }
            else -> v.toString()
          }
        if (formatted.isNotBlank()) {
          saveToProps[k] = formatted
        }
      }
    }
    extractedFields.forEach { f ->
      saveToProps.getOrPut(f.questionName) { f.answerValue }
    }

    val createEntityState = resolvedEntityStates.firstOrNull { it.shouldCreate }

    val entityId =
      activeDataCollectionEntityId
        ?: entityRefFromForm
        ?: if (wasFormLaunchedWithoutEntity) {
          null
        } else {
          selectedEntityId ?: entities.firstOrNull()?.id
        }

    // 1. Check if an entity should be created via XForms save_to / entities:entity create="1"
    if (entityId == null && (createEntityState != null || (form.targetDatasetId.isNotBlank() && !form.requiresEntity))) {
      val targetDataset =
        createEntityState?.dataset?.takeIf { it.isNotBlank() }
          ?: form.targetDatasetId.ifBlank { "locations" }
      val targetDatasetName = form.targetDatasetName.ifBlank { targetDataset.replace('_', ' ') }
      val newEntId =
        createEntityState?.entityId?.takeIf { it.isNotBlank() }
          ?: "ent-${targetDataset.take(4)}-${(entities.size + 1).toString().padStart(2, '0')}"
      val labelCandidate =
        createEntityState?.label?.takeIf { it.isNotBlank() }
          ?: saveToProps["farmer_parcel_code"]
          ?: saveToProps["producer_farm_id"]
          ?: saveToProps["land_use"]?.substringBefore(" (")
          ?: "$targetDatasetName #${entities.size + 1}"
      val finalEntLabel =
        if (labelCandidate.contains("•")) {
          labelCandidate
        } else {
          val subCategory =
            saveToProps["commodity_type"]?.substringBefore(" (")
              ?: saveToProps["land_use"]?.substringBefore(" (")
          if (subCategory != null) "$labelCandidate • $subCategory" else labelCandidate
        }

      val newSubId = "sub-$newEntId-${form.id}-${entities.size + 1}"
      val newSubmission =
        SubmissionPreviewItem(
          id = newSubId,
          entityId = newEntId,
          entityLabel = finalEntLabel,
          formId = form.id,
          formTitle = resolvedTitle,
          formVersion = resolvedVersion,
          collectorName = signedInUserName,
          collectorEmail = signedInUserEmail,
          timestamp = "2026-09-19 18:30 UTC",
          fields = extractedFields,
          targetTypeLabel = form.targetSingularTypeLabel,
          syncStatus = SyncStatus.UPLOADING,
          coordinatesLabel = userGpsCoordinatesLabel,
          normalizedX = userGpsNormalizedX,
          normalizedY = userGpsNormalizedY,
        )

      val targetDatasetDashed = targetDataset.replace('_', '-')
      val matchingLayer =
        mapLayers.firstOrNull { it.id == "layer-$targetDataset" || it.id == "layer-$targetDatasetDashed" }
          ?: mapLayers.firstOrNull { it.id == "layer-form-${form.id}" }
          ?: mapLayers.firstOrNull { it.id == "layer-form-commodity-perimeter" && targetDataset == "commodity_plots" }
          ?: mapLayers.firstOrNull { it.id == "layer-form-single-point-land-use" && targetDataset == "land_use_observations" }
      val layerId = matchingLayer?.id ?: "layer-$targetDatasetDashed"
      val layerColorHex = matchingLayer?.colorHex ?: 0xFF2E7D32

      val geomWkt = saveToProps["geometry"]
      val isPolygonGeom =
        geomWkt?.contains(";") == true ||
          geomWkt?.startsWith("POLYGON", ignoreCase = true) == true ||
          extractedFields.any { it.questionName.contains("perimeter") }
      val geomTypeLabel = if (isPolygonGeom) "Polygon" else "Point"

      val entityProps =
        saveToProps.toMutableMap().apply {
          put("status", "Completed")
          put("marker-symbol", "✓")
          put("marker-color", "#1E8E3E")
          put("stroke", "#1E8E3E")
          put("fill", "#1E8E3E")
        }

      val newEntity =
        GeospatialEntityItem(
          id = newEntId,
          label = finalEntLabel,
          datasetId = targetDataset,
          datasetName = targetDatasetName,
          layerId = layerId,
          geoId = newEntId.uppercase(),
          geometryTypeLabel = geomTypeLabel,
          areaHectares = if (isPolygonGeom) 1.2 else 0.05,
          perimeterMeters = if (isPolygonGeom) 480 else 25,
          coordinatesLabel = userGpsCoordinatesLabel.substringBefore(" ("),
          normalizedX = userGpsNormalizedX,
          normalizedY = userGpsNormalizedY,
          colorHex = layerColorHex,
          properties = entityProps,
          submissions = listOf(newSubmission),
          singularTypeLabel = form.targetSingularTypeLabel,
          syncStatus = SyncStatus.UPLOADING,
        )

      entities = listOf(newEntity) + entities

      val nextSeq = mutations.size + 1
      val seqSuffix = (nextSeq % 60).toString().padStart(2, '0')
      val opTime = "2026-09-19 18:30:$seqSuffix UTC"
      val startTime = "2026-09-19 18:31:$seqSuffix UTC"
      val newEntityMutation =
        MutationLogItem(
          id = "mut-create-ent-$newEntId",
          surveyId = activeSurveyId,
          operationKind = MutationOperationKind.CREATE_ENTITY,
          title = "Created entity $finalEntLabel",
          targetLabel = finalEntLabel,
          entityId = newEntId,
          submissionId = newSubId,
          actorName = signedInUserName,
          state = MutationSyncState.QUEUED,
          stateDetail = "Waiting to upload new entity",
          operationTimestamp = opTime,
          startedTimestamp = startTime,
          completedTimestamp = null,
          payloadSummary = "Created new entity via save_to",
        )
      val newSubmissionMutation =
        MutationLogItem(
          id = "mut-sub-$newSubId",
          surveyId = activeSurveyId,
          operationKind = MutationOperationKind.CREATE_SUBMISSION,
          title = resolvedTitle,
          targetLabel = finalEntLabel,
          entityId = newEntId,
          submissionId = newSubId,
          actorName = signedInUserName,
          state = MutationSyncState.UPLOADING,
          stateDetail = "Uploading",
          operationTimestamp = opTime,
          startedTimestamp = startTime,
          completedTimestamp = null,
          payloadSummary = "${extractedFields.size} responses",
        )
      mutations = listOf(newEntityMutation, newSubmissionMutation) + mutations
      selectedEntityId = newEntId
      selectedSubmissionId = newSubId
      activeSurveyNotice = "Created new entity \"$finalEntLabel\" via save_to (✓ Completed)"
      closeActiveFormRunner()
      return
    }

    // 2. Handle standalone ("Log Only") form submissions without an attached entity
    if (entityId == null && !form.requiresEntity) {
      val newSubId = "sub-standalone-${form.id}-${standaloneSubmissions.size + 1}"
      val newStandaloneSubmission =
        SubmissionPreviewItem(
          id = newSubId,
          entityId = "",
          entityLabel = "",
          formId = form.id,
          formTitle = resolvedTitle,
          formVersion = resolvedVersion,
          collectorName = signedInUserName,
          collectorEmail = signedInUserEmail,
          timestamp = "2026-09-19 18:30 UTC",
          fields = extractedFields,
          targetTypeLabel = "Standalone Field Log",
          syncStatus = SyncStatus.UPLOADING,
          coordinatesLabel = userGpsCoordinatesLabel,
          normalizedX = userGpsNormalizedX,
          normalizedY = userGpsNormalizedY,
        )
      standaloneSubmissions = listOf(newStandaloneSubmission) + standaloneSubmissions
      val nextSeq = mutations.size + 1
      val seqSuffix = (nextSeq % 60).toString().padStart(2, '0')
      val opTime = "2026-09-19 18:30:$seqSuffix UTC"
      val startTime = "2026-09-19 18:31:$seqSuffix UTC"
      val newSubmissionMutation =
        MutationLogItem(
          id = "mut-sub-$newSubId",
          surveyId = activeSurveyId,
          operationKind = MutationOperationKind.CREATE_SUBMISSION,
          title = resolvedTitle,
          targetLabel = "Standalone Field Log (${userGpsCoordinatesLabel.substringBefore(" (")})",
          entityId = "",
          submissionId = newSubId,
          actorName = signedInUserName,
          state = MutationSyncState.UPLOADING,
          stateDetail =
            "Uploading standalone submission (${extractedFields.size} fields) to Ground Cloud",
          operationTimestamp = opTime,
          startedTimestamp = startTime,
          completedTimestamp = null,
          payloadSummary = "${extractedFields.size} fields • Standalone Field Log",
        )
      mutations = listOf(newSubmissionMutation) + mutations
      selectedEntityId = null
      selectedSubmissionId = newSubId
      activeSurveyNotice =
        "Submitted standalone field log \"$resolvedTitle\" (${userGpsCoordinatesLabel.substringBefore(" (")})"
      closeActiveFormRunner()
      return
    }

    // 3. Update existing entity with submission and merged save_to properties
    val resolvedEntityId = entityId ?: return
    val entity = entities.firstOrNull { it.id == resolvedEntityId } ?: return
    val newSubId = "sub-${entity.id}-${form.id}-${entity.submissions.size + 1}"
    val newSubmission =
      SubmissionPreviewItem(
        id = newSubId,
        entityId = entity.id,
        entityLabel = entity.label,
        formId = form.id,
        formTitle = resolvedTitle,
        formVersion = resolvedVersion,
        collectorName = signedInUserName,
        collectorEmail = signedInUserEmail,
        timestamp = "2026-09-19 18:30 UTC",
        fields = extractedFields,
        targetTypeLabel = entity.singularTypeLabel,
        syncStatus = SyncStatus.UPLOADING,
      )

    val previousMarkerSymbol = entity.markerSymbol
    val (nextMarkerSymbol, nextMarkerColor, nextStatus) =
      when (previousMarkerSymbol) {
        "○" -> Triple("◐", "#F9AB00", "In progress")
        "◐" -> Triple("✓", "#1E8E3E", "Completed")
        else -> Triple("✓", "#1E8E3E", "Completed")
      }
    val updatedProperties =
      entity.properties.toMutableMap().apply {
        putAll(saveToProps)
        put("status", nextStatus)
        put("marker-symbol", nextMarkerSymbol)
        put("marker-color", nextMarkerColor)
        put("stroke", nextMarkerColor)
        put("fill", nextMarkerColor)
      }

    entities = entities.map { item ->
      if (item.id == entity.id) {
        val updatedSubmissions = listOf(newSubmission) + item.submissions
        item.copy(
          properties = updatedProperties,
          submissions = updatedSubmissions,
          syncStatus = deriveEntitySyncStatus(updatedSubmissions, fallback = SyncStatus.UPLOADING),
        )
      } else {
        item
      }
    }
    val nextSeq = mutations.size + 1
    val seqSuffix = (nextSeq % 60).toString().padStart(2, '0')
    val opTime = "2026-09-19 18:30:$seqSuffix UTC"
    val startTime = "2026-09-19 18:31:$seqSuffix UTC"
    val newSubmissionMutation =
      MutationLogItem(
        id = "mut-sub-$newSubId",
        surveyId = activeSurveyId,
        operationKind = MutationOperationKind.CREATE_SUBMISSION,
        title = resolvedTitle,
        targetLabel = entity.label,
        entityId = entity.id,
        submissionId = newSubId,
        actorName = signedInUserName,
        state = MutationSyncState.UPLOADING,
        stateDetail = "Uploading",
        operationTimestamp = opTime,
        startedTimestamp = startTime,
        completedTimestamp = null,
        payloadSummary = "${extractedFields.size} responses",
      )
    val newEntityUpdateMutation =
      MutationLogItem(
        id = "mut-ent-$newSubId",
        surveyId = activeSurveyId,
        operationKind = MutationOperationKind.UPDATE_ENTITY,
        title = "Marked ${entity.singularTypeLabel.lowercase()} as $nextStatus",
        targetLabel = entity.label,
        entityId = entity.id,
        submissionId = newSubId,
        actorName = signedInUserName,
        state = MutationSyncState.QUEUED,
        stateDetail = "Waiting to upload",
        operationTimestamp = opTime,
        startedTimestamp = startTime,
        completedTimestamp = null,
        payloadSummary = "${entity.singularTypeLabel} status updated",
      )
    mutations = listOf(newSubmissionMutation, newEntityUpdateMutation) + mutations
    selectedEntityId = entity.id
    activeSurveyNotice =
      "Submitted \"$resolvedTitle\" for ${entity.label} (Marker: $previousMarkerSymbol → $nextMarkerSymbol $nextStatus)"
    closeActiveFormRunner()
  }

  /**
   * Updates the [SyncStatus] of a specific [GeospatialEntityItem] ([entityId]) and synchronizes its
   * submissions accordingly when marked [SyncStatus.SYNCED].
   */
  fun updateEntitySyncStatus(entityId: String, newStatus: SyncStatus) {
    entities = entities.map { item ->
      if (item.id == entityId) {
        val updatedSubmissions =
          if (newStatus == SyncStatus.SYNCED) {
            item.submissions.map { sub -> sub.copy(syncStatus = SyncStatus.SYNCED) }
          } else {
            item.submissions
          }
        item.copy(submissions = updatedSubmissions, syncStatus = newStatus)
      } else {
        item
      }
    }
    val entity = entities.firstOrNull { it.id == entityId } ?: return
    activeSurveyNotice = "${entity.label}: Sync status set to ${newStatus.label}"
  }

  /**
   * Cycles the [SyncStatus] of a specific [GeospatialEntityItem] ([entityId]) (`Uploading` ->
   * `Synced` -> `Failed` -> `Uploading`), or retries failed uploads immediately.
   */
  fun cycleEntitySyncStatus(entityId: String) {
    val entity = entities.firstOrNull { it.id == entityId } ?: return
    updateEntitySyncStatus(entityId, entity.syncStatus.next())
  }

  /**
   * Updates the [SyncStatus] of a specific [SubmissionPreviewItem] ([submissionId]) and recomputes
   * the parent entity's aggregate [SyncStatus] (or updates [standaloneSubmissions]).
   */
  fun updateSubmissionSyncStatus(submissionId: String, newStatus: SyncStatus) {
    var updatedSubTitle: String? = null
    entities = entities.map { item ->
      val hasTarget = item.submissions.any { it.id == submissionId }
      if (hasTarget) {
        val updatedSubmissions =
          item.submissions.map { sub ->
            if (sub.id == submissionId) {
              updatedSubTitle = sub.formTitle
              sub.copy(syncStatus = newStatus)
            } else {
              sub
            }
          }
        item.copy(
          submissions = updatedSubmissions,
          syncStatus =
            deriveEntitySyncStatus(
              updatedSubmissions,
              fallback = if (newStatus == SyncStatus.SYNCED) SyncStatus.SYNCED else item.syncStatus,
            ),
        )
      } else {
        item
      }
    }
    standaloneSubmissions = standaloneSubmissions.map { sub ->
      if (sub.id == submissionId) {
        updatedSubTitle = sub.formTitle
        sub.copy(syncStatus = newStatus)
      } else {
        sub
      }
    }
    if (updatedSubTitle != null) {
      activeSurveyNotice = "$updatedSubTitle: Sync status set to ${newStatus.label}"
    }
  }

  /**
   * Cycles the [SyncStatus] of a specific [SubmissionPreviewItem] ([submissionId]) (`Uploading` ->
   * `Synced` -> `Failed` -> `Uploading`).
   */
  fun cycleSubmissionSyncStatus(submissionId: String) {
    val submission = allSubmissions.firstOrNull { it.id == submissionId } ?: return
    updateSubmissionSyncStatus(submissionId, submission.syncStatus.next())
  }

  /** Closes the active `MobileFormRunner` and returns to the survey map/list screen. */
  fun closeActiveFormRunner() {
    activeFormWizardController = null
    activeDataCollectionEntityId = null
    activeDataCollectionFormId = null
    wasFormLaunchedWithoutEntity = false
    entityRefSearchQuery = ""
  }

  /** Opens the scannable S2 GeoID / Entity QR code modal dialog for [entityId]. */
  fun openEntityQrCode(entityId: String) {
    activeQrCodeEntityId = entityId
  }

  /** Closes the active Entity QR code modal dialog. */
  fun closeEntityQrCode() {
    activeQrCodeEntityId = null
  }

  /** Opens the Share PDF modal sheet to share a location's report PDF to a preferred app. */
  fun shareEntityPdf(entityId: String) {
    val entity = entities.firstOrNull { it.id == entityId } ?: return
    val filePrefix = entity.singularTypeLabel.lowercase().replace(' ', '-')
    activeSharedPdfSheet =
      SharedPdfSheetState(
        targetId = entity.id,
        title = "Share ${entity.singularTypeLabel} PDF Report",
        subtitle = "${entity.label} • GeoID ${entity.geoId}",
        pdfFileName = "$filePrefix-${entity.geoId}.pdf",
        targetKindLabel = "${entity.singularTypeLabel} Summary PDF",
      )
  }

  /** Opens the Share PDF modal sheet to share a submission's report PDF to a preferred app. */
  fun shareSubmissionPdf(submissionId: String) {
    val sub = allSubmissions.firstOrNull { it.id == submissionId } ?: return
    activeSharedPdfSheet =
      SharedPdfSheetState(
        targetId = sub.id,
        title = "Share ${sub.formTitle} PDF Report",
        subtitle = "${sub.formTitle} • ${sub.collectorName} (${sub.timestamp})",
        pdfFileName = "${sub.id}.pdf",
        targetKindLabel = "${sub.formTitle} PDF",
      )
  }

  /** Closes the active Share PDF modal sheet. */
  fun closeSharePdfSheet() {
    activeSharedPdfSheet = null
  }

  // --- Hamburger Navigation Drawer Actions ---

  /**
   * Drawer option 1: "Surveys" — opens the dedicated screen showing only surveys that have already
   * been downloaded onto the device, with a primary button to browse & download more surveys.
   */
  fun drawerSwitchSurveys() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.SWITCH_SURVEYS
  }

  /**
   * Drawer option: "Uploads" — opens the unified screen listing all mutations with status filter
   * chips (`Pending`, `In progress`, `Uploaded`, `Failed`).
   */
  fun drawerOpenUploads(filter: UploadStatusFilter? = null) {
    isDrawerOpen = false
    selectedUploadStatusFilter = filter
    activeDrawerSubView = MainDrawerSubView.UPLOADS
  }

  /** Selects or clears the active [UploadStatusFilter] chip on the `Uploads` screen. */
  fun selectUploadStatusFilter(filter: UploadStatusFilter?) {
    selectedUploadStatusFilter = filter
  }

  /** Toggles [filter] on the `Uploads` screen (selecting it, or clearing it if already active). */
  fun toggleUploadStatusFilter(filter: UploadStatusFilter) {
    selectedUploadStatusFilter =
      if (selectedUploadStatusFilter == filter) {
        null
      } else {
        filter
      }
  }

  /** Legacy helper: opens the `Outbox` sub-view (rendered by the unified `Uploads` screen). */
  fun drawerOpenOutbox() {
    isDrawerOpen = false
    selectedUploadStatusFilter = null
    activeDrawerSubView = MainDrawerSubView.OUTBOX
  }

  /** Legacy helper: opens the `Uploaded` sub-view (rendered by the unified `Uploads` screen). */
  fun drawerOpenUploaded() {
    isDrawerOpen = false
    selectedUploadStatusFilter = UploadStatusFilter.UPLOADED
    activeDrawerSubView = MainDrawerSubView.UPLOADED
  }

  /**
   * Synchronizes a single Outbox mutation ([mutationId]), transitioning its state to
   * [MutationSyncState.UPLOADED] with a completed timestamp and moving it into `Uploaded`.
   */
  fun syncMutationNow(mutationId: String) {
    val target = mutations.firstOrNull { it.id == mutationId } ?: return
    val started = target.startedTimestamp ?: "2026-09-19 09:42:02 UTC"
    val completed = "2026-09-19 09:42:06 UTC"
    mutations = mutations.map { item ->
      if (item.id == mutationId) {
        item.copy(
          state = MutationSyncState.UPLOADED,
          stateDetail = "Synced to Ground Cloud • Commit rev #1052",
          startedTimestamp = started,
          completedTimestamp = completed,
        )
      } else {
        item
      }
    }
    val subId = target.submissionId
    if (subId != null) {
      updateSubmissionSyncStatus(subId, SyncStatus.SYNCED)
    } else {
      updateEntitySyncStatus(target.entityId, SyncStatus.SYNCED)
    }
    activeSurveyNotice = "Uploaded mutation \"${target.title}\" (${completed})"
  }

  /**
   * Synchronizes all currently pending/in-progress mutations in the `Outbox`, transitioning them to
   * [MutationSyncState.UPLOADED] with completed timestamps.
   */
  fun syncAllOutboxMutations() {
    val count = outboxMutationCount
    if (count == 0) return
    val completed = "2026-09-19 09:42:10 UTC"
    mutations = mutations.map { item ->
      if (item.isOutbox) {
        item.copy(
          state = MutationSyncState.UPLOADED,
          stateDetail = "Synced to Ground Cloud • Batch commit rev #1055",
          startedTimestamp = item.startedTimestamp ?: "2026-09-19 09:42:04 UTC",
          completedTimestamp = completed,
        )
      } else {
        item
      }
    }
    entities = entities.map { entity ->
      entity.copy(
        submissions = entity.submissions.map { sub -> sub.copy(syncStatus = SyncStatus.SYNCED) },
        syncStatus = SyncStatus.SYNCED,
      )
    }
    standaloneSubmissions = standaloneSubmissions.map { sub ->
      sub.copy(syncStatus = SyncStatus.SYNCED)
    }
    activeSurveyNotice = "Uploaded all $count Outbox mutation(s) to Ground Cloud ($completed)"
  }

  /** Navigates from the "Surveys" screen to the full "Download survey" directory screen. */
  fun openDownloadMoreSurveysScreen() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    downloadSurveyEntryOrigin = DownloadSurveyEntryOrigin.SURVEY_LIST
    isDownloadSurveySignOutPromptOpen = false
    currentScreen = PrototypeScreen.DOWNLOAD_SURVEY
  }

  /** Alias kept for compatibility: opens the "Surveys" sub-screen. */
  fun drawerSwitchOrDownloadSurveys() {
    drawerSwitchSurveys()
  }

  /** Drawer option: Offline maps. */
  fun drawerManageOfflineMaps() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.MANAGE_OFFLINE_MAPS
  }

  /** Drawer option: Change settings. */
  fun drawerOpenSettings() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.SETTINGS
  }

  /**
   * Closes the active drawer sub-view (Switch Surveys, Outbox, Uploaded, Offline Maps, or Settings)
   * and returns to the survey.
   */
  fun closeDrawerSubView() {
    activeDrawerSubView = MainDrawerSubView.NONE
  }

  /** Drawer option 4: View Terms of Service. */
  fun drawerViewTermsOfService() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    currentScreen = PrototypeScreen.TERMS_OF_SERVICE
  }

  /** Drawer option 5: Sign out. */
  fun drawerSignOut() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    isSignedIn = false
    currentScreen = PrototypeScreen.SIGN_IN
  }

  /** Toggles download status of an offline Mapbox basemap tile package. */
  fun toggleOfflineTilePackage(packageId: String) {
    offlineTilePackages = offlineTilePackages.map { pkg ->
      if (pkg.id == packageId) pkg.copy(isDownloaded = !pkg.isDownloaded) else pkg
    }
  }

  /** Updates the measurement unit preference (`METRIC` vs `IMPERIAL`). */
  fun updateUnitSystem(system: MeasurementUnitSystem) {
    unitSystem = system
  }

  /**
   * Updates the active application & survey language using either a language code (e.g. `"en"`,
   * `"fr"`, `"es"`, `"pt"`, `"vi"`, `"th"`, `"lo"`, `"km"`, `"sw"`) or a formatted locale string
   * (e.g. `"fr (Français)"`). Synchronizes both [selectedLanguageCode] and
   * [selectedLanguageLocale].
   */
  fun updateSelectedLanguage(languageCodeOrLocale: String) {
    val trimmed = languageCodeOrLocale.trim()
    val codeCandidate = trimmed.substringBefore(" ").lowercase()
    val matched = GROUND_LANGUAGE_OPTIONS.firstOrNull {
      it.code.equals(trimmed, ignoreCase = true) ||
        it.code.equals(codeCandidate, ignoreCase = true) ||
        it.label.equals(trimmed, ignoreCase = true) ||
        "${it.code} (${it.label})".equals(trimmed, ignoreCase = true)
    }
    if (matched != null) {
      selectedLanguageCode = matched.code
      selectedLanguageLocale = "${matched.code} (${matched.label})"
    } else {
      selectedLanguageCode = codeCandidate.ifEmpty { "en" }
      selectedLanguageLocale = trimmed.ifEmpty { "en (English)" }
    }
  }

  /** Updates the active in-app language locale (delegates to [updateSelectedLanguage]). */
  fun updateLanguageLocale(locale: String) {
    updateSelectedLanguage(locale)
  }

  /**
   * Updates the "Upload photos over Wi-Fi only" preference (matching `SettingsViewModel` in
   * `ground-android`).
   */
  fun updateUploadMediaOverUnmeteredConnectionOnly(enabled: Boolean) {
    shouldUploadPhotosOnWifiOnly = enabled
  }

  /**
   * Records a click on "Visit website" (`https://groundplatform.org/`) in the Settings Help
   * section.
   */
  fun visitGroundWebsite(url: String = GROUND_WEBSITE_URL) {
    visitedWebsiteUrl = url
    activeSurveyNotice = "Opened $url"
  }

  /** Evicts uploaded media attachments from local device cache (per `00-index.md`). */
  fun evictUploadedMediaCache() {
    mediaCacheCleared = true
  }

  /**
   * Selects the device preview form factor (`Mobile` vs `Tablet`) in the prototype wrapper page.
   */
  fun selectDeviceFormFactor(formFactor: DeviceFormFactor) {
    deviceFormFactor = formFactor
    deviceOrientation = formFactor.defaultOrientation
  }

  /** Toggles between `Mobile` and `Tablet` form factors in the prototype wrapper page. */
  fun toggleDeviceFormFactor() {
    val nextFormFactor =
      if (deviceFormFactor == DeviceFormFactor.MOBILE) {
        DeviceFormFactor.TABLET
      } else {
        DeviceFormFactor.MOBILE
      }
    selectDeviceFormFactor(nextFormFactor)
  }

  /** Rotates the simulated device between `Portrait` and `Landscape` orientation (`90°` swap). */
  fun rotateDevice() {
    deviceOrientation =
      if (deviceOrientation == DeviceOrientation.PORTRAIT) {
        DeviceOrientation.LANDSCAPE
      } else {
        DeviceOrientation.PORTRAIT
      }
  }

  /** Alias for [rotateDevice]: toggles between `Portrait` and `Landscape` device orientation. */
  fun toggleDeviceOrientation() {
    rotateDevice()
  }

  /** Explicitly sets the simulated device orientation (`Portrait` or `Landscape`). */
  fun selectDeviceOrientation(orientation: DeviceOrientation) {
    deviceOrientation = orientation
  }

  /**
   * Pans (drags) the survey map viewport by normalized deltas `(deltaNormalizedX,
   * deltaNormalizedY)`.
   *
   * Dragging the map disengages automatic GPS camera centering ([isCameraFollowingUser] = `false`,
   * [locationLockState] = [LocationLockState.PANNED]), causing the Google Maps-style `"Recenter"`
   * button to appear on the map.
   */
  fun panMap(deltaNormalizedX: Float, deltaNormalizedY: Float) {
    if (deltaNormalizedX == 0f && deltaNormalizedY == 0f) return
    isCameraFollowingUser = false
    locationLockState = LocationLockState.PANNED
    mapPanOffsetX = (mapPanOffsetX + deltaNormalizedX).coerceIn(-10000f, 10000f)
    mapPanOffsetY = (mapPanOffsetY + deltaNormalizedY).coerceIn(-10000f, 10000f)
  }

  /**
   * Recenters the map camera on the user's current GPS location (`(0.50f, 0.50f)` screen center)
   * and re-enables automatic GPS camera following ([isCameraFollowingUser] = `true`,
   * [locationLockState] = [LocationLockState.LOCKED]).
   */
  fun recenterMapOnUser() {
    isCameraFollowingUser = true
    locationLockState = LocationLockState.LOCKED
    mapPanOffsetX = 0f
    mapPanOffsetY = 0f
  }

  /** Adjusts the Mapbox zoom level by [deltaZoom] (clamped to `[-5.0f, +3.7f]`). */
  fun zoomMapBy(deltaZoom: Float) {
    if (deltaZoom == 0f) return
    mapZoomDelta = (mapZoomDelta + deltaZoom).coerceIn(-5.0f, 3.7f)
    if (!isMapClusteringActive) {
      selectedClusterId = null
    }
  }

  /** Zooms the Mapbox map in by one step (`+0.75` zoom levels). */
  fun zoomInMap() {
    zoomMapBy(0.75f)
  }

  /** Zooms the Mapbox map out by one step (`-0.75` zoom levels). */
  fun zoomOutMap() {
    zoomMapBy(-0.75f)
  }

  /** Resets the Mapbox zoom level to the active survey's default (`15.3z`). */
  fun resetMapZoom() {
    mapZoomDelta = 0f
    selectedClusterId = null
  }

  /**
   * Formats the count of map features in a cluster using the active domain noun (e.g. `"5 map
   * features"`, `"1 map feature"`, or `"5 coffee parcels"`).
   */
  fun formatClusterSitesCountLabel(siteCount: Int): String {
    val noun =
      if (siteCount == 1) {
        activeEntitiesCountNoun.removeSuffix("s")
      } else {
        activeEntitiesCountNoun
      }
    return "$siteCount $noun"
  }

  /**
   * Selects a zoomed-out [MapFeatureCluster] balloon by [clusterId] (or clears selection when
   * `null`). If the same cluster is tapped a second time while already selected, zooms in toward
   * that cluster.
   */
  fun selectCluster(clusterId: String?) {
    if (clusterId == null) {
      selectedClusterId = null
      return
    }
    if (selectedClusterId == clusterId) {
      zoomIntoCluster(clusterId)
      return
    }
    val target = mapFeatureClusters.firstOrNull { it.id == clusterId }
    selectedClusterId = clusterId
    if (target != null) {
      selectedEntityId = null
      selectedSubmissionId = null
      activeSurveyNotice = formatClusterSitesCountLabel(target.siteCount)
    }
  }

  /**
   * Centers the map viewport on the specified [clusterId] and zooms in one step (`+0.75z`) to
   * expand the cluster.
   */
  fun zoomIntoCluster(clusterId: String) {
    val target = mapFeatureClusters.firstOrNull { it.id == clusterId }
    if (target != null) {
      isCameraFollowingUser = false
      locationLockState = LocationLockState.PANNED
      mapPanOffsetX = (userGpsNormalizedX - target.normalizedX).coerceIn(-10000f, 10000f)
      mapPanOffsetY = (userGpsNormalizedY - target.normalizedY).coerceIn(-10000f, 10000f)
    }
    zoomInMap()
    val refreshed = mapFeatureClusters.firstOrNull {
      target != null &&
        hypot(it.normalizedX - target.normalizedX, it.normalizedY - target.normalizedY) < 0.08f
    }
    selectedClusterId = refreshed?.id
  }

  /**
   * Updates the user's current GPS location (`userGpsNormalizedX`, `userGpsNormalizedY`).
   *
   * - When [isCameraFollowingUser] is `true` (default), [mapPanOffsetX] and [mapPanOffsetY] stay
   *   `0f`, so the map automatically pans ([mapWorldToScreenShiftX], [mapWorldToScreenShiftY]) to
   *   keep the user's GPS location at the exact center `(0.50f, 0.50f)` of the screen.
   * - When [isCameraFollowingUser] is `false` (after the map has been manually dragged/panned), the
   *   panned camera viewport remains stationary while the user's GPS blue dot moves across the map.
   */
  fun updateUserGpsLocation(
    newNormalizedX: Float,
    newNormalizedY: Float,
    coordinatesLabel: String? = null,
  ) {
    val clampedX = newNormalizedX.coerceIn(0.10f, 0.90f)
    val clampedY = newNormalizedY.coerceIn(0.10f, 0.90f)
    val dx = clampedX - userGpsNormalizedX
    val dy = clampedY - userGpsNormalizedY
    userGpsNormalizedX = clampedX
    userGpsNormalizedY = clampedY
    if (coordinatesLabel != null) {
      userGpsCoordinatesLabel = coordinatesLabel
    }
    if (!isCameraFollowingUser) {
      mapPanOffsetX = (mapPanOffsetX + dx).coerceIn(-10000f, 10000f)
      mapPanOffsetY = (mapPanOffsetY + dy).coerceIn(-10000f, 10000f)
    }
  }

  // --- Straight-Line Wayfinding Navigation Actions ---

  /**
   * Starts straight-line navigation from the collector's current GPS position to [entityId],
   * ensuring its layer is visible, selecting the entity in collapsed bottom-sheet peek mode, and
   * switching to the Map view with GPS auto-centering enabled.
   */
  fun startNavigationToEntity(entityId: String) {
    val entity = entities.firstOrNull { it.id == entityId } ?: return
    mapLayers = mapLayers.map { layer ->
      if (layer.id == entity.layerId) layer.copy(isVisible = true) else layer
    }
    navigationTargetKind = NavigationTargetKind.ENTITY
    navigationTargetId = entity.id
    selectedEntityId = entity.id
    selectedSubmissionId = null
    isEntityBottomSheetExpanded = false
    isLayersSheetOpen = false
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    mainViewMode = MainSurveyViewMode.MAP
    if (currentScreen != PrototypeScreen.MAIN_SURVEY) {
      currentScreen = PrototypeScreen.MAIN_SURVEY
    }
    recenterMapOnUser()
    val badge = formattedWayfindingBadgeForEntity(entity.id)
    activeSurveyNotice = "Straight-line navigation to ${entity.label} ($badge)"
  }

  /**
   * Starts straight-line navigation from the collector's current GPS position to [submissionId]
   * (targeting its recorded geometry polygon or parent entity location), ensuring both parent
   * entity and form geometry layers are visible, selecting the submission, and switching to Map
   * view.
   */
  fun startNavigationToSubmission(submissionId: String) {
    val sub = allSubmissions.firstOrNull { it.id == submissionId } ?: return
    val parentEntity = entities.firstOrNull { it.id == sub.entityId }
    val geom = submissionGeometries.firstOrNull { it.submissionId == sub.id }
    mapLayers = mapLayers.map { layer ->
      if (
        (parentEntity != null && layer.id == parentEntity.layerId) ||
          (geom != null && layer.id == geom.layerId)
      ) {
        layer.copy(isVisible = true)
      } else {
        layer
      }
    }
    navigationTargetKind = NavigationTargetKind.SUBMISSION
    navigationTargetId = sub.id
    selectedEntityId = parentEntity?.id
    selectedSubmissionId = sub.id
    isEntityBottomSheetExpanded = false
    isLayersSheetOpen = false
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    mainViewMode = MainSurveyViewMode.MAP
    if (currentScreen != PrototypeScreen.MAIN_SURVEY) {
      currentScreen = PrototypeScreen.MAIN_SURVEY
    }
    recenterMapOnUser()
    val badge = formattedWayfindingBadgeForSubmission(sub.id)
    val contextLabel =
      parentEntity?.label?.substringBefore(" •")
        ?: sub.coordinatesLabel.ifBlank { "Standalone Field Log" }
    activeSurveyNotice = "Straight-line navigation to ${sub.formTitle} • $contextLabel ($badge)"
  }

  /** Toggles straight-line navigation to [entityId] on or off. */
  fun toggleNavigationToEntity(entityId: String) {
    if (isNavigatingToEntity(entityId)) {
      stopNavigation()
    } else {
      startNavigationToEntity(entityId)
    }
  }

  /** Toggles straight-line navigation to [submissionId] on or off. */
  fun toggleNavigationToSubmission(submissionId: String) {
    if (isNavigatingToSubmission(submissionId)) {
      stopNavigation()
    } else {
      startNavigationToSubmission(submissionId)
    }
  }

  /**
   * Starts straight-line navigation from the collector's current GPS position to [placeId] and
   * switches to the Map view with GPS auto-centering enabled.
   */
  fun startNavigationToPlace(placeId: String) {
    val place = findPlaceById(placeId) ?: return
    navigationTargetKind = NavigationTargetKind.PLACE
    navigationTargetId = place.id
    selectedPlaceId = place.id
    lastSelectedPlace = place
    selectedEntityId = null
    selectedSubmissionId = null
    isEntityBottomSheetExpanded = false
    isLayersSheetOpen = false
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    mainViewMode = MainSurveyViewMode.MAP
    if (currentScreen != PrototypeScreen.MAIN_SURVEY) {
      currentScreen = PrototypeScreen.MAIN_SURVEY
    }
    recenterMapOnUser()
    val badge = formattedWayfindingBadgeForPlace(place.id)
    activeSurveyNotice = "Straight-line navigation to ${place.name} ($badge)"
  }

  /** Toggles straight-line navigation to [placeId] on or off. */
  fun toggleNavigationToPlace(placeId: String) {
    if (isNavigatingToPlace(placeId)) {
      stopNavigation()
    } else {
      startNavigationToPlace(placeId)
    }
  }

  /** Stops active straight-line navigation and clears the navigation line & HUD banner. */
  fun stopNavigation() {
    navigationTargetKind = null
    navigationTargetId = null
    activeSurveyNotice = null
  }

  /**
   * Advances the collector's simulated GPS blue dot along the active straight-line navigation
   * vector toward the target entity or submission by [stepFraction] (`0.40f` by default, snapping
   * directly onto the destination when within `12` meters).
   */
  fun stepUserTowardNavigationTarget(stepFraction: Float = 0.40f) {
    val nav = activeNavigation ?: return
    val targetX = nav.vector.toNormalizedX
    val targetY = nav.vector.toNormalizedY
    val dx = targetX - userGpsNormalizedX
    val dy = targetY - userGpsNormalizedY
    val fraction = stepFraction.coerceIn(0.10f, 1.0f)
    val nextX =
      if (nav.vector.distanceMeters <= 18) {
        targetX
      } else {
        userGpsNormalizedX + dx * fraction
      }
    val nextY =
      if (nav.vector.distanceMeters <= 18) {
        targetY
      } else {
        userGpsNormalizedY + dy * fraction
      }
    updateUserGpsLocation(nextX, nextY)
  }

  /** Toggles between Light and Dark Ground Material 3 themes. */
  fun toggleDarkTheme() {
    isDarkTheme = !isDarkTheme
  }

  /**
   * Randomly generates and appends [count] polygon map features (`GeospatialEntityItem`)
   * across the active survey region to benchmark Mapbox GL WebGL polygon & marker scaling at 5,000,
   * 10,000, 15,000+ map features.
   */
  fun addRandomSites(count: Int = 5_000) {
    if (count <= 0) return
    val existingSize = entities.size
    val batchSeed = existingSize * 1103515245L + 12345L
    val newFeatures = ArrayList<GeospatialEntityItem>(count)

    for (i in 0 until count) {
      val idx = existingSize + i + 1
      // Fast deterministic LCG + quasi-random 2D Halton/lattice spread so features cover both the
      // immediate viewport ([0.0, 1.0]) and the surrounding valley ([-2.2, 3.2]) without clumping.
      val h1 = ((batchSeed + i * 2654435761L) ushr 16) and 0xFFFFL
      val h2 = ((batchSeed + i * 1597334677L) ushr 16) and 0xFFFFL
      val u = (h1.toFloat() / 65535f)
      val v = (h2.toFloat() / 65535f)

      // 30% within the central viewport [-0.15, 1.15], 70% across the wider survey region [-2.2,
      // 3.2]
      val isInner = (i % 10) < 3
      val nx = if (isInner) -0.15f + u * 1.30f else -2.20f + u * 5.40f
      val ny = if (isInner) -0.15f + v * 1.30f else -2.20f + v * 5.40f

      val statusBucket = i % 4
      val (status, symbol, hexStr, hexLong) =
        when (statusBucket) {
          0 -> listOf("Completed", "✓", "#1E8E3E", 0xFF1E8E3EL)
          1 -> listOf("In progress", "◐", "#F9AB00", 0xFFF9AB00L)
          else -> listOf("Pending", "○", "#E65100", 0xFFE65100L)
        }
      val statusLabel = status as String
      val markerSymbol = symbol as String
      val colorCss = hexStr as String
      val colorHex = hexLong as Long

      val areaHa = ((0.4 + (u * 2.4)) * 100.0).roundToInt() / 100.0
      val perimeterM = (220 + (v * 420)).roundToInt()
      val lat = ((0.4198 - (ny - 0.5f) * 0.018) * 10000.0).roundToInt() / 10000.0
      val lng = ((36.9512 + (nx - 0.5f) * 0.014) * 10000.0).roundToInt() / 10000.0

      newFeatures.add(
        GeospatialEntityItem(
          id = "entity-rnd-$idx",
          label = "Plot RND-$idx • Parcel #$idx",
          datasetId = "coffee_parcels",
          datasetName = "Smallholder Coffee Parcels",
          layerId = "layer-coffee-parcels",
          geoId = "S2-rnd-${idx.toString(16)}",
          geometryTypeLabel = "Polygon",
          areaHectares = areaHa,
          perimeterMeters = perimeterM,
          coordinatesLabel = "${lat}°S, ${lng}°E",
          normalizedX = nx,
          normalizedY = ny,
          colorHex = colorHex,
          properties =
            mapOf(
              "status" to statusLabel,
              "marker-symbol" to markerSymbol,
              "marker-color" to colorCss,
              "stroke" to colorCss,
              "fill" to colorCss,
              "Cooperative" to "Othaya Farmers Co-op",
              "Primary Cultivar" to if (i % 2 == 0) "SL28" else "Ruiru 11",
            ),
          submissions = emptyList(),
          singularTypeLabel = "Coffee Parcel",
          syncStatus = SyncStatus.SYNCED,
        )
      )
    }

    val updatedEntities = entities + newFeatures
    entities = updatedEntities
    surveys = surveys.map { s ->
      if (s.id == activeSurveyId) s.copy(entityCount = updatedEntities.size) else s
    }
    currentScreen = PrototypeScreen.MAIN_SURVEY
    mainViewMode = MainSurveyViewMode.MAP
    activeDrawerSubView = MainDrawerSubView.NONE
    activeSurveyNotice = "Added $count random polygon features (${updatedEntities.size} total map features)"
  }

  /** Resets the onboarding and prototype state back to the initial Sign In screen. */
  fun resetPrototypeFlow() {
    currentScreen = PrototypeScreen.SIGN_IN
    isSignedIn = false
    hasAcceptedTerms = false
    downloadSurveyEntryOrigin = DownloadSurveyEntryOrigin.AFTER_TOS
    isDownloadSurveySignOutPromptOpen = false
    termsCheckboxChecked = true
    searchQuery = ""
    activeSurveyNotice = null
    mainViewMode = MainSurveyViewMode.MAP
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    isLayersSheetOpen = false
    isAvailableFormsSheetOpen = false
    isOfflineBasemapVisible = true
    offlineBasemapStyle = OfflineBasemapStyle.SATELLITE_HYBRID
    mapLayers = defaultMapLayers()
    submissionGeometries = defaultSubmissionGeometries()
    entities = defaultGeospatialEntities()
    places = defaultSurveyPlaces()
    selectedPlaceId = null
    lastSelectedPlace = null
    isAirplaneMode = false
    mapboxPlacesApiResults = emptyList()
    isMapboxPlacesSearching = false
    clearPlatformMapboxPlace()
    standaloneSubmissions = defaultStandaloneSubmissions()
    mutations = defaultMutations()
    selectedEntityId = null
    isEntityBottomSheetExpanded = false
    selectedSubmissionId = null
    navigationTargetKind = null
    navigationTargetId = null
    listSearchQuery = ""
    listFilterTab = ListFilterTab.ALL
    userGpsNormalizedX = 0.50f
    userGpsNormalizedY = 0.50f
    userGpsCoordinatesLabel = "-0.4198°, 36.9512° (±3.2m GPS)"
    isCameraFollowingUser = true
    locationLockState = LocationLockState.LOCKED
    mapPanOffsetX = 0f
    mapPanOffsetY = 0f
    surveys = defaultSampleSurveys()
    closeActiveFormRunner()
    resetDefaultXFormsXml()
  }

  companion object {
    /** Alias for [defaultSurveyPlaces]. */
    fun defaultPlaces(): List<SurveyPlaceItem> = defaultSurveyPlaces()

    /**
     * Default geographic places, towns, landmarks, road junctions, and hydrology features from the
     * Mapbox Places API (`mapbox.places`) searchable via `"Search places or map features..."`.
     */
    fun defaultSurveyPlaces(): List<SurveyPlaceItem> =
      listOf(
        SurveyPlaceItem(
          id = "place-othaya-town",
          name = "Othaya Town Center",
          categoryLabel = "Town",
          regionSubtitle = "Othaya Sub-County, Nyeri • Cooperative Market Hub",
          coordinatesLabel = "0.4192°S, 36.9498°E",
          normalizedX = 0.40f,
          normalizedY = 0.47f,
          zoomDelta = -2.5f,
          longitude = 36.9498,
          latitude = -0.4192,
          bboxMinLng = 36.9220,
          bboxMinLat = -0.4420,
          bboxMaxLng = 36.9780,
          bboxMaxLat = -0.3960,
          mapboxPlaceId = "place.othaya.101",
        ),
        SurveyPlaceItem(
          id = "place-chinga-dam",
          name = "Chinga Dam & Reservoir",
          categoryLabel = "Hydrology / Dam",
          regionSubtitle = "Chinga Ward, Nyeri County • Upper Gura Catchment",
          coordinatesLabel = "0.4258°S, 36.9574°E",
          normalizedX = 0.78f,
          normalizedY = 0.74f,
          zoomDelta = -1.7f,
          longitude = 36.9574,
          latitude = -0.4258,
          bboxMinLng = 36.9430,
          bboxMinLat = -0.4380,
          bboxMaxLng = 36.9720,
          bboxMaxLat = -0.4130,
          mapboxPlaceId = "poi.chinga.102",
        ),
        SurveyPlaceItem(
          id = "place-gura-river-bridge",
          name = "Gura River Crossing",
          categoryLabel = "River Crossing",
          regionSubtitle = "Gura Valley Riparian Corridor • 1,785 m",
          coordinatesLabel = "0.4215°S, 36.9535°E",
          normalizedX = 0.62f,
          normalizedY = 0.56f,
          zoomDelta = 0.2f,
          longitude = 36.9535,
          latitude = -0.4215,
          bboxMinLng = 36.9495,
          bboxMinLat = -0.4255,
          bboxMaxLng = 36.9575,
          bboxMaxLat = -0.4175,
          mapboxPlaceId = "poi.gura.103",
        ),
        SurveyPlaceItem(
          id = "place-karima-forest",
          name = "Karima Hill Forest Reserve",
          categoryLabel = "Forest Reserve",
          regionSubtitle = "Othaya Highlands • Sacred Indigenous Canopy",
          coordinatesLabel = "0.4160°S, 36.9465°E",
          normalizedX = 0.20f,
          normalizedY = 0.22f,
          zoomDelta = -1.7f,
          longitude = 36.9465,
          latitude = -0.4160,
          bboxMinLng = 36.9320,
          bboxMinLat = -0.4280,
          bboxMaxLng = 36.9610,
          bboxMaxLat = -0.4040,
          mapboxPlaceId = "poi.karima.104",
        ),
        SurveyPlaceItem(
          id = "place-nyeri-town",
          name = "Nyeri Town",
          categoryLabel = "Regional Hub",
          regionSubtitle = "Nyeri County Headquarters • Central Highlands",
          coordinatesLabel = "0.4148°S, 36.9510°E",
          normalizedX = 0.49f,
          normalizedY = 0.16f,
          zoomDelta = -3.5f,
          longitude = 36.9510,
          latitude = -0.4148,
          bboxMinLng = 36.8900,
          bboxMinLat = -0.4650,
          bboxMaxLng = 37.0120,
          bboxMaxLat = -0.3650,
          mapboxPlaceId = "place.nyeri.105",
        ),
        SurveyPlaceItem(
          id = "place-iriaini-market",
          name = "Iriaini Coffee & Tea Market",
          categoryLabel = "Village / Market Center",
          regionSubtitle = "Iriaini Ward, Othaya • Smallholder Buying Center",
          coordinatesLabel = "0.4238°S, 36.9478°E",
          normalizedX = 0.26f,
          normalizedY = 0.68f,
          zoomDelta = -0.8f,
          longitude = 36.9478,
          latitude = -0.4238,
          bboxMinLng = 36.9410,
          bboxMinLat = -0.4305,
          bboxMaxLng = 36.9545,
          bboxMaxLat = -0.4170,
          mapboxPlaceId = "poi.iriaini.106",
        ),
        SurveyPlaceItem(
          id = "place-kenya-country",
          name = "Kenya",
          categoryLabel = "Country",
          regionSubtitle = "East Africa • Republic of Kenya",
          coordinatesLabel = "0.0236°N, 37.9062°E",
          normalizedX = 0.50f,
          normalizedY = 0.50f,
          zoomDelta = -10.1f,
          longitude = 37.9062,
          latitude = 0.0236,
          bboxMinLng = 33.9098,
          bboxMinLat = -4.6780,
          bboxMaxLng = 41.8995,
          bboxMaxLat = 5.5060,
          mapboxPlaceId = "country.kenya.01",
        ),
      )

    /**
     * Default sample surveys shared with the user, including the 5 swappable Workbench Example
     * Surveys (each with its own preloaded entities, sample submissions, geometries, map layers,
     * and XForms definition) plus a remote undownloaded survey for offline download testing.
     */
    fun defaultSampleSurveys(): List<SurveyPreviewItem> =
      listOf(
        SurveyPreviewItem(
          id = "survey-single-point-land-use",
          title = "1. Simple Point & Land Use Survey",
          description =
            "Single GPS point (map pan allowed, <= 10m accuracy required) and land-use classification. Forms auto-create land_use_observations entities via save_to.",
          location = "Arusha, Tanzania",
          coordinatesLabel = "3.38°S, 36.68°E",
          offlineSizeLabel = "4.2 MB",
          isDownloaded = true,
          thumbnailTheme = MapThumbnailTheme.SAVANNA,
          entityCount = 3,
        ),
        SurveyPreviewItem(
          id = "survey-sample-plots-forest",
          title = "2. Sample Plot Forest Assessment Survey",
          description =
            "Predefined permanent sample plot entities (SP-01 to SP-05) with forest stand assessment form: selecting the sample plot entity, taking a canopy photo, canopy cover %, and basal area.",
          location = "Pará, Brazil",
          coordinatesLabel = "3.46°S, 62.21°W",
          offlineSizeLabel = "14.8 MB",
          isDownloaded = true,
          thumbnailTheme = MapThumbnailTheme.RAINFOREST,
          entityCount = 5,
        ),
        SurveyPreviewItem(
          id = "survey-commodity-perimeter-center",
          title = "3. Commodity Plot Perimeter & Center Mapping (EUDR)",
          description =
            "Walk forest-risk commodity plot boundary (GPS override / manual pan allowed while walking) and capture the plot center point. Forms auto-create commodity_plots entities via save_to.",
          location = "Ashanti Region, Ghana",
          coordinatesLabel = "6.69°N, 1.62°W",
          offlineSizeLabel = "11.5 MB",
          isDownloaded = true,
          thumbnailTheme = MapThumbnailTheme.WATERSHED,
          entityCount = 3,
        ),
        SurveyPreviewItem(
          id = "survey-household-past-individuals",
          title = "4. Household Panel Survey (Past Individuals)",
          description =
            "Household longitudinal survey using a preloaded roster of past household individuals (IND-101 to IND-106) for residency reconciliation, occupation updates, and new member enrollment.",
          location = "Kakamega, Western Province",
          coordinatesLabel = "0.28°N, 34.75°E",
          offlineSizeLabel = "8.4 MB",
          isDownloaded = true,
          thumbnailTheme = MapThumbnailTheme.COASTAL_DELTA,
          entityCount = 6,
        ),
        SurveyPreviewItem(
          id = "survey-kenya-coffee",
          title = "5. All Form Field Types Showcase (Kenya Coffee)",
          description =
            "EUDR traceability polygon mapping, shade-tree biodiversity inventory, and all 20+ XForms field types showcase for cooperative coffee growers.",
          location = "Nyeri County, Kenya",
          coordinatesLabel = "0.42°S, 36.95°E",
          offlineSizeLabel = "9.6 MB",
          isDownloaded = true,
          thumbnailTheme = MapThumbnailTheme.HIGHLAND_AGRI,
          entityCount = 5,
        ),
        SurveyPreviewItem(
          id = "survey-serengeti-corridor",
          title = "Mekong Delta Mangrove Restoration",
          description =
            "Coastal shoreline erosion monitoring and sapling survival rate audits across intertidal restoration zones.",
          location = "Cần Thơ, Vietnam",
          coordinatesLabel = "9.82°N, 106.34°E",
          offlineSizeLabel = "12.8 MB",
          isDownloaded = false,
          thumbnailTheme = MapThumbnailTheme.PEATLAND,
          entityCount = 204,
        ),
      )

    private val exampleFormDefCache = mutableMapOf<WorkbenchExampleForm, FormDef?>()

    /** Returns a cached parsed [FormDef] for [example], parsing it at most once. */
    fun cachedExampleFormDef(example: WorkbenchExampleForm): FormDef? =
      exampleFormDefCache.getOrPut(example) {
        try {
          XFormsXmlSerializer.deserializeFormDef(example.xformsXml)
        } catch (_: Exception) {
          null
        }
      }

    /** Maps a [WorkbenchExampleForm] to its canonical Example Survey ID. */
    fun surveyIdForExampleForm(example: WorkbenchExampleForm): String =
      when (example) {
        WorkbenchExampleForm.SINGLE_POINT_LAND_USE -> "survey-single-point-land-use"
        WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT -> "survey-sample-plots-forest"
        WorkbenchExampleForm.COMMODITY_PERIMETER_AND_CENTER -> "survey-commodity-perimeter-center"
        WorkbenchExampleForm.HOUSEHOLD_SURVEY_PAST_INDIVIDUALS ->
          "survey-household-past-individuals"
        WorkbenchExampleForm.ALL_FIELD_TYPES -> "survey-kenya-coffee"
      }

    /** Maps an Example Survey ID to its corresponding [WorkbenchExampleForm]. */
    fun exampleFormForSurveyId(surveyId: String): WorkbenchExampleForm =
      when (surveyId) {
        "survey-single-point-land-use" -> WorkbenchExampleForm.SINGLE_POINT_LAND_USE
        "survey-sample-plots-forest" -> WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT
        "survey-commodity-perimeter-center" -> WorkbenchExampleForm.COMMODITY_PERIMETER_AND_CENTER
        "survey-household-past-individuals" ->
          WorkbenchExampleForm.HOUSEHOLD_SURVEY_PAST_INDIVIDUALS
        else -> WorkbenchExampleForm.ALL_FIELD_TYPES
      }

    /** Returns the preloaded entity count for [surveyId] without allocating entity lists. */
    fun preloadedEntityCountForSurvey(surveyId: String): Int =
      when (surveyId) {
        "survey-single-point-land-use" -> 3
        "survey-sample-plots-forest" -> 5
        "survey-commodity-perimeter-center" -> 3
        "survey-household-past-individuals" -> 6
        else -> 5
      }

    /** Returns the preloaded submission count for [surveyId] without allocating submission lists. */
    fun preloadedSubmissionCountForSurvey(surveyId: String): Int =
      when (surveyId) {
        "survey-single-point-land-use" -> 3
        "survey-sample-plots-forest" -> 3
        "survey-commodity-perimeter-center" -> 3
        "survey-household-past-individuals" -> 4
        else -> 9
      }

    /** Returns the preloaded [FormPreviewItem] list for [surveyId]. */
    fun formsForSurvey(surveyId: String): List<FormPreviewItem> =
      when (surveyId) {
        "survey-single-point-land-use" ->
          listOf(
            FormPreviewItem(
              id = "form-single-point-land-use",
              title = "Simple Point & Land Use Observation",
              description =
                "Collect a single point (map pan allowed, <= 10m GPS accuracy required) and primary land-use category. Auto-saves to land_use_observations entity dataset.",
              version = "2026092401",
              targetDatasetId = "land_use_observations",
              targetDatasetName = "Land Use Observations (land_use_observations)",
              questionCount = 3,
              ctaLabel = "Collect Point & Land Use",
              requiresEntity = false,
            )
          )
        "survey-sample-plots-forest" ->
          listOf(
            FormPreviewItem(
              id = "form-sample-plots-forest",
              title = "Sample Plot Entity & Forest Stand Assessment",
              description =
                "Select a predefined sample plot entity (SP-01 to SP-05), capture an upward canopy photo, and record canopy closure %, dominant species, basal area, sapling count, and disturbances.",
              version = "2026092401",
              targetDatasetId = "sample_plots",
              targetDatasetName = "Permanent Forest Sample Plots (sample_plots)",
              questionCount = 9,
              ctaLabel = "Assess Forest Sample Plot",
            )
          )
        "survey-commodity-perimeter-center" ->
          listOf(
            FormPreviewItem(
              id = "form-commodity-perimeter-center",
              title = "Commodity Plot Perimeter & Center Mapping",
              description =
                "Walk the perimeter of a forest-risk commodity plot (manual pan / GPS override allowed) and capture the plot center point. Auto-saves to commodity_plots entity dataset.",
              version = "2026092401",
              targetDatasetId = "commodity_plots",
              targetDatasetName = "Commodity Plots (commodity_plots)",
              questionCount = 6,
              ctaLabel = "Map Plot Perimeter & Center",
              requiresEntity = false,
            )
          )
        "survey-household-past-individuals" ->
          listOf(
            FormPreviewItem(
              id = "form-household-past-individuals",
              title = "Household Follow-Up Survey (Past Individuals)",
              description =
                "Select a preloaded past individual (IND-101 to IND-106), reconcile residency & livelihood status since the 2022 baseline wave, and enroll new household members.",
              version = "2026092401",
              targetDatasetId = "past_individuals",
              targetDatasetName = "Preloaded Past Household Individuals (past_individuals)",
              questionCount = 11,
              ctaLabel = "Conduct Household Follow-Up",
            )
          )
        else -> defaultForms()
      }

    /** Returns the preloaded [GeospatialEntityItem] list for [surveyId]. */
    fun entitiesForSurvey(surveyId: String): List<GeospatialEntityItem> =
      when (surveyId) {
        "survey-single-point-land-use" ->
          listOf(
            GeospatialEntityItem(
              id = "ent-splu-01",
              label = "Primary Forest • -0.4182°, 36.9491°",
              datasetId = "land_use_observations",
              datasetName = "Land Use Observations (land_use_observations)",
              layerId = "layer-land-use-observations",
              geoId = "ENT-SPLU-01",
              geometryTypeLabel = "Point",
              areaHectares = 0.05,
              perimeterMeters = 25,
              coordinatesLabel = "0.4182° S, 36.9491° E",
              normalizedX = 0.30f,
              normalizedY = 0.34f,
              colorHex = 0xFF2E7D32,
              properties =
                mapOf(
                  "status" to "Completed",
                  "marker-symbol" to "✓",
                  "marker-color" to "#1E8E3E",
                  "stroke" to "#1E8E3E",
                  "fill" to "#1E8E3E",
                  "land_use" to "primary_forest",
                  "observer_note" to "Closed-canopy indigenous montane forest stand.",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-splu-01",
                    entityId = "ent-splu-01",
                    entityLabel = "Primary Forest • -0.4182°, 36.9491°",
                    formId = "form-single-point-land-use",
                    formTitle = "Simple Point & Land Use Observation",
                    formVersion = "2026092401",
                    collectorName = "Maya Lin",
                    collectorEmail = "maya.lin@ground-demo.org",
                    timestamp = "2026-09-24 08:15 UTC",
                    targetTypeLabel = "Land Use Observation",
                    coordinatesLabel = "0.4182° S, 36.9491° E (±4.8m <=10m GPS)",
                    fields =
                      listOf(
                        SubmissionFieldEntry(
                          questionName = "sample_point",
                          questionLabel = "Sample Location Point (geopoint)",
                          answerValue = "-0.418200 36.949100 1742.0 4.8 (Pan Allowed, <=10m GPS)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "land_use",
                          questionLabel = "Primary Land Use Category",
                          answerValue = "primary_forest (Primary / Intact Natural Forest)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "observer_note",
                          questionLabel = "Optional Field Note",
                          answerValue = "Closed-canopy indigenous montane forest stand.",
                        ),
                      ),
                  ),
                ),
            ),
            GeospatialEntityItem(
              id = "ent-splu-02",
              label = "Agroforestry • -0.4209°, 36.9524°",
              datasetId = "land_use_observations",
              datasetName = "Land Use Observations (land_use_observations)",
              layerId = "layer-land-use-observations",
              geoId = "ENT-SPLU-02",
              geometryTypeLabel = "Point",
              areaHectares = 0.05,
              perimeterMeters = 25,
              coordinatesLabel = "0.4209° S, 36.9524° E",
              normalizedX = 0.54f,
              normalizedY = 0.56f,
              colorHex = 0xFF2E7D32,
              properties =
                mapOf(
                  "status" to "Completed",
                  "marker-symbol" to "✓",
                  "marker-color" to "#1E8E3E",
                  "stroke" to "#1E8E3E",
                  "fill" to "#1E8E3E",
                  "land_use" to "agroforestry_shade",
                  "observer_note" to "Terraced shade coffee intercropped with Cordia & Grevillea.",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-splu-02",
                    entityId = "ent-splu-02",
                    entityLabel = "Agroforestry • -0.4209°, 36.9524°",
                    formId = "form-single-point-land-use",
                    formTitle = "Simple Point & Land Use Observation",
                    formVersion = "2026092401",
                    collectorName = "David Kamau",
                    collectorEmail = "d.kamau@nyericoop.ke",
                    timestamp = "2026-09-24 09:05 UTC",
                    targetTypeLabel = "Land Use Observation",
                    coordinatesLabel = "0.4209° S, 36.9524° E (±6.2m <=10m GPS)",
                    fields =
                      listOf(
                        SubmissionFieldEntry(
                          questionName = "sample_point",
                          questionLabel = "Sample Location Point (geopoint)",
                          answerValue = "-0.420900 36.952400 1695.0 6.2 (Pan Allowed, <=10m GPS)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "land_use",
                          questionLabel = "Primary Land Use Category",
                          answerValue = "agroforestry_shade (Agroforestry / Shade-Grown Tree Crop)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "observer_note",
                          questionLabel = "Optional Field Note",
                          answerValue = "Terraced shade coffee intercropped with Cordia & Grevillea.",
                        ),
                      ),
                  ),
                ),
            ),
            GeospatialEntityItem(
              id = "ent-splu-03",
              label = "Wetland Riparian • -0.4168°, 36.9538°",
              datasetId = "land_use_observations",
              datasetName = "Land Use Observations (land_use_observations)",
              layerId = "layer-land-use-observations",
              geoId = "ENT-SPLU-03",
              geometryTypeLabel = "Point",
              areaHectares = 0.05,
              perimeterMeters = 25,
              coordinatesLabel = "0.4168° S, 36.9538° E",
              normalizedX = 0.68f,
              normalizedY = 0.30f,
              colorHex = 0xFF2E7D32,
              properties =
                mapOf(
                  "status" to "Completed",
                  "marker-symbol" to "✓",
                  "marker-color" to "#1E8E3E",
                  "stroke" to "#1E8E3E",
                  "fill" to "#1E8E3E",
                  "land_use" to "wetland_riparian",
                  "observer_note" to "15m vegetated riparian buffer along Chania stream.",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-splu-03",
                    entityId = "ent-splu-03",
                    entityLabel = "Wetland Riparian • -0.4168°, 36.9538°",
                    formId = "form-single-point-land-use",
                    formTitle = "Simple Point & Land Use Observation",
                    formVersion = "2026092401",
                    collectorName = "Grace Wanjiku",
                    collectorEmail = "g.wanjiku@nyericoop.ke",
                    timestamp = "2026-09-24 09:42 UTC",
                    targetTypeLabel = "Land Use Observation",
                    coordinatesLabel = "0.4168° S, 36.9538° E (±5.1m <=10m GPS)",
                    fields =
                      listOf(
                        SubmissionFieldEntry(
                          questionName = "sample_point",
                          questionLabel = "Sample Location Point (geopoint)",
                          answerValue = "-0.416800 36.953800 1668.0 5.1 (Pan Allowed, <=10m GPS)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "land_use",
                          questionLabel = "Primary Land Use Category",
                          answerValue = "wetland_riparian (Wetland / Riparian Buffer)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "observer_note",
                          questionLabel = "Optional Field Note",
                          answerValue = "15m vegetated riparian buffer along Chania stream.",
                        ),
                      ),
                  ),
                ),
            ),
          )
        "survey-commodity-perimeter-center" ->
          listOf(
            GeospatialEntityItem(
              id = "ent-eudr-0419",
              label = "Cocoa • EUDR-GH-0419",
              datasetId = "commodity_plots",
              datasetName = "Commodity Plots (commodity_plots)",
              layerId = "layer-commodity-plots",
              geoId = "EUDR-GH-0419",
              geometryTypeLabel = "Polygon",
              areaHectares = 1.42,
              perimeterMeters = 512,
              coordinatesLabel = "0.4194° S, 36.9510° E",
              normalizedX = 0.31f,
              normalizedY = 0.35f,
              colorHex = 0xFFD84315,
              properties =
                mapOf(
                  "status" to "Completed",
                  "marker-symbol" to "✓",
                  "marker-color" to "#1E8E3E",
                  "stroke" to "#D84315",
                  "fill" to "#D84315",
                  "commodity_type" to "cocoa",
                  "farmer_parcel_code" to "EUDR-GH-0419",
                  "estimated_area_ha" to "1.42 ha",
                  "deforestation_free_attestation" to "yes_verified",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-eudr-gh-0419",
                    entityId = "ent-eudr-0419",
                    entityLabel = "Cocoa • EUDR-GH-0419",
                    formId = "form-commodity-perimeter-center",
                    formTitle = "Commodity Plot Perimeter & Center Mapping",
                    formVersion = "2026092401",
                    collectorName = "Maya Lin",
                    collectorEmail = "maya.lin@ground-demo.org",
                    timestamp = "2026-09-24 08:30 UTC",
                    targetTypeLabel = "Commodity Plot",
                    coordinatesLabel = "Center: 0.41935° S, 36.95100° E (±2.4m <=5m GPS, No Pan)",
                    fields =
                      listOf(
                        SubmissionFieldEntry(
                          questionName = "commodity_type",
                          questionLabel = "Forest-Risk Commodity Class (EUDR)",
                          answerValue = "cocoa (Cocoa • Theobroma cacao)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "farmer_parcel_code",
                          questionLabel = "Producer / Cooperative Parcel Code",
                          answerValue = "EUDR-GH-0419",
                        ),
                        SubmissionFieldEntry(
                          questionName = "plot_perimeter",
                          questionLabel = "Commodity Plot Perimeter Walk (Pan Override Allowed)",
                          answerValue =
                            "5 vertices walked (1.42 ha; manual pan adjustment applied on NE boundary corner)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "plot_center_point",
                          questionLabel = "Plot Center Point (No Pan, <=5m GPS Accuracy Required)",
                          answerValue = "-0.419350 36.951000 1679.0 2.4 (±2.4m Hardware GPS Lock)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "estimated_area_ha",
                          questionLabel = "Estimated Plot Area (Hectares)",
                          answerValue = "1.42 ha",
                        ),
                        SubmissionFieldEntry(
                          questionName = "deforestation_free_attestation",
                          questionLabel = "Post-2020 Deforestation-Free Verification",
                          answerValue = "yes_verified (Confirmed No Forest Conversion Since Dec 31, 2020)",
                        ),
                      ),
                  ),
                ),
            ),
            GeospatialEntityItem(
              id = "ent-eudr-0422",
              label = "Coffee • EUDR-GH-0422",
              datasetId = "commodity_plots",
              datasetName = "Commodity Plots (commodity_plots)",
              layerId = "layer-commodity-plots",
              geoId = "EUDR-GH-0422",
              geometryTypeLabel = "Polygon",
              areaHectares = 0.95,
              perimeterMeters = 380,
              coordinatesLabel = "0.4211° S, 36.9487° E",
              normalizedX = 0.56f,
              normalizedY = 0.52f,
              colorHex = 0xFFD84315,
              properties =
                mapOf(
                  "status" to "Completed",
                  "marker-symbol" to "✓",
                  "marker-color" to "#1E8E3E",
                  "stroke" to "#D84315",
                  "fill" to "#D84315",
                  "commodity_type" to "coffee",
                  "farmer_parcel_code" to "EUDR-GH-0422",
                  "estimated_area_ha" to "0.95 ha",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-eudr-gh-0422",
                    entityId = "ent-eudr-0422",
                    entityLabel = "Coffee • EUDR-GH-0422",
                    formId = "form-commodity-perimeter-center",
                    formTitle = "Commodity Plot Perimeter & Center Mapping",
                    formVersion = "2026092401",
                    collectorName = "Samuel Kariuki",
                    collectorEmail = "s.kariuki@nyericoop.ke",
                    timestamp = "2026-09-24 09:25 UTC",
                    targetTypeLabel = "Commodity Plot",
                    coordinatesLabel = "Center: 0.42110° S, 36.94870° E (±3.1m <=5m GPS, No Pan)",
                    fields =
                      listOf(
                        SubmissionFieldEntry(
                          questionName = "commodity_type",
                          questionLabel = "Forest-Risk Commodity Class (EUDR)",
                          answerValue = "coffee (Coffee • Coffea arabica)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "farmer_parcel_code",
                          questionLabel = "Producer / Cooperative Parcel Code",
                          answerValue = "EUDR-GH-0422",
                        ),
                        SubmissionFieldEntry(
                          questionName = "plot_perimeter",
                          questionLabel = "Commodity Plot Perimeter Walk (Pan Override Allowed)",
                          answerValue = "4 vertices walked (0.95 ha)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "plot_center_point",
                          questionLabel = "Plot Center Point (No Pan, <=5m GPS Accuracy Required)",
                          answerValue = "-0.421100 36.948700 1704.0 3.1 (±3.1m Hardware GPS Lock)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "estimated_area_ha",
                          questionLabel = "Estimated Plot Area (Hectares)",
                          answerValue = "0.95 ha",
                        ),
                      ),
                  ),
                ),
            ),
            GeospatialEntityItem(
              id = "ent-eudr-0428",
              label = "Rubber • EUDR-GH-0428",
              datasetId = "commodity_plots",
              datasetName = "Commodity Plots (commodity_plots)",
              layerId = "layer-commodity-plots",
              geoId = "EUDR-GH-0428",
              geometryTypeLabel = "Polygon",
              areaHectares = 2.18,
              perimeterMeters = 640,
              coordinatesLabel = "0.4176° S, 36.9532° E",
              normalizedX = 0.69f,
              normalizedY = 0.29f,
              colorHex = 0xFFD84315,
              properties =
                mapOf(
                  "status" to "Completed",
                  "marker-symbol" to "✓",
                  "marker-color" to "#1E8E3E",
                  "stroke" to "#D84315",
                  "fill" to "#D84315",
                  "commodity_type" to "rubber",
                  "farmer_parcel_code" to "EUDR-GH-0428",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-eudr-gh-0428",
                    entityId = "ent-eudr-0428",
                    entityLabel = "Rubber • EUDR-GH-0428",
                    formId = "form-commodity-perimeter-center",
                    formTitle = "Commodity Plot Perimeter & Center Mapping",
                    formVersion = "2026092401",
                    collectorName = "David Kamau",
                    collectorEmail = "d.kamau@nyericoop.ke",
                    timestamp = "2026-09-24 10:12 UTC",
                    targetTypeLabel = "Commodity Plot",
                    coordinatesLabel = "Center: 0.41760° S, 36.95320° E (±4.2m <=5m GPS, No Pan)",
                    fields =
                      listOf(
                        SubmissionFieldEntry(
                          questionName = "commodity_type",
                          questionLabel = "Forest-Risk Commodity Class (EUDR)",
                          answerValue = "rubber (Natural Rubber • Hevea brasiliensis)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "farmer_parcel_code",
                          questionLabel = "Producer / Cooperative Parcel Code",
                          answerValue = "EUDR-GH-0428",
                        ),
                        SubmissionFieldEntry(
                          questionName = "plot_perimeter",
                          questionLabel = "Commodity Plot Perimeter Walk (Pan Override Allowed)",
                          answerValue = "5 vertices walked (2.18 ha; pan override along stream ravine)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "plot_center_point",
                          questionLabel = "Plot Center Point (No Pan, <=5m GPS Accuracy Required)",
                          answerValue = "-0.417600 36.953200 1662.0 4.2 (±4.2m Hardware GPS Lock)",
                        ),
                      ),
                  ),
                ),
            ),
          )
        "survey-sample-plots-forest" ->
          listOf(
            GeospatialEntityItem(
              id = "plot_sp01",
              label = "Plot SP-01 • Upper Montane Buffer",
              datasetId = "sample_plots",
              datasetName = "Permanent Forest Sample Plots (sample_plots)",
              layerId = "layer-sample-plots",
              geoId = "SP-01-MONTANE",
              geometryTypeLabel = "Polygon",
              areaHectares = 0.13,
              perimeterMeters = 126,
              coordinatesLabel = "0.4182° S, 36.9491° E",
              normalizedX = 0.30f,
              normalizedY = 0.34f,
              colorHex = 0xFF1B5E20,
              properties =
                mapOf(
                  "status" to "Completed",
                  "marker-symbol" to "✓",
                  "marker-color" to "#1E8E3E",
                  "stroke" to "#1B5E20",
                  "fill" to "#1B5E20",
                  "plot_code" to "SP-01",
                  "stratum" to "Montane Moist Indigenous Forest",
                  "elevation_m" to "1840m",
                  "plot_radius_m" to "20m (0.125 ha)",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-sp01-assess-2026",
                    entityId = "plot_sp01",
                    entityLabel = "Plot SP-01 • Upper Montane Buffer",
                    formId = "form-sample-plots-forest",
                    formTitle = "Sample Plot Entity & Forest Stand Assessment",
                    formVersion = "2026092401",
                    collectorName = "Maya Lin",
                    collectorEmail = "maya.lin@ground-demo.org",
                    timestamp = "2026-09-23 09:20 UTC",
                    targetTypeLabel = "Sample Plot",
                    coordinatesLabel = "0.4182° S, 36.9491° E",
                    fields =
                      listOf(
                        SubmissionFieldEntry(
                          questionName = "sample_plot_entity",
                          questionLabel = "Select Predefined Sample Plot Entity",
                          answerValue = "plot_sp01 (Plot SP-01 • Upper Montane Buffer)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "selected_plot_stratum",
                          questionLabel = "Preloaded Plot Forest Stratum",
                          answerValue = "Montane Moist Indigenous Forest",
                        ),
                        SubmissionFieldEntry(
                          questionName = "canopy_photo",
                          questionLabel = "North-Facing Hemispherical Canopy Photo",
                          answerValue = "📷 hemispherical_canopy_sp01.jpg",
                        ),
                        SubmissionFieldEntry(
                          questionName = "canopy_cover_pct",
                          questionLabel = "Measured Canopy Closure (%)",
                          answerValue = "78%",
                        ),
                        SubmissionFieldEntry(
                          questionName = "dominant_species",
                          questionLabel = "Dominant Overstory Tree Species",
                          answerValue = "Podocarpus latifolius",
                        ),
                        SubmissionFieldEntry(
                          questionName = "stand_basal_area_m2_ha",
                          questionLabel = "Stand Basal Area (m²/ha)",
                          answerValue = "28.5",
                        ),
                        SubmissionFieldEntry(
                          questionName = "regenerating_saplings_count",
                          questionLabel = "Natural Regeneration Count (Saplings > 50cm)",
                          answerValue = "18",
                        ),
                        SubmissionFieldEntry(
                          questionName = "disturbance_indicators",
                          questionLabel = "Observed Forest Disturbance Signs",
                          answerValue = "none (Undisturbed Stand)",
                        ),
                      ),
                  )
                ),
            ),
            GeospatialEntityItem(
              id = "plot_sp02",
              label = "Plot SP-02 • Riparian Gallery Transect",
              datasetId = "sample_plots",
              datasetName = "Permanent Forest Sample Plots (sample_plots)",
              layerId = "layer-sample-plots",
              geoId = "SP-02-RIPARIAN",
              geometryTypeLabel = "Polygon",
              areaHectares = 0.13,
              perimeterMeters = 126,
              coordinatesLabel = "0.4196° S, 36.9522° E",
              normalizedX = 0.56f,
              normalizedY = 0.42f,
              colorHex = 0xFF1B5E20,
              properties =
                mapOf(
                  "status" to "Completed",
                  "marker-symbol" to "✓",
                  "marker-color" to "#1E8E3E",
                  "stroke" to "#1B5E20",
                  "fill" to "#1B5E20",
                  "plot_code" to "SP-02",
                  "stratum" to "Riparian Corridor Restoration",
                  "elevation_m" to "1715m",
                  "plot_radius_m" to "20m (0.125 ha)",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-sp02-assess-2026",
                    entityId = "plot_sp02",
                    entityLabel = "Plot SP-02 • Riparian Gallery Transect",
                    formId = "form-sample-plots-forest",
                    formTitle = "Sample Plot Entity & Forest Stand Assessment",
                    formVersion = "2026092401",
                    collectorName = "Samuel Kariuki",
                    collectorEmail = "s.kariuki@nyericoop.ke",
                    timestamp = "2026-09-23 11:05 UTC",
                    targetTypeLabel = "Sample Plot",
                    coordinatesLabel = "0.4196° S, 36.9522° E",
                    fields =
                      listOf(
                        SubmissionFieldEntry(
                          questionName = "sample_plot_entity",
                          questionLabel = "Select Predefined Sample Plot Entity",
                          answerValue = "plot_sp02 (Plot SP-02 • Riparian Gallery Transect)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "canopy_photo",
                          questionLabel = "North-Facing Hemispherical Canopy Photo",
                          answerValue = "📷 hemispherical_canopy_sp02.jpg",
                        ),
                        SubmissionFieldEntry(
                          questionName = "canopy_cover_pct",
                          questionLabel = "Measured Canopy Closure (%)",
                          answerValue = "84%",
                        ),
                        SubmissionFieldEntry(
                          questionName = "dominant_species",
                          questionLabel = "Dominant Overstory Tree Species",
                          answerValue = "Syzygium guineense",
                        ),
                        SubmissionFieldEntry(
                          questionName = "stand_basal_area_m2_ha",
                          questionLabel = "Stand Basal Area (m²/ha)",
                          answerValue = "31.2",
                        ),
                        SubmissionFieldEntry(
                          questionName = "regenerating_saplings_count",
                          questionLabel = "Natural Regeneration Count (Saplings > 50cm)",
                          answerValue = "22",
                        ),
                      ),
                  )
                ),
            ),
            GeospatialEntityItem(
              id = "plot_sp03",
              label = "Plot SP-03 • Shade Agroforestry Core",
              datasetId = "sample_plots",
              datasetName = "Permanent Forest Sample Plots (sample_plots)",
              layerId = "layer-sample-plots",
              geoId = "SP-03-AGROFOREST",
              geometryTypeLabel = "Polygon",
              areaHectares = 0.13,
              perimeterMeters = 126,
              coordinatesLabel = "0.4214° S, 36.9484° E",
              normalizedX = 0.26f,
              normalizedY = 0.62f,
              colorHex = 0xFF1B5E20,
              properties =
                mapOf(
                  "status" to "In progress",
                  "marker-symbol" to "◐",
                  "marker-color" to "#F9AB00",
                  "stroke" to "#F9AB00",
                  "fill" to "#F9AB00",
                  "plot_code" to "SP-03",
                  "stratum" to "Multi-Strata Shade Coffee",
                  "elevation_m" to "1690m",
                  "plot_radius_m" to "20m (0.125 ha)",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-sp03-assess-2026",
                    entityId = "plot_sp03",
                    entityLabel = "Plot SP-03 • Shade Agroforestry Core",
                    formId = "form-sample-plots-forest",
                    formTitle = "Sample Plot Entity & Forest Stand Assessment",
                    formVersion = "2026092401",
                    collectorName = "Maya Lin",
                    collectorEmail = "maya.lin@ground-demo.org",
                    timestamp = "2026-09-24 07:50 UTC",
                    targetTypeLabel = "Sample Plot",
                    coordinatesLabel = "0.4214° S, 36.9484° E",
                    fields =
                      listOf(
                        SubmissionFieldEntry(
                          questionName = "sample_plot_entity",
                          questionLabel = "Select Predefined Sample Plot Entity",
                          answerValue = "plot_sp03 (Plot SP-03 • Shade Agroforestry Core)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "canopy_photo",
                          questionLabel = "North-Facing Hemispherical Canopy Photo",
                          answerValue = "📷 hemispherical_canopy_sp03.jpg",
                        ),
                        SubmissionFieldEntry(
                          questionName = "canopy_cover_pct",
                          questionLabel = "Measured Canopy Closure (%)",
                          answerValue = "65%",
                        ),
                        SubmissionFieldEntry(
                          questionName = "dominant_species",
                          questionLabel = "Dominant Overstory Tree Species",
                          answerValue = "Cordia africana",
                        ),
                        SubmissionFieldEntry(
                          questionName = "stand_basal_area_m2_ha",
                          questionLabel = "Stand Basal Area (m²/ha)",
                          answerValue = "19.4",
                        ),
                      ),
                  )
                ),
            ),
            GeospatialEntityItem(
              id = "plot_sp04",
              label = "Plot SP-04 • Community Forest Edge",
              datasetId = "sample_plots",
              datasetName = "Permanent Forest Sample Plots (sample_plots)",
              layerId = "layer-sample-plots",
              geoId = "SP-04-EDGE",
              geometryTypeLabel = "Polygon",
              areaHectares = 0.13,
              perimeterMeters = 126,
              coordinatesLabel = "0.4172° S, 36.9541° E",
              normalizedX = 0.72f,
              normalizedY = 0.28f,
              colorHex = 0xFF1B5E20,
              properties =
                mapOf(
                  "status" to "Pending",
                  "marker-symbol" to "○",
                  "marker-color" to "#E65100",
                  "stroke" to "#E65100",
                  "fill" to "#E65100",
                  "plot_code" to "SP-04",
                  "stratum" to "Secondary Enrichment Planting",
                  "elevation_m" to "1795m",
                  "plot_radius_m" to "20m (0.125 ha)",
                ),
              submissions = emptyList(),
            ),
            GeospatialEntityItem(
              id = "plot_sp05",
              label = "Plot SP-05 • Ridge Benchmark Control",
              datasetId = "sample_plots",
              datasetName = "Permanent Forest Sample Plots (sample_plots)",
              layerId = "layer-sample-plots",
              geoId = "SP-05-BENCHMARK",
              geometryTypeLabel = "Polygon",
              areaHectares = 0.13,
              perimeterMeters = 126,
              coordinatesLabel = "0.4228° S, 36.9535° E",
              normalizedX = 0.68f,
              normalizedY = 0.68f,
              colorHex = 0xFF1B5E20,
              properties =
                mapOf(
                  "status" to "Pending",
                  "marker-symbol" to "○",
                  "marker-color" to "#E65100",
                  "stroke" to "#E65100",
                  "fill" to "#E65100",
                  "plot_code" to "SP-05",
                  "stratum" to "Intact Reference Stand",
                  "elevation_m" to "1910m",
                  "plot_radius_m" to "20m (0.125 ha)",
                ),
              submissions = emptyList(),
            ),
          )
        "survey-household-past-individuals" ->
          listOf(
            GeospatialEntityItem(
              id = "ind_101",
              label = "IND-101 • Amina Wanjiku (HH-KAK-014, Head)",
              datasetId = "past_individuals",
              datasetName = "Preloaded Past Household Individuals (past_individuals)",
              layerId = "layer-past-individuals",
              geoId = "IND-101-KAK014",
              geometryTypeLabel = "Point",
              areaHectares = 0.0,
              perimeterMeters = 0,
              coordinatesLabel = "0.4184° S, 36.9495° E",
              normalizedX = 0.32f,
              normalizedY = 0.36f,
              colorHex = 0xFF6A1B9A,
              properties =
                mapOf(
                  "status" to "Completed",
                  "marker-symbol" to "✓",
                  "marker-color" to "#1E8E3E",
                  "stroke" to "#6A1B9A",
                  "fill" to "#6A1B9A",
                  "individual_id" to "ind_101",
                  "household_code" to "HH-KAK-014",
                  "relationship" to "Household Head",
                  "baseline_age_2022" to "44",
                  "prior_occupation_2022" to "Smallholder Coffee & Maize",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-ind101-wave4",
                    entityId = "ind_101",
                    entityLabel = "IND-101 • Amina Wanjiku (HH-KAK-014, Head)",
                    formId = "form-household-past-individuals",
                    formTitle = "Household Follow-Up Survey (Past Individuals)",
                    formVersion = "2026092401",
                    collectorName = "Grace Wanjiku",
                    collectorEmail = "g.wanjiku@nyericoop.ke",
                    timestamp = "2026-09-23 14:10 UTC",
                    targetTypeLabel = "Past Individual",
                    coordinatesLabel = "0.4184° S, 36.9495° E",
                    fields =
                      listOf(
                        SubmissionFieldEntry(
                          questionName = "primary_respondent_id",
                          questionLabel = "Select Preloaded Past Individual",
                          answerValue = "ind_101 (IND-101 • Amina Wanjiku)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "residency_status",
                          questionLabel = "2026 Residency Verification Status",
                          answerValue = "present_resident (Still Residing in Household)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "current_primary_occupation",
                          questionLabel = "Current Primary Livelihood / Occupation",
                          answerValue = "smallholder_farming",
                        ),
                        SubmissionFieldEntry(
                          questionName = "household_size_today",
                          questionLabel = "Total Household Members Currently Residing",
                          answerValue = "6",
                        ),
                        SubmissionFieldEntry(
                          questionName = "dwelling_roof_material",
                          questionLabel = "Main Dwelling Roof Material",
                          answerValue = "iron_sheet (Corrugated Iron / Mabati)",
                        ),
                      ),
                  )
                ),
            ),
            GeospatialEntityItem(
              id = "ind_102",
              label = "IND-102 • Samuel Ochieng (HH-KAK-014, Spouse)",
              datasetId = "past_individuals",
              datasetName = "Preloaded Past Household Individuals (past_individuals)",
              layerId = "layer-past-individuals",
              geoId = "IND-102-KAK014",
              geometryTypeLabel = "Point",
              areaHectares = 0.0,
              perimeterMeters = 0,
              coordinatesLabel = "0.4187° S, 36.9501° E",
              normalizedX = 0.38f,
              normalizedY = 0.39f,
              colorHex = 0xFF6A1B9A,
              properties =
                mapOf(
                  "status" to "Completed",
                  "marker-symbol" to "✓",
                  "marker-color" to "#1E8E3E",
                  "stroke" to "#6A1B9A",
                  "fill" to "#6A1B9A",
                  "individual_id" to "ind_102",
                  "household_code" to "HH-KAK-014",
                  "relationship" to "Spouse",
                  "baseline_age_2022" to "47",
                  "prior_occupation_2022" to "Dairy & Agro-Processing",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-ind102-wave4",
                    entityId = "ind_102",
                    entityLabel = "IND-102 • Samuel Ochieng (HH-KAK-014, Spouse)",
                    formId = "form-household-past-individuals",
                    formTitle = "Household Follow-Up Survey (Past Individuals)",
                    formVersion = "2026092401",
                    collectorName = "Grace Wanjiku",
                    collectorEmail = "g.wanjiku@nyericoop.ke",
                    timestamp = "2026-09-23 14:28 UTC",
                    targetTypeLabel = "Past Individual",
                    coordinatesLabel = "0.4187° S, 36.9501° E",
                    fields =
                      listOf(
                        SubmissionFieldEntry(
                          questionName = "primary_respondent_id",
                          questionLabel = "Select Preloaded Past Individual",
                          answerValue = "ind_102 (IND-102 • Samuel Ochieng)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "residency_status",
                          questionLabel = "2026 Residency Verification Status",
                          answerValue = "present_resident",
                        ),
                        SubmissionFieldEntry(
                          questionName = "current_primary_occupation",
                          questionLabel = "Current Primary Livelihood / Occupation",
                          answerValue = "agri_processing",
                        ),
                      ),
                  )
                ),
            ),
            GeospatialEntityItem(
              id = "ind_103",
              label = "IND-103 • Grace Atieno (HH-KAK-022, Head)",
              datasetId = "past_individuals",
              datasetName = "Preloaded Past Household Individuals (past_individuals)",
              layerId = "layer-past-individuals",
              geoId = "IND-103-KAK022",
              geometryTypeLabel = "Point",
              areaHectares = 0.0,
              perimeterMeters = 0,
              coordinatesLabel = "0.4202° S, 36.9526° E",
              normalizedX = 0.58f,
              normalizedY = 0.48f,
              colorHex = 0xFF6A1B9A,
              properties =
                mapOf(
                  "status" to "Completed",
                  "marker-symbol" to "✓",
                  "marker-color" to "#1E8E3E",
                  "stroke" to "#6A1B9A",
                  "fill" to "#6A1B9A",
                  "individual_id" to "ind_103",
                  "household_code" to "HH-KAK-022",
                  "relationship" to "Household Head",
                  "baseline_age_2022" to "38",
                  "prior_occupation_2022" to "Tree Nursery & Seedlings",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-ind103-wave4",
                    entityId = "ind_103",
                    entityLabel = "IND-103 • Grace Atieno (HH-KAK-022, Head)",
                    formId = "form-household-past-individuals",
                    formTitle = "Household Follow-Up Survey (Past Individuals)",
                    formVersion = "2026092401",
                    collectorName = "Maya Lin",
                    collectorEmail = "maya.lin@ground-demo.org",
                    timestamp = "2026-09-23 15:40 UTC",
                    targetTypeLabel = "Past Individual",
                    coordinatesLabel = "0.4202° S, 36.9526° E",
                    fields =
                      listOf(
                        SubmissionFieldEntry(
                          questionName = "primary_respondent_id",
                          questionLabel = "Select Preloaded Past Individual",
                          answerValue = "ind_103 (IND-103 • Grace Atieno)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "residency_status",
                          questionLabel = "2026 Residency Verification Status",
                          answerValue = "present_resident",
                        ),
                        SubmissionFieldEntry(
                          questionName = "current_primary_occupation",
                          questionLabel = "Current Primary Livelihood / Occupation",
                          answerValue = "off_farm_trade",
                        ),
                      ),
                  )
                ),
            ),
            GeospatialEntityItem(
              id = "ind_104",
              label = "IND-104 • David Barasa (HH-KAK-014, Child)",
              datasetId = "past_individuals",
              datasetName = "Preloaded Past Household Individuals (past_individuals)",
              layerId = "layer-past-individuals",
              geoId = "IND-104-KAK014",
              geometryTypeLabel = "Point",
              areaHectares = 0.0,
              perimeterMeters = 0,
              coordinatesLabel = "0.4215° S, 36.9488° E",
              normalizedX = 0.29f,
              normalizedY = 0.61f,
              colorHex = 0xFF6A1B9A,
              properties =
                mapOf(
                  "status" to "In progress",
                  "marker-symbol" to "◐",
                  "marker-color" to "#F9AB00",
                  "stroke" to "#F9AB00",
                  "fill" to "#F9AB00",
                  "individual_id" to "ind_104",
                  "household_code" to "HH-KAK-014",
                  "relationship" to "Adult Child",
                  "baseline_age_2022" to "19",
                  "prior_occupation_2022" to "Secondary Student",
                ),
              submissions =
                listOf(
                  SubmissionPreviewItem(
                    id = "sub-ind104-wave4",
                    entityId = "ind_104",
                    entityLabel = "IND-104 • David Barasa (HH-KAK-014, Child)",
                    formId = "form-household-past-individuals",
                    formTitle = "Household Follow-Up Survey (Past Individuals)",
                    formVersion = "2026092401",
                    collectorName = "Maya Lin",
                    collectorEmail = "maya.lin@ground-demo.org",
                    timestamp = "2026-09-24 08:10 UTC",
                    targetTypeLabel = "Past Individual",
                    coordinatesLabel = "0.4215° S, 36.9488° E",
                    fields =
                      listOf(
                        SubmissionFieldEntry(
                          questionName = "primary_respondent_id",
                          questionLabel = "Select Preloaded Past Individual",
                          answerValue = "ind_104 (IND-104 • David Barasa)",
                        ),
                        SubmissionFieldEntry(
                          questionName = "residency_status",
                          questionLabel = "2026 Residency Verification Status",
                          answerValue = "moved_within_district",
                        ),
                        SubmissionFieldEntry(
                          questionName = "migration_destination",
                          questionLabel = "Destination Community / District",
                          answerValue = "Kakamega Town Polytechnic Campus",
                        ),
                      ),
                  )
                ),
            ),
            GeospatialEntityItem(
              id = "ind_105",
              label = "IND-105 • Esther Nekesa (HH-KAK-031, Head)",
              datasetId = "past_individuals",
              datasetName = "Preloaded Past Household Individuals (past_individuals)",
              layerId = "layer-past-individuals",
              geoId = "IND-105-KAK031",
              geometryTypeLabel = "Point",
              areaHectares = 0.0,
              perimeterMeters = 0,
              coordinatesLabel = "0.4174° S, 36.9539° E",
              normalizedX = 0.70f,
              normalizedY = 0.30f,
              colorHex = 0xFF6A1B9A,
              properties =
                mapOf(
                  "status" to "Pending",
                  "marker-symbol" to "○",
                  "marker-color" to "#E65100",
                  "stroke" to "#E65100",
                  "fill" to "#E65100",
                  "individual_id" to "ind_105",
                  "household_code" to "HH-KAK-031",
                  "relationship" to "Household Head",
                  "baseline_age_2022" to "61",
                  "prior_occupation_2022" to "Beekeeping & Honey Cooperative",
                ),
              submissions = emptyList(),
            ),
            GeospatialEntityItem(
              id = "ind_106",
              label = "IND-106 • Peter Wafula (HH-KAK-039, Head)",
              datasetId = "past_individuals",
              datasetName = "Preloaded Past Household Individuals (past_individuals)",
              layerId = "layer-past-individuals",
              geoId = "IND-106-KAK039",
              geometryTypeLabel = "Point",
              areaHectares = 0.0,
              perimeterMeters = 0,
              coordinatesLabel = "0.4226° S, 36.9532° E",
              normalizedX = 0.66f,
              normalizedY = 0.66f,
              colorHex = 0xFF6A1B9A,
              properties =
                mapOf(
                  "status" to "Pending",
                  "marker-symbol" to "○",
                  "marker-color" to "#E65100",
                  "stroke" to "#E65100",
                  "fill" to "#E65100",
                  "individual_id" to "ind_106",
                  "household_code" to "HH-KAK-039",
                  "relationship" to "Household Head",
                  "baseline_age_2022" to "52",
                  "prior_occupation_2022" to "Smallholder Tea & Agroforestry",
                ),
              submissions = emptyList(),
            ),
          )
        else -> defaultGeospatialEntities()
      }

    /** Returns the preloaded standalone [SubmissionPreviewItem] list for [surveyId]. */
    fun standaloneSubmissionsForSurvey(surveyId: String): List<SubmissionPreviewItem> =
      when (surveyId) {
        "survey-single-point-land-use" -> emptyList()
        "survey-commodity-perimeter-center" -> emptyList()
        "survey-sample-plots-forest" -> emptyList()
        "survey-household-past-individuals" -> emptyList()
        else -> defaultStandaloneSubmissions()
      }

    /** Returns the preloaded [SubmissionGeometryPolygon] list for [surveyId]. */
    fun submissionGeometriesForSurvey(surveyId: String): List<SubmissionGeometryPolygon> =
      when (surveyId) {
        "survey-single-point-land-use" ->
          listOf(
            SubmissionGeometryPolygon(
              id = "geom-splu-01",
              submissionId = "sub-splu-01",
              entityId = "",
              layerId = "layer-form-single-point-land-use",
              formId = "form-single-point-land-use",
              formTitle = "Simple Point & Land Use Observation",
              fieldPath = "sample_point",
              questionLabel = "Sample Location Point (geopoint <=10m GPS)",
              shortMapBadge = "📍 Primary Forest (±4.8m)",
              collectorName = "Maya Lin",
              timestamp = "2026-09-24 08:15 UTC",
              areaHectares = 0.05,
              vertexCount = 4,
              normalizedX = 0.30f,
              normalizedY = 0.34f,
              widthFraction = 0.14f,
              heightFraction = 0.10f,
              colorHex = 0xFF2E7D32,
            ),
            SubmissionGeometryPolygon(
              id = "geom-splu-02",
              submissionId = "sub-splu-02",
              entityId = "",
              layerId = "layer-form-single-point-land-use",
              formId = "form-single-point-land-use",
              formTitle = "Simple Point & Land Use Observation",
              fieldPath = "sample_point",
              questionLabel = "Sample Location Point (geopoint <=10m GPS)",
              shortMapBadge = "📍 Agroforestry (±6.2m)",
              collectorName = "David Kamau",
              timestamp = "2026-09-24 09:05 UTC",
              areaHectares = 0.05,
              vertexCount = 4,
              normalizedX = 0.54f,
              normalizedY = 0.56f,
              widthFraction = 0.14f,
              heightFraction = 0.10f,
              colorHex = 0xFF2E7D32,
            ),
            SubmissionGeometryPolygon(
              id = "geom-splu-03",
              submissionId = "sub-splu-03",
              entityId = "",
              layerId = "layer-form-single-point-land-use",
              formId = "form-single-point-land-use",
              formTitle = "Simple Point & Land Use Observation",
              fieldPath = "sample_point",
              questionLabel = "Sample Location Point (geopoint <=10m GPS)",
              shortMapBadge = "📍 Riparian Buffer (±5.1m)",
              collectorName = "Grace Wanjiku",
              timestamp = "2026-09-24 09:42 UTC",
              areaHectares = 0.05,
              vertexCount = 4,
              normalizedX = 0.68f,
              normalizedY = 0.30f,
              widthFraction = 0.14f,
              heightFraction = 0.10f,
              colorHex = 0xFF0277BD,
            ),
          )
        "survey-sample-plots-forest" ->
          listOf(
            SubmissionGeometryPolygon(
              id = "geom-sp-01",
              submissionId = "sub-sp01-assess-2026",
              entityId = "plot_sp01",
              layerId = "layer-form-sample-plots-forest",
              formId = "form-sample-plots-forest",
              formTitle = "Sample Plot Entity & Forest Stand Assessment",
              fieldPath = "sample_plot_entity",
              questionLabel = "Sample Plot Assessment Footprint (20m radius)",
              shortMapBadge = "SP-01 • 78% Canopy (📷)",
              collectorName = "Maya Lin",
              timestamp = "2026-09-23 09:20 UTC",
              areaHectares = 0.13,
              vertexCount = 12,
              normalizedX = 0.30f,
              normalizedY = 0.34f,
              widthFraction = 0.18f,
              heightFraction = 0.13f,
              colorHex = 0xFF00897B,
            ),
            SubmissionGeometryPolygon(
              id = "geom-sp-02",
              submissionId = "sub-sp02-assess-2026",
              entityId = "plot_sp02",
              layerId = "layer-form-sample-plots-forest",
              formId = "form-sample-plots-forest",
              formTitle = "Sample Plot Entity & Forest Stand Assessment",
              fieldPath = "sample_plot_entity",
              questionLabel = "Sample Plot Assessment Footprint (20m radius)",
              shortMapBadge = "SP-02 • 84% Canopy (📷)",
              collectorName = "Samuel Kariuki",
              timestamp = "2026-09-23 11:05 UTC",
              areaHectares = 0.13,
              vertexCount = 12,
              normalizedX = 0.56f,
              normalizedY = 0.42f,
              widthFraction = 0.18f,
              heightFraction = 0.13f,
              colorHex = 0xFF00897B,
            ),
            SubmissionGeometryPolygon(
              id = "geom-sp-03",
              submissionId = "sub-sp03-assess-2026",
              entityId = "plot_sp03",
              layerId = "layer-form-sample-plots-forest",
              formId = "form-sample-plots-forest",
              formTitle = "Sample Plot Entity & Forest Stand Assessment",
              fieldPath = "sample_plot_entity",
              questionLabel = "Sample Plot Assessment Footprint (20m radius)",
              shortMapBadge = "SP-03 • 65% Canopy (📷)",
              collectorName = "Maya Lin",
              timestamp = "2026-09-24 07:50 UTC",
              areaHectares = 0.13,
              vertexCount = 12,
              normalizedX = 0.26f,
              normalizedY = 0.62f,
              widthFraction = 0.18f,
              heightFraction = 0.13f,
              colorHex = 0xFF00897B,
            ),
          )
        "survey-commodity-perimeter-center" ->
          listOf(
            SubmissionGeometryPolygon(
              id = "geom-eudr-0419",
              submissionId = "sub-eudr-gh-0419",
              entityId = "",
              layerId = "layer-form-commodity-perimeter",
              formId = "form-commodity-perimeter-center",
              formTitle = "Commodity Plot Perimeter & Center Mapping",
              fieldPath = "plot_perimeter",
              questionLabel = "Walked Commodity Plot Perimeter (geoshape) + <=5m Center",
              shortMapBadge = "Cocoa 1.42ha • Center ±2.4m",
              collectorName = "Maya Lin",
              timestamp = "2026-09-24 08:30 UTC",
              areaHectares = 1.42,
              vertexCount = 5,
              normalizedX = 0.31f,
              normalizedY = 0.35f,
              widthFraction = 0.24f,
              heightFraction = 0.16f,
              colorHex = 0xFFD84315,
            ),
            SubmissionGeometryPolygon(
              id = "geom-eudr-0422",
              submissionId = "sub-eudr-gh-0422",
              entityId = "",
              layerId = "layer-form-commodity-perimeter",
              formId = "form-commodity-perimeter-center",
              formTitle = "Commodity Plot Perimeter & Center Mapping",
              fieldPath = "plot_perimeter",
              questionLabel = "Walked Commodity Plot Perimeter (geoshape) + <=5m Center",
              shortMapBadge = "Coffee 0.95ha • Center ±3.1m",
              collectorName = "Samuel Kariuki",
              timestamp = "2026-09-24 09:25 UTC",
              areaHectares = 0.95,
              vertexCount = 4,
              normalizedX = 0.56f,
              normalizedY = 0.52f,
              widthFraction = 0.21f,
              heightFraction = 0.14f,
              colorHex = 0xFF2E7D32,
            ),
            SubmissionGeometryPolygon(
              id = "geom-eudr-0428",
              submissionId = "sub-eudr-gh-0428",
              entityId = "",
              layerId = "layer-form-commodity-perimeter",
              formId = "form-commodity-perimeter-center",
              formTitle = "Commodity Plot Perimeter & Center Mapping",
              fieldPath = "plot_perimeter",
              questionLabel = "Walked Commodity Plot Perimeter (geoshape) + <=5m Center",
              shortMapBadge = "Rubber 2.18ha • Center ±4.2m",
              collectorName = "David Kamau",
              timestamp = "2026-09-24 10:12 UTC",
              areaHectares = 2.18,
              vertexCount = 5,
              normalizedX = 0.69f,
              normalizedY = 0.29f,
              widthFraction = 0.26f,
              heightFraction = 0.18f,
              colorHex = 0xFF6A1B9A,
            ),
          )
        "survey-household-past-individuals" ->
          listOf(
            SubmissionGeometryPolygon(
              id = "geom-ind-101",
              submissionId = "sub-ind101-wave4",
              entityId = "ind_101",
              layerId = "layer-form-household-survey",
              formId = "form-household-past-individuals",
              formTitle = "Household Follow-Up Survey (Past Individuals)",
              fieldPath = "compound_gps",
              questionLabel = "Household Compound GPS Verification",
              shortMapBadge = "HH-KAK-014 • 6 Members",
              collectorName = "Grace Wanjiku",
              timestamp = "2026-09-23 14:10 UTC",
              areaHectares = 0.08,
              vertexCount = 4,
              normalizedX = 0.32f,
              normalizedY = 0.36f,
              widthFraction = 0.15f,
              heightFraction = 0.11f,
              colorHex = 0xFF0277BD,
            ),
            SubmissionGeometryPolygon(
              id = "geom-ind-103",
              submissionId = "sub-ind103-wave4",
              entityId = "ind_103",
              layerId = "layer-form-household-survey",
              formId = "form-household-past-individuals",
              formTitle = "Household Follow-Up Survey (Past Individuals)",
              fieldPath = "compound_gps",
              questionLabel = "Household Compound GPS Verification",
              shortMapBadge = "HH-KAK-022 • Present",
              collectorName = "Maya Lin",
              timestamp = "2026-09-23 15:40 UTC",
              areaHectares = 0.08,
              vertexCount = 4,
              normalizedX = 0.58f,
              normalizedY = 0.48f,
              widthFraction = 0.15f,
              heightFraction = 0.11f,
              colorHex = 0xFF0277BD,
            ),
          )
        else -> defaultSubmissionGeometries()
      }

    /** Returns the preloaded [MapLayerItem] list for [surveyId]. */
    fun mapLayersForSurvey(surveyId: String): List<MapLayerItem> =
      when (surveyId) {
        "survey-single-point-land-use" ->
          listOf(
            MapLayerItem(
              id = "layer-land-use-observations",
              label = "Land Use Observations",
              sourceDescription = "Dataset: land_use_observations (Point)",
              colorHex = 0xFF2E7D32,
              geometryTypeLabel = "Point",
              isVisible = true,
              sourceType = LayerSourceType.ENTITY_DATASET,
              singularItemLabel = "land-use observation",
              pluralItemLabel = "land-use observations",
            )
          )
        "survey-sample-plots-forest" ->
          listOf(
            MapLayerItem(
              id = "layer-sample-plots",
              label = "Permanent Forest Sample Plots",
              sourceDescription = "Dataset: sample_plots (Polygon, SP-01 to SP-05)",
              colorHex = 0xFF1B5E20,
              geometryTypeLabel = "Polygon",
              isVisible = true,
              sourceType = LayerSourceType.ENTITY_DATASET,
              singularItemLabel = "sample plot",
              pluralItemLabel = "sample plots",
            ),
            MapLayerItem(
              id = "layer-form-sample-plots-forest",
              label = "Sample Plot Forest Stand Assessments",
              sourceDescription = "Form: Sample Plot Entity & Forest Stand Assessment",
              colorHex = 0xFF00897B,
              geometryTypeLabel = "Polygon",
              isVisible = true,
              sourceType = LayerSourceType.FORM_GEOMETRY,
              singularItemLabel = "forest stand assessment",
              pluralItemLabel = "forest stand assessments",
            ),
          )
        "survey-commodity-perimeter-center" ->
          listOf(
            MapLayerItem(
              id = "layer-commodity-plots",
              label = "Commodity Plots (EUDR)",
              sourceDescription = "Dataset: commodity_plots (Polygon)",
              colorHex = 0xFFD84315,
              geometryTypeLabel = "Polygon",
              isVisible = true,
              sourceType = LayerSourceType.ENTITY_DATASET,
              singularItemLabel = "commodity plot",
              pluralItemLabel = "commodity plots",
            )
          )
        "survey-household-past-individuals" ->
          listOf(
            MapLayerItem(
              id = "layer-past-individuals",
              label = "Preloaded Past Household Individuals",
              sourceDescription = "Dataset: past_individuals (Point, IND-101 to IND-106)",
              colorHex = 0xFF6A1B9A,
              geometryTypeLabel = "Point",
              isVisible = true,
              sourceType = LayerSourceType.ENTITY_DATASET,
              singularItemLabel = "past individual",
              pluralItemLabel = "past individuals",
            ),
            MapLayerItem(
              id = "layer-form-household-survey",
              label = "Household Follow-Up Compound Check-Ins",
              sourceDescription = "Form: Household Follow-Up Survey (Past Individuals)",
              colorHex = 0xFF0277BD,
              geometryTypeLabel = "Point",
              isVisible = true,
              sourceType = LayerSourceType.FORM_GEOMETRY,
              singularItemLabel = "household check-in",
              pluralItemLabel = "household check-ins",
            ),
          )
        else -> defaultMapLayers()
      }

    /**
     * Default map layer definitions (`LayerDef` inside `SurveyDef.map_config.layers`), including:
     * - 3 Entity Dataset layers (`LayerDef.entity_dataset_id`, solid outlines)
     * - 4 Form Geometry layers (`LayerDef.form_geometry`, dotted polygon outlines)
     */
    fun defaultMapLayers(): List<MapLayerItem> =
      listOf(
        // Survey Dataset Layers (solid outlines)
        MapLayerItem(
          id = "layer-coffee-parcels",
          label = "Smallholder Coffee Parcels",
          sourceDescription = "Dataset: coffee_parcels (Polygon)",
          colorHex = 0xFF2E7D32,
          geometryTypeLabel = "Polygon",
          isVisible = true,
          sourceType = LayerSourceType.ENTITY_DATASET,
          singularItemLabel = "coffee parcel",
          pluralItemLabel = "coffee parcels",
        ),
        MapLayerItem(
          id = "layer-shade-transects",
          label = "Shade Tree Monitoring Plots",
          sourceDescription = "Dataset: shade_monitoring_plots (LineString)",
          colorHex = 0xFF1565C0,
          geometryTypeLabel = "LineString",
          isVisible = true,
          sourceType = LayerSourceType.ENTITY_DATASET,
          singularItemLabel = "monitoring plot",
          pluralItemLabel = "monitoring plots",
        ),
        MapLayerItem(
          id = "layer-water-points",
          label = "Cooperative Washing Stations",
          sourceDescription = "Dataset: washing_stations (Point)",
          colorHex = 0xFFEF6C00,
          geometryTypeLabel = "Point",
          isVisible = true,
          sourceType = LayerSourceType.ENTITY_DATASET,
          singularItemLabel = "washing station",
          pluralItemLabel = "washing stations",
        ),
        // Submission Geometry Layers (dotted polygon outlines, grouped by form title)
        MapLayerItem(
          id = "layer-form-walked-perimeter",
          label = "Walked Parcel Perimeters",
          sourceDescription = "EUDR Parcel Baseline Registration • Walked EUDR Perimeter Polygon",
          colorHex = 0xFF66BB6A,
          geometryTypeLabel = "Dotted Polygon",
          isVisible = true,
          sourceType = LayerSourceType.FORM_GEOMETRY,
          formId = "form-eudr-baseline",
          formTitle = "EUDR Parcel Baseline Registration",
          fieldPath = "parcel/walked_perimeter_geoshape",
        ),
        MapLayerItem(
          id = "layer-form-canopy-subzone",
          label = "Surveyed Canopy Audit Sub-Zones",
          sourceDescription =
            "Seasonal Shade Tree & Canopy Audit • Surveyed Canopy Regeneration Sub-Plot",
          colorHex = 0xFF42A5F5,
          geometryTypeLabel = "Dotted Polygon",
          isVisible = true,
          sourceType = LayerSourceType.FORM_GEOMETRY,
          formId = "form-shade-canopy-audit",
          formTitle = "Seasonal Shade Tree & Canopy Audit",
          fieldPath = "audit/canopy_sample_polygon",
        ),
        MapLayerItem(
          id = "layer-form-riparian-buffer",
          label = "Riparian Buffer Zone Polygons",
          sourceDescription =
            "Washing Station Effluent & Water Check • Riparian Filtration Buffer Polygon",
          colorHex = 0xFFFFCA28,
          geometryTypeLabel = "Dotted Polygon",
          isVisible = true,
          sourceType = LayerSourceType.FORM_GEOMETRY,
          formId = "form-water-quality",
          formTitle = "Washing Station Effluent & Water Check",
          fieldPath = "inspection/riparian_buffer_zone",
        ),
        MapLayerItem(
          id = "layer-form-pest-sighting-zone",
          label = "Opportunistic Pest & Rust Sighting Zones",
          sourceDescription =
            "Opportunistic Berry Borer & Rust Sighting • Affected Roadside Buffer Zone",
          colorHex = 0xFFAB47BC,
          geometryTypeLabel = "Dotted Polygon",
          isVisible = true,
          sourceType = LayerSourceType.FORM_GEOMETRY,
          formId = "form-pest-disease-sighting",
          formTitle = "Opportunistic Berry Borer & Rust Sighting",
          fieldPath = "sighting/affected_buffer_geoshape",
        ),
      )

    /**
     * Sample Submission Geometries (`SubmissionGeometryPolygon`) corresponding to geometry
     * questions/fields (`FormGeometrySource { form_id, field_path }`) in the survey's forms.
     * Rendered on the map with dotted polygon outlines and toggleable via the `Layers` dialog.
     */
    fun defaultSubmissionGeometries(): List<SubmissionGeometryPolygon> =
      listOf(
        SubmissionGeometryPolygon(
          id = "geom-sub-nyr-104",
          submissionId = "sub-nyr-104-baseline",
          entityId = "entity-nyr-104",
          layerId = "layer-form-walked-perimeter",
          formId = "form-eudr-baseline",
          formTitle = "EUDR Parcel Baseline Registration",
          fieldPath = "parcel/walked_perimeter_geoshape",
          questionLabel = "Walked EUDR Perimeter Polygon (geoshape)",
          shortMapBadge = "Walked Perimeter (NYR-104)",
          collectorName = "Maya Lin",
          timestamp = "2026-09-18 10:14 UTC",
          areaHectares = 1.81,
          vertexCount = 28,
          normalizedX = 0.295f,
          normalizedY = 0.345f,
          widthFraction = 0.25f,
          heightFraction = 0.17f,
          colorHex = 0xFF66BB6A,
        ),
        SubmissionGeometryPolygon(
          id = "geom-sub-nyr-108",
          submissionId = "sub-nyr-108-baseline",
          entityId = "entity-nyr-108",
          layerId = "layer-form-walked-perimeter",
          formId = "form-eudr-baseline",
          formTitle = "EUDR Parcel Baseline Registration",
          fieldPath = "parcel/walked_perimeter_geoshape",
          questionLabel = "Walked EUDR Perimeter Polygon (geoshape)",
          shortMapBadge = "Walked Perimeter (NYR-108)",
          collectorName = "Samuel Kariuki",
          timestamp = "2026-09-17 16:02 UTC",
          areaHectares = 2.41,
          vertexCount = 34,
          normalizedX = 0.355f,
          normalizedY = 0.665f,
          widthFraction = 0.26f,
          heightFraction = 0.17f,
          colorHex = 0xFF66BB6A,
        ),
        SubmissionGeometryPolygon(
          id = "geom-sub-shade-201-w3",
          submissionId = "sub-shade-201-wave3",
          entityId = "entity-shade-201",
          layerId = "layer-form-canopy-subzone",
          formId = "form-shade-canopy-audit",
          formTitle = "Seasonal Shade Tree & Canopy Audit",
          fieldPath = "audit/canopy_sample_polygon",
          questionLabel = "Surveyed Canopy Regeneration Sub-Plot (geoshape)",
          shortMapBadge = "Canopy Sub-Plot (W3)",
          collectorName = "Maya Lin",
          timestamp = "2026-09-19 08:45 UTC",
          areaHectares = 1.24,
          vertexCount = 19,
          normalizedX = 0.675f,
          normalizedY = 0.315f,
          widthFraction = 0.24f,
          heightFraction = 0.16f,
          colorHex = 0xFF42A5F5,
        ),
        SubmissionGeometryPolygon(
          id = "geom-sub-wsh-01-sep",
          submissionId = "sub-wsh-01-sep",
          entityId = "entity-station-01",
          layerId = "layer-form-riparian-buffer",
          formId = "form-water-quality",
          formTitle = "Washing Station Effluent & Water Check",
          fieldPath = "inspection/riparian_buffer_zone",
          questionLabel = "Riparian Filtration Buffer Polygon (geoshape)",
          shortMapBadge = "Riparian Buffer (WSH-01)",
          collectorName = "Maya Lin",
          timestamp = "2026-09-18 17:30 UTC",
          areaHectares = 0.48,
          vertexCount = 14,
          normalizedX = 0.725f,
          normalizedY = 0.635f,
          widthFraction = 0.22f,
          heightFraction = 0.15f,
          colorHex = 0xFFFFCA28,
        ),
        SubmissionGeometryPolygon(
          id = "geom-sub-standalone-pest-01",
          submissionId = "sub-standalone-pest-01",
          entityId = "",
          layerId = "layer-form-pest-sighting-zone",
          formId = "form-pest-disease-sighting",
          formTitle = "Opportunistic Berry Borer & Rust Sighting",
          fieldPath = "sighting/affected_buffer_geoshape",
          questionLabel = "Affected Roadside Buffer Zone (geoshape)",
          shortMapBadge = "Pest Sighting (Standalone)",
          collectorName = "Maya Lin",
          timestamp = "2026-09-19 11:20 UTC",
          areaHectares = 0.32,
          vertexCount = 11,
          normalizedX = 0.52f,
          normalizedY = 0.46f,
          widthFraction = 0.18f,
          heightFraction = 0.13f,
          colorHex = 0xFFAB47BC,
        ),
      )

    /**
     * Sample forms configured in the active survey, including 5 entity-targeted forms and 1
     * standalone ("Log Only") form (`targetDatasetId = ""`, `requiresEntity == false`).
     */
    fun defaultForms(): List<FormPreviewItem> =
      listOf(
        FormPreviewItem(
          id = "form-eudr-baseline",
          title = "EUDR Parcel Baseline Registration",
          description =
            "Parcel perimeter georeferencing, deforestation-free attestation, and cultivar count (`save_to` updates `marker-symbol` ○ → ◐ → ✓).",
          version = "v2026.09.1",
          targetDatasetId = "coffee_parcels",
          targetDatasetName = "Smallholder Coffee Parcels",
          questionCount = 8,
          ctaLabel = "Register baseline parcel",
        ),
        FormPreviewItem(
          id = "form-household-interview",
          title = "Smallholder Household Socio-Economic Survey",
          description =
            "Grower household interview, farm income diversification, and cooperative membership (`save_to` updates `marker-symbol` ○ → ◐ → ✓).",
          version = "v2026.09.1",
          targetDatasetId = "coffee_parcels",
          targetDatasetName = "Smallholder Coffee Parcels",
          questionCount = 6,
          ctaLabel = "Interview household",
        ),
        FormPreviewItem(
          id = "form-shade-canopy-audit",
          title = "Seasonal Shade Tree & Canopy Audit",
          description =
            "Longitudinal multi-wave monitoring of native shade tree survival, canopy percentage, and soil moisture.",
          version = "v2026.09.2",
          targetDatasetId = "shade_monitoring_plots",
          targetDatasetName = "Shade Tree Monitoring Plots",
          questionCount = 11,
          ctaLabel = "Record canopy audit",
        ),
        FormPreviewItem(
          id = "form-deforestation-alert",
          title = "GLAD Canopy Disturbance Alert Verification",
          description =
            "Field ground-truthing of satellite canopy disturbance and deforestation alerts.",
          version = "v2026.09.2",
          targetDatasetId = "shade_monitoring_plots",
          targetDatasetName = "Shade Tree Monitoring Plots",
          questionCount = 5,
          ctaLabel = "Validate alert",
        ),
        FormPreviewItem(
          id = "form-water-quality",
          title = "Washing Station Effluent & Water Check",
          description =
            "Periodic water pH, turbidity, and eco-pulper recycling inspection at cooperative stations.",
          version = "v2026.08.4",
          targetDatasetId = "washing_stations",
          targetDatasetName = "Cooperative Washing Stations",
          questionCount = 6,
          ctaLabel = "Inspect water effluent",
        ),
        FormPreviewItem(
          id = "form-pest-disease-sighting",
          title = "Opportunistic Berry Borer & Rust Sighting",
          description =
            "Standalone field observation log for coffee berry borer (Hypothenemus hampei), leaf rust hotspots, or roadside erosion not tied to a registered parcel.",
          version = "v2026.09.1",
          targetDatasetId = "",
          targetDatasetName = "",
          questionCount = 5,
          ctaLabel = "Log field sighting",
        ),
      )

    /**
     * Sample standalone submissions (`entityId = ""`, `hasAttachedEntity == false`) recorded
     * directly in the field without being attached to any `GeospatialEntityItem`.
     */
    fun defaultStandaloneSubmissions(): List<SubmissionPreviewItem> =
      listOf(
        SubmissionPreviewItem(
          id = "sub-standalone-pest-01",
          entityId = "",
          entityLabel = "",
          formId = "form-pest-disease-sighting",
          formTitle = "Opportunistic Berry Borer & Rust Sighting",
          formVersion = "v2026.09.1",
          collectorName = "Maya Lin",
          collectorEmail = "maya.lin@groundplatform.org",
          timestamp = "2026-09-19 11:20 UTC",
          targetTypeLabel = "Standalone Field Log",
          syncStatus = SyncStatus.SYNCED,
          coordinatesLabel = "0.4204°S, 36.9521°E (±2.4m GPS)",
          normalizedX = 0.52f,
          normalizedY = 0.46f,
          fields =
            listOf(
              SubmissionFieldEntry(
                questionName = "sighting/affected_buffer_geoshape",
                questionLabel = "Affected Roadside Buffer Zone (geoshape)",
                answerValue = "Polygon (11 vertices • 0.32 ha • Chinga Feeder Road)",
              ),
              SubmissionFieldEntry(
                questionName = "pest_or_hazard_type",
                questionLabel = "Observed Pest, Pathogen, or Hazard",
                answerValue = "Coffee Leaf Rust (Hemileia vastatrix) & Berry Borer",
              ),
              SubmissionFieldEntry(
                questionName = "severity_rating",
                questionLabel = "Outbreak Severity Rating",
                answerValue = "Moderate — Localized to roadside volunteer shrubs",
              ),
              SubmissionFieldEntry(
                questionName = "recommended_action",
                questionLabel = "Recommended Agronomist Action",
                answerValue = "Prune volunteer shrubs & notify neighboring Block B growers",
              ),
              SubmissionFieldEntry(
                questionName = "gps_observation_point",
                questionLabel = "Observation GNSS Fix",
                answerValue = "0.4204°S, 36.9521°E (1,812m • ±2.4m)",
              ),
            ),
        ),
        SubmissionPreviewItem(
          id = "sub-standalone-erosion-02",
          entityId = "",
          entityLabel = "",
          formId = "form-pest-disease-sighting",
          formTitle = "Opportunistic Berry Borer & Rust Sighting",
          formVersion = "v2026.09.1",
          collectorName = "Samuel Kariuki",
          collectorEmail = "s.kariuki@kenyaforestry.org",
          timestamp = "2026-09-18 15:05 UTC",
          targetTypeLabel = "Standalone Field Log",
          syncStatus = SyncStatus.UPLOADING,
          coordinatesLabel = "0.4231°S, 36.9495°E (±3.1m GPS)",
          normalizedX = 0.48f,
          normalizedY = 0.54f,
          fields =
            listOf(
              SubmissionFieldEntry(
                questionName = "pest_or_hazard_type",
                questionLabel = "Observed Pest, Pathogen, or Hazard",
                answerValue = "Gully Erosion & Culvert Washout Along Access Track",
              ),
              SubmissionFieldEntry(
                questionName = "severity_rating",
                questionLabel = "Outbreak Severity Rating",
                answerValue = "High — Sediment runoff entering Gura River tributary",
              ),
              SubmissionFieldEntry(
                questionName = "recommended_action",
                questionLabel = "Recommended Agronomist Action",
                answerValue = "Install vetiver grass check-dams before October short rains",
              ),
              SubmissionFieldEntry(
                questionName = "gps_observation_point",
                questionLabel = "Observation GNSS Fix",
                answerValue = "0.4231°S, 36.9495°E (1,798m • ±3.1m)",
              ),
            ),
        ),
      )

    /**
     * Sample Geospatial Entities (`EntityRecord`s) across **Polygon**, **LineString**, and
     * **Point** geometries showcasing the 3-stage `simplestyle-spec` marker progression driven by
     * `save_to`:
     * - Stage 1 (`"○"` Empty Circle, `#E65100` Orange): `entity-nyr-112` (Pending baseline, 0
     *   submissions)
     * - Stage 2 (`"◐"` Half-Filled Circle, `#F9AB00` Amber): `entity-nyr-108` & `entity-station-01`
     *   (In progress, 1st stage recorded)
     * - Stage 3 (`"✓"` Checkmark, `#1E8E3E` Green / `#1565C0` Blue): `entity-nyr-104` &
     *   `entity-shade-201` (Completed)
     */
    fun defaultGeospatialEntities(): List<GeospatialEntityItem> =
      listOf(
        // 1. Stage 3 — Completed Polygon Entity ("✓" Checkmark marker)
        GeospatialEntityItem(
          id = "entity-nyr-104",
          label = "Plot NYR-104 • Kamau Family Parcel",
          datasetId = "coffee_parcels",
          datasetName = "Smallholder Coffee Parcels",
          layerId = "layer-coffee-parcels",
          geoId = "S2-10c4a89e2f",
          geometryTypeLabel = "Polygon",
          areaHectares = 1.84,
          perimeterMeters = 542,
          coordinatesLabel = "0.4182°S, 36.9481°E",
          normalizedX = 0.28f,
          normalizedY = 0.32f,
          colorHex = 0xFF2E7D32,
          properties =
            mapOf(
              "status" to "Completed",
              "marker-symbol" to "✓",
              "marker-color" to "#1E8E3E",
              "stroke" to "#1E8E3E",
              "fill" to "#1E8E3E",
              "Farmer / Owner" to "Josephat Kamau",
              "Cooperative" to "Othaya Farmers Co-op",
              "Primary Cultivar" to "SL28 & Ruiru 11",
              "Elevation" to "1,820 m",
            ),
          submissions =
            listOf(
              SubmissionPreviewItem(
                id = "sub-nyr-104-baseline",
                entityId = "entity-nyr-104",
                entityLabel = "Plot NYR-104 • Kamau Family Parcel",
                formId = "form-eudr-baseline",
                formTitle = "EUDR Parcel Baseline Registration",
                formVersion = "v2026.09.1",
                collectorName = "Maya Lin",
                collectorEmail = "maya.lin@groundplatform.org",
                timestamp = "2026-09-18 10:14 UTC",
                targetTypeLabel = "Coffee Parcel",
                fields =
                  listOf(
                    SubmissionFieldEntry(
                      questionName = "deforestation_free_since_2020",
                      questionLabel = "Deforestation-free since Dec 2020 (EUDR)",
                      answerValue = "Yes — Verified via perimeter walk & canopy history",
                    ),
                    SubmissionFieldEntry(
                      questionName = "productive_coffee_stems",
                      questionLabel = "Productive coffee stems count",
                      answerValue = "1,420 stems",
                    ),
                    SubmissionFieldEntry(
                      questionName = "canopy_shade_pct",
                      questionLabel = "Canopy shade cover (%)",
                      answerValue = "42%",
                    ),
                    SubmissionFieldEntry(
                      questionName = "intercropped_species",
                      questionLabel = "Intercropped shade species",
                      answerValue = "Grevillea robusta, Macadamia, Cordia africana",
                    ),
                    SubmissionFieldEntry(
                      questionName = "parcel/walked_perimeter_geoshape",
                      questionLabel = "Walked EUDR Perimeter Polygon (geoshape)",
                      answerValue = "Polygon (28 vertices • 1.81 ha • ±2.1m GNSS)",
                    ),
                    SubmissionFieldEntry(
                      questionName = "gps_horizontal_accuracy",
                      questionLabel = "Hardware GNSS Horizontal Accuracy",
                      answerValue = "2.1 m (Walked perimeter, 28 vertices)",
                    ),
                  ),
              )
            ),
        ),
        // 2. Stage 3 — Completed LineString Entity ("✓" Checkmark marker on a Transect Line!)
        GeospatialEntityItem(
          id = "entity-shade-201",
          label = "Transect SHD-201 • Chinga North Agroforestry",
          datasetId = "shade_monitoring_plots",
          datasetName = "Shade Tree Monitoring Plots",
          layerId = "layer-shade-transects",
          geoId = "S2-10c4b12d9a",
          geometryTypeLabel = "LineString",
          areaHectares = 3.12,
          perimeterMeters = 738,
          coordinatesLabel = "0.4245°S, 36.9560°E",
          normalizedX = 0.65f,
          normalizedY = 0.29f,
          colorHex = 0xFF1565C0,
          properties =
            mapOf(
              "status" to "Completed",
              "marker-symbol" to "✓",
              "marker-color" to "#1565C0",
              "stroke" to "#1565C0",
              "fill" to "#1565C0",
              "Community Group" to "Chinga Restoration CFA",
              "Target survival rate" to "85%",
              "Planting Cohort" to "2025 Long Rains",
              "Elevation" to "1,865 m",
            ),
          submissions =
            listOf(
              SubmissionPreviewItem(
                id = "sub-shade-201-wave3",
                entityId = "entity-shade-201",
                entityLabel = "Transect SHD-201 • Chinga North Agroforestry",
                formId = "form-shade-canopy-audit",
                formTitle = "Seasonal Shade Tree & Canopy Audit",
                formVersion = "v2026.09.2",
                collectorName = "Maya Lin",
                collectorEmail = "maya.lin@groundplatform.org",
                timestamp = "2026-09-19 08:45 UTC",
                targetTypeLabel = "Shade Tree Monitoring Plot",
                syncStatus = SyncStatus.UPLOADING,
                fields =
                  listOf(
                    SubmissionFieldEntry(
                      questionName = "audit/canopy_sample_polygon",
                      questionLabel = "Surveyed Canopy Regeneration Sub-Plot (geoshape)",
                      answerValue = "Polygon (19 vertices • 1.24 ha • North Ridge)",
                    ),
                    SubmissionFieldEntry(
                      questionName = "surviving_saplings_count",
                      questionLabel = "Surviving indigenous saplings",
                      answerValue = "188 of 200 (94% survival)",
                    ),
                    SubmissionFieldEntry(
                      questionName = "mean_canopy_height_m",
                      questionLabel = "Mean sapling height (m)",
                      answerValue = "2.65 m",
                    ),
                    SubmissionFieldEntry(
                      questionName = "soil_moisture_status",
                      questionLabel = "Topsoil moisture & mulch cover",
                      answerValue = "Moist — Heavy leaf litter mulch intact",
                    ),
                    SubmissionFieldEntry(
                      questionName = "pest_observation",
                      questionLabel = "Observed pest or browsing damage",
                      answerValue = "None detected",
                    ),
                  ),
              ),
              SubmissionPreviewItem(
                id = "sub-shade-201-wave2",
                entityId = "entity-shade-201",
                entityLabel = "Transect SHD-201 • Chinga North Agroforestry",
                formId = "form-shade-canopy-audit",
                formTitle = "Seasonal Shade Tree & Canopy Audit",
                formVersion = "v2026.06.0",
                collectorName = "Samuel Kariuki",
                collectorEmail = "s.kariuki@kenyaforestry.org",
                timestamp = "2026-06-14 14:20 UTC",
                targetTypeLabel = "Shade Tree Monitoring Plot",
                syncStatus = SyncStatus.SYNCED,
                fields =
                  listOf(
                    SubmissionFieldEntry(
                      questionName = "surviving_saplings_count",
                      questionLabel = "Surviving indigenous saplings",
                      answerValue = "191 of 200 (95.5% survival)",
                    ),
                    SubmissionFieldEntry(
                      questionName = "mean_canopy_height_m",
                      questionLabel = "Mean sapling height (m)",
                      answerValue = "2.10 m",
                    ),
                    SubmissionFieldEntry(
                      questionName = "soil_moisture_status",
                      questionLabel = "Topsoil moisture & mulch cover",
                      answerValue = "Moderate — Post-rains weeding completed",
                    ),
                  ),
              ),
              SubmissionPreviewItem(
                id = "sub-shade-201-wave1",
                entityId = "entity-shade-201",
                entityLabel = "Transect SHD-201 • Chinga North Agroforestry",
                formId = "form-deforestation-alert",
                formTitle = "GLAD Canopy Disturbance Alert Verification",
                formVersion = "v2026.03.1",
                collectorName = "Grace Wanjiku",
                collectorEmail = "g.wanjiku@kenyaforestry.org",
                timestamp = "2026-03-08 11:05 UTC",
                targetTypeLabel = "Shade Tree Monitoring Plot",
                syncStatus = SyncStatus.SYNCED,
                fields =
                  listOf(
                    SubmissionFieldEntry(
                      questionName = "surviving_saplings_count",
                      questionLabel = "Surviving indigenous saplings",
                      answerValue = "196 of 200 (98% initial establishment)",
                    ),
                    SubmissionFieldEntry(
                      questionName = "mean_canopy_height_m",
                      questionLabel = "Mean sapling height (m)",
                      answerValue = "1.45 m",
                    ),
                  ),
              ),
            ),
        ),
        // 3. Stage 2 — Half-Filled Circle Polygon Entity ("◐" In progress after household
        // interview, awaiting EUDR verification)
        GeospatialEntityItem(
          id = "entity-nyr-108",
          label = "Plot NYR-108 • Njeri Cooperative Block B",
          datasetId = "coffee_parcels",
          datasetName = "Smallholder Coffee Parcels",
          layerId = "layer-coffee-parcels",
          geoId = "S2-10c4a91c04",
          geometryTypeLabel = "Polygon",
          areaHectares = 2.45,
          perimeterMeters = 615,
          coordinatesLabel = "0.4290°S, 36.9442°E",
          normalizedX = 0.34f,
          normalizedY = 0.64f,
          colorHex = 0xFF2E7D32,
          properties =
            mapOf(
              "status" to "In progress",
              "marker-symbol" to "◐",
              "marker-color" to "#F9AB00",
              "stroke" to "#F9AB00",
              "fill" to "#F9AB00",
              "Farmer / Owner" to "Beatrice Njeri",
              "Cooperative" to "Othaya Farmers Co-op",
              "Primary Cultivar" to "Batian & SL34",
              "Elevation" to "1,795 m",
            ),
          submissions =
            listOf(
              SubmissionPreviewItem(
                id = "sub-nyr-108-baseline",
                entityId = "entity-nyr-108",
                entityLabel = "Plot NYR-108 • Njeri Cooperative Block B",
                formId = "form-household-interview",
                formTitle = "Smallholder Household Socio-Economic Survey",
                formVersion = "v2026.09.1",
                collectorName = "Samuel Kariuki",
                collectorEmail = "s.kariuki@kenyaforestry.org",
                timestamp = "2026-09-17 16:02 UTC",
                targetTypeLabel = "Coffee Parcel",
                syncStatus = SyncStatus.UPLOADING,
                fields =
                  listOf(
                    SubmissionFieldEntry(
                      questionName = "parcel/walked_perimeter_geoshape",
                      questionLabel = "Walked EUDR Perimeter Polygon (geoshape)",
                      answerValue = "Polygon (34 vertices • 2.41 ha • ±1.9m GNSS)",
                    ),
                    SubmissionFieldEntry(
                      questionName = "deforestation_free_since_2020",
                      questionLabel = "Deforestation-free since Dec 2020 (EUDR)",
                      answerValue = "Yes — Verified perennial agroforestry parcel",
                    ),
                    SubmissionFieldEntry(
                      questionName = "productive_coffee_stems",
                      questionLabel = "Productive coffee stems count",
                      answerValue = "1,980 stems",
                    ),
                    SubmissionFieldEntry(
                      questionName = "canopy_shade_pct",
                      questionLabel = "Canopy shade cover (%)",
                      answerValue = "38%",
                    ),
                  ),
              )
            ),
        ),
        // 4. Stage 1 — Initialized Empty Circle Polygon Entity ("○" Pending baseline, 0
        // submissions)
        GeospatialEntityItem(
          id = "entity-nyr-112",
          label = "Plot NYR-112 • Kariuki Hillside Parcel",
          datasetId = "coffee_parcels",
          datasetName = "Smallholder Coffee Parcels",
          layerId = "layer-coffee-parcels",
          geoId = "S2-10c4a98e7b",
          geometryTypeLabel = "Polygon",
          areaHectares = 1.56,
          perimeterMeters = 498,
          coordinatesLabel = "0.4150°S, 36.9585°E",
          normalizedX = 0.24f,
          normalizedY = 0.80f,
          colorHex = 0xFF2E7D32,
          properties =
            mapOf(
              "status" to "Pending",
              "marker-symbol" to "○",
              "marker-color" to "#E65100",
              "stroke" to "#E65100",
              "fill" to "#E65100",
              "Farmer / Owner" to "Daniel Kariuki",
              "Cooperative" to "Othaya Farmers Co-op",
              "Primary Cultivar" to "SL28 & Batian",
              "Elevation" to "1,845 m",
            ),
          submissions = emptyList(),
          syncStatus = SyncStatus.FAILED,
        ),
        // 5. Stage 2 — Half-Filled Circle Point Entity ("◐" Cooperative Washing Station)
        GeospatialEntityItem(
          id = "entity-station-01",
          label = "Station WSH-01 • Gura River Wet Mill",
          datasetId = "washing_stations",
          datasetName = "Cooperative Washing Stations",
          layerId = "layer-water-points",
          geoId = "S2-10c4a77f11",
          geometryTypeLabel = "Point",
          areaHectares = 0.15,
          perimeterMeters = 160,
          coordinatesLabel = "0.4212°S, 36.9518°E",
          normalizedX = 0.72f,
          normalizedY = 0.62f,
          colorHex = 0xFFEF6C00,
          properties =
            mapOf(
              "status" to "In progress",
              "marker-symbol" to "◐",
              "marker-color" to "#EF6C00",
              "stroke" to "#EF6C00",
              "fill" to "#EF6C00",
              "Station Manager" to "Peter Mwangi",
              "Water Source" to "Gura River Intake",
              "Eco-Pulper Installed" to "Yes (Closed-loop recirculation)",
            ),
          submissions =
            listOf(
              SubmissionPreviewItem(
                id = "sub-wsh-01-sep",
                entityId = "entity-station-01",
                entityLabel = "Station WSH-01 • Gura River Wet Mill",
                formId = "form-water-quality",
                formTitle = "Washing Station Effluent & Water Check",
                formVersion = "v2026.08.4",
                collectorName = "Maya Lin",
                collectorEmail = "maya.lin@groundplatform.org",
                timestamp = "2026-09-18 17:30 UTC",
                targetTypeLabel = "Cooperative Washing Station",
                syncStatus = SyncStatus.FAILED,
                fields =
                  listOf(
                    SubmissionFieldEntry(
                      questionName = "inspection/riparian_buffer_zone",
                      questionLabel = "Riparian Filtration Buffer Polygon (geoshape)",
                      answerValue = "Polygon (14 vertices • 0.48 ha • Constructed Wetland)",
                    ),
                    SubmissionFieldEntry(
                      questionName = "water_ph",
                      questionLabel = "Downstream water pH reading",
                      answerValue = "6.8 pH (Within normal range)",
                    ),
                    SubmissionFieldEntry(
                      questionName = "recirculation_active",
                      questionLabel = "Recirculation tank operational",
                      answerValue = "Yes — Zero untreated discharge",
                    ),
                  ),
              ),
              SubmissionPreviewItem(
                id = "sub-wsh-01-aug",
                entityId = "entity-station-01",
                entityLabel = "Station WSH-01 • Gura River Wet Mill",
                formId = "form-water-quality",
                formTitle = "Washing Station Effluent & Water Check",
                formVersion = "v2026.08.4",
                collectorName = "Grace Wanjiku",
                collectorEmail = "g.wanjiku@kenyaforestry.org",
                timestamp = "2026-08-22 09:50 UTC",
                targetTypeLabel = "Cooperative Washing Station",
                fields =
                  listOf(
                    SubmissionFieldEntry(
                      questionName = "water_ph",
                      questionLabel = "Downstream water pH reading",
                      answerValue = "6.7 pH",
                    ),
                    SubmissionFieldEntry(
                      questionName = "recirculation_active",
                      questionLabel = "Recirculation tank operational",
                      answerValue = "Yes",
                    ),
                  ),
              ),
            ),
        ),
      )

    /** Sample Mapbox vector & satellite raster tile packages for `Offline maps`. */
    fun defaultOfflineTilePackages(): List<OfflineTilePackageItem> =
      listOf(
        OfflineTilePackageItem(
          id = "tiles-nyeri-vector",
          regionName = "Nyeri & Mt. Kenya West Vector Basemap",
          tileTypeLabel = "Vector Tiles (Contours & Roads)",
          zoomRangeLabel = "Zoom 10–18",
          sizeLabel = "14.2 MB",
          isDownloaded = true,
        ),
        OfflineTilePackageItem(
          id = "tiles-nyeri-satellite",
          regionName = "Othaya & Chinga High-Res Satellite Imagery",
          tileTypeLabel = "Satellite Raster Tiles",
          zoomRangeLabel = "Zoom 12–19",
          sizeLabel = "68.5 MB",
          isDownloaded = true,
        ),
        OfflineTilePackageItem(
          id = "tiles-kirinyaga-east",
          regionName = "Kirinyaga Neighboring Cooperative Sector",
          tileTypeLabel = "Hybrid Vector + Raster Tiles",
          zoomRangeLabel = "Zoom 11–18",
          sizeLabel = "42.0 MB",
          isDownloaded = false,
        ),
      )

    /**
     * Sample local mutations (`DataMutation` items) spanning `Pending`, `In progress`, `Uploaded`,
     * and `Failed`, ordered in reverse chronological order with user-friendly operation
     * descriptions.
     */
    fun defaultMutations(): List<MutationLogItem> =
      listOf(
        // --- Pending, In progress, and Failed Mutations (reverse chronological order) ---
        MutationLogItem(
          id = "mut-outbox-04",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.CREATE_SUBMISSION,
          title = "Seasonal Shade Tree & Canopy Audit",
          targetLabel = "Transect SHD-201 • Chinga North Agroforestry",
          entityId = "entity-shade-201",
          submissionId = "sub-shade-201-wave3",
          actorName = "Maya Lin",
          state = MutationSyncState.UPLOADING,
          stateDetail = "Uploading (74%)",
          operationTimestamp = "2026-09-19 09:40:18 UTC",
          startedTimestamp = "2026-09-19 09:40:22 UTC",
          completedTimestamp = null,
          payloadSummary = "5 responses",
        ),
        MutationLogItem(
          id = "mut-outbox-03",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.UPDATE_SUBMISSION,
          title = "Smallholder Household Socio-Economic Survey",
          targetLabel = "Plot NYR-108 • Njeri Cooperative Block B",
          entityId = "entity-nyr-108",
          submissionId = "sub-nyr-108-baseline",
          actorName = "Maya Lin",
          state = MutationSyncState.RETRYING,
          stateDetail = "Retrying over weak signal",
          operationTimestamp = "2026-09-19 09:34:05 UTC",
          startedTimestamp = "2026-09-19 09:34:12 UTC",
          completedTimestamp = null,
          payloadSummary = "Updated household interview",
        ),
        MutationLogItem(
          id = "mut-outbox-02",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.UPDATE_ENTITY,
          title = "Marked parcel as In progress",
          targetLabel = "Plot NYR-108 • Njeri Cooperative Block B",
          entityId = "entity-nyr-108",
          submissionId = "sub-nyr-108-baseline",
          actorName = "Samuel Kariuki",
          state = MutationSyncState.QUEUED,
          stateDetail = "Waiting for network connection",
          operationTimestamp = "2026-09-19 09:21:40 UTC",
          startedTimestamp = "2026-09-19 09:21:44 UTC",
          completedTimestamp = null,
          payloadSummary = "Coffee Parcel status updated",
        ),
        MutationLogItem(
          id = "mut-outbox-01",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.DELETE_SUBMISSION,
          title = "Duplicate Parcel Baseline Draft",
          targetLabel = "Plot NYR-112 • Kariuki Hillside Parcel",
          entityId = "entity-nyr-112",
          submissionId = null,
          actorName = "Maya Lin",
          state = MutationSyncState.FAILED,
          stateDetail = "Connection timed out — tap Retry",
          operationTimestamp = "2026-09-19 08:55:10 UTC",
          startedTimestamp = "2026-09-19 08:55:15 UTC",
          completedTimestamp = null,
          payloadSummary = "Deleted draft submission",
        ),
        // --- Uploaded Mutations (reverse chronological order) ---
        MutationLogItem(
          id = "mut-uploaded-05",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.CREATE_SUBMISSION,
          title = "Washing Station Effluent & Water Check",
          targetLabel = "Station WSH-01 • Gura River Wet Mill",
          entityId = "entity-station-01",
          submissionId = "sub-wsh-01-sep",
          actorName = "Maya Lin",
          state = MutationSyncState.UPLOADED,
          stateDetail = "Uploaded",
          operationTimestamp = "2026-09-18 17:30:04 UTC",
          startedTimestamp = "2026-09-18 17:30:11 UTC",
          completedTimestamp = "2026-09-18 17:30:15 UTC",
          payloadSummary = "3 responses",
        ),
        MutationLogItem(
          id = "mut-uploaded-04",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.UPDATE_ENTITY,
          title = "Marked parcel as Completed",
          targetLabel = "Plot NYR-104 • Kamau Family Parcel",
          entityId = "entity-nyr-104",
          submissionId = "sub-nyr-104-baseline",
          actorName = "Maya Lin",
          state = MutationSyncState.UPLOADED,
          stateDetail = "Uploaded",
          operationTimestamp = "2026-09-18 10:14:20 UTC",
          startedTimestamp = "2026-09-18 10:14:25 UTC",
          completedTimestamp = "2026-09-18 10:14:27 UTC",
          payloadSummary = "Coffee Parcel status updated",
        ),
        MutationLogItem(
          id = "mut-uploaded-03",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.CREATE_SUBMISSION,
          title = "EUDR Parcel Baseline Registration",
          targetLabel = "Plot NYR-104 • Kamau Family Parcel",
          entityId = "entity-nyr-104",
          submissionId = "sub-nyr-104-baseline",
          actorName = "Maya Lin",
          state = MutationSyncState.UPLOADED,
          stateDetail = "Uploaded",
          operationTimestamp = "2026-09-18 10:14:02 UTC",
          startedTimestamp = "2026-09-18 10:14:15 UTC",
          completedTimestamp = "2026-09-18 10:14:24 UTC",
          payloadSummary = "4 responses",
        ),
        MutationLogItem(
          id = "mut-uploaded-02",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.UPDATE_SUBMISSION,
          title = "Smallholder Household Socio-Economic Survey",
          targetLabel = "Plot NYR-108 • Njeri Cooperative Block B",
          entityId = "entity-nyr-108",
          submissionId = "sub-nyr-108-baseline",
          actorName = "Samuel Kariuki",
          state = MutationSyncState.UPLOADED,
          stateDetail = "Uploaded",
          operationTimestamp = "2026-09-17 16:02:11 UTC",
          startedTimestamp = "2026-09-17 16:05:00 UTC",
          completedTimestamp = "2026-09-17 16:05:08 UTC",
          payloadSummary = "4 responses",
        ),
        MutationLogItem(
          id = "mut-uploaded-01",
          surveyId = "survey-kenya-coffee",
          operationKind = MutationOperationKind.CREATE_SUBMISSION,
          title = "Seasonal Shade Tree & Canopy Audit",
          targetLabel = "Transect SHD-201 • Chinga North Agroforestry",
          entityId = "entity-shade-201",
          submissionId = "sub-shade-201-wave2",
          actorName = "Samuel Kariuki",
          state = MutationSyncState.UPLOADED,
          stateDetail = "Uploaded",
          operationTimestamp = "2026-06-14 14:20:09 UTC",
          startedTimestamp = "2026-06-14 14:22:30 UTC",
          completedTimestamp = "2026-06-14 14:22:36 UTC",
          payloadSummary = "3 responses",
        ),
      )
  }
}

/**
 * Groups [features] inside a cluster by `marker-symbol` (including `""` for features with no marker
 * symbol as one group), returning ordered [ClusterMarkerSymbolGroup] entries with their counts.
 */
internal fun groupClusterFeaturesByMarkerSymbol(
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
 * Performs deterministic spatial clustering on [features] using the zoom-scaled [radiusNormalized]
 * distance threshold and merges any clusters whose on-screen balloons (`sepX` × `sepY`) would
 * overlap at the current zoom level.
 */
internal fun computeMapFeatureClusters(
  features: List<MapClusterFeatureItem>,
  radiusNormalized: Float,
): List<MapFeatureCluster> {
  if (features.isEmpty() || radiusNormalized <= 0f) return emptyList()

  // On-screen cluster balloons are horizontal pills (~220px wide × ~60px tall).
  // Scaling horizontal and vertical separation with radiusNormalized (which is proportional to
  // 2^(-mapZoomDelta)) ensures cluster balloons cluster over larger and larger geographic areas as
  // the user zooms out and never overlap on screen.
  val sepX = (radiusNormalized * 2.85f).coerceAtLeast(radiusNormalized)
  val sepY = (radiusNormalized * 1.30f).coerceAtLeast(radiusNormalized)

  class MutableWorkingCluster(
    val members: MutableList<MapClusterFeatureItem>,
    var sumX: Float,
    var sumY: Float,
  ) {
    val cx: Float
      get() = sumX / members.size

    val cy: Float
      get() = sumY / members.size
  }

  // Stage 1: For large datasets (> 64 features, e.g. 5,000 - 60,000+ map features), pre-bin features
  // in O(N) into a spatial grid sized to half the balloon footprint before running the Stage 2
  // balloon-overlap merge.
  val working: MutableList<MutableWorkingCluster> =
    if (features.size > 64) {
      val cellX = (sepX * 0.75f).coerceAtLeast(0.15f)
      val cellY = (sepY * 0.75f).coerceAtLeast(0.15f)
      val buckets = LinkedHashMap<Long, MutableWorkingCluster>()
      for (feat in features) {
        val gx = kotlin.math.floor(feat.normalizedX / cellX).toInt()
        val gy = kotlin.math.floor(feat.normalizedY / cellY).toInt()
        val key = (gx.toLong() shl 32) or (gy.toLong() and 0xFFFFFFFFL)
        val existing = buckets[key]
        if (existing != null) {
          existing.members.add(feat)
          existing.sumX += feat.normalizedX
          existing.sumY += feat.normalizedY
        } else {
          val list = ArrayList<MapClusterFeatureItem>()
          list.add(feat)
          buckets[key] = MutableWorkingCluster(list, feat.normalizedX, feat.normalizedY)
        }
      }
      buckets.values.toMutableList()
    } else {
      features
        .map { feat ->
          MutableWorkingCluster(mutableListOf(feat), feat.normalizedX, feat.normalizedY)
        }
        .toMutableList()
    }

  // Stage 2: Agglomeratively merge any two clusters that are within radiusNormalized OR whose
  // on-screen cluster balloons (sepX × sepY) would overlap.
  while (working.size > 1) {
    var bestI = -1
    var bestJ = -1
    var bestMetric = Float.MAX_VALUE

    for (i in 0 until working.size) {
      val ci = working[i]
      for (j in i + 1 until working.size) {
        val cj = working[j]
        val dx = kotlin.math.abs(ci.cx - cj.cx)
        val dy = kotlin.math.abs(ci.cy - cj.cy)
        val radialRatio = hypot(dx, dy) / radiusNormalized
        val balloonOverlapRatio = maxOf(dx / sepX, dy / sepY)
        val metric = minOf(radialRatio, balloonOverlapRatio)
        if (metric <= 1.0f && metric < bestMetric) {
          bestMetric = metric
          bestI = i
          bestJ = j
        }
      }
    }

    if (bestI < 0 || bestJ < 0) break

    val target = working[bestI]
    val absorbed = working.removeAt(bestJ)
    target.members.addAll(absorbed.members)
    target.sumX += absorbed.sumX
    target.sumY += absorbed.sumY
  }

  return working
    .sortedWith(compareBy<MutableWorkingCluster> { (it.cy * 10f).toInt() }.thenBy { it.cx })
    .mapIndexed { index, cluster ->
      MapFeatureCluster(
        id = "cluster-${index + 1}",
        normalizedX = cluster.cx,
        normalizedY = cluster.cy,
        features = cluster.members,
        symbolGroups = groupClusterFeaturesByMarkerSymbol(cluster.members),
      )
    }
}
