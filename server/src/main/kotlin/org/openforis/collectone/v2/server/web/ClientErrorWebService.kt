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

import com.linecorp.armeria.common.HttpHeaderNames
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.cors.CorsService
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Receives crash and unhandled-error reports from Collect One web clients (`webApp`,
 * `mobileWebApp`, `balloonFormApp`), which Firebase Crashlytics does not support, and writes
 * secret-redacted structured error events to the server's logger so Cloud Run / Cloud Error
 * Reporting captures and groups them.
 */
object ClientErrorWebService {
  internal const val MAX_BODY_BYTES = 16 * 1024
  internal const val MAX_REPORTS_PER_MINUTE_PER_IP = 30
  private const val MAX_MESSAGE_CHARS = 1024
  private const val MAX_STACK_CHARS = 4096
  private const val MAX_BREADCRUMBS = 30

  private val defaultLogger: Logger = LoggerFactory.getLogger(ClientErrorWebService::class.java)
  private val moshi = Moshi.Builder().build()
  private val jsonMapAdapter =
    moshi.adapter<Map<String, Any?>>(
      Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

  private val EMAIL_ADDRESS = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+""")
  private val BEARER_TOKEN = Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=-]+""")
  private val JSON_WEB_TOKEN = Regex("""\beyJ[A-Za-z0-9_-]*\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")

  internal data class ClientErrorReport(
    val client: String = "unknown",
    val environment: String = "",
    val appVersion: String = "",
    val kind: String = "error",
    val typeName: String = "",
    val message: String = "",
    val stackTrace: String = "",
    val url: String = "",
    val userId: String = "",
    val customKeys: Map<String, String> = emptyMap(),
    val breadcrumbs: List<String> = emptyList(),
  )

  fun corsDecorator() =
    CorsService.builderForAnyOrigin()
      .allowRequestMethods(HttpMethod.POST, HttpMethod.OPTIONS)
      .allowRequestHeaders("content-type")
      .maxAge(3600)
      .newDecorator()

  fun decoratedService(): HttpService = service().decorate(corsDecorator())

  fun service(
    logger: Logger = defaultLogger,
    clock: Clock = Clock.systemUTC(),
    maxReportsPerMinutePerIp: Int = MAX_REPORTS_PER_MINUTE_PER_IP,
  ): HttpService {
    val rateLimiter = PerIpRateLimiter(clock, maxReportsPerMinutePerIp)
    return HttpService { ctx, req ->
      if (req.method() != HttpMethod.POST) {
        return@HttpService HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED)
      }
      val clientIp =
        req
          .headers()
          .get(HttpHeaderNames.of("x-forwarded-for"))
          ?.substringBefore(',')
          ?.trim()
          ?.takeIf { it.isNotEmpty() } ?: ctx.remoteAddress()?.address?.hostAddress ?: "unknown"
      if (!rateLimiter.tryAcquire(clientIp)) {
        return@HttpService HttpResponse.of(
          HttpStatus.TOO_MANY_REQUESTS,
          MediaType.JSON_UTF_8,
          """{"error":"rate_limited"}""",
        )
      }
      HttpResponse.of(
        req.aggregate().thenApply { aggregated ->
          val rawBytes = aggregated.content().array()
          if (rawBytes.size > MAX_BODY_BYTES) {
            return@thenApply HttpResponse.of(
              HttpStatus.REQUEST_ENTITY_TOO_LARGE,
              MediaType.JSON_UTF_8,
              """{"error":"payload_too_large"}""",
            )
          }
          val body = rawBytes.toString(Charsets.UTF_8).trim()
          if (body.isEmpty()) {
            return@thenApply HttpResponse.of(
              HttpStatus.BAD_REQUEST,
              MediaType.JSON_UTF_8,
              """{"error":"empty_body"}""",
            )
          }
          val report =
            parseReport(body)
              ?: return@thenApply HttpResponse.of(
                HttpStatus.BAD_REQUEST,
                MediaType.JSON_UTF_8,
                """{"error":"invalid_json"}""",
              )
          logReport(logger, report)
          HttpResponse.of(HttpStatus.ACCEPTED, MediaType.JSON_UTF_8, """{"accepted":true}""")
        }
      )
    }
  }

  internal fun redactSecrets(text: String): String =
    text
      .replace(BEARER_TOKEN, "Bearer <token>")
      .replace(JSON_WEB_TOKEN, "<token>")
      .replace(EMAIL_ADDRESS, "<email>")

  private fun parseReport(body: String): ClientErrorReport? {
    val raw = runCatching { jsonMapAdapter.fromJson(body) }.getOrNull() ?: return null
    val customKeys =
      (raw["customKeys"] as? Map<*, *>)
        ?.entries
        ?.mapNotNull { (k, v) ->
          val key = k as? String ?: return@mapNotNull null
          key to (v?.toString() ?: "")
        }
        ?.toMap()
        .orEmpty()
    val breadcrumbs = (raw["breadcrumbs"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
    return ClientErrorReport(
      client = raw["client"]?.toString() ?: "unknown",
      environment = raw["environment"]?.toString() ?: "",
      appVersion = raw["appVersion"]?.toString() ?: "",
      kind = raw["kind"]?.toString() ?: "error",
      typeName = raw["typeName"]?.toString() ?: "",
      message = raw["message"]?.toString() ?: "",
      stackTrace = raw["stackTrace"]?.toString() ?: "",
      url = raw["url"]?.toString() ?: "",
      userId = raw["userId"]?.toString() ?: "",
      customKeys = customKeys,
      breadcrumbs = breadcrumbs,
    )
  }

  private fun logReport(logger: Logger, report: ClientErrorReport) {
    val client = sanitizeField(report.client, 64).ifEmpty { "unknown" }
    val environment = sanitizeField(report.environment, 64)
    val appVersion = sanitizeField(report.appVersion, 64)
    val kind = sanitizeField(report.kind, 64).ifEmpty { "error" }
    val typeName = sanitizeField(report.typeName, 128)
    val message = sanitizeField(report.message, MAX_MESSAGE_CHARS)
    val stackTrace = sanitizeField(report.stackTrace, MAX_STACK_CHARS)
    val url = sanitizeField(report.url, 512)
    val breadcrumbs =
      report.breadcrumbs
        .takeLast(MAX_BREADCRUMBS)
        .map { sanitizeField(it, 256) }
        .filter {
          it.isNotEmpty()
        }
    val customKeys =
      report.customKeys.entries.take(16).joinToString(", ") { (k, v) ->
        "${sanitizeField(k, 64)}=${sanitizeField(v, 128)}"
      }

    val summary = buildString {
      append("ClientError [client=").append(client)
      if (environment.isNotEmpty()) append(", env=").append(environment)
      if (appVersion.isNotEmpty()) append(", version=").append(appVersion)
      append(", kind=").append(kind)
      if (typeName.isNotEmpty()) append(", type=").append(typeName)
      if (url.isNotEmpty()) append(", url=").append(url)
      if (customKeys.isNotEmpty()) append(", keys={").append(customKeys).append('}')
      append("] ")
      append(message.ifEmpty { "(no message)" })
      if (stackTrace.isNotEmpty()) {
        append("\n").append(stackTrace)
      }
      if (breadcrumbs.isNotEmpty()) {
        append("\nBreadcrumbs:\n")
        breadcrumbs.forEach { append("  - ").append(it).append('\n') }
      }
    }
    logger.error(summary.trimEnd())
  }

  private fun sanitizeField(value: String, maxChars: Int): String =
    redactSecrets(value.trim()).take(maxChars)

  private class PerIpRateLimiter(private val clock: Clock, private val limitPerMinute: Int) {
    private val windows = ConcurrentHashMap<String, Window>()

    private data class Window(val minuteBucket: Long, val count: Int)

    fun tryAcquire(ip: String): Boolean {
      val bucket = clock.millis() / 60_000L
      if (windows.size > 4096) {
        windows.entries.removeIf { it.value.minuteBucket < bucket }
      }
      var allowed = false
      windows.compute(ip) { _, current ->
        val next =
          if (current == null || current.minuteBucket != bucket) {
            Window(bucket, 1)
          } else {
            Window(bucket, current.count + 1)
          }
        allowed = next.count <= limitPerMinute
        next
      }
      return allowed
    }
  }
}
