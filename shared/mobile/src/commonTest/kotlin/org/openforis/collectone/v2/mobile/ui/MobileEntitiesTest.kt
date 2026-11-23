/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit
import org.openforis.collectone.v2.app.domain.model.NavigationTargetKind
import org.openforis.collectone.v2.app.domain.model.updatedAtLabel
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_collector_with_email
import org.openforis.collectone.v2.app.resources.app_collector_you
import org.openforis.collectone.v2.app.resources.app_participant_identity_hidden
import org.openforis.collectone.v2.app.resources.app_pdf_share_title_submission
import org.openforis.collectone.v2.app.ui.common.collectorAttributionText
import org.openforis.collectone.v2.app.ui.common.collectorNameText
import org.openforis.collectone.v2.app.ui.datacollection.awaitSharedPdfSheet
import org.openforis.collectone.v2.app.ui.navigation.EntityDetailsPane
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode
import org.openforis.collectone.v2.app.ui.session.searchableListUiState
import org.openforis.collectone.v2.app.ui.session.submissionGroupsOf
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/** Map features and their submissions: selection, the bottom sheet, details, and sharing. */
class MobileEntitiesTest {
  private val state =
    MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

  private fun entity(id: String) =
    state.viewModels.dataCollection.uiState.value.entities.first { it.id == id }

  private fun submittedTwice(entityId: String) {
    state.viewModels.dataCollection.launchFormForEntity(
      entityId = entityId,
      formId = "form-household-interview",
    )
    state.viewModels.dataCollection.completeActiveFormSubmission()
    state.viewModels.dataCollection.launchFormForEntity(
      entityId = entityId,
      formId = "form-eudr-baseline",
    )
    state.viewModels.dataCollection.completeActiveFormSubmission()
  }

  // --- Marker progression and geometry ---

  @Test
  fun entityWithoutSubmissions_showsThePendingMarker() {
    val nyr112 = entity(NYR_112)

    assertEquals(GeometryType.POLYGON, nyr112.geometry?.type)
    assertEquals("○", nyr112.markerSymbol)
    assertEquals("#E65100", nyr112.markerColorCss)
    assertEquals("Pending", nyr112.workflowStatus)
    assertEquals("○ Pending", nyr112.mapStatusSummaryBadge)
    assertEquals(0, nyr112.submissions.size)
  }

  @Test
  fun entityWithOneOfTwoForms_showsTheInProgressMarker() {
    val nyr108 = entity(NYR_108)

    assertEquals("◐", nyr108.markerSymbol)
    assertEquals("#F9AB00", nyr108.markerColorCss)
    assertEquals("◐ In progress", nyr108.mapStatusSummaryBadge)
    assertEquals(1, nyr108.submissions.size)
  }

  @Test
  fun completedEntity_showsTheCheckmarkMarker() {
    val nyr104 = entity(NYR_104)

    assertEquals("✓", nyr104.markerSymbol)
    assertEquals("#1E8E3E", nyr104.markerColorCss)
    assertEquals("✓ Completed", nyr104.mapStatusSummaryBadge)
    assertEquals("Maya Lin", nyr104.submissions.single().collectorName)
  }

  @Test
  fun lineAndPointEntities_reportTheirGeometryType() {
    assertEquals(GeometryType.LINE_STRING, entity(SHADE_201).geometry?.type)
    assertEquals(GeometryType.POINT, entity(STATION_01).geometry?.type)
  }

  @Test
  fun firstSubmission_movesThePendingMarkerToInProgress() {
    state.viewModels.dataCollection.launchFormForEntity(
      entityId = NYR_112,
      formId = "form-household-interview",
    )

    state.viewModels.dataCollection.completeActiveFormSubmission()

    assertEquals("◐", entity(NYR_112).markerSymbol)
    assertEquals("In progress", entity(NYR_112).workflowStatus)
  }

