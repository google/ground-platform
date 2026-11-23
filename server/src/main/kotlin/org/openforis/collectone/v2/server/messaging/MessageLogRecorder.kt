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
package org.openforis.collectone.v2.server.messaging

import com.linecorp.armeria.common.HttpResponse
import com.squareup.moshi.JsonWriter
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong
import okio.Buffer
import org.openforis.collectone.v2.server.web.SseBroadcaster

/** Single sent or logged email entry recorded for the Message Inspector Web UI. */
data class MessageLogEntry(
  val id: Long,
  val timestamp: String,
  val idempotencyKey: String,
  val kind: String,
  val recipientEmail: String,
  val subject: String,
  val ctaUrl: String,
  val status: String,
  val providerMessageId: String,
  val errorMessage: String,
  val htmlBody: String,
  val textBody: String,
)

/**
 * Retains recent sent/logged invitation emails in a bounded ring buffer and broadcasts new entries
 * to connected Server-Sent Events (SSE) listeners.
 */
class MessageLogRecorder(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {
  private val nextId = AtomicLong(1L)
  private val entries = ConcurrentLinkedDeque<MessageLogEntry>()
  private val sse = SseBroadcaster()

  fun snapshot(): List<MessageLogEntry> = entries.toList()

  fun clear() {
    entries.clear()
    sse.broadcast(event = "clear", data = "{}")
  }

  fun record(email: OutboundEmail, outcome: EmailDeliveryOutcome): MessageLogEntry {
    val entry =
      MessageLogEntry(
        id = nextId.getAndIncrement(),
        timestamp = Instant.now().toString(),
        idempotencyKey = email.idempotencyKey,
        kind = email.kind,
        recipientEmail = email.recipientEmail,
        subject = email.subject,
        ctaUrl = email.ctaUrl,
        status = outcome.status.name,
        providerMessageId = outcome.providerMessageId,
        errorMessage = outcome.errorMessage,
        htmlBody = email.htmlBody,
        textBody = email.textBody,
      )
    entries.addLast(entry)
    while (entries.size > maxEntries) {
      entries.pollFirst()
    }
    sse.broadcast(event = "message", data = entry.toJsonString())
    return entry
  }

  fun openEventStream(): HttpResponse = sse.openEventStream()

  companion object {
    const val DEFAULT_MAX_ENTRIES = 200

    internal fun List<MessageLogEntry>.toJsonArrayString(): String {
      val buffer = Buffer()
      JsonWriter.of(buffer).use { writer ->
        writer.beginArray()
        for (entry in this) {
          entry.writeJson(writer)
        }
        writer.endArray()
      }
      return buffer.readUtf8()
    }

    internal fun MessageLogEntry.toJsonString(): String {
      val buffer = Buffer()
      JsonWriter.of(buffer).use { writer -> writeJson(writer) }
      return buffer.readUtf8()
    }

    private fun MessageLogEntry.writeJson(writer: JsonWriter) {
      writer.beginObject()
      writer.name("id").value(id)
      writer.name("timestamp").value(timestamp)
      writer.name("idempotencyKey").value(idempotencyKey)
      writer.name("kind").value(kind)
      writer.name("recipientEmail").value(recipientEmail)
      writer.name("subject").value(subject)
      writer.name("ctaUrl").value(ctaUrl)
      writer.name("status").value(status)
      writer.name("providerMessageId").value(providerMessageId)
      writer.name("errorMessage").value(errorMessage)
      writer.name("htmlBody").value(htmlBody)
      writer.name("textBody").value(textBody)
      writer.endObject()
    }
  }
}
