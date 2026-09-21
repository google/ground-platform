/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
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
import kotlin.math.pow
import kotlin.math.roundToInt
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.FormWizardController

/** Specification for the Google Maps-style horizontal map scale bar widget. */
data class MapScaleBarSpec(
  val label: String,
  val distanceMeters: Int,
  val barWidthDp: Float,
)

/** Screens in the Ground 2.0 Mobile UI onboarding and survey workflow. */
enum class PrototypeScreen(val stepNumber: Int, val title: String, val subtitle: String) {
  SPLASH(
    stepNumber = 1,
    title = "Splash / Loading",
    subtitle = "Initial app launch & workspace initialization",
  ),
  SIGN_IN(
    stepNumber = 2,
    title = "Sign In",
    subtitle = "Google authentication entry point",
  ),
  TERMS_OF_SERVICE(
    stepNumber = 3,
    title = "Terms of Service",
    subtitle = "Data governance & platform terms acceptance",
  ),
  DOWNLOAD_SURVEY(
    stepNumber = 4,
    title = "Download Survey",
    subtitle = "Browse shared surveys or search by name/location",
  ),
  MAIN_SURVEY(
    stepNumber = 5,
    title = "Main Survey (Map & List)",
    subtitle = "Geospatial entity map, layers, 1:1 & 1:N bottom sheets, searchable list & drawer",
  ),
}

/** Primary view mode inside the Main Survey screen (`Map` vs `List`). */
enum class MainSurveyViewMode(val label: String) {
  MAP("Map"),
  LIST("List"),
}

/** Category filter tabs inside the Main Survey `List` view (Forms group Submissions rather than being a separate list). */
enum class ListFilterTab(val label: String) {
  ALL("All"),
  ENTITIES("Sites"),
  SUBMISSIONS("Submissions"),
}

/**
 * Device hardware bezel form factor selectable in the Prototype App wrapper page (`Mobile` vs `Tablet`).
 */
enum class DeviceFormFactor(
  val label: String,
  val dimensionsLabel: String,
  val frameWidthDp: Int,
  val frameHeightDp: Int,
  val outerCornerRadiusDp: Int,
  val innerCornerRadiusDp: Int,
) {
  MOBILE(
    label = "Mobile",
    dimensionsLabel = "404 × 764 dp",
    frameWidthDp = 404,
    frameHeightDp = 764,
    outerCornerRadiusDp = 40,
    innerCornerRadiusDp = 32,
  ),
  TABLET(
    label = "Tablet",
    dimensionsLabel = "780 × 620 dp",
    frameWidthDp = 780,
    frameHeightDp = 620,
    outerCornerRadiusDp = 28,
    innerCornerRadiusDp = 20,
  ),
}

/** Sub-screens opened from the Hamburger Navigation Drawer inside the Main Survey UI. */
enum class MainDrawerSubView {
  NONE,
  SWITCH_SURVEYS,
  MANAGE_OFFLINE_MAPS,
  SETTINGS,
}

/**
 * Relationship model between Geospatial Entities and Form Submissions (per `docs/design/00-index.md`
 * "Longitudinal Linking & Multi-Wave Surveys").
 */
enum class SubmissionModel(val badgeLabel: String, val description: String) {
  SINGLE_1_TO_1(
    badgeLabel = "Single Submission",
    description = "Baseline registration / asset audit with a single submission per entity",
  ),
  MULTIPLE_1_TO_N(
    badgeLabel = "Multiple Submissions",
    description = "Longitudinal monitoring with chronological follow-up submissions",
  ),
}

/** Measurement unit preference (per `docs/design/00-index.md` and `ground-android` `MeasurementUnits`). */
enum class MeasurementUnitSystem(
  val label: String,
  val shortLabel: String,
  val areaUnit: String,
  val distanceUnit: String,
) {
  METRIC("Metric (ha, m)", "Metric", "ha", "m"),
  IMPERIAL("Imperial (acres, ft)", "Imperial", "acres", "ft"),
}

/** Supported language option matching `arrays.xml` & `strings-untranslated.xml` in `github.com/google/ground-android`. */
data class GroundLanguageOption(
  val code: String,
  val label: String,
)

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
 * - [FORM_GEOMETRY]: `FormGeometrySource { form_id, field_path }` (submission geometries from
 *   form geometry questions/fields, rendered with dotted polygon outlines)
 */
enum class LayerSourceType(val badgeLabel: String) {
  ENTITY_DATASET("Entity Dataset"),
  FORM_GEOMETRY("Form Geometry Field"),
}

/** Primary map basemap mode selectable by the user in the `Layers` dialog (`Map` vs `Satellite`). */
enum class BasemapType(val label: String, val description: String) {
  NORMAL(
    label = "Map",
    description = "Standard vector terrain, roads & contour basemap",
  ),
  SATELLITE(
    label = "Satellite",
    description = "High-resolution satellite & aerial canopy imagery",
  ),
}

/** Offline basemap rendering style toggleable in the `Layers` dialog. */
enum class OfflineBasemapStyle(val label: String, val tileDescription: String) {
  SATELLITE_HYBRID(
    label = "Satellite + Contours",
    tileDescription = "Mapbox Satellite Raster + Vector Contours (Nyeri 82.7 MB cached)",
  ),
  VECTOR_TOPO(
    label = "Vector Topographic",
    tileDescription = "Mapbox Vector Terrain & Hydrology (Nyeri 14.2 MB cached)",
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
  val fieldPath: String? = null,
  val isDottedOutline: Boolean = sourceType == LayerSourceType.FORM_GEOMETRY,
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
      else -> "site"
    },
  val pluralNoun: String =
    when (id) {
      "layer-coffee-parcels" -> "parcels"
      "layer-shade-transects" -> "plots"
      "layer-water-points" -> "stations"
      else -> "sites"
    },
) {
  /** Formats a user-friendly domain item count for this layer (e.g. `"2 parcels"`, `"1 plot"`). */
  fun itemCountLabel(count: Int): String = "$count ${if (count == 1) singularNoun else pluralNoun}"
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

/** Represents a completed form submission linked to a Geospatial Entity. */
data class SubmissionPreviewItem(
  val id: String,
  val entityId: String,
  val entityLabel: String,
  val formId: String,
  val formTitle: String,
  val formVersion: String,
  val collectorName: String,
  val collectorEmail: String,
  val timestamp: String,
  val fields: List<SubmissionFieldEntry>,
)

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
  val submissionModel: SubmissionModel,
  val normalizedX: Float,
  val normalizedY: Float,
  val colorHex: Long,
  val properties: Map<String, String>,
  val submissions: List<SubmissionPreviewItem>,
) {
  /** User-facing singular domain noun derived from the dataset (e.g. `"Coffee Parcel"`). */
  val singularTypeLabel: String
    get() =
      when (datasetId) {
        "coffee_parcels" -> "Coffee Parcel"
        "shade_monitoring_plots" -> "Monitoring Plot"
        "washing_stations" -> "Washing Station"
        else -> datasetName.removeSuffix("s").ifBlank { "Site" }
      }
}

