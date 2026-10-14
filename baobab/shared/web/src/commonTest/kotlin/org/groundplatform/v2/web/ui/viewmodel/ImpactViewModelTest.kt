/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.web.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.groundplatform.v2.app.data.datasource.local.store.LocalStore
import org.groundplatform.v2.app.data.datasource.local.store.runNow
import org.groundplatform.v2.app.data.datasource.local.store.seededStore
import org.groundplatform.v2.app.data.repository.AuthRepositoryImpl
import org.groundplatform.v2.app.data.repository.ImpactEventRepositoryImpl
import org.groundplatform.v2.app.data.repository.LibraryRepositoryImpl
import org.groundplatform.v2.app.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.app.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.app.data.seed.SampleOrganizationsData
import org.groundplatform.v2.app.domain.model.AuthProfile
import org.groundplatform.v2.app.domain.model.AuthSession
import org.groundplatform.v2.app.domain.model.ImpactEvent
import org.groundplatform.v2.app.domain.model.ImpactEventType
import org.groundplatform.v2.app.domain.repository.AuthRepository
import org.groundplatform.v2.app.testing.testScope
import org.groundplatform.v2.app.ui.viewmodel.RecordingPdfExportClient
import org.groundplatform.v2.web.ui.state.ImpactDashboardFocus
import org.groundplatform.v2.web.ui.state.ImpactFilter

class ImpactViewModelTest {
  private class Fixture(
    val store: LocalStore = seededStore(),
    auth: AuthRepository = AuthRepositoryImpl(),
  ) {
    val pdf = RecordingPdfExportClient()
    val organizations = OrganizationRepositoryImpl(store)
    val events = ImpactEventRepositoryImpl(store)
    val viewModel =
      ImpactViewModel(
        surveyRepository = SurveyRepositoryImpl(store),
        organizationRepository = organizations,
        libraryRepository = LibraryRepositoryImpl(store),
        impactEventRepository = events,
        authRepository = auth,
        scope = testScope(),
        surveyEditorRepository = SurveyEditorRepositoryImpl(store),
        pdfExportClient = pdf,
        debounceMillis = 0,
        now = { 1_791_000_000_000 },
      )

    val state
      get() = viewModel.uiState.value
  }

  private class FixedAuthRepository(email: String) : AuthRepository {
    private val session =
      MutableStateFlow(AuthSession(isSignedIn = true, profile = AuthProfile("Someone", email, "")))

    override fun observeSession(): Flow<AuthSession> = session

    override suspend fun getSession(): AuthSession = session.value

    override suspend fun signInWithGoogle(): AuthProfile = session.value.profile

    override suspend fun signOut() {}
  }

  @Test
  fun seededData_givesEverySurveyAndOrganizationItsNumbers() {
    val f = Fixture()

    assertFalse(f.state.isLoading)
    val coffee = assertNotNull(f.state.surveys[KENYA])
    assertEquals(5, coffee.features.uniqueCount)
    val kfs = assertNotNull(f.state.organizations[KFS])
    assertEquals(14, kfs.summary.features.uniqueCount)
    assertEquals(3, kfs.surveys.size)
    assertEquals(listOf("KE"), kfs.countries.map { it.code })
    // The synthetic "All users" organization has no organization Impact view of its own.
    assertNull(f.state.organizations[SampleOrganizationsData.ALL_USERS])
  }

  @Test
  fun platformWideNumbers_arePublishedToEveryone_andInFullToManagersOfAllUsers() {
    val admin = assertNotNull(Fixture().state.global)
    assertFalse(admin.isPublished)
    val member = assertNotNull(Fixture(auth = FixedAuthRepository(LINH)).state.global)
    assertTrue(member.isPublished)
    assertEquals(admin.features, member.features)
  }