  @Test
  fun secondSubmission_movesTheMarkerToCompleted() {
    submittedTwice(NYR_112)

    assertEquals("✓", entity(NYR_112).markerSymbol)
    assertEquals("Completed", entity(NYR_112).workflowStatus)
    assertEquals(2, entity(NYR_112).submissions.size)
  }

  // --- Forms of a map feature ---

  @Test
  fun formsForEntity_listsEveryFormOfItsDataset() {
    val forms = state.viewModels.dataCollection.uiState.value.formsForEntity(entity(NYR_104))

    assertEquals(
      listOf("Register baseline parcel", "Interview household"),
      forms.map { it.ctaLabel },
    )
  }

  @Test
  fun formButtons_stayEnabledForCompletedEntities() {
    val nyr104 = entity(NYR_104)

    assertTrue(
      state.viewModels.dataCollection.uiState.value.formsForEntity(nyr104).all {
        state.viewModels.dataCollection.uiState.value.isFormButtonEnabled(nyr104, it)
      }
    )
  }

  // --- Selection and the bottom sheet ---

  @Test
  fun noEntityIsSelectedAtFirst() {
    assertNull(state.viewModels.dataCollection.uiState.value.selectedEntity)
    assertFalse(state.viewModels.surveyMap.uiState.value.isEntityBottomSheetExpanded)
  }

  @Test
  fun selectEntity_opensItsSheetCollapsed() {
    state.viewModels.surveyMap.selectEntity(NYR_104)

    assertEquals(NYR_104, state.viewModels.dataCollection.uiState.value.selectedEntityId)
    assertFalse(state.viewModels.surveyMap.uiState.value.isEntityBottomSheetExpanded)
  }

  @Test
  fun toggleEntityBottomSheetExpanded_expandsTheSheet() {
    state.viewModels.surveyMap.selectEntity(NYR_104)

    state.viewModels.surveyMap.toggleEntityBottomSheetExpanded()

    assertTrue(state.viewModels.surveyMap.uiState.value.isEntityBottomSheetExpanded)
  }

  @Test
  fun collapsingTheSheet_keepsTheEntitySelected() {
    state.viewModels.surveyMap.selectEntity(NYR_104)
    state.viewModels.surveyMap.updateEntityBottomSheetExpanded(true)

    state.viewModels.surveyMap.updateEntityBottomSheetExpanded(false)

    assertFalse(state.viewModels.surveyMap.uiState.value.isEntityBottomSheetExpanded)
    assertEquals(NYR_104, state.viewModels.dataCollection.uiState.value.selectedEntityId)
  }

  @Test
  fun selectingAnotherEntity_opensItsSheetCollapsed() {
    state.viewModels.surveyMap.selectEntity(NYR_104)
    state.viewModels.surveyMap.updateEntityBottomSheetExpanded(true)

    state.viewModels.surveyMap.selectEntity(SHADE_201)

    assertEquals(SHADE_201, state.viewModels.dataCollection.uiState.value.selectedEntityId)
    assertFalse(state.viewModels.surveyMap.uiState.value.isEntityBottomSheetExpanded)
  }

  @Test
  fun clearingTheSelection_collapsesTheSheet() {
    state.viewModels.surveyMap.selectEntity(NYR_104)
    state.viewModels.surveyMap.updateEntityBottomSheetExpanded(true)

    state.viewModels.surveyMap.selectEntity(null)

    assertNull(state.viewModels.dataCollection.uiState.value.selectedEntityId)
    assertFalse(state.viewModels.surveyMap.uiState.value.isEntityBottomSheetExpanded)
  }

  @Test
  fun listMode_expandsTheSheetWithNothingSelected() {
    state.setMainSurveyViewMode(MainSurveyViewMode.LIST)

    assertEquals(MainSurveyViewMode.LIST, state.mainViewMode)
    assertTrue(state.viewModels.surveyMap.uiState.value.isEntityBottomSheetExpanded)
    assertNull(state.viewModels.dataCollection.uiState.value.selectedEntityId)
  }

