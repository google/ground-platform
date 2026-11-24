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
import openforis.collectone.v1.forms.FormDef
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.ui.FormWizardController

/** Outcome of submitting a form inside the KML balloon. */
sealed interface BalloonSubmissionOutcome {
  data class SyncedToServer(
    val submissionId: String,
    val nextMarkerSymbol: String,
    val collectorEmail: String = "",
    val nextStatus: String = if (nextMarkerSymbol == "◐") "In progress" else "Completed",
    val nextMarkerColor: String = "",
  ) : BalloonSubmissionOutcome

  data object AuthenticationRequired : BalloonSubmissionOutcome

  data class UploadFailed(val message: String) : BalloonSubmissionOutcome
}

data class CompletedBalloonSubmission(
  val formTitle: String,
  val formDef: FormDef,
  val entries: List<SubmissionFieldEntryDef>,
  val summaryJson: String,
  val outcome: BalloonSubmissionOutcome.SyncedToServer,
)

data class FailedBalloonSubmission(
  val form: BalloonFormEntry,
  val result: FinalizationResult.Success,
  val entries: List<SubmissionFieldEntryDef>,
  val submissionId: String,
  val summaryJson: String,
  val message: String,
)

data class PendingAuthBalloonSubmission(
  val form: BalloonFormEntry,
  val result: FinalizationResult.Success,
  val entries: List<SubmissionFieldEntryDef>,
  val submissionId: String,
  val summaryJson: String,
  val pkce: BalloonPkceSession,
  val errorMessage: String? = null,
)

/** One-shot side effects emitted by [BalloonFormViewModel] to the host. */
sealed interface BalloonFormEffect {
  data class OpenExternalUrl(val url: String) : BalloonFormEffect
}

/** Immutable UI state exposed by [BalloonFormViewModel] and observed by [BalloonFormScreen]. */
data class BalloonFormUiState(
  val config: BalloonConfig,
  val forms: List<BalloonFormEntry>,
  val selectedFormIndex: Int?,
  val formController: FormWizardController?,
  val sessionGeneration: Int = 0,
  val viewerSession: BalloonViewerSession = config.initialViewerSession(),
  val isSubmitting: Boolean = false,
  val restoredDraftFormId: String = "",
  val showDraftRestoredBanner: Boolean = false,
  val pendingAuth: PendingAuthBalloonSubmission? = null,
  val failedSubmission: FailedBalloonSubmission? = null,
  val completedSubmission: CompletedBalloonSubmission? = null,
) {
  val activeForm: BalloonFormEntry?
    get() = selectedFormIndex?.let { forms.getOrNull(it) }

  val isBusy: Boolean
    get() = isSubmitting || (pendingAuth != null && pendingAuth.errorMessage == null)

  val showFormHeader: Boolean
    get() = forms.size > 1 && activeForm != null
}

/** User intents of the KML balloon form runner, implemented by [BalloonFormViewModel]. */
interface BalloonFormActions {
  fun selectForm(index: Int)

  fun backToFormChooser()

  fun submitForm(result: FinalizationResult.Success)

  fun retrySubmission()

  fun cancelPendingAuth()

  fun openSignIn()

  fun switchAccount()

  fun backToFormFromError()

  fun discardRestoredDraft()

  fun startAnotherEntry()
}
