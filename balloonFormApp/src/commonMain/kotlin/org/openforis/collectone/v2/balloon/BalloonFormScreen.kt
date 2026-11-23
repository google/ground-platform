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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import openforis.collectone.v1.data.SubmissionFieldEntryDef
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.RecordInstance
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.ui.ChoiceColorPillsRow
import org.openforis.collectone.v2.core.forms.ui.ChoiceImageRegistry
import org.openforis.collectone.v2.core.forms.ui.CompactFormRunner
import org.openforis.collectone.v2.core.forms.ui.FormVariant
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.core.forms.ui.GoogleEarthTheme
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.ids.RecordIds

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

private data class CompletedBalloonSubmission(
  val formTitle: String,
  val formDef: FormDef,
  val entries: List<SubmissionFieldEntryDef>,
  val summaryJson: String,
  val outcome: BalloonSubmissionOutcome.SyncedToServer,
)

private data class FailedBalloonSubmission(
  val form: BalloonFormEntry,
  val result: FinalizationResult.Success,
  val entries: List<SubmissionFieldEntryDef>,
  val submissionId: String,
  val summaryJson: String,
  val message: String,
)

private data class PendingAuthBalloonSubmission(
  val form: BalloonFormEntry,
  val result: FinalizationResult.Success,
  val entries: List<SubmissionFieldEntryDef>,
  val submissionId: String,
  val summaryJson: String,
  val pkce: BalloonPkceSession,
  val errorMessage: String? = null,
)

/**
 * Root Compose Multiplatform UI rendered inside a KML placemark balloon `<iframe>`.
 *
 * Hosts Collect One's [CompactFormRunner] inside [AppTheme] with a compact plot context banner, a
 * zero-credential PKCE sign-in handoff when submitting to a live Collect One survey, and a
 * confirmation receipt after submission.
 */