  @Test
  fun selectEntityFromList_showsItsPropertiesAtPeekHeight() {
    state.setMainSurveyViewMode(MainSurveyViewMode.LIST)

    state.searchableListActions.selectEntityFromList(NYR_104)

    assertEquals(NYR_104, state.viewModels.dataCollection.uiState.value.selectedEntityId)
    assertFalse(state.viewModels.surveyMap.uiState.value.isEntityBottomSheetExpanded)
    assertEquals(
      EntityDetailsPane.PROPERTIES,
      state.viewModels.dashboard.uiState.value.entityDetailsPane,
    )
  }

  @Test
  fun returnToBottomSheetList_clearsTheSelectionAndShowsTheExpandedList() {
    state.setMainSurveyViewMode(MainSurveyViewMode.LIST)
    state.searchableListActions.selectEntityFromList(NYR_104)

    state.viewModels.dataCollection.returnToBottomSheetList()

    assertNull(state.viewModels.dataCollection.uiState.value.selectedEntityId)
    assertNull(state.viewModels.dataCollection.uiState.value.selectedSubmissionId)
    assertTrue(state.viewModels.surveyMap.uiState.value.isEntityBottomSheetExpanded)
    assertEquals(MainSurveyViewMode.LIST, state.mainViewMode)
  }

  // --- Entity details and submissions ---

  @Test
  fun selectSubmissionDetail_opensTheSubmission() {
    state.viewModels.dataCollection.selectSubmissionDetail(SAPLING_SUBMISSION)

    val detail = assertNotNull(state.viewModels.dataCollection.uiState.value.selectedSubmission)
    assertEquals("Maya Lin", detail.collectorName)
    assertEquals("2026-09-19 08:45 UTC", detail.updatedAtLabel)
    assertTrue(detail.fields.any { it.questionName == "surviving_saplings_count" })
  }

  @Test
  fun entityDetails_openOnProperties() {
    state.viewModels.surveyMap.selectEntity(SHADE_201)

    assertEquals(
      EntityDetailsPane.PROPERTIES,
      state.viewModels.dashboard.uiState.value.entityDetailsPane,
    )
  }

  @Test
  fun showEntitySubmissions_switchesToTheSubmissionsPane() {
    state.viewModels.surveyMap.selectEntity(SHADE_201)

    state.viewModels.dashboard.showEntitySubmissions()

    assertEquals(
      EntityDetailsPane.SUBMISSIONS,
      state.viewModels.dashboard.uiState.value.entityDetailsPane,
    )
  }

  @Test
  fun closingASubmission_returnsToTheSubmissionsPane() {
    state.viewModels.surveyMap.selectEntity(SHADE_201)
    state.viewModels.dashboard.showEntitySubmissions()
    state.viewModels.dataCollection.selectSubmissionDetail(entity(SHADE_201).submissions.first().id)

    state.viewModels.dataCollection.selectSubmissionDetail(null)

    assertEquals(
      EntityDetailsPane.SUBMISSIONS,
      state.viewModels.dashboard.uiState.value.entityDetailsPane,
    )
  }

  @Test
  fun showEntityProperties_returnsToThePropertiesPane() {
    state.viewModels.surveyMap.selectEntity(SHADE_201)
    state.viewModels.dashboard.showEntitySubmissions()

    state.viewModels.dataCollection.showEntityProperties()

    assertEquals(
      EntityDetailsPane.PROPERTIES,
      state.viewModels.dashboard.uiState.value.entityDetailsPane,
    )
  }

  @Test
  fun selectingAnotherEntity_opensOnItsProperties() {
    state.viewModels.surveyMap.selectEntity(SHADE_201)
    state.viewModels.dashboard.showEntitySubmissions()

    state.viewModels.surveyMap.selectEntity(NYR_104)

    assertEquals(
      EntityDetailsPane.PROPERTIES,
      state.viewModels.dashboard.uiState.value.entityDetailsPane,
    )
  }

