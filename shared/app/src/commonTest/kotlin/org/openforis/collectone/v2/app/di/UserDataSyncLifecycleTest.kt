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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.auth.AuthenticatedCollectorProfile
import org.openforis.collectone.v2.app.client.auth.DevRole
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.domain.model.SaveStatus

/**
 * How [AppDataHolder] reports the initial sync and the sync engine through the account lifecycle.
 */
class UserDataSyncLifecycleTest {
  /** Real accounts (as with Firebase) whose signed-in user the test sets. */
  private class RealAccountsAuthClient(signedIn: AuthenticatedCollectorProfile?) : AuthClient {
    val user = MutableStateFlow(signedIn)
    override val currentUser: StateFlow<AuthenticatedCollectorProfile?> = user.asStateFlow()
    override val usesRealAccounts: Boolean = true
    override val supportsDevRoles: Boolean = false
    override val devRole: DevRole = DevRole.USER

    override fun setDevRole(role: DevRole) = Unit

    override suspend fun signInWithGoogle(): AuthenticatedCollectorProfile = error("unused in test")

    override suspend fun signOut() {
      user.value = null
    }

    override suspend fun idToken(forceRefresh: Boolean): String? = "token"
  }

  private val service = SeedUserDataService()
  private val store = InMemoryLocalStore()

  private fun connectedHolder(authClient: AuthClient) =
    AppDataHolder(
      localStore = store,
      userDataServiceUrl = "http://service.test",
      authClient = authClient,
      grpcClient =
        CollectOneGrpcClient(
          surveyService = service,
          organizationService = service,
          libraryService = service,
          dataService = service,
        ),
    )

  private fun surveyCount() = runDirect { store.transaction { surveys().size } }

  @Test
  fun withoutAService_theInitialSyncIsAlreadyComplete() {
    val holder = AppDataHolder(localStore = InMemoryLocalStore())

    assertTrue(holder.hasCompletedInitialSync.value)
  }

  @Test
  fun withoutTheSyncEngine_nothingIsEverSaving() {
    val holder = AppDataHolder(localStore = InMemoryLocalStore())

    assertEquals(SaveStatus.IDLE, holder.saveStatus.value)
  }

  @Test
  fun withoutTheSyncEngine_theLastSyncTimeIsUnknown() {
    val holder = AppDataHolder(localStore = InMemoryLocalStore())

    assertNull(holder.lastSyncedAt.value)
  }

  @Test
  fun signedIn_theInitialSyncDownloadsTheUsersData() {
    val holder = connectedHolder(RealAccountsAuthClient(signedIn = ADMIN))

    assertEquals(UserDataSyncState.Synced, holder.userDataSyncState.value)
    assertTrue(holder.hasCompletedInitialSync.value)
    assertEquals(7, surveyCount())
  }

  @Test
  fun signingOut_reportsTheSyncAsSettled() {
    val auth = RealAccountsAuthClient(signedIn = ADMIN)
    val holder = connectedHolder(auth)

    auth.user.value = null

    assertEquals(UserDataSyncState.Synced, holder.userDataSyncState.value)
    assertTrue(holder.hasCompletedInitialSync.value)
  }

  companion object {
    private val ADMIN =
      AuthenticatedCollectorProfile(
        displayName = "Admin",
        email = "admin@example.org",
        organization = "",
        devRole = DevRole.USER,
      )
  }
}
