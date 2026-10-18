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

import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.grpc.GrpcSerializationFormats
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.cors.CorsService
import com.linecorp.armeria.server.grpc.GrpcService
import com.linecorp.armeria.server.healthcheck.HealthCheckService
import groundplatform.v2.data.DataServiceServer
import groundplatform.v2.library.LibraryServiceServer
import groundplatform.v2.survey.OrganizationServiceServer
import groundplatform.v2.survey.SurveyServiceServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Builds the HTTP server for the user data service.
 *
 * One port serves native gRPC (HTTP/2, including cleartext h2c as used by Cloud Run) and gRPC-Web
 * (HTTP/1.1 or HTTP/2, for browsers), plus `GET /healthz`, the RPC debug web UI at `GET /` and `GET
 * /debug`, and the Spanner debug web UI at `GET /debug/spanner` ([SpannerDebugWebService]). CORS is
 * open so the Web Console can call the service from another origin; restrict it once authentication
 * is in place.
 */
object UserDataServer {
  fun create(
    backend: UserDataBackend,
    port: Int,
    rpcRecorder: RpcCallRecorder = RpcCallRecorder(),
    spannerDebug: SpannerDebug? = null,
  ): Server {
    // The in-memory backend isn't thread-safe, so every RPC runs on one serialized dispatcher.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    val grpcService =
      GrpcService.builder()
        .addService(WireGrpcBindings.bind(SurveyServiceServer::class.java, backend, scope))
        .addService(WireGrpcBindings.bind(OrganizationServiceServer::class.java, backend, scope))
        .addService(WireGrpcBindings.bind(LibraryServiceServer::class.java, backend, scope))
        .addService(WireGrpcBindings.bind(DataServiceServer::class.java, backend, scope))
        .intercept(rpcRecorder.interceptor())
        .supportedSerializationFormats(
          GrpcSerializationFormats.PROTO,
          GrpcSerializationFormats.PROTO_WEB,
          GrpcSerializationFormats.PROTO_WEB_TEXT,
        )
        .build()
    val cors =
      CorsService.builderForAnyOrigin()
        .allowRequestMethods(HttpMethod.POST, HttpMethod.OPTIONS)
        .allowRequestHeaders(
          "content-type",
          "x-grpc-web",
          "x-user-agent",
          "grpc-timeout",
          "authorization",
        )
        .exposeHeaders("grpc-status", "grpc-message", "grpc-status-details-bin")
        .newDecorator()
    val server =
      Server.builder()
        .http(port)
        .service(grpcService, cors)
        .service("/healthz", HealthCheckService.of())
        .service("/", DebugWebService.indexService())
        .service("/debug", DebugWebService.indexService())
        .service("/debug/rpcs", DebugWebService.listRpcsService(rpcRecorder))
        .service("/debug/rpcs/stream", DebugWebService.streamRpcsService(rpcRecorder))
        .service("/debug/rpcs/clear", DebugWebService.clearRpcsService(rpcRecorder))
    SpannerDebugWebService.register(server, spannerDebug)
    return server.build()
  }
}
