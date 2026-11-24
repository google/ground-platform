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
package org.openforis.collectone.v2.balloon

import openforis.collectone.v1.data.SubmissionFieldEntryDef
import org.openforis.collectone.v2.core.forms.model.FinalizationResult

/**
 * Transport boundary for the KML balloon form runner's communication with `c1-server` (live entity
 * state, draft persistence, PKCE auth handoff, and `MutateData` gRPC-Web submission).
 */
interface BalloonClient {
  suspend fun fetchEntityState(
    serviceUrl: String,
    surveyId: String,
    entityId: String,
  ): BalloonEntityLiveState?

  suspend fun saveDraft(
    serviceUrl: String,
    surveyId: String,
    entityId: String,
    formId: String,
    recordBase64: String,
  )

  suspend fun claimAuthSession(
    serviceUrl: String,
    pkce: BalloonPkceSession,
  ): BalloonClaimPollResult

  suspend fun submitRecord(
    config: BalloonConfig,
    viewerSession: BalloonViewerSession,
    form: BalloonFormEntry,
    result: FinalizationResult.Success,
    entries: List<SubmissionFieldEntryDef>,
    submissionId: String,
  ): BalloonSubmissionOutcome
}

/**
 * [BalloonClient] adapter backed by caller-provided suspend lambdas (for tests and Compose callback
 * wrappers).
 */
class CallbackBalloonClient(
  var onSubmitRecord:
    suspend (
      form: BalloonFormEntry,
      result: FinalizationResult.Success,
      entries: List<SubmissionFieldEntryDef>,
      submissionId: String,
      viewerSession: BalloonViewerSession,
    ) -> BalloonSubmissionOutcome,
  var onClaimAuthSession:
    suspend (serviceUrl: String, pkce: BalloonPkceSession) -> BalloonClaimPollResult =
    { _, _ ->
      BalloonClaimPollResult.Pending
    },
  var onFetchEntityState:
    suspend (serviceUrl: String, surveyId: String, entityId: String) -> BalloonEntityLiveState? =
    { _, _, _ ->
      null
    },
  var onSaveDraft:
    suspend (
      serviceUrl: String,
      surveyId: String,
      entityId: String,
      formId: String,
      recordBase64: String,
    ) -> Unit =
    { _, _, _, _, _ ->
    },
) : BalloonClient {
  override suspend fun fetchEntityState(
    serviceUrl: String,
    surveyId: String,
    entityId: String,
  ): BalloonEntityLiveState? = onFetchEntityState(serviceUrl, surveyId, entityId)

  override suspend fun saveDraft(
    serviceUrl: String,
    surveyId: String,
    entityId: String,
    formId: String,
    recordBase64: String,
  ) = onSaveDraft(serviceUrl, surveyId, entityId, formId, recordBase64)

  override suspend fun claimAuthSession(
    serviceUrl: String,
    pkce: BalloonPkceSession,
  ): BalloonClaimPollResult = onClaimAuthSession(serviceUrl, pkce)

  override suspend fun submitRecord(
    config: BalloonConfig,
    viewerSession: BalloonViewerSession,
    form: BalloonFormEntry,
    result: FinalizationResult.Success,
    entries: List<SubmissionFieldEntryDef>,
    submissionId: String,
  ): BalloonSubmissionOutcome = onSubmitRecord(form, result, entries, submissionId, viewerSession)
}
