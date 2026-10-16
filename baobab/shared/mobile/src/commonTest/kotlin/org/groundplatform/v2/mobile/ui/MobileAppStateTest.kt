/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.mobile.ui

import groundplatform.v2.forms.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.app.data.datasource.local.store.sampleDataHolder
import org.groundplatform.v2.app.domain.model.AppScreen
import org.groundplatform.v2.app.domain.model.BasemapType
import org.groundplatform.v2.app.domain.model.DownloadSurveyEntryOrigin
import org.groundplatform.v2.app.domain.model.EntityDetailsPane
import org.groundplatform.v2.app.domain.model.EntityGeometryKind
import org.groundplatform.v2.app.domain.model.LayerSourceType
import org.groundplatform.v2.app.domain.model.ListFilterTab
import org.groundplatform.v2.app.domain.model.LocationLockState
import org.groundplatform.v2.app.domain.model.MainDrawerSubView
import org.groundplatform.v2.app.domain.model.MainSurveyViewMode
import org.groundplatform.v2.app.domain.model.MapClusterFeatureItem
import org.groundplatform.v2.app.domain.model.MapFeatureKind
import org.groundplatform.v2.app.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.app.domain.model.MutationOperationKind
import org.groundplatform.v2.app.domain.model.MutationSyncState
import org.groundplatform.v2.app.domain.model.NavigationTargetKind
import org.groundplatform.v2.app.domain.model.OfflineBasemapStyle
import org.groundplatform.v2.app.domain.model.SyncStatus
import org.groundplatform.v2.app.domain.model.UploadStatusFilter
import org.groundplatform.v2.app.domain.model.UserSettings
import org.groundplatform.v2.app.domain.model.WEBSITE_URL
import org.groundplatform.v2.app.domain.model.formatClusterSitesCountLabel
import org.groundplatform.v2.app.domain.model.geometryKind
import org.groundplatform.v2.app.domain.model.groupClusterFeaturesByMarkerSymbol
import org.groundplatform.v2.app.domain.model.hasGeometry
import org.groundplatform.v2.app.domain.model.maxFramingZoom
import org.groundplatform.v2.app.domain.model.relatedEntityForPropertyValue
import org.groundplatform.v2.app.ui.map.SurveyMap
import org.groundplatform.v2.app.ui.map.SurveyMapContent
import org.groundplatform.v2.app.ui.map.SurveyMapIds
import org.groundplatform.v2.app.ui.map.SurveyMarker
import org.groundplatform.v2.app.ui.state.formGeometryBounds
import org.groundplatform.v2.app.ui.state.maxFramingZoom
import org.groundplatform.v2.core.forms.ui.MapDrawingKind
import org.groundplatform.v2.map.LngLatBounds

class MobileAppStateTest {

  @Test
  fun initialState_startsOnSignInWithSampleSurveys() {
    val state = MobileAppState(dataHolder = sampleDataHolder())

    assertEquals(AppScreen.SIGN_IN, state.currentScreen)
    assertFalse(state.isSignedIn)
    assertFalse(state.hasAcceptedTerms)
    assertEquals(6, state.surveys.size)
    assertEquals(6, state.filteredSurveys.size)
    assertTrue(state.downloadedSurveyCount >= 1)
  }

  @Test
  fun onboardingFlow_advancesThroughSignInTermsAndDownloadSurvey() {
    val state = MobileAppState(dataHolder = sampleDataHolder())

    // 1. Initial Screen is Sign In
    assertEquals(AppScreen.SIGN_IN, state.currentScreen)

    // 2. Sign In with Google -> Terms of Service
    state.signInWithGoogle()
    assertTrue(state.isSignedIn)
    assertEquals(AppScreen.TERMS_OF_SERVICE, state.currentScreen)

    // 3. Accept Terms of Service -> Download Survey
    state.acceptTermsOfService()
    assertTrue(state.hasAcceptedTerms)
    assertEquals(AppScreen.DOWNLOAD_SURVEY, state.currentScreen)
  }

  @Test
  fun declineTermsOfService_returnsToSignInAndResetsAuth() {
    val state = MobileAppState(dataHolder = sampleDataHolder())
    state.signInWithGoogle()
    assertEquals(AppScreen.TERMS_OF_SERVICE, state.currentScreen)

    state.declineTermsOfService()
    assertEquals(AppScreen.SIGN_IN, state.currentScreen)
    assertFalse(state.isSignedIn)
    assertFalse(state.hasAcceptedTerms)
  }

  @Test
  fun searchQuery_filtersSurveysByNameLocationAndOrganization() {
    val state =
      MobileAppState(initialScreen = AppScreen.DOWNLOAD_SURVEY, dataHolder = sampleDataHolder())

    // Search by survey title keyword
    state.updateSearchQuery("Mangrove")
    assertEquals(1, state.filteredSurveys.size)
    assertEquals("Mekong Delta Mangrove Restoration", state.filteredSurveys.first().title)

    // Search by location (Country / Region)
    state.updateSearchQuery("Nyeri")
    assertEquals(1, state.filteredSurveys.size)
    assertEquals("Nyeri County, Kenya", state.filteredSurveys.first().location)

    // Search by organization name (Kenya Forest Service runs three sample surveys)
    state.updateSearchQuery("Forest Service")
    assertEquals(3, state.filteredSurveys.size)
    assertTrue(state.filteredSurveys.all { it.organizationId == "org-kenya-forest-service" })

    // Search by another location
    state.updateSearchQuery("Brazil")
    assertEquals(1, state.filteredSurveys.size)
    assertEquals("Sample Plot Forest Assessment Survey", state.filteredSurveys.first().title)

    // Clearing search restores full shared list
    state.clearSearchQuery()
    assertEquals(6, state.filteredSurveys.size)
  }

  @Test
  fun openSurvey_navigatesToMainSurveyScreenWithNoDefaultSelectedSite() {
    val state =
      MobileAppState(initialScreen = AppScreen.DOWNLOAD_SURVEY, dataHolder = sampleDataHolder())
    assertEquals(null, state.selectedEntityId)
    assertEquals(null, state.selectedEntity)

    state.openSurvey("survey-kenya-coffee")

    assertEquals(AppScreen.MAIN_SURVEY, state.currentScreen)
    assertEquals(MainSurveyViewMode.MAP, state.mainViewMode)
    assertEquals("survey-kenya-coffee", state.activeSurvey.id)
    assertEquals(null, state.selectedEntityId)
    assertEquals(null, state.selectedEntity)
    assertFalse(state.isEntityBottomSheetExpanded)
  }

  @Test
  fun toggleLayerVisibility_filtersVisibleMapEntitiesAndDeselectsHiddenEntity() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    assertEquals(5, state.visibleMapEntities.size)
    assertEquals(null, state.selectedEntityId)

    state.selectEntity("entity-nyr-104")
    assertEquals("entity-nyr-104", state.selectedEntityId)

    // Hide the "Smallholder Coffee Parcels" layer (`layer-coffee-parcels`)
    state.toggleLayerVisibility("layer-coffee-parcels")

    // The 3 entities on `layer-coffee-parcels` should hide
    assertEquals(2, state.visibleMapEntities.size)
    assertEquals(null, state.selectedEntity)

