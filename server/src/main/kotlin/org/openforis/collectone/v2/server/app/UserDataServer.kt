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
package org.openforis.collectone.v2.server.app

import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.grpc.GrpcSerializationFormats
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.ServerBuilder
import com.linecorp.armeria.server.cors.CorsService
import com.linecorp.armeria.server.grpc.GrpcService
import com.linecorp.armeria.server.healthcheck.HealthCheckService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import openforis.collectone.v1.data.DataServiceServer
import openforis.collectone.v1.library.LibraryServiceServer
import openforis.collectone.v1.survey.GeoDataServiceServer
import openforis.collectone.v1.survey.OrganizationServiceServer
import openforis.collectone.v1.survey.SurveyServiceServer
import openforis.collectone.v1.sync.SyncServiceServer
import openforis.collectone.v1.userdata.ClientVersionServiceServer
import org.openforis.collectone.v2.core.version.ClientVersionHeaders
import org.openforis.collectone.v2.server.auth.Authenticator
import org.openforis.collectone.v2.server.auth.interceptor
import org.openforis.collectone.v2.server.debug.DebugWebService
import org.openforis.collectone.v2.server.debug.MessagesDebugWebService
import org.openforis.collectone.v2.server.debug.RpcCallRecorder
import org.openforis.collectone.v2.server.debug.SpannerDebug
import org.openforis.collectone.v2.server.debug.SpannerDebugWebService
import org.openforis.collectone.v2.server.messaging.MessageLogRecorder
import org.openforis.collectone.v2.server.service.UserDataBackend
import org.openforis.collectone.v2.server.web.BalloonAuthStore
import org.openforis.collectone.v2.server.web.BalloonAuthWebService
import org.openforis.collectone.v2.server.web.BalloonDraftStore
import org.openforis.collectone.v2.server.web.BalloonStateWebService
import org.openforis.collectone.v2.server.web.KmlExportWebService

/** How many RPC handlers run at once in one instance by default (`RPC_PARALLELISM`). */
const val DEFAULT_RPC_PARALLELISM = 8

/** The most RPC handlers `RPC_PARALLELISM` may let run at once in one instance. */
const val MAX_RPC_PARALLELISM = 64

/**
 * Builds the HTTP server for Collect One.
 *
 * One port serves native gRPC (HTTP/2, including cleartext h2c as used by Cloud Run) and gRPC-Web
 * (HTTP/1.1 or HTTP/2, for browsers), plus health checks at `GET /healthz` and `GET /health` (Cloud
 * Run's front end answers some paths ending in `z` itself, so checks from outside use `/health`).
 * CORS is open so the Web Console can call the service from another origin.
 *
 * With [debugUiEnabled] (development only: the debug UIs aren't authenticated and show every RPC,
 * email, and database row), it also serves the RPC debug web UI at `GET /` and `GET /debug`
 * ([DebugWebService]), the Message Inspector UI at `GET /debug/messages`
 * ([MessagesDebugWebService]), and the Spanner debug web UI at `GET /debug/spanner`
 * ([SpannerDebugWebService]), and records RPCs in [rpcRecorder]. Without it, those paths aren't
 * served and RPCs aren't recorded.
 *
 * With [dataResetEnabled] (development only, independent of [debugUiEnabled]), `POST /debug/reset`
 * also restores the store's initial data, as tests that change data need between runs.
 *
 * Up to `rpcParallelism` RPC handlers run at once in this instance (`RPC_PARALLELISM`).
 */
