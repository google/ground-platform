/*
 * IGNORE_COPYRIGHT: Ground is a Google-developed open-source project (The Ground Authors)
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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrototypeAppStateTest {

  @Test
  fun initialState_startsOnSplashWithSampleSurveys() {
    val state = PrototypeAppState()

    assertEquals(PrototypeScreen.SPLASH, state.currentScreen)
    assertFalse(state.isSignedIn)
    assertFalse(state.hasAcceptedTerms)
    assertEquals(6, state.surveys.size)
    assertEquals(6, state.filteredSurveys.size)
    assertTrue(state.downloadedSurveyCount >= 1)
  }

  @Test
  fun onboardingFlow_advancesThroughSplashSignInTermsAndDownloadSurvey() {
    val state = PrototypeAppState()

    // 1. Splash -> Sign In
    state.completeSplashLoading()
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
    state.completeSplashLoading()
    state.signInWithGoogle()
    assertEquals(PrototypeScreen.TERMS_OF_SERVICE, state.currentScreen)

    state.declineTermsOfService()
    assertEquals(PrototypeScreen.SIGN_IN, state.currentScreen)
    assertFalse(state.isSignedIn)
    assertFalse(state.hasAcceptedTerms)
  }

  @Test
  fun searchQuery_filtersSurveysByNameAndLocation() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.DOWNLOAD_SURVEY)

    // Search by survey title keyword
    state.updateSearchQuery("Mangrove")
    assertEquals(1, state.filteredSurveys.size)
    assertEquals("Mekong Delta Mangrove Restoration", state.filteredSurveys.first().title)

    // Search by location (Country / Region)
    state.updateSearchQuery("Kenya")
    assertEquals(1, state.filteredSurveys.size)
    assertEquals("Nyeri County, Kenya", state.filteredSurveys.first().location)

    // Search by another location
    state.updateSearchQuery("Brazil")
    assertEquals(1, state.filteredSurveys.size)
    assertEquals("Amazon Basin Deforestation Monitoring", state.filteredSurveys.first().title)

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
  fun openSurvey_navigatesToMainSurveyScreenAndSelectsDefault1To1Entity() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.DOWNLOAD_SURVEY)
    state.openSurvey("survey-kenya-coffee")

    assertEquals(PrototypeScreen.MAIN_SURVEY, state.currentScreen)
    assertEquals(MainSurveyViewMode.MAP, state.mainViewMode)
    assertEquals("survey-kenya-coffee", state.activeSurvey.id)
    assertEquals("entity-nyr-104", state.selectedEntity?.id)
    assertEquals(SubmissionModel.SINGLE_1_TO_1, state.selectedEntity?.submissionModel)
  }

  @Test
  fun toggleLayerVisibility_filtersVisibleMapEntitiesAndDeselectsHiddenEntity() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    assertEquals(4, state.visibleMapEntities.size)
    assertEquals("entity-nyr-104", state.selectedEntityId)

    // Hide the "Smallholder Coffee Parcels" layer (`layer-coffee-parcels`)
    state.toggleLayerVisibility("layer-coffee-parcels")

    // The 2 entities on `layer-coffee-parcels` should hide
    assertEquals(2, state.visibleMapEntities.size)
    assertEquals(null, state.selectedEntity)

    // Re-enable layer
    state.toggleLayerVisibility("layer-coffee-parcels")
    assertEquals(4, state.visibleMapEntities.size)
  }

  @Test
  fun select1To1And1ToNEntities_supportsInlineDataAndSubmissionDetailInspection() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

    // 1. Select 1:1 entity with inline submission data (`entity-nyr-104`)
    state.selectEntity("entity-nyr-104")
    val oneToOneEntity = state.selectedEntity!!
    assertEquals(SubmissionModel.SINGLE_1_TO_1, oneToOneEntity.submissionModel)
    assertEquals(1, oneToOneEntity.submissions.size)
    assertEquals("Maya Lin", oneToOneEntity.submissions.first().collectorName)

    // 2. Select 1:N entity with multiple chronological submissions (`entity-shade-201`)
    state.selectEntity("entity-shade-201")
    val oneToManyEntity = state.selectedEntity!!
    assertEquals(SubmissionModel.MULTIPLE_1_TO_N, oneToManyEntity.submissionModel)
    assertEquals(3, oneToManyEntity.submissions.size)
    assertEquals(null, state.selectedSubmission)

    // Click a specific submission in the 1:N list to view its full submission details
    state.selectSubmissionDetail("sub-shade-201-wave3")
    val detail = state.selectedSubmission!!
    assertEquals("Maya Lin", detail.collectorName)
    assertEquals("2026-09-19 08:45 UTC", detail.timestamp)
    assertTrue(detail.fields.any { it.questionName == "surviving_saplings_count" })
  }

  @Test
  fun listViewSearch_groupsSubmissionsByFormAndFiltersEntitiesAndSubmissions() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    state.setMainSurveyViewMode(MainSurveyViewMode.LIST)

    // ListFilterTab only contains ALL, ENTITIES, SUBMISSIONS (Forms are a grouping for Submissions)
    assertEquals(
      listOf(ListFilterTab.ALL, ListFilterTab.ENTITIES, ListFilterTab.SUBMISSIONS),
      ListFilterTab.entries,
    )

    // Unfiltered counts: 4 entities, 7 submissions grouped across 5 forms
    assertEquals(4, state.filteredListEntities.size)
    assertEquals(7, state.filteredListSubmissions.size)
    assertEquals(5, state.groupedFilteredListSubmissions.size)

    // Search for "Grevillea" (matches entity submission field values)
    state.updateListSearchQuery("Grevillea")
    assertTrue(state.filteredListSubmissions.isNotEmpty())
    assertTrue(state.groupedFilteredListSubmissions.isNotEmpty())

    // Search for "Kamau" (matches entity label & owner property)
    state.updateListSearchQuery("Kamau")
    assertEquals(1, state.filteredListEntities.size)
    assertEquals("Plot NYR-104 • Kamau Family Parcel", state.filteredListEntities.first().label)

    state.clearListSearchQuery()
    assertEquals(7, state.filteredListSubmissions.size)
  }

  @Test
  fun entityBottomSheetFormButtons_showOrganizerCtaLabels_andDisableWhenOneToOneSubmissionExists() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

    // 1:1 entity `entity-nyr-104` belongs to `coffee_parcels` which is requested by 2 forms:
    // - `form-eudr-baseline` ("Register baseline parcel") -> already has a submission -> disabled
    // - `form-household-interview` ("Interview household") -> no submission yet -> enabled
    val nyr104 = state.entities.first { it.id == "entity-nyr-104" }
    val nyr104Forms = state.formsForEntity(nyr104)
    assertEquals(2, nyr104Forms.size)
    assertEquals(
      listOf("Register baseline parcel", "Interview household"),
      nyr104Forms.map { it.ctaLabel },
    )
    val baselineForm = nyr104Forms.first { it.id == "form-eudr-baseline" }
    val interviewForm = nyr104Forms.first { it.id == "form-household-interview" }
    assertFalse(state.isFormButtonEnabled(nyr104, baselineForm))
    assertTrue(state.isFormButtonEnabled(nyr104, interviewForm))

    // 1:N entity `entity-shade-201` belongs to `shade_monitoring_plots` which is requested by:
    // - `form-shade-canopy-audit` ("Record canopy audit") -> 1:N -> enabled even with existing submissions
    // - `form-deforestation-alert` ("Validate alert") -> 1:N -> enabled
    val shade201 = state.entities.first { it.id == "entity-shade-201" }
    val shadeForms = state.formsForEntity(shade201)
    assertEquals(
      listOf("Record canopy audit", "Validate alert"),
      shadeForms.map { it.ctaLabel },
    )
    assertTrue(shadeForms.all { state.isFormButtonEnabled(shade201, it) })
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
    assertEquals(2, state.downloadedSurveys.size)

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
    assertEquals("18 sats • ±2.1 m", state.gnssStatusChipLabel)

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
  fun submissionGeometryLayersAndOfflineBasemap_toggleIndependentlyViaLayersDialog() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)

    // 3 entity dataset layers (solid) + 3 form geometry layers (dotted polygons)
    assertEquals(3, state.entityDatasetLayers.size)
    assertEquals(3, state.formGeometryLayers.size)
    assertTrue(state.formGeometryLayers.all { it.isDottedOutline })
    assertEquals(4, state.visibleSubmissionGeometries.size)

    // Toggle off the "Walked Parcel Perimeters" form geometry layer (`layer-form-walked-perimeter`)
    state.toggleLayerVisibility("layer-form-walked-perimeter")
    assertEquals(2, state.visibleSubmissionGeometries.size)

    // Clicking a dotted submission geometry on the map selects its parent entity and submission
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

    // Starts collapsed (compact peek bar) by default so it does not obscure the map
    assertEquals("entity-nyr-104", state.selectedEntityId)
    assertFalse(state.isEntityBottomSheetExpanded)

    // Toggling expands the bottom sheet
    state.toggleEntityBottomSheetExpanded()
    assertTrue(state.isEntityBottomSheetExpanded)

    // Explicit collapse returns to peek state without deselecting the entity
    state.updateEntityBottomSheetExpanded(false)
    assertFalse(state.isEntityBottomSheetExpanded)
    assertEquals("entity-nyr-104", state.selectedEntityId)

    // Selecting an entity explicitly expands the bottom sheet
    state.selectEntity("entity-shade-201")
    assertEquals("entity-shade-201", state.selectedEntityId)
    assertTrue(state.isEntityBottomSheetExpanded)

    // Closing the sheet deselects the entity and resets expanded state
    state.selectEntity(null)
    assertEquals(null, state.selectedEntityId)
    assertFalse(state.isEntityBottomSheetExpanded)
  }
}


