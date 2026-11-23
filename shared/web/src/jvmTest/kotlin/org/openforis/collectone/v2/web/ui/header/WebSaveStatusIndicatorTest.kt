/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.web.ui.header

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.client.offline.UnavailableOfflineRegionClient
import org.openforis.collectone.v2.app.data.datasource.local.store.SampleDeviceStorage
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.SaveStatus
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionUiState
import org.openforis.collectone.v2.web.WebConsoleApp
import org.openforis.collectone.v2.web.navigation.AdminRoute
import org.openforis.collectone.v2.web.navigation.MemoryBrowserHistory
import org.openforis.collectone.v2.web.navigation.OrganizationRoute
import org.openforis.collectone.v2.web.navigation.SurveyDashboardRoute
import org.openforis.collectone.v2.web.navigation.WebUrls
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.common.WebHeaderContext
import org.openforis.collectone.v2.web.ui.organization

/**
 * The header's save status indicator on the survey map page ([WebTopToolbar]) and on the admin and
 * organization pages ([WebAppHeader]): "Saving…" only while saving, "Not saved" while edits wait to
 * retry, "Saved" briefly after a save finishes without failing, and nothing while idle.
 */
@OptIn(ExperimentalTestApi::class)
class WebSaveStatusIndicatorTest {
  private object NoHeaderActions : WebAppHeaderActions {
    override fun openSurveys() = Unit

    override fun updateLanguage(languageCode: String) = Unit

    override fun openOrganizations() = Unit

    override fun openAdminPanel() = Unit
  }

  /** The survey map page's toolbar showing [saveStatus]. */
  @Composable
  private fun MapToolbar(state: WebAppState, saveStatus: SaveStatus) {
    WebTopToolbar(
      header = WebAppHeaderUiState(saveStatus = saveStatus),
      headerActions = NoHeaderActions,
      survey =
        WebSurveyToolbarUiState(
          activeSurveyId = "s1",
          activeSurvey =
            SurveyPreviewItem(
              id = "s1",
              title = "Mangrove plots",
              description = "",
              location = "Kenya",
              isDownloaded = true,
              entityCount = 0,
            ),
        ),
      dataCollection = DataCollectionUiState(),
      dataCollectionActions = state.viewModels.dataCollection,
      onOpenSurvey = {},
      onSelectLifecycleState = {},
      onOpenSurveyEditor = {},
      onSignOut = {},
    )
  }

  /** The admin panel's header showing [saveStatus]. */
  @Composable
  private fun AdminHeader(saveStatus: SaveStatus) {
    WebAppHeader(
      uiState = WebAppHeaderUiState(saveStatus = saveStatus, isPlatformAdmin = true),
      headerActions = NoHeaderActions,
      onSignOut = {},
      context = { WebHeaderContext(title = "Platform administration") },
    )
  }

  private fun ComposeUiTest.assertShowsNoSaveStatus() {
    onNodeWithText(SAVING).assertDoesNotExist()
    onNodeWithText(NOT_SAVED).assertDoesNotExist()
    onNodeWithText(SAVED).assertDoesNotExist()
  }

  private fun ComposeUiTest.assertShowsOnly(text: String) {
    onNodeWithText(text).assertIsDisplayed()
    listOf(SAVING, NOT_SAVED, SAVED)
      .filter { it != text }
      .forEach { onNodeWithText(it).assertDoesNotExist() }
  }

  /**
   * Drives a header through idle → saving → saved → idle, saving → failed (no "Saved") → idle, and
   * failed → saving → saved, with the test's clock under manual control (the spinner animates
   * forever).
   */
  private fun ComposeUiTest.checkSaveStatusTransitions(header: @Composable (SaveStatus) -> Unit) {
    mainClock.autoAdvance = false
    var status by mutableStateOf(SaveStatus.IDLE)
    setContent { header(status) }
    fun moveTo(next: SaveStatus) {
      status = next
      // One frame to recompose and start the status effect, another to show what it decided.
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeByFrame()
    }
    mainClock.advanceTimeByFrame()

    // Idle: nothing, however long it stays idle (no spinner "for no reason").
    assertShowsNoSaveStatus()
    mainClock.advanceTimeBy(60_000L)
    assertShowsNoSaveStatus()

    // Saving, then completion: "Saved" for a moment, then nothing.
    moveTo(SaveStatus.SAVING)
    assertShowsOnly(SAVING)
    moveTo(SaveStatus.IDLE)
    assertShowsOnly(SAVED)
    mainClock.advanceTimeBy(SAVED_CONFIRMATION_MILLIS + 100L)
    assertShowsNoSaveStatus()

    // Saving, then an error: "Not saved", never "Saved".
    moveTo(SaveStatus.SAVING)
    moveTo(SaveStatus.FAILED)
    assertShowsOnly(NOT_SAVED)
    mainClock.advanceTimeBy(SAVED_CONFIRMATION_MILLIS + 100L)
    assertShowsOnly(NOT_SAVED)

    // The retry succeeds: "Saved", then nothing.
    moveTo(SaveStatus.SAVING)
    assertShowsOnly(SAVING)
    moveTo(SaveStatus.IDLE)
    assertShowsOnly(SAVED)
    mainClock.advanceTimeBy(SAVED_CONFIRMATION_MILLIS + 100L)
    assertShowsNoSaveStatus()

    // An error that clears without a save (the rejection is dismissed): nothing, not "Saved".
    moveTo(SaveStatus.FAILED)
    assertShowsOnly(NOT_SAVED)
    moveTo(SaveStatus.IDLE)
    assertShowsNoSaveStatus()
  }