object UserDataServer {
  fun create(
    backend: UserDataBackend,
    port: Int,
    debugUiEnabled: Boolean = false,
    rpcRecorder: RpcCallRecorder = RpcCallRecorder(),
    messageLogRecorder: MessageLogRecorder = MessageLogRecorder(),
    spannerDebug: SpannerDebug? = null,
    authenticator: Authenticator = Authenticator.dev(),
    dataResetEnabled: Boolean = false,
    rpcParallelism: Int = DEFAULT_RPC_PARALLELISM,
    publicWebUrl: String = "",
  ): Server {
    require(rpcParallelism in 1..MAX_RPC_PARALLELISM) {
      "rpcParallelism must be from 1 to $MAX_RPC_PARALLELISM, not $rpcParallelism"
    }
    // Up to [rpcParallelism] RPC handlers run at once on this dispatcher between store calls; store
    // I/O runs on the store's own threads, so other RPCs proceed while one waits for it.
    // Correctness doesn't depend on the limit: each Mutate* RPC checks replays and versions in its
    // write transaction.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val scope =
      CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(rpcParallelism))
    fun <T : Any> bind(service: Class<T>) =
      WireGrpcBindings.bind(service, service.cast(backend), scope) { caller ->
        caller?.let { backend.registerCaller(it) }
      }
    val grpcService =
      GrpcService.builder()
        .addService(bind(SurveyServiceServer::class.java))
        .addService(bind(OrganizationServiceServer::class.java))
        .addService(bind(LibraryServiceServer::class.java))
        .addService(bind(DataServiceServer::class.java))
        .addService(bind(GeoDataServiceServer::class.java))
        .addService(bind(SyncServiceServer::class.java))
        .addService(bind(ClientVersionServiceServer::class.java))
        .apply { if (debugUiEnabled) intercept(rpcRecorder.interceptor()) }
        .intercept(ClientVersionInterceptor(backend::currentMobileClientVersionPolicy))
        .intercept(authenticator.interceptor())
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
          Authenticator.DEV_USER_ID_HEADER,
          Authenticator.DEV_USER_EMAIL_HEADER,
          Authenticator.DEV_USER_NAME_HEADER,
          Authenticator.DEV_ROLE_HEADER,
          ClientVersionHeaders.CLIENT_PLATFORM,
          ClientVersionHeaders.CLIENT_VERSION,
        )
        .exposeHeaders(
          "grpc-status",
          "grpc-message",
          "grpc-status-details-bin",
          ClientVersionHeaders.POLICY_CHECKED,
          ClientVersionHeaders.LATEST_MOBILE_VERSION,
          ClientVersionHeaders.MIN_REQUIRED_MOBILE_VERSION,
          ClientVersionHeaders.MOBILE_UPDATE_URL,
        )
        .maxAge(3600)
        .newDecorator()
    val server =
      Server.builder()
        .http(port)
        .service(grpcService, cors)
        .service("/healthz", HealthCheckService.of())
        .service("/health", HealthCheckService.of())
    registerBalloonServices(server, backend, authenticator, scope, publicWebUrl)
    if (debugUiEnabled) {
      server
        .service("/", DebugWebService.indexService())
        .service("/debug", DebugWebService.indexService())
        .service("/debug/", DebugWebService.indexService())
        .service("/debug/rpcs", DebugWebService.listRpcsService(rpcRecorder))
        .service("/debug/rpcs/stream", DebugWebService.streamRpcsService(rpcRecorder))
        .service("/debug/rpcs/clear", DebugWebService.clearRpcsService(rpcRecorder))
      MessagesDebugWebService.register(server, messageLogRecorder)
      SpannerDebugWebService.register(server, spannerDebug)
    }
    if (dataResetEnabled) {
      server.service(
        "/debug/reset",
        DebugWebService.resetService(backend, rpcRecorder, scope, messageLogRecorder),
      )
    }
    return server.build()
  }

  private fun registerBalloonServices(
    server: ServerBuilder,
    backend: UserDataBackend,
    authenticator: Authenticator,
    scope: CoroutineScope,
    publicWebUrl: String,
  ) {
    val authStore = BalloonAuthStore()
    val draftStore = BalloonDraftStore()
    val kmlCors = kmlCorsDecorator()
    val balloonJsonCors = balloonCorsDecorator()
    val defaultBalloonUrl =
      publicWebUrl
        .trim()
        .trimEnd('/')
        .takeIf { it.isNotEmpty() }
        ?.let {
          "$it/balloon/balloon-form.html"
        } ?: ""
    server
      .service(
        "/kml/surveys/:surveyId",
        KmlExportWebService.service(backend, scope, defaultBalloonUrl).decorate(kmlCors),
      )
      .service(
        "/balloon/auth/complete",
        BalloonAuthWebService.completeService(backend, authenticator, authStore, scope)
          .decorate(balloonJsonCors),
      )
      .service(
        "/balloon/auth/claim",
        BalloonAuthWebService.claimService(authStore).decorate(balloonJsonCors),
      )
      .service(
        "/balloon/state/:surveyId/:entityId",
        BalloonStateWebService.service(backend, draftStore, scope).decorate(balloonJsonCors),
      )
  }

  private fun kmlCorsDecorator() =
    CorsService.builderForAnyOrigin()
      .allowRequestMethods(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS)
      .allowRequestHeaders(
        "authorization",
        "if-none-match",
        Authenticator.DEV_USER_ID_HEADER,
        Authenticator.DEV_USER_EMAIL_HEADER,
        Authenticator.DEV_USER_NAME_HEADER,
        Authenticator.DEV_ROLE_HEADER,
      )
      .exposeHeaders("etag")
      .maxAge(3600)
      .newDecorator()

  private fun balloonCorsDecorator() =
    CorsService.builderForAnyOrigin()
      .allowRequestMethods(
        HttpMethod.GET,
        HttpMethod.POST,
        HttpMethod.DELETE,
        HttpMethod.OPTIONS,
      )
      .allowRequestHeaders(
        "content-type",
        "authorization",
        Authenticator.DEV_USER_ID_HEADER,
        Authenticator.DEV_USER_EMAIL_HEADER,
        Authenticator.DEV_USER_NAME_HEADER,
        Authenticator.DEV_ROLE_HEADER,
      )
      .maxAge(3600)
      .newDecorator()
}
