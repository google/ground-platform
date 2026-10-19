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
package org.groundplatform.v2.server.userdata

import com.linecorp.armeria.server.Server
import groundplatform.v2.survey.ListSurveyOverviewsRequest
import groundplatform.v2.survey.ListSurveyOverviewsResponse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies `POST /debug/reset` on [UserDataServer]. */
class DebugResetTest {
  private val httpClient = HttpClient.newHttpClient()
  private val recorder = RpcCallRecorder()
  private lateinit var server: Server
  private val baseUrl
    get() = "http://127.0.0.1:${server.activeLocalPort()}"

  private fun start(dataResetEnabled: Boolean) {
    server =
      UserDataServer.create(
        sampleBackend(),
        port = 0,
        rpcRecorder = recorder,
        dataResetEnabled = dataResetEnabled,
      )
    server.start().join()
  }

  @AfterTest
  fun stop() {
    server.stop().join()
  }

  @Test
  fun reset_startsNewDataEpochAndClearsRpcLog() {
    start(dataResetEnabled = true)
    val token = listSurveyOverviews(syncToken = "").single().next_sync_token
    // Before the reset, the token is good for a delta poll.
    assertEquals(1, listSurveyOverviews(syncToken = token).size)

    val response = post("/debug/reset")

    assertEquals(200, response.statusCode())
    assertTrue(recorder.snapshot().isEmpty())
    // Tokens from before the reset name the old data epoch, so the service rejects them.
    assertTrue(listSurveyOverviews(syncToken = token).isEmpty())
  }

  @Test
  fun reset_needsPost() {
    start(dataResetEnabled = true)

    val response =
      httpClient.send(
        HttpRequest.newBuilder(URI("$baseUrl/debug/reset")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
      )

    assertEquals(405, response.statusCode())
  }

  @Test
  fun reset_isOffByDefault() {
    start(dataResetEnabled = false)

    assertEquals(404, post("/debug/reset").statusCode())
  }

  private fun post(path: String): HttpResponse<String> =
    httpClient.send(
      HttpRequest.newBuilder(URI(baseUrl + path)).POST(HttpRequest.BodyPublishers.noBody()).build(),
      HttpResponse.BodyHandlers.ofString(),
    )

  /** The responses of a gRPC-Web ListSurveyOverviews call; empty when it fails. */
  private fun listSurveyOverviews(syncToken: String): List<ListSurveyOverviewsResponse> {
    val request =
      ListSurveyOverviewsRequest.ADAPTER.encode(ListSurveyOverviewsRequest(sync_token = syncToken))
    val body = ByteBuffer.allocate(5 + request.size).put(0).putInt(request.size).put(request)
    val response =
      httpClient.send(
        HttpRequest.newBuilder(
            URI("$baseUrl/groundplatform.v2.survey.SurveyService/ListSurveyOverviews")
          )
          .header("content-type", "application/grpc-web+proto")
          .header("x-grpc-web", "1")
          .POST(HttpRequest.BodyPublishers.ofByteArray(body.array()))
          .build(),
        HttpResponse.BodyHandlers.ofByteArray(),
      )
    assertEquals(200, response.statusCode())
    if (response.headers().firstValue("grpc-status").isPresent) return emptyList()
    val buffer = ByteBuffer.wrap(response.body())
    val messages = mutableListOf<ListSurveyOverviewsResponse>()
    while (buffer.hasRemaining()) {
      val flags = buffer.get().toInt()
      val payload = ByteArray(buffer.getInt()).also { buffer.get(it) }
      if (flags and 0x80 == 0) messages += ListSurveyOverviewsResponse.ADAPTER.decode(payload)
    }
    return messages
  }
}
