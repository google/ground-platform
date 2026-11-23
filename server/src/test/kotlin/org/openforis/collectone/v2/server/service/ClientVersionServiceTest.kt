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
package org.openforis.collectone.v2.server.service

import io.grpc.Status
import io.grpc.StatusException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import openforis.collectone.v1.survey.ListSurveyOverviewsRequest
import openforis.collectone.v1.userdata.ClientVersionPolicy
import openforis.collectone.v1.userdata.GetClientVersionPolicyRequest
import openforis.collectone.v1.userdata.GetClientVersionPolicyResponse
import openforis.collectone.v1.userdata.UpdateClientVersionPolicyRequest
import org.openforis.collectone.v2.core.version.ClientVersionHeaders
import org.openforis.collectone.v2.core.version.MobileClientVersionPolicy
import org.openforis.collectone.v2.server.app.UserDataServer
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.auth.CallerElement
import org.openforis.collectone.v2.server.data.InMemoryUserDataStore

class ClientVersionServiceTest {

  @Test
  fun getPolicyFallsBackToDefaultAndUpdateOverridesIt() = runTest {
    val defaultPolicy =
      MobileClientVersionPolicy(
        latestVersion = "2.1.0",
        minRequiredVersion = "2.0.0",
        updateUrl = "https://example.com/app",
      )
    val backend =
      UserDataBackend(
        store = InMemoryUserDataStore(),
        defaultMobileClientVersionPolicy = defaultPolicy,
      )

    val initial = backend.GetClientVersionPolicy(GetClientVersionPolicyRequest()).policy
    assertEquals("2.1.0", initial?.latest_mobile_version)
    assertEquals("2.0.0", initial?.min_required_mobile_version)
    assertEquals("https://example.com/app", initial?.mobile_update_url)

    val adminCaller =
      Caller(
        userId = "admin-1",
        email = "admin@example.org",
        emailVerified = true,
        isAdmin = true,
      )
    withContext(CallerElement(adminCaller)) {
      val updated =
        backend
          .UpdateClientVersionPolicy(
            UpdateClientVersionPolicyRequest(
              policy =
                ClientVersionPolicy(
                  latest_mobile_version = "2.3.0",
                  min_required_mobile_version = "",
                  mobile_update_url = "https://example.com/new",
                )
            )
          )
          .policy
      assertEquals("2.3.0", updated?.latest_mobile_version)
      assertEquals("", updated?.min_required_mobile_version)
      assertEquals("https://example.com/new", updated?.mobile_update_url)
    }

    assertEquals(
      MobileClientVersionPolicy(
        latestVersion = "2.3.0",
        minRequiredVersion = "",
        updateUrl = "https://example.com/new",
      ),
      backend.currentMobileClientVersionPolicy(),
    )
  }

  @Test
  fun updatePolicyRejectsNonAdminsAndInvalidInputs() = runTest {
    val backend = UserDataBackend(store = InMemoryUserDataStore())
    val regularUser =
      Caller(
        userId = "user-1",
        email = "user@example.org",
        emailVerified = true,
        isAdmin = false,
      )

    val denied =
      assertFailsWith<StatusException> {
        withContext(CallerElement(regularUser)) {
          backend.UpdateClientVersionPolicy(
            UpdateClientVersionPolicyRequest(
              policy = ClientVersionPolicy(min_required_mobile_version = "2.0.0")
            )
          )
        }
      }
    assertEquals(Status.Code.PERMISSION_DENIED, denied.status.code)

    val admin = regularUser.copy(isAdmin = true)
    val invalidVersion =
      assertFailsWith<StatusException> {
        withContext(CallerElement(admin)) {
          backend.UpdateClientVersionPolicy(
            UpdateClientVersionPolicyRequest(
              policy = ClientVersionPolicy(min_required_mobile_version = "not-semver")
            )
          )
        }
      }
    assertEquals(Status.Code.INVALID_ARGUMENT, invalidVersion.status.code)

    val invalidUrl =
      assertFailsWith<StatusException> {
        withContext(CallerElement(admin)) {
          backend.UpdateClientVersionPolicy(
            UpdateClientVersionPolicyRequest(
              policy = ClientVersionPolicy(mobile_update_url = "javascript:alert(1)")
            )
          )
        }
      }
    assertEquals(Status.Code.INVALID_ARGUMENT, invalidUrl.status.code)
  }

  @Test
  fun interceptorSuggestsUpdateAndAllowsNonMobileClients() = runTest {
    withTestServer(
      MobileClientVersionPolicy(
        latestVersion = "2.2.0",
        minRequiredVersion = "2.1.0",
        updateUrl = "https://example.com/download",
      )
    ) { baseUrl ->
      // Mobile client at 2.1.0 (meets min 2.1.0, below latest 2.2.0): RPC succeeds with headers.
      val suggestedResp =
        postGrpcWeb(
          baseUrl = baseUrl,
          path = "/openforis.collectone.v1.survey.SurveyService/ListSurveyOverviews",
          payload = ListSurveyOverviewsRequest.ADAPTER.encode(ListSurveyOverviewsRequest()),
          headers =
            mapOf(
              ClientVersionHeaders.CLIENT_PLATFORM to ClientVersionHeaders.PLATFORM_MOBILE,
              ClientVersionHeaders.CLIENT_VERSION to "2.1.0",
            ),
        )
      assertEquals(0, suggestedResp.grpcStatus)
      assertEquals("1", suggestedResp.allMetadata[ClientVersionHeaders.POLICY_CHECKED])
      assertEquals("2.2.0", suggestedResp.allMetadata[ClientVersionHeaders.LATEST_MOBILE_VERSION])
      assertEquals(
        "2.1.0",
        suggestedResp.allMetadata[ClientVersionHeaders.MIN_REQUIRED_MOBILE_VERSION],
      )
      assertEquals(
        "https://example.com/download",
        suggestedResp.allMetadata[ClientVersionHeaders.MOBILE_UPDATE_URL],
      )

      // Non-mobile client (Web Console) is never blocked even when min version is higher.
      val webResp =
        postGrpcWeb(
          baseUrl = baseUrl,
          path = "/openforis.collectone.v1.survey.SurveyService/ListSurveyOverviews",
          payload = ListSurveyOverviewsRequest.ADAPTER.encode(ListSurveyOverviewsRequest()),
          headers = mapOf(ClientVersionHeaders.CLIENT_VERSION to "1.0.0"),
        )
      assertEquals(0, webResp.grpcStatus)
    }
  }

