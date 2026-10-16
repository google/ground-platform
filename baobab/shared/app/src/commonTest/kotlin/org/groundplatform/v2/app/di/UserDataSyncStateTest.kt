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
package org.groundplatform.v2.app.di

import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.OrganizationServiceServer
import groundplatform.v2.survey.StreamOrganizationsRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.SendChannel
import org.groundplatform.v2.app.client.grpc.CollectOneGrpcClient
import org.groundplatform.v2.app.client.grpc.GrpcWebException
import org.groundplatform.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.groundplatform.v2.app.data.datasource.local.store.runDirect
import org.groundplatform.v2.app.data.datasource.remote.SeedUserDataService

/** [AppDataHolder] reports a failed sync from the user data service and recovers on retry. */
class UserDataSyncStateTest {
  /** Fails every sync while [isDown] is true, as if the service were unreachable. */
  private class FlakyOrganizationService(private val delegate: SeedUserDataService) :
    OrganizationServiceServer by delegate {
    var isDown = true

    override suspend fun StreamOrganizations(
      request: StreamOrganizationsRequest,
      response: SendChannel<Organization>,
    ) {
      if (isDown) throw GrpcWebException(UNAVAILABLE, PATH, "connection refused")
      delegate.StreamOrganizations(request, response)
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
    assertTrue("connection refused" in failed.message, failed.message)
    assertEquals(0, runDirect { store.transaction { surveys().size } })

    organizations.isDown = false
    holder.retryUserDataSync()

    assertEquals(UserDataSyncState.Synced, holder.userDataSyncState.value)
    assertEquals(6, runDirect { store.transaction { surveys().size } })
  }

  @Test
  fun withoutAService_nothingIsSynced() {
    assertEquals(
      UserDataSyncState.NotConfigured,
      AppDataHolder(localStore = InMemoryLocalStore()).userDataSyncState.value,
    )
  }

  companion object {
    private const val UNAVAILABLE = 14
    private const val PATH = "/groundplatform.v2.survey.OrganizationService/StreamOrganizations"
  }
}
