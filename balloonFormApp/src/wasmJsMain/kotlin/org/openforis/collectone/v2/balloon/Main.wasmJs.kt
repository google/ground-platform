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
@file:OptIn(ExperimentalWasmJsInterop::class)

package org.openforis.collectone.v2.balloon

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlin.coroutines.resume
import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.SubmissionFieldEntryDef
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.ids.RecordIds

@JsFun("() => window.location.search || ''") private external fun jsLocationSearch(): String

@JsFun("() => window.location.hash || ''") private external fun jsLocationHash(): String

@JsFun(
  "() => { try { return Boolean(window.parent && window.parent !== window); } catch (e) { return true; } }"
)
private external fun jsIsEmbeddedInParent(): Boolean

@JsFun(
  "() => typeof window.COLLECT_ONE_USER_DATA_SERVICE_URL === 'string' " +
    "? window.COLLECT_ONE_USER_DATA_SERVICE_URL : ''"
)
private external fun jsDefaultServiceUrl(): String

@JsFun("() => Math.floor(Date.now() / 1000)") private external fun jsEpochSeconds(): Double

@JsFun("() => window.CollectOneBalloonBridge.authPageUrl()")
private external fun jsAuthPageUrl(): String

@JsFun("(url) => window.CollectOneBalloonBridge.openExternalUrl(url)")
private external fun jsOpenExternalUrl(url: String)

@JsFun(
  "(serviceUrl, sessionId, codeVerifier, callback) => " +
    "window.CollectOneBalloonBridge.claimAuthSession(" +
    "serviceUrl, sessionId, codeVerifier, " +
    "(statusCode, bodyText, errText) => callback(statusCode, bodyText, errText))"
)
private external fun jsClaimAuthSession(
  serviceUrl: String,
  sessionId: String,
  codeVerifier: String,
  callback: (Int, JsString?, JsString?) -> Unit,
)

@JsFun(
  "(serviceUrl, surveyId, entityId, callback) => " +
    "window.CollectOneBalloonBridge.fetchEntityState(" +
    "serviceUrl, surveyId, entityId, " +
    "(statusCode, bodyText, errText) => callback(statusCode, bodyText, errText))"
)
private external fun jsFetchEntityState(
  serviceUrl: String,
  surveyId: String,
  entityId: String,
  callback: (Int, JsString?, JsString?) -> Unit,
)

@JsFun(
  "(serviceUrl, surveyId, entityId, formId, recordBase64, callback) => " +
    "window.CollectOneBalloonBridge.saveDraftState(" +
    "serviceUrl, surveyId, entityId, formId, recordBase64, " +
    "(statusCode) => callback(statusCode))"
)
private external fun jsSaveDraftState(
  serviceUrl: String,
  surveyId: String,
  entityId: String,
  formId: String,
  recordBase64: String,
  callback: (Int) -> Unit,
)

@JsFun(
  "(onParamsJson) => window.CollectOneBalloonBridge.subscribe(" + "(json) => onParamsJson(json))"
)
private external fun jsSubscribeBalloonMessages(onParamsJson: (JsString) -> Unit): JsAny

@JsFun("(handle) => window.CollectOneBalloonBridge.unsubscribe(handle)")
private external fun jsUnsubscribeBalloonMessages(handle: JsAny)

@JsFun("(text) => window.CollectOneBalloonBridge.copyText(text)")
private external fun jsCopyText(text: String): Boolean

@JsFun(
  "(url, authToken, devHeadersJson, framedBase64, callback) => " +
    "window.CollectOneBalloonBridge.postGrpcWeb(" +
    "url, authToken, devHeadersJson, framedBase64, " +
    "(respBase64, statusHeader, msgHeader, netErr) => " +
    "callback(respBase64, statusHeader, msgHeader, netErr))"
)
private external fun jsPostGrpcWeb(
  url: String,
  authToken: String,
  devHeadersJson: String,
  framedBase64: String,
  callback: (JsString?, JsString?, JsString?, JsString?) -> Unit,
)

private val messageJson = Json { ignoreUnknownKeys = true }