  @Test
  fun interceptorBlocksOutdatedMobileClientAndAllowsPolicyCheck() = runTest {
    withTestServer(
      MobileClientVersionPolicy(
        latestVersion = "2.2.0",
        minRequiredVersion = "2.1.0",
        updateUrl = "https://example.com/download",
      )
    ) { baseUrl ->
      // Mobile client at 2.0.0 (below min 2.1.0): blocked with FAILED_PRECONDITION.
      val blockedResp =
        postGrpcWeb(
          baseUrl = baseUrl,
          path = "/openforis.collectone.v1.survey.SurveyService/ListSurveyOverviews",
          payload = ListSurveyOverviewsRequest.ADAPTER.encode(ListSurveyOverviewsRequest()),
          headers =
            mapOf(
              ClientVersionHeaders.CLIENT_PLATFORM to ClientVersionHeaders.PLATFORM_MOBILE,
              ClientVersionHeaders.CLIENT_VERSION to "2.0.0",
            ),
        )
      assertEquals(Status.Code.FAILED_PRECONDITION.value(), blockedResp.grpcStatus)
      assertEquals(
        "2.1.0",
        blockedResp.allMetadata[ClientVersionHeaders.MIN_REQUIRED_MOBILE_VERSION],
      )
      assertTrue(blockedResp.grpcMessage.contains("2.1.0"))

      // Blocked mobile client can still call GetClientVersionPolicy to re-check.
      val checkResp =
        postGrpcWeb(
          baseUrl = baseUrl,
          path = "/openforis.collectone.v1.userdata.ClientVersionService/GetClientVersionPolicy",
          payload = GetClientVersionPolicyRequest.ADAPTER.encode(GetClientVersionPolicyRequest()),
          headers =
            mapOf(
              ClientVersionHeaders.CLIENT_PLATFORM to ClientVersionHeaders.PLATFORM_MOBILE,
              ClientVersionHeaders.CLIENT_VERSION to "2.0.0",
            ),
        )
      assertEquals(0, checkResp.grpcStatus)
      val decoded = GetClientVersionPolicyResponse.ADAPTER.decode(checkResp.messages.single())
      assertEquals("2.1.0", decoded.policy?.min_required_mobile_version)
    }
  }

  private fun withTestServer(policy: MobileClientVersionPolicy, block: (baseUrl: String) -> Unit) {
    val backend =
      UserDataBackend(
        store = InMemoryUserDataStore(),
        defaultMobileClientVersionPolicy = policy,
      )
    val server = UserDataServer.create(backend, port = 0)
    server.start().join()
    try {
      block("http://127.0.0.1:${server.activeLocalPort()}")
    } finally {
      server.stop().join()
    }
  }

  private data class GrpcWebResult(
    val grpcStatus: Int,
    val grpcMessage: String,
    val messages: List<ByteArray>,
    val allMetadata: Map<String, String>,
  )

  private fun postGrpcWeb(
    baseUrl: String,
    path: String,
    payload: ByteArray,
    headers: Map<String, String>,
  ): GrpcWebResult {
    val frame = ByteArray(5 + payload.size)
    frame[0] = 0
    ByteBuffer.wrap(frame, 1, 4).putInt(payload.size)
    payload.copyInto(frame, destinationOffset = 5)

    val builder =
      HttpRequest.newBuilder(URI.create(baseUrl + path))
        .header("content-type", "application/grpc-web+proto")
        .header("accept", "application/grpc-web+proto")
        .header("x-grpc-web", "1")
        .POST(HttpRequest.BodyPublishers.ofByteArray(frame))
    headers.forEach { (k, v) -> builder.header(k, v) }

    val response =
      HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
    assertEquals(200, response.statusCode())

    val metadata = linkedMapOf<String, String>()
    response.headers().map().forEach { (k, v) ->
      if (v.isNotEmpty()) metadata[k.lowercase()] = v.first()
    }
    val messages = mutableListOf<ByteArray>()
    val body = response.body()
    var offset = 0
    while (offset + 5 <= body.size) {
      val flags = body[offset].toInt() and 0xFF
      val len = ByteBuffer.wrap(body, offset + 1, 4).int
      val chunk = body.copyOfRange(offset + 5, offset + 5 + len)
      offset += 5 + len
      if (flags and 0x80 != 0) {
        chunk.decodeToString().split("\r\n").forEach { line ->
          if (':' in line) {
            metadata[line.substringBefore(':').trim().lowercase()] = line.substringAfter(':').trim()
          }
        }
      } else {
        messages += chunk
      }
    }
    return GrpcWebResult(
      grpcStatus = metadata["grpc-status"]?.toIntOrNull() ?: -1,
      grpcMessage = metadata["grpc-message"].orEmpty(),
      messages = messages,
      allMetadata = metadata,
    )
  }
}