    // Re-enable layer
    state.toggleLayerVisibility("layer-coffee-parcels")
    assertEquals(5, state.visibleMapEntities.size)
  }

  @Test
  fun selectEntities_supportsUnified1ToNSubmissionsAndSimplestyleMarkersAcrossGeometries() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())

    // 1. Stage 1 (Initialized Empty Circle "○") Polygon entity: `entity-nyr-112`
    val nyr112 = state.entities.first { it.id == "entity-nyr-112" }
    assertEquals("Polygon", nyr112.geometryTypeLabel)
    assertEquals("○", nyr112.markerSymbol)
    assertEquals("#E65100", nyr112.markerColorCss)
    assertEquals("Pending", nyr112.workflowStatus)
    assertEquals("○ Pending", nyr112.mapStatusSummaryBadge)
    assertEquals(0, nyr112.submissions.size)

    // 2. Stage 2 (Half-Filled Circle "◐") Polygon entity: `entity-nyr-108`
    val nyr108 = state.entities.first { it.id == "entity-nyr-108" }
    assertEquals("Polygon", nyr108.geometryTypeLabel)
    assertEquals("◐", nyr108.markerSymbol)
    assertEquals("#F9AB00", nyr108.markerColorCss)
    assertEquals("In progress", nyr108.workflowStatus)
    assertEquals("◐ In progress", nyr108.mapStatusSummaryBadge)
    assertEquals(1, nyr108.submissions.size)

    // 3. Stage 3 (Completed Checkmark "✓") Polygon entity: `entity-nyr-104`
    state.selectEntity("entity-nyr-104")
    val nyr104 = state.selectedEntity!!
    assertEquals("Polygon", nyr104.geometryTypeLabel)
    assertEquals("✓", nyr104.markerSymbol)
    assertEquals("#1E8E3E", nyr104.markerColorCss)
    assertEquals("Completed", nyr104.workflowStatus)
    assertEquals("✓ Completed", nyr104.mapStatusSummaryBadge)
    assertEquals(1, nyr104.submissions.size)
    assertEquals("Maya Lin", nyr104.submissions.first().collectorName)

    // 4. LineString entity (`entity-shade-201`) and Point entity (`entity-station-01`)
    state.selectEntity("entity-shade-201")
    val shade201 = state.selectedEntity!!
    assertEquals("LineString", shade201.geometryTypeLabel)
    assertEquals("✓", shade201.markerSymbol)
    assertEquals(3, shade201.submissions.size)
    assertEquals(null, state.selectedSubmission)

    val station01 = state.entities.first { it.id == "entity-station-01" }
    assertEquals("Point", station01.geometryTypeLabel)
    assertEquals("◐", station01.markerSymbol)
    assertEquals(2, station01.submissions.size)

    // Click a specific submission in the unified 1:N list to view its full submission details
    state.selectSubmissionDetail("sub-shade-201-wave3")
    val detail = state.selectedSubmission!!
    assertEquals("Maya Lin", detail.collectorName)
    assertEquals("2026-09-19 08:45 UTC", detail.timestamp)
    assertTrue(detail.fields.any { it.questionName == "surviving_saplings_count" })
  }

  @Test
  fun entityBottomSheetFormButtons_allowAll1ToNSubmissions_andProgressMarkerFromEmptyToHalfToCheckmark() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())

    // All forms linked to an entity dataset follow 1:N cardinality and remain enabled
    val nyr104 = state.entities.first { it.id == "entity-nyr-104" }
    val nyr104Forms = state.formsForEntity(nyr104)
    assertEquals(2, nyr104Forms.size)
    assertEquals(
      listOf("Register baseline parcel", "Interview household"),
      nyr104Forms.map { it.ctaLabel },
    )
    assertTrue(nyr104Forms.all { state.isFormButtonEnabled(nyr104, it) })

    // Verify 3-stage marker progression on `entity-nyr-112`: ○ (Pending) -> ◐ (In progress) -> ✓
    // (Completed)
    val initial112 = state.entities.first { it.id == "entity-nyr-112" }
    assertEquals("○", initial112.markerSymbol)
    assertEquals("#E65100", initial112.markerColorCss)
    assertEquals("Pending", initial112.workflowStatus)

    // 1st submission transitions marker-symbol from "○" to "◐" (half-filled circle)
    state.launchFormForEntity(entityId = "entity-nyr-112", formId = "form-household-interview")
    state.completeActiveFormSubmission()
    val stage2Entity = state.entities.first { it.id == "entity-nyr-112" }
    assertEquals("◐", stage2Entity.markerSymbol)
    assertEquals("#F9AB00", stage2Entity.markerColorCss)
    assertEquals("In progress", stage2Entity.workflowStatus)
    assertEquals(1, stage2Entity.submissions.size)

    // 2nd submission transitions marker-symbol from "◐" to "✓" (checkmark)
    state.launchFormForEntity(entityId = "entity-nyr-112", formId = "form-eudr-baseline")
    state.completeActiveFormSubmission()
    val stage3Entity = state.entities.first { it.id == "entity-nyr-112" }
    assertEquals("✓", stage3Entity.markerSymbol)
    assertEquals("#1E8E3E", stage3Entity.markerColorCss)
    assertEquals("Completed", stage3Entity.workflowStatus)
    assertEquals(2, stage3Entity.submissions.size)
  }

  @Test
  fun navigationDrawerActions_switchSurveysManageOfflineMapsSettingsTosAndSignOut() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    state.updateDrawerOpen(true)
    assertTrue(state.isDrawerOpen)

    // Open Manage Offline Maps sub-screen
    state.drawerManageOfflineMaps()
    assertEquals(MainDrawerSubView.MANAGE_OFFLINE_MAPS, state.activeDrawerSubView)

    // Open Settings sub-screen and toggle units
    state.drawerOpenSettings()
    assertEquals(MeasurementUnitSystem.METRIC, state.unitSystem)
    state.updateUnitSystem(MeasurementUnitSystem.IMPERIAL)
    assertEquals(MeasurementUnitSystem.IMPERIAL, state.unitSystem)

    // "Switch surveys" opens the separate SWITCH_SURVEYS sub-screen showing only downloaded surveys
    state.drawerSwitchSurveys()
    assertEquals(MainDrawerSubView.SWITCH_SURVEYS, state.activeDrawerSubView)
    assertTrue(state.downloadedSurveys.all { it.isDownloaded })
    assertEquals(5, state.downloadedSurveys.size)

    // Primary action button on Switch Surveys screen navigates to DOWNLOAD_SURVEY
    state.openDownloadMoreSurveysScreen()
    assertEquals(AppScreen.DOWNLOAD_SURVEY, state.currentScreen)

    // Sign out returns to SIGN_IN
    state.openSurvey("survey-kenya-coffee")
    state.drawerSignOut()
    assertEquals(AppScreen.SIGN_IN, state.currentScreen)
    assertFalse(state.isSignedIn)
  }

  @Test
  fun qrCodeAndSharePdfModals_openForGeospatialEntitiesAndSubmissions() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())

    // GNSS satellite count and accuracy chip label
    assertEquals(18, state.gnssSatelliteCount)
    assertEquals("±2.1 m", state.gnssStatusChipLabel)

    // Open QR Code modal for geospatial entity
    state.openEntityQrCode("entity-nyr-104")
    assertEquals("entity-nyr-104", state.activeQrCodeEntity?.id)
    state.closeEntityQrCode()
    assertEquals(null, state.activeQrCodeEntity)

    // Share PDF for geospatial entity
    state.shareEntityPdf("entity-nyr-104")
    assertTrue(state.activeSharedPdfSheet?.pdfFileName?.contains("S2-10c4a89e2f") == true)
    state.closeSharePdfSheet()
    assertEquals(null, state.activeSharedPdfSheet)

    // Share PDF for submission
    state.shareSubmissionPdf("sub-nyr-104-baseline")
    assertTrue(state.activeSharedPdfSheet?.pdfFileName?.contains("sub-nyr-104-baseline") == true)
    state.closeSharePdfSheet()
    assertEquals(null, state.activeSharedPdfSheet)
  }

  @Test
  fun mapLayersAndOfflineBasemap_toggleIndependentlyViaLayersSheet() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())

    // 3 entity dataset layers; submissions are not shown as map layers
    assertEquals(3, state.entityDatasetLayers.size)
    assertEquals(3, state.visibleEntityDatasetLayers.size)
    assertEquals(emptyList(), state.visibleSubmissionGeometries)

    // Toggle off the "Smallholder Coffee Parcels" map layer (`layer-coffee-parcels`)
    state.toggleLayerVisibility("layer-coffee-parcels")
    assertEquals(2, state.visibleEntityDatasetLayers.size)

    // Selecting a submission geometry resolves its parent entity and submission
    state.selectSubmissionGeometry("geom-sub-shade-201-w3")
    assertEquals("entity-shade-201", state.selectedEntity?.id)
    assertEquals("sub-shade-201-wave3", state.selectedSubmission?.id)

    // Select between Map (NORMAL) vs Satellite basemap and toggle offline basemap visibility
    assertEquals("Map", BasemapType.NORMAL.label)
    assertEquals("Satellite", BasemapType.SATELLITE.label)
    assertEquals(BasemapType.SATELLITE, state.selectedBasemapType)
    assertTrue(state.isOfflineBasemapVisible)

    state.selectBasemapType(BasemapType.NORMAL)
    assertEquals(BasemapType.NORMAL, state.selectedBasemapType)
    assertEquals(OfflineBasemapStyle.VECTOR_TOPO, state.offlineBasemapStyle)

    state.toggleBasemapType()
    assertEquals(BasemapType.SATELLITE, state.selectedBasemapType)

    state.toggleOfflineBasemapVisibility()
    assertFalse(state.isOfflineBasemapVisible)
  }

  @Test
  fun mapCamera_autoCentersOnUserGpsByDefault_andShowsRecenterWhenDragged() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())

    // By default, camera follows user and keeps user's GPS location at center (0.50, 0.50)
    assertTrue(state.isCameraFollowingUser)
    assertEquals(0.50f, state.userScreenNormalizedX, 0.0001f)
    assertEquals(0.50f, state.userScreenNormalizedY, 0.0001f)

    // When the user's GPS position moves while isCameraFollowingUser is true,
    // the map automatically pans so the user's GPS dot stays centered at (0.50, 0.50)
    state.updateUserGpsLocation(newNormalizedX = 0.64f, newNormalizedY = 0.38f)
    assertTrue(state.isCameraFollowingUser)
    assertEquals(0.50f, state.userGpsNormalizedX + state.mapWorldToScreenShiftX, 0.0001f)
    assertEquals(0.50f, state.userGpsNormalizedY + state.mapWorldToScreenShiftY, 0.0001f)

    // Dragging (panning) the map disengages auto-centering (showing the "Recenter" button)
    state.panMap(deltaNormalizedX = 0.12f, deltaNormalizedY = -0.09f)
    assertFalse(state.isCameraFollowingUser)
    assertEquals(0.62f, state.userScreenNormalizedX, 0.0001f)
    assertEquals(0.41f, state.userScreenNormalizedY, 0.0001f)

    // Clicking "Recenter" re-engages auto-centering and brings the GPS location back to center
    state.recenterMapOnUser()
    assertTrue(state.isCameraFollowingUser)
    assertEquals(0f, state.mapPanOffsetX, 0.0001f)
    assertEquals(0f, state.mapPanOffsetY, 0.0001f)
    assertEquals(0.50f, state.userGpsNormalizedX + state.mapWorldToScreenShiftX, 0.0001f)
    assertEquals(0.50f, state.userGpsNormalizedY + state.mapWorldToScreenShiftY, 0.0001f)
  }

  @Test
  fun recenterMapOnEntity_adjustsPanOffset_centeringEntityAtTargetScreenY() {
    val state = MobileAppState(dataHolder = sampleDataHolder())
    val entity = state.entities.first()

    assertEquals(0L, state.entitySelectionEpoch)
    state.selectEntity(entity.id)
    assertEquals(1L, state.entitySelectionEpoch)
    assertEquals(entity.id, state.selectedEntityId)

    // Recenter at standard 0.50f center
    state.recenterMapOnEntity(entity, targetScreenY = 0.50f)
    assertFalse(state.isCameraFollowingUser)
    assertEquals(LocationLockState.PANNED, state.locationLockState)
    assertEquals(0.50f, entity.normalizedX + state.mapWorldToScreenShiftX, 0.0001f)
    assertEquals(0.50f, entity.normalizedY + state.mapWorldToScreenShiftY, 0.0001f)

    // Recenter adjusting for reduced viewport above expanded table (e.g. 0.26f)
    state.recenterMapOnEntity(entity, targetScreenY = 0.26f)
    assertEquals(0.50f, entity.normalizedX + state.mapWorldToScreenShiftX, 0.0001f)
    assertEquals(0.26f, entity.normalizedY + state.mapWorldToScreenShiftY, 0.0001f)

    // Selecting entity again increments entitySelectionEpoch
    state.selectEntity(entity.id)
    assertEquals(2L, state.entitySelectionEpoch)

    // Selecting from list also increments entitySelectionEpoch
    state.selectEntityFromList(entity.id)
    assertEquals(3L, state.entitySelectionEpoch)
  }

  @Test
  fun entityBottomSheet_startsCollapsedAndSupportsExpandAndCollapse() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())

    // No entity is selected by default so the bottom sheet is not visible
    assertEquals(null, state.selectedEntityId)
    assertEquals(null, state.selectedEntity)
    assertFalse(state.isEntityBottomSheetExpanded)

    // Selecting an entity opens the bottom sheet in collapsed (compact peek bar) state by default
    state.selectEntity("entity-nyr-104")
    assertEquals("entity-nyr-104", state.selectedEntityId)
    assertFalse(state.isEntityBottomSheetExpanded)

    // Toggling expands the bottom sheet
    state.toggleEntityBottomSheetExpanded()
    assertTrue(state.isEntityBottomSheetExpanded)

    // Explicit collapse returns to peek state without deselecting the entity
    state.updateEntityBottomSheetExpanded(false)
    assertFalse(state.isEntityBottomSheetExpanded)
    assertEquals("entity-nyr-104", state.selectedEntityId)

    // Selecting an entity opens the bottom sheet in collapsed/peek state so the map stays visible
    state.selectEntity("entity-shade-201")
    assertEquals("entity-shade-201", state.selectedEntityId)
    assertFalse(state.isEntityBottomSheetExpanded)

    // Swiping up expands the bottom sheet to full screen
    state.updateEntityBottomSheetExpanded(true)
    assertTrue(state.isEntityBottomSheetExpanded)

    // Swiping down collapses the bottom sheet back to peek state
    state.updateEntityBottomSheetExpanded(false)
    assertFalse(state.isEntityBottomSheetExpanded)

    // Closing the sheet deselects the entity and resets expanded state
    state.selectEntity(null)
    assertEquals(null, state.selectedEntityId)
    assertFalse(state.isEntityBottomSheetExpanded)
  }

  @Test
  fun mapZoomControls_supportZoomInZoomOutAndReset() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    assertEquals(0f, state.mapZoomDelta)
    assertEquals("15.3z", state.effectiveMapZoomLabel)
    val initialScale = state.mapScaleBarSpec
    assertTrue(initialScale.distanceMeters > 0)
    assertTrue(initialScale.barWidthDp in 44f..114f)

    // Zoom in (+0.75) -> 16.1z
    state.zoomInMap()
    assertEquals(0.75f, state.mapZoomDelta)
    assertEquals("16.1z", state.effectiveMapZoomLabel)
    state.zoomInMap()
    state.zoomInMap()
    val zoomedInScale = state.mapScaleBarSpec
    assertTrue(zoomedInScale.distanceMeters < initialScale.distanceMeters)

    // Zoom out
    state.resetMapZoom()
    state.zoomOutMap()
    state.zoomOutMap()
    val zoomedOutScale = state.mapScaleBarSpec
    assertTrue(zoomedOutScale.distanceMeters > initialScale.distanceMeters)

    // Reset zoom restores 0f delta (15.3z)
    state.resetMapZoom()
    assertEquals(0f, state.mapZoomDelta)
    assertEquals("15.3z", state.effectiveMapZoomLabel)
    assertEquals(initialScale, state.mapScaleBarSpec)
  }

  @Test
  fun groundSettingsAndSignInLanguageSelector_syncUserSettingsAndLocales() {
    val state = MobileAppState(initialScreen = AppScreen.SIGN_IN, dataHolder = sampleDataHolder())

    // Default settings match ground-android UserSettings defaults
    assertEquals("en", state.selectedLanguageCode)
    assertEquals("English", state.selectedLanguageDisplayName)
    assertEquals("en (English)", state.selectedLanguageLocale)
    assertFalse(state.shouldUploadPhotosOnWifiOnly)
    assertEquals(MeasurementUnitSystem.METRIC, state.unitSystem)
    assertEquals(
      UserSettings(
        language = "en",
        measurementUnits = MeasurementUnitSystem.METRIC,
        shouldUploadPhotosOnWifiOnly = false,
      ),
      state.userSettings,
    )

    // Selecting language on Sign-In screen updates both settings and locale display
    state.updateSelectedLanguage("fr")
    assertEquals("fr", state.selectedLanguageCode)
    assertEquals("Français", state.selectedLanguageDisplayName)
    assertEquals("fr (Français)", state.selectedLanguageLocale)
    assertEquals("fr", state.userSettings.language)

    // Updating via legacy label or code also works seamlessly
    state.updateLanguageLocale("Español")
    assertEquals("es", state.selectedLanguageCode)
    assertEquals("Español", state.selectedLanguageDisplayName)
    assertEquals("es", state.userSettings.language)

    // Toggle Upload photos over Wi-Fi only switch
    state.updateUploadMediaOverUnmeteredConnectionOnly(true)
    assertTrue(state.shouldUploadPhotosOnWifiOnly)
    assertTrue(state.userSettings.shouldUploadPhotosOnWifiOnly)

    // Change measurement units to Imperial
    state.updateUnitSystem(MeasurementUnitSystem.IMPERIAL)
    assertEquals(MeasurementUnitSystem.IMPERIAL, state.userSettings.measurementUnits)

    // Visit website action records https://groundplatform.org/
    state.visitGroundWebsite()
    assertEquals(WEBSITE_URL, state.visitedWebsiteUrl)
    assertTrue(state.activeSurveyNotice?.contains(WEBSITE_URL) == true)
  }

  @Test
  fun mobileUiDataModels_doNotContain1To1Or1ToNIndicators() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())

    state.mapLayers.forEach { layer ->
      assertFalse(layer.label.contains("1:1"), "Unexpected 1:1 in layer label: ${layer.label}")
      assertFalse(layer.label.contains("1:N"), "Unexpected 1:N in layer label: ${layer.label}")
    }

    state.forms.forEach { form ->
      assertFalse(form.title.contains("1:1"), "Unexpected 1:1 in form title: ${form.title}")
      assertFalse(form.title.contains("1:N"), "Unexpected 1:N in form title: ${form.title}")
      assertFalse(
        form.description.contains("1:1"),
        "Unexpected 1:1 in form description: ${form.description}",
      )
      assertFalse(
        form.description.contains("1:N"),
        "Unexpected 1:N in form description: ${form.description}",
      )
      assertFalse(
        form.targetDatasetName.contains("1:1"),
        "Unexpected 1:1 in form targetDatasetName: ${form.targetDatasetName}",
      )
      assertFalse(
        form.targetDatasetName.contains("1:N"),
        "Unexpected 1:N in form targetDatasetName: ${form.targetDatasetName}",
      )
    }

    state.entities.forEach { entity ->
      assertFalse(entity.label.contains("1:1"), "Unexpected 1:1 in entity label: ${entity.label}")
      assertFalse(entity.label.contains("1:N"), "Unexpected 1:N in entity label: ${entity.label}")
      assertFalse(
        entity.datasetName.contains("1:1"),
        "Unexpected 1:1 in entity datasetName: ${entity.datasetName}",
      )
      assertFalse(
        entity.datasetName.contains("1:N"),
        "Unexpected 1:N in entity datasetName: ${entity.datasetName}",
      )
    }
  }

  @Test
  fun straightLineNavigation_toEntityAndSubmission_computesBearingDistanceAndStepsUser() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    assertEquals(null, state.activeNavigation)

    // 1. Start straight-line navigation to a Geospatial Entity
    state.startNavigationToEntity("entity-nyr-104")
    val entityNav = assertNotNull(state.activeNavigation)
    assertEquals(NavigationTargetKind.ENTITY, entityNav.targetKind)
    assertEquals("entity-nyr-104", entityNav.targetId)
    assertTrue(state.isNavigatingToEntity("entity-nyr-104"))
    assertFalse(state.isNavigatingToSubmission("sub-nyr-104-a"))
    assertTrue(entityNav.vector.distanceMeters > 0)
    assertTrue(entityNav.vector.bearingDegrees in 0..359)
    assertTrue(
      entityNav.formattedDistance.endsWith("m") || entityNav.formattedDistance.endsWith("km")
    )

    // Verify the survey map draws the navigation line, target, and distance pill
    val entityNavMap = SurveyMapContent.main(state, showNavigation = true)
    assertTrue(
      entityNavMap.overlayIds().containsAll(listOf("navigation-line", "navigation-target"))
    )
    val navPill = entityNavMap.markers[SurveyMapIds.NAVIGATION] as SurveyMarker.NavigationPill
    assertTrue(navPill.text.contains(entityNav.vector.formattedDistance))

    // Verify Imperial unit formatting on active straight-line navigation
    state.updateUnitSystem(MeasurementUnitSystem.IMPERIAL)
    val imperialNav = assertNotNull(state.activeNavigation)
    assertTrue(
      imperialNav.formattedDistance.endsWith("ft") || imperialNav.formattedDistance.endsWith("mi"),
      "Expected Imperial distance suffix but got: ${imperialNav.formattedDistance}",
    )
    state.updateUnitSystem(MeasurementUnitSystem.METRIC)

    // 2. Simulate walking closer toward the navigation target until arrival
    val initialDist = entityNav.vector.distanceMeters
    state.stepUserTowardNavigationTarget(stepFraction = 0.5f)
    val closerNav = assertNotNull(state.activeNavigation)
    assertTrue(closerNav.vector.distanceMeters < initialDist)

    repeat(10) { state.stepUserTowardNavigationTarget(stepFraction = 0.5f) }
    val arrivedNav = assertNotNull(state.activeNavigation)
    assertTrue(arrivedNav.vector.isArrived)
    assertEquals(0, arrivedNav.vector.estimatedWalkMinutes)

    // 3. Switch straight-line navigation to a Form Submission
    state.startNavigationToSubmission("sub-shade-201-wave3")
    val subNav = assertNotNull(state.activeNavigation)
    assertEquals(NavigationTargetKind.SUBMISSION, subNav.targetKind)
    assertEquals("sub-shade-201-wave3", subNav.targetId)
    assertTrue(state.isNavigatingToSubmission("sub-shade-201-wave3"))
    assertFalse(state.isNavigatingToEntity("entity-nyr-104"))
    assertNotNull(state.formattedWayfindingBadgeForSubmission("sub-shade-201-wave3"))

    assertTrue(
      "navigation-line" in SurveyMapContent.main(state, showNavigation = true).overlayIds()
    )

    // 4. Toggle / Stop straight-line navigation
    state.toggleNavigationToSubmission("sub-shade-201-wave3")
    assertEquals(null, state.activeNavigation)
    val stoppedMap = SurveyMapContent.main(state, showNavigation = true)
    assertFalse("navigation-line" in stoppedMap.overlayIds())
    assertFalse(SurveyMapIds.NAVIGATION in stoppedMap.markers)

    // 5. Verify distance/wayfinding badge is computed ONLY for geometry fields in a submission
    val wave3Sub =
      assertNotNull(state.allSubmissions.firstOrNull { it.id == "sub-shade-201-wave3" })
    val geomField =
      assertNotNull(
        wave3Sub.fields.firstOrNull { it.questionName == "audit/canopy_sample_polygon" }
      )
    val nonGeomField =
      assertNotNull(wave3Sub.fields.firstOrNull { it.questionName == "surviving_saplings_count" })
    assertTrue(state.isSubmissionFieldGeometry(wave3Sub.id, geomField))
    assertFalse(state.isSubmissionFieldGeometry(wave3Sub.id, nonGeomField))
    assertTrue(
      state.formattedWayfindingBadgeForSubmissionField(wave3Sub.id, geomField).isNotEmpty()
    )
    assertEquals("", state.formattedWayfindingBadgeForSubmissionField(wave3Sub.id, nonGeomField))
  }

  @Test
  fun downloadSurveyEscapeHatch_afterTosPromptsBeforeSignOut_andFromSurveyListReturnsToList() {
    val state = MobileAppState(dataHolder = sampleDataHolder())
    state.signInWithGoogle()
    state.acceptTermsOfService()

    // 1. Reached after ToS -> origin is AFTER_TOS
    assertEquals(AppScreen.DOWNLOAD_SURVEY, state.currentScreen)
    assertEquals(DownloadSurveyEntryOrigin.AFTER_TOS, state.downloadSurveyEntryOrigin)
    assertFalse(state.isDownloadSurveyAccessedFromSurveyList)
    assertFalse(state.isDownloadSurveySignOutPromptOpen)

    // Back action prompts first without immediately signing out
    state.navigateBackFromDownloadSurvey()
    assertTrue(state.isDownloadSurveySignOutPromptOpen)
    assertEquals(AppScreen.DOWNLOAD_SURVEY, state.currentScreen)
    assertTrue(state.isSignedIn)

    // Canceling prompt keeps user on Download surveys screen and signed in
    state.dismissDownloadSurveySignOutPrompt()
    assertFalse(state.isDownloadSurveySignOutPromptOpen)
    assertEquals(AppScreen.DOWNLOAD_SURVEY, state.currentScreen)
    assertTrue(state.isSignedIn)

    // Confirming sign out from prompt signs the user out and returns to Sign In screen
    state.navigateBackFromDownloadSurvey()
    assertTrue(state.isDownloadSurveySignOutPromptOpen)
    state.confirmDownloadSurveySignOut()
    assertFalse(state.isDownloadSurveySignOutPromptOpen)
    assertFalse(state.isSignedIn)
    assertFalse(state.hasAcceptedTerms)
    assertEquals(AppScreen.SIGN_IN, state.currentScreen)

    // 2. Re-sign in, open a survey, and access Download surveys from the Survey list
    // (SWITCH_SURVEYS)
    state.signInWithGoogle()
    state.acceptTermsOfService()
    state.openSurvey("survey-kenya-coffee")
    state.drawerSwitchSurveys()
    assertEquals(AppScreen.MAIN_SURVEY, state.currentScreen)
    assertEquals(MainDrawerSubView.SWITCH_SURVEYS, state.activeDrawerSubView)

    state.openDownloadMoreSurveysScreen()
    assertEquals(AppScreen.DOWNLOAD_SURVEY, state.currentScreen)
    assertEquals(DownloadSurveyEntryOrigin.SURVEY_LIST, state.downloadSurveyEntryOrigin)
    assertTrue(state.isDownloadSurveyAccessedFromSurveyList)

    // Back action when accessed from Survey list returns directly to the Survey list without
    // prompting
    state.navigateBackFromDownloadSurvey()
    assertFalse(state.isDownloadSurveySignOutPromptOpen)
    assertTrue(state.isSignedIn)
    assertEquals(AppScreen.MAIN_SURVEY, state.currentScreen)
    assertEquals(MainDrawerSubView.SWITCH_SURVEYS, state.activeDrawerSubView)
  }

  @Test
  fun userFacingTerminology_replacesEntityWithGenericOrDomainFocusedLanguage() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())

    assertEquals("Map features", ListFilterTab.ENTITIES.label)
    assertEquals("Places", ListFilterTab.PLACES.label)
    assertEquals("Survey Layer", LayerSourceType.ENTITY_DATASET.badgeLabel)

    AppScreen.entries.forEach { screen ->
      assertFalse(screen.title.contains("entity", ignoreCase = true))
      assertFalse(screen.subtitle.contains("entity", ignoreCase = true))
      assertFalse(screen.subtitle.contains("entities", ignoreCase = true))
    }

    state.mapLayers.forEach { layer ->
      assertFalse(layer.label.contains("entity", ignoreCase = true))
      assertFalse(layer.sourceDescription.contains("entity", ignoreCase = true))
      assertFalse(layer.formatCountLabel(1).contains("entity", ignoreCase = true))
      assertFalse(layer.formatCountLabel(2).contains("entities", ignoreCase = true))
    }

    state.entities.forEach { entity ->
      assertFalse(entity.singularTypeLabel.contains("entity", ignoreCase = true))
      assertFalse(entity.datasetName.contains("entity", ignoreCase = true))
      state.shareEntityPdf(entity.id)
      val sheet = state.activeSharedPdfSheet!!
      assertFalse(sheet.title.contains("entity", ignoreCase = true))
      assertFalse(sheet.targetKindLabel.contains("entity", ignoreCase = true))
      assertFalse(sheet.pdfFileName.contains("entity", ignoreCase = true))
      state.closeSharePdfSheet()
    }

    state.allSubmissions.forEach { sub ->
      assertFalse(sub.targetTypeLabel.contains("entity", ignoreCase = true))
    }
  }

  @Test
  fun submissionListsAndLayersSheet_groupSubmissionsByFormAndUseFormTitleInsteadOfWordForm() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())

    // 1. Main List View search no longer includes form submissions
    assertTrue(state.groupedFilteredListSubmissions.isEmpty())

    // 2. Entity bottom sheet submission list groups submissions by form and uses form title
    val shade201 = state.entities.first { it.id == "entity-shade-201" }
    val shadeGroups = state.groupedSubmissionsForEntity(shade201)
    assertEquals(2, shadeGroups.size)
    assertEquals(
      listOf("Seasonal Shade Tree & Canopy Audit", "GLAD Canopy Disturbance Alert Verification"),
      shadeGroups.map { it.formTitle },
    )
    assertEquals(2, shadeGroups[0].submissions.size)
    assertEquals(1, shadeGroups[1].submissions.size)

    // 3. Layers sheet displays map layers (entityDatasetLayers); submissions are not shown as
    // layers
    assertTrue(state.hasGeospatialEntities)
    val siteLayers = state.entityDatasetLayers
    assertEquals(3, siteLayers.size)
    assertEquals(
      listOf(
        "Smallholder Coffee Parcels",
        "Shade Tree Monitoring Plots",
        "Cooperative Washing Stations",
      ),
      siteLayers.map { it.label },
    )
    assertEquals(emptyList(), state.visibleSubmissionGeometries)

    // When a survey has no geospatial entities, map layers section in the Layers sheet is hidden
    val noEntityState =
      MobileAppState(dataHolder = sampleDataHolder()).apply {
        openSurvey("survey-single-point-land-use")
        entities = emptyList()
      }
    assertTrue(noEntityState.entities.isEmpty())
    assertFalse(noEntityState.hasGeospatialEntities)
    assertTrue(noEntityState.entityDatasetLayers.isEmpty())

    // 4. Shared submission PDF sheet uses the form title instead of 'Form Submission PDF'
    state.shareSubmissionPdf("sub-nyr-104-baseline")
    val pdfSheet = assertNotNull(state.activeSharedPdfSheet)
    assertTrue(pdfSheet.title.contains("EUDR Parcel Baseline Registration"))
    assertFalse(pdfSheet.title.contains("form", ignoreCase = true))
    assertFalse(pdfSheet.targetKindLabel.contains("form", ignoreCase = true))
    state.closeSharePdfSheet()
  }

  @Test
  fun bottomCenteredFab_opensAvailableForms_andPresentsMapOrListSelectorAtEntityRefStep() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    state.selectEntity(null)
    assertEquals(null, state.selectedEntityId)
    assertFalse(state.isAvailableFormsSheetOpen)
    assertFalse(state.isDataCollectionFormOpen)

    // 1. Tapping the bottom-centered FAB opens the list of available forms
    state.openAvailableFormsSheet()
    assertTrue(state.isAvailableFormsSheetOpen)
    assertEquals(6, state.forms.size)
    assertEquals(5, state.forms.count { it.requiresEntity })
    assertEquals(1, state.forms.count { !it.requiresEntity })

    // 2. Launching a form from the FAB (with no entity pre-selected from the map)
    val initialNyr108 = state.entities.first { it.id == "entity-nyr-108" }
    val initialNyr108Subs = initialNyr108.submissions.size
    assertEquals(1, initialNyr108Subs)
    assertEquals("◐", initialNyr108.markerSymbol)
    state.launchFormFromFab("form-eudr-baseline")

    assertFalse(state.isAvailableFormsSheetOpen)
    assertTrue(state.isDataCollectionFormOpen)
    assertTrue(state.wasFormLaunchedWithoutEntity)
    assertEquals(null, state.activeDataCollectionEntityId)
    assertEquals("form-eudr-baseline", state.activeDataCollectionFormId)

    // 3. Because no entity was selected from the map, step 0 is the required entityref step
    // presenting the Map or List selector
    assertTrue(state.isCurrentFormStepEntityRef)
    val controller = assertNotNull(state.activeFormWizardController)
    assertEquals(0, controller.currentStepIndex)

    // Advancing before selecting an entity is blocked by required entityref validation
    val blockedStepResult = controller.nextStep()
    assertFalse(blockedStepResult)
    assertEquals(0, controller.currentStepIndex)

    // Switch between Map and List selector modes and filter candidates in List mode
    assertEquals(MainSurveyViewMode.MAP, state.entityRefSelectorViewMode)
    state.updateEntityRefSelectorViewMode(MainSurveyViewMode.LIST)
    assertEquals(MainSurveyViewMode.LIST, state.entityRefSelectorViewMode)
    assertEquals(3, state.filteredEntityRefCandidates.size)
    assertEquals(
      listOf("entity-nyr-104", "entity-nyr-108", "entity-nyr-112"),
      state.eligibleEntitiesForActiveForm.map { it.id },
    )

    state.updateEntityRefSearchQuery("108")
    assertEquals(1, state.filteredEntityRefCandidates.size)
    assertEquals("entity-nyr-108", state.filteredEntityRefCandidates.first().id)
    state.clearEntityRefSearchQuery()

    // 4. Select the entity ("entity-nyr-108") at the entityref step and advance to data questions
    state.selectEntityRefForActiveForm("entity-nyr-108")
    assertEquals("entity-nyr-108", state.activeDataCollectionEntityId)
    assertEquals("entity-nyr-108", state.selectedEntityId)

    val advanceResult = controller.nextStep()
    assertTrue(advanceResult)
    assertEquals(1, controller.currentStepIndex)
    assertFalse(state.isCurrentFormStepEntityRef)

    // 5. Complete form submission and verify it is attached to the selected entity
    // ("entity-nyr-108")
    // and advances its marker-symbol from "◐" (In progress) to "✓" (Completed)
    state.completeActiveFormSubmission(controller.formState.recordInstance)
    assertFalse(state.isDataCollectionFormOpen)
    assertFalse(state.wasFormLaunchedWithoutEntity)
    val updatedNyr108 = state.entities.first { it.id == "entity-nyr-108" }
    assertEquals(initialNyr108Subs + 1, updatedNyr108.submissions.size)
    assertEquals("entity-nyr-108", updatedNyr108.submissions.first().entityId)
    assertEquals("✓", updatedNyr108.markerSymbol)
    assertEquals("Completed", updatedNyr108.workflowStatus)
    assertEquals(SyncStatus.UPLOADING, updatedNyr108.submissions.first().syncStatus)
    assertEquals(SyncStatus.UPLOADING, updatedNyr108.syncStatus)
  }

  @Test
  fun selectEntityRefForActiveForm_framesSelectedMapFeatureOnMap() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    state.selectEntity(null)
    state.launchFormFromFab("form-eudr-baseline")
    assertTrue(state.isCurrentFormStepEntityRef)
    val form = assertNotNull(state.activeDataCollectionForm)
    val epochBefore = state.entityRefFramingEpoch

    state.selectEntityRefForActiveForm("entity-nyr-108")

    // The camera target moves onto the picked map feature and stops following GPS.
    val entity = state.entities.first { it.id == "entity-nyr-108" }
    assertTrue(entity.hasGeometry)
    assertEquals(epochBefore + 1, state.entityRefFramingEpoch)
    assertFalse(state.isCameraFollowingUser)
    val (lng, lat) = state.resolveEntityLngLat(entity)
    val camera = state.desiredMapCamera()
    assertEquals(lat, camera.center.latitude, 1e-4)
    assertEquals(lng, camera.center.longitude, 1e-4)

    // Picking the same map feature again frames it again.
    state.selectEntityRefForActiveForm("entity-nyr-108")
    assertEquals(epochBefore + 2, state.entityRefFramingEpoch)

    // A map feature from another data table is rejected and leaves the camera where it was.
    val other = state.entities.firstOrNull { it.datasetId != form.targetDatasetId }
    if (other != null) {
      state.selectEntityRefForActiveForm(other.id)
      assertEquals(epochBefore + 2, state.entityRefFramingEpoch)
      assertEquals("entity-nyr-108", state.activeDataCollectionEntityId)
      assertEquals(camera.center, state.desiredMapCamera().center)
    }
  }

  @Test
  fun standaloneSubmissionsWithoutAttachedEntities_supportMapSelectionListInspectionWayfindingAndFabSubmission() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())

    // 1. Verify default sample standalone submissions exist without attached entities
    assertEquals(2, state.standaloneSubmissions.size)
    val pestSub = state.standaloneSubmissions.first { it.id == "sub-standalone-pest-01" }
    val erosionSub = state.standaloneSubmissions.first { it.id == "sub-standalone-erosion-02" }
    assertFalse(pestSub.hasAttachedEntity)
    assertFalse(erosionSub.hasAttachedEntity)
    assertEquals("", pestSub.entityId)
    assertEquals("", pestSub.entityLabel)
    assertEquals("Standalone Field Log", pestSub.targetTypeLabel)
    assertTrue(state.allSubmissions.any { it.id == "sub-standalone-pest-01" })
    assertTrue(state.allSubmissions.any { it.id == "sub-standalone-erosion-02" })

    // 2. Selecting a standalone submission opens its details directly with selectedEntity == null
    state.selectSubmissionDetail("sub-standalone-pest-01")
    assertEquals(null, state.selectedEntityId)
    assertEquals(null, state.selectedEntity)
    assertEquals("sub-standalone-pest-01", state.selectedSubmission?.id)

    // 3. Clicking the standalone submission's dotted polygon on the map selects it with no parent
    // entity
    state.selectEntity("entity-nyr-104")
    assertEquals("entity-nyr-104", state.selectedEntityId)
    state.selectSubmissionGeometry("geom-sub-standalone-pest-01")
    assertEquals(null, state.selectedEntityId)
    assertEquals("sub-standalone-pest-01", state.selectedSubmission?.id)

    // 4. Straight-line navigation works for both polygon-backed and point-backed standalone
    // submissions
    state.startNavigationToSubmission("sub-standalone-erosion-02")
    val nav = assertNotNull(state.activeNavigation)
    assertEquals(NavigationTargetKind.SUBMISSION, nav.targetKind)
    assertEquals("sub-standalone-erosion-02", nav.targetId)
    assertEquals(null, state.selectedEntityId)
    assertEquals("sub-standalone-erosion-02", state.selectedSubmissionId)
    state.stopNavigation()

    // 5. Launching the standalone form (`!form.requiresEntity`) from the FAB skips entityref step
    // and records a new standalone submission without an attached entity
    val standaloneForm = state.forms.first { it.id == "form-pest-disease-sighting" }
    assertFalse(standaloneForm.requiresEntity)
    state.launchFormFromFab("form-pest-disease-sighting")
    assertTrue(state.isDataCollectionFormOpen)
    assertEquals(null, state.activeDataCollectionEntityId)
    assertFalse(state.isCurrentFormStepEntityRef)

    state.completeActiveFormSubmission()
    assertFalse(state.isDataCollectionFormOpen)
    assertEquals(3, state.standaloneSubmissions.size)
    val newlyCreated = state.standaloneSubmissions.first()
    assertFalse(newlyCreated.hasAttachedEntity)
    assertEquals("", newlyCreated.entityId)
    assertEquals(null, state.selectedEntityId)
    assertEquals(newlyCreated.id, state.selectedSubmissionId)
  }

  @Test
  fun outboxAndUploadedDrawerViews_listMutationsInReverseChronologicalOrderWithStateOperationTimeAndStartedOrCompletedTime() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())

    // 1. Open Outbox from Navigation Drawer
    state.updateDrawerOpen(true)
    state.drawerOpenOutbox()
    assertFalse(state.isDrawerOpen)
    assertEquals(MainDrawerSubView.OUTBOX, state.activeDrawerSubView)
    assertEquals(4, state.outboxMutationCount)

    // Verify Outbox mutations are in strict reverse chronological order (newest operationTimestamp
    // first)
    val outbox = state.outboxMutations
    assertEquals(
      listOf("mut-outbox-04", "mut-outbox-03", "mut-outbox-02", "mut-outbox-01"),
      outbox.map { it.id },
    )
    for (i in 0 until outbox.size - 1) {
      assertTrue(
        outbox[i].operationTimestamp >= outbox[i + 1].operationTimestamp,
        "Expected reverse chronological order in Outbox: ${outbox[i].operationTimestamp} >= ${outbox[i + 1].operationTimestamp}",
      )
    }
    // Verify each Outbox mutation has state, operationTimestamp, and startedTimestamp
    outbox.forEach { item ->
      assertTrue(item.isOutbox)
      assertFalse(item.isUploaded)
      assertTrue(item.operationTimestamp.isNotBlank())
      assertTrue(item.startedTimestamp?.isNotBlank() == true)
      assertEquals(null, item.completedTimestamp)
      assertTrue(item.startedOrCompletedSummary.contains("Started:"))
    }

    // 2. Open Uploaded from Navigation Drawer
    state.drawerOpenUploaded()
    assertEquals(MainDrawerSubView.UPLOADED, state.activeDrawerSubView)
    assertEquals(5, state.uploadedMutationCount)

    // Verify Uploaded mutations are in strict reverse chronological order (newest
    // operationTimestamp first)
    val uploaded = state.uploadedMutations
    assertEquals(
      listOf(
        "mut-uploaded-05",
        "mut-uploaded-04",
        "mut-uploaded-03",
        "mut-uploaded-02",
        "mut-uploaded-01",
      ),
      uploaded.map { it.id },
    )
    for (i in 0 until uploaded.size - 1) {
      assertTrue(
        uploaded[i].operationTimestamp >= uploaded[i + 1].operationTimestamp,
        "Expected reverse chronological order in Uploaded: ${uploaded[i].operationTimestamp} >= ${uploaded[i + 1].operationTimestamp}",
      )
    }
    // Verify each Uploaded mutation has state == UPLOADED, operationTimestamp, startedTimestamp,
    // and completedTimestamp
    uploaded.forEach { item ->
      assertTrue(item.isUploaded)
      assertFalse(item.isOutbox)
      assertEquals(MutationSyncState.UPLOADED, item.state)
      assertTrue(item.operationTimestamp.isNotBlank())
      assertTrue(item.startedTimestamp?.isNotBlank() == true)
      assertTrue(item.completedTimestamp?.isNotBlank() == true)
      assertTrue(item.startedOrCompletedSummary.contains("Completed:"))
    }

    // 3. Syncing a single Outbox mutation moves it to Uploaded with completedTimestamp populated
    state.syncMutationNow("mut-outbox-04")
    assertEquals(3, state.outboxMutationCount)
    assertEquals(6, state.uploadedMutationCount)
    assertEquals("mut-outbox-04", state.uploadedMutations.first().id)
    assertEquals(MutationSyncState.UPLOADED, state.uploadedMutations.first().state)
    assertNotNull(state.uploadedMutations.first().completedTimestamp)

    // 4. Syncing all remaining Outbox mutations empties Outbox and moves all to Uploaded
    state.syncAllOutboxMutations()
    assertEquals(0, state.outboxMutationCount)
    assertEquals(9, state.uploadedMutationCount)
  }

  @Test
  fun unifiedBottomSheet_supportsListExpansionEntityInspectionAndReturnToList() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())

    // Starts in collapsed Map peek mode with no entity selected
    assertEquals(MainSurveyViewMode.MAP, state.mainViewMode)
    assertFalse(state.isEntityBottomSheetExpanded)
    assertEquals(null, state.selectedEntityId)

    // Switching to LIST mode expands the unified bottom sheet to show the searchable list
    state.setMainSurveyViewMode(MainSurveyViewMode.LIST)
    assertEquals(MainSurveyViewMode.LIST, state.mainViewMode)
    assertTrue(state.isEntityBottomSheetExpanded)
    assertEquals(null, state.selectedEntityId)

    // Selecting an entity from the bottom sheet list replaces the list with the entity's details
    // and lowers the sheet to its peek height so the map can frame the entity above it
    state.selectEntityFromList("entity-nyr-104")
    assertEquals("entity-nyr-104", state.selectedEntityId)
    assertFalse(state.isEntityBottomSheetExpanded)
    assertEquals(EntityDetailsPane.PROPERTIES, state.entityDetailsPane)

    // Returning to the bottom sheet list clears the selected entity while keeping the sheet
    // expanded
    state.returnToBottomSheetList()
    assertEquals(null, state.selectedEntityId)
    assertEquals(null, state.selectedSubmissionId)
    assertTrue(state.isEntityBottomSheetExpanded)
    assertEquals(MainSurveyViewMode.LIST, state.mainViewMode)
  }

  @Test
  fun unifiedUploadsDrawerView_supportsStatusFilterChipsAndUserFriendlyLabels() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())

    // Verify user-friendly operation labels (Form submitted / modified / deleted, Map feature
    // modified)
    assertEquals("Form submitted", MutationOperationKind.CREATE_SUBMISSION.label)
    assertEquals("Form modified", MutationOperationKind.UPDATE_SUBMISSION.label)
    assertEquals("Form deleted", MutationOperationKind.DELETE_SUBMISSION.label)
    assertEquals("Map feature created", MutationOperationKind.CREATE_ENTITY.label)
    assertEquals("Map feature modified", MutationOperationKind.UPDATE_ENTITY.label)
    assertEquals("Map feature deleted", MutationOperationKind.DELETE_ENTITY.label)

    // Verify the 4 status filter chips: Pending, In progress, Uploaded, Failed
    assertEquals(
      listOf("Pending", "In progress", "Uploaded", "Failed"),
      UploadStatusFilter.entries.map { it.label },
    )

    // Open unified Uploads screen from Navigation Drawer
    state.updateDrawerOpen(true)
    state.drawerOpenUploads()
    assertFalse(state.isDrawerOpen)
    assertEquals(MainDrawerSubView.UPLOADS, state.activeDrawerSubView)
    assertEquals(null, state.selectedUploadStatusFilter)
    assertEquals(9, state.filteredUploadMutations.size)

    // Filter counts across the 4 chips
    assertEquals(1, state.uploadCountForFilter(UploadStatusFilter.PENDING))
    assertEquals(2, state.uploadCountForFilter(UploadStatusFilter.IN_PROGRESS))
    assertEquals(5, state.uploadCountForFilter(UploadStatusFilter.UPLOADED))
    assertEquals(1, state.uploadCountForFilter(UploadStatusFilter.FAILED))

    // Toggle Pending chip
    state.toggleUploadStatusFilter(UploadStatusFilter.PENDING)
    assertEquals(UploadStatusFilter.PENDING, state.selectedUploadStatusFilter)
    assertEquals(1, state.filteredUploadMutations.size)
    assertEquals("mut-outbox-02", state.filteredUploadMutations.first().id)

    // Toggle In progress chip
    state.toggleUploadStatusFilter(UploadStatusFilter.IN_PROGRESS)
    assertEquals(UploadStatusFilter.IN_PROGRESS, state.selectedUploadStatusFilter)
    assertEquals(2, state.filteredUploadMutations.size)

    // Toggle Uploaded chip
    state.toggleUploadStatusFilter(UploadStatusFilter.UPLOADED)
    assertEquals(UploadStatusFilter.UPLOADED, state.selectedUploadStatusFilter)
    assertEquals(5, state.filteredUploadMutations.size)

    // Toggle Failed chip
    state.toggleUploadStatusFilter(UploadStatusFilter.FAILED)
    assertEquals(UploadStatusFilter.FAILED, state.selectedUploadStatusFilter)
    assertEquals(1, state.filteredUploadMutations.size)
    assertEquals("Form deleted", state.filteredUploadMutations.first().operationKind.label)

    // Tapping the active filter chip again clears the filter and shows all uploads
    state.toggleUploadStatusFilter(UploadStatusFilter.FAILED)
    assertEquals(null, state.selectedUploadStatusFilter)
    assertEquals(9, state.filteredUploadMutations.size)
  }

  @Test
  fun mapZoomOut_clustersMapFeaturesByMarkerSymbolIncludingNoSymbolGroupInClusterBalloon() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())

    // 1. At default zoom (mapZoomDelta == 0f), clustering is inactive; only the 5 entities are
    // included (form submission geometries are not displayed on the map or in cluster chips)
    assertEquals(0f, state.mapZoomDelta)
    assertFalse(state.isMapClusteringActive)
    assertEquals(emptyList(), state.mapFeatureClusters)
    assertEquals(5, state.visibleMapClusterFeatures.size)
    assertEquals(5, state.visibleMapClusterFeatures.count { it.hasMarkerSymbol })
    assertEquals(0, state.visibleMapClusterFeatures.count { !it.hasMarkerSymbol })

    // 2. Zooming out up to 2.25 levels (about 13z) keeps features unclustered; zooming out once
    // more (-3.0f) activates clustering and groups nearby features spatially
    repeat(3) { state.zoomOutMap() }
    assertEquals(-2.25f, state.mapZoomDelta)
    assertFalse(state.isMapClusteringActive)
    assertEquals(emptyList(), state.mapFeatureClusters)
    state.zoomOutMap()
    assertEquals(-3.0f, state.mapZoomDelta)
    assertTrue(state.isMapClusteringActive)
    val initialClusters = state.mapFeatureClusters
    assertTrue(initialClusters.size > 1)
    assertEquals(5, initialClusters.sumOf { it.totalCount })

    // 3. Zoom out further (-3.75f) so local clusters cover a broader area and merge into a single
    // survey-wide cluster balloon
    state.zoomOutMap()
    assertEquals(-3.75f, state.mapZoomDelta)
    assertEquals(1, state.mapFeatureClusters.size)
    val allCluster = state.mapFeatureClusters.first()
    assertEquals(5, allCluster.totalCount)
    assertTrue(
      allCluster.diameterNormalized > initialClusters.maxOf { it.diameterNormalized },
      "Survey-wide cluster at -3.75f should cover a broader spatial area than local clusters at -3.0f",
    )

    // Verify grouping by marker symbol for entity geometries
    assertEquals(3, allCluster.symbolGroups.size)
    assertEquals(listOf("✓", "◐", "○"), allCluster.symbolGroups.map { it.markerSymbol })
    assertEquals(mapOf("✓" to 2, "◐" to 2, "○" to 1), allCluster.countsByMarkerSymbol)
    assertEquals(2, allCluster.countForSymbol("✓"))
    assertEquals(2, allCluster.countForSymbol("◐"))
    assertEquals(1, allCluster.countForSymbol("○"))
    assertEquals(0, allCluster.noMarkerSymbolCount)
    assertEquals("✓ 2 • ◐ 2 • ○ 1", allCluster.balloonSummaryLabel)

    // 4. Verify an entity without a "marker-symbol" property groups into the no-marker-symbol
    // ("") group
    val rawUnmarkedEntity =
      state.entities
        .first()
        .copy(
          id = "entity-unmarked-999",
          label = "Unmarked Plot 999",
          properties = mapOf("marker-color" to "#1565C0"),
        )
    assertFalse(rawUnmarkedEntity.hasMarkerSymbol)
    assertEquals("", rawUnmarkedEntity.rawMarkerSymbol)
    val customGrouped =
      groupClusterFeaturesByMarkerSymbol(
        state.visibleMapClusterFeatures +
          MapClusterFeatureItem(
            id = rawUnmarkedEntity.id,
            kind = MapFeatureKind.ENTITY,
            label = rawUnmarkedEntity.label,
            normalizedX = rawUnmarkedEntity.normalizedX,
            normalizedY = rawUnmarkedEntity.normalizedY,
            markerSymbol = rawUnmarkedEntity.rawMarkerSymbol,
            colorHex = rawUnmarkedEntity.colorHex,
            colorCss = rawUnmarkedEntity.markerColorCss,
          )
      )
    assertEquals(1, customGrouped.first { it.isNoSymbolGroup }.count)

    // 5. Verify feature count + feature states with 0 form submission geometries
    assertEquals(5, allCluster.siteCount)
    assertEquals("5 map features", state.formatClusterSitesCountLabel(allCluster.siteCount))
    assertEquals(0, allCluster.submissionGeometryCount)
    assertEquals(listOf("✓", "◐", "○"), allCluster.siteSymbolGroups.map { it.markerSymbol })
    assertEquals("✓ 2 • ◐ 2 • ○ 1", allCluster.siteStatesSummaryLabel)

    // 6. Selecting the cluster balloon and building the survey map
    state.selectCluster(allCluster.id)
    assertEquals(allCluster.id, state.selectedClusterId)
    assertEquals(allCluster.id, state.selectedCluster?.id)
    assertEquals("5 map features", state.activeSurveyNotice)

    val clusteredMap = SurveyMapContent.main(state, showNavigation = true)
    val balloon =
      clusteredMap.markers[SurveyMapIds.cluster(allCluster.id)] as SurveyMarker.ClusterBalloon
    assertTrue(balloon.selected)
    assertEquals("5 map features", balloon.header)
    assertEquals(5, balloon.chips.sumOf { it.count })
    // When clustering is active, individual entities are omitted from the map
    assertTrue(clusteredMap.entityFeatures().isEmpty())
    assertTrue(clusteredMap.markers.keys.none { SurveyMapIds.entityIdOf(it) != null })

    // 7. Zooming into the cluster steps zoom back in, and resetting zoom exits clustering and
    // restores individual features
    state.zoomIntoCluster(allCluster.id)
    assertEquals(-3.0f, state.mapZoomDelta)
    assertTrue(state.isMapClusteringActive)
    state.resetMapZoom()
    assertEquals(0f, state.mapZoomDelta)
    assertFalse(state.isMapClusteringActive)
    assertEquals(null, state.selectedClusterId)
    val unclusteredMap = SurveyMapContent.main(state, showNavigation = true)
    assertTrue(unclusteredMap.markers.keys.none { SurveyMapIds.clusterIdOf(it) != null })
    assertTrue(unclusteredMap.entityFeatures().isNotEmpty())
  }

  @Test
  fun resolveEntityLngLatBounds_coversLinesAndPolygonsAndCollapsesForPoints() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    val kinds = state.entities.groupBy { it.geometryKind }

    for (kind in listOf(EntityGeometryKind.POLYGON, EntityGeometryKind.LINE)) {
      val entity = kinds.getValue(kind).first()
      val bounds = state.resolveEntityLngLatBounds(entity)
      val (lng, lat) = state.resolveEntityLngLat(entity)
      assertTrue(bounds.west < lng && lng < bounds.east, "$kind bounds span its center longitude")
      assertTrue(bounds.south < lat && lat < bounds.north, "$kind bounds span its center latitude")
    }

    kinds[EntityGeometryKind.POINT]?.firstOrNull()?.let { point ->
      val bounds = state.resolveEntityLngLatBounds(point)
      val (lng, lat) = state.resolveEntityLngLat(point)
      assertEquals(LngLatBounds(west = lng, south = lat, east = lng, north = lat), bounds)
    }
  }

  @Test
  fun syncMapZoomDelta_followsFittedCameraAndExitsClustering() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    repeat(4) { state.zoomOutMap() }
    assertTrue(state.isMapClusteringActive)
    state.selectCluster(state.mapFeatureClusters.first().id)

    // Fitting a selected feature zooms the camera in; clustering follows the camera.
    state.syncMapZoomDelta(1.2f)
    assertEquals(1.2f, state.mapZoomDelta)
    assertFalse(state.isMapClusteringActive)
    assertEquals(null, state.selectedClusterId)

    // NaN (no camera change, e.g. re-centering only) leaves the zoom untouched.
    state.syncMapZoomDelta(Float.NaN)
    assertEquals(1.2f, state.mapZoomDelta)
  }

  @Test
  fun completeActiveFormSubmission_createsNewEntityFromSaveTo() {
    val state = MobileAppState(dataHolder = sampleDataHolder())
    state.openSurvey("survey-single-point-land-use")
    val initialEntityCount = state.entities.size
    assertEquals(3, initialEntityCount)

    // Launch the single-point land-use form from FAB (which does not require picking an entity)
    state.launchFormFromFab("form-single-point-land-use")
    state.acceptCollectorConsent()
    assertTrue(state.isDataCollectionFormOpen)
    val ctrl = assertNotNull(state.activeFormWizardController)

    // Fill in required questions
    ctrl.updateGeoPoint("/data/sample_point", latitude = -0.4190, longitude = 36.9500)
    ctrl.updateString("/data/land_use", "primary_forest")
    ctrl.updateString("/data/observer_note", "Newly surveyed forest patch")

    // Complete active submission
    state.completeActiveFormSubmission()

    // Assert that a new entity was created and placed on the map
    assertEquals(initialEntityCount + 1, state.entities.size)
    val createdEntity = state.entities.first()
    assertEquals("land_use_observations", createdEntity.datasetId)
    assertEquals("layer-land-use-observations", createdEntity.layerId)
    assertEquals("primary_forest", createdEntity.properties["land_use"])
    assertEquals("Newly surveyed forest patch", createdEntity.properties["observer_note"])
    // The form's save_to sets no workflow status, so none is defaulted in.
    assertFalse(createdEntity.hasWorkflowStatus)

    // Assert submission is attached to the new entity
    assertEquals(1, createdEntity.submissions.size)
    assertEquals(createdEntity.id, createdEntity.submissions.first().entityId)
    assertEquals(createdEntity.id, state.selectedEntityId)

    // Assert a CREATE_ENTITY mutation was recorded
    assertTrue(
      state.outboxMutations.any {
        it.operationKind == MutationOperationKind.CREATE_ENTITY && it.entityId == createdEntity.id
      }
    )
  }

  @Test
  fun deviceStorageInfo_calculatesStorageBreakdownAccurately() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    val storage = state.deviceStorageInfo

    // Verify storage figures exist and total is 64 GB
    assertEquals(64L * 1024L * 1024L * 1024L, storage.totalBytes)
    assertEquals("64.0 GB", storage.totalStorageLabel)

    // Verify downloaded imagery, data, other used, and free storage are positive
    assertTrue(storage.downloadedImageryBytes > 0L)
    assertTrue(storage.dataBytes > 0L)
    assertTrue(storage.otherUsedBytes > 0L)
    assertTrue(storage.freeBytes > 0L)

    // Verify fractions sum to <= 1.0f and are in valid range [0, 1]
    assertTrue(storage.imageryFraction in 0.0f..1.0f)
    assertTrue(storage.dataFraction in 0.0f..1.0f)
    assertTrue(storage.otherUsedFraction in 0.0f..1.0f)
    assertTrue(storage.freeFraction in 0.0f..1.0f)
    assertTrue(storage.imageryFraction + storage.dataFraction + storage.otherUsedFraction <= 1.0f)

    // Labels should be non-empty and formatted
    assertTrue(
      storage.downloadedImageryStorageLabel.contains("GB") ||
        storage.downloadedImageryStorageLabel.contains("MB")
    )
    assertTrue(storage.dataStorageLabel.contains("MB") || storage.dataStorageLabel.contains("GB"))
    assertTrue(storage.freeStorageLabel.contains("GB"))
    assertTrue(storage.totalStorageLabel.contains("GB"))

    // Toggling tile package download updates imagery size dynamically
    val initialImageryBytes = storage.downloadedImageryBytes
    val pkg = state.offlineTilePackages.first { !it.isDownloaded }
    state.toggleOfflineTilePackage(pkg.id)
    val updatedStorage = state.deviceStorageInfo
    assertTrue(updatedStorage.downloadedImageryBytes > initialImageryBytes)
  }

  @Test
  fun promptRemoveDownloadedSurvey_requiresConfirmationBeforeRemoval() {
    val state =
      MobileAppState(initialScreen = AppScreen.DOWNLOAD_SURVEY, dataHolder = sampleDataHolder())
    val targetId = "survey-kenya-coffee"
    assertTrue(state.surveys.first { it.id == targetId }.isDownloaded)

    // User clicks Downloaded button -> triggers prompt confirmation
    state.promptRemoveDownloadedSurvey(targetId)
    assertEquals(targetId, state.pendingRemovalSurveyId)
    // Survey is NOT yet removed
    assertTrue(state.surveys.first { it.id == targetId }.isDownloaded)

    // User cancels/dismisses dialog
    state.dismissRemoveDownloadedSurvey()
    assertEquals(null, state.pendingRemovalSurveyId)
    assertTrue(state.surveys.first { it.id == targetId }.isDownloaded)

    // User prompts again and confirms removal
    state.promptRemoveDownloadedSurvey(targetId)
    assertEquals(targetId, state.pendingRemovalSurveyId)
    state.confirmRemoveDownloadedSurvey()
    assertEquals(null, state.pendingRemovalSurveyId)
    assertFalse(state.surveys.first { it.id == targetId }.isDownloaded)
    assertTrue(state.activeSurveyNotice?.contains("Removed offline copy") == true)
  }

  @Test
  fun promptRemoveDownloadedSurvey_downloadsImmediatelyIfNotAlreadyDownloaded() {
    val state =
      MobileAppState(initialScreen = AppScreen.DOWNLOAD_SURVEY, dataHolder = sampleDataHolder())
    val targetId = "survey-serengeti-corridor"
    assertFalse(state.surveys.first { it.id == targetId }.isDownloaded)

    // Prompting on a non-downloaded survey downloads directly without warning
    state.promptRemoveDownloadedSurvey(targetId)
    assertEquals(null, state.pendingRemovalSurveyId)
    assertTrue(state.surveys.first { it.id == targetId }.isDownloaded)
  }

  @Test
  fun promptRemoveOfflineTilePackage_requiresConfirmationBeforeRemoval() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    val downloadedPkg = state.offlineTilePackages.first { it.isDownloaded }

    // User clicks Downloaded button -> triggers prompt confirmation
    state.promptRemoveOfflineTilePackage(downloadedPkg.id)
    assertEquals(downloadedPkg.id, state.pendingRemovalTilePackageId)
    // Package is NOT yet removed
    assertTrue(state.offlineTilePackages.first { it.id == downloadedPkg.id }.isDownloaded)

    // User cancels/dismisses
    state.dismissRemoveOfflineTilePackage()
    assertEquals(null, state.pendingRemovalTilePackageId)
    assertTrue(state.offlineTilePackages.first { it.id == downloadedPkg.id }.isDownloaded)

    // User prompts again and confirms
    state.promptRemoveOfflineTilePackage(downloadedPkg.id)
    state.confirmRemoveOfflineTilePackage()
    assertEquals(null, state.pendingRemovalTilePackageId)
    assertFalse(state.offlineTilePackages.first { it.id == downloadedPkg.id }.isDownloaded)
  }

  @Test
  fun promptRemoveOfflineTilePackage_downloadsImmediatelyIfNotAlreadyDownloaded() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    val nonDownloadedPkg = state.offlineTilePackages.first { !it.isDownloaded }

    state.promptRemoveOfflineTilePackage(nonDownloadedPkg.id)
    assertEquals(null, state.pendingRemovalTilePackageId)
    assertTrue(state.offlineTilePackages.first { it.id == nonDownloadedPkg.id }.isDownloaded)
  }

  @Test
  fun entityDetails_openOnPropertiesWithSubmissionsOneClickAway() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    val entity = state.entities.first { it.submissions.isNotEmpty() }

    state.selectEntity(entity.id)
    assertEquals(EntityDetailsPane.PROPERTIES, state.entityDetailsPane)

    state.showEntitySubmissions()
    assertEquals(EntityDetailsPane.SUBMISSIONS, state.entityDetailsPane)

    // Opening a submission keeps the submissions pane behind it, so closing it returns there.
    val submission = entity.submissions.first()
    state.selectSubmissionDetail(submission.id)
    assertEquals(submission.id, state.selectedSubmissionId)
    state.selectSubmissionDetail(null)
    assertEquals(EntityDetailsPane.SUBMISSIONS, state.entityDetailsPane)

    state.showEntityProperties()
    assertEquals(EntityDetailsPane.PROPERTIES, state.entityDetailsPane)

    // Selecting another entity always opens on its properties.
    state.showEntitySubmissions()
    state.selectEntity(state.entities.first { it.id != entity.id }.id)
    assertEquals(EntityDetailsPane.PROPERTIES, state.entityDetailsPane)
  }

  @Test
  fun openingSubmissionOfAnotherEntity_framesThatEntity() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    val entities = state.entities.filter { it.submissions.isNotEmpty() }
    state.selectEntity(entities[0].id)
    val epoch = state.entitySelectionEpoch

    state.selectSubmissionDetail(entities[1].submissions.first().id)

    assertEquals(entities[1].id, state.selectedEntityId)
    assertEquals(epoch + 1, state.entitySelectionEpoch)
  }

  @Test
  fun relatedEntityForPropertyValue_resolvesReferencesToOtherRecords() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    val parcel = state.entities.first { it.properties["Washing Station"] != null }
    val station = state.entities.first { it.id == parcel.properties["Washing Station"] }

    assertEquals(station, state.relatedEntityForPropertyValue(parcel, station.id))
    assertEquals(station, state.relatedEntityForPropertyValue(parcel, station.geoId))
    assertEquals(null, state.relatedEntityForPropertyValue(parcel, "Othaya Farmers Co-op"))
    // A record never links to itself.
    assertEquals(null, state.relatedEntityForPropertyValue(parcel, parcel.id))
  }

  @Test
  fun openUploadsForEntity_filtersUploadsToThatEntityUntilCleared() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    val entityId = state.mutations.first().entityId
    val expected = state.mutations.count { it.entityId == entityId }

    state.openUploadsForEntity(entityId)
    assertEquals(MainDrawerSubView.UPLOADS, state.activeDrawerSubView)
    assertEquals(entityId, state.uploadsEntityFilter?.id)
    assertEquals(expected, state.filteredUploadMutations.size)
    assertEquals(expected, state.uploadCountForEntity(entityId))
    assertTrue(state.filteredUploadMutations.all { it.entityId == entityId })

    state.clearUploadsEntityFilter()
    assertEquals(state.mutations.size, state.filteredUploadMutations.size)

    // Leaving Uploads clears the filter, and opening it from the drawer shows everything.
    state.openUploadsForEntity(entityId)
    state.closeDrawerSubView()
    assertEquals(null, state.uploadsEntityFilterId)
    state.openUploadsForEntity(entityId)
    state.drawerOpenUploads()
    assertEquals(null, state.uploadsEntityFilterId)
  }

  @Test
  fun surveyMap_marksOnlyEntitiesWithOutboxMutationsAsPending() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    val pendingIds = state.pendingUploadEntityIds
    val features =
      SurveyMapContent.main(state, showNavigation = true).entityFeatures().associateBy { it.id }

    val entities = state.visibleMapEntities
    // The old logic keyed off the ○ workflow status, so these would have been drawn as synced.
    assertTrue(entities.any { !it.isNotStarted && it.id in pendingIds })
    entities.forEach { ent ->
      val feature = assertNotNull(features[SurveyMapIds.entity(ent.id)])
      val variant = feature.properties.getValue(SurveyMapContent.PROP_VARIANT)
      assertEquals(
        ent.id in pendingIds,
        variant == SurveyMapContent.Variant.PENDING ||
          variant == SurveyMapContent.Variant.SELECTED_PENDING,
        "Unexpected variant $variant for ${ent.id} (markerSymbol=${ent.markerSymbol})",
      )
    }
  }

  private fun SurveyMap.entityFeatures() =
    content.sources.first { it.id == SurveyMapContent.ENTITY_SOURCE }.features

  private fun SurveyMap.overlayIds() =
    content.sources.first { it.id == SurveyMapContent.OVERLAY_SOURCE }.features.map { it.id }

  @Test
  fun surveyMapIds_formGeometryIdsRoundTrip_andDontCollideWithOtherIds() {
    val path = "/data/site/plot_boundary"
    val id = SurveyMapIds.formGeometry(path)
    assertEquals(path, SurveyMapIds.formGeometryPathOf(id))
    assertEquals(path, SurveyMapIds.formGeometryPathOf(SurveyMapIds.formGeometryVertex(path, 3)))
    assertNull(SurveyMapIds.entityIdOf(id))
    assertNull(SurveyMapIds.clusterIdOf(id))
    assertFalse(SurveyMapIds.isPlace(id))
    assertNull(SurveyMapIds.formGeometryPathOf(SurveyMapIds.entity("form-geometry:x")))
    assertNull(SurveyMapIds.formGeometryPathOf(SurveyMapIds.cluster("c1")))
    assertNull(SurveyMapIds.formGeometryPathOf(SurveyMapContent.DRAFT_SHAPE_ID))
    assertNull(SurveyMapIds.formGeometryPathOf(SurveyMapIds.USER))
  }

  @Test
  fun formGeometryBounds_coverAllVertices_andAreNullWhenEmpty() {
    assertNull(formGeometryBounds(emptyList()))
    val bounds =
      assertNotNull(
        formGeometryBounds(
          listOf(
            GeoPoint(latitude = 1.0, longitude = 10.0),
            GeoPoint(latitude = -2.0, longitude = 12.0),
            GeoPoint(latitude = 0.5, longitude = 8.0),
          )
        )
      )
    assertEquals(LngLatBounds(west = 8.0, south = -2.0, east = 12.0, north = 1.0), bounds)
    assertEquals(16.0, MapDrawingKind.POINT.maxFramingZoom)
    assertEquals(17.0, MapDrawingKind.LINE.maxFramingZoom)
    assertEquals(17.0, MapDrawingKind.POLYGON.maxFramingZoom)
  }

  @Test
  fun contributorIdentities_alwaysHiddenFromNonOrganizersAcrossAllSharingPolicies() {
    val state =
      MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
    val entity = state.entities.first { it.id == "entity-shade-201" }
    val mayaSubmission =
      entity.submissions.first { it.collectorEmail == "maya.lin@groundplatform.org" }
    val samuelSubmission =
      entity.submissions.first { it.collectorEmail == "s.kariuki@kenyaforestry.org" }

    // 1. Default signed-in user (Maya Lin) is a survey organizer on survey-kenya-coffee:
    //    organizers always see full contributor names and emails.
    assertFalse(state.shouldHideContributorIdentity(mayaSubmission))
    assertFalse(state.shouldHideContributorIdentity(samuelSubmission))
    assertEquals("Maya Lin", state.displayCollectorName(mayaSubmission))
    assertEquals("Samuel Kariuki", state.displayCollectorName(samuelSubmission))
    assertEquals(
      "Samuel Kariuki (s.kariuki@kenyaforestry.org)",
      state.displayCollectorAttribution(samuelSubmission),
    )

    // 2. Switch to survey-serengeti-corridor, where Maya Lin is a regular organization MEMBER
    //    (not an organizer): Maya sees her own name with "(You)", while Samuel's identity is
    // hidden.
    state.openSurvey("survey-serengeti-corridor")
    assertFalse(state.shouldHideContributorIdentity(mayaSubmission))
    assertTrue(state.shouldHideContributorIdentity(samuelSubmission))
    assertEquals("Maya Lin (You)", state.displayCollectorName(mayaSubmission))
    assertEquals("Maya Lin (You)", state.displayCollectorAttribution(mayaSubmission))
    assertEquals("Participant (identity hidden)", state.displayCollectorName(samuelSubmission))
    assertEquals(
      "Participant (identity hidden)",
      state.displayCollectorAttribution(samuelSubmission),
    )
  }
}
