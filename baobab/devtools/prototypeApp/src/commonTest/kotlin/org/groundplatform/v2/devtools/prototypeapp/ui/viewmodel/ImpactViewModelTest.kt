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
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStore
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.AuthRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.ImpactEventRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LibraryRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.AuthProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.AuthSession
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEvent
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEventType
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.AuthRepository

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
    assertNull(f.state.organizations[PrototypeFakeOrganizationsData.ALL_USERS])
  }

  @Test
  fun platformWideNumbers_areOnlyForManagersOfAllUsers() {
    assertNotNull(Fixture().state.global)
    val member = Fixture(auth = FixedAuthRepository("linh.tran@example.org"))
    assertNull(member.state.global)
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
        "impact-summary-all-ground-users.pdf",
      ),
      f.pdf.saved,
    )
    assertTrue(f.state.message.orEmpty().startsWith("Saved impact-summary-all-ground-users.pdf"))
    f.viewModel.dismissMessage()
    assertNull(f.state.message)
  }

  @Test
  fun platformDownload_isIgnoredForEveryoneElse() {
    val f = Fixture(auth = FixedAuthRepository("linh.tran@example.org"))

    f.viewModel.downloadGlobalSummary()

    assertTrue(f.pdf.saved.isEmpty())
  }

  private companion object {
    const val KENYA = "survey-kenya-coffee"
    const val KFS = PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE
  }
}
