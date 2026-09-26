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
package org.groundplatform.v2.devtools.prototypeapp.ui.state

import groundplatform.v2.forms.FormDef
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.PrototypeAppDataStore
import org.groundplatform.v2.devtools.prototypeapp.domain.model.BasemapType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceFormFactor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceOrientation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DownloadSurveyEntryOrigin
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ListFilterTab
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocationLockState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MainDrawerSubView
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MainSurveyViewMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationLogItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.NavigationTargetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineBasemapStyle
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineTilePackageItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PrototypeScreen
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SharedPdfSheetState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UploadStatusFilter
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UserSettings

/**
 * Pure, immutable UI state (`PrototypeUiState`) exposed as a `StateFlow<PrototypeUiState>` by
 * [org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.PrototypeAppViewModel] per Section 6 of
 * `docs/technical/client/architecture.md`.
 */
data class PrototypeUiState(
  val currentScreen: PrototypeScreen = PrototypeScreen.SIGN_IN,
  val isSignedIn: Boolean = currentScreen != PrototypeScreen.SIGN_IN,
  val signedInUserEmail: String = "maya.lin@groundplatform.org",
  val signedInUserName: String = "Maya Lin",
  val termsCheckboxChecked: Boolean = true,
  val hasAcceptedTerms: Boolean =
    currentScreen == PrototypeScreen.DOWNLOAD_SURVEY ||
      currentScreen == PrototypeScreen.MAIN_SURVEY,
  val downloadSurveyEntryOrigin: DownloadSurveyEntryOrigin = DownloadSurveyEntryOrigin.AFTER_TOS,
  val isDownloadSurveySignOutPromptOpen: Boolean = false,
  val searchQuery: String = "",
  val surveys: List<SurveyPreviewItem> = PrototypeAppDataStore.defaultSampleSurveys(),
  val activeSurveyId: String = "survey-kenya-coffee",
  val activeSurveyNotice: String? = null,
  val isDarkTheme: Boolean = false,
  val deviceFormFactor: DeviceFormFactor = DeviceFormFactor.MOBILE,
  val deviceOrientation: DeviceOrientation = DeviceFormFactor.MOBILE.defaultOrientation,
  val mainViewMode: MainSurveyViewMode = MainSurveyViewMode.MAP,
  val isLayersSheetOpen: Boolean = false,
  val selectedBasemapType: BasemapType = BasemapType.NORMAL,
  val selectedOfflineBasemapStyle: OfflineBasemapStyle = OfflineBasemapStyle.SATELLITE_HYBRID,
  val isDrawerOpen: Boolean = false,
  val activeDrawerSubView: MainDrawerSubView = MainDrawerSubView.NONE,
  val selectedUploadStatusFilter: UploadStatusFilter? = null,
  val mapLayers: List<MapLayerItem> = PrototypeAppDataStore.defaultMapLayers(),
  val forms: List<FormPreviewItem> = PrototypeAppDataStore.defaultForms(),
  val entities: List<GeospatialEntityItem> = PrototypeAppDataStore.defaultGeospatialEntities(),
  val standaloneSubmissions: List<SubmissionPreviewItem> =
    PrototypeAppDataStore.defaultStandaloneSubmissions(),
  val submissionGeometries: List<SubmissionGeometryPolygon> =
    PrototypeAppDataStore.defaultSubmissionGeometries(),
  val offlineTilePackages: List<OfflineTilePackageItem> =
    PrototypeAppDataStore.defaultOfflineTilePackages(),
  val mutations: List<MutationLogItem> = PrototypeAppDataStore.defaultMutations(),
  val places: List<SurveyPlaceItem> = PrototypeAppDataStore.defaultSurveyPlaces(),
  val mapboxPlacesApiResults: List<SurveyPlaceItem> = emptyList(),
  val isMapboxPlacesSearching: Boolean = false,
  val isAirplaneMode: Boolean = false,
  val selectedEntityId: String? = null,
  val selectedClusterId: String? = null,
  val selectedPlaceId: String? = null,
  val lastSelectedPlace: SurveyPlaceItem? = null,
  val isEntityBottomSheetExpanded: Boolean = false,
  val selectedSubmissionId: String? = null,
  val listSearchQuery: String = "",
  val listFilterTab: ListFilterTab = ListFilterTab.ALL,
  val userGpsNormalizedX: Float = 0.50f,
  val userGpsNormalizedY: Float = 0.50f,
  val userGpsCoordinatesLabel: String = "-0.4198°, 36.9512° (±3.2m GPS)",
  val gnssSatelliteCount: Int = 18,
  val gnssAccuracyMeters: Double = 2.1,
  val isCameraFollowingUser: Boolean = true,
  val locationLockState: LocationLockState = LocationLockState.LOCKED,
  val isMap3dMode: Boolean = false,
  val mapBearingDegrees: Float = 0f,
  val mapZoomDelta: Float = 0f,
  val mapPanOffsetX: Float = 0f,
  val mapPanOffsetY: Float = 0f,
  val cameraTargetCommandSeq: Int = 0,
  val cameraTargetLng: Double? = null,
  val cameraTargetLat: Double? = null,
  val cameraTargetZoom: Float? = null,
  val activeQrCodeEntityId: String? = null,
  val activeSharedPdfSheet: SharedPdfSheetState? = null,
  val navigationTargetKind: NavigationTargetKind? = null,
  val navigationTargetId: String? = null,
  val userSettings: UserSettings = PrototypeAppDataStore.defaultUserSettings(),
  val uploadedMediaCacheSizeLabel: String =
    PrototypeAppDataStore.DEFAULT_UPLOADED_MEDIA_CACHE_SIZE_LABEL,
  val uploadedMediaFileCount: Int = PrototypeAppDataStore.DEFAULT_UPLOADED_MEDIA_FILE_COUNT,
  val isWebsiteModalOpen: Boolean = false,
  val customXFormsXml: String = PrototypeAppDataStore.DEFAULT_PROTOTYPE_XFORMS_XML,
  val selectedWorkbenchExampleForm: WorkbenchExampleForm? = WorkbenchExampleForm.ALL_FIELD_TYPES,
  val customFormDef: FormDef? =
    PrototypeAppDataStore.cachedExampleFormDef(WorkbenchExampleForm.ALL_FIELD_TYPES),
  val xformsXmlError: String? = null,
  val activeDataCollectionEntityId: String? = null,
  val activeDataCollectionFormId: String? = null,
  val activeFormWizardController: FormWizardController? = null,
  val isAvailableFormsSheetOpen: Boolean = false,
  val wasFormLaunchedWithoutEntity: Boolean = false,
  val entityRefSelectorViewMode: MainSurveyViewMode = MainSurveyViewMode.MAP,
  val entityRefSearchQuery: String = "",
)
