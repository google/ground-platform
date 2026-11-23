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
package org.openforis.collectone.v2.app.di

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import openforis.collectone.v1.survey.ListOrganizationsRequest
import openforis.collectone.v1.survey.ListOrganizationsResponse
import openforis.collectone.v1.survey.OrganizationServiceServer
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.auth.AuthenticatedCollectorProfile
import org.openforis.collectone.v2.app.client.auth.DevRole
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.client.storage.JdbcSqlDriverFactory
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService

/**
 * The app opens offline with the last synced data of the account that synced it, after a restart
 * with the local database (`docs/technical/client/local-data-store.md`).
 */
class UserDataSyncPersistenceTest {
  /**
   * Fails every sync while [isDown] is true, as if the device were offline, and never answers while
   * [isUnresponsive] is true, as on a very slow connection.
   */
  private class OfflineOrganizationService(private val delegate: SeedUserDataService) :
    OrganizationServiceServer by delegate {
    var isDown = false
    var isUnresponsive = false

    override suspend fun ListOrganizations(
      request: ListOrganizationsRequest
    ): ListOrganizationsResponse {
      if (isDown) throw GrpcWebException(UNAVAILABLE, PATH, "connection refused")
      if (isUnresponsive) awaitCancellation()
      return delegate.ListOrganizations(request)
    }
  }

  private class SignedInAuthClient(email: String, override val usesRealAccounts: Boolean) :
    AuthClient {
    override val currentUser: StateFlow<AuthenticatedCollectorProfile?> =
      MutableStateFlow(
          AuthenticatedCollectorProfile(
            displayName = "Collector",
            email = email,
            organization = "",
            devRole = DevRole.USER,
          )
        )
        .asStateFlow()

    override suspend fun signInWithGoogle(): AuthenticatedCollectorProfile = error("unused in test")

    override suspend fun signOut() = Unit

    override suspend fun idToken(forceRefresh: Boolean): String? = "token"
  }

  private val databaseFile = Files.createTempFile("collect-one-app", ".db").toFile()
  private val service = SeedUserDataService()
  private val organizations = OfflineOrganizationService(service)

  @AfterTest
  fun deleteDatabase() {
    databaseFile.delete()
  }

  /** Starts the app's graph over the database file, as each launch of the mobile app does. */
  private fun launch(authClient: AuthClient): AppDataHolder {
    val client =
      CollectOneGrpcClient(
        surveyService = service,
        organizationService = organizations,
        libraryService = service,
        dataService = service,
      )
    return createAppDataHolder(
      config =
        AppConfig(
          userDataServiceUrl = "http://service.test",
          userDataClient = { client },
          enableSyncEngine = true,
        ),
      platform =
        platformModule(
          authClient = { authClient },
          localDatabase = { JdbcSqlDriverFactory(databaseFile) },
        ),
    )
  }

  private fun AppDataHolder.surveyCount() = runDirect { localStore.transaction { surveys().size } }

  @Test
  fun realAccount_opensOfflineWithItsLastSyncedData() {
    assertEquals(7, launch(realAccount(COLLECTOR)).surveyCount())
    organizations.isDown = true

    val relaunched = launch(realAccount(COLLECTOR))

    assertEquals(UserDataSyncState.Synced, relaunched.userDataSyncState.value)
    assertEquals(7, relaunched.surveyCount())
  }

  @Test
  fun anotherRealAccount_doesNotSeeTheStoredData() {
    assertEquals(7, launch(realAccount(COLLECTOR)).surveyCount())
    organizations.isDown = true

    val other = launch(realAccount("other@example.org"))

    assertIs<UserDataSyncState.Failed>(other.userDataSyncState.value)
    assertEquals(0, other.surveyCount())
  }

  @Test
  fun sampleAccount_opensOfflineWithTheLastSyncedData() {
    assertEquals(7, launch(sampleAccount()).surveyCount())
    organizations.isDown = true

    val relaunched = launch(sampleAccount())

    assertEquals(UserDataSyncState.Synced, relaunched.userDataSyncState.value)
    assertEquals(7, relaunched.surveyCount())
  }

  @Test
  fun relaunch_reopensTheSurveyTheUserHadOpen() {
    val first = launch(realAccount(COLLECTOR))
    val opened = runDirect {
      val activeId = first.surveyRepository.getActiveSurveyId()
      first.surveyRepository.getSurveys().first { it.id != activeId }.id
    }
    runBlocking { first.surveyRepository.setActiveSurveyId(opened) }
    organizations.isDown = true

    val relaunched = launch(realAccount(COLLECTOR))

    assertEquals(opened, relaunched.surveyRepository.observeOpenSurveyId().value)
    assertEquals(opened, runDirect { relaunched.surveyRepository.getActiveSurveyId() })
  }

  @Test
  fun relaunch_loadsTheStoredDataWithoutWaitingForTheNetwork() {
    assertEquals(7, launch(realAccount(COLLECTOR)).surveyCount())
    organizations.isUnresponsive = true

    val relaunched = launch(realAccount(COLLECTOR))

    assertTrue(relaunched.userDataSync.hasLoadedLocalData.value)
    assertEquals(7, relaunched.surveyCount())
  }

  private fun realAccount(email: String) = SignedInAuthClient(email, usesRealAccounts = true)

  private fun sampleAccount() = SignedInAuthClient(COLLECTOR, usesRealAccounts = false)

  companion object {
    private const val COLLECTOR = "collector@example.org"
    private const val UNAVAILABLE = 14
    private const val PATH = "/openforis.collectone.v1.survey.OrganizationService/ListOrganizations"
  }
}
