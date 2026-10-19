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

import com.linecorp.armeria.common.HttpResponse
import com.squareup.moshi.Moshi
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.samplers.Sampler
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/** One finished Spanner client operation (an OpenTelemetry span) shown in the Spanner debug UI. */
data class SpannerOperation(
  val id: Long,
  val traceId: String,
  val spanId: String,
  /** The enclosing span (for example the transaction a commit belongs to), or null at the top. */
  val parentSpanId: String?,
  val name: String,
  /** Coarse kind for filtering: transaction, query, read, dml, commit, session, or other. */
  val category: String,
  val startTime: String,
  val durationMs: Double,
  /** `OK`, `ERROR`, or `UNSET`. */
  val status: String,
  val statusMessage: String?,
  /** SQL text (`db.statement`), when the operation ran one. */
  val statement: String?,
  val attributes: Map<String, String>,
  /** Span events as `"+<ms> <name>"`, e.g. `+1.20 Commit Done`. */
  val events: List<String>,
) {
  fun toMap(): Map<String, Any?> =
    mapOf(
      "id" to id,
      "traceId" to traceId,
      "spanId" to spanId,
      "parentSpanId" to parentSpanId,
      "name" to name,
      "category" to category,
      "startTime" to startTime,
      "durationMs" to durationMs,
      "status" to status,
      "statusMessage" to statusMessage,
      "statement" to statement,
      "attributes" to attributes,
      "events" to events,
    )
}

/**
 * Records the OpenTelemetry spans the Cloud Spanner client emits (transactions, queries, reads,
 * DML, commits, session management) in a bounded in-memory ring buffer, and broadcasts each one to
 * connected Server-Sent Events clients.
 *
 * Pass [openTelemetry] to the Spanner client (`SpannerOptions.Builder.setOpenTelemetry`); see
 * [SpannerConnection].
 */
class SpannerOperationRecorder(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) : SpanProcessor {
  private val nextId = AtomicLong(1L)
  private val entries = ConcurrentLinkedDeque<SpannerOperation>()
  private val sse = SseBroadcaster()

  /** OpenTelemetry instance whose tracer reports every span to this recorder. */
  val openTelemetry: OpenTelemetry =
    OpenTelemetrySdk.builder()
      .setTracerProvider(
        SdkTracerProvider.builder().addSpanProcessor(this).setSampler(Sampler.alwaysOn()).build()
      )
      .build()

  /** Retained operations, oldest first. */
  fun snapshot(): List<SpannerOperation> = entries.toList()

  /** Clears the retained operations and tells connected streams. */
  fun clear() {
    entries.clear()
    sse.broadcast("clear", "{}")
  }

  /** Opens an SSE stream of `operation` and `clear` events. */
  fun openEventStream(): HttpResponse = sse.openEventStream()

  /** Records [operation] and broadcasts it. */
  fun record(operation: SpannerOperation) {
    entries.addLast(operation)
    while (entries.size > maxEntries) entries.pollFirst()
    sse.broadcast("operation", jsonOf(operation.toMap()))
  }

  override fun onStart(parentContext: Context, span: ReadWriteSpan) = Unit

  override fun isStartRequired(): Boolean = false

  override fun onEnd(span: ReadableSpan) {
    val data = span.toSpanData()
    val attributes = data.attributes.asMap().entries.associate { (k, v) -> k.key to v.toString() }
    val startNanos = data.startEpochNanos
    record(
      SpannerOperation(
        id = nextId.getAndIncrement(),
        traceId = data.traceId,
        spanId = data.spanId,
        parentSpanId = data.parentSpanId.takeIf { SpanId.isValid(it) },
        name = data.name,
        category = categoryOf(data.name),
        startTime =
          Instant.ofEpochSecond(startNanos / 1_000_000_000L, startNanos % 1_000_000_000L)
            .toString(),
        durationMs = millis(data.endEpochNanos - startNanos),
        status =
          when (data.status.statusCode) {
            StatusCode.OK -> "OK"
            StatusCode.ERROR -> "ERROR"
            else -> "UNSET"
          },
        statusMessage = data.status.description.takeIf { it.isNotBlank() },
        statement = attributes["db.statement"],
        attributes = attributes,
        events = data.events.map { "+${millis(it.epochNanos - startNanos)} ${it.name}" },
      )
    )
  }

  override fun isEndRequired(): Boolean = true

  companion object {
    const val DEFAULT_MAX_ENTRIES = 1000

    private val anyAdapter = Moshi.Builder().build().adapter(Any::class.java)

    /** Serializes maps, lists, strings, numbers, booleans, and nulls as JSON. */
    fun jsonOf(value: Any?): String = anyAdapter.serializeNulls().toJson(value)

    /** Groups a Spanner client span name (e.g. `CloudSpannerOperation.Commit`) for filtering. */
    fun categoryOf(spanName: String): String {
      val name = spanName.substringAfterLast('.').lowercase()
      return when {
        "session" in name -> "session"
        "commit" in name -> "commit"
        "transaction" in name && "begin" !in name -> "transaction"
        "update" in name || "dml" in name || "partitioned" in name -> "dml"
        "query" in name || "sql" in name -> "query"
        "read" in name -> "read"
        else -> "other"
      }
    }

    private fun millis(nanos: Long): Double = Math.round(nanos / 10_000.0) / 100.0
  }
}