  @Test
  fun mapToolbar_showsIdleSavingErrorAndCompletion() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      checkSaveStatusTransitions { MapToolbar(state, it) }
    }

  @Test
  fun adminHeader_showsIdleSavingErrorAndCompletion() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      checkSaveStatusTransitions { AdminHeader(it) }
    }

  /** A web app state with the sample user signed in. */
  private fun signedInState(): WebAppState =
    WebAppState(dataHolder = sampleDataHolder()).apply {
      viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
      viewModels.onboarding.acceptTermsOfService()
    }

  private fun ComposeUiTest.showConsole(state: WebAppState, path: String) {
    setContent { WebConsoleApp(state = state, browserHistory = MemoryBrowserHistory(path)) }
    waitForIdle()
  }

  @Test
  fun mapPage_withNothingToSave_showsNoSaveStatus() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = signedInState()
      val surveyId = state.viewModels.dashboard.uiState.value.surveys.first().id
      state.openSurveyOnWeb(surveyId)

      showConsole(state, WebUrls.pathOf(SurveyDashboardRoute(surveyId)))

      assertShowsNoSaveStatus()
    }

  @Test
  fun organizationPage_withNothingToSave_showsNoSaveStatus() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = signedInState()
      val organizations = state.organization.uiState.value.organizations
      assertTrue(organizations.isNotEmpty(), "the sample user belongs to an organization")

      showConsole(state, WebUrls.pathOf(OrganizationRoute(organizations.first().id)))

      assertShowsNoSaveStatus()
    }

  @Test
  fun adminPage_withNothingToSave_showsNoSaveStatus() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = signedInState()
      assertTrue(state.organization.uiState.value.isPlatformAdmin)

      showConsole(state, WebUrls.pathOf(AdminRoute()))

      assertEquals(Organization.ALL_USERS_ID, state.organization.uiState.value.openOrganizationId)
      assertShowsNoSaveStatus()
    }

  /**
   * A signed-in web app state with the sync engine enabled and a stored working copy on the first
   * survey, reproducing a survey with unsaved server working-copy edits.
   */
  private fun connectedStateWithStoredWorkingCopy(): Pair<WebAppState, String> {
    val holder =
      AppDataHolder(
        localStore = seededStore(),
        userDataServiceUrl = "http://service.test",
        grpcClient = SeedUserDataService().client(),
        offlineRegionClient = { UnavailableOfflineRegionClient() },
        deviceStorageClient = { SampleDeviceStorage },
      )
    val state =
      WebAppState(dataHolder = holder).apply {
        viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
        viewModels.onboarding.acceptTermsOfService()
      }
    val surveyId = state.viewModels.dashboard.uiState.value.surveys.first().id
    runDirect {
      val saved = holder.surveyEditorRepository.getDraft(surveyId)
      holder.localStore.transaction {
        putSurveyWorkingCopyDraft(
          surveyId,
          saved.copy(details = saved.details.copy(title = "Unsaved server working copy")),
        )
      }
    }
    state.openSurveyOnWeb(surveyId)
    return state to surveyId
  }

  @Test
  fun mapPage_withStoredWorkingCopyOnActiveSurvey_doesNotAutosaveOrShowSaving() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val (state, surveyId) = connectedStateWithStoredWorkingCopy()
      try {
        showConsole(state, WebUrls.pathOf(SurveyDashboardRoute(surveyId)))

        assertShowsNoSaveStatus()
        val outbox = runDirect { state.dataHolder.localStore.transaction { outbox() } }
        assertEquals(emptyList(), outbox)
      } finally {
        state.close()
        state.dataHolder.koin.close()
      }
    }

  @Test
  fun adminPage_withStoredWorkingCopyOnActiveSurvey_doesNotAutosaveOrShowSaving() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val (state, _) = connectedStateWithStoredWorkingCopy()
      try {
        assertTrue(state.organization.uiState.value.isPlatformAdmin)

        showConsole(state, WebUrls.pathOf(AdminRoute()))

        assertEquals(Organization.ALL_USERS_ID, state.organization.uiState.value.openOrganizationId)
        assertShowsNoSaveStatus()
        val outbox = runDirect { state.dataHolder.localStore.transaction { outbox() } }
        assertEquals(emptyList(), outbox)
      } finally {
        state.close()
        state.dataHolder.koin.close()
      }
    }

  private companion object {
    const val SAVING = "Saving…"
    const val NOT_SAVED = "Not saved"
    const val SAVED = "Saved"
  }
}
