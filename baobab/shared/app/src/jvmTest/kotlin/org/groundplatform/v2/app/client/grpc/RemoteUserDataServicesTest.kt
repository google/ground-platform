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
package org.groundplatform.v2.app.client.grpc

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.WireRpc
import groundplatform.v2.data.DataServiceServer
import groundplatform.v2.library.LibraryServiceServer
import groundplatform.v2.survey.GetSurveyRequest
import groundplatform.v2.survey.GetSurveyResponse
import groundplatform.v2.survey.OrganizationServiceServer
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyServiceServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.reflect.full.callSuspend
import kotlin.reflect.jvm.kotlinFunction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking

/**
 * Verifies that every remote adapter calls the path declared by the Wire `@WireRpc` annotation, and
 * that [GrpcWebTransport] frames requests and parses responses and trailers.
 */
class RemoteUserDataServicesTest {
  private val trailerOk = GrpcWebTransport.frame("grpc-status:0\r\n".encodeToByteArray(), 0x80)

  @Test
  fun everyRpc_callsItsWireRpcPath() = runBlocking {
    val requestedPaths = mutableListOf<String>()
    var responseAdapter: ProtoAdapter<Any>? = null
    val engine = MockEngine { request ->
      requestedPaths += request.url.encodedPath
      assertEquals("1", request.headers["X-Grpc-Web"])
      val body = responseAdapter!!.encode(responseAdapter!!.newBuilderDefault())
      respond(
        content = GrpcWebTransport.frame(body) + trailerOk,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/grpc-web+proto"),
      )
    }
    val client = remoteUserDataGrpcClient(GrpcWebTransport("http://test", HttpClient(engine)))
    val services =
      listOf(
        SurveyServiceServer::class.java to client.surveyService,
        OrganizationServiceServer::class.java to client.organizationService,
        LibraryServiceServer::class.java to client.libraryService,
        DataServiceServer::class.java to client.dataService,
      )
    var rpcCount = 0
    for ((serviceInterface, implementation) in services) {
      for (method in serviceInterface.methods) {
        val rpc = method.getAnnotation(WireRpc::class.java) ?: continue
        @Suppress("UNCHECKED_CAST")
        responseAdapter = ProtoAdapter.get(rpc.responseAdapter) as ProtoAdapter<Any>
        val request = method.parameterTypes[0].getDeclaredConstructor().newInstance()
        val function = method.kotlinFunction!!
        if (method.parameterTypes.any { SendChannel::class.java.isAssignableFrom(it) }) {
          val channel = Channel<Any>(Channel.UNLIMITED)
          function.callSuspend(implementation, request, channel as SendChannel<Any>)
          channel.close()
          assertEquals(1, channel.toList().size, rpc.path)
        } else {
          function.callSuspend(implementation, request)
        }
        assertEquals(rpc.path, requestedPaths.last())
        rpcCount++
      }
    }
    assertEquals(34, rpcCount)
  }

  @Test
  fun unary_decodesTheResponseMessage() = runBlocking {
    val expected = GetSurveyResponse(survey = SurveyDef(survey_id = "s1", title = "Survey"))
    val engine = MockEngine { request ->
      val body = request.body.toByteArray()
      // 5-byte frame header, then the encoded request.
      assertEquals(0, body[0].toInt())
      assertEquals(
        GetSurveyRequest(survey_id = "s1"),
        GetSurveyRequest.ADAPTER.decode(body.copyOfRange(5, body.size)),
      )
      respond(
        GrpcWebTransport.frame(GetSurveyResponse.ADAPTER.encode(expected)) + trailerOk,
        headers = grpcWebHeaders,
      )
    }
    val service = RemoteSurveyService(GrpcWebTransport("http://test/", HttpClient(engine)))
    assertEquals(expected, service.GetSurvey(GetSurveyRequest(survey_id = "s1")))
  }

  @Test
  fun errorStatus_inTrailersOrHeaders_throws() = runBlocking {
    val inTrailers = MockEngine {
      respond(
        GrpcWebTransport.frame(
          "grpc-status:5\r\ngrpc-message:can%27t%20find%20it\r\n".encodeToByteArray(),
          0x80,
        ),
        headers = grpcWebHeaders,
      )
    }
    val trailerError =
      assertFailsWith<GrpcWebException> {
        RemoteSurveyService(GrpcWebTransport("http://test", HttpClient(inTrailers)))
          .GetSurvey(GetSurveyRequest())
      }
    assertEquals(5, trailerError.status)
    assertTrue("can't find it" in trailerError.message!!, trailerError.message)

    val trailersOnly = MockEngine {
      respond(
        ByteArray(0),
        headers =
          headersOf(
            HttpHeaders.ContentType to listOf("application/grpc-web+proto"),
            "grpc-status" to listOf("16"),
          ),
      )
    }
    val headerError =
      assertFailsWith<GrpcWebException> {
        RemoteSurveyService(GrpcWebTransport("http://test", HttpClient(trailersOnly)))
          .GetSurvey(GetSurveyRequest())
      }
    assertEquals(GrpcWebTransport.UNAUTHENTICATED, headerError.status)
  }

  @Test
  fun nonGrpcWebResponse_throwsAClearError() = runBlocking {
    // For example, another web server on the configured port answering with an HTML page.
    val htmlPage = MockEngine {
      respond("<html></html>", headers = headersOf(HttpHeaders.ContentType, "text/html"))
    }
    val error =
      assertFailsWith<GrpcWebException> {
        RemoteSurveyService(GrpcWebTransport("http://test", HttpClient(htmlPage)))
          .GetSurvey(GetSurveyRequest())
      }
    assertTrue("USER_DATA_SERVICE_URL" in error.description!!, error.description)
  }

  private val grpcWebHeaders = headersOf(HttpHeaders.ContentType, "application/grpc-web+proto")

  private fun ProtoAdapter<Any>.newBuilderDefault(): Any =
    checkNotNull(type).java.getDeclaredConstructor().newInstance()

  private fun OutgoingContent.toByteArray(): ByteArray =
    (this as OutgoingContent.ByteArrayContent).bytes()

  private suspend fun <T> Channel<T>.toList(): List<T> {
    val items = mutableListOf<T>()
    for (item in this) items += item
    return items
  }
}
