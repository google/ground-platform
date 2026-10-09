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

import groundplatform.v2.forms.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.ui.MapDrawingKind
import org.groundplatform.v2.core.forms.ui.mapDrawingVertices
import org.groundplatform.v2.core.forms.ui.setMapDrawingVertices
import org.groundplatform.v2.devtools.prototypeapp.domain.model.BasemapType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceFormFactor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceOrientation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DownloadSurveyEntryOrigin
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityDetailsPane
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityGeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GROUND_WEBSITE_URL
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LayerSourceType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ListFilterTab
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocationLockState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MainDrawerSubView
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MainSurveyViewMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapClusterFeatureItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapFeatureKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationOperationKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationSyncState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.NavigationTargetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OfflineBasemapStyle
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PrototypeScreen
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UploadStatusFilter
import org.groundplatform.v2.devtools.prototypeapp.domain.model.UserSettings
import org.groundplatform.v2.devtools.prototypeapp.domain.model.formatClusterSitesCountLabel
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.groupClusterFeaturesByMarkerSymbol
import org.groundplatform.v2.devtools.prototypeapp.domain.model.hasGeometry
import org.groundplatform.v2.devtools.prototypeapp.domain.model.maxFramingZoom
import org.groundplatform.v2.devtools.prototypeapp.domain.model.parsePlaceCoordinates
import org.groundplatform.v2.devtools.prototypeapp.domain.model.relatedEntityForPropertyValue
import org.groundplatform.v2.devtools.prototypeapp.ui.map.SurveyMap
import org.groundplatform.v2.devtools.prototypeapp.ui.map.SurveyMapContent
import org.groundplatform.v2.devtools.prototypeapp.ui.map.SurveyMapIds
import org.groundplatform.v2.devtools.prototypeapp.ui.map.SurveyMarker
import org.groundplatform.v2.devtools.prototypeapp.ui.state.formGeometryBounds
import org.groundplatform.v2.devtools.prototypeapp.ui.state.maxFramingZoom
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.ui
import org.groundplatform.v2.devtools.prototypeapp.ui.workbench.BAOBAB_BIOMETRICS_SAMPLE_XFORMS_XML
import org.groundplatform.v2.map.LatLng
import org.groundplatform.v2.map.LngLatBounds

class PrototypeAppStateTest {

  @Test
  fun initialState_startsOnSignInWithSampleSurveys() {
    val state = PrototypeAppState()

    assertEquals(PrototypeScreen.SIGN_IN, state.currentScreen)
    assertFalse(state.isSignedIn)
    assertFalse(state.hasAcceptedTerms)
    assertEquals(6, state.surveys.size)
    assertEquals(6, state.filteredSurveys.size)
    assertTrue(state.downloadedSurveyCount >= 1)
  }

  @Test
  fun onboardingFlow_advancesThroughSignInTermsAndDownloadSurvey() {
    val state = PrototypeAppState()

    // 1. Initial Screen is Sign In
    assertEquals(PrototypeScreen.SIGN_IN, state.currentScreen)

    // 2. Sign In with Google -> Terms of Service
    state.signInWithGoogle()
    assertTrue(state.isSignedIn)
    assertEquals(PrototypeScreen.TERMS_OF_SERVICE, state.currentScreen)

    // 3. Accept Terms of Service -> Download Survey
    state.acceptTermsOfService()
    assertTrue(state.hasAcceptedTerms)
    assertEquals(PrototypeScreen.DOWNLOAD_SURVEY, state.currentScreen)
  }

  @Test
  fun declineTermsOfService_returnsToSignInAndResetsAuth() {
    val state = PrototypeAppState()
    state.signInWithGoogle()
    assertEquals(PrototypeScreen.TERMS_OF_SERVICE, state.currentScreen)

    state.declineTermsOfService()
    assertEquals(PrototypeScreen.SIGN_IN, state.currentScreen)
    assertFalse(state.isSignedIn)
    assertFalse(state.hasAcceptedTerms)
  }

