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

import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.common.ResponseHeaders
import com.linecorp.armeria.server.HttpService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.openforis.collectone.v2.server.userdata.RpcCallRecorder.Companion.toJsonArrayString

/**
 * HTTP endpoints for the Collect One RPC Debug Web UI:
 * - `GET /` and `GET /debug`: Single-page web UI for viewing, searching, and filtering RPCs.
 * - `GET /debug/rpcs`: Snapshot of recorded RPC entries in JSON.
 * - `GET /debug/rpcs/stream`: Server-Sent Events stream of live RPC completions.
 * - `POST /debug/rpcs/clear`: Clears the in-memory RPC log.
 * - `POST /debug/reset`: Restores the store's initial data and clears the RPC log (development
 *   only; see [UserDataServer.create]).
 */
object DebugWebService {
  private const val INDEX_RESOURCE = "/debug/index.html"

  private val indexHtmlBytes: ByteArray by lazy {
    checkNotNull(DebugWebService::class.java.getResourceAsStream(INDEX_RESOURCE)) {
        "Missing classpath resource $INDEX_RESOURCE"
      }
      .use { it.readBytes() }
  }

  /** Serves the single-page RPC debug web UI HTML. */
  fun indexService(): HttpService = HttpService { _, _ ->
    HttpResponse.of(
      ResponseHeaders.builder(HttpStatus.OK)
        .contentType(MediaType.HTML_UTF_8)
        .add("cache-control", "no-store")
        .build(),
      HttpData.wrap(indexHtmlBytes),
    )
  }

  /** Serves the current snapshot of recorded RPC calls as a JSON array. */
  fun listRpcsService(recorder: RpcCallRecorder): HttpService = HttpService { _, _ ->
    HttpResponse.of(
      ResponseHeaders.builder(HttpStatus.OK)
        .contentType(MediaType.JSON_UTF_8)
        .add("cache-control", "no-store")
        .build(),
      HttpData.ofUtf8(recorder.snapshot().toJsonArrayString()),
    )
  }

  /** Serves a live Server-Sent Events (`text/event-stream`) feed of incoming RPCs. */
  fun streamRpcsService(recorder: RpcCallRecorder): HttpService = HttpService { _, _ ->
    recorder.openEventStream()
  }

  /** Clears all recorded RPC entries when called via `POST`. */
  fun clearRpcsService(recorder: RpcCallRecorder): HttpService = HttpService { _, req ->
    if (req.method() != HttpMethod.POST && req.method() != HttpMethod.DELETE) {
      return@HttpService HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED)
    }
    recorder.clear()
    HttpResponse.of(
      ResponseHeaders.builder(HttpStatus.OK)
        .contentType(MediaType.JSON_UTF_8)
        .add("cache-control", "no-store")
        .build(),
      HttpData.ofUtf8("""{"cleared":true}"""),
    )
  }

  /**
   * Restores [backend]'s initial data and clears [recorder] when called via `POST`. The reset runs
   * in [scope], the dispatcher that serializes RPCs, so it never interleaves with one.
   */
  fun resetService(
    backend: UserDataBackend,
    recorder: RpcCallRecorder,
    scope: CoroutineScope,
  ): HttpService = HttpService { _, req ->
    if (req.method() != HttpMethod.POST) {
      return@HttpService HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED)
    }
    HttpResponse.of(
      scope
        .future {
          backend.reset()
          recorder.clear()
        }
        .thenApply {
          HttpResponse.of(
            ResponseHeaders.builder(HttpStatus.OK)
              .contentType(MediaType.JSON_UTF_8)
              .add("cache-control", "no-store")
              .build(),
            HttpData.ofUtf8("""{"reset":true}"""),
          )
        }
    )
  }
}
