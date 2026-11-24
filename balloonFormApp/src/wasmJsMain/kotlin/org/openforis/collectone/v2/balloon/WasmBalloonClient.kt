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

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.SubmissionFieldEntryDef
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.ids.RecordIds

@JsFun("() => Math.floor(Date.now() / 1000)") private external fun jsEpochSeconds(): Double

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

/**
 * Browser Wasm implementation of [BalloonClient] that delegates HTTP and gRPC-Web requests to the
 * JS bridge in `balloon-form.html`.
 */
class WasmBalloonClient(private val clientId: String = "earth-balloon-${RecordIds.newId()}") :
  BalloonClient {
  private var nextClientSeq: Long = 1L

  private fun allocateSeq(): Long {
    val seq = nextClientSeq
    nextClientSeq += 2L
    return seq
  }

  override suspend fun fetchEntityState(
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

  override suspend fun saveDraft(
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

  override suspend fun claimAuthSession(
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

  override suspend fun submitRecord(
    config: BalloonConfig,
    viewerSession: BalloonViewerSession,
    form: BalloonFormEntry,
    result: FinalizationResult.Success,
    entries: List<SubmissionFieldEntryDef>,
    submissionId: String,
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
}