  @Test
  fun openingSubmissionOfAnotherEntity_framesThatEntity() {
    val entities =
      state.viewModels.dataCollection.uiState.value.entities.filter { it.submissions.isNotEmpty() }
    state.viewModels.surveyMap.selectEntity(entities[0].id)
    val epoch = state.viewModels.surveyMap.uiState.value.entitySelectionEpoch

    state.viewModels.dataCollection.selectSubmissionDetail(entities[1].submissions.first().id)

    assertEquals(entities[1].id, state.viewModels.dataCollection.uiState.value.selectedEntityId)
    assertEquals(epoch + 1, state.viewModels.surveyMap.uiState.value.entitySelectionEpoch)
  }

  @Test
  fun groupedSubmissionsForEntity_groupsByFormTitle() {
    val groups = state.surveyRecords.submissionGroupsOf(entity(SHADE_201))

    assertEquals(
      listOf("Seasonal Shade Tree & Canopy Audit", "GLAD Canopy Disturbance Alert Verification"),
      groups.map { it.formTitle },
    )
    assertEquals(listOf(2, 1), groups.map { it.submissions.size })
  }

  @Test
  fun relatedEntityForPropertyValue_resolvesAReferenceById() {
    val parcel =
      state.viewModels.dataCollection.uiState.value.entities.first {
        it.properties["Washing Station"] != null
      }
    val station = entity(parcel.properties.getValue("Washing Station"))

    assertEquals(
      station,
      state.viewModels.dashboard.uiState.value.relatedEntityForPropertyValue(parcel, station.id),
    )
  }

  @Test
  fun relatedEntityForPropertyValue_resolvesAReferenceByGeoId() {
    val parcel =
      state.viewModels.dataCollection.uiState.value.entities.first {
        it.properties["Washing Station"] != null
      }
    val station = entity(parcel.properties.getValue("Washing Station"))

    assertEquals(
      station,
      state.viewModels.dashboard.uiState.value.relatedEntityForPropertyValue(parcel, station.geoId),
    )
  }

  @Test
  fun relatedEntityForPropertyValue_ignoresPlainValues() {
    val parcel =
      state.viewModels.dataCollection.uiState.value.entities.first {
        it.properties["Washing Station"] != null
      }

    assertNull(
      state.viewModels.dashboard.uiState.value.relatedEntityForPropertyValue(
        parcel,
        "Othaya Farmers Co-op",
      )
    )
  }

  @Test
  fun relatedEntityForPropertyValue_neverLinksARecordToItself() {
    val parcel =
      state.viewModels.dataCollection.uiState.value.entities.first {
        it.properties["Washing Station"] != null
      }

    assertNull(
      state.viewModels.dashboard.uiState.value.relatedEntityForPropertyValue(parcel, parcel.id)
    )
  }

  // --- Layers sheet ---

  @Test
  fun entityDatasetLayers_listTheSurveysMapLayers() {
    assertEquals(
      listOf(
        "Smallholder Coffee Parcels",
        "Shade Tree Monitoring Plots",
        "Cooperative Washing Stations",
      ),
      state.viewModels.dashboard.uiState.value.entityDatasetLayers.map { it.label },
    )
  }

  @Test
  fun surveyWithoutMapFeatures_hasNoMapLayersInTheLayersSheet() {
    val noEntityState =
      MobileAppState(dataHolder = sampleDataHolder()).apply {
        viewModels.onboarding.openSurvey("00000036-1a1c-5c51-997f-28b67d813aa3")
        dataHolder.launch { dataHolder.surveyRepository.setEntities(emptyList()) }
      }

    assertTrue(noEntityState.viewModels.dataCollection.uiState.value.entities.isEmpty())
    assertTrue(noEntityState.viewModels.dashboard.uiState.value.entityDatasetLayers.isEmpty())
  }