  @Test
  fun searchQuery_filtersSurveysByNameLocationAndOrganization() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.DOWNLOAD_SURVEY)

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
  fun downloadSurvey_marksSurveyAsDownloaded() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.DOWNLOAD_SURVEY)
    val targetId = "survey-serengeti-corridor"
    assertFalse(state.surveys.first { it.id == targetId }.isDownloaded)

    state.downloadSurvey(targetId)
    assertTrue(state.surveys.first { it.id == targetId }.isDownloaded)

    // Toggle downloaded indicator off and back on
    state.toggleSurveyDownloaded(targetId)
    assertFalse(state.surveys.first { it.id == targetId }.isDownloaded)
  }

  @Test
  fun openSurvey_navigatesToMainSurveyScreenWithNoDefaultSelectedSite() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.DOWNLOAD_SURVEY)
    assertEquals(null, state.selectedEntityId)
    assertEquals(null, state.selectedEntity)

    state.openSurvey("survey-kenya-coffee")

    assertEquals(PrototypeScreen.MAIN_SURVEY, state.currentScreen)
    assertEquals(MainSurveyViewMode.MAP, state.mainViewMode)
    assertEquals("survey-kenya-coffee", state.activeSurvey.id)
    assertEquals(null, state.selectedEntityId)
    assertEquals(null, state.selectedEntity)
    assertFalse(state.isEntityBottomSheetExpanded)
  }

  @Test
  fun toggleLayerVisibility_filtersVisibleMapEntitiesAndDeselectsHiddenEntity() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

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
  fun listViewSearch_filtersPlacesAndEntitiesAndRemovesFormSubmissionSearch() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    state.setMainSurveyViewMode(MainSurveyViewMode.LIST)

    // ListFilterTab contains ALL, PLACES ("Places"), ENTITIES ("Map features")
    assertEquals(
      listOf(ListFilterTab.ALL, ListFilterTab.PLACES, ListFilterTab.ENTITIES),
      ListFilterTab.entries,
    )
    assertEquals("Places", ListFilterTab.PLACES.label)
    assertEquals("Map features", ListFilterTab.ENTITIES.label)
    assertEquals("Map features", state.activeEntitiesTabLabel)
    assertEquals("map features", state.activeEntitiesCountNoun)
    assertEquals("Coffee Parcel", state.entitySingularTypeLabel("entity-nyr-104"))
    assertEquals("Shade Tree Monitoring Plot", state.entitySingularTypeLabel("entity-shade-201"))
    assertEquals("Cooperative Washing Station", state.entitySingularTypeLabel("entity-station-01"))

    // When filtered down to a single visible entity dataset layer, dynamic domain naming activates
    state.toggleLayerVisibility("layer-shade-transects")
    state.toggleLayerVisibility("layer-water-points")
    assertEquals("Coffee Parcels", state.activeEntitiesTabLabel)
    assertEquals("coffee parcels", state.activeEntitiesCountNoun)
    state.toggleLayerVisibility("layer-shade-transects")
    state.toggleLayerVisibility("layer-water-points")
    assertEquals("Map features", state.activeEntitiesTabLabel)

    // Unfiltered counts: 0 places when search query is blank, 5 entities grouped across 3 datasets;
    // form submission search is removed since there is no unique key to identify them with
    assertEquals(0, state.filteredListPlaces.size)
    assertTrue(state.filteredListPlaces.isEmpty())
    assertEquals(5, state.filteredListEntities.size)
    assertEquals(3, state.groupedFilteredListEntities.size)
    assertTrue(state.filteredListSubmissions.isEmpty())
    assertTrue(state.groupedFilteredListSubmissions.isEmpty())

    // Search for a geographic place ("Karima Hill" / "Karima Hill Forest Reserve")
    state.updateListSearchQuery("Karima Hill")
    assertEquals(1, state.filteredListPlaces.size)
    assertEquals("Karima Hill Forest Reserve", state.filteredListPlaces.first().name)
    assertTrue(state.filteredListEntities.isEmpty())

    // Verify coordinate parsing helper handles both degree/cardinal and signed decimal strings
    val parsedCardinal = assertNotNull(state.parsePlaceCoordinates("0.5012° S, 36.9324° E"))
    assertEquals(-0.5012, parsedCardinal.first, 0.0001)
    assertEquals(36.9324, parsedCardinal.second, 0.0001)
    val parsedDecimal = assertNotNull(state.parsePlaceCoordinates("-0.5012, 36.9324"))
    assertEquals(-0.5012, parsedDecimal.first, 0.0001)
    assertEquals(36.9324, parsedDecimal.second, 0.0001)

    // Selecting a place centers/zooms the map on the place's bounds, switches locationLockState to
    // PANNED,
    // and collapses the bottom sheet. A country zooms out much farther than a village/forest.
    state.selectPlace("place-kenya-country")
    val kenyaZoom = assertNotNull(state.selectedPlace).targetZoom
    val kenyaZoomDelta = state.mapZoomDelta
    assertTrue(kenyaZoom < 6.0f)

    state.selectPlace("place-iriaini-market")
    val villageZoom = assertNotNull(state.selectedPlace).targetZoom
    val villageZoomDelta = state.mapZoomDelta
    assertTrue(villageZoom > 13.0f)
    assertTrue(villageZoom > kenyaZoom)
    assertTrue(villageZoomDelta > kenyaZoomDelta)

    // Clicking away on the map (selectEntity(null)) dismisses the selected place chip
    state.selectEntity(null)
    assertEquals(null, state.selectedPlace)

    state.selectPlace("place-karima-forest")
    assertEquals("place-karima-forest", state.selectedPlace?.id)
    assertEquals(MainSurveyViewMode.MAP, state.mainViewMode)
    assertEquals(LocationLockState.PANNED, state.locationLockState)
    assertFalse(state.isCameraFollowingUser)
    assertFalse(state.isEntityBottomSheetExpanded)

    // Straight-line wayfinding to a place works
    state.startNavigationToPlace("place-karima-forest")
    val placeNav = assertNotNull(state.activeNavigation)
    assertEquals(NavigationTargetKind.PLACE, placeNav.targetKind)
    assertEquals("place-karima-forest", placeNav.targetId)
    state.stopNavigation()
    state.clearSelectedPlace()

    // Search for decimal coordinates creates an ad-hoc coordinate place match
    state.updateListSearchQuery("-0.4210, 36.9520")
    assertTrue(state.filteredListPlaces.any { it.categoryLabel == "GPS Coordinates" })

    // Search for "Kamau" (matches entity label & owner property)
    state.updateListSearchQuery("Kamau")
    assertEquals(1, state.filteredListEntities.size)
    assertEquals("Plot NYR-104 • Kamau Family Parcel", state.filteredListEntities.first().label)

    // Filtering by ListFilterTab.PLACES hides entities; places remain empty when query is blank
    state.clearListSearchQuery()
    state.selectListFilterTab(ListFilterTab.PLACES)
    assertEquals(0, state.filteredListPlaces.size)
    assertTrue(state.filteredListEntities.isEmpty())
    state.updateListSearchQuery("Nyeri")
    assertTrue(state.filteredListPlaces.isNotEmpty())
    assertTrue(state.filteredListEntities.isEmpty())
    state.clearListSearchQuery()

    state.selectListFilterTab(ListFilterTab.ENTITIES)
    assertTrue(state.filteredListPlaces.isEmpty())
    assertEquals(5, state.filteredListEntities.size)
    state.selectListFilterTab(ListFilterTab.ALL)

    // Live Places search results merge into filteredListPlaces
    state.updateListSearchQuery("Aberdare National Park")
    state.onPlacesSearchResults(
      query = "Aberdare National Park",
      results =
        listOf(
          SurveyPlaceItem(
            id = "place.aberdare.901",
            name = "Aberdare National Park",
            categoryLabel = "National Park",
            regionSubtitle = "Nyeri County, Kenya",
            coordinatesLabel = "0.3833° S, 36.7000° E",
            normalizedX = 0.05f,
            normalizedY = 0.05f,
            longitude = 36.7,
            latitude = -0.3833,
          )
        ),
    )
    assertTrue(
      state.filteredListPlaces.any {
        it.name == "Aberdare National Park" && it.sourceLabel == "Places API"
      }
    )

    // Workbench offline simulation disables Mapbox Places search while keeping local map features
    // searchable
    state.selectListFilterTab(ListFilterTab.PLACES)
    assertEquals(ListFilterTab.PLACES, state.listFilterTab)
    state.workbench.updateAirplaneMode(true)
    assertTrue(state.workbenchUiState.isAirplaneMode)
    assertFalse(state.isOnline)
    assertFalse(state.isPlacesSearchAvailable)
    assertEquals(ListFilterTab.ALL, state.listFilterTab)
    assertTrue(state.filteredListPlaces.isEmpty())
    state.selectListFilterTab(ListFilterTab.PLACES)
    assertEquals(ListFilterTab.ALL, state.listFilterTab)

    // Searching local features ("Kamau") works while offline
    state.updateListSearchQuery("Kamau")
    assertTrue(state.filteredListPlaces.isEmpty())
    assertEquals(1, state.filteredListEntities.size)
    assertEquals("Plot NYR-104 • Kamau Family Parcel", state.filteredListEntities.first().label)

    // Turning Airplane mode back off in the workbench restores Places search availability (empty
    // when query blank, populated when query entered)
    state.workbench.toggleAirplaneMode()
    assertFalse(state.workbenchUiState.isAirplaneMode)
    assertTrue(state.isOnline)
    assertTrue(state.isPlacesSearchAvailable)
    state.clearListSearchQuery()
    assertEquals(0, state.filteredListPlaces.size)
    state.updateListSearchQuery("36.9")
    assertEquals(6, state.filteredListPlaces.size)
  }

  @Test
  fun entityBottomSheetFormButtons_allowAll1ToNSubmissions_andProgressMarkerFromEmptyToHalfToCheckmark() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
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
    assertEquals(PrototypeScreen.DOWNLOAD_SURVEY, state.currentScreen)

    // Sign out returns to SIGN_IN
    state.openSurvey("survey-kenya-coffee")
    state.drawerSignOut()
    assertEquals(PrototypeScreen.SIGN_IN, state.currentScreen)
    assertFalse(state.isSignedIn)
  }

  @Test
  fun qrCodeAndSharePdfModals_openForGeospatialEntitiesAndSubmissions() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

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
    val state = PrototypeAppState()
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
  fun deviceFormFactor_togglesBetweenMobileAndTablet() {
    val state = PrototypeAppState()

    // Defaults to Mobile (404 × 764 dp)
    assertEquals(DeviceFormFactor.MOBILE, state.deviceFormFactor)
    assertEquals(404, state.deviceFormFactor.frameWidthDp)
    assertEquals(764, state.deviceFormFactor.frameHeightDp)

    // Toggle to Tablet (780 × 620 dp)
    state.toggleDeviceFormFactor()
    assertEquals(DeviceFormFactor.TABLET, state.deviceFormFactor)
    assertEquals(780, state.deviceFormFactor.frameWidthDp)
    assertEquals(620, state.deviceFormFactor.frameHeightDp)

    // Explicitly select Mobile
    state.selectDeviceFormFactor(DeviceFormFactor.MOBILE)
    assertEquals(DeviceFormFactor.MOBILE, state.deviceFormFactor)
  }

  @Test
  fun rotateDevice_rotatesMobileAndTabletBetweenPortraitAndLandscape() {
    val state = PrototypeAppState()

    // Mobile defaults to Portrait (404 × 764 dp)
    assertEquals(DeviceFormFactor.MOBILE, state.deviceFormFactor)
    assertEquals(DeviceOrientation.PORTRAIT, state.deviceOrientation)
    assertFalse(state.isDeviceRotated)
    assertEquals(404, state.effectiveFrameWidthDp)
    assertEquals(764, state.effectiveFrameHeightDp)
    assertEquals("404 × 764 dp", state.effectiveDimensionsLabel)

    // Rotate Mobile -> Landscape (764 × 404 dp)
    state.rotateDevice()
    assertEquals(DeviceOrientation.LANDSCAPE, state.deviceOrientation)
    assertTrue(state.isDeviceRotated)
    assertEquals(764, state.effectiveFrameWidthDp)
    assertEquals(404, state.effectiveFrameHeightDp)
    assertEquals("764 × 404 dp", state.effectiveDimensionsLabel)

    // Rotate Mobile back -> Portrait (404 × 764 dp)
    state.rotateDevice()
    assertEquals(DeviceOrientation.PORTRAIT, state.deviceOrientation)
    assertFalse(state.isDeviceRotated)
    assertEquals(404, state.effectiveFrameWidthDp)
    assertEquals(764, state.effectiveFrameHeightDp)

    // Switch to Tablet -> defaults to Landscape (780 × 620 dp)
    state.selectDeviceFormFactor(DeviceFormFactor.TABLET)
    assertEquals(DeviceFormFactor.TABLET, state.deviceFormFactor)
    assertEquals(DeviceOrientation.LANDSCAPE, state.deviceOrientation)
    assertFalse(state.isDeviceRotated)
    assertEquals(780, state.effectiveFrameWidthDp)
    assertEquals(620, state.effectiveFrameHeightDp)
    assertEquals("780 × 620 dp", state.effectiveDimensionsLabel)

    // Rotate Tablet -> Portrait (620 × 780 dp)
    state.rotateDevice()
    assertEquals(DeviceOrientation.PORTRAIT, state.deviceOrientation)
    assertTrue(state.isDeviceRotated)
    assertEquals(620, state.effectiveFrameWidthDp)
    assertEquals(780, state.effectiveFrameHeightDp)
    assertEquals("620 × 780 dp", state.effectiveDimensionsLabel)
  }

  @Test
  fun launchFormForEntity_opensFormWizardController_andCompletesSubmissionWithFormattedAnswers() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    val initialShadeSubs = state.entities.first { it.id == "entity-shade-201" }.submissions.size
    assertEquals(3, initialShadeSubs)
    assertFalse(state.isDataCollectionFormOpen)
    assertEquals(null, state.activeFormWizardController)

    // Launch form on 1:N entity `entity-shade-201`
    state.launchFormForEntity(entityId = "entity-shade-201", formId = "form-shade-canopy-audit")

    assertTrue(state.isDataCollectionFormOpen)
    assertEquals("entity-shade-201", state.activeDataCollectionEntityId)
    assertEquals("form-shade-canopy-audit", state.activeDataCollectionFormId)
    val controller = state.activeFormWizardController!!
    assertEquals("EUDR & Shade-Tree Field Survey", controller.formState.formDef.title)

    // Modify an answer via FormWizardController before finalizing
    controller.updateString("/data/dominant_shade_species", "Cordia africana & Grevillea")
    controller.updateInt("/data/surviving_saplings_count", 196)

    // Finalize and submit recordInstance
    state.completeActiveFormSubmission(controller.formState.recordInstance)

    // Form runner closes and the entity's submissions list grows by 1 with the answered fields
    assertFalse(state.isDataCollectionFormOpen)
    assertEquals(null, state.activeFormWizardController)
    val updatedEntity = state.entities.first { it.id == "entity-shade-201" }
    assertEquals(4, updatedEntity.submissions.size)
    val latestSubmission = updatedEntity.submissions.first()
    assertEquals("EUDR & Shade-Tree Field Survey", latestSubmission.formTitle)
    assertTrue(
      latestSubmission.fields.any {
        it.questionName == "dominant_shade_species" &&
          it.answerValue == "Cordia africana & Grevillea"
      }
    )
    assertTrue(
      latestSubmission.fields.any {
        it.questionName == "surviving_saplings_count" && it.answerValue == "196"
      }
    )
    assertTrue(state.activeSurveyNotice?.contains("Submitted") == true)
  }

  @Test
  fun updateCustomXFormsXml_parsesValidAndInvalidXForms_andRefreshesActiveController() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    assertEquals(null, state.xformsXmlError)
    assertTrue(state.customFormDef != null)

    // Launch form runner first
    state.launchFormForEntity(entityId = "entity-shade-201", formId = "form-shade-canopy-audit")
    assertTrue(state.isDataCollectionFormOpen)

    // 1. Update with valid Baobab Biometrics XForms XML -> refreshes open FormWizardController
    state.updateCustomXFormsXml(BAOBAB_BIOMETRICS_SAMPLE_XFORMS_XML)
    assertEquals(null, state.xformsXmlError)
    assertEquals("Shade Tree & Baobab Biometrics", state.customFormDef?.title)
    assertEquals(
      "Shade Tree & Baobab Biometrics",
      state.activeFormWizardController?.formState?.formDef?.title,
    )

    // 2. Update with invalid XML -> populates xformsXmlError and clears customFormDef
    state.updateCustomXFormsXml("<h:html><unclosed_tag></h:html>")
    assertTrue(state.xformsXmlError != null)
    assertEquals(null, state.customFormDef)

    // 3. Reset to default sample XForms XML -> clears error and restores default FormDef
    state.resetDefaultXFormsXml()
    assertEquals(null, state.xformsXmlError)
    assertEquals("EUDR & Shade-Tree Field Survey", state.customFormDef?.title)

    // 4. Clearing customXFormsXml uses built-in fallback FormDef per form ID
    state.updateCustomXFormsXml("")
    assertEquals(null, state.xformsXmlError)
    assertEquals(null, state.customFormDef)
    assertEquals(
      "Seasonal Shade Tree & Canopy Audit",
      state.activeFormWizardController?.formState?.formDef?.title,
    )
    state.closeActiveFormRunner()
    assertFalse(state.isDataCollectionFormOpen)
  }

  @Test
  fun entityBottomSheet_startsCollapsedAndSupportsExpandAndCollapse() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.SIGN_IN)

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
    assertEquals(GROUND_WEBSITE_URL, state.visitedWebsiteUrl)
    assertTrue(state.activeSurveyNotice?.contains(GROUND_WEBSITE_URL) == true)
  }

  @Test
  fun mobileUiDataModels_doNotContain1To1Or1ToNIndicators() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
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
    val state = PrototypeAppState()
    state.signInWithGoogle()
    state.acceptTermsOfService()

    // 1. Reached after ToS -> origin is AFTER_TOS
    assertEquals(PrototypeScreen.DOWNLOAD_SURVEY, state.currentScreen)
    assertEquals(DownloadSurveyEntryOrigin.AFTER_TOS, state.downloadSurveyEntryOrigin)
    assertFalse(state.isDownloadSurveyAccessedFromSurveyList)
    assertFalse(state.isDownloadSurveySignOutPromptOpen)

    // Back action prompts first without immediately signing out
    state.navigateBackFromDownloadSurvey()
    assertTrue(state.isDownloadSurveySignOutPromptOpen)
    assertEquals(PrototypeScreen.DOWNLOAD_SURVEY, state.currentScreen)
    assertTrue(state.isSignedIn)

    // Canceling prompt keeps user on Download surveys screen and signed in
    state.dismissDownloadSurveySignOutPrompt()
    assertFalse(state.isDownloadSurveySignOutPromptOpen)
    assertEquals(PrototypeScreen.DOWNLOAD_SURVEY, state.currentScreen)
    assertTrue(state.isSignedIn)

    // Confirming sign out from prompt signs the user out and returns to Sign In screen
    state.navigateBackFromDownloadSurvey()
    assertTrue(state.isDownloadSurveySignOutPromptOpen)
    state.confirmDownloadSurveySignOut()
    assertFalse(state.isDownloadSurveySignOutPromptOpen)
    assertFalse(state.isSignedIn)
    assertFalse(state.hasAcceptedTerms)
    assertEquals(PrototypeScreen.SIGN_IN, state.currentScreen)

    // 2. Re-sign in, open a survey, and access Download surveys from the Survey list
    // (SWITCH_SURVEYS)
    state.signInWithGoogle()
    state.acceptTermsOfService()
    state.openSurvey("survey-kenya-coffee")
    state.drawerSwitchSurveys()
    assertEquals(PrototypeScreen.MAIN_SURVEY, state.currentScreen)
    assertEquals(MainDrawerSubView.SWITCH_SURVEYS, state.activeDrawerSubView)

    state.openDownloadMoreSurveysScreen()
    assertEquals(PrototypeScreen.DOWNLOAD_SURVEY, state.currentScreen)
    assertEquals(DownloadSurveyEntryOrigin.SURVEY_LIST, state.downloadSurveyEntryOrigin)
    assertTrue(state.isDownloadSurveyAccessedFromSurveyList)

    // Back action when accessed from Survey list returns directly to the Survey list without
    // prompting
    state.navigateBackFromDownloadSurvey()
    assertFalse(state.isDownloadSurveySignOutPromptOpen)
    assertTrue(state.isSignedIn)
    assertEquals(PrototypeScreen.MAIN_SURVEY, state.currentScreen)
    assertEquals(MainDrawerSubView.SWITCH_SURVEYS, state.activeDrawerSubView)
  }

  @Test
  fun userFacingTerminology_replacesEntityWithGenericOrDomainFocusedLanguage() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

    assertEquals("Map features", ListFilterTab.ENTITIES.label)
    assertEquals("Places", ListFilterTab.PLACES.label)
    assertEquals("Survey Layer", LayerSourceType.ENTITY_DATASET.badgeLabel)

    PrototypeScreen.entries.forEach { screen ->
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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

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
      PrototypeAppState().apply {
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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
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
  fun syncStatusIndicators_showUploadingSyncedAndFailedOnEntitiesAndSubmissionsInLists() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    state.setMainSurveyViewMode(MainSurveyViewMode.LIST)

    // 1. Default entities cover Uploading, Synced, and Failed states
    val nyr104 = state.entities.first { it.id == "entity-nyr-104" }
    val shade201 = state.entities.first { it.id == "entity-shade-201" }
    val nyr108 = state.entities.first { it.id == "entity-nyr-108" }
    val nyr112 = state.entities.first { it.id == "entity-nyr-112" }
    val station01 = state.entities.first { it.id == "entity-station-01" }

    assertEquals(SyncStatus.SYNCED, nyr104.syncStatus)
    assertTrue(nyr104.isSynced)
    assertEquals(SyncStatus.UPLOADING, shade201.syncStatus)
    assertTrue(shade201.isUploading)
    assertEquals(SyncStatus.UPLOADING, nyr108.syncStatus)
    assertEquals(SyncStatus.FAILED, nyr112.syncStatus)
    assertTrue(nyr112.isFailed)
    assertEquals(SyncStatus.FAILED, station01.syncStatus)

    assertEquals(2, state.uploadingEntityCount)
    assertEquals(1, state.syncedEntityCount)
    assertEquals(2, state.failedEntityCount)

    // 2. Default submissions cover Uploading, Synced, and Failed states
    val subNyr104 = state.allSubmissions.first { it.id == "sub-nyr-104-baseline" }
    val subShadeWave3 = state.allSubmissions.first { it.id == "sub-shade-201-wave3" }
    val subShadeWave2 = state.allSubmissions.first { it.id == "sub-shade-201-wave2" }
    val subWshSep = state.allSubmissions.first { it.id == "sub-wsh-01-sep" }

    assertEquals(SyncStatus.SYNCED, subNyr104.syncStatus)
    assertTrue(subNyr104.isSynced)
    assertEquals(SyncStatus.UPLOADING, subShadeWave3.syncStatus)
    assertTrue(subShadeWave3.isUploading)
    assertEquals(SyncStatus.SYNCED, subShadeWave2.syncStatus)
    assertEquals(SyncStatus.FAILED, subWshSep.syncStatus)
    assertTrue(subWshSep.isFailed)

    assertEquals(3, state.uploadingSubmissionCount)
    assertEquals(5, state.syncedSubmissionCount)
    assertEquals(1, state.failedSubmissionCount)

    // 3. Filtering lists by syncStatus label ("Uploading", "Synced", "Failed") filters entities
    // (form submission search is removed)
    state.updateListSearchQuery("Uploading")
    assertEquals(2, state.filteredListEntities.size)
    assertTrue(state.filteredListEntities.all { it.syncStatus == SyncStatus.UPLOADING })
    assertTrue(state.filteredListSubmissions.isEmpty())

    state.updateListSearchQuery("Failed")
    assertEquals(2, state.filteredListEntities.size)
    assertTrue(state.filteredListEntities.all { it.syncStatus == SyncStatus.FAILED })
    assertTrue(state.filteredListSubmissions.isEmpty())

    state.updateListSearchQuery("Synced")
    assertEquals(1, state.filteredListEntities.size)
    assertEquals("entity-nyr-104", state.filteredListEntities.first().id)
    assertTrue(state.filteredListSubmissions.isEmpty())
    state.clearListSearchQuery()

    // 4. Cycling / updating submission and entity sync statuses updates parent entity aggregate
    // state
    state.cycleSubmissionSyncStatus("sub-shade-201-wave3")
    assertEquals(
      SyncStatus.SYNCED,
      state.allSubmissions.first { it.id == "sub-shade-201-wave3" }.syncStatus,
    )
    assertEquals(SyncStatus.SYNCED, state.entities.first { it.id == "entity-shade-201" }.syncStatus)

    // 5. Syncing all Outbox mutations transitions remaining Uploading/Failed items to Synced
    state.syncAllOutboxMutations()
    assertTrue(state.entities.all { it.syncStatus == SyncStatus.SYNCED })
    assertTrue(state.allSubmissions.all { it.syncStatus == SyncStatus.SYNCED })
  }

  @Test
  fun standaloneSubmissionsWithoutAttachedEntities_supportMapSelectionListInspectionWayfindingAndFabSubmission() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

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
    assertTrue(state.mapFeatureClusters.isNotEmpty())
    assertEquals(5, state.mapFeatureClusters.sumOf { it.totalCount })

    // 3. Zoom out further (-3.75f) so all 5 entity features merge into a single cluster balloon
    state.zoomOutMap()
    assertEquals(-3.75f, state.mapZoomDelta)
    assertEquals(1, state.mapFeatureClusters.size)
    val allCluster = state.mapFeatureClusters.first()
    assertEquals(5, allCluster.totalCount)

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
  fun addRandomSites_appendsFiveThousandPolygonsEachClickAndScalesClustering() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    assertEquals(5, state.entities.size)

    // First click: +5,000 features -> 5,005 total features
    state.addRandomSites(count = 5_000)
    assertEquals(5_005, state.entities.size)
    assertEquals(5_005, state.activeSurvey.entityCount)
    assertEquals(PrototypeScreen.MAIN_SURVEY, state.currentScreen)
    assertEquals(MainSurveyViewMode.MAP, state.mainViewMode)
    assertTrue(state.entities.any { it.id == "entity-rnd-6" && it.geometryTypeLabel == "Polygon" })
    assertTrue(state.activeSurveyNotice?.contains("5000") == true)

    // Second click: +5,000 features -> 10,005 total features (>10,000 polygons)
    state.addRandomSites(count = 5_000)
    assertEquals(10_005, state.entities.size)
    assertEquals(10_005, state.activeSurvey.entityCount)
    assertTrue(state.entities.any { it.id == "entity-rnd-10005" })

    // Verify spatial clustering handles >10,000 features when zoomed out and merges over larger
    // areas as the user zooms out further to avoid overlapping cluster balloons
    repeat(3) { state.zoomOutMap() } // -2.25f: not yet clustered
    assertFalse(state.isMapClusteringActive)
    state.zoomOutMap() // -3.00f
    assertTrue(state.isMapClusteringActive)
    val clustersAtStep1 = state.mapFeatureClusters
    assertTrue(clustersAtStep1.isNotEmpty())
    assertEquals(state.visibleMapClusterFeatures.size, clustersAtStep1.sumOf { it.totalCount })

    state.zoomOutMap() // -3.75f
    val clustersAtStep2 = state.mapFeatureClusters
    assertTrue(clustersAtStep2.size < clustersAtStep1.size)
    assertEquals(state.visibleMapClusterFeatures.size, clustersAtStep2.sumOf { it.totalCount })

    // Zoom all the way out (-4.50f, e.g. 10.8z): all 10,005 features across [-2.2, 3.2] merge into
    // a single non-overlapping cluster balloon, including LineString transects
    state.zoomOutMap() // -4.50f
    val clustersAtDeepZoomOut = state.mapFeatureClusters
    assertEquals(1, clustersAtDeepZoomOut.size)
    assertEquals(10_005, clustersAtDeepZoomOut.first().siteCount)
    assertTrue(clustersAtDeepZoomOut.first().features.any { it.id == "entity-shade-201" })

    val clusteredMap = SurveyMapContent.main(state, showNavigation = true)
    assertTrue(clusteredMap.entityFeatures().isEmpty())
    assertTrue(clusteredMap.markers.keys.any { SurveyMapIds.clusterIdOf(it) != null })
  }

  @Test
  fun resolveEntityLngLatBounds_coversLinesAndPolygonsAndCollapsesForPoints() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
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
  fun selectWorkbenchExampleForm_swapsAndValidatesAllFiveExampleForms() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    assertEquals(
      org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm.ALL_FIELD_TYPES,
      state.selectedWorkbenchExampleForm,
    )

    // 1. Simple Single-Point & Land Use Survey (pan allowed via placement-map, 10m accuracy
    // required, 3 entities via save_to, 0 standalone submissions)
    state.selectWorkbenchExampleForm(
      org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm.SINGLE_POINT_LAND_USE,
      launchImmediately = true,
    )
    assertEquals("survey-single-point-land-use", state.activeSurveyId)
    assertEquals(3, state.entities.size)
    assertEquals(0, state.standaloneSubmissions.size)
    assertEquals(3, state.allSubmissions.size)
    assertEquals(3, state.submissionGeometries.size)
    assertEquals(1, state.forms.size)
    assertFalse(state.forms.first().requiresEntity)
    assertEquals(null, state.xformsXmlError)
    assertTrue(state.isDataCollectionFormOpen)
    val singlePointFormDef = state.customFormDef!!
    assertEquals("Simple Point & Land Use Observation", singlePointFormDef.title)
    val samplePointCtrl =
      state.activeFormWizardController!!
        .steps
        .filterIsInstance<org.groundplatform.v2.core.forms.ui.FormWizardStep.QuestionStep>()
        .map { it.control }
        .first { it.canonicalPath == "/data/sample_point" }
    assertTrue(samplePointCtrl.appearance.contains("placement-map"))
    assertEquals(10.0, samplePointCtrl.controlDef.geo_config?.accuracy_threshold_meters)

    // 2. Predefined Sample Plots & Forest Assessment Survey (5 sample_plots entities, 3 preloaded
    // assessments)
    state.selectWorkbenchExampleForm(
      org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT,
      launchImmediately = true,
    )
    assertEquals("survey-sample-plots-forest", state.activeSurveyId)
    assertEquals(5, state.entities.size)
    assertEquals("plot_sp01", state.entities.first().id)
    assertEquals(3, state.allSubmissions.size)
    assertEquals(3, state.submissionGeometries.size)
    assertEquals(1, state.forms.size)
    assertTrue(state.forms.first().requiresEntity)
    assertEquals(null, state.xformsXmlError)
    val samplePlotsFormDef = state.customFormDef!!
    assertEquals("Sample Plot Entity & Forest Stand Assessment", samplePlotsFormDef.title)
    assertTrue(
      samplePlotsFormDef.model?.secondary_instances?.any {
        it.id == "sample_plots" && it.inline_data.contains("plot_sp01")
      } == true
    )
    val plotSelectCtrl =
      state.activeFormWizardController!!
        .steps
        .filterIsInstance<org.groundplatform.v2.core.forms.ui.FormWizardStep.QuestionStep>()
        .map { it.control }
        .first { it.canonicalPath == "/data/sample_plot_entity" }
    assertEquals(5, plotSelectCtrl.options.size)
    assertEquals("plot_sp01", plotSelectCtrl.options.first().value)

    // 3. Commodity Plot Perimeter (walk + pan override) & Plot Center (no pan, <= 5m GPS accuracy,
    // 3 entities via save_to, 0 standalone submissions)
    state.selectWorkbenchExampleForm(
      org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm.COMMODITY_PERIMETER_AND_CENTER,
      launchImmediately = true,
    )
    assertEquals("survey-commodity-perimeter-center", state.activeSurveyId)
    assertEquals(3, state.entities.size)
    assertEquals(0, state.standaloneSubmissions.size)
    assertEquals(3, state.allSubmissions.size)
    assertEquals(3, state.submissionGeometries.size)
    assertEquals(1, state.forms.size)
    assertFalse(state.forms.first().requiresEntity)
    assertEquals(null, state.xformsXmlError)
    val commodityFormDef = state.customFormDef!!
    assertEquals("Forest-Risk Commodity Plot Perimeter & Center Mapping", commodityFormDef.title)
    val commodityQuestionControls =
      state.activeFormWizardController!!
        .steps
        .filterIsInstance<org.groundplatform.v2.core.forms.ui.FormWizardStep.QuestionStep>()
        .map { it.control }
    val perimeterCtrl = commodityQuestionControls.first {
      it.canonicalPath == "/data/plot_perimeter"
    }
    val centerCtrl = commodityQuestionControls.first {
      it.canonicalPath == "/data/plot_center_point"
    }
    assertTrue(perimeterCtrl.appearance.contains("placement-map"))
    assertFalse(centerCtrl.appearance.contains("placement-map"))
    assertEquals(5.0, centerCtrl.controlDef.geo_config?.accuracy_threshold_meters)

    // 4. Household Survey (6 preloaded past_individuals entities, 4 follow-up submissions)
    state.selectWorkbenchExampleForm(
      org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm.HOUSEHOLD_SURVEY_PAST_INDIVIDUALS,
      launchImmediately = true,
    )
    assertEquals("survey-household-past-individuals", state.activeSurveyId)
    assertEquals(6, state.entities.size)
    assertEquals("ind_101", state.entities.first().id)
    assertEquals(4, state.allSubmissions.size)
    assertEquals(2, state.submissionGeometries.size)
    assertEquals(1, state.forms.size)
    assertTrue(state.forms.first().requiresEntity)
    assertEquals(null, state.xformsXmlError)
    val householdFormDef = state.customFormDef!!
    assertEquals("Household Panel Survey (Preloaded Past Individuals)", householdFormDef.title)
    val respondentSelectCtrl =
      state.activeFormWizardController!!
        .steps
        .filterIsInstance<org.groundplatform.v2.core.forms.ui.FormWizardStep.QuestionStep>()
        .map { it.control }
        .first { it.canonicalPath == "/data/primary_respondent_id" }
    assertEquals(6, respondentSelectCtrl.options.size)
    assertEquals("ind_101", respondentSelectCtrl.options.first().value)

    // 5. All Form Field Types Showcase ("EUDR & Shade-Tree Field Survey", 5 entities, 6 forms)
    state.selectWorkbenchExampleForm(
      org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm.ALL_FIELD_TYPES,
      launchImmediately = true,
    )
    assertEquals("survey-kenya-coffee", state.activeSurveyId)
    assertEquals(5, state.entities.size)
    assertEquals(6, state.forms.size)
    assertEquals(null, state.xformsXmlError)
    val allTypesFormDef = state.customFormDef!!
    assertEquals("EUDR & Shade-Tree Field Survey", allTypesFormDef.title)
    assertTrue((allTypesFormDef.model?.bindings?.size ?: 0) >= 18)
  }

  @Test
  fun completeActiveFormSubmission_createsNewEntityFromSaveTo() {
    val state = PrototypeAppState()
    state.openSurvey("survey-single-point-land-use")
    val initialEntityCount = state.entities.size
    assertEquals(3, initialEntityCount)

    // Launch the single-point land-use form from FAB (which does not require picking an entity)
    state.launchFormFromFab("form-single-point-land-use")
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
    assertEquals("Completed", createdEntity.properties["status"])

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
  fun geoPointStepInDataCollectionForm_showsMapAndSupportsCaptureLocationAndAddPointTriggers() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    state.selectWorkbenchExampleForm(
      org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm.SINGLE_POINT_LAND_USE,
      launchImmediately = true,
    )

    assertTrue(state.isDataCollectionFormOpen)
    val ctrl = assertNotNull(state.activeFormWizardController)
    assertEquals(0, ctrl.currentStepIndex)
    assertTrue(state.isCurrentFormStepGeoPoint)

    // Verify trigger labels for unpanned ("Capture location") vs panned ("Add point")
    assertEquals(
      "Capture location",
      org.groundplatform.v2.core.forms.ui.geoPointPrimaryTriggerLabel(isPanned = false),
    )
    assertEquals(
      "Add point",
      org.groundplatform.v2.core.forms.ui.geoPointPrimaryTriggerLabel(isPanned = true),
    )

    // Advance to the next step (select1 land_use) -> isCurrentFormStepGeoPoint becomes false
    assertTrue(ctrl.nextStep())
    assertFalse(state.isCurrentFormStepGeoPoint)
  }

  @Test
  fun deviceStorageInfo_calculatesStorageBreakdownAccurately() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.DOWNLOAD_SURVEY)
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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.DOWNLOAD_SURVEY)
    val targetId = "survey-serengeti-corridor"
    assertFalse(state.surveys.first { it.id == targetId }.isDownloaded)

    // Prompting on a non-downloaded survey downloads directly without warning
    state.promptRemoveDownloadedSurvey(targetId)
    assertEquals(null, state.pendingRemovalSurveyId)
    assertTrue(state.surveys.first { it.id == targetId }.isDownloaded)
  }

  @Test
  fun promptRemoveOfflineTilePackage_requiresConfirmationBeforeRemoval() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
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
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    val nonDownloadedPkg = state.offlineTilePackages.first { !it.isDownloaded }

    state.promptRemoveOfflineTilePackage(nonDownloadedPkg.id)
    assertEquals(null, state.pendingRemovalTilePackageId)
    assertTrue(state.offlineTilePackages.first { it.id == nonDownloadedPkg.id }.isDownloaded)
  }

  @Test
  fun entityDetails_openOnPropertiesWithSubmissionsOneClickAway() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
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
  fun webEntityDetailsTabs_openingSubmissionSelectsHistoryAndSwitchingTabsKeepsIt() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    val entity = state.entities.first { it.submissions.isNotEmpty() }
    val submission = entity.submissions.first()
    state.selectEntity(entity.id)

    state.selectEntityDetailsTab(EntityDetailsPane.SUBMISSIONS)
    assertEquals(EntityDetailsPane.SUBMISSIONS, state.entityDetailsPane)

    // Opening a submission (e.g. from `Uploads`) lands on the `History` tab.
    state.selectEntityDetailsTab(EntityDetailsPane.PROPERTIES)
    state.selectSubmissionDetail(submission.id)
    assertEquals(EntityDetailsPane.SUBMISSIONS, state.entityDetailsPane)

    // Switching to `Data` and back keeps the opened submission.
    state.selectEntityDetailsTab(EntityDetailsPane.PROPERTIES)
    assertEquals(EntityDetailsPane.PROPERTIES, state.entityDetailsPane)
    assertEquals(submission.id, state.selectedSubmissionId)
    state.selectEntityDetailsTab(EntityDetailsPane.SUBMISSIONS)
    assertEquals(submission.id, state.selectedSubmissionId)
  }

  @Test
  fun openingSubmissionOfAnotherEntity_framesThatEntity() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    val entities = state.entities.filter { it.submissions.isNotEmpty() }
    state.selectEntity(entities[0].id)
    val epoch = state.entitySelectionEpoch

    state.selectSubmissionDetail(entities[1].submissions.first().id)

    assertEquals(entities[1].id, state.selectedEntityId)
    assertEquals(epoch + 1, state.entitySelectionEpoch)
  }

  @Test
  fun relatedEntityForPropertyValue_resolvesReferencesToOtherRecords() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    val parcel = state.entities.first { it.properties["Washing Station"] != null }
    val station = state.entities.first { it.id == parcel.properties["Washing Station"] }

    assertEquals(station, state.relatedEntityForPropertyValue(parcel, station.id))
    assertEquals(station, state.relatedEntityForPropertyValue(parcel, station.geoId))
    assertEquals(null, state.relatedEntityForPropertyValue(parcel, "Othaya Farmers Co-op"))
    // A record never links to itself.
    assertEquals(null, state.relatedEntityForPropertyValue(parcel, parcel.id))
  }

  @Test
  fun availableSubmissions_offlineShowsOnlySubmissionsStoredOnDevice() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    val entity =
      state.entities.first { e ->
        e.submissions.any { state.isSubmissionStoredOnDevice(it) } &&
          e.submissions.any { !state.isSubmissionStoredOnDevice(it) }
      }

    assertEquals(
      entity.submissions.size,
      state.availableGroupedSubmissionsForEntity(entity).sumOf { it.submissions.size },
    )

    state.workbench.updateAirplaneMode(true)
    val offline = state.availableGroupedSubmissionsForEntity(entity).flatMap { it.submissions }
    assertTrue(offline.isNotEmpty())
    assertTrue(offline.size < entity.submissions.size)
    assertTrue(offline.all { state.isSubmissionStoredOnDevice(it) })

    // Other collectors' submissions (e.g. Samuel Kariuki on entity-nyr-108) require connectivity
    val nyr108 = state.entities.first { it.id == "entity-nyr-108" }
    assertTrue(state.availableGroupedSubmissionsForEntity(nyr108).isEmpty())
  }

  @Test
  fun offlineMode_preventsOnlineOnlyFlows_forDownloadsAndUploads() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    state.workbench.updateAirplaneMode(true)
    assertFalse(state.isOnline)

    // 1. Outbox uploads are prevented while offline
    val initialOutboxCount = state.outboxMutationCount
    state.syncMutationNow("mut-outbox-04")
    assertEquals(initialOutboxCount, state.outboxMutationCount)
    state.syncAllOutboxMutations()
    assertEquals(initialOutboxCount, state.outboxMutationCount)
    assertTrue(state.activeSurveyNotice?.contains("offline", ignoreCase = true) == true)

    // 2. Downloading an undownloaded survey is prevented while offline, whereas opening an already
    // downloaded survey succeeds
    val undownloadedSurvey = state.surveys.first { !it.isDownloaded }
    state.downloadSurvey(undownloadedSurvey.id)
    assertFalse(state.surveys.first { it.id == undownloadedSurvey.id }.isDownloaded)

    // 3. Downloading an undownloaded offline tile package is prevented while offline
    val undownloadedTilePkg = state.offlineTilePackages.first { !it.isDownloaded }
    state.toggleOfflineTilePackage(undownloadedTilePkg.id)
    assertFalse(state.offlineTilePackages.first { it.id == undownloadedTilePkg.id }.isDownloaded)
  }

  @Test
  fun openUploadsForEntity_filtersUploadsToThatEntityUntilCleared() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
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
  fun selectWorkbenchPage_updatesActivePageAndNotifiesListener() {
    val state = PrototypeAppState()
    assertEquals(PrototypeWorkbenchPage.MOBILE_PROTOTYPE, state.activeWorkbenchPage)

    var notifiedPage: PrototypeWorkbenchPage? = null
    state.onWorkbenchPageChanged = { notifiedPage = it }

    state.selectWorkbenchPage(PrototypeWorkbenchPage.WEB_DASHBOARD)
    assertEquals(PrototypeWorkbenchPage.WEB_DASHBOARD, state.activeWorkbenchPage)
    assertEquals(PrototypeWorkbenchPage.WEB_DASHBOARD, notifiedPage)

    state.selectWorkbenchPage(PrototypeWorkbenchPage.SURVEY_EDITOR)
    assertEquals(PrototypeWorkbenchPage.SURVEY_EDITOR, state.activeWorkbenchPage)
    assertEquals(PrototypeWorkbenchPage.SURVEY_EDITOR, notifiedPage)

    state.selectWorkbenchPage(PrototypeWorkbenchPage.MOBILE_PROTOTYPE)
    assertEquals(PrototypeWorkbenchPage.MOBILE_PROTOTYPE, state.activeWorkbenchPage)
    assertEquals(PrototypeWorkbenchPage.MOBILE_PROTOTYPE, notifiedPage)
  }

  @Test
  fun surveyMap_marksOnlyEntitiesWithOutboxMutationsAsPending() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
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
  fun webMapDrawing_pointIsPlacedByOneClick_andDrawingStopsWithTheForm() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    val host = state.webMapDrawing
    assertFalse(host.isDrawing)

    // Clicks do nothing until a question asks to be drawn.
    state.addWebMapDrawingVertex(LatLng(-1.29, 36.82))
    assertNull(state.webMapDraftGeometry)

    state.launchFormForEntity(entityId = "entity-shade-201", formId = "form-shade-canopy-audit")
    val controller = assertNotNull(state.activeFormWizardController)
    val path = "/data/plot_center_gps"
    assertTrue(controller.formState.fieldStates.containsKey(path))

    host.startDrawing(path, MapDrawingKind.POINT)
    assertTrue(host.isDrawing)
    assertEquals(path, host.activeDrawingPath)
    assertEquals(MapDrawingKind.POINT, host.activeDrawingKind)
    assertEquals(MapDrawingKind.POINT, state.webMapDraftGeometry?.kind)

    state.addWebMapDrawingVertex(LatLng(-1.29, 36.82))
    val placed =
      mapDrawingVertices(controller.formState.fieldStates.getValue(path), MapDrawingKind.POINT)
    assertEquals(1, placed.size)
    assertEquals(-1.29, placed.single().latitude)
    assertEquals(36.82, placed.single().longitude)
    // One click places a point and ends the drawing.
    assertFalse(host.isDrawing)
    assertNull(state.webMapDraftGeometry)

    // Closing the form while a line is being drawn cancels the drawing.
    host.startDrawing("/data/riparian_transect", MapDrawingKind.LINE)
    assertTrue(host.isDrawing)
    state.closeActiveFormRunner()
    assertFalse(host.isDrawing)
    assertNull(state.webMapDraftGeometry)
  }

  @Test
  fun webMapDrawing_polygonCollectsVertices_rendersDraftOverlay_andRoutesAroundSelection() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    state.launchFormForEntity(entityId = "entity-shade-201", formId = "form-shade-canopy-audit")
    val controller = assertNotNull(state.activeFormWizardController)
    val path = "/data/plot_boundary"
    val host = state.webMapDrawing
    val before =
      mapDrawingVertices(controller.formState.fieldStates.getValue(path), MapDrawingKind.POLYGON)

    // Redrawing starts from the stored answer; Clear in the widget empties it first.
    host.startDrawing(path, MapDrawingKind.POLYGON)
    controller.setMapDrawingVertices(path, MapDrawingKind.POLYGON, emptyList())
    assertEquals(0, state.webMapDraftGeometry?.vertices?.size)

    state.addWebMapDrawingVertex(LatLng(0.0, 0.0))
    state.addWebMapDrawingVertex(LatLng(0.0, 1.0))
    assertTrue(host.isDrawing)
    val twoVertices = assertNotNull(state.webMapDraftGeometry)
    assertEquals(2, twoVertices.vertices.size)
    assertFalse(twoVertices.isComplete)
    // Two vertices draw as a line, not a polygon.
    val lineOverlay = SurveyMapContent.main(state, showNavigation = false).overlayIds()
    assertTrue(SurveyMapContent.DRAFT_SHAPE_ID in lineOverlay)
    assertEquals(
      SurveyMapContent.KIND_DRAFT_LINE,
      SurveyMapContent.draftFeatures(twoVertices)
        .first { it.id == SurveyMapContent.DRAFT_SHAPE_ID }
        .properties[SurveyMapContent.PROP_KIND],
    )

    state.addWebMapDrawingVertex(LatLng(1.0, 1.0))
    val threeVertices = assertNotNull(state.webMapDraftGeometry)
    assertTrue(threeVertices.isComplete)
    val draft = SurveyMapContent.draftFeatures(threeVertices)
    assertEquals(
      SurveyMapContent.KIND_DRAFT_POLYGON,
      draft
        .first { it.id == SurveyMapContent.DRAFT_SHAPE_ID }
        .properties[SurveyMapContent.PROP_KIND],
    )
    assertEquals(
      3,
      draft.count {
        it.properties[SurveyMapContent.PROP_KIND] == SurveyMapContent.KIND_DRAFT_VERTEX
      },
    )
    val overlayIds = SurveyMapContent.main(state, showNavigation = false).overlayIds()
    assertTrue(SurveyMapContent.draftVertexId(2) in overlayIds)

    host.undoVertex()
    assertEquals(2, state.webMapDraftGeometry?.vertices?.size)

    // Cancelling puts the question back the way it was before drawing started.
    host.cancelDrawing()
    assertFalse(host.isDrawing)
    assertEquals(
      before,
      mapDrawingVertices(controller.formState.fieldStates.getValue(path), MapDrawingKind.POLYGON),
    )

    // Done keeps what was drawn.
    host.startDrawing(path, MapDrawingKind.POLYGON)
    state.addWebMapDrawingVertex(LatLng(5.0, 5.0))
    host.stopDrawing()
    assertFalse(host.isDrawing)
    assertEquals(
      before.size + 1,
      mapDrawingVertices(controller.formState.fieldStates.getValue(path), MapDrawingKind.POLYGON)
        .size,
    )
  }

  @Test
  fun webFormGeometries_followTheOpenForm_andSkipTheOneBeingDrawn() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    assertTrue(state.webFormGeometries.isEmpty())

    // The sample form opens with a point, a line, and a polygon already answered.
    state.launchFormForEntity(entityId = "entity-shade-201", formId = "form-shade-canopy-audit")
    val controller = assertNotNull(state.activeFormWizardController)
    val byPath = state.webFormGeometries.associateBy { it.path }
    val point = assertNotNull(byPath["/data/plot_center_gps"])
    val line = assertNotNull(byPath["/data/riparian_transect"])
    val polygon = assertNotNull(byPath["/data/plot_boundary"])
    assertEquals(MapDrawingKind.POINT, point.kind)
    assertEquals(MapDrawingKind.LINE, line.kind)
    assertEquals(MapDrawingKind.POLYGON, polygon.kind)
    assertEquals(1, point.vertices.size)
    assertEquals(LatLng(-1.292066, 36.821946), point.vertices.single())
    assertEquals(2, line.vertices.size)
    assertTrue(polygon.vertices.size >= 3)
    assertEquals(controller.questionTitleFor("/data/plot_boundary"), polygon.title)
    assertTrue(polygon.title.isNotBlank())

    // Only geometry fields with a value are listed.
    controller.setMapDrawingVertices("/data/riparian_transect", MapDrawingKind.LINE, emptyList())
    assertEquals(
      setOf("/data/plot_center_gps", "/data/plot_boundary"),
      state.webFormGeometries.map { it.path }.toSet(),
    )

    // The question being drawn is left to the draft overlay, and comes back after Done.
    state.webMapDrawing.startDrawing("/data/plot_boundary", MapDrawingKind.POLYGON)
    assertEquals(listOf("/data/plot_center_gps"), state.webFormGeometries.map { it.path })
    state.webMapDrawing.stopDrawing()
    assertEquals(
      setOf("/data/plot_center_gps", "/data/plot_boundary"),
      state.webFormGeometries.map { it.path }.toSet(),
    )

    // The main map shows them as form-geometry features (above entities) with a title label.
    val map = SurveyMapContent.main(state, showNavigation = false)
    val overlay =
      map.content.sources
        .first { it.id == SurveyMapContent.OVERLAY_SOURCE }
        .features
        .associateBy { it.id }
    val pointFeature = assertNotNull(overlay[SurveyMapIds.formGeometry("/data/plot_center_gps")])
    assertEquals(
      SurveyMapContent.KIND_FORM_POINT,
      pointFeature.properties[SurveyMapContent.PROP_KIND],
    )
    val polygonFeature = assertNotNull(overlay[SurveyMapIds.formGeometry("/data/plot_boundary")])
    assertEquals(
      SurveyMapContent.KIND_FORM_POLYGON,
      polygonFeature.properties[SurveyMapContent.PROP_KIND],
    )
    assertNotNull(overlay[SurveyMapIds.formGeometryVertex("/data/plot_boundary", 0)])
    val label = map.markers[SurveyMapIds.formGeometry("/data/plot_boundary")]
    assertEquals(SurveyMarker.FormQuestion(polygon.title, MapDrawingKind.POLYGON), label)
    val layerIds = map.content.layers.map { it.id }
    assertTrue(layerIds.indexOf("form-geometry-outline") > layerIds.indexOf("entity-pins"))
    assertTrue(layerIds.indexOf("form-geometry-outline") < layerIds.indexOf("draft-outline"))

    // Closing the form clears the overlay.
    state.closeActiveFormRunner()
    assertTrue(state.webFormGeometries.isEmpty())
  }

  @Test
  fun focusWebFormQuestion_issuesRequestsWithIncreasingTokens_andClearsWithTheForm() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    state.launchFormForEntity(entityId = "entity-shade-201", formId = "form-shade-canopy-audit")
    assertNull(state.webFormFocusRequest)

    state.focusWebFormQuestion("/data/plot_boundary")
    val first = assertNotNull(state.webFormFocusRequest)
    assertEquals("/data/plot_boundary", first.path)

    // Clicking the same geometry again re-fires with a new token.
    state.focusWebFormQuestion("/data/plot_boundary")
    val second = assertNotNull(state.webFormFocusRequest)
    assertEquals(first.path, second.path)
    assertTrue(second.token > first.token)

    state.consumeWebFormFocusRequest()
    assertNull(state.webFormFocusRequest)

    state.focusWebFormQuestion("/data/plot_center_gps")
    assertNotNull(state.webFormFocusRequest)
    state.closeActiveFormRunner()
    assertNull(state.webFormFocusRequest)
  }

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
  fun frameGeometry_requestsBoundsContainingEveryVertex_withIncreasingTokens() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    val host = state.webMapDrawing
    assertTrue(host.canFrameGeometry)
    assertNull(state.webMapFramingRequest)

    // No form open: nothing to frame.
    host.frameGeometry("/data/plot_boundary", MapDrawingKind.POLYGON)
    assertNull(state.webMapFramingRequest)

    state.launchFormForEntity(entityId = "entity-shade-201", formId = "form-shade-canopy-audit")
    val controller = assertNotNull(state.activeFormWizardController)
    val polygonVertices =
      mapDrawingVertices(
        controller.formState.fieldStates.getValue("/data/plot_boundary"),
        MapDrawingKind.POLYGON,
      )
    assertTrue(polygonVertices.size >= 3)

    host.frameGeometry("/data/plot_boundary", MapDrawingKind.POLYGON)
    val polygonRequest = assertNotNull(state.webMapFramingRequest)
    polygonVertices.forEach { vertex ->
      assertTrue(LatLng(vertex.latitude, vertex.longitude) in polygonRequest.bounds, "$vertex")
    }
    assertEquals(17.0, polygonRequest.maxZoom)
    assertTrue(polygonRequest.bounds.north > polygonRequest.bounds.south)

    // A point frames as zero-size bounds at the point, zoomed in less far.
    host.frameGeometry("/data/plot_center_gps", MapDrawingKind.POINT)
    val pointRequest = assertNotNull(state.webMapFramingRequest)
    assertTrue(pointRequest.token > polygonRequest.token)
    assertEquals(LatLng(-1.292066, 36.821946), pointRequest.bounds.center)
    assertEquals(16.0, pointRequest.maxZoom)

    // Framing the same geometry again re-fires with a new token.
    host.frameGeometry("/data/plot_center_gps", MapDrawingKind.POINT)
    val again = assertNotNull(state.webMapFramingRequest)
    assertTrue(again.token > pointRequest.token)
    assertEquals(pointRequest.bounds, again.bounds)

    // An empty answer has nothing to frame and leaves the last request alone.
    controller.setMapDrawingVertices("/data/riparian_transect", MapDrawingKind.LINE, emptyList())
    host.frameGeometry("/data/riparian_transect", MapDrawingKind.LINE)
    assertEquals(again, state.webMapFramingRequest)
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
}
