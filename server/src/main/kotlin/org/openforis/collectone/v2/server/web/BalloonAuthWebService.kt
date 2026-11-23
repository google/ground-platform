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
package org.openforis.collectone.v2.server.web

import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.common.ResponseHeaders
import com.linecorp.armeria.server.HttpService
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.grpc.Metadata
import io.grpc.Status
import java.util.concurrent.CompletionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.openforis.collectone.v2.server.auth.Authenticator
import org.openforis.collectone.v2.server.service.UserDataBackend

/**
 * HTTP JSON endpoints backing zero-credential KML balloons:
 * - `POST /balloon/auth/complete`: Called by the top-level `/balloon-auth.html` tab with the
 *   signed-in viewer's Collect One credentials and PKCE `codeChallenge`.
 * - `POST /balloon/auth/claim`: Polled by `balloonFormApp` inside the Google Earth KML balloon with
 *   the matching PKCE `codeVerifier` to claim the verified viewer session once.
 */
object BalloonAuthWebService {
  private val moshi = Moshi.Builder().build()
  private val stringMapAdapter =
    moshi.adapter<Map<String, String>>(
      Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )

  fun completeService(
    backend: UserDataBackend,
    authenticator: Authenticator,
    authStore: BalloonAuthStore,
    scope: CoroutineScope,
  ): HttpService = HttpService { _, req ->
    if (req.method() != HttpMethod.POST) {
      return@HttpService HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED)
    }
    HttpResponse.of(
      req
        .aggregate()
        .thenCompose { agg ->
          val body = parseJsonMap(agg.contentUtf8())
          val sessionId = body["sessionId"].orEmpty().trim()
          val codeChallenge = body["codeChallenge"].orEmpty().trim()
          val creds = extractAuthenticatedCredentials(req, authenticator)
          scope.future {
            backend.registerCaller(creds.caller)
            val stored = authStore.completeAuth(sessionId, codeChallenge, creds)
            if (!stored) {
              jsonResponse(
                HttpStatus.BAD_REQUEST,
                mapOf("status" to "invalid_request", "message" to "Invalid sessionId or challenge"),
              )
            } else {
              jsonResponse(
                HttpStatus.OK,
                mapOf(
                  "status" to "completed",
                  "email" to creds.caller.email,
                  "displayName" to creds.caller.displayName,
                ),
              )
            }
          }
        }
        .exceptionally(::errorResponse)
    )
  }

  fun claimService(authStore: BalloonAuthStore): HttpService = HttpService { _, req ->
    if (req.method() != HttpMethod.POST) {
      return@HttpService HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED)
    }
    HttpResponse.of(
      req
        .aggregate()
        .thenApply { agg ->
          val body = parseJsonMap(agg.contentUtf8())
          val sessionId = body["sessionId"].orEmpty()
          val codeVerifier = body["codeVerifier"].orEmpty()
          when (val outcome = authStore.claimAuth(sessionId, codeVerifier)) {
            BalloonClaimOutcome.Pending ->
              jsonResponse(HttpStatus.ACCEPTED, mapOf("status" to "pending"))
            BalloonClaimOutcome.InvalidVerifier ->
              jsonResponse(HttpStatus.FORBIDDEN, mapOf("status" to "invalid_verifier"))
            is BalloonClaimOutcome.Authenticated -> {
              val creds = outcome.credentials
              jsonResponse(
                HttpStatus.OK,
                mapOf(
                  "status" to "authenticated",
                  "token" to creds.token,
                  "devRole" to creds.devRole,
                  "devUserId" to creds.devUserId,
                  "devEmail" to creds.devEmail,
                  "devName" to creds.devName,
                  "collectorName" to creds.caller.displayName,
                  "collectorEmail" to creds.caller.email,
                ),
              )
            }
          }
        }
        .exceptionally(::errorResponse)
    )
  }

  private fun extractAuthenticatedCredentials(
    req: HttpRequest,
    authenticator: Authenticator,
  ): RelayedBalloonCredentials {
    val authHeader = req.headers().get("authorization")?.trim().orEmpty()
    val bearer =
      if (authHeader.startsWith("Bearer ", ignoreCase = true)) authHeader.substring(7).trim()
      else ""
    val devRole = req.headers().get(Authenticator.DEV_ROLE_HEADER)?.trim().orEmpty()
    val devUserId = req.headers().get(Authenticator.DEV_USER_ID_HEADER)?.trim().orEmpty()
    val devEmail = req.headers().get(Authenticator.DEV_USER_EMAIL_HEADER)?.trim().orEmpty()
    val devName = req.headers().get(Authenticator.DEV_USER_NAME_HEADER)?.trim().orEmpty()

    val metadata = Metadata()
    if (bearer.isNotEmpty()) metadata.put(metaKey("authorization"), "Bearer $bearer")
    if (devRole.isNotEmpty()) metadata.put(metaKey(Authenticator.DEV_ROLE_HEADER), devRole)
    if (devUserId.isNotEmpty()) metadata.put(metaKey(Authenticator.DEV_USER_ID_HEADER), devUserId)
    if (devEmail.isNotEmpty()) metadata.put(metaKey(Authenticator.DEV_USER_EMAIL_HEADER), devEmail)
    if (devName.isNotEmpty()) metadata.put(metaKey(Authenticator.DEV_USER_NAME_HEADER), devName)

    val caller = authenticator.authenticate(metadata)
    return RelayedBalloonCredentials(
      caller = caller,
      token = bearer,
      devRole = devRole,
      devUserId = devUserId,
      devEmail = devEmail,
      devName = devName,
    )
  }

  private fun parseJsonMap(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return runCatching { stringMapAdapter.fromJson(raw).orEmpty() }.getOrElse { emptyMap() }
  }

  private fun jsonResponse(status: HttpStatus, payload: Map<String, String>): HttpResponse =
    HttpResponse.of(
      ResponseHeaders.builder(status)
        .contentType(MediaType.JSON_UTF_8)
        .add("cache-control", "no-store")
        .build(),
      HttpData.ofUtf8(stringMapAdapter.toJson(payload)),
    )

  private fun errorResponse(err: Throwable): HttpResponse {
    val root = (err as? CompletionException)?.cause ?: err
    val statusEx = StatusMapper.toStatusException(root)
    val httpStatus =
      when (statusEx.status.code) {
        Status.Code.UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED
        Status.Code.PERMISSION_DENIED -> HttpStatus.FORBIDDEN
        Status.Code.NOT_FOUND -> HttpStatus.NOT_FOUND
        Status.Code.INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST
        else -> HttpStatus.INTERNAL_SERVER_ERROR
      }
    return jsonResponse(
      httpStatus,
      mapOf(
        "status" to "error",
        "message" to (statusEx.status.description ?: statusEx.status.code.name),
      ),
    )
  }

  private fun metaKey(name: String): Metadata.Key<String> =
    Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER)
}
