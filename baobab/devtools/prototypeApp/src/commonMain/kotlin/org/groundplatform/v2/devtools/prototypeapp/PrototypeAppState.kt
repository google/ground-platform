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
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import org.groundplatform.v2.core.forms.model.EntityState
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.PrototypeAppDataStore
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ClusterMapFeaturesUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.PrototypeUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.PrototypeAppViewModel

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
  val dataStore: PrototypeAppDataStore = PrototypeAppDataStore(initialSurveys = initialSurveys),
  val viewModel: PrototypeAppViewModel =
    PrototypeAppViewModel(initialScreen = initialScreen, dataStore = dataStore),
) {
  /**
   * Immutable [StateFlow] of [PrototypeUiState] exposed by the underlying MVVM
   * [PrototypeAppViewModel] per Section 6 of `docs/technical/client/architecture.md`.
   */
  val uiState: StateFlow<PrototypeUiState>
    get() {
      syncViewModelState()
      return viewModel.uiState
    }

  /** Synchronizes current state into [dataStore] and [viewModel]. */
  fun syncViewModelState() {
    dataStore.surveys = surveys
    dataStore.activeSurveyId = activeSurveyId
    dataStore.mapLayers = mapLayers
    dataStore.forms = forms
    dataStore.entities = entities
    dataStore.standaloneSubmissions = standaloneSubmissions
    dataStore.submissionGeometries = submissionGeometries
    dataStore.offlineTilePackages = offlineTilePackages
    dataStore.mutations = mutations
    dataStore.places = places
    dataStore.userSettings = userSettings
    viewModel.updateUiState { current ->
      current.copy(
        currentScreen = currentScreen,
        isSignedIn = isSignedIn,
        signedInUserEmail = signedInUserEmail,
        signedInUserName = signedInUserName,
        termsCheckboxChecked = termsCheckboxChecked,
        hasAcceptedTerms = hasAcceptedTerms,
        downloadSurveyEntryOrigin = downloadSurveyEntryOrigin,
        isDownloadSurveySignOutPromptOpen = isDownloadSurveySignOutPromptOpen,
        searchQuery = searchQuery,
        surveys = surveys,
        activeSurveyId = activeSurveyId,
        activeSurveyNotice = activeSurveyNotice,
        isDarkTheme = isDarkTheme,
        deviceFormFactor = deviceFormFactor,
        deviceOrientation = deviceOrientation,
        mainViewMode = mainViewMode,
        isLayersSheetOpen = isLayersSheetOpen,
        selectedBasemapType = selectedBasemapType,
        selectedOfflineBasemapStyle = offlineBasemapStyle,
        isDrawerOpen = isDrawerOpen,
        activeDrawerSubView = activeDrawerSubView,
        selectedUploadStatusFilter = selectedUploadStatusFilter,
        mapLayers = mapLayers,
        forms = forms,
        entities = entities,
        standaloneSubmissions = standaloneSubmissions,
        submissionGeometries = submissionGeometries,
        offlineTilePackages = offlineTilePackages,
        mutations = mutations,
        places = places,
        mapboxPlacesApiResults = mapboxPlacesApiResults,
        isMapboxPlacesSearching = isMapboxPlacesSearching,
        isAirplaneMode = isAirplaneMode,
        selectedEntityId = selectedEntityId,
        selectedClusterId = selectedClusterId,
        selectedPlaceId = selectedPlaceId,
        lastSelectedPlace = lastSelectedPlace,
        isEntityBottomSheetExpanded = isEntityBottomSheetExpanded,
        selectedSubmissionId = selectedSubmissionId,
        listSearchQuery = listSearchQuery,
        listFilterTab = listFilterTab,
        userGpsNormalizedX = userGpsNormalizedX,
        userGpsNormalizedY = userGpsNormalizedY,
        userGpsCoordinatesLabel = userGpsCoordinatesLabel,
        gnssSatelliteCount = gnssSatelliteCount,
        gnssAccuracyMeters = gnssAccuracyMeters,
        isCameraFollowingUser = isCameraFollowingUser,
        locationLockState = locationLockState,
        mapZoomDelta = mapZoomDelta,
        mapPanOffsetX = mapPanOffsetX,
        mapPanOffsetY = mapPanOffsetY,
        activeQrCodeEntityId = activeQrCodeEntityId,
        activeSharedPdfSheet = activeSharedPdfSheet,
        navigationTargetKind = navigationTargetKind,
        navigationTargetId = navigationTargetId,
        userSettings = userSettings,
        customXFormsXml = customXFormsXml,
        selectedWorkbenchExampleForm = selectedWorkbenchExampleForm,
        customFormDef = customFormDef,
        xformsXmlError = xformsXmlError,
        activeDataCollectionEntityId = activeDataCollectionEntityId,
        activeDataCollectionFormId = activeDataCollectionFormId,
        activeFormWizardController = activeFormWizardController,
        isAvailableFormsSheetOpen = isAvailableFormsSheetOpen,
        wasFormLaunchedWithoutEntity = wasFormLaunchedWithoutEntity,
        entityRefSelectorViewMode = entityRefSelectorViewMode,
        entityRefSearchQuery = entityRefSearchQuery,
      )
    }
  }

  /** Pulls updated repository state from [dataStore] after domain use cases execute. */
  private fun pullRepositoryState() {
    surveys = dataStore.surveys
    activeSurveyId = dataStore.activeSurveyId
    mapLayers = dataStore.mapLayers
    forms = dataStore.forms
    entities = dataStore.entities
    standaloneSubmissions = dataStore.standaloneSubmissions
    submissionGeometries = dataStore.submissionGeometries
    offlineTilePackages = dataStore.offlineTilePackages
    mutations = dataStore.mutations
    places = dataStore.places
  }
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
   * `barWidthDp`) via [ClusterMapFeaturesUseCase.computeScaleBarSpec].
   */
  val mapScaleBarSpec: MapScaleBarSpec
    get() = viewModel.clusterMapFeaturesUseCase.computeScaleBarSpec(activeSurveyId, mapZoomDelta)

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
   * Computes a [StraightLineVector] from the collector's current GPS position via
   * [org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ComputeWayfindingNavigationUseCase].
   */
  fun computeStraightLineVector(
    targetNormalizedX: Float,
    targetNormalizedY: Float,
  ): StraightLineVector =
    viewModel.computeWayfindingNavigationUseCase(
      fromNormalizedX = userGpsNormalizedX,
      fromNormalizedY = userGpsNormalizedY,
      targetNormalizedX = targetNormalizedX,
      targetNormalizedY = targetNormalizedY,
      unitSystem = unitSystem,
    )

  /**
   * Resolves the normalized map coordinates `(normalizedX, normalizedY)` and optional
   * [SubmissionGeometryPolygon] for any [SubmissionPreviewItem] via
   * [org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ComputeWayfindingNavigationUseCase].
   */
  fun resolveSubmissionTargetGeometry(
    submissionId: String
  ): Triple<Float, Float, SubmissionGeometryPolygon?>? =
    viewModel.computeWayfindingNavigationUseCase.resolveSubmissionTargetGeometry(
      submissionId = submissionId,
      allSubmissions = allSubmissions,
      submissionGeometries = submissionGeometries,
      entities = entities,
    )

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
   * Returns `true` if [field] in [submissionId] represents a geometry question/field via
   * [org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ComputeWayfindingNavigationUseCase].
   */
  fun isSubmissionFieldGeometry(submissionId: String, field: SubmissionFieldEntry): Boolean =
    viewModel.computeWayfindingNavigationUseCase.isSubmissionFieldGeometry(
      submissionId = submissionId,
      field = field,
      submissionGeometries = submissionGeometries,
    )

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
   * Currently active straight-line navigation session ([StraightLineNavigationState]) resolved via
   * [org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ComputeWayfindingNavigationUseCase].
   */
  val activeNavigation: StraightLineNavigationState?
    get() =
      viewModel.computeWayfindingNavigationUseCase.resolveActiveNavigationState(
        navigationTargetKind = navigationTargetKind,
        navigationTargetId = navigationTargetId,
        fromNormalizedX = userGpsNormalizedX,
        fromNormalizedY = userGpsNormalizedY,
        unitSystem = unitSystem,
        userGpsCoordinatesLabel = userGpsCoordinatesLabel,
        entities = entities,
        allSubmissions = allSubmissions,
        submissionGeometries = submissionGeometries,
        findPlaceById = ::findPlaceById,
      )

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

  /**
   * True when the embedded form runner is open and the current step in [activeFormWizardController]
   * contains a `geopoint` (`DataType.TYPE_GEOPOINT`) question.
   */
  val isCurrentFormStepGeoPoint: Boolean
    get() = isWizardStepGeoPoint(activeFormWizardController?.currentStep)

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
   * has geospatial entities. Returns `emptyList()` when there are no geospatial entities so the
   * map layers section of the `Layers` sheet is hidden.
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
   * True when the active survey has geospatial entities to display in the `Layers` sheet.
   */
  val hasGeospatialEntities: Boolean
    get() = entities.isNotEmpty() && entityDatasetLayers.isNotEmpty()

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
      val (surveyLng, surveyLat) = activeSurveyBaseLngLat()
      return viewModel.searchPlacesUseCase(
        query = listSearchQuery,
        isAirplaneMode = isAirplaneMode,
        listFilterTab = listFilterTab,
        localPlaces = places,
        remoteApiPlaces = mapboxPlacesApiResults,
        surveyBaseLng = surveyLng,
        surveyBaseLat = surveyLat,
        surveyLocationLabel = activeSurvey.location,
      )
    }

  /**
   * Parses a coordinate string (e.g. `"0.5012° S, 36.9324° E"`, `"0.4160°S, 36.9465°E"`, or
   * `"-0.5012, 36.9324"`) into a `(latitude, longitude)` pair.
   */
  fun parsePlaceCoordinates(coordinatesLabel: String): Pair<Double, Double>? =
    org.groundplatform.v2.devtools.prototypeapp.domain.model.parsePlaceCoordinates(coordinatesLabel)

  /**
   * Parses a decimal coordinate query (e.g. `"-0.4210, 36.9505"` or `"0.4210° S, 36.9505° E"`) into
   * an ad-hoc [SurveyPlaceItem] within the survey map bounds, or returns `null` if not a coordinate
   * pair.
   */
  private fun parseCoordinateQueryToPlace(query: String): SurveyPlaceItem? {
    val (surveyLng, surveyLat) = activeSurveyBaseLngLat()
    return viewModel.searchPlacesUseCase.parseCoordinateQueryToPlace(
      query = query,
      surveyBaseLng = surveyLng,
      surveyBaseLat = surveyLat,
      surveyLocationLabel = activeSurvey.location,
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
    val (surveyLng, surveyLat) = activeSurveyBaseLngLat()
    viewModel.placeRepository.searchRemotePlaces(
      surveyId = activeSurveyId,
      query = query,
      isAirplaneMode = isAirplaneMode,
      defaultRegionSubtitle = activeSurvey.location,
      centerLongitude = surveyLng,
      centerLatitude = surveyLat,
    ) { results ->
      if (isAirplaneMode || listSearchQuery.trim() != query.trim()) {
        isMapboxPlacesSearching = false
      } else {
        mapboxPlacesApiResults = results
        isMapboxPlacesSearching = false
      }
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
    val (surveyLng, surveyLat) = activeSurveyBaseLngLat()
    return viewModel.placeRepository.parseRemotePlacesPayload(
      jsonPayload = json,
      defaultRegionSubtitle = activeSurvey.location,
      centerLongitude = surveyLng,
      centerLatitude = surveyLat,
    )
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
    syncViewModelState()
    val result =
      viewModel.completeFormSubmissionUseCase(
        recordInstance = recordInstance,
        entityStates = entityStates,
        controller = activeFormWizardController,
        activeDataCollectionFormId = activeDataCollectionFormId,
        activeDataCollectionEntityId = activeDataCollectionEntityId,
        wasFormLaunchedWithoutEntity = wasFormLaunchedWithoutEntity,
        selectedEntityId = selectedEntityId,
        customFormDef = customFormDef,
        gnssStatusChipLabel = gnssStatusChipLabel,
        signedInUserName = signedInUserName,
        signedInUserEmail = signedInUserEmail,
        userGpsCoordinatesLabel = userGpsCoordinatesLabel,
        userGpsNormalizedX = userGpsNormalizedX,
        userGpsNormalizedY = userGpsNormalizedY,
      ) ?: return
    pullRepositoryState()
    selectedEntityId = result.selectedEntityId
    if (result.updateSelectedSubmissionId) {
      selectedSubmissionId = result.selectedSubmissionId
    }
    activeSurveyNotice = result.noticeMessage
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
    viewModel.mutationRepository.setMutations(mutations)
    viewModel.surveyRepository.setEntities(entities)
    viewModel.surveyRepository.setStandaloneSubmissions(standaloneSubmissions)
    val notice = viewModel.syncMutationsUseCase.syncSingleMutation(mutationId) ?: return
    mutations = viewModel.mutationRepository.getMutations()
    entities = viewModel.surveyRepository.getEntities()
    standaloneSubmissions = viewModel.surveyRepository.getStandaloneSubmissions()
    activeSurveyNotice = notice
  }

  /**
   * Synchronizes all currently pending/in-progress mutations in the `Outbox`, transitioning them to
   * [MutationSyncState.UPLOADED] with completed timestamps.
   */
  fun syncAllOutboxMutations() {
    viewModel.mutationRepository.setMutations(mutations)
    viewModel.surveyRepository.setEntities(entities)
    viewModel.surveyRepository.setStandaloneSubmissions(standaloneSubmissions)
    val notice = viewModel.syncMutationsUseCase.syncAllOutboxMutations(activeSurveyId) ?: return
    mutations = viewModel.mutationRepository.getMutations()
    entities = viewModel.surveyRepository.getEntities()
    standaloneSubmissions = viewModel.surveyRepository.getStandaloneSubmissions()
    activeSurveyNotice = notice
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
   * (targeting its recorded geometry polygon or parent entity location), ensuring the parent
   * entity's map layer is visible, selecting the submission, and switching to Map view.
   */
  fun startNavigationToSubmission(submissionId: String) {
    val sub = allSubmissions.firstOrNull { it.id == submissionId } ?: return
    val parentEntity = entities.firstOrNull { it.id == sub.entityId }
    mapLayers = mapLayers.map { layer ->
      if (parentEntity != null && layer.id == parentEntity.layerId) {
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
    syncViewModelState()
    val totalCount = viewModel.generateRandomSitesUseCase(count)
    pullRepositoryState()
    currentScreen = PrototypeScreen.MAIN_SURVEY
    mainViewMode = MainSurveyViewMode.MAP
    activeDrawerSubView = MainDrawerSubView.NONE
    activeSurveyNotice = "Added $count random polygon features ($totalCount total map features)"
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
    fun defaultPlaces(): List<SurveyPlaceItem> = PrototypeAppDataStore.defaultPlaces()

    /**
     * Default geographic places, towns, landmarks, road junctions, and hydrology features from the
     * Mapbox Places API (`mapbox.places`) searchable via `"Search places or map features..."`.
     */
    fun defaultSurveyPlaces(): List<SurveyPlaceItem> = PrototypeAppDataStore.defaultSurveyPlaces()

    /** Default sample surveys displayed in `devtools/prototypeApp`, sourced from [PrototypeAppDataStore]. */
    fun defaultSampleSurveys(): List<SurveyPreviewItem> =
      PrototypeAppDataStore.defaultSampleSurveys()

    /** Returns a pre-parsed [FormDef] for [example] from [PrototypeAppDataStore]. */
    fun cachedExampleFormDef(example: WorkbenchExampleForm): FormDef? =
      PrototypeAppDataStore.cachedExampleFormDef(example)

    /** Maps a [WorkbenchExampleForm] to its corresponding survey ID via [PrototypeAppDataStore]. */
    fun surveyIdForExampleForm(example: WorkbenchExampleForm): String =
      PrototypeAppDataStore.surveyIdForExampleForm(example)

    /** Maps a survey ID to its corresponding [WorkbenchExampleForm] via [PrototypeAppDataStore]. */
    fun exampleFormForSurveyId(surveyId: String): WorkbenchExampleForm =
      PrototypeAppDataStore.exampleFormForSurveyId(surveyId)

    /** Returns the count of preloaded entities for [surveyId] from [PrototypeAppDataStore]. */
    fun preloadedEntityCountForSurvey(surveyId: String): Int =
      PrototypeAppDataStore.preloadedEntityCountForSurvey(surveyId)

    /** Returns the count of preloaded submissions for [surveyId] from [PrototypeAppDataStore]. */
    fun preloadedSubmissionCountForSurvey(surveyId: String): Int =
      PrototypeAppDataStore.preloadedSubmissionCountForSurvey(surveyId)

    /** Returns the [FormPreviewItem]s for [surveyId] from [PrototypeAppDataStore]. */
    fun formsForSurvey(surveyId: String): List<FormPreviewItem> =
      PrototypeAppDataStore.formsForSurvey(surveyId)

    /** Returns the [GeospatialEntityItem]s for [surveyId] from [PrototypeAppDataStore]. */
    fun entitiesForSurvey(surveyId: String): List<GeospatialEntityItem> =
      PrototypeAppDataStore.entitiesForSurvey(surveyId)

    /** Returns the standalone [SubmissionPreviewItem]s for [surveyId] from [PrototypeAppDataStore]. */
    fun standaloneSubmissionsForSurvey(surveyId: String): List<SubmissionPreviewItem> =
      PrototypeAppDataStore.standaloneSubmissionsForSurvey(surveyId)

    /** Returns the [SubmissionGeometryPolygon]s for [surveyId] from [PrototypeAppDataStore]. */
    fun submissionGeometriesForSurvey(surveyId: String): List<SubmissionGeometryPolygon> =
      PrototypeAppDataStore.submissionGeometriesForSurvey(surveyId)

    /** Returns the [MapLayerItem]s for [surveyId] from [PrototypeAppDataStore]. */
    fun mapLayersForSurvey(surveyId: String): List<MapLayerItem> =
      PrototypeAppDataStore.mapLayersForSurvey(surveyId)

    /** Default map layers for the primary survey from [PrototypeAppDataStore]. */
    fun defaultMapLayers(): List<MapLayerItem> = PrototypeAppDataStore.defaultMapLayers()

    /** Default submission geometries for the primary survey from [PrototypeAppDataStore]. */
    fun defaultSubmissionGeometries(): List<SubmissionGeometryPolygon> =
      PrototypeAppDataStore.defaultSubmissionGeometries()

    /** Default forms for the primary survey from [PrototypeAppDataStore]. */
    fun defaultForms(): List<FormPreviewItem> = PrototypeAppDataStore.defaultForms()

    /** Default standalone submissions for the primary survey from [PrototypeAppDataStore]. */
    fun defaultStandaloneSubmissions(): List<SubmissionPreviewItem> =
      PrototypeAppDataStore.defaultStandaloneSubmissions()

    /** Default geospatial entities for the primary survey from [PrototypeAppDataStore]. */
    fun defaultGeospatialEntities(): List<GeospatialEntityItem> =
      PrototypeAppDataStore.defaultGeospatialEntities()

    /** Default offline basemap tile packages from [PrototypeAppDataStore]. */
    fun defaultOfflineTilePackages(): List<OfflineTilePackageItem> =
      PrototypeAppDataStore.defaultOfflineTilePackages()

    /** Default mutation log items from [PrototypeAppDataStore]. */
    fun defaultMutations(): List<MutationLogItem> = PrototypeAppDataStore.defaultMutations()
  }
}

private val defaultClusterMapFeaturesUseCase = ClusterMapFeaturesUseCase()

/**
 * Performs deterministic spatial clustering on [features] using [ClusterMapFeaturesUseCase].
 */
internal fun computeMapFeatureClusters(
  features: List<MapClusterFeatureItem>,
  radiusNormalized: Float,
): List<MapFeatureCluster> = defaultClusterMapFeaturesUseCase(features, radiusNormalized)