  @Test
  fun fullOrganizationNumbers_areOnlyForItsMembers() {
    val f = Fixture(auth = FixedAuthRepository(LINH))

    assertEquals(setOf(MEKONG), f.state.organizations.keys)
    // Everyone else gets the published numbers.
    val kfs = assertNotNull(f.state.dashboard.organization(KFS))
    assertFalse(kfs.isMember)
    assertTrue(kfs.published.summary.isPublished)
    assertEquals(14, kfs.published.summary.features.uniqueCount)
    // Survey names aren't published, so there's no survey filter.
    assertTrue(kfs.published.surveys.isEmpty())
  }

  @Test
  fun dashboard_startsWithYourOrganizations_andOffersListedOnes() {
    val f = Fixture()

    val dashboard = f.state.dashboard
    assertEquals(
      listOf(KFS, MEKONG, SampleOrganizationsData.OPEN_FORIS_COMMUNITY),
      dashboard.available.map { it.id },
    )
    assertEquals(listOf(KFS, MEKONG), dashboard.selectedIds)
    assertTrue(dashboard.organization(KFS)!!.isManager)
    assertEquals(ImpactDashboardFocus.Overview, dashboard.focus)
  }

  @Test
  fun dashboard_hidesUnlistedOrganizationsFromEveryoneElse() {
    val f = Fixture(auth = FixedAuthRepository(LINH))
    assertNotNull(f.state.dashboard.organization(KFS))

    runNow { f.organizations.updateOrganization(KFS) { it.copy(isListed = false) } }

    assertNull(f.state.dashboard.organization(KFS))
    f.viewModel.addToDashboard(KFS)
    assertEquals(listOf(MEKONG), f.state.dashboard.selectedIds)
  }

  @Test
  fun dashboard_choosingOrganizations_addsRemovesAndFocuses() {
    val f = Fixture(auth = FixedAuthRepository(LINH))
    assertEquals(listOf(MEKONG), f.state.dashboard.selectedIds)

    f.viewModel.addToDashboard(KFS)
    assertEquals(listOf(MEKONG, KFS), f.state.dashboard.selectedIds)
    f.viewModel.setDashboardOrganizations(listOf(KFS, "org-unknown", KFS))
    assertEquals(listOf(KFS), f.state.dashboard.selectedIds)

    f.viewModel.focusDashboard(ImpactDashboardFocus.OrganizationFocus(KFS))
    assertEquals(ImpactDashboardFocus.OrganizationFocus(KFS), f.state.dashboard.focus)
    // Unfollowing the organization shown goes back to the overview.
    f.viewModel.removeFromDashboard(KFS)
    assertTrue(f.state.dashboard.selectedIds.isEmpty())
    assertEquals(ImpactDashboardFocus.Overview, f.state.dashboard.focus)
  }

  @Test
  fun openDashboard_addsAndShowsTheOrganization_orThePlatform() {
    val f = Fixture(auth = FixedAuthRepository(LINH))

    f.viewModel.openDashboard(KFS)
    assertEquals(listOf(MEKONG, KFS), f.state.dashboard.selectedIds)
    assertEquals(ImpactDashboardFocus.OrganizationFocus(KFS), f.state.dashboard.focus)

    f.viewModel.openDashboard(SampleOrganizationsData.ALL_USERS)
    assertEquals(ImpactDashboardFocus.Platform, f.state.dashboard.focus)
    f.viewModel.openDashboard(null)
    assertEquals(ImpactDashboardFocus.Overview, f.state.dashboard.focus)

    f.viewModel.openDashboard(KFS)
    f.viewModel.setPreviewingPublished(true)
    f.viewModel.reset()
    // Resets keep each person's organizations but not what was shown.
    assertEquals(listOf(MEKONG, KFS), f.state.dashboard.selectedIds)
    assertEquals(ImpactDashboardFocus.Overview, f.state.dashboard.focus)
    assertFalse(f.state.dashboard.isPreviewingPublished)
  }

  @Test
  fun publishedNumbers_followTheCountryFilter_butNotTheSurveyFilter() {
    val f = Fixture()

    f.viewModel.setOrganizationSurveyFilter(KFS, KENYA)
    f.viewModel.setOrganizationCountryFilter(KFS, "VN")
    val published = f.state.dashboard.organization(KFS)!!.published
    assertEquals(ImpactFilter(countryCode = "VN"), published.filter)
    assertTrue(published.summary.isBelowPublicationThreshold)
  }