  @Test
  fun statusFilter_filtersTargetDatasetOnMobileWhenStatusIsUsedAndHidesWhenNotUsed() {
    val initialList = state.surveyRecords.searchableListUiState(state.viewModels.surveyMap)
    assertTrue(initialList.usesEntityStatus)
    val parcelsGroup = initialList.entityGroups.first { it.datasetId == "coffee_parcels" }
    assertEquals(
      listOf("○ Pending", "◐ In progress", "✓ Completed"),
      parcelsGroup.statusFilterOptions.map { it.badgeText },
    )

    state.searchableListActions.toggleListStatusFilter("coffee_parcels", "Pending")
    val filteredList = state.surveyRecords.searchableListUiState(state.viewModels.surveyMap)
    assertEquals("Pending", filteredList.activeStatusFilters["coffee_parcels"])
    assertEquals(
      listOf(NYR_112),
      filteredList.entityGroups.first { it.datasetId == "coffee_parcels" }.entities.map { it.id },
    )
    // Other layers remain visible while filtering coffee_parcels.
    assertEquals(
      setOf(NYR_112, SHADE_201, STATION_01),
      filteredList.entityGroups.flatMap { it.entities }.map { it.id }.toSet(),
    )

    state.viewModels.onboarding.openSurvey("00000036-1a1c-5c51-997f-28b67d813aa3")
    val noStatusList = state.surveyRecords.searchableListUiState(state.viewModels.surveyMap)
    assertFalse(noStatusList.usesEntityStatus)
    assertTrue(noStatusList.activeStatusFilters.isEmpty())
    assertTrue(noStatusList.entityGroups.all { it.statusFilterOptions.isEmpty() })
    assertEquals(3, noStatusList.entityGroups.flatMap { it.entities }.size)
  }

  // --- QR code, GNSS, and PDF sharing ---

  @Test
  fun gnssStatusChip_showsTheAccuracy() {
    assertEquals(18, state.viewModels.surveyMap.uiState.value.gnssSatelliteCount)
    assertEquals(
      Measurement(2.1, MeasurementUnit.METER, decimals = null),
      state.viewModels.surveyMap.uiState.value.gnssAccuracy,
    )
  }

  @Test
  fun openEntityQrCode_opensTheEntitysQrCode() {
    state.viewModels.dataCollection.openEntityQrCode(NYR_104)

    assertEquals(NYR_104, state.viewModels.dataCollection.uiState.value.activeQrCodeEntity?.id)
  }

  @Test
  fun closeEntityQrCode_closesTheQrCode() {
    state.viewModels.dataCollection.openEntityQrCode(NYR_104)

    state.viewModels.dataCollection.closeEntityQrCode()

    assertNull(state.viewModels.dataCollection.uiState.value.activeQrCodeEntity)
  }

  @Test
  fun shareEntityPdf_namesThePdfAfterTheGeoId() = runTest {
    state.viewModels.dataCollection.shareEntityPdf(NYR_104)

    val pdfSheet = state.viewModels.dataCollection.awaitSharedPdfSheet()
    assertTrue(pdfSheet.pdfFileName.contains("S2-10c4a89e2f"))
  }

  @Test
  fun shareSubmissionPdf_namesThePdfAfterTheSubmission() = runTest {
    state.viewModels.dataCollection.shareSubmissionPdf(EUDR_SUBMISSION)

    val pdfSheet = state.viewModels.dataCollection.awaitSharedPdfSheet()
    assertTrue(pdfSheet.pdfFileName.contains(EUDR_SUBMISSION))
  }

