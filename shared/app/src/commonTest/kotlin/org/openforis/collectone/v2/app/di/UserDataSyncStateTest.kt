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
package org.openforis.collectone.v2.app.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import openforis.collectone.v1.survey.ListOrganizationsRequest
import openforis.collectone.v1.survey.ListOrganizationsResponse
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.MutateSurveyResponse
import openforis.collectone.v1.survey.OrganizationServiceServer
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.survey.SurveyServiceServer
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.auth.AuthenticatedCollectorProfile
import org.openforis.collectone.v2.app.client.auth.DevRole
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.data.sync.OutboxRecorder
import org.openforis.collectone.v2.app.data.sync.Rebaser
import org.openforis.collectone.v2.app.domain.error.DomainError

/** [AppDataHolder] reports a failed sync from the user data service and recovers on retry. */
class UserDataSyncStateTest {
  /** Fails every sync while [isDown] is true, as if the service were unreachable. */
  private class FlakyOrganizationService(private val delegate: SeedUserDataService) :
    OrganizationServiceServer by delegate {
    var isDown = true

    override suspend fun ListOrganizations(
      request: ListOrganizationsRequest
    ): ListOrganizationsResponse {
      if (isDown) throw GrpcWebException(UNAVAILABLE, PATH, "connection refused")
      return delegate.ListOrganizations(request)
    }
  }

  private val service = SeedUserDataService()
  private val organizations = FlakyOrganizationService(service)
  private val store = InMemoryLocalStore()

  private fun holder() =
    AppDataHolder(
      localStore = store,
      userDataServiceUrl = "http://service.test",
      grpcClient =
        CollectOneGrpcClient(
          surveyService = service,
          organizationService = organizations,
          libraryService = service,
          dataService = service,
        ),
    )

  @Test
  fun failedSync_isReported_andRetrySyncsTheData() {
    val holder = holder()
    val failed = assertIs<UserDataSyncState.Failed>(holder.userDataSyncState.value)
    assertEquals("http://service.test", failed.serviceUrl)
    assertEquals(DomainError.Network, failed.error)
    assertEquals(0, runDirect { store.transaction { surveys().size } })

    organizations.isDown = false
    holder.retryUserDataSync()

    assertEquals(UserDataSyncState.Synced, holder.userDataSyncState.value)
    assertEquals(7, runDirect { store.transaction { surveys().size } })
  }

  @Test
  fun withoutAService_nothingIsSynced() {
    assertEquals(
      UserDataSyncState.NotConfigured,
      AppDataHolder(localStore = InMemoryLocalStore()).userDataSyncState.value,
    )
  }

  @Test
  fun realAccountSignIn_resetsHasCompletedInitialSyncUntilSeedCompletes() {
    var gate: CompletableDeferred<Unit>? = CompletableDeferred()
    val gatedOrgs =
      object : OrganizationServiceServer by service {
        override suspend fun ListOrganizations(
          request: ListOrganizationsRequest
        ): ListOrganizationsResponse {
          gate?.await()
          return service.ListOrganizations(request)
        }
      }
    val userFlow = MutableStateFlow<AuthenticatedCollectorProfile?>(null)
    val realAuth =
      object : AuthClient {
        private var currentDevRole: DevRole = DevRole.USER
        override val currentUser: StateFlow<AuthenticatedCollectorProfile?> = userFlow.asStateFlow()
        override val usesRealAccounts: Boolean = true
        override val supportsDevRoles: Boolean = true
        override val devRole: DevRole
          get() = currentDevRole

        override fun setDevRole(role: DevRole) {
          currentDevRole = role
          userFlow.value = userFlow.value?.copy(devRole = role)
        }

        override suspend fun signInWithGoogle(): AuthenticatedCollectorProfile =
          error("unused in test")

        override suspend fun signOut() {
          userFlow.value = null
        }

        override suspend fun idToken(forceRefresh: Boolean): String? = "token"
      }

    val holder =
      AppDataHolder(
        localStore = store,
        userDataServiceUrl = "http://service.test",
        authClient = realAuth,
        grpcClient =
          CollectOneGrpcClient(
            surveyService = service,
            organizationService = gatedOrgs,
            libraryService = service,
            dataService = service,
          ),
      )

    // Signed out initially: initial sync is marked complete so the sign-in screen doesn't spin.
    assertTrue(holder.hasCompletedInitialSync.value)

    // User signs in: while the seed RPC is in flight, hasCompletedInitialSync resets to false so
    // Web and Mobile show the loading state instead of an empty "No surveys" state.
    userFlow.value =
      AuthenticatedCollectorProfile(
        displayName = "Admin",
        email = "admin@example.org",
        organization = "",
        devRole = DevRole.USER,
      )
    assertFalse(holder.hasCompletedInitialSync.value)
    assertEquals(UserDataSyncState.Syncing, holder.userDataSyncState.value)

    // Switching role while the initial sync is stuck cancels the stuck sync and completes the new
    // one once the server responds.
    gate = null
    realAuth.setDevRole(DevRole.ADMIN)

    assertTrue(holder.hasCompletedInitialSync.value)
    assertEquals(UserDataSyncState.Synced, holder.userDataSyncState.value)
    assertEquals(7, runDirect { store.transaction { surveys().size } })
  }

  @Test
  fun rejectedClosedSurveyMutation_surfacesInBackendError_andClearsOnDismiss() {
    val rejectingSurveys =
      object : SurveyServiceServer by service {
        override suspend fun MutateSurvey(request: MutateSurveyRequest): MutateSurveyResponse {
          throw GrpcWebException(
            9,
            "/openforis.collectone.v1.survey.SurveyService/MutateSurvey",
            "This survey is closed and read-only. Reopen it or switch it to draft to make changes.",
          )
        }
      }
    val local = InMemoryLocalStore()
    val holder =
      AppDataHolder(
        localStore = local,
        userDataServiceUrl = "http://service.test",
        grpcClient =
          CollectOneGrpcClient(
            surveyService = rejectingSurveys,
            organizationService = service,
            libraryService = service,
            dataService = service,
          ),
      )
    assertEquals(UserDataSyncState.Synced, holder.userDataSyncState.value)
    assertNull(holder.backendError.value)

    val surveyId = runDirect { local.transaction { surveys().first().id } }
    runDirect {
      local.transaction {
        OutboxRecorder()
          .record(
            this,
            MutationTarget.Survey(surveyId, workingCopy = false),
            Rebaser.surveyKey(surveyId),
            MutationPayload.Survey(SurveyMutation(upsert = SurveyDef(title = "Disallowed edit"))),
          )
      }
      holder.syncCoordinator?.syncNow()
    }

    val error = assertNotNull(holder.backendError.value)
    assertFalse(error.canRetry)
    assertEquals(DomainError.NotEditable, error.error)
    assertEquals(
      OutboxState.REJECTED,
      runDirect { local.transaction { outbox().single().state } },
    )

    holder.dismissBackendError()
    assertNull(holder.backendError.value)
    assertTrue(runDirect { local.transaction { outbox().isEmpty() } })
  }

  companion object {
    private const val UNAVAILABLE = 14
    private const val PATH = "/openforis.collectone.v1.survey.OrganizationService/ListOrganizations"
  }
}
