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
package org.openforis.collectone.v2.server.debug

import com.linecorp.armeria.server.Server
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import openforis.collectone.v1.survey.GetSurveyRequest
import org.openforis.collectone.v2.server.app.UserDataServer
import org.openforis.collectone.v2.server.service.sampleBackend

/**
 * Verifies that [UserDataServer] without the debug UI serves no debug routes and records no RPCs.
 */
class DebugUiDisabledTest {
  private val httpClient = HttpClient.newHttpClient()
  private val recorder = RpcCallRecorder()
  private lateinit var server: Server
  private val baseUrl
    get() = "http://127.0.0.1:${server.activeLocalPort()}"

  @BeforeTest
  fun start() {
    server =
      UserDataServer.create(
        sampleBackend(),
        port = 0,
        debugUiEnabled = false,
        rpcRecorder = recorder,
      )
    server.start().join()
  }

  @AfterTest
  fun stop() {
    server.stop().join()
  }

  @Test
  fun debugRoutes_areNotFound() {
    for (path in
      listOf(
        "/",
        "/debug",
        "/debug/rpcs",
        "/debug/rpcs/stream",
        "/debug/messages",
        "/debug/messages/api/messages",
        "/debug/spanner",
        "/debug/spanner/api/status",
      )) {
      assertEquals(404, get(path).statusCode(), "GET $path")
    }
  }

  @Test
  fun debugClearRoutes_areNotFound() {
    for (path in listOf("/debug/rpcs/clear", "/debug/messages/api/clear")) {
      assertEquals(404, post(path).statusCode(), "POST $path")
    }
  }

  @Test
  fun healthCheck_isStillServed() {
    assertEquals(200, get("/healthz").statusCode())
    assertEquals(200, get("/health").statusCode())
  }

  @Test
  fun rpcs_areServedButNotRecorded() {
    val request = GetSurveyRequest.ADAPTER.encode(GetSurveyRequest(survey_id = KENYA))
    val body = ByteBuffer.allocate(5 + request.size).put(0).putInt(request.size).put(request)

    val response =
      httpClient.send(
        HttpRequest.newBuilder(
            URI("$baseUrl/openforis.collectone.v1.survey.SurveyService/GetSurvey")
          )
          .header("content-type", "application/grpc-web+proto")
          .header("x-grpc-web", "1")
          .POST(HttpRequest.BodyPublishers.ofByteArray(body.array()))
          .build(),
        HttpResponse.BodyHandlers.ofByteArray(),
      )

    assertEquals(200, response.statusCode())
    assertTrue(response.body().isNotEmpty())
    assertTrue(recorder.snapshot().isEmpty())
  }

  private fun get(path: String): HttpResponse<String> =
    httpClient.send(
      HttpRequest.newBuilder(URI("$baseUrl$path")).GET().build(),
      HttpResponse.BodyHandlers.ofString(),
    )

  private fun post(path: String): HttpResponse<String> =
    httpClient.send(
      HttpRequest.newBuilder(URI("$baseUrl$path"))
        .POST(HttpRequest.BodyPublishers.noBody())
        .build(),
      HttpResponse.BodyHandlers.ofString(),
    )

  private companion object {
    const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"
  }
}