  @Test
  fun shareSubmissionPdf_titlesTheSheetWithTheFormTitle() = runTest {
    state.viewModels.dataCollection.shareSubmissionPdf(EUDR_SUBMISSION)

    val pdfSheet = state.viewModels.dataCollection.awaitSharedPdfSheet()
    assertEquals(
      UiText.Resource(
        Res.string.app_pdf_share_title_submission,
        listOf("EUDR Parcel Baseline Registration"),
      ),
      pdfSheet.shareTitle,
    )
  }

  @Test
  fun closeSharePdfSheet_closesTheSheet() = runTest {
    state.viewModels.dataCollection.shareEntityPdf(NYR_104)
    state.viewModels.dataCollection.awaitSharedPdfSheet()

    state.viewModels.dataCollection.closeSharePdfSheet()

    assertNull(state.viewModels.dataCollection.uiState.value.activeSharedPdfSheet)
  }

  // --- Standalone submissions ---

  @Test
  fun standaloneSubmissions_haveNoAttachedEntity() {
    val pestSub =
      state.viewModels.dataCollection.uiState.value.standaloneSubmissions.first {
        it.id == PEST_SUBMISSION
      }

    assertEquals(2, state.viewModels.dataCollection.uiState.value.standaloneSubmissions.size)
    assertFalse(pestSub.hasAttachedEntity)
    assertEquals("", pestSub.entityLabel)
    assertEquals("", pestSub.targetTypeNoun)
  }

  @Test
  fun allSubmissions_includeTheStandaloneSubmissions() {
    assertTrue(
      state.viewModels.dataCollection.uiState.value.allSubmissions.any { it.id == PEST_SUBMISSION }
    )
    assertTrue(
      state.viewModels.dataCollection.uiState.value.allSubmissions.any {
        it.id == EROSION_SUBMISSION
      }
    )
  }

  @Test
  fun selectingAStandaloneSubmission_opensItWithoutAnEntity() {
    state.viewModels.dataCollection.selectSubmissionDetail(PEST_SUBMISSION)

    assertNull(state.viewModels.dataCollection.uiState.value.selectedEntity)
    assertEquals(
      PEST_SUBMISSION,
      state.viewModels.dataCollection.uiState.value.selectedSubmission?.id,
    )
  }

  @Test
  fun selectSubmissionGeometry_opensTheStandaloneSubmissionAndClearsTheEntity() {
    state.viewModels.surveyMap.selectEntity(NYR_104)

    state.viewModels.dataCollection.selectSubmissionGeometry("geom-sub-standalone-pest-01")

    assertNull(state.viewModels.dataCollection.uiState.value.selectedEntityId)
    assertEquals(
      PEST_SUBMISSION,
      state.viewModels.dataCollection.uiState.value.selectedSubmission?.id,
    )
  }

  @Test
  fun startNavigationToSubmission_navigatesToAStandaloneSubmission() {
    state.viewModels.surveyMap.startNavigationToSubmission(EROSION_SUBMISSION)

    val nav = assertNotNull(state.viewModels.surveyMap.uiState.value.activeNavigation)
    assertEquals(NavigationTargetKind.SUBMISSION, nav.targetKind)
    assertEquals(EROSION_SUBMISSION, nav.targetId)
    assertEquals(
      EROSION_SUBMISSION,
      state.viewModels.dataCollection.uiState.value.selectedSubmissionId,
    )
    assertNull(state.viewModels.dataCollection.uiState.value.selectedEntityId)
  }

  @Test
  fun launchingAStandaloneFormFromTheFab_skipsTheEntityStep() {
    state.viewModels.dataCollection.launchFormFromFab("form-pest-disease-sighting")

    assertTrue(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)
    assertNull(state.viewModels.dataCollection.uiState.value.activeDataCollectionEntityId)
    assertFalse(state.viewModels.dataCollection.uiState.value.isCurrentFormStepEntityRef)
  }

