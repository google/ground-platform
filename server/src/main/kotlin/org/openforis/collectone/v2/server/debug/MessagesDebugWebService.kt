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

import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.common.ResponseHeaders
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.ServerBuilder
import org.openforis.collectone.v2.server.messaging.MessageLogRecorder
import org.openforis.collectone.v2.server.messaging.MessageLogRecorder.Companion.toJsonArrayString

/**
 * HTTP endpoints for the Collect One Message Inspector debug web UI:
 * - `GET /debug/messages`: Single-page UI for inspecting sent and logged invitation emails.
 * - `GET /debug/messages/api/messages`: Snapshot of recorded invitation emails in JSON.
 * - `GET /debug/messages/api/stream`: Server-Sent Events stream of live email deliveries.
 * - `POST /debug/messages/api/clear`: Clears the in-memory email log.
 */
object MessagesDebugWebService {
  private const val PAGE_RESOURCE = "/debug/messages.html"

  private val pageBytes: ByteArray by lazy {
    checkNotNull(MessagesDebugWebService::class.java.getResourceAsStream(PAGE_RESOURCE)) {
        "Missing classpath resource $PAGE_RESOURCE"
      }
      .use { it.readBytes() }
  }

  /** Registers the Message Inspector routes on [server]. */
  fun register(server: ServerBuilder, recorder: MessageLogRecorder) {
    server.service("/debug/messages", pageService())
    server.service("/debug/messages/", pageService())
    server.service("/debug/messages/api/messages", listMessagesService(recorder))
    server.service("/debug/messages/api/stream", streamMessagesService(recorder))
    server.service("/debug/messages/api/clear", clearMessagesService(recorder))
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

  private fun listMessagesService(recorder: MessageLogRecorder): HttpService = HttpService { _, _ ->
    HttpResponse.of(
      ResponseHeaders.builder(HttpStatus.OK)
        .contentType(MediaType.JSON_UTF_8)
        .add("cache-control", "no-store")
        .build(),
      HttpData.ofUtf8(recorder.snapshot().toJsonArrayString()),
    )
  }

  private fun streamMessagesService(recorder: MessageLogRecorder): HttpService =
    HttpService { _, _ ->
      recorder.openEventStream()
    }

  private fun clearMessagesService(recorder: MessageLogRecorder): HttpService =
    HttpService { _, req ->
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
}
