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
import com.linecorp.armeria.common.QueryParams
import com.linecorp.armeria.common.ResponseHeaders
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.ServiceRequestContext
import io.grpc.Status
import io.grpc.StatusException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.openforis.collectone.v2.core.geo.io.GoogleEarthDeepLinks
import org.openforis.collectone.v2.core.geo.io.KmlBalloonWriter
import org.openforis.collectone.v2.server.service.UserDataBackend

/**
 * Serves live OGC KML 2.2 documents (`GET /kml/surveys/:surveyId`) so Google Earth Web can open a
 * Collect One survey directly over HTTPS (`https://earth.google.com/web/c/<OpenKmlDocument>`).
 *
 * The KML contains only map layer geometries and embedded form schemas (never user credentials);
 * authentication is enforced when a collector submits a form inside the KML balloon.
 */
object KmlExportWebService {
  private val KML_MEDIA_TYPE: MediaType = MediaType.parse(KmlBalloonWriter.KML_MIME)

  fun service(
    backend: UserDataBackend,
    scope: CoroutineScope,
    defaultBalloonUrl: String = "",
  ): HttpService = HttpService { ctx, req ->
    if (req.method() != HttpMethod.GET && req.method() != HttpMethod.HEAD) {
      return@HttpService HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED)
    }
    val rawParam = ctx.pathParam("surveyId").orEmpty().trim()
    val surveyId =
      if (rawParam.endsWith(".kml", ignoreCase = true)) {
        rawParam.dropLast(4).trim()
      } else {
        rawParam
      }
    if (surveyId.isEmpty()) {
      return@HttpService textError(HttpStatus.BAD_REQUEST, "Missing surveyId")
    }

    val params = QueryParams.fromQueryString(ctx.query())
    val balloonUrl =
      sanitizeBalloonUrl(params.get("balloonUrl"))?.ifBlank { defaultBalloonUrl.trim() }
    if (balloonUrl == null) {
      return@HttpService textError(
        HttpStatus.BAD_REQUEST,
        "Invalid balloonUrl: must be an https://, http://localhost, or root-relative URL",
      )
    }

    val serviceUrl = resolveServiceUrl(ctx, req, params.get("serviceUrl"))
    val isNetworkLinkMode = params.get("mode")?.trim()?.lowercase() == "networklink"
    val isNetworkLinkTarget = params.get("source")?.trim()?.lowercase() == "networklink"
    val refreshIntervalSeconds =
      params.get("refreshInterval")?.trim()?.toIntOrNull()?.coerceIn(5, 3600)
        ?: KmlBalloonWriter.DEFAULT_REFRESH_INTERVAL_SECONDS

