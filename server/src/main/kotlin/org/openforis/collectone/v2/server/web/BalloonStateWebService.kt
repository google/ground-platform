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
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.common.ResponseHeaders
import com.linecorp.armeria.server.HttpService
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.concurrent.CompletionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.openforis.collectone.v2.server.service.UserDataBackend

/**
 * HTTP JSON endpoint (`/balloon/state/:surveyId/:entityId`) serving live entity status and
 * in-progress form draft persistence for Google Earth KML balloon iframes:
 * - `GET`: Returns live entity `status`, `markerSymbol`, `markerColor`, `entityLabel`, and any
 *   active draft (`draftFormId`, `draftRecordBase64`).
 * - `POST`: Saves or clears an in-progress `RecordInstance` draft (`formId`, `recordBase64`).
 * - `DELETE`: Clears any stored draft for `(surveyId, entityId)`.
 */
object BalloonStateWebService {
  private val moshi = Moshi.Builder().build()
  private val stringMapAdapter =
    moshi.adapter<Map<String, String>>(
      Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )

  fun service(
    backend: UserDataBackend,
    draftStore: BalloonDraftStore,
    scope: CoroutineScope,
  ): HttpService = HttpService { ctx, req ->
    val surveyId = ctx.pathParam("surveyId").orEmpty().trim()
    val entityId = ctx.pathParam("entityId").orEmpty().trim()
    if (surveyId.isEmpty() || entityId.isEmpty()) {
      return@HttpService jsonResponse(
        HttpStatus.BAD_REQUEST,
        mapOf("status" to "invalid_request", "message" to "Missing surveyId or entityId"),
      )
    }

    when (req.method()) {
      HttpMethod.GET ->
        HttpResponse.of(
          scope
            .future {
              val entity = backend.readEntityForBalloon(surveyId, entityId)
              val draft = draftStore.getDraft(surveyId, entityId)
              val props = entity?.properties.orEmpty()
              val liveStatus = props["status"]?.string_value.orEmpty()
              val liveSymbol = props["marker-symbol"]?.string_value.orEmpty()
              val liveColor = props["marker-color"]?.string_value.orEmpty()
              val entityLabel =
                entity?.label?.ifBlank { entity.geo_id.ifBlank { entity.entity_id } }.orEmpty()
              jsonResponse(
                HttpStatus.OK,
                mapOf(
                  "surveyId" to surveyId,
                  "entityId" to entityId,
                  "entityLabel" to entityLabel,
                  "status" to liveStatus,
                  "markerSymbol" to liveSymbol,
                  "markerColor" to liveColor,
                  "draftFormId" to draft?.formId.orEmpty(),
                  "draftRecordBase64" to draft?.recordBase64.orEmpty(),
                ),
              )
            }
            .exceptionally(::errorResponse)
        )
      HttpMethod.POST ->
        HttpResponse.of(
          req
            .aggregate()
            .thenApply { agg ->
              val body = parseJsonMap(agg.contentUtf8())
              val formId = body["formId"].orEmpty().trim()
              val recordBase64 = body["recordBase64"].orEmpty().trim()
              val ok = draftStore.saveDraft(surveyId, entityId, formId, recordBase64)
              if (!ok) {
                jsonResponse(
                  HttpStatus.BAD_REQUEST,
                  mapOf("status" to "invalid_request", "message" to "Invalid draft payload"),
                )
              } else {
                jsonResponse(
                  HttpStatus.OK,
                  mapOf("status" to if (recordBase64.isEmpty()) "cleared" else "saved"),
                )
              }
            }
            .exceptionally(::errorResponse)
        )
      HttpMethod.DELETE -> {
        draftStore.clearDraft(surveyId, entityId)
        jsonResponse(HttpStatus.OK, mapOf("status" to "cleared"))
      }
      else -> HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED)
    }
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
    return jsonResponse(
      HttpStatus.INTERNAL_SERVER_ERROR,
      mapOf(
        "status" to "error",
        "message" to (statusEx.status.description ?: statusEx.status.code.name),
      ),
    )
  }
}