@Composable
fun BalloonFormScreen(
  config: BalloonConfig,
  onSubmitRecord:
    suspend (
      form: BalloonFormEntry,
      result: FinalizationResult.Success,
      entries: List<SubmissionFieldEntryDef>,
      submissionId: String,
      viewerSession: BalloonViewerSession,
    ) -> BalloonSubmissionOutcome,
  authPageUrl: String = "/balloon-auth.html",
  onOpenExternalUrl: (String) -> Unit = {},
  onClaimAuthSession:
    suspend (serviceUrl: String, pkce: BalloonPkceSession) -> BalloonClaimPollResult =
    { _, _ ->
      BalloonClaimPollResult.Pending
    },
  onFetchEntityState:
    suspend (serviceUrl: String, surveyId: String, entityId: String) -> BalloonEntityLiveState? =
    { _, _, _ ->
      null
    },
  onSaveDraft:
    suspend (
      serviceUrl: String,
      surveyId: String,
      entityId: String,
      formId: String,
      recordBase64: String,
    ) -> Unit =
    { _, _, _, _, _ ->
    },
  embeddedInBalloon: Boolean = false,
  onCopyText: (String) -> Boolean = { false },
  darkTheme: Boolean = false,
) {
  var liveEntityState by
    remember(config.surveyId, config.entityId) { mutableStateOf<BalloonEntityLiveState?>(null) }
  val effectiveConfig =
    remember(config, liveEntityState) {
      liveEntityState?.let { config.withLiveEntityState(it) } ?: config
    }
  val forms =
    remember(config) {
      if (config.forms.isNotEmpty() || config.surveyId.isNotBlank()) {
        config.forms
      } else {
        listOf(defaultDemoBalloonForm())
      }
    }
  var selectedFormIndex by
    remember(config) { mutableStateOf<Int?>(if (forms.size == 1) 0 else null) }
  var sessionGeneration by remember(config) { mutableIntStateOf(0) }
  var viewerSession by remember(config) { mutableStateOf(config.initialViewerSession()) }
  var isSubmitting by remember(config) { mutableStateOf(false) }
  var restoredDraftFormId by remember(config.surveyId, config.entityId) { mutableStateOf("") }
  var restoredDraftRecord by
    remember(config.surveyId, config.entityId) { mutableStateOf<RecordInstance?>(null) }
  var showDraftRestoredBanner by
    remember(config.surveyId, config.entityId) { mutableStateOf(false) }
  var latestDraftFormId by remember(config.surveyId, config.entityId) { mutableStateOf("") }
  var latestDraftBase64 by
    remember(config.surveyId, config.entityId) { mutableStateOf<String?>(null) }
  var pendingAuth by
    remember(config, selectedFormIndex) { mutableStateOf<PendingAuthBalloonSubmission?>(null) }
  var failedSubmission by
    remember(config, selectedFormIndex) { mutableStateOf<FailedBalloonSubmission?>(null) }
  var completed by
    remember(config, selectedFormIndex) { mutableStateOf<CompletedBalloonSubmission?>(null) }
  val scope = rememberCoroutineScope()

  val activeForm = selectedFormIndex?.let { forms.getOrNull(it) }
  val existingForForm =
    if (activeForm != null && restoredDraftFormId == activeForm.id) restoredDraftRecord else null
  val controller =
    remember(config, activeForm?.id, sessionGeneration, existingForForm) {
      activeForm?.let { form ->
        FormWizardController(
          formDef = form.formDef,
          existingRecord = existingForForm,
          onRecordUpdated = { recordInstance, formState ->
            if (config.surveyId.isNotBlank() && config.entityId.isNotBlank()) {
              val answered = extractAnsweredEntries(formState)
              latestDraftFormId = form.id
              latestDraftBase64 =
                if (answered.isNotEmpty()) {
                  encodeDraftRecordBase64(recordInstance)
                } else {
                  ""
                }
            }
          },
          variant = FormVariant.GOOGLE_EARTH,
        )
      }
    }

  LaunchedEffect(config.serviceUrl, config.surveyId, config.entityId) {
    if (config.surveyId.isBlank() || config.entityId.isBlank()) return@LaunchedEffect
    val fetched = onFetchEntityState(config.serviceUrl, config.surveyId, config.entityId)
    if (fetched != null) {
      liveEntityState = fetched
      val decodedDraft = decodeDraftRecordBase64(fetched.draftRecordBase64)
      if (
        decodedDraft != null && completed == null && pendingAuth == null && failedSubmission == null
      ) {
        val matchingFormIndex =
          if (fetched.draftFormId.isNotBlank()) {
            forms.indexOfFirst { it.id == fetched.draftFormId }.takeIf { it >= 0 }
          } else {
            selectedFormIndex ?: if (forms.isNotEmpty()) 0 else null
          }
        if (matchingFormIndex != null) {
          restoredDraftFormId = forms[matchingFormIndex].id
          restoredDraftRecord = decodedDraft
          showDraftRestoredBanner = true
          selectedFormIndex = matchingFormIndex
          sessionGeneration++
        }
      }
    }
  }

  LaunchedEffect(latestDraftFormId, latestDraftBase64) {
    val draftBase64 = latestDraftBase64 ?: return@LaunchedEffect
    if (config.surveyId.isBlank() || config.entityId.isBlank()) return@LaunchedEffect
    delay(200L)
    onSaveDraft(
      config.serviceUrl,
      config.surveyId,
      config.entityId,
      latestDraftFormId,
      draftBase64,
    )
  }

  suspend fun performSubmission(
    form: BalloonFormEntry,
    result: FinalizationResult.Success,
    entries: List<SubmissionFieldEntryDef>,
    submissionId: String,
    summaryJson: String,
    session: BalloonViewerSession,
  ) {
    isSubmitting = true
    failedSubmission = null
    val outcome = onSubmitRecord(form, result, entries, submissionId, session)
    isSubmitting = false
    when (outcome) {
      is BalloonSubmissionOutcome.AuthenticationRequired -> {
        viewerSession = BalloonViewerSession()
        val freshPkce = createBalloonPkceSession()
        pendingAuth =
          PendingAuthBalloonSubmission(
            form = form,
            result = result,
            entries = entries,
            submissionId = submissionId,
            summaryJson = summaryJson,
            pkce = freshPkce,
          )
        onOpenExternalUrl(buildBalloonAuthBridgeUrl(authPageUrl, freshPkce, effectiveConfig))
      }
      is BalloonSubmissionOutcome.SyncedToServer -> {
        pendingAuth = null
        failedSubmission = null
        restoredDraftRecord = null
        restoredDraftFormId = ""
        showDraftRestoredBanner = false
        latestDraftBase64 = null
        if (config.surveyId.isNotBlank() && config.entityId.isNotBlank()) {
          onSaveDraft(config.serviceUrl, config.surveyId, config.entityId, form.id, "")
        }
        val priorState = liveEntityState ?: BalloonEntityLiveState()
        liveEntityState =
          priorState.copy(
            markerSymbol = outcome.nextMarkerSymbol,
            status = outcome.nextStatus,
            markerColor = outcome.nextMarkerColor.ifBlank { priorState.markerColor },
            draftFormId = "",
            draftRecordBase64 = "",
          )
        completed =
          CompletedBalloonSubmission(
            formTitle = form.title,
            formDef = form.formDef,
            entries = entries,
            summaryJson = summaryJson,
            outcome = outcome,
          )
      }
      is BalloonSubmissionOutcome.UploadFailed -> {
        pendingAuth = null
        completed = null
        failedSubmission =
          FailedBalloonSubmission(
            form = form,
            result = result,
            entries = entries,
            submissionId = submissionId,
            summaryJson = summaryJson,
            message = outcome.message,
          )
      }
    }
  }

  val activePendingAuth = pendingAuth
  LaunchedEffect(activePendingAuth?.pkce?.sessionId) {
    val waiting = activePendingAuth ?: return@LaunchedEffect
    if (waiting.errorMessage != null) return@LaunchedEffect
    repeat(180) {
      when (val claim = onClaimAuthSession(config.serviceUrl, waiting.pkce)) {
        is BalloonClaimPollResult.Authenticated -> {
          viewerSession = claim.session
          performSubmission(
            form = waiting.form,
            result = waiting.result,
            entries = waiting.entries,
            submissionId = waiting.submissionId,
            summaryJson = waiting.summaryJson,
            session = claim.session,
          )
          return@LaunchedEffect
        }
        is BalloonClaimPollResult.Failed -> {
          pendingAuth = waiting.copy(errorMessage = claim.message)
          return@LaunchedEffect
        }
        BalloonClaimPollResult.Pending -> delay(1_000L)
      }
    }
    pendingAuth =
      waiting.copy(
        errorMessage =
          "Sign-in timed out before completing. Click Sign in to open Collect One again."
      )
  }

  GoogleEarthTheme(darkTheme = darkTheme) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        if (forms.size > 1 && activeForm != null) {
          BalloonSelectedFormHeader(
            activeForm = activeForm,
            onBackToChooser = {
              selectedFormIndex = null
              pendingAuth = null
              failedSubmission = null
              completed = null
            },
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (isSubmitting || (activePendingAuth != null && activePendingAuth.errorMessage == null)) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          if (isSubmitting) {
            Text(
              text = "Syncing submission to Collect One…",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
          }
        }

        val completedSnapshot = completed
        val failedSnapshot = failedSubmission
        when {
          forms.isEmpty() -> {
            BalloonNoFormsAvailableCard(
              config = effectiveConfig,
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
          }
          activeForm == null -> {
            BalloonFormChooser(
              config = effectiveConfig,
              forms = forms,
              onSelectForm = { index ->
                selectedFormIndex = index
                pendingAuth = null
                failedSubmission = null
                completed = null
              },
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
          }
          completedSnapshot != null -> {
            BalloonSubmissionReceipt(
              config = effectiveConfig,
              submission = completedSnapshot,
              onCopyJson = onCopyText,
              onStartAnother = {
                completed = null
                failedSubmission = null
                sessionGeneration++
                if (forms.size > 1) {
                  selectedFormIndex = null
                }
              },
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
          }
          activePendingAuth != null -> {
            BalloonAuthHandoffCard(
              config = effectiveConfig,
              pending = activePendingAuth,
              onOpenSignIn = {
                val nextPkce =
                  if (activePendingAuth.errorMessage != null) createBalloonPkceSession()
                  else activePendingAuth.pkce
                pendingAuth = activePendingAuth.copy(pkce = nextPkce, errorMessage = null)
                onOpenExternalUrl(buildBalloonAuthBridgeUrl(authPageUrl, nextPkce, effectiveConfig))
              },
              onCancel = { pendingAuth = null },
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
          }
          failedSnapshot != null -> {
            BalloonUploadErrorCard(
              config = effectiveConfig,
              failed = failedSnapshot,
              isSubmitting = isSubmitting,
              onRetry = {
                if (!isSubmitting) {
                  scope.launch {
                    performSubmission(
                      form = failedSnapshot.form,
                      result = failedSnapshot.result,
                      entries = failedSnapshot.entries,
                      submissionId = failedSnapshot.submissionId,
                      summaryJson = failedSnapshot.summaryJson,
                      session = viewerSession,
                    )
                  }
                }
              },
              onSwitchAccount = {
                viewerSession = BalloonViewerSession()
                val freshPkce = createBalloonPkceSession()
                failedSubmission = null
                pendingAuth =
                  PendingAuthBalloonSubmission(
                    form = failedSnapshot.form,
                    result = failedSnapshot.result,
                    entries = failedSnapshot.entries,
                    submissionId = failedSnapshot.submissionId,
                    summaryJson = failedSnapshot.summaryJson,
                    pkce = freshPkce,
                  )
                onOpenExternalUrl(
                  buildBalloonAuthBridgeUrl(authPageUrl, freshPkce, effectiveConfig)
                )
              },
              onBackToForm = { failedSubmission = null },
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
          }
          else -> {
            val activeController = controller ?: return@Column
            Column(
              modifier =
                Modifier.weight(1f)
                  .fillMaxWidth()
                  .verticalScroll(rememberScrollState())
                  .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
              if (showDraftRestoredBanner && restoredDraftFormId == activeForm.id) {
                BalloonDraftRestoredBanner(
                  onDiscardDraft = {
                    restoredDraftRecord = null
                    restoredDraftFormId = ""
                    showDraftRestoredBanner = false
                    latestDraftFormId = activeForm.id
                    latestDraftBase64 = ""
                    sessionGeneration++
                    scope.launch {
                      if (config.surveyId.isNotBlank() && config.entityId.isNotBlank()) {
                        onSaveDraft(
                          config.serviceUrl,
                          config.surveyId,
                          config.entityId,
                          activeForm.id,
                          "",
                        )
                      }
                    }
                  }
                )
                Spacer(Modifier.height(8.dp))
              }
              key(config, activeForm.id, sessionGeneration, existingForForm) {
                CompactFormRunner(
                  controller = activeController,
                  showHeader = false,
                  variant = FormVariant.GOOGLE_EARTH,
                  questionFilter = { step -> !isEntityReferenceStep(step) },
                  onSubmitted = { result ->
                    if (isSubmitting) return@CompactFormRunner
                    val entries = extractAnsweredEntries(activeController.formState)
                    val submissionId = RecordIds.newId()
                    val summaryJson =
                      buildSubmissionSummaryJson(
                        config = effectiveConfig,
                        form = activeForm,
                        entries = entries,
                        submissionId = submissionId,
                      )
                    if (effectiveConfig.canSyncWithViewerAuth && !viewerSession.isAuthenticated) {
                      val pkce = createBalloonPkceSession()
                      pendingAuth =
                        PendingAuthBalloonSubmission(
                          form = activeForm,
                          result = result,
                          entries = entries,
                          submissionId = submissionId,
                          summaryJson = summaryJson,
                          pkce = pkce,
                        )
                      onOpenExternalUrl(
                        buildBalloonAuthBridgeUrl(authPageUrl, pkce, effectiveConfig)
                      )
                    } else {
                      scope.launch {
                        performSubmission(
                          form = activeForm,
                          result = result,
                          entries = entries,
                          submissionId = submissionId,
                          summaryJson = summaryJson,
                          session = viewerSession,
                        )
                      }
                    }
                  },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BalloonDraftRestoredBanner(
  onDiscardDraft: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier = modifier.fillMaxWidth().testTag("balloon_draft_restored_banner"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "In-progress draft restored",
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.weight(1f),
      )
      TextButton(
        onClick = onDiscardDraft,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.testTag("balloon_discard_draft_button"),
      ) {
        Text(
          text = "Discard",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        )
      }
    }
  }
}

@Composable
private fun BalloonAuthHandoffCard(
  config: BalloonConfig,
  pending: PendingAuthBalloonSubmission,
  onOpenSignIn: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Lock,
          contentDescription = null,
          modifier = Modifier.size(22.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "Confirm your Collect One account",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          )
          Text(
            text =
              pending.errorMessage
                ?: buildString {
                  append("Completing sign-in in a Collect One browser tab so ")
                  append(pending.form.title)
                  if (config.entityName.isNotBlank()) {
                    append(" for ${config.entityName}")
                  }
                  append(" is saved under your account. Your answers are ready to submit.")
                },
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = onCancel,
        modifier = Modifier.weight(1f),
      ) {
        Text("Back to form")
      }
      Button(
        onClick = onOpenSignIn,
        modifier = Modifier.weight(1.3f),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("Sign in")
      }
    }
  }
}

@Composable
private fun BalloonFormChooser(
  config: BalloonConfig,
  forms: List<BalloonFormEntry>,
  onSelectForm: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val contextParts =
    listOf(config.entityName, config.datasetName).filter { it.isNotBlank() }.distinct()
  Column(
    modifier =
      modifier
        .testTag("balloon_form_chooser")
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 14.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      if (contextParts.isNotEmpty()) {
        Text(
          text = contextParts.joinToString(" • "),
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = "Choose a form to complete",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
      )
      Text(
        text =
          if (config.entityName.isNotBlank()) {
            "Select which form you want to fill out for ${config.entityName}."
          } else {
            "Select which form you want to fill out for this map feature."
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Spacer(Modifier.height(2.dp))

    forms.forEachIndexed { index, form ->
      val questionCount = remember(form.formDef) { visibleQuestionCount(form.formDef) }
      OutlinedCard(
        onClick = { onSelectForm(index) },
        modifier = Modifier.fillMaxWidth().testTag("balloon_form_option_${form.id}"),
        shape = MaterialTheme.shapes.medium,
        colors =
          CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(36.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            }
          }
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = form.title,
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = if (questionCount == 1) "1 question" else "$questionCount questions",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun BalloonSelectedFormHeader(
  activeForm: BalloonFormEntry,
  onBackToChooser: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    TextButton(
      onClick = onBackToChooser,
      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
      modifier = Modifier.testTag("balloon_change_form_button"),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      Spacer(Modifier.width(4.dp))
      Text(
        text = "Change form",
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
      )
    }
    Text(
      text = activeForm.title,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f).padding(end = 6.dp).semantics { heading() },
    )
  }
}

@Composable
private fun BalloonNoFormsAvailableCard(
  config: BalloonConfig,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(horizontal = 14.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    OutlinedCard(
      modifier = Modifier.fillMaxWidth().testTag("balloon_no_forms_card"),
      colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = "No Google Earth forms available",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text =
            if (config.entityName.isNotBlank()) {
              "No forms with Open in Google Earth enabled apply to ${config.entityName}."
            } else {
              "No forms with Open in Google Earth enabled apply to this map feature."
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun BalloonUploadErrorCard(
  config: BalloonConfig,
  failed: FailedBalloonSubmission,
  isSubmitting: Boolean,
  onRetry: () -> Unit,
  onSwitchAccount: () -> Unit,
  onBackToForm: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .testTag("balloon_upload_error_card")
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 14.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.errorContainer,
      contentColor = MaterialTheme.colorScheme.onErrorContainer,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.CloudOff,
          contentDescription = null,
          modifier = Modifier.size(22.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "Couldn't sync submission to Collect One",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          )
          Text(
            text = failed.message,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = onBackToForm,
        enabled = !isSubmitting,
        modifier = Modifier.weight(1f).testTag("balloon_back_to_form_button"),
      ) {
        Text("Back to form")
      }
      Button(
        onClick = onRetry,
        enabled = !isSubmitting,
        modifier = Modifier.weight(1.3f).testTag("balloon_retry_upload_button"),
      ) {
        Icon(
          imageVector = Icons.Outlined.Refresh,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("Retry upload")
      }
    }

    if (config.canSyncWithViewerAuth) {
      OutlinedButton(
        onClick = onSwitchAccount,
        enabled = !isSubmitting,
        modifier = Modifier.fillMaxWidth().testTag("balloon_switch_account_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("Sign in with a different Collect One account")
      }
    }
  }
}

@Composable
private fun BalloonSubmissionReceipt(
  config: BalloonConfig,
  submission: CompletedBalloonSubmission,
  onCopyJson: (String) -> Boolean,
  onStartAnother: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var copied by remember(submission) { mutableStateOf(false) }
  val outcome = submission.outcome

  Column(
    modifier =
      modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.CloudDone,
          contentDescription = null,
          modifier = Modifier.size(22.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "Submitted to Collect One",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          )
          Text(
            text =
              buildString {
                val target =
                  if (config.entityName.isNotBlank()) " for ${config.entityName}"
                  else " to Collect One Cloud"
                val actor =
                  if (outcome.collectorEmail.isNotBlank()) " as ${outcome.collectorEmail}" else ""
                append("Saved ${submission.formTitle}$target$actor.")
              },
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }

    OutlinedCard(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Captured responses (${submission.entries.size})",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary,
        )
        if (submission.entries.isEmpty()) {
          Text(
            text = "No optional answers entered.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          submission.entries.forEach { entry ->
            val coloredAnswers =
              remember(entry.question_name, entry.answer_value, submission.formDef) {
                ChoiceImageRegistry.resolveChoiceAnswerColors(
                  questionName = entry.question_name,
                  answerValue = entry.answer_value,
                  formDefs = listOf(submission.formDef),
                )
              }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = entry.question_label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (coloredAnswers.isNotEmpty()) {
                ChoiceColorPillsRow(
                  items = coloredAnswers,
                  compact = true,
                )
              } else {
                Text(
                  text = entry.answer_value,
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = {
          onCopyJson(submission.summaryJson)
          copied = true
        },
        modifier = Modifier.weight(1f),
      ) {
        Icon(
          imageVector = Icons.Outlined.ContentCopy,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(if (copied) "Copied JSON" else "Copy JSON")
      }
      Button(
        onClick = onStartAnother,
        modifier = Modifier.weight(1f),
      ) {
        Icon(
          imageVector = Icons.Outlined.Refresh,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("New entry")
      }
    }
    Spacer(Modifier.height(4.dp))
  }
}