    HttpResponse.of(
      scope
        .future {
          if (isNetworkLinkMode) {
            val encodedSurveyId = GoogleEarthDeepLinks.encodeUriComponent(surveyId)
            val targetQuery =
              if (balloonUrl.isBlank()) {
                "?source=networklink"
              } else {
                "?balloonUrl=${GoogleEarthDeepLinks.encodeUriComponent(balloonUrl)}&source=networklink"
              }
            val targetKmlUrl = "$serviceUrl/kml/surveys/$encodedSurveyId.kml$targetQuery"
            backend.exportSurveyNetworkLinkKml(
              surveyId = surveyId,
              targetKmlUrl = targetKmlUrl,
              refreshIntervalSeconds = refreshIntervalSeconds,
            )
          } else {
            backend.exportSurveyKml(
              surveyId = surveyId,
              balloonAppUrl = balloonUrl,
              serviceUrl = serviceUrl,
              forNetworkLink = isNetworkLinkTarget,
            )
          }
        }
        .thenApply { exported ->
          val etag = "\"${sha256Hex(exported.content)}\""
          if (matchesIfNoneMatch(req.headers().get("if-none-match"), etag)) {
            val notModifiedHeaders =
              ResponseHeaders.builder(HttpStatus.NOT_MODIFIED)
                .add("etag", etag)
                .add("cache-control", "no-cache")
                .build()
            return@thenApply HttpResponse.of(notModifiedHeaders)
          }
          val headers =
            ResponseHeaders.builder(HttpStatus.OK)
              .contentType(KML_MEDIA_TYPE)
              .add(
                "content-disposition",
                "inline; filename=\"${asciiFileName(exported.fileName)}\"",
              )
              .add("etag", etag)
              .add("cache-control", "no-cache")
              .build()
          if (req.method() == HttpMethod.HEAD) {
            HttpResponse.of(headers)
          } else {
            HttpResponse.of(headers, HttpData.ofUtf8(exported.content))
          }
        }
        .exceptionally { err ->
          val root = (err as? java.util.concurrent.CompletionException)?.cause ?: err
          statusError(StatusMapper.toStatusException(root))
        }
    )
  }

  internal fun sha256Hex(content: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { b -> ((b.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
  }

  internal fun matchesIfNoneMatch(rawHeader: String?, currentEtag: String): Boolean {
    val header = rawHeader?.trim().orEmpty()
    if (header.isEmpty()) return false
    if (header == "*") return true
    return header.split(',').any { candidate ->
      val normalized = candidate.trim().removePrefix("W/").trim()
      normalized == currentEtag
    }
  }

  /**
   * Allows blank (uses default path), `/...`, `https://...`, or local dev `http://localhost...`.
   */
  internal fun sanitizeBalloonUrl(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return ""
    val lower = trimmed.lowercase()
    val allowed =
      lower.startsWith("https://") ||
        lower.startsWith("http://localhost:") ||
        lower.startsWith("http://localhost/") ||
        lower == "http://localhost" ||
        lower.startsWith("http://127.0.0.1:") ||
        lower.startsWith("http://127.0.0.1/") ||
        (trimmed.startsWith("/") && !trimmed.startsWith("//"))
    return if (allowed) trimmed else null
  }

  private fun resolveServiceUrl(
    ctx: ServiceRequestContext,
    req: HttpRequest,
    explicitServiceUrl: String?,
  ): String {
    val explicit = explicitServiceUrl?.trim().orEmpty()
    if (explicit.startsWith("https://") || explicit.startsWith("http://")) {
      return explicit.trimEnd('/')
    }
    val forwardedProto = req.headers().get("x-forwarded-proto")?.substringBefore(',')?.trim()
    val scheme =
      when {
        !forwardedProto.isNullOrEmpty() -> forwardedProto
        ctx.sessionProtocol().isTls -> "https"
        else -> "http"
      }
    val authority = req.authority()?.trim().orEmpty()
    return if (authority.isNotEmpty()) "$scheme://$authority" else ""
  }

  private fun statusError(e: StatusException): HttpResponse {
    val httpStatus =
      when (e.status.code) {
        Status.Code.UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED
        Status.Code.PERMISSION_DENIED -> HttpStatus.FORBIDDEN
        Status.Code.NOT_FOUND -> HttpStatus.NOT_FOUND
        Status.Code.INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST
        Status.Code.FAILED_PRECONDITION -> HttpStatus.PRECONDITION_FAILED
        else -> HttpStatus.INTERNAL_SERVER_ERROR
      }
    return textError(httpStatus, e.status.description ?: e.status.code.name)
  }

  private fun textError(status: HttpStatus, message: String): HttpResponse =
    HttpResponse.of(
      ResponseHeaders.builder(status)
        .contentType(MediaType.PLAIN_TEXT_UTF_8)
        .add("cache-control", "no-store")
        .build(),
      HttpData.ofUtf8(message),
    )

  private fun asciiFileName(fileName: String): String =
    fileName
      .map { ch -> if (ch.code in 0x20..0x7E && ch != '"' && ch != '\\') ch else '_' }
      .joinToString("")
}
