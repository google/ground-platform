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
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpResponseWriter
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.common.ResponseHeaders
import java.util.concurrent.CopyOnWriteArraySet

/** Broadcasts named Server-Sent Events to every connected debug web UI stream. */
class SseBroadcaster {
  private val subscribers = CopyOnWriteArraySet<HttpResponseWriter>()

  /** Opens an SSE (`text/event-stream`) [HttpResponse] that receives every later [broadcast]. */
  fun openEventStream(): HttpResponse {
    val writer = HttpResponse.streaming()
    writer.write(
      ResponseHeaders.builder(HttpStatus.OK)
        .contentType(MediaType.EVENT_STREAM)
        .add("cache-control", "no-store")
        .add("x-accel-buffering", "no")
        .build()
    )
    writer.write(HttpData.ofUtf8(": connected\n\n"))
    subscribers.add(writer)
    writer.whenComplete().handle { _, _ -> subscribers.remove(writer) }
    return writer
  }

  /** Sends an [event] with [data] (any text; newlines are split into `data:` lines). */
  fun broadcast(event: String, data: String) {
    val frame = "event: $event\ndata: ${data.replace("\n", "\ndata: ")}\n\n"
    val httpData = HttpData.ofUtf8(frame)
    for (subscriber in subscribers) {
      if (!subscriber.tryWrite(httpData)) {
        subscribers.remove(subscriber)
      }
    }
  }
}
