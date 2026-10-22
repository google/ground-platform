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
package org.openforis.collectone.v2.server.userdata

import com.linecorp.armeria.server.Server
import io.grpc.Status
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.ListSurveyOverviewsRequest
import openforis.collectone.v1.survey.ListSurveyOverviewsResponse

/** Verifies the RPC Debug Web UI and `/debug/rpcs*` endpoints on [UserDataServer]. */
class RpcDebugServiceTest {
  private lateinit var recorder: RpcCallRecorder
  private lateinit var server: Server
  private val httpClient = HttpClient.newHttpClient()
  private val baseUrl
    get() = "http://127.0.0.1:${server.activeLocalPort()}"

  @BeforeTest
  fun start() {
    recorder = RpcCallRecorder()
    server = UserDataServer.create(sampleBackend(), port = 0, rpcRecorder = recorder)
    server.start().join()
  }

  @AfterTest
  fun stop() {
    server.stop().join()
  }

  @Test
  fun debugIndex_servesHtmlOnRootAndDebugPath() {
    for (path in listOf("/", "/debug")) {
      val response =
        httpClient.send(
          HttpRequest.newBuilder(URI("$baseUrl$path")).GET().build(),
          HttpResponse.BodyHandlers.ofString(),
        )
      assertEquals(200, response.statusCode())
      assertTrue(
        response.headers().firstValue("content-type").orElse("").contains("text/html"),
        "Expected HTML content-type for $path",
      )
      assertTrue("Collect One RPC Inspector" in response.body())
    }
  }

  @Test
  fun rpcCalls_areRecordedWithProtoJsonDeltaDetectionAndCleared() {
    // 1. Standard lookup RPC.
    sendGrpcWeb(
      "/openforis.collectone.v1.survey.SurveyService/GetSurvey",
      GetSurveyRequest.ADAPTER.encode(GetSurveyRequest(survey_id = KENYA)),
    )

    // 2. Initial ListSurveyOverviews query (no sync_token -> 6 items).
    val initialBytes =
      sendGrpcWeb(
        LIST_SURVEY_OVERVIEWS,
        ListSurveyOverviewsRequest.ADAPTER.encode(ListSurveyOverviewsRequest()),
      )
    val initialResponse = ListSurveyOverviewsResponse.ADAPTER.decode(initialBytes.single())

    // 3. Delta poll with sync_token returning 0 changes (isEmptyDeltaPoll = true).
    sendGrpcWeb(
      LIST_SURVEY_OVERVIEWS,
      ListSurveyOverviewsRequest.ADAPTER.encode(
        ListSurveyOverviewsRequest(sync_token = initialResponse.next_sync_token)
      ),
    )

    // 4. Malformed sync_token returning INVALID_ARGUMENT.
    sendGrpcWeb(
      LIST_SURVEY_OVERVIEWS,
      ListSurveyOverviewsRequest.ADAPTER.encode(ListSurveyOverviewsRequest(sync_token = "bad")),
    )

    val entries = recorder.snapshot()
    assertEquals(4, entries.size)

    val getSurvey = entries[0]
    assertEquals("SurveyService", getSurvey.service)
    assertEquals("GetSurvey", getSurvey.method)
    assertEquals("/openforis.collectone.v1.survey.SurveyService/GetSurvey", getSurvey.fullPath)
    assertEquals("gRPC-Web", getSurvey.transport)
    assertEquals(0, getSurvey.statusCode)
    assertEquals("OK", getSurvey.statusName)
    assertFalse(getSurvey.isDeltaPoll)
    assertFalse(getSurvey.isEmptyDeltaPoll)
    assertTrue("survey=$KENYA → OK" in getSurvey.summary, "summary: ${getSurvey.summary}")
    assertTrue(KENYA in getSurvey.requestJson, "requestJson: ${getSurvey.requestJson}")
    assertTrue(KENYA in getSurvey.responseJson, "responseJson: ${getSurvey.responseJson}")

    val listQuery = entries[1]
    assertEquals("SurveyService", listQuery.service)
    assertEquals("ListSurveyOverviews", listQuery.method)
    assertFalse(listQuery.isDeltaPoll)
    assertFalse(listQuery.isEmptyDeltaPoll)
    assertEquals(6, listQuery.itemCount)
    assertTrue("6 items" in listQuery.summary, "summary: ${listQuery.summary}")

    val emptyDelta = entries[2]
    assertEquals("ListSurveyOverviews", emptyDelta.method)
    assertTrue(emptyDelta.isDeltaPoll)
    assertTrue(emptyDelta.isEmptyDeltaPoll)
    assertEquals(0, emptyDelta.itemCount)
    assertTrue("delta poll → 0 items" in emptyDelta.summary, "summary: ${emptyDelta.summary}")

    val badToken = entries[3]
    assertEquals(Status.Code.INVALID_ARGUMENT.value(), badToken.statusCode)
    assertEquals("INVALID_ARGUMENT", badToken.statusName)
    assertTrue(!badToken.errorMessage.isNullOrBlank())
    assertTrue(badToken.isDeltaPoll)
    assertFalse(badToken.isEmptyDeltaPoll)

    val jsonResponse =
      httpClient.send(
        HttpRequest.newBuilder(URI("$baseUrl/debug/rpcs")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(200, jsonResponse.statusCode())
    assertTrue("GetSurvey" in jsonResponse.body())
    assertTrue("ListSurveyOverviews" in jsonResponse.body())

    val clearResponse =
      httpClient.send(
        HttpRequest.newBuilder(URI("$baseUrl/debug/rpcs/clear"))
          .POST(HttpRequest.BodyPublishers.noBody())
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(200, clearResponse.statusCode())
    assertTrue(recorder.snapshot().isEmpty())
  }

  private fun sendGrpcWeb(path: String, request: ByteArray): List<ByteArray> {
    val body = ByteBuffer.allocate(5 + request.size).put(0).putInt(request.size).put(request)
    val response =
      httpClient.send(
        HttpRequest.newBuilder(URI(baseUrl + path))
          .header("content-type", "application/grpc-web+proto")
          .header("x-grpc-web", "1")
          .POST(HttpRequest.BodyPublishers.ofByteArray(body.array()))
          .build(),
        HttpResponse.BodyHandlers.ofByteArray(),
      )
    assertEquals(200, response.statusCode())
    if (response.headers().firstValue("grpc-status").isPresent) return emptyList()
    val buffer = ByteBuffer.wrap(response.body())
    val messages = mutableListOf<ByteArray>()
    while (buffer.hasRemaining()) {
      val flags = buffer.get().toInt()
      val payload = ByteArray(buffer.getInt()).also { buffer.get(it) }
      if (flags and 0x80 == 0) messages += payload
    }
    return messages
  }

  companion object {
    private const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"
    private const val LIST_SURVEY_OVERVIEWS =
      "/openforis.collectone.v1.survey.SurveyService/ListSurveyOverviews"
  }
}
