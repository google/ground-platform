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
package org.openforis.collectone.v2.app.client.grpc

import com.squareup.wire.ProtoAdapter
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readInt

/**
 * A call failed with a non-OK gRPC status (see `google.rpc.Code`); [description] is the server's
 * `grpc-message`, if any.
 */
class GrpcWebException(val status: Int, val path: String, val description: String?) :
  Exception("gRPC status $status for $path${description?.let { ": $it" } ?: ""}")

/**
 * Minimal gRPC-Web client (binary `application/grpc-web+proto` framing) over Ktor.
 *
 * Every platform uses gRPC-Web because browsers can't speak native gRPC; the user data service
 * accepts both on the same port. Every call the sync engine makes is unary
 * (`docs/technical/sync-engine.md`, "Web Client"). Non-OK statuses throw [GrpcWebException], so
 * callers can tell, for example, an expired sync token ([FAILED_PRECONDITION]) from an outage
 * ([UNAVAILABLE]).
 *
 * Calls carry the signed-in user's ID token from [authToken] as `Authorization: Bearer <token>`
 * when there is one, plus any [headers] (for example a simulated development role). A call rejected
 * as [UNAUTHENTICATED] is retried once with a refreshed token.
 */
class GrpcWebTransport(
  baseUrl: String,
  private val httpClient: HttpClient = HttpClient(),
  private val authToken: suspend (forceRefresh: Boolean) -> String? = { null },
  private val headers: () -> Map<String, String> = { emptyMap() },
) {
  private val baseUrl = baseUrl.trimEnd('/')

  /** Sends [request] to the unary method at [path] and returns its single response. */
  suspend fun <S : Any, R : Any> unary(
    path: String,
    request: S,
    requestAdapter: ProtoAdapter<S>,
    responseAdapter: ProtoAdapter<R>,
  ): R {
    val token = authToken(false)
    return try {
      unaryWithToken(path, request, requestAdapter, responseAdapter, token)
    } catch (e: GrpcWebException) {
      // The token may have expired or been revoked since it was cached.
      if (e.status != UNAUTHENTICATED || token == null) throw e
      unaryWithToken(path, request, requestAdapter, responseAdapter, authToken(true))
    }
  }

  private suspend fun <S : Any, R : Any> unaryWithToken(
    path: String,
    request: S,
    requestAdapter: ProtoAdapter<S>,
    responseAdapter: ProtoAdapter<R>,
    token: String?,
  ): R {
    var result: R? = null
    call(path, request, requestAdapter, responseAdapter, token) { message ->
      if (result != null) throw GrpcWebException(INTERNAL, path, "more than one response")
      result = message
    }
    return result ?: throw GrpcWebException(INTERNAL, path, "no response")
  }

  private suspend fun <S : Any, R : Any> call(
    path: String,
    request: S,
    requestAdapter: ProtoAdapter<S>,
    responseAdapter: ProtoAdapter<R>,
    token: String?,
    onMessage: suspend (R) -> Unit,
  ) {
    httpClient
      .preparePost(baseUrl + path) {
        header("X-Grpc-Web", "1")
        header(HttpHeaders.Accept, GRPC_WEB_PROTO)
        headers().forEach { (name, value) -> header(name, value) }
        if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
        setBody(ByteArrayContent(frame(requestAdapter.encode(request)), CONTENT_TYPE))
      }
      .execute { response -> readResponse(path, response, responseAdapter, onMessage) }
  }

  private suspend fun <R : Any> readResponse(
    path: String,
    response: HttpResponse,
    responseAdapter: ProtoAdapter<R>,
    onMessage: suspend (R) -> Unit,
  ) {
    if (!response.status.isSuccess()) {
      throw GrpcWebException(statusForHttp(response.status), path, response.status.description)
    }
    // Anything but gRPC-Web (for example an HTML page after a redirect) means the URL doesn't point
    // at the user data service.
    val contentType = response.headers[HttpHeaders.ContentType].orEmpty()
    if (!contentType.startsWith("application/grpc")) {
      throw GrpcWebException(
        UNKNOWN,
        path,
        "Unexpected response from ${response.call.request.url.host}:" +
          "${response.call.request.url.port} (HTTP ${response.status.value}, " +
          "${contentType.ifEmpty { "no content type" }}). Check that USER_DATA_SERVICE_URL points " +
          "at the user data service.",
      )
    }
    // A "trailers-only" response carries the status in its headers and has no body.
    response.headers[GRPC_STATUS]?.let { status ->
      checkStatus(
        path,
        status.toIntOrNull() ?: UNKNOWN,
        response.headers[GRPC_MESSAGE]?.let(::percentDecode),
      )
      return
    }
    val body = response.bodyAsChannel()
    var status: Int? = null
    var message: String? = null
    while (body.awaitContent()) {
      val flags = body.readByte().toInt()
      val length = body.readInt()
      val payload = if (length > 0) body.readByteArray(length) else ByteArray(0)
      if (flags and TRAILER_FLAG != 0) {
        val trailers = parseTrailers(payload.decodeToString())
        status = trailers[GRPC_STATUS]?.toIntOrNull() ?: UNKNOWN
        message = trailers[GRPC_MESSAGE]?.let(::percentDecode)
      } else {
        onMessage(responseAdapter.decode(payload))
      }
    }
    checkStatus(path, status ?: UNKNOWN, message ?: "missing grpc-status trailer")
  }

  private fun checkStatus(path: String, status: Int, message: String?) {
    if (status != OK) throw GrpcWebException(status, path, message)
  }

  companion object {
    private const val GRPC_WEB_PROTO = "application/grpc-web+proto"
    private val CONTENT_TYPE = ContentType.parse(GRPC_WEB_PROTO)
    private const val GRPC_STATUS = "grpc-status"
    private const val GRPC_MESSAGE = "grpc-message"
    private const val TRAILER_FLAG = 0x80

    const val OK = 0
    const val UNKNOWN = 2
    const val INVALID_ARGUMENT = 3
    const val NOT_FOUND = 5
    const val FAILED_PRECONDITION = 9
    const val INTERNAL = 13
    const val UNAVAILABLE = 14
    const val UNIMPLEMENTED = 12
    const val UNAUTHENTICATED = 16
    const val PERMISSION_DENIED = 7

    /** Prefixes [message] with the 5-byte gRPC frame header (uncompressed, big-endian length). */
    fun frame(message: ByteArray, flags: Int = 0): ByteArray {
      val framed = ByteArray(5 + message.size)
      framed[0] = flags.toByte()
      framed[1] = (message.size ushr 24).toByte()
      framed[2] = (message.size ushr 16).toByte()
      framed[3] = (message.size ushr 8).toByte()
      framed[4] = message.size.toByte()
      message.copyInto(framed, destinationOffset = 5)
      return framed
    }

    /** Decodes a percent-encoded `grpc-message` (UTF-8), as the gRPC protocol specifies. */
    internal fun percentDecode(value: String): String {
      if ('%' !in value) return value
      val bytes = ArrayList<Byte>(value.length)
      var i = 0
      while (i < value.length) {
        val hex =
          if (value[i] == '%' && i + 2 < value.length) {
            value.substring(i + 1, i + 3).toIntOrNull(16)
          } else {
            null
          }
        if (hex != null) {
          bytes += hex.toByte()
          i += 3
        } else {
          bytes += value[i].toString().encodeToByteArray().toList()
          i++
        }
      }
      return bytes.toByteArray().decodeToString()
    }

    private fun parseTrailers(text: String): Map<String, String> =
      text
        .split("\r\n")
        .filter { ':' in it }
        .associate { line ->
          line.substringBefore(':').trim().lowercase() to line.substringAfter(':').trim()
        }

    /** Maps an HTTP error to a gRPC status, as in the gRPC HTTP-to-status mapping. */
    private fun statusForHttp(status: HttpStatusCode): Int =
      when (status.value) {
        401 -> UNAUTHENTICATED
        403 -> PERMISSION_DENIED
        404 -> UNIMPLEMENTED
        429,
        502,
        503,
        504 -> UNAVAILABLE
        else -> UNKNOWN
      }
  }
}