  @Test
  fun numbers_recomputeWhenTheDataChanges() {
    val f = Fixture()
    assertEquals(0, downloads(f))

    runNow {
      f.events.append(
        ImpactEvent(
          id = "evt-1",
          type = ImpactEventType.EXPORT,
          surveyId = KENYA,
          organizationId = KFS,
          featureCount = 3,
          areaHa = 5.85,
          occurredAt = "2026-10-01T00:00:00Z",
          actorUserId = "uid",
        )
      )
    }
    assertEquals(1, downloads(f))

    runNow {
      f.organizations.updateOrganization(KFS) { it.copy(excludeFromPlatformAggregates = true) }
    }
    assertTrue(assertNotNull(f.state.global).suggestedIndicators.isEmpty())
  }

  private fun downloads(f: Fixture) =
    assertNotNull(f.state.surveys[KENYA]).activity.first { it.type == ImpactEventType.EXPORT }.count

  @Test
  fun organizationFilters_narrowItsNumbers_andCanBeCleared() {
    val f = Fixture()

    f.viewModel.setOrganizationSurveyFilter(KFS, KENYA)
    assertEquals(5, f.state.organizations.getValue(KFS).summary.features.uniqueCount)
    f.viewModel.setOrganizationCountryFilter(KFS, "KE")
    val filtered = f.state.organizations.getValue(KFS)
    assertEquals(KENYA, filtered.filter.surveyId)
    assertEquals("KE", filtered.filter.countryCode)
    assertEquals(5, filtered.summary.features.uniqueCount)

    f.viewModel.setOrganizationSurveyFilter(KFS, null)
    f.viewModel.setOrganizationCountryFilter(KFS, null)
    assertFalse(f.state.organizations.getValue(KFS).filter.isActive)
    assertEquals(14, f.state.organizations.getValue(KFS).summary.features.uniqueCount)

    f.viewModel.setOrganizationSurveyFilter(KFS, KENYA)
    f.viewModel.reset()
    assertFalse(f.state.organizations.getValue(KFS).filter.isActive)
  }

  @Test
  fun downloads_saveAPdfSummary_andSayWhere() {
    val f = Fixture()

    f.viewModel.downloadSurveySummary(KENYA)
    f.viewModel.downloadOrganizationSummary(KFS)
    f.viewModel.downloadGlobalSummary()

    assertEquals(
      listOf(
        "impact-summary-all-form-field-types-showcase-kenya-coffee.pdf",
        "impact-summary-kenya-forest-service.pdf",
        "impact-summary-all-collect-one-users.pdf",
      ),
      f.pdf.saved,
    )
    assertTrue(
      f.state.message.orEmpty().startsWith("Saved impact-summary-all-collect-one-users.pdf")
    )
    f.viewModel.dismissMessage()
    assertNull(f.state.message)
  }

  @Test
  fun downloads_ofPublishedNumbers_sayTheyArePublished() {
    val f = Fixture(auth = FixedAuthRepository(LINH))

    f.viewModel.downloadGlobalSummary()
    f.viewModel.downloadPublishedOrganizationSummary(KFS)
    // Linh isn't a member of Kenya Forest Service, so there are no full numbers to download.
    f.viewModel.downloadOrganizationSummary(KFS)

    assertEquals(
      listOf("impact-summary-all-collect-one-users.pdf", "impact-summary-kenya-forest-service.pdf"),
      f.pdf.saved,
    )
  }

  private companion object {
    const val KENYA = "survey-kenya-coffee"
    const val KFS = SampleOrganizationsData.KENYA_FOREST_SERVICE
    const val MEKONG = SampleOrganizationsData.MEKONG_MANGROVE_ALLIANCE
    const val LINH = "linh.tran@example.org"
  }
}
