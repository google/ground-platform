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

import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.server.ServiceRequestContext
import com.squareup.moshi.JsonWriter
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToLong
import okio.Buffer

/** Single recorded gRPC or gRPC-Web call for the debug web UI. */
data class RpcLogEntry(
  val id: Long,
  val timestamp: String,
  val service: String,
  val method: String,
  val fullPath: String,
  val transport: String,
  val statusCode: Int,
  val statusName: String,
  val errorMessage: String?,
  val durationMs: Double,
  val isDeltaPoll: Boolean,
  val isEmptyDeltaPoll: Boolean,
  val itemCount: Int,
  val summary: String,
  val requestJson: String,
  val responseJson: String,
)

/**
 * Captures incoming gRPC and gRPC-Web calls in a bounded in-memory ring buffer and broadcasts new
 * entries to connected Server-Sent Events (SSE) clients.
 */
class RpcCallRecorder(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {
  private val nextId = AtomicLong(1L)
  private val entries = ConcurrentLinkedDeque<RpcLogEntry>()
  private val sse = SseBroadcaster()

  /** Returns all currently retained RPC entries in chronological order (oldest to newest). */
  fun snapshot(): List<RpcLogEntry> = entries.toList()

  /** Clears all retained RPC entries and notifies connected SSE streams. */
  fun clear() {
    entries.clear()
    broadcastSse(event = "clear", data = "{}")
  }

  /** Records a completed RPC call and broadcasts it to any live SSE listeners. */
  fun record(entry: RpcLogEntry) {
    entries.addLast(entry)
    while (entries.size > maxEntries) {
      entries.pollFirst()
    }
    broadcastSse(event = "rpc", data = entry.toJsonString())
  }

  /** Opens an SSE [HttpResponse] stream that receives live `rpc` and `clear` events. */
  fun openEventStream(): HttpResponse = sse.openEventStream()

  /** Returns a gRPC [ServerInterceptor] that captures request/response messages and status. */
  fun interceptor(): ServerInterceptor =
    object : ServerInterceptor {
      override fun <ReqT : Any?, RespT : Any?> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
      ): ServerCall.Listener<ReqT> {
        val startNanos = System.nanoTime()
        val startedAt = Instant.now().toString()
        val transport = detectTransport(headers)
        val fullMethodName = call.methodDescriptor.fullMethodName
        val fullPath = "/$fullMethodName"
        val service = fullMethodName.substringBefore('/').substringAfterLast('.')
        val method = fullMethodName.substringAfterLast('/')
        val isStreaming = call.methodDescriptor.type != MethodDescriptor.MethodType.UNARY

        val requestMessage = AtomicReference<Any?>(null)
        val responseMessages = CopyOnWriteArrayList<Any>()

        val forwardingCall =
          object : SimpleForwardingServerCall<ReqT, RespT>(call) {
            override fun sendMessage(message: RespT) {
              if (message != null) {
                responseMessages.add(message as Any)
              }
              super.sendMessage(message)
            }

            override fun close(status: Status, trailers: Metadata) {
              val elapsedMs = ((System.nanoTime() - startNanos) / 10_000.0).roundToLong() / 100.0
              val entry =
                buildEntry(
                  id = nextId.getAndIncrement(),
                  timestamp = startedAt,
                  service = service,
                  method = method,
                  fullPath = fullPath,
                  transport = transport,
                  status = status,
                  durationMs = elapsedMs,
                  isStreaming = isStreaming,
                  requestMessage = requestMessage.get(),
                  responseMessages = responseMessages,
                )
              record(entry)
              super.close(status, trailers)
            }
          }

        val listener = next.startCall(forwardingCall, headers)
        return object : SimpleForwardingServerCallListener<ReqT>(listener) {
          override fun onMessage(message: ReqT) {
            if (message != null) {
              requestMessage.set(message as Any)
            }
            super.onMessage(message)
          }
        }
      }
    }

  private fun broadcastSse(event: String, data: String) = sse.broadcast(event, data)

  companion object {
    const val DEFAULT_MAX_ENTRIES = 500

    private val GrpcWebHeaderKey: Metadata.Key<String> =
      Metadata.Key.of("x-grpc-web", Metadata.ASCII_STRING_MARSHALLER)
    private val ContentTypeHeaderKey: Metadata.Key<String> =
      Metadata.Key.of("content-type", Metadata.ASCII_STRING_MARSHALLER)

    private fun detectTransport(headers: Metadata): String {
      val ctx = ServiceRequestContext.currentOrNull()
      val reqHeaders = ctx?.request()?.headers()
      if (reqHeaders != null) {
        val contentType = reqHeaders.contentType()?.toString().orEmpty()
        if (reqHeaders.get("x-grpc-web") != null || contentType.contains("grpc-web")) {
          return "gRPC-Web"
        }
      }
      val grpcWeb = headers.get(GrpcWebHeaderKey)
      val contentType = headers.get(ContentTypeHeaderKey).orEmpty()
      return if (grpcWeb != null || contentType.contains("grpc-web")) "gRPC-Web" else "gRPC"
    }

    private fun buildEntry(
      id: Long,
      timestamp: String,
      service: String,
      method: String,
      fullPath: String,
      transport: String,
      status: Status,
      durationMs: Double,
      isStreaming: Boolean,
      requestMessage: Any?,
      responseMessages: List<Any>,
    ): RpcLogEntry {
      val requestJson = requestMessage?.let(::formatMessageJson) ?: "{}"
      val responseJson =
        when {
          isStreaming ->
            responseMessages.joinToString(prefix = "[\n", separator = ",\n", postfix = "\n]") {
              formatMessageJson(it).prependIndent("  ")
            }
          responseMessages.isEmpty() -> "{}"
          else -> formatMessageJson(responseMessages.first())
        }

      val requestMap = parseJsonObject(requestJson)
      val responseMap = if (!isStreaming) parseJsonObject(responseJson) else emptyMap()

      val syncToken = extractSyncToken(requestMessage, requestMap)
      val isDeltaPoll = method.startsWith("List") && !syncToken.isNullOrBlank()
      val itemCount =
        when {
          isStreaming -> responseMessages.size
          else -> responseMap.values.filterIsInstance<List<*>>().sumOf { it.size }
        }
      val isEmptyDeltaPoll = isDeltaPoll && status.isOk && itemCount == 0
      val summary =
        buildSummary(
          method = method,
          status = status,
          isStreaming = isStreaming,
          isDeltaPoll = isDeltaPoll,
          itemCount = itemCount,
          requestMap = requestMap,
        )

      return RpcLogEntry(
        id = id,
        timestamp = timestamp,
        service = service,
        method = method,
        fullPath = fullPath,
        transport = transport,
        statusCode = status.code.value(),
        statusName = status.code.name,
        errorMessage = status.description,
        durationMs = durationMs,
        isDeltaPoll = isDeltaPoll,
        isEmptyDeltaPoll = isEmptyDeltaPoll,
        itemCount = itemCount,
        summary = summary,
        requestJson = requestJson,
        responseJson = responseJson,
      )
    }

    private fun formatMessageJson(message: Any): String = runCatching {
      val adapter = protoJsonMoshi.adapter<Any>(message.javaClass).indent("  ")
      adapter.toJson(message)
    }
      .getOrElse { "{}" }

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonObject(json: String): Map<String, Any?> = runCatching {
      if (!json.trimStart().startsWith("{")) return emptyMap()
      protoJsonMoshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?> ?: emptyMap()
    }
      .getOrElse { emptyMap() }

    private fun extractSyncToken(requestMessage: Any?, requestMap: Map<String, Any?>): String? {
      val fromMap = (requestMap["syncToken"] ?: requestMap["sync_token"]) as? String
      if (!fromMap.isNullOrBlank()) return fromMap
      if (requestMessage == null) return null
      return runCatching {
        requestMessage.javaClass.getMethod("getSync_token").invoke(requestMessage) as? String
      }
        .getOrNull()
    }

    private fun buildSummary(
      method: String,
      status: Status,
      isStreaming: Boolean,
      isDeltaPoll: Boolean,
      itemCount: Int,
      requestMap: Map<String, Any?>,
    ): String {
      val reqParts = mutableListOf<String>()
      for ((label, keys) in SUMMARY_KEYS) {
        val value = keys.firstNotNullOfOrNull { key ->
          (requestMap[key] as? String)?.takeIf { it.isNotBlank() }
        }
        if (value != null) {
          reqParts += "$label=$value"
        }
      }
      val mutations = requestMap["mutations"] as? List<*>
      if (mutations != null && mutations.isNotEmpty()) {
        reqParts += "${mutations.size} ${if (mutations.size == 1) "mutation" else "mutations"}"
      }
      if (isDeltaPoll) {
        reqParts += "delta poll"
      }

      val outcome =
        when {
          !status.isOk -> status.code.name + (status.description?.let { ": $it" } ?: "")
          isStreaming -> "$itemCount streamed"
          method.startsWith("List") || method.startsWith("Search") ->
            "$itemCount ${if (itemCount == 1) "item" else "items"}"
          else -> "OK"
        }

      return if (reqParts.isEmpty()) {
        "→ $outcome"
      } else {
        "${reqParts.joinToString(", ")} → $outcome"
      }
    }

    private val SUMMARY_KEYS =
      listOf(
        "survey" to listOf("surveyId", "survey_id"),
        "org" to listOf("organizationId", "organization_id"),
        "entity" to listOf("entityId", "entity_id"),
        "submission" to listOf("submissionId", "submission_id"),
        "query" to listOf("query"),
      )

    internal fun List<RpcLogEntry>.toJsonArrayString(): String {
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

    internal fun RpcLogEntry.toJsonString(): String {
      val buffer = Buffer()
      JsonWriter.of(buffer).use { writer -> writeJson(writer) }
      return buffer.readUtf8()
    }

    private fun RpcLogEntry.writeJson(writer: JsonWriter) {
      writer.beginObject()
      writer.name("id").value(id)
      writer.name("timestamp").value(timestamp)
      writer.name("service").value(service)
      writer.name("method").value(method)
      writer.name("fullPath").value(fullPath)
      writer.name("transport").value(transport)
      writer.name("statusCode").value(statusCode)
      writer.name("statusName").value(statusName)
      writer.name("errorMessage").value(errorMessage)
      writer.name("durationMs").value(durationMs)
      writer.name("isDeltaPoll").value(isDeltaPoll)
      writer.name("isEmptyDeltaPoll").value(isEmptyDeltaPoll)
      writer.name("itemCount").value(itemCount)
      writer.name("summary").value(summary)
      writer.name("requestJson").value(requestJson)
      writer.name("responseJson").value(responseJson)
      writer.endObject()
    }
  }
}
