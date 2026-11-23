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
package org.openforis.collectone.v2.server.geo

import com.squareup.moshi.Types
import google.earthengine.v1main.Expression
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.openforis.collectone.v2.server.web.protoJsonMoshi

/**
 * Evaluates Earth Engine [Expression]s with the `value:compute` REST method and returns the decoded
 * JSON result (`Map`, `List`, `String`, `Double`, `Boolean`, or `null`).
 *
 * [workloadTag] labels the computation in Earth Engine's Cloud Monitoring usage metrics, so the
 * cost of each feature can be tracked separately.
 */
fun interface EarthEngineValueClient {
  fun compute(expression: Expression, workloadTag: String): Any?

  companion object {
    /** A client that fails every call with `UNAVAILABLE`, for servers without credentials. */
    fun unavailable(): EarthEngineValueClient = EarthEngineValueClient { _, _ ->
      throw Status.UNAVAILABLE.withDescription(EarthEngineAuth.NOT_CONFIGURED).asRuntimeException()
    }

    /** Calls Earth Engine with the credentials from [EarthEngineAuth.fromEnvironment]. */
    fun fromEnvironment(
      httpClient: HttpClient = HttpClient.newHttpClient()
    ): EarthEngineValueClient =
      EarthEngineAuth.fromEnvironment()?.let { GoogleEarthEngineValueFetcher(it, httpClient) }
        ?: unavailable()
  }
}

/** Calls `POST https://earthengine.googleapis.com/v1/projects/{project}/value:compute`. */
internal class GoogleEarthEngineValueFetcher(
  private val auth: EarthEngineAuth,
  private val httpClient: HttpClient,
  private val timeout: Duration = Duration.ofSeconds(120),
) : EarthEngineValueClient {
  private val expressionAdapter = protoJsonMoshi.adapter(Expression::class.java)
  private val stringAdapter = protoJsonMoshi.adapter(String::class.java)
  private val mapAdapter =
    protoJsonMoshi.adapter<Map<String, Any?>>(
      Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

  override fun compute(expression: Expression, workloadTag: String): Any? {
    val body =
      """{"expression":${expressionAdapter.toJson(expression)},""" +
        """"workloadTag":${stringAdapter.toJson(workloadTag)}}"""
    val request =
      HttpRequest.newBuilder()
        .uri(
          URI.create(
            "https://earthengine.googleapis.com/v1/projects/${auth.projectId}/value:compute"
          )
        )
        .timeout(timeout)
        .header("Authorization", "Bearer ${auth.accessToken()}")
        .header("Content-Type", "application/json; charset=utf-8")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
    val response =
      try {
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      } catch (e: java.net.http.HttpTimeoutException) {
        throw Status.DEADLINE_EXCEEDED.withDescription(
            "Earth Engine didn't answer within ${timeout.seconds} s"
          )
          .withCause(e)
          .asRuntimeException()
      } catch (e: Exception) {
        throw Status.UNAVAILABLE.withDescription(
            "Failed to communicate with Earth Engine API: ${e.message}"
          )
          .withCause(e)
          .asRuntimeException()
      }
    if (response.statusCode() !in 200..299) {
      throw earthEngineHttpError(response.statusCode(), response.body())
    }
    val parsed =
      mapAdapter.fromJson(response.body())
        ?: throw Status.INTERNAL.withDescription("Empty response from Earth Engine API")
          .asRuntimeException()
    return parsed["result"]
  }
}

/**
 * Maps an Earth Engine REST error to a gRPC status: rate limiting and memory limits become
 * `RESOURCE_EXHAUSTED`, timeouts `DEADLINE_EXCEEDED`, permission problems (a server
 * misconfiguration, not the caller's fault) `FAILED_PRECONDITION`, and server errors `UNAVAILABLE`.
 */
internal fun earthEngineHttpError(statusCode: Int, body: String): StatusRuntimeException {
  val message = body.take(MAX_ERROR_BODY_CHARS)
  val status =
    when {
      statusCode == 429 ->
        Status.RESOURCE_EXHAUSTED.withDescription("Earth Engine is busy: $message")
      body.contains("timed out", ignoreCase = true) ->
        Status.DEADLINE_EXCEEDED.withDescription("Earth Engine computation timed out: $message")
      body.contains("memory limit", ignoreCase = true) ->
        Status.RESOURCE_EXHAUSTED.withDescription("Earth Engine memory limit exceeded: $message")
      statusCode == 401 || statusCode == 403 ->
        Status.FAILED_PRECONDITION.withDescription(
          "Earth Engine denied the server's request (check the service account's roles): $message"
        )
      statusCode >= 500 -> Status.UNAVAILABLE.withDescription("Earth Engine error: $message")
      else ->
        Status.INTERNAL.withDescription("Earth Engine API returned HTTP $statusCode: $message")
    }
  return status.asRuntimeException()
}

private const val MAX_ERROR_BODY_CHARS = 2000
