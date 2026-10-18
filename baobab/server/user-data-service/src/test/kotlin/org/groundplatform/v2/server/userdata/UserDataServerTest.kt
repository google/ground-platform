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
import groundplatform.v2.survey.GetSurveyRequest
import groundplatform.v2.survey.GetSurveyResponse
import groundplatform.v2.survey.ListSurveyOverviewsRequest
import groundplatform.v2.survey.ListSurveyOverviewsResponse
import groundplatform.v2.survey.SurveyServiceServer
import io.grpc.CallOptions
import io.grpc.ManagedChannelBuilder
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.ClientCalls
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** Calls a running server over gRPC-Web (as browsers and the app do) and over native gRPC. */
class UserDataServerTest {
  private lateinit var server: Server
  private val baseUrl
    get() = "http://127.0.0.1:${server.activeLocalPort()}"

  @BeforeTest
  fun start() {
    server = UserDataServer.create(UserDataBackend(loadSeed(null)), port = 0)
    server.start().join()
  }

  @AfterTest
  fun stop() {
    server.stop().join()
  }

  @Test
  fun grpcWeb_unaryCalls_andSyncTokens() {
    val survey =
      grpcWeb(
          "/groundplatform.v2.survey.SurveyService/GetSurvey",
          GetSurveyRequest.ADAPTER.encode(GetSurveyRequest(survey_id = KENYA)),
        )
        .messages
        .single()
        .let(GetSurveyResponse.ADAPTER::decode)
    assertEquals(KENYA, survey.survey?.survey_id)

    val overviews = listOverviews(ListSurveyOverviewsRequest())
    assertEquals(6, overviews.overviews.size)
    val delta = listOverviews(ListSurveyOverviewsRequest(sync_token = overviews.next_sync_token))
    assertTrue(delta.overviews.isEmpty())
    assertTrue(delta.next_sync_token.isNotEmpty())
  }

  @Test
  fun grpcWeb_reportsTokenErrorsAsGrpcStatuses() {
    val malformed =
      grpcWeb(
        LIST_SURVEY_OVERVIEWS,
        ListSurveyOverviewsRequest.ADAPTER.encode(ListSurveyOverviewsRequest(sync_token = "bad")),
      )
    assertEquals(Status.Code.INVALID_ARGUMENT.value(), malformed.status)

    // A token from another server process (another data epoch) can't be served.
    val otherToken =
      kotlinx.coroutines.runBlocking {
        UserDataBackend(loadSeed(null))
          .ListSurveyOverviews(ListSurveyOverviewsRequest())
          .next_sync_token
      }
    val stale =
      grpcWeb(
        LIST_SURVEY_OVERVIEWS,
        ListSurveyOverviewsRequest.ADAPTER.encode(
          ListSurveyOverviewsRequest(sync_token = otherToken)
        ),
      )
    assertEquals(Status.Code.FAILED_PRECONDITION.value(), stale.status)
  }

  @Test
  fun nativeGrpc_unary() {
    val definition =
      WireGrpcBindings.bind(
        SurveyServiceServer::class.java,
        UserDataBackend(),
        CoroutineScope(Dispatchers.Default),
      )
    @Suppress("UNCHECKED_CAST")
    val getSurvey =
      definition.getMethod("groundplatform.v2.survey.SurveyService/GetSurvey").methodDescriptor
        as MethodDescriptor<Any, Any>
    val channel =
      ManagedChannelBuilder.forAddress("127.0.0.1", server.activeLocalPort()).usePlaintext().build()
    try {
      val response =
        ClientCalls.blockingUnaryCall(
          channel,
          getSurvey,
          CallOptions.DEFAULT,
          GetSurveyRequest(survey_id = KENYA),
        ) as GetSurveyResponse
      assertEquals(KENYA, response.survey?.survey_id)

      @Suppress("UNCHECKED_CAST")
      val listOverviews =
        definition
          .getMethod("groundplatform.v2.survey.SurveyService/ListSurveyOverviews")
          .methodDescriptor as MethodDescriptor<Any, Any>
      val error =
        assertFailsWith<StatusRuntimeException> {
          ClientCalls.blockingUnaryCall(
            channel,
            listOverviews,
            CallOptions.DEFAULT,
            ListSurveyOverviewsRequest(sync_token = "bad"),
          )
        }
      assertEquals(Status.Code.INVALID_ARGUMENT, error.status.code)
    } finally {
      channel.shutdownNow()
    }
  }

  @Test
  fun healthCheck_isServing() {
    val response =
      HttpClient.newHttpClient()
        .send(
          HttpRequest.newBuilder(URI("$baseUrl/healthz")).GET().build(),
          HttpResponse.BodyHandlers.discarding(),
        )
    assertEquals(200, response.statusCode())
  }

  private fun listOverviews(request: ListSurveyOverviewsRequest): ListSurveyOverviewsResponse {
    val response =
      grpcWeb(LIST_SURVEY_OVERVIEWS, ListSurveyOverviewsRequest.ADAPTER.encode(request))
    assertEquals(0, response.status)
    return ListSurveyOverviewsResponse.ADAPTER.decode(response.messages.single())
  }

  /** Response messages and grpc-status (from the headers or the trailers) of a gRPC-Web call. */
  private class GrpcWebResponse(val messages: List<ByteArray>, val status: Int?)

  /** Sends one gRPC-Web request and returns its messages and status. */
  private fun grpcWeb(path: String, request: ByteArray): GrpcWebResponse {
    val body = ByteBuffer.allocate(5 + request.size).put(0).putInt(request.size).put(request)
    val response =
      HttpClient.newHttpClient()
        .send(
          HttpRequest.newBuilder(URI(baseUrl + path))
            .header("content-type", "application/grpc-web+proto")
            .header("x-grpc-web", "1")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.array()))
            .build(),
          HttpResponse.BodyHandlers.ofByteArray(),
        )
    assertEquals(200, response.statusCode())
    // A trailers-only response carries the status in its headers.
    response.headers().firstValue("grpc-status").orElse(null)?.let { status ->
      return GrpcWebResponse(emptyList(), status.toInt())
    }
    val buffer = ByteBuffer.wrap(response.body())
    val messages = mutableListOf<ByteArray>()
    var status: Int? = null
    while (buffer.hasRemaining()) {
      val flags = buffer.get().toInt()
      val payload = ByteArray(buffer.getInt()).also { buffer.get(it) }
      if (flags and 0x80 != 0) {
        status =
          payload
            .decodeToString()
            .lines()
            .firstOrNull { it.startsWith("grpc-status:") }
            ?.substringAfter(':')
            ?.trim()
            ?.toInt()
      } else {
        messages += payload
      }
    }
    return GrpcWebResponse(messages, status)
  }

  companion object {
    const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"
    const val LIST_SURVEY_OVERVIEWS = "/groundplatform.v2.survey.SurveyService/ListSurveyOverviews"
  }
}