/**
 * Entry point of the standalone KML balloon form runner (`balloonFormApp.js`).
 *
 * Runs inside sandboxed KML balloon iframes (`Origin: null`) without touching browser storage,
 * session history, or popup windows. Reads initial plot and form parameters from `location.search`
 * + `location.hash`, and authenticates submissions via a single-use PKCE top-level tab handoff to
 *   `/balloon-auth.html` so KML files never contain credentials.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  val container = document.getElementById("ComposeTarget") ?: document.body!!
  val clientId = "earth-balloon-${RecordIds.newId()}"
  var nextClientSeq = 1L

  ComposeViewport(container) {
    val defaultServiceUrl = remember { jsDefaultServiceUrl() }
    val authPageUrl = remember { jsAuthPageUrl() }
    val embeddedInBalloon = remember { jsIsEmbeddedInParent() }
    var mergedParams by remember {
      mutableStateOf(parseUrlParams(jsLocationSearch()) + parseUrlParams(jsLocationHash()))
    }

    DisposableEffect(Unit) {
      val handle = jsSubscribeBalloonMessages { rawJson ->
        val incoming = parseJsonStringMap(rawJson.toString())
        if (incoming.isNotEmpty()) {
          mergedParams = mergedParams + incoming
        }
      }
      onDispose { jsUnsubscribeBalloonMessages(handle) }
    }

    val config =
      remember(mergedParams, defaultServiceUrl) {
        decodeBalloonConfig(mergedParams, defaultServiceUrl = defaultServiceUrl)
      }
    var liveEntityState by
      remember(config.surveyId, config.entityId) { mutableStateOf<BalloonEntityLiveState?>(null) }
    val darkTheme = remember(mergedParams) { mergedParams["theme"]?.lowercase() == "dark" }

    BalloonFormScreen(
      config = config,
      authPageUrl = authPageUrl,
      onOpenExternalUrl = { url -> jsOpenExternalUrl(url) },
      onClaimAuthSession = { serviceUrl, pkce -> claimAuthSession(serviceUrl, pkce) },
      onFetchEntityState = { serviceUrl, surveyId, entityId ->
        fetchEntityState(serviceUrl, surveyId, entityId)?.also { liveEntityState = it }
      },
      onSaveDraft = { serviceUrl, surveyId, entityId, formId, recordBase64 ->
        saveDraftState(serviceUrl, surveyId, entityId, formId, recordBase64)
      },
      embeddedInBalloon = embeddedInBalloon,
      darkTheme = darkTheme,
      onCopyText = { text -> jsCopyText(text) },
      onSubmitRecord = { form, result, entries, submissionId, viewerSession ->
        val effectiveConfig =
          (liveEntityState?.let { config.withLiveEntityState(it) } ?: config).withViewerSession(
            viewerSession
          )
        submitFromBalloon(
            config = effectiveConfig,
            viewerSession = viewerSession,
            form = form,
            result = result,
            entries = entries,
            submissionId = submissionId,
            clientId = clientId,
            allocateSeq = {
              val seq = nextClientSeq
              nextClientSeq += 2L
              seq
            },
          )
          .also { outcome ->
            if (outcome is BalloonSubmissionOutcome.SyncedToServer) {
              val priorState = liveEntityState ?: BalloonEntityLiveState()
              liveEntityState =
                priorState.copy(
                  markerSymbol = outcome.nextMarkerSymbol,
                  status = outcome.nextStatus,
                  markerColor = outcome.nextMarkerColor.ifBlank { priorState.markerColor },
                  draftFormId = "",
                  draftRecordBase64 = "",
                )
            }
          }
      },
    )
  }
}

private suspend fun fetchEntityState(
  serviceUrl: String,
  surveyId: String,
  entityId: String,
): BalloonEntityLiveState? = suspendCancellableCoroutine { continuation ->
  jsFetchEntityState(
    serviceUrl = serviceUrl,
    surveyId = surveyId,
    entityId = entityId,
  ) { statusCode, bodyText, errText ->
    if (!continuation.isActive) return@jsFetchEntityState
    continuation.resume(
      parseBalloonEntityStateResponse(
        httpStatus = statusCode,
        responseBody = bodyText?.toString().orEmpty(),
        networkError = errText?.toString().orEmpty(),
      )
    )
  }
}

private suspend fun saveDraftState(
  serviceUrl: String,
  surveyId: String,
  entityId: String,
  formId: String,
  recordBase64: String,
) = suspendCancellableCoroutine { continuation ->
  jsSaveDraftState(
    serviceUrl = serviceUrl,
    surveyId = surveyId,
    entityId = entityId,
    formId = formId,
    recordBase64 = recordBase64,
  ) { _ ->
    if (!continuation.isActive) return@jsSaveDraftState
    continuation.resume(Unit)
  }
}

private suspend fun claimAuthSession(
  serviceUrl: String,
  pkce: BalloonPkceSession,
): BalloonClaimPollResult = suspendCancellableCoroutine { continuation ->
  jsClaimAuthSession(
    serviceUrl = serviceUrl,
    sessionId = pkce.sessionId,
    codeVerifier = pkce.codeVerifier,
  ) { statusCode, bodyText, errText ->
    if (!continuation.isActive) return@jsClaimAuthSession
    continuation.resume(
      parseBalloonClaimResponse(
        httpStatus = statusCode,
        responseBody = bodyText?.toString().orEmpty(),
        networkError = errText?.toString().orEmpty(),
      )
    )
  }
}

private fun parseJsonStringMap(rawJson: String): Map<String, String> = runCatching {
  val obj = messageJson.parseToJsonElement(rawJson) as? JsonObject ?: return emptyMap()
  buildMap {
    obj.forEach { (key, element) ->
      runCatching { element.jsonPrimitive.content }.getOrNull()?.let { put(key, it) }
    }
  }
}
  .getOrElse { emptyMap() }

private suspend fun submitFromBalloon(
  config: BalloonConfig,
  viewerSession: BalloonViewerSession,
  form: BalloonFormEntry,
  result: FinalizationResult.Success,
  entries: List<SubmissionFieldEntryDef>,
  submissionId: String,
  clientId: String,
  allocateSeq: () -> Long,
): BalloonSubmissionOutcome {
  if (!config.canSyncWithViewerAuth) {
    val reason =
      "Cannot upload submission: this KML balloon is not linked to a live Collect One survey server. Open or export a survey KML from the Collect One Web Console."
    return BalloonSubmissionOutcome.UploadFailed(message = reason)
  }
  if (!viewerSession.isAuthenticated) {
    return BalloonSubmissionOutcome.AuthenticationRequired
  }

  val (request, nextSymbol) =
    buildBalloonMutateDataRequest(
      config = config,
      form = form,
      result = result,
      entries = entries,
      submissionId = submissionId,
      clientId = clientId,
      baseSeq = allocateSeq(),
      nowEpochSeconds = jsEpochSeconds().toLong(),
    )
  val entityProps =
    request.mutations.firstNotNullOfOrNull { it.entity_mutation?.upsert?.properties }.orEmpty()
  val nextStatus =
    entityProps["status"]?.string_value?.takeIf { it.isNotBlank() }
      ?: if (nextSymbol == "◐") "In progress" else "Completed"
  val nextColor = entityProps["marker-color"]?.string_value.orEmpty()
  val framedBytes = frameGrpcWebRequest(MutateDataRequest.ADAPTER.encode(request))
  val endpoint = "${config.serviceUrl}/openforis.collectone.v1.data.DataService/MutateData"

  return suspendCancellableCoroutine { continuation ->
    jsPostGrpcWeb(
      url = endpoint,
      authToken = viewerSession.authToken.orEmpty(),
      devHeadersJson = viewerSession.devHeadersJson(),
      framedBase64 = encodeBase64Url(framedBytes),
    ) { respBase64, statusHeader, msgHeader, netErr ->
      if (!continuation.isActive) return@jsPostGrpcWeb
      val errText = netErr?.toString()?.takeIf { it.isNotBlank() }
      if (errText != null) {
        continuation.resume(
          BalloonSubmissionOutcome.UploadFailed(
            message =
              "Could not reach Collect One server ($errText). Check your connection and retry."
          )
        )
        return@jsPostGrpcWeb
      }
      val bodyBytes =
        respBase64?.toString()?.takeIf { it.isNotBlank() }?.let(::decodeFlexibleBase64)
          ?: ByteArray(0)
      val parsed =
        parseMutateDataGrpcWebResponse(
          bodyBytes = bodyBytes,
          headerGrpcStatus = statusHeader?.toString()?.takeIf { it.isNotBlank() },
          headerGrpcMessage = msgHeader?.toString()?.takeIf { it.isNotBlank() },
        )
      when (parsed) {
        is GrpcWebCallOutcome.Success -> {
          if (parsed.response.results.isEmpty()) {
            continuation.resume(
              BalloonSubmissionOutcome.UploadFailed(
                message =
                  "Collect One server returned an empty confirmation for this submission. Please retry."
              )
            )
            return@jsPostGrpcWeb
          }
          continuation.resume(
            BalloonSubmissionOutcome.SyncedToServer(
              submissionId = submissionId,
              nextMarkerSymbol = nextSymbol,
              collectorEmail = config.collectorEmail,
              nextStatus = nextStatus,
              nextMarkerColor = nextColor,
            )
          )
        }
        is GrpcWebCallOutcome.GrpcFailure -> {
          if (parsed.grpcStatus == 16) {
            continuation.resume(BalloonSubmissionOutcome.AuthenticationRequired)
            return@jsPostGrpcWeb
          }
          val explanation =
            if (parsed.grpcStatus == 7) {
              "Your Collect One account (${config.collectorEmail.ifBlank { "current user" }}) does not have permission to submit to this survey${parsed.grpcMessage?.let { ": $it" }.orEmpty()}."
            } else {
              "Server rejected submission (gRPC ${parsed.grpcStatus}${parsed.grpcMessage?.let { ": $it" }.orEmpty()})."
            }
          continuation.resume(BalloonSubmissionOutcome.UploadFailed(message = explanation))
        }
      }
    }
  }
}
