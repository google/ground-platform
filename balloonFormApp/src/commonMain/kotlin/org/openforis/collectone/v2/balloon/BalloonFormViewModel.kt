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

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import openforis.collectone.v1.data.SubmissionFieldEntryDef
import openforis.collectone.v1.forms.RecordInstance
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.ui.FormVariant
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.core.ids.RecordIds

/**
 * ViewModel for the Google Earth KML balloon form runner ([BalloonFormScreen]).
 *
 * Owns the form selection, [FormWizardController] lifecycle, live entity state & draft restoration,
 * debounced draft persistence, PKCE browser-tab authentication polling, and `MutateData`
 * submission/retry orchestration, exposing immutable [uiState] and one-shot [effects] to passive
 * Compose views.
 */
class BalloonFormViewModel(
  initialConfig: BalloonConfig,
  private val client: BalloonClient,
  var authPageUrl: String = "/balloon-auth.html",
  dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) : ViewModel(), BalloonFormActions {

  private val scope = CoroutineScope(SupervisorJob() + dispatcher)

  private var baseConfig: BalloonConfig = initialConfig
  private var liveEntityState: BalloonEntityLiveState? = null
  private var restoredDraftRecord: RecordInstance? = null
  private var fetchJob: Job? = null
  private var draftSaveJob: Job? = null
  private var authPollJob: Job? = null

  private val _uiState = MutableStateFlow(buildInitialState(initialConfig))
  val uiState: StateFlow<BalloonFormUiState> = _uiState.asStateFlow()

  private val _effects =
    MutableSharedFlow<BalloonFormEffect>(
      extraBufferCapacity = 8,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  val effects: SharedFlow<BalloonFormEffect> = _effects.asSharedFlow()

  init {
    refreshLiveEntityState(initialConfig)
  }

  /**
   * Updates the ViewModel when the host delivers a new [BalloonConfig] (for example, via URL
   * parameter updates or `postMessage` from Google Earth).
   */
  fun updateConfig(newConfig: BalloonConfig) {
    if (newConfig == baseConfig) return
    val entityChanged =
      newConfig.serviceUrl != baseConfig.serviceUrl ||
        newConfig.surveyId != baseConfig.surveyId ||
        newConfig.entityId != baseConfig.entityId
    baseConfig = newConfig
    if (entityChanged) {
      liveEntityState = null
      restoredDraftRecord = null
      draftSaveJob?.cancel()
      authPollJob?.cancel()
      _uiState.value = buildInitialState(newConfig)
      refreshLiveEntityState(newConfig)
    } else {
      val forms = resolveForms(newConfig)
      val effective = effectiveConfig(newConfig, liveEntityState)
      val selectedIndex =
        _uiState.value.selectedFormIndex?.takeIf { it in forms.indices }
          ?: if (forms.size == 1) 0 else null
      val nextGen = _uiState.value.sessionGeneration + 1
      val activeForm = selectedIndex?.let { forms.getOrNull(it) }
      val controller =
        createController(
          form = activeForm,
          restoredFormId = _uiState.value.restoredDraftFormId,
          restoredRecord = restoredDraftRecord,
        )
      _uiState.update {
        it.copy(
          config = effective,
          forms = forms,
          selectedFormIndex = selectedIndex,
          formController = controller,
          sessionGeneration = nextGen,
        )
      }
    }
  }

  override fun selectForm(index: Int) {
    authPollJob?.cancel()
    val state = _uiState.value
    val form = state.forms.getOrNull(index)
    val controller =
      createController(
        form = form,
        restoredFormId = state.restoredDraftFormId,
        restoredRecord = restoredDraftRecord,
      )
    _uiState.update {
      it.copy(
        selectedFormIndex = index,
        formController = controller,
        pendingAuth = null,
        failedSubmission = null,
        completedSubmission = null,
      )
    }
  }

  override fun backToFormChooser() {
    authPollJob?.cancel()
    _uiState.update {
      it.copy(
        selectedFormIndex = null,
        formController = null,
        pendingAuth = null,
        failedSubmission = null,
        completedSubmission = null,
      )
    }
  }

  override fun submitForm(result: FinalizationResult.Success) {
    val state = _uiState.value
    if (state.isSubmitting) return
    val activeForm = state.activeForm ?: return
    val controller = state.formController ?: return
    val entries = extractAnsweredEntries(controller.formState)
    val submissionId = RecordIds.newId()
    val summaryJson =
      buildSubmissionSummaryJson(
        config = state.config,
        form = activeForm,
        entries = entries,
        submissionId = submissionId,
      )

    if (state.config.canSyncWithViewerAuth && !state.viewerSession.isAuthenticated) {
      startPendingAuth(
        form = activeForm,
        result = result,
        entries = entries,
        submissionId = submissionId,
        summaryJson = summaryJson,
        effectiveConfig = state.config,
      )
    } else {
      scope.launch {
        performSubmission(
          form = activeForm,
          result = result,
          entries = entries,
          submissionId = submissionId,
          summaryJson = summaryJson,
          session = state.viewerSession,
        )
      }
    }
  }

  override fun retrySubmission() {
    val state = _uiState.value
    val failed = state.failedSubmission ?: return
    if (state.isSubmitting) return
    scope.launch {
      performSubmission(
        form = failed.form,
        result = failed.result,
        entries = failed.entries,
        submissionId = failed.submissionId,
        summaryJson = failed.summaryJson,
        session = state.viewerSession,
      )
    }
  }

  override fun cancelPendingAuth() {
    authPollJob?.cancel()
    _uiState.update { it.copy(pendingAuth = null) }
  }

  override fun openSignIn() {
    val state = _uiState.value
    val pending = state.pendingAuth ?: return
    val nextPkce = if (pending.errorMessage != null) createBalloonPkceSession() else pending.pkce
    val updated = pending.copy(pkce = nextPkce, errorMessage = null)
    _uiState.update { it.copy(pendingAuth = updated) }
    _effects.tryEmit(
      BalloonFormEffect.OpenExternalUrl(
        buildBalloonAuthBridgeUrl(authPageUrl, nextPkce, state.config)
      )
    )
    startAuthPolling(updated)
  }

  override fun switchAccount() {
    val state = _uiState.value
    val failed = state.failedSubmission ?: return
    authPollJob?.cancel()
    _uiState.update {
      it.copy(viewerSession = BalloonViewerSession(), failedSubmission = null)
    }
    startPendingAuth(
      form = failed.form,
      result = failed.result,
      entries = failed.entries,
      submissionId = failed.submissionId,
      summaryJson = failed.summaryJson,
      effectiveConfig = state.config,
    )
  }

  override fun backToFormFromError() {
    _uiState.update { it.copy(failedSubmission = null) }
  }

  override fun discardRestoredDraft() {
    draftSaveJob?.cancel()
    restoredDraftRecord = null
    val state = _uiState.value
    val activeForm = state.activeForm
    val nextGen = state.sessionGeneration + 1
    val controller = createController(form = activeForm, restoredFormId = "", restoredRecord = null)
    _uiState.update {
      it.copy(
        restoredDraftFormId = "",
        showDraftRestoredBanner = false,
        sessionGeneration = nextGen,
        formController = controller,
      )
    }
    if (
      activeForm != null && baseConfig.surveyId.isNotBlank() && baseConfig.entityId.isNotBlank()
    ) {
      scope.launch {
        client.saveDraft(
          baseConfig.serviceUrl,
          baseConfig.surveyId,
          baseConfig.entityId,
          activeForm.id,
          "",
        )
      }
    }
  }

  override fun startAnotherEntry() {
    val state = _uiState.value
    val nextGen = state.sessionGeneration + 1
    val nextSelectedIndex = if (state.forms.size > 1) null else state.selectedFormIndex
    val nextForm = nextSelectedIndex?.let { state.forms.getOrNull(it) }
    val controller =
      createController(
        form = nextForm,
        restoredFormId = state.restoredDraftFormId,
        restoredRecord = restoredDraftRecord,
      )
    _uiState.update {
      it.copy(
        completedSubmission = null,
        failedSubmission = null,
        sessionGeneration = nextGen,
        selectedFormIndex = nextSelectedIndex,
        formController = controller,
      )
    }
  }

  /**
   * Cancels in-flight ViewModel jobs when a host disposes the ViewModel outside a ViewModelStore.
   */
  fun dispose() {
    onCleared()
  }

  override fun onCleared() {
    scope.cancel()
  }

  private fun buildInitialState(config: BalloonConfig): BalloonFormUiState {
    val forms = resolveForms(config)
    val selectedIndex = if (forms.size == 1) 0 else null
    val activeForm = selectedIndex?.let { forms.getOrNull(it) }
    return BalloonFormUiState(
      config = effectiveConfig(config, liveEntityState),
      forms = forms,
      selectedFormIndex = selectedIndex,
      formController =
        createController(form = activeForm, restoredFormId = "", restoredRecord = null),
      viewerSession = config.initialViewerSession(),
    )
  }

  private fun resolveForms(config: BalloonConfig): List<BalloonFormEntry> =
    if (config.forms.isNotEmpty() || config.surveyId.isNotBlank()) {
      config.forms
    } else {
      listOf(defaultDemoBalloonForm())
    }

  private fun effectiveConfig(
    config: BalloonConfig,
    liveState: BalloonEntityLiveState?,
  ): BalloonConfig = liveState?.let { config.withLiveEntityState(it) } ?: config

  private fun createController(
    form: BalloonFormEntry?,
    restoredFormId: String,
    restoredRecord: RecordInstance?,
  ): FormWizardController? {
    form ?: return null
    val existing = if (restoredFormId == form.id) restoredRecord else null
    return FormWizardController(
      formDef = form.formDef,
      existingRecord = existing,
      onRecordUpdated = { recordInstance, formState ->
        scheduleDraftSave(form.id, recordInstance, formState)
      },
      variant = FormVariant.GOOGLE_EARTH,
    )
  }

  private fun scheduleDraftSave(
    formId: String,
    recordInstance: RecordInstance,
    formState: org.openforis.collectone.v2.core.forms.model.FormState,
  ) {
    if (baseConfig.surveyId.isBlank() || baseConfig.entityId.isBlank()) return
    val answered = extractAnsweredEntries(formState)
    val draftBase64 = if (answered.isNotEmpty()) encodeDraftRecordBase64(recordInstance) else ""
    draftSaveJob?.cancel()
    draftSaveJob = scope.launch {
      delay(DRAFT_DEBOUNCE_MS)
      client.saveDraft(
        serviceUrl = baseConfig.serviceUrl,
        surveyId = baseConfig.surveyId,
        entityId = baseConfig.entityId,
        formId = formId,
        recordBase64 = draftBase64,
      )
    }
  }

  private fun refreshLiveEntityState(config: BalloonConfig) {
    fetchJob?.cancel()
    if (config.surveyId.isBlank() || config.entityId.isBlank()) return
    fetchJob = scope.launch {
      val fetched = client.fetchEntityState(config.serviceUrl, config.surveyId, config.entityId)
      if (fetched != null) {
        liveEntityState = fetched
        val state = _uiState.value
        val updatedConfig = effectiveConfig(baseConfig, fetched)
        val decodedDraft = decodeDraftRecordBase64(fetched.draftRecordBase64)
        val canRestoreDraft =
          decodedDraft != null &&
            state.completedSubmission == null &&
            state.pendingAuth == null &&
            state.failedSubmission == null
        val matchingFormIndex =
          if (canRestoreDraft) {
            if (fetched.draftFormId.isNotBlank()) {
              state.forms.indexOfFirst { it.id == fetched.draftFormId }.takeIf { it >= 0 }
            } else {
              state.selectedFormIndex ?: if (state.forms.isNotEmpty()) 0 else null
            }
          } else {
            null
          }
        if (matchingFormIndex != null && decodedDraft != null) {
          val restoredForm = state.forms[matchingFormIndex]
          restoredDraftRecord = decodedDraft
          val controller =
            createController(
              form = restoredForm,
              restoredFormId = restoredForm.id,
              restoredRecord = decodedDraft,
            )
          _uiState.update {
            it.copy(
              config = updatedConfig,
              restoredDraftFormId = restoredForm.id,
              showDraftRestoredBanner = true,
              selectedFormIndex = matchingFormIndex,
              sessionGeneration = it.sessionGeneration + 1,
              formController = controller,
            )
          }
        } else {
          _uiState.update { it.copy(config = updatedConfig) }
        }
      }
    }
  }

  private fun startPendingAuth(
    form: BalloonFormEntry,
    result: FinalizationResult.Success,
    entries: List<SubmissionFieldEntryDef>,
    submissionId: String,
    summaryJson: String,
    effectiveConfig: BalloonConfig,
  ) {
    val pkce = createBalloonPkceSession()
    val pending =
      PendingAuthBalloonSubmission(
        form = form,
        result = result,
        entries = entries,
        submissionId = submissionId,
        summaryJson = summaryJson,
        pkce = pkce,
      )
    _uiState.update { it.copy(pendingAuth = pending) }
    _effects.tryEmit(
      BalloonFormEffect.OpenExternalUrl(
        buildBalloonAuthBridgeUrl(authPageUrl, pkce, effectiveConfig)
      )
    )
    startAuthPolling(pending)
  }

  private fun startAuthPolling(waiting: PendingAuthBalloonSubmission) {
    authPollJob?.cancel()
    authPollJob = scope.launch {
      repeat(AUTH_POLL_MAX_ATTEMPTS) {
        when (val claim = client.claimAuthSession(baseConfig.serviceUrl, waiting.pkce)) {
          is BalloonClaimPollResult.Authenticated -> {
            _uiState.update { it.copy(viewerSession = claim.session) }
            performSubmission(
              form = waiting.form,
              result = waiting.result,
              entries = waiting.entries,
              submissionId = waiting.submissionId,
              summaryJson = waiting.summaryJson,
              session = claim.session,
            )
            return@launch
          }
          is BalloonClaimPollResult.Failed -> {
            _uiState.update { state ->
              val current = state.pendingAuth
              if (current?.pkce?.sessionId == waiting.pkce.sessionId) {
                state.copy(pendingAuth = current.copy(errorMessage = claim.message))
              } else {
                state
              }
            }
            return@launch
          }
          BalloonClaimPollResult.Pending -> delay(AUTH_POLL_INTERVAL_MS)
        }
      }
      _uiState.update { state ->
        val current = state.pendingAuth
        if (current?.pkce?.sessionId == waiting.pkce.sessionId) {
          state.copy(
            pendingAuth =
              current.copy(
                errorMessage =
                  "Sign-in timed out before completing. Click Sign in to open Collect One again."
              )
          )
        } else {
          state
        }
      }
    }
  }

  private suspend fun performSubmission(
    form: BalloonFormEntry,
    result: FinalizationResult.Success,
    entries: List<SubmissionFieldEntryDef>,
    submissionId: String,
    summaryJson: String,
    session: BalloonViewerSession,
  ) {
    _uiState.update { it.copy(isSubmitting = true, failedSubmission = null) }
    val outcome =
      client.submitRecord(
        config = _uiState.value.config.withViewerSession(session),
        viewerSession = session,
        form = form,
        result = result,
        entries = entries,
        submissionId = submissionId,
      )
    when (outcome) {
      is BalloonSubmissionOutcome.AuthenticationRequired -> {
        val currentConfig = _uiState.value.config
        _uiState.update {
          it.copy(isSubmitting = false, viewerSession = BalloonViewerSession())
        }
        startPendingAuth(
          form = form,
          result = result,
          entries = entries,
          submissionId = submissionId,
          summaryJson = summaryJson,
          effectiveConfig = currentConfig,
        )
      }
      is BalloonSubmissionOutcome.SyncedToServer -> {
        draftSaveJob?.cancel()
        restoredDraftRecord = null
        if (baseConfig.surveyId.isNotBlank() && baseConfig.entityId.isNotBlank()) {
          client.saveDraft(
            baseConfig.serviceUrl,
            baseConfig.surveyId,
            baseConfig.entityId,
            form.id,
            "",
          )
        }
        val priorState = liveEntityState ?: BalloonEntityLiveState()
        val nextLiveState =
          priorState.copy(
            markerSymbol = outcome.nextMarkerSymbol,
            status = outcome.nextStatus,
            markerColor = outcome.nextMarkerColor.ifBlank { priorState.markerColor },
            draftFormId = "",
            draftRecordBase64 = "",
          )
        liveEntityState = nextLiveState
        _uiState.update {
          it.copy(
            config = effectiveConfig(baseConfig, nextLiveState),
            isSubmitting = false,
            pendingAuth = null,
            failedSubmission = null,
            restoredDraftFormId = "",
            showDraftRestoredBanner = false,
            completedSubmission =
              CompletedBalloonSubmission(
                formTitle = form.title,
                formDef = form.formDef,
                entries = entries,
                summaryJson = summaryJson,
                outcome = outcome,
              ),
          )
        }
      }
      is BalloonSubmissionOutcome.UploadFailed -> {
        _uiState.update {
          it.copy(
            isSubmitting = false,
            pendingAuth = null,
            completedSubmission = null,
            failedSubmission =
              FailedBalloonSubmission(
                form = form,
                result = result,
                entries = entries,
                submissionId = submissionId,
                summaryJson = summaryJson,
                message = outcome.message,
              ),
          )
        }
      }
    }
  }

  private companion object {
    const val DRAFT_DEBOUNCE_MS = 200L
    const val AUTH_POLL_INTERVAL_MS = 1_000L
    const val AUTH_POLL_MAX_ATTEMPTS = 180
  }
}
