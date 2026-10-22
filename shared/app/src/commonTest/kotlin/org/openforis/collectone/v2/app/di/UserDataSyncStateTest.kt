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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import openforis.collectone.v1.survey.ListOrganizationsRequest
import openforis.collectone.v1.survey.ListOrganizationsResponse
import openforis.collectone.v1.survey.OrganizationServiceServer
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService

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
    private const val PATH = "/openforis.collectone.v1.survey.OrganizationService/ListOrganizations"
  }
}
