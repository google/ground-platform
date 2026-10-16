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
import groundplatform.v2.survey.StreamSurveyOverviewsRequest
import groundplatform.v2.survey.SurveyOverview
import groundplatform.v2.survey.SurveyServiceServer
import io.grpc.CallOptions
import io.grpc.ManagedChannelBuilder
import io.grpc.MethodDescriptor
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
  fun grpcWeb_unaryAndServerStreaming() {
    val survey =
      grpcWeb(
          "/groundplatform.v2.survey.SurveyService/GetSurvey",
          GetSurveyRequest.ADAPTER.encode(GetSurveyRequest(survey_id = KENYA)),
        )
        .single()
        .let(GetSurveyResponse.ADAPTER::decode)
    assertEquals(KENYA, survey.survey?.survey_id)

    val overviews =
      grpcWeb(
          "/groundplatform.v2.survey.SurveyService/StreamSurveyOverviews",
          StreamSurveyOverviewsRequest.ADAPTER.encode(StreamSurveyOverviewsRequest()),
        )
        .map(SurveyOverview.ADAPTER::decode)
    assertEquals(6, overviews.size)
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

  /** Sends one gRPC-Web request and returns the response messages after checking grpc-status. */
  private fun grpcWeb(path: String, request: ByteArray): List<ByteArray> {
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
    val buffer = ByteBuffer.wrap(response.body())
    val messages = mutableListOf<ByteArray>()
    var trailers = ""
    while (buffer.hasRemaining()) {
      val flags = buffer.get().toInt()
      val payload = ByteArray(buffer.getInt()).also { buffer.get(it) }
      if (flags and 0x80 != 0) trailers = payload.decodeToString() else messages += payload
    }
    assertTrue("grpc-status:0" in trailers.replace(" ", ""), "trailers: $trailers")
    return messages
  }

  companion object {
    const val KENYA = "survey-kenya-coffee"
  }
}
