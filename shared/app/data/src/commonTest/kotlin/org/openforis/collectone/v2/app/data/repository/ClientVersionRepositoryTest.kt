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
package org.openforis.collectone.v2.app.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.userdata.ClientVersionPolicy
import openforis.collectone.v1.userdata.ClientVersionServiceServer
import openforis.collectone.v1.userdata.GetClientVersionPolicyRequest
import openforis.collectone.v1.userdata.GetClientVersionPolicyResponse
import openforis.collectone.v1.userdata.UpdateClientVersionPolicyRequest
import openforis.collectone.v1.userdata.UpdateClientVersionPolicyResponse
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.core.version.MobileClientVersionPolicy
import org.openforis.collectone.v2.core.version.MobileUpdateStatus

@OptIn(ExperimentalCoroutinesApi::class)
class ClientVersionRepositoryTest {

  @Test
  fun tracksHeaderPolicyAndSuggestionDismissal() = runTest {
    val tracker = ClientVersionPolicyTracker()
    val repo =
      ClientVersionRepositoryImpl(
        tracker = tracker,
        grpcClient = null,
        scope = backgroundScope,
        clientVersion = "2.0.0",
      )

    assertEquals(MobileUpdateStatus.UpToDate, repo.updateStatus.value)
    assertFalse(repo.isSuggestionDismissed.value)

    // Receiving headers advertising 2.1.0 triggers Suggested.
    tracker.recordFromResponseMetadata(
      MobileClientVersionPolicy(latestVersion = "2.1.0").toResponseHeaders()
    )
    runCurrent()
    assertEquals(
      MobileUpdateStatus.Suggested(currentVersion = "2.0.0", latestVersion = "2.1.0"),
      repo.updateStatus.value,
    )

    // Dismissing hides suggestion for 2.1.0.
    repo.dismissSuggestion()
    runCurrent()
    assertTrue(repo.isSuggestionDismissed.value)

    // When a newer version (2.2.0) is advertised, suggestion re-appears.
    tracker.recordFromResponseMetadata(
      MobileClientVersionPolicy(latestVersion = "2.2.0").toResponseHeaders()
    )
    runCurrent()
    assertFalse(repo.isSuggestionDismissed.value)

    // Receiving a required version transitions to Required, which cannot be dismissed.
    tracker.recordFromResponseMetadata(
      MobileClientVersionPolicy(latestVersion = "2.2.0", minRequiredVersion = "2.1.0")
        .toResponseHeaders()
    )
    runCurrent()
    assertTrue(repo.updateStatus.value is MobileUpdateStatus.Required)
    repo.dismissSuggestion()
    runCurrent()
    assertFalse(repo.isSuggestionDismissed.value)
  }

  @Test
  fun refreshAndUpdatePolicyThroughGrpcClient() = runTest {
    var stored =
      ClientVersionPolicy(
        latest_mobile_version = "2.1.0",
        min_required_mobile_version = "",
        mobile_update_url = "https://example.com/v2",
      )
    val fakeVersionService =
      object : ClientVersionServiceServer {
        override suspend fun GetClientVersionPolicy(
          request: GetClientVersionPolicyRequest
        ): GetClientVersionPolicyResponse = GetClientVersionPolicyResponse(policy = stored)

        override suspend fun UpdateClientVersionPolicy(
          request: UpdateClientVersionPolicyRequest
        ): UpdateClientVersionPolicyResponse {
          stored = request.policy ?: ClientVersionPolicy()
          return UpdateClientVersionPolicyResponse(policy = stored)
        }
      }
    val seed = SeedUserDataService()
    val grpcClient =
      CollectOneGrpcClient(
        surveyService = seed,
        organizationService = seed,
        libraryService = seed,
        dataService = seed,
        syncService = seed,
        clientVersionService = fakeVersionService,
      )
    val repo =
      ClientVersionRepositoryImpl(
        tracker = ClientVersionPolicyTracker(),
        grpcClient = grpcClient,
        scope = backgroundScope,
        clientVersion = "2.0.0",
      )

    val fetched = repo.refreshPolicy().getOrThrow()
    runCurrent()
    assertEquals("2.1.0", fetched.latestVersion)
    assertTrue(repo.updateStatus.value is MobileUpdateStatus.Suggested)

    // Invalid SemVer is rejected client-side before calling server.
    assertTrue(repo.updatePolicy(MobileClientVersionPolicy(minRequiredVersion = "bad")).isFailure)

    // Valid update saves and updates state immediately.
    val saved =
      repo
        .updatePolicy(
          MobileClientVersionPolicy(latestVersion = "2.2.0", minRequiredVersion = "2.1.0")
        )
        .getOrThrow()
    runCurrent()
    assertEquals("2.1.0", saved.minRequiredVersion)
    assertTrue(repo.updateStatus.value is MobileUpdateStatus.Required)
  }
}
