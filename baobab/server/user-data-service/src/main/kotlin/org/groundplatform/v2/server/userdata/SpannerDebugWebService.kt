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

import com.google.cloud.spanner.SpannerException
import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.common.ResponseHeaders
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.ServerBuilder
import com.squareup.moshi.Moshi
import java.util.concurrent.CompletableFuture

/** The pieces behind the Spanner debug web UI, when the service is connected to Spanner. */
class SpannerDebug(val connection: SpannerConnection, val recorder: SpannerOperationRecorder) {
  val inspector =
    SpannerInspector(
      inspectorClient = connection.inspectorClient,
      queryClient = connection.databaseClient,
      databaseAdmin = connection.databaseAdmin,
      settings = connection.settings,
    )
}

/**
 * HTTP endpoints for the Spanner debug web UI, linked from the RPC debug UI:
 * - `GET /debug/spanner`: Single-page UI for browsing tables, running read-only SQL, viewing the
 *   schema DDL, and watching database operations live.
 * - `GET /debug/spanner/api/status`: Whether Spanner is configured, and which database.
 * - `GET /debug/spanner/api/tables`: Tables with columns, keys, and indexes.
 * - `GET /debug/spanner/api/tables/{table}/rows?limit=&offset=`: A page of rows.
 * - `GET /debug/spanner/api/tables/{table}/count`: Row count.
 * - `GET /debug/spanner/api/ddl`: Schema DDL statements.
 * - `POST /debug/spanner/api/query` with `{"sql": "…"}`: Runs read-only SQL (recorded).
 * - `GET /debug/spanner/api/operations`, `GET /debug/spanner/api/operations/stream` (SSE), and
 *   `POST /debug/spanner/api/operations/clear`: The operation log.
 *
 * Without Spanner ([debug] is null), the page explains how to connect and the API returns
 * `{"configured": false}`.
 */
object SpannerDebugWebService {
  private const val PAGE_RESOURCE = "/debug/spanner.html"
  private const val DEFAULT_PAGE_SIZE = 50

  private val requestAdapter = Moshi.Builder().build().adapter(Map::class.java)

  private val pageBytes: ByteArray by lazy {
    checkNotNull(SpannerDebugWebService::class.java.getResourceAsStream(PAGE_RESOURCE)) {
        "Missing classpath resource $PAGE_RESOURCE"
      }
      .use { it.readBytes() }
  }

  /** Adds the Spanner debug routes to [server]. */
  fun register(server: ServerBuilder, debug: SpannerDebug?) {
    server.service("/debug/spanner", pageService())
    server.service("/debug/spanner/api/status", statusService(debug))
    server.service(
      "/debug/spanner/api/tables",
      api(debug) { d, _, _ ->
        mapOf("tables" to d.inspector.tables().map { it.toMap() })
      },
    )
    server.service(
      "/debug/spanner/api/tables/{table}/rows",
      api(debug) { d, ctx, _ ->
        val params = ctx.queryParams()
        d.inspector
          .rows(
            table = ctx.pathParam("table").orEmpty(),
            limit = params.get("limit")?.toIntOrNull() ?: DEFAULT_PAGE_SIZE,
            offset = params.get("offset")?.toIntOrNull() ?: 0,
          )
          .toMap()
      },
    )
    server.service(
      "/debug/spanner/api/tables/{table}/count",
      api(debug) { d, ctx, _ ->
        mapOf("count" to d.inspector.rowCount(ctx.pathParam("table").orEmpty()))
      },
    )
    server.service(
      "/debug/spanner/api/ddl",
      api(debug) { d, _, _ ->
        mapOf("statements" to d.inspector.ddl())
      },
    )
    server.service(
      "/debug/spanner/api/query",
      api(debug, HttpMethod.POST) { d, _, body ->
        val sql = (requestAdapter.fromJson(body) as? Map<*, *>)?.get("sql") as? String ?: ""
        d.inspector.runQuery(sql).toMap()
      },
    )
    server.service(
      "/debug/spanner/api/operations",
      api(debug) { d, _, _ ->
        mapOf("operations" to d.recorder.snapshot().map { it.toMap() })
      },
    )
    server.service(
      "/debug/spanner/api/operations/clear",
      api(debug, HttpMethod.POST) { d, _, _ ->
        d.recorder.clear()
        mapOf("cleared" to true)
      },
    )
    server.service("/debug/spanner/api/operations/stream") { _, _ ->
      debug?.recorder?.openEventStream() ?: json(HttpStatus.OK, mapOf("configured" to false))
    }
  }

  private fun pageService(): HttpService = HttpService { _, _ ->
    HttpResponse.of(
      ResponseHeaders.builder(HttpStatus.OK)
        .contentType(MediaType.HTML_UTF_8)
        .add("cache-control", "no-store")
        .build(),
      HttpData.wrap(pageBytes),
    )
  }

  private fun statusService(debug: SpannerDebug?): HttpService = HttpService { _, _ ->
    val settings = debug?.connection?.settings
    json(
      HttpStatus.OK,
      if (settings == null) {
        mapOf("configured" to false)
      } else {
        mapOf(
          "configured" to true,
          "projectId" to settings.projectId,
          "instanceId" to settings.instanceId,
          "databaseId" to settings.databaseId,
          "emulatorHost" to settings.emulatorHost,
          "description" to settings.description,
        )
      },
    )
  }

  /**
   * A JSON API endpoint running [handle] on a blocking-task thread (Spanner calls block). Requires
   * Spanner; `IllegalArgumentException`s become 400s and Spanner errors 502s.
   */
  private fun api(
    debug: SpannerDebug?,
    method: HttpMethod = HttpMethod.GET,
    handle:
      (
        debug: SpannerDebug, ctx: com.linecorp.armeria.server.ServiceRequestContext, body: String,
      ) -> Any?,
  ): HttpService = HttpService { ctx, req ->
    if (req.method() != method) return@HttpService HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED)
    if (debug == null) return@HttpService json(HttpStatus.OK, mapOf("configured" to false))
    val response =
      req
        .aggregate()
        .thenApplyAsync(
          { aggregated ->
            try {
              json(HttpStatus.OK, handle(debug, ctx, aggregated.contentUtf8()))
            } catch (e: IllegalArgumentException) {
              json(HttpStatus.BAD_REQUEST, mapOf("error" to e.message))
            } catch (e: SpannerException) {
              json(HttpStatus.BAD_GATEWAY, mapOf("error" to e.message, "code" to e.errorCode.name))
            }
          },
          ctx.blockingTaskExecutor(),
        )
    HttpResponse.of(response as CompletableFuture<HttpResponse>)
  }

  private fun json(status: HttpStatus, value: Any?): HttpResponse =
    HttpResponse.of(
      ResponseHeaders.builder(status)
        .contentType(MediaType.JSON_UTF_8)
        .add("cache-control", "no-store")
        .build(),
      HttpData.ofUtf8(SpannerOperationRecorder.jsonOf(value)),
    )
}