/** Represents a hierarchical Form (`FormDef` + `FormLaunchConfig`) in the active survey. */
data class FormPreviewItem(
  val id: String,
  val title: String,
  val description: String,
  val version: String,
  val submissionModel: SubmissionModel,
  val targetDatasetId: String,
  val targetDatasetName: String,
  val questionCount: Int,
  val ctaLabel: String,
)

/** Grouping of [SubmissionPreviewItem]s under a [FormPreviewItem] in the searchable `List` view. */
data class FormSubmissionsGroup(
  val form: FormPreviewItem,
  val submissions: List<SubmissionPreviewItem>,
)

/** Active PDF export & app-share sheet state for either a Geospatial Entity or a Form Submission. */
data class SharedPdfSheetState(
  val targetId: String,
  val title: String,
  val subtitle: String,
  val pdfFileName: String,
  val targetKindLabel: String,
)

/** Distinguishes whether straight-line navigation is targeting a Geospatial Entity or a Submission. */
enum class NavigationTargetKind(val badgeLabel: String) {
  ENTITY("ENTITY"),
  SUBMISSION("SUBMISSION"),
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
 * Manages both the onboarding screens (`Splash` -> `Sign In` -> `Terms of Service` ->
 * `Download survey`) and the **Main Survey UI** (`Map` view with geospatial entities, `Layers`
 * filter popover, `1:1` and `1:N` entity bottom sheets, `List` view with searchable forms,
 * entities, and submissions, and the Hamburger Navigation Drawer).
 */
class PrototypeAppState(
  initialScreen: PrototypeScreen = PrototypeScreen.SPLASH,
  initialSurveys: List<SurveyPreviewItem> = defaultSampleSurveys(),
) {
  var currentScreen by mutableStateOf(initialScreen)
    private set

  var isDarkTheme by mutableStateOf(false)
    private set

  /** Active hardware preview bezel form factor in the wrapper workbench (`Mobile` vs `Tablet`). */
  var deviceFormFactor by mutableStateOf(DeviceFormFactor.MOBILE)
    private set

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
    private set

  var selectedEntityId by mutableStateOf<String?>("entity-nyr-104")
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
   * Whether the map camera automatically pans to keep the user's current GPS location at the
   * center of the screen (`true` by default). Becomes `false` when the user drags/pans the map,
   * which reveals the Google Maps-style `"Recenter"` button.
   */
  var isCameraFollowingUser by mutableStateOf(true)
    private set

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
      return MapScaleBarSpec(
        label = label,
        distanceMeters = chosenMeters,
        barWidthDp = barWidthDp,
      )
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

  /** Formatted GNSS satellites + accuracy badge displayed on the chip over the map. */
  val gnssStatusChipLabel: String
    get() {
      val accuracyFormatted =
        if (unitSystem == MeasurementUnitSystem.METRIC) {
          "±$gnssAccuracyMeters m"
        } else {
          val feet = ((gnssAccuracyMeters * 3.28084) * 10.0).toInt() / 10.0
          "±$feet ft"
        }
      return "$gnssSatelliteCount sats • $accuracyFormatted"
    }

  /** Entity ID whose scannable GeoID QR code modal dialog is currently open (`null` when closed). */
  var activeQrCodeEntityId by mutableStateOf<String?>(null)
    private set

  /** The [GeospatialEntityItem] whose QR code modal dialog is currently open (if any). */
  val activeQrCodeEntity: GeospatialEntityItem?
    get() = activeQrCodeEntityId?.let { id -> entities.firstOrNull { it.id == id } }

  /** Active PDF export & app-sharing modal state for an entity or submission (`null` when closed). */
  var activeSharedPdfSheet by mutableStateOf<SharedPdfSheetState?>(null)
    private set

  // --- Straight-Line Wayfinding Navigation State (Entities & Submissions) ---
  /** Target kind (`ENTITY` or `SUBMISSION`) for active straight-line navigation (`null` when inactive). */
  var navigationTargetKind by mutableStateOf<NavigationTargetKind?>(null)
    private set

  /** Target ID (`entityId` or `submissionId`) for active straight-line navigation (`null` when inactive). */
  var navigationTargetId by mutableStateOf<String?>(null)
    private set

  /**
   * Computes a [StraightLineVector] from the collector's current GPS position
   * (`userGpsNormalizedX`, `userGpsNormalizedY`) to `(targetNormalizedX, targetNormalizedY)`
   * using the active survey's geographic scale and the user's [unitSystem] (`METRIC` vs `IMPERIAL`).
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
   * [SubmissionGeometryPolygon] for any [SubmissionPreviewItem].
   */
  fun resolveSubmissionTargetGeometry(
    submissionId: String,
  ): Triple<Float, Float, SubmissionGeometryPolygon?>? {
    val sub = allSubmissions.firstOrNull { it.id == submissionId } ?: return null
    val geom = submissionGeometries.firstOrNull { it.submissionId == sub.id }
    if (geom != null) {
      return Triple(geom.normalizedX, geom.normalizedY, geom)
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

  /** Formatted distance & compass bearing badge for [entityId] (e.g. `"495 m • 319° NW"`). */
  fun formattedWayfindingBadgeForEntity(entityId: String): String {
    val v = distanceAndBearingToEntity(entityId) ?: return ""
    return "${v.formattedDistance} • ${v.bearingDegrees}° ${v.cardinalDirection}"
  }

  /** Formatted distance & compass bearing badge for [submissionId] (e.g. `"452 m • 321° NW"`). */
  fun formattedWayfindingBadgeForSubmission(submissionId: String): String {
    val v = distanceAndBearingToSubmission(submissionId) ?: return ""
    return "${v.formattedDistance} • ${v.bearingDegrees}° ${v.cardinalDirection}"
  }

  /** True when straight-line navigation is currently active and targeting [entityId]. */
  fun isNavigatingToEntity(entityId: String): Boolean =
    navigationTargetKind == NavigationTargetKind.ENTITY && navigationTargetId == entityId

  /** True when straight-line navigation is currently active and targeting [submissionId]. */
  fun isNavigatingToSubmission(submissionId: String): Boolean =
    navigationTargetKind == NavigationTargetKind.SUBMISSION && navigationTargetId == submissionId

  /**
   * Currently active straight-line navigation session ([StraightLineNavigationState]) to either a
   * Geospatial Entity or a Form Submission, dynamically updated as the user's GPS position or
   * measurement unit preference changes (`null` when navigation is inactive).
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
          val parentEntity = entities.firstOrNull { it.id == sub.entityId } ?: return null
          val (tx, ty, geom) = resolveSubmissionTargetGeometry(sub.id) ?: return null
          val vector = computeStraightLineVector(tx, ty)
          StraightLineNavigationState(
            targetKind = NavigationTargetKind.SUBMISSION,
            targetId = sub.id,
            entityId = parentEntity.id,
            submissionId = sub.id,
            geometryId = geom?.id,
            targetTitle =
              geom?.shortMapBadge ?: "${sub.formTitle} (${parentEntity.label.substringBefore(" •")})",
            targetSubtitle = "${sub.entityLabel} • ${sub.collectorName} (${sub.timestamp})",
            targetCoordinatesLabel = parentEntity.coordinatesLabel,
            colorHex = geom?.colorHex ?: parentEntity.colorHex,
            vector = vector,
          )
        }
      }
    }

  // --- Data Collection Form & XForms FormDef Chrome State ---
  /**
   * Custom ODK XForms `<h:html>` definition editable in the Prototype App Chrome (`UxDesignerInspectorPanel`).
   * Initialized to a rich EUDR / Shade-Tree Field Survey XForms XML that parses cleanly with
   * [XFormsXmlSerializer.deserializeFormDef].
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
   * Active [FormWizardController] driving the embedded [org.groundplatform.v2.core.forms.ui.MobileFormRunner]
   * when data collection is triggered for a Geospatial Entity (`null` when closed).
   */
  var activeFormWizardController by mutableStateOf<FormWizardController?>(null)
    private set

  /** Target Geospatial Entity ID for the currently active data collection form (`null` when closed). */
  var activeDataCollectionEntityId by mutableStateOf<String?>(null)
    private set

  /** Target Form ID for the currently active data collection form (`null` when closed). */
  var activeDataCollectionFormId by mutableStateOf<String?>(null)
    private set

  /** True when the embedded [org.groundplatform.v2.core.forms.ui.MobileFormRunner] is open. */
  val isDataCollectionFormOpen: Boolean
    get() = activeFormWizardController != null

  /** The target [GeospatialEntityItem] for the currently active data collection session (if any). */
  val activeDataCollectionEntity: GeospatialEntityItem?
    get() = activeDataCollectionEntityId?.let { id -> entities.firstOrNull { it.id == id } }

  /** The target [FormPreviewItem] for the currently active data collection session (if any). */
  val activeDataCollectionForm: FormPreviewItem?
    get() = activeDataCollectionFormId?.let { id -> forms.firstOrNull { it.id == id } }

  /** The currently active survey loaded in the Main Survey UI. */
  val activeSurvey: SurveyPreviewItem
    get() = surveys.firstOrNull { it.id == activeSurveyId } ?: surveys.first()

  /**
   * Surveys that have already been downloaded onto the device (shown on the `"Surveys"`
   * screen accessible from the navigation drawer).
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

  /** Layers backed by `LayerDef.entity_dataset_id` (rendered with solid outlines). */
  val entityDatasetLayers: List<MapLayerItem>
    get() = mapLayers.filter { it.sourceType == LayerSourceType.ENTITY_DATASET }

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
   * User-facing plural category label for the `Sites` tab and list section header:
   * - When exactly 1 entity dataset layer is visible on the map, returns its plural domain label
   *   (e.g. `"Coffee Parcels"`, `"Monitoring Plots"`, `"Washing Stations"`).
   * - When multiple entity dataset layers are visible (or none), falls back to `"Sites"`.
   */
  val activeEntitiesTabLabel: String
    get() =
      visibleEntityDatasetLayers.singleOrNull()?.pluralDomainLabel ?: ListFilterTab.ENTITIES.label

  /**
   * User-facing lowercase plural count noun for map counters, search hints, and empty states:
   * - When 1 entity dataset layer is visible, returns its lowercase plural domain label
   *   (e.g. `"coffee parcels"`, `"monitoring plots"`, `"washing stations"`).
   * - Otherwise falls back to `"sites"`.
   */
  val activeEntitiesCountNoun: String
    get() =
      visibleEntityDatasetLayers.singleOrNull()?.pluralDomainLabel?.lowercase() ?: "sites"

  /** Resolves the user-facing singular domain noun for [entityId] (e.g. `"Coffee Parcel"`, or `"Site"`). */
  fun entitySingularTypeLabel(entityId: String?): String =
    entityId?.let { id -> entities.firstOrNull { it.id == id }?.singularTypeLabel } ?: "Site"

  /** Resolves the dynamic display label for a [ListFilterTab] chip. */
  fun tabLabelFor(tab: ListFilterTab): String =
    when (tab) {
      ListFilterTab.ENTITIES -> activeEntitiesTabLabel
      else -> tab.label
    }

  /**
   * Submission geometries (recorded answers to form geometry questions/fields) currently visible
   * on the map with dotted polygon outlines according to active `FORM_GEOMETRY` layer toggles.
   */
  val visibleSubmissionGeometries: List<SubmissionGeometryPolygon>
    get() = submissionGeometries.filter { it.layerId in visibleLayerIds }

  /** The currently selected Geospatial Entity shown in the bottom sheet (if any). */
  val selectedEntity: GeospatialEntityItem?
    get() =
      selectedEntityId?.let { id ->
        entities.firstOrNull { it.id == id && it.layerId in visibleLayerIds }
      }

  /** All submissions across all entities in the active survey. */
  val allSubmissions: List<SubmissionPreviewItem>
    get() = entities.flatMap { it.submissions }

  /** The currently selected individual submission for full submission detail inspection. */
  val selectedSubmission: SubmissionPreviewItem?
    get() = selectedSubmissionId?.let { id -> allSubmissions.firstOrNull { it.id == id } }

  /** Returns all forms in the active survey that request entities of [entity]'s dataset type. */
  fun formsForEntity(entity: GeospatialEntityItem): List<FormPreviewItem> =
    forms.filter { it.targetDatasetId == entity.datasetId }

  /**
   * Returns whether the organizer-defined action button for [form] is enabled on [entity].
   * For `1:1` entities or `1:1` forms, the button is enabled ONLY when a submission does not
   * yet exist for that form on [entity].
   */
  fun isFormButtonEnabled(entity: GeospatialEntityItem, form: FormPreviewItem): Boolean {
    if (
      entity.submissionModel == SubmissionModel.SINGLE_1_TO_1 ||
        form.submissionModel == SubmissionModel.SINGLE_1_TO_1
    ) {
      return entity.submissions.none { it.formId == form.id }
    }
    return true
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
          entity.properties.values.any { it.contains(q, ignoreCase = true) }
      }
    }

  /** Filtered Submissions in the Main Survey `List` view matching [listSearchQuery]. */
  val filteredListSubmissions: List<SubmissionPreviewItem>
    get() {
      if (listFilterTab != ListFilterTab.ALL && listFilterTab != ListFilterTab.SUBMISSIONS) {
        return emptyList()
      }
      val q = listSearchQuery.trim()
      if (q.isEmpty()) return allSubmissions
      return allSubmissions.filter { sub ->
        sub.entityLabel.contains(q, ignoreCase = true) ||
          sub.collectorName.contains(q, ignoreCase = true) ||
          sub.formTitle.contains(q, ignoreCase = true) ||
          sub.timestamp.contains(q, ignoreCase = true) ||
          sub.fields.any {
            it.questionLabel.contains(q, ignoreCase = true) ||
              it.answerValue.contains(q, ignoreCase = true)
          }
      }
    }

  /**
   * Submissions in the Main Survey `List` view grouped by their parent [FormPreviewItem]
   * ("Forms" act as a grouping header for Submissions rather than a standalone list).
   */
  val groupedFilteredListSubmissions: List<FormSubmissionsGroup>
    get() {
      val matchingSubs = filteredListSubmissions
      if (matchingSubs.isEmpty()) return emptyList()
      return forms.mapNotNull { form ->
        val formSubs = matchingSubs.filter { it.formId == form.id }
        if (formSubs.isNotEmpty()) {
          FormSubmissionsGroup(form = form, submissions = formSubs)
        } else {
          null
        }
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

  /** Transitions from the Splash / Loading screen to the Sign In screen. */
  fun completeSplashLoading() {
    isDownloadSurveySignOutPromptOpen = false
    currentScreen =
      if (isSignedIn && hasAcceptedTerms) {
        downloadSurveyEntryOrigin = DownloadSurveyEntryOrigin.AFTER_TOS
        PrototypeScreen.DOWNLOAD_SURVEY
      } else if (isSignedIn) {
        PrototypeScreen.TERMS_OF_SERVICE
      } else {
        PrototypeScreen.SIGN_IN
      }
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
    surveys =
      surveys.map { item ->
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
    currentScreen = PrototypeScreen.MAIN_SURVEY
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.NONE
    activeSurveyNotice = null
  }

  /** Toggles the downloaded status of a survey (for UX prototyping & testing). */
  fun toggleSurveyDownloaded(surveyId: String) {
    surveys =
      surveys.map { item ->
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

  /** Switches between the Map view and List view inside the Main Survey screen. */
  fun setMainSurveyViewMode(mode: MainSurveyViewMode) {
    mainViewMode = mode
    activeDrawerSubView = MainDrawerSubView.NONE
  }

  /** Opens or closes the Hamburger Navigation Drawer. */
  fun updateDrawerOpen(open: Boolean) {
    isDrawerOpen = open
  }

  /** Toggles the "Layers" visibility popover sheet on the Map view. */
  fun updateLayersSheetOpen(open: Boolean) {
    isLayersSheetOpen = open
  }

  /** Selects between `Normal` (`BasemapType.NORMAL`) and `Satellite` (`BasemapType.SATELLITE`) basemap. */
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

  /** Selects a submission geometry polygon on the map and opens its parent entity + submission. */
  fun selectSubmissionGeometry(geometryId: String) {
    val geom = submissionGeometries.firstOrNull { it.id == geometryId } ?: return
    selectedEntityId = geom.entityId
    selectedSubmissionId = geom.submissionId
    isEntityBottomSheetExpanded = true
    isLayersSheetOpen = false
  }

  /** Toggles visibility of a specific `LayerDef` on the survey map. */
  fun toggleLayerVisibility(layerId: String) {
    mapLayers =
      mapLayers.map { layer ->
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

  /** Selects a Geospatial Entity on the map or list to open its bottom sheet in collapsed/peek state. */
  fun selectEntity(entityId: String?) {
    selectedEntityId = entityId
    selectedSubmissionId = null
    isEntityBottomSheetExpanded = false
    if (entityId != null) {
      isLayersSheetOpen = false
    }
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
      if (parentEntity != null) {
        selectedEntityId = parentEntity.id
      }
    }
  }

  /** Updates the search query in the Main Survey `List` view. */
  fun updateListSearchQuery(query: String) {
    listSearchQuery = query
  }

  /** Clears the search query in the Main Survey `List` view. */
  fun clearListSearchQuery() {
    listSearchQuery = ""
  }

  /** Updates the active category filter tab (`All`, `Entities`, `Submissions`). */
  fun selectListFilterTab(tab: ListFilterTab) {
    listFilterTab = tab
  }

  /**
   * Updates [customXFormsXml], parses [FormDef] via [XFormsXmlSerializer.deserializeFormDef], and
   * updates [customFormDef] and [xformsXmlError]. If a form runner is currently open and the new
   * [FormDef] is valid, refreshes [activeFormWizardController] with the new [FormDef].
   */
  fun updateCustomXFormsXml(xml: String) {
    customXFormsXml = xml
    if (xml.isBlank()) {
      customFormDef = null
      xformsXmlError = null
      if (activeFormWizardController != null) {
        val currentForm = activeDataCollectionForm ?: forms.first()
        val fallbackFormDef = resolveFormDefForLaunch(null, currentForm)
        activeFormWizardController = FormWizardController(formDef = fallbackFormDef)
      }
      return
    }
    try {
      val parsed = XFormsXmlSerializer.deserializeFormDef(xml)
      customFormDef = parsed
      xformsXmlError = null
      if (activeFormWizardController != null) {
        activeFormWizardController = FormWizardController(formDef = parsed)
      }
    } catch (e: Exception) {
      customFormDef = null
      xformsXmlError = e.message ?: "Invalid XForms XML"
    }
  }

  /** Restores the default sample XForms XML definition (`DEFAULT_PROTOTYPE_XFORMS_XML`). */
  fun resetDefaultXFormsXml() {
    updateCustomXFormsXml(DEFAULT_PROTOTYPE_XFORMS_XML)
  }

  /**
   * Launches the embedded [org.groundplatform.v2.core.forms.ui.MobileFormRunner] (`[FormWizardController]`)
   * for [formId] on [entityId] when the organizer-defined form button (`ctaLabel`) is tapped in the
   * entity bottom sheet or triggered from the UX Chrome tester.
   */
  fun launchFormForEntity(entityId: String, formId: String) {
    val entity = entities.firstOrNull { it.id == entityId } ?: return
    val form = forms.firstOrNull { it.id == formId } ?: return
    if (!isFormButtonEnabled(entity, form)) return

    val resolvedFormDef = resolveFormDefForLaunch(customFormDef, form)
    val controller = FormWizardController(formDef = resolvedFormDef)

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
   * entity (or a default entity with an enabled form).
   */
  fun launchActiveOrDefaultFormForTesting() {
    val currentEntity = selectedEntity ?: entities.firstOrNull { it.id == "entity-shade-201" } ?: entities.first()
    val enabledFormOnCurrent =
      formsForEntity(currentEntity).firstOrNull { isFormButtonEnabled(currentEntity, it) }
    if (enabledFormOnCurrent != null) {
      launchFormForEntity(currentEntity.id, enabledFormOnCurrent.id)
      return
    }
    val fallbackEntity =
      entities.firstOrNull { ent -> formsForEntity(ent).any { isFormButtonEnabled(ent, it) } }
        ?: return
    val fallbackForm =
      formsForEntity(fallbackEntity).firstOrNull { isFormButtonEnabled(fallbackEntity, it) }
        ?: return
    launchFormForEntity(fallbackEntity.id, fallbackForm.id)
  }

  /**
   * Completes the active form submission when the user clicks `Submit ✓` in `MobileFormRunner`
   * (or when invoked programmatically with a finalized [recordInstance]).
   *
   * Extracts all answered fields from [recordInstance] (and `activeFormWizardController?.formState`)
   * using `formatFieldValueForDisplay` into `List<SubmissionFieldEntry>`, appends a new
   * [SubmissionPreviewItem] to the target entity's `submissions` list, updates [activeSurveyNotice],
   * and closes the active form runner.
   */
  fun completeActiveFormSubmission(
    recordInstance: RecordInstance =
      activeFormWizardController?.formState?.recordInstance ?: RecordInstance()
  ) {
    val entityId = activeDataCollectionEntityId ?: selectedEntityId ?: entities.firstOrNull()?.id ?: return
    val entity = entities.firstOrNull { it.id == entityId } ?: return
    val formId =
      activeDataCollectionFormId
        ?: formsForEntity(entity).firstOrNull()?.id
        ?: forms.firstOrNull()?.id
        ?: return
    val form = forms.firstOrNull { it.id == formId } ?: forms.first()

    val controller = activeFormWizardController
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
      )

    entities =
      entities.map { item ->
        if (item.id == entity.id) {
          item.copy(submissions = listOf(newSubmission) + item.submissions)
        } else {
          item
        }
      }
    activeSurveyNotice = "Submitted \"$resolvedTitle\" for ${entity.label}"
    closeActiveFormRunner()
  }

  /** Closes the active `MobileFormRunner` and returns to the survey map/list screen. */
  fun closeActiveFormRunner() {
    activeFormWizardController = null
    activeDataCollectionEntityId = null
    activeDataCollectionFormId = null
  }

  /** Opens the scannable S2 GeoID / Entity QR code modal dialog for [entityId]. */
  fun openEntityQrCode(entityId: String) {
    activeQrCodeEntityId = entityId
  }

  /** Closes the active Entity QR code modal dialog. */
  fun closeEntityQrCode() {
    activeQrCodeEntityId = null
  }

  /** Opens the Share PDF modal sheet to share a Geospatial Entity's report PDF to a preferred app. */
  fun shareEntityPdf(entityId: String) {
    val entity = entities.firstOrNull { it.id == entityId } ?: return
    activeSharedPdfSheet =
      SharedPdfSheetState(
        targetId = entity.id,
        title = "Share ${entity.singularTypeLabel} PDF Report",
        subtitle = "${entity.label} • GeoID ${entity.geoId}",
        pdfFileName = "${entity.id}-${entity.geoId}.pdf",
        targetKindLabel = "${entity.singularTypeLabel} PDF",
      )
  }

  /** Opens the Share PDF modal sheet to share a Form Submission's report PDF to a preferred app. */
  fun shareSubmissionPdf(submissionId: String) {
    val sub = allSubmissions.firstOrNull { it.id == submissionId } ?: return
    activeSharedPdfSheet =
      SharedPdfSheetState(
        targetId = sub.id,
        title = "Share Submission PDF Report",
        subtitle = "${sub.formTitle} • ${sub.collectorName} (${sub.timestamp})",
        pdfFileName = "${sub.id}.pdf",
        targetKindLabel = "Form Submission PDF",
      )
  }

  /** Closes the active Share PDF modal sheet. */
  fun closeSharePdfSheet() {
    activeSharedPdfSheet = null
  }

  // --- Hamburger Navigation Drawer Actions ---

  /**
   * Drawer option 1: "Surveys" — opens the dedicated screen showing only surveys that have
   * already been downloaded onto the device, with a primary button to browse & download more surveys.
   */
  fun drawerSwitchSurveys() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.SWITCH_SURVEYS
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

  /** Drawer option 2: Offline maps. */
  fun drawerManageOfflineMaps() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.MANAGE_OFFLINE_MAPS
  }

  /** Drawer option 3: Change settings. */
  fun drawerOpenSettings() {
    isDrawerOpen = false
    activeDrawerSubView = MainDrawerSubView.SETTINGS
  }

  /** Closes the active drawer sub-view (Switch Surveys, Offline Maps, or Settings) and returns to the survey. */
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
    offlineTilePackages =
      offlineTilePackages.map { pkg ->
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
   * (e.g. `"fr (Français)"`). Synchronizes both [selectedLanguageCode] and [selectedLanguageLocale].
   */
  fun updateSelectedLanguage(languageCodeOrLocale: String) {
    val trimmed = languageCodeOrLocale.trim()
    val codeCandidate = trimmed.substringBefore(" ").lowercase()
    val matched =
      GROUND_LANGUAGE_OPTIONS.firstOrNull {
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

  /** Updates the "Upload photos over Wi-Fi only" preference (matching `SettingsViewModel` in `ground-android`). */
  fun updateUploadMediaOverUnmeteredConnectionOnly(enabled: Boolean) {
    shouldUploadPhotosOnWifiOnly = enabled
  }

  /** Records a click on "Visit website" (`https://groundplatform.org/`) in the Settings Help section. */
  fun visitGroundWebsite(url: String = GROUND_WEBSITE_URL) {
    visitedWebsiteUrl = url
    activeSurveyNotice = "Opened $url"
  }

  /** Evicts uploaded media attachments from local device cache (per `00-index.md`). */
  fun evictUploadedMediaCache() {
    mediaCacheCleared = true
  }

  /** Selects the device preview form factor (`Mobile` vs `Tablet`) in the prototype wrapper page. */
  fun selectDeviceFormFactor(formFactor: DeviceFormFactor) {
    deviceFormFactor = formFactor
  }

  /** Toggles between `Mobile` and `Tablet` form factors in the prototype wrapper page. */
  fun toggleDeviceFormFactor() {
    deviceFormFactor =
      if (deviceFormFactor == DeviceFormFactor.MOBILE) {
        DeviceFormFactor.TABLET
      } else {
        DeviceFormFactor.MOBILE
      }
  }

  /**
   * Pans (drags) the survey map viewport by normalized deltas `(deltaNormalizedX, deltaNormalizedY)`.
   *
   * Dragging the map disengages automatic GPS camera centering ([isCameraFollowingUser] = `false`),
   * causing the Google Maps-style `"Recenter"` button to appear on the map.
   */
  fun panMap(deltaNormalizedX: Float, deltaNormalizedY: Float) {
    if (deltaNormalizedX == 0f && deltaNormalizedY == 0f) return
    isCameraFollowingUser = false
    mapPanOffsetX = (mapPanOffsetX + deltaNormalizedX).coerceIn(-0.65f, 0.65f)
    mapPanOffsetY = (mapPanOffsetY + deltaNormalizedY).coerceIn(-0.65f, 0.65f)
  }

  /**
   * Recenters the map camera on the user's current GPS location (`(0.50f, 0.50f)` screen center)
   * and re-enables automatic GPS camera following ([isCameraFollowingUser] = `true`).
   */
  fun recenterMapOnUser() {
    isCameraFollowingUser = true
    mapPanOffsetX = 0f
    mapPanOffsetY = 0f
  }

  /** Adjusts the Mapbox zoom level by [deltaZoom] (clamped to `[-5.0f, +3.7f]`). */
  fun zoomMapBy(deltaZoom: Float) {
    if (deltaZoom == 0f) return
    mapZoomDelta = (mapZoomDelta + deltaZoom).coerceIn(-5.0f, 3.7f)
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
  }

  /**
   * Updates the user's current GPS location (`userGpsNormalizedX`, `userGpsNormalizedY`).
   *
   * - When [isCameraFollowingUser] is `true` (default), [mapPanOffsetX] and [mapPanOffsetY] stay
   *   `0f`, so the map automatically pans ([mapWorldToScreenShiftX], [mapWorldToScreenShiftY]) to
   *   keep the user's GPS location at the exact center `(0.50f, 0.50f)` of the screen.
   * - When [isCameraFollowingUser] is `false` (after the map has been manually dragged/panned),
   *   the panned camera viewport remains stationary while the user's GPS blue dot moves across
   *   the map.
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
      mapPanOffsetX = (mapPanOffsetX + dx).coerceIn(-0.65f, 0.65f)
      mapPanOffsetY = (mapPanOffsetY + dy).coerceIn(-0.65f, 0.65f)
    }
  }

  // --- Straight-Line Wayfinding Navigation Actions ---

  /**
   * Starts straight-line navigation from the collector's current GPS position to [entityId],
   * ensuring its layer is visible, selecting the entity in collapsed bottom-sheet peek mode,
   * and switching to the Map view with GPS auto-centering enabled.
   */
  fun startNavigationToEntity(entityId: String) {
    val entity = entities.firstOrNull { it.id == entityId } ?: return
    mapLayers =
      mapLayers.map { layer ->
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
   * entity and form geometry layers are visible, selecting the submission, and switching to Map view.
   */
  fun startNavigationToSubmission(submissionId: String) {
    val sub = allSubmissions.firstOrNull { it.id == submissionId } ?: return
    val parentEntity = entities.firstOrNull { it.id == sub.entityId } ?: return
    val geom = submissionGeometries.firstOrNull { it.submissionId == sub.id }
    mapLayers =
      mapLayers.map { layer ->
        if (layer.id == parentEntity.layerId || (geom != null && layer.id == geom.layerId)) {
          layer.copy(isVisible = true)
        } else {
          layer
        }
      }
    navigationTargetKind = NavigationTargetKind.SUBMISSION
    navigationTargetId = sub.id
    selectedEntityId = parentEntity.id
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
    activeSurveyNotice =
      "Straight-line navigation to ${sub.formTitle} • ${parentEntity.label.substringBefore(" •")} ($badge)"
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

  /** Resets the onboarding and prototype state back to the initial Splash screen. */
  fun resetPrototypeFlow() {
    currentScreen = PrototypeScreen.SPLASH
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
    isOfflineBasemapVisible = true
    offlineBasemapStyle = OfflineBasemapStyle.SATELLITE_HYBRID
    mapLayers = defaultMapLayers()
    submissionGeometries = defaultSubmissionGeometries()
    entities = defaultGeospatialEntities()
    selectedEntityId = "entity-nyr-104"
    selectedSubmissionId = null
    navigationTargetKind = null
    navigationTargetId = null
    listSearchQuery = ""
    listFilterTab = ListFilterTab.ALL
    userGpsNormalizedX = 0.50f
    userGpsNormalizedY = 0.50f
    userGpsCoordinatesLabel = "-0.4198°, 36.9512° (±3.2m GPS)"
    isCameraFollowingUser = true
    mapPanOffsetX = 0f
    mapPanOffsetY = 0f
    surveys = defaultSampleSurveys()
    closeActiveFormRunner()
    resetDefaultXFormsXml()
  }

  companion object {
    /** Default sample surveys shared with the user, spanning multiple regions and download states. */
    fun defaultSampleSurveys(): List<SurveyPreviewItem> =
      listOf(
        SurveyPreviewItem(
          id = "survey-kenya-coffee",
          title = "Kenyan Smallholder Coffee Parcel Mapping",
          description =
            "EUDR traceability polygon mapping and shade-tree biodiversity inventory for cooperative coffee growers.",
          location = "Nyeri County, Kenya",
          coordinatesLabel = "0.42°S, 36.95°E",
          offlineSizeLabel = "9.6 MB",
          isDownloaded = true,
          thumbnailTheme = MapThumbnailTheme.HIGHLAND_AGRI,
          entityCount = 4,
        ),
        SurveyPreviewItem(
          id = "survey-amazon-canopy",
          title = "Amazon Basin Deforestation Monitoring",
          description =
            "Field verification of satellite canopy alerts and secondary forest regrowth plots along community boundaries.",
          location = "Pará, Brazil",
          coordinatesLabel = "3.46°S, 62.21°W",
          offlineSizeLabel = "18.4 MB",
          isDownloaded = true,
          thumbnailTheme = MapThumbnailTheme.RAINFOREST,
          entityCount = 142,
        ),
        SurveyPreviewItem(
          id = "survey-serengeti-corridor",
          title = "Serengeti Wildlife Corridor Census",
          description =
            "Seasonal grazing transect observations, waterhole status checks, and human-wildlife coexistence points.",
          location = "Arusha, Tanzania",
          coordinatesLabel = "2.33°S, 34.83°E",
          offlineSizeLabel = "24.1 MB",
          isDownloaded = false,
          thumbnailTheme = MapThumbnailTheme.SAVANNA,
          entityCount = 86,
        ),
        SurveyPreviewItem(
          id = "survey-mekong-mangroves",
          title = "Mekong Delta Mangrove Restoration",
          description =
            "Coastal shoreline erosion monitoring and sapling survival rate audits across intertidal restoration zones.",
          location = "Cần Thơ, Vietnam",
          coordinatesLabel = "9.82°N, 106.34°E",
          offlineSizeLabel = "12.8 MB",
          isDownloaded = false,
          thumbnailTheme = MapThumbnailTheme.COASTAL_DELTA,
          entityCount = 204,
        ),
        SurveyPreviewItem(
          id = "survey-oaxaca-watershed",
          title = "Oaxaca Community Watershed Inventory",
          description =
            "Spring recharge zone delineation, riparian buffer health assessment, and communal water infrastructure survey.",
          location = "Oaxaca, Mexico",
          coordinatesLabel = "17.07°N, 96.72°W",
          offlineSizeLabel = "11.2 MB",
          isDownloaded = false,
          thumbnailTheme = MapThumbnailTheme.WATERSHED,
          entityCount = 95,
        ),
        SurveyPreviewItem(
          id = "survey-sumatra-peatland",
          title = "Sumatra Peatland Rewetting Survey",
          description =
            "Canal blocking structure inspections, groundwater dipwell readings, and paludiculture trial plot tracking.",
          location = "Riau, Indonesia",
          coordinatesLabel = "0.51°N, 101.45°E",
          offlineSizeLabel = "15.7 MB",
          isDownloaded = false,
          thumbnailTheme = MapThumbnailTheme.PEATLAND,
          entityCount = 127,
        ),
      )

    /**
     * Default map layer definitions (`LayerDef` inside `SurveyDef.map_config.layers`), including:
     * - 3 Entity Dataset layers (`LayerDef.entity_dataset_id`, solid outlines)
     * - 3 Form Geometry layers (`LayerDef.form_geometry`, dotted polygon outlines)
     */
    fun defaultMapLayers(): List<MapLayerItem> =
      listOf(
        // Entity Dataset Layers (solid outlines)
        MapLayerItem(
          id = "layer-coffee-parcels",
          label = "Smallholder Coffee Parcels",
          sourceDescription = "Entity Dataset: coffee_parcels (Polygon)",
          colorHex = 0xFF2E7D32,
          geometryTypeLabel = "Polygon",
          isVisible = true,
          sourceType = LayerSourceType.ENTITY_DATASET,
        ),
        MapLayerItem(
          id = "layer-shade-transects",
          label = "Shade Tree Monitoring Plots",
          sourceDescription = "Entity Dataset: shade_monitoring_plots (Polygon)",
          colorHex = 0xFF1565C0,
          geometryTypeLabel = "Polygon",
          isVisible = true,
          sourceType = LayerSourceType.ENTITY_DATASET,
        ),
        MapLayerItem(
          id = "layer-water-points",
          label = "Cooperative Washing Stations",
          sourceDescription = "Entity Dataset: washing_stations (Point)",
          colorHex = 0xFFEF6C00,
          geometryTypeLabel = "Point",
          isVisible = true,
          sourceType = LayerSourceType.ENTITY_DATASET,
        ),
        // Form Geometry Question Layers (dotted polygon outlines, per `LayerDef.form_geometry`)
        MapLayerItem(
          id = "layer-form-walked-perimeter",
          label = "Walked Parcel Perimeters",
          sourceDescription = "Form Field: form-eudr-baseline :: parcel/walked_perimeter_geoshape",
          colorHex = 0xFF66BB6A,
          geometryTypeLabel = "Dotted Polygon",
          isVisible = true,
          sourceType = LayerSourceType.FORM_GEOMETRY,
          formId = "form-eudr-baseline",
          fieldPath = "parcel/walked_perimeter_geoshape",
        ),
        MapLayerItem(
          id = "layer-form-canopy-subzone",
          label = "Surveyed Canopy Audit Sub-Zones",
          sourceDescription = "Form Field: form-shade-canopy-audit :: audit/canopy_sample_polygon",
          colorHex = 0xFF42A5F5,
          geometryTypeLabel = "Dotted Polygon",
          isVisible = true,
          sourceType = LayerSourceType.FORM_GEOMETRY,
          formId = "form-shade-canopy-audit",
          fieldPath = "audit/canopy_sample_polygon",
        ),
        MapLayerItem(
          id = "layer-form-riparian-buffer",
          label = "Riparian Buffer Zone Polygons",
          sourceDescription = "Form Field: form-water-quality :: inspection/riparian_buffer_zone",
          colorHex = 0xFFFFCA28,
          geometryTypeLabel = "Dotted Polygon",
          isVisible = true,
          sourceType = LayerSourceType.FORM_GEOMETRY,
          formId = "form-water-quality",
          fieldPath = "inspection/riparian_buffer_zone",
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
      )

    /** Sample hierarchical forms configured in the active survey. */
    fun defaultForms(): List<FormPreviewItem> =
      listOf(
        FormPreviewItem(
          id = "form-eudr-baseline",
          title = "EUDR Parcel Baseline Registration",
          description =
            "Single-submission baseline georeferencing, deforestation-free attestation, and cultivar count.",
          version = "v2026.09.1",
          submissionModel = SubmissionModel.SINGLE_1_TO_1,
          targetDatasetId = "coffee_parcels",
          targetDatasetName = "Smallholder Coffee Parcels",
          questionCount = 8,
          ctaLabel = "Register baseline parcel",
        ),
        FormPreviewItem(
          id = "form-household-interview",
          title = "Smallholder Household Socio-Economic Survey",
          description =
            "Single-submission grower household interview, farm income diversification, and cooperative membership.",
          version = "v2026.09.1",
          submissionModel = SubmissionModel.SINGLE_1_TO_1,
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
          submissionModel = SubmissionModel.MULTIPLE_1_TO_N,
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
          submissionModel = SubmissionModel.MULTIPLE_1_TO_N,
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
          submissionModel = SubmissionModel.MULTIPLE_1_TO_N,
          targetDatasetId = "washing_stations",
          targetDatasetName = "Cooperative Washing Stations",
          questionCount = 6,
          ctaLabel = "Inspect water effluent",
        ),
      )

    /**
     * Sample Geospatial Entities (`EntityRecord`s) covering both:
     * - `1:1` (`SubmissionModel.SINGLE_1_TO_1`) with inline submission data
     * - `1:N` (`SubmissionModel.MULTIPLE_1_TO_N`) with multiple chronological submissions
     */
    fun defaultGeospatialEntities(): List<GeospatialEntityItem> =
      listOf(
        // 1. 1:1 Entity with a completed baseline submission (shows inline submission data!)
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
          submissionModel = SubmissionModel.SINGLE_1_TO_1,
          normalizedX = 0.28f,
          normalizedY = 0.32f,
          colorHex = 0xFF2E7D32,
          properties =
            mapOf(
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
        // 2. 1:N Entity with 3 chronological submissions (shows list of collector + timestamp!)
        GeospatialEntityItem(
          id = "entity-shade-201",
          label = "Transect SHD-201 • Chinga North Agroforestry",
          datasetId = "shade_monitoring_plots",
          datasetName = "Shade Tree Monitoring Plots",
          layerId = "layer-shade-transects",
          geoId = "S2-10c4b12d9a",
          geometryTypeLabel = "Polygon",
          areaHectares = 3.12,
          perimeterMeters = 738,
          coordinatesLabel = "0.4245°S, 36.9560°E",
          submissionModel = SubmissionModel.MULTIPLE_1_TO_N,
          normalizedX = 0.65f,
          normalizedY = 0.29f,
          colorHex = 0xFF1565C0,
          properties =
            mapOf(
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
        // 3. Another 1:1 Entity where "Interview household" is completed and "Register baseline parcel" is still open
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
          submissionModel = SubmissionModel.SINGLE_1_TO_1,
          normalizedX = 0.34f,
          normalizedY = 0.64f,
          colorHex = 0xFF2E7D32,
          properties =
            mapOf(
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
        // 4. 1:N Point Entity (Cooperative Washing Station)
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
          submissionModel = SubmissionModel.MULTIPLE_1_TO_N,
          normalizedX = 0.72f,
          normalizedY = 0.62f,
          colorHex = 0xFFEF6C00,
          properties =
            mapOf(
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
          tileTypeLabel = "Mapbox Vector Tiles (Contours & Roads)",
          zoomRangeLabel = "Zoom 10–18",
          sizeLabel = "14.2 MB",
          isDownloaded = true,
        ),
        OfflineTilePackageItem(
          id = "tiles-nyeri-satellite",
          regionName = "Othaya & Chinga High-Res Satellite Imagery",
          tileTypeLabel = "Mapbox Satellite Raster Tiles",
          zoomRangeLabel = "Zoom 12–19",
          sizeLabel = "68.5 MB",
          isDownloaded = true,
        ),
        OfflineTilePackageItem(
          id = "tiles-kirinyaga-east",
          regionName = "Kirinyaga Neighboring Cooperative Sector",
          tileTypeLabel = "Mapbox Hybrid Vector + Raster Tiles",
          zoomRangeLabel = "Zoom 11–18",
          sizeLabel = "42.0 MB",
          isDownloaded = false,
        ),
      )
  }
}