  @Test
  fun submittingAStandaloneForm_recordsAndOpensAStandaloneSubmission() {
    state.viewModels.dataCollection.launchFormFromFab("form-pest-disease-sighting")

    state.viewModels.dataCollection.completeActiveFormSubmission()

    val newlyCreated = state.viewModels.dataCollection.uiState.value.standaloneSubmissions.first()
    assertFalse(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)
    assertEquals(3, state.viewModels.dataCollection.uiState.value.standaloneSubmissions.size)
    assertFalse(newlyCreated.hasAttachedEntity)
    assertEquals(
      newlyCreated.id,
      state.viewModels.dataCollection.uiState.value.selectedSubmissionId,
    )
    assertNull(state.viewModels.dataCollection.uiState.value.selectedEntityId)
  }

  // --- Contributor privacy ---

  private val mayaSubmission
    get() = entity(SHADE_201).submissions.first { it.collectorEmail == MAYA_EMAIL }

  private val samuelSubmission
    get() = entity(SHADE_201).submissions.first { it.collectorEmail == SAMUEL_EMAIL }

  @Test
  fun signedInOrganizer_isAnOrganizerViewer() {
    assertTrue(state.activeSurveyAccess.submissionViewer.isOrganizer)
  }

  @Test
  fun organizer_seesOtherContributorsNames() {
    assertEquals(
      UiText.Plain("Samuel Kariuki"),
      collectorNameText(state.activeSurveyAccess.submissionViewer, samuelSubmission),
    )
  }

  @Test
  fun organizer_seesOtherContributorsEmails() {
    assertEquals(
      UiText.Resource(Res.string.app_collector_with_email, listOf("Samuel Kariuki", SAMUEL_EMAIL)),
      collectorAttributionText(state.activeSurveyAccess.submissionViewer, samuelSubmission),
    )
  }

  @Test
  fun signedInMember_isNotAnOrganizerViewer() {
    state.viewModels.onboarding.openSurvey(MEMBER_SURVEY)

    assertFalse(state.activeSurveyAccess.submissionViewer.isOrganizer)
  }

  @Test
  fun member_seesTheirOwnSubmissionsAsYou() {
    state.viewModels.onboarding.openSurvey(MEMBER_SURVEY)

    assertEquals(
      UiText.Resource(Res.string.app_collector_you, listOf("Maya Lin")),
      collectorNameText(state.activeSurveyAccess.submissionViewer, mayaSubmission),
    )
  }

  @Test
  fun member_doesNotSeeOtherContributors() {
    state.viewModels.onboarding.openSurvey(MEMBER_SURVEY)

    assertEquals(
      UiText.Resource(Res.string.app_participant_identity_hidden),
      collectorAttributionText(state.activeSurveyAccess.submissionViewer, samuelSubmission),
    )
  }

  private companion object {
    const val NYR_104 = "00000007-640e-5ed7-9874-4d6b6717f21d"
    const val NYR_108 = "00000008-15d0-51f2-84eb-a72dfea5a747"
    const val NYR_112 = "00000009-1699-51bb-b7a3-f90a60bedd4d"
    const val SHADE_201 = "0000000a-56d7-5c1d-9457-7457267120a3"
    const val STATION_01 = "0000000b-7633-5116-9d6d-50581002d8cf"
    const val SAPLING_SUBMISSION = "00000026-cca2-5594-b83f-02266f40048e"
    const val EUDR_SUBMISSION = "00000022-c99b-5e7e-b810-c506ebf06f19"
    const val PEST_SUBMISSION = "0000002e-4118-5239-bcc9-78dda11063f5"
    const val EROSION_SUBMISSION = "0000002d-a881-53e1-90c1-b3e3ad027756"

    /** A survey where the signed-in user (Maya Lin) is a member, not an organizer. */
    const val MEMBER_SURVEY = "00000035-caf4-571e-b907-f506f92666ab"
    const val MAYA_EMAIL = "maya.lin@collect-one.example.org"
    const val SAMUEL_EMAIL = "s.kariuki@kenyaforestry.org"
  }
}
