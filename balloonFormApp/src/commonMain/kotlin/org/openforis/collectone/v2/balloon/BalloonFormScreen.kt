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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import openforis.collectone.v1.data.SubmissionFieldEntryDef
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.ui.ChoiceColorPillsRow
import org.openforis.collectone.v2.core.forms.ui.ChoiceImageRegistry
import org.openforis.collectone.v2.core.forms.ui.CompactFormRunner
import org.openforis.collectone.v2.core.forms.ui.FormVariant
import org.openforis.collectone.v2.core.forms.ui.GoogleEarthTheme
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.isEntityReferenceStep

/**
 * Convenience overload that binds a [BalloonFormViewModel] for callers passing callback lambdas
 * directly (such as Compose UI tests). Production hosts should construct [BalloonFormViewModel]
 * directly and call the stateless [BalloonFormScreen] overload.
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
  @Suppress("UNUSED_VARIABLE") val unusedEmbedded = embeddedInBalloon
  val client = remember {
    CallbackBalloonClient(
      onSubmitRecord = onSubmitRecord,
      onClaimAuthSession = onClaimAuthSession,
      onFetchEntityState = onFetchEntityState,
      onSaveDraft = onSaveDraft,
    )
  }
  client.onSubmitRecord = onSubmitRecord
  client.onClaimAuthSession = onClaimAuthSession
  client.onFetchEntityState = onFetchEntityState
  client.onSaveDraft = onSaveDraft

  val viewModel = remember {
    BalloonFormViewModel(initialConfig = config, client = client, authPageUrl = authPageUrl)
  }
  viewModel.authPageUrl = authPageUrl
  LaunchedEffect(config) { viewModel.updateConfig(config) }
  DisposableEffect(viewModel) { onDispose { viewModel.dispose() } }

  val uiState by viewModel.uiState.collectAsState()
  BalloonFormScreen(
    uiState = uiState,
    actions = viewModel,
    effects = viewModel.effects,
    onOpenExternalUrl = onOpenExternalUrl,
    onCopyText = onCopyText,
    darkTheme = darkTheme,
  )
}

/**
 * Passive, declarative Compose Multiplatform UI rendered inside a KML placemark balloon `<iframe>`.
 *
 * Observes immutable [uiState] from [BalloonFormViewModel] and dispatches user intents through
 * [actions], hosting Collect One's shared [CompactFormRunner] inside [GoogleEarthTheme].
 */
@Composable
fun BalloonFormScreen(
  uiState: BalloonFormUiState,
  actions: BalloonFormActions,
  effects: Flow<BalloonFormEffect> = emptyFlow(),
  onOpenExternalUrl: (String) -> Unit = {},
  onCopyText: (String) -> Boolean = { false },
  darkTheme: Boolean = false,
) {
  val latestOpenExternalUrl by rememberUpdatedState(onOpenExternalUrl)
  LaunchedEffect(effects) {
    effects.collect { effect ->
      when (effect) {
        is BalloonFormEffect.OpenExternalUrl -> latestOpenExternalUrl(effect.url)
      }
    }
  }

  GoogleEarthTheme(darkTheme = darkTheme) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        val activeForm = uiState.activeForm
        if (uiState.showFormHeader && activeForm != null) {
          BalloonSelectedFormHeader(
            activeForm = activeForm,
            onBackToChooser = { actions.backToFormChooser() },
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (uiState.isBusy) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          if (uiState.isSubmitting) {
            Text(
              text = "Syncing submission to Collect One…",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
          }
        }

        val completedSnapshot = uiState.completedSubmission
        val pendingAuthSnapshot = uiState.pendingAuth
        val failedSnapshot = uiState.failedSubmission
        when {
          uiState.forms.isEmpty() -> {
            BalloonNoFormsAvailableCard(
              config = uiState.config,
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
          }
          activeForm == null -> {
            BalloonFormChooser(
              config = uiState.config,
              forms = uiState.forms,
              onSelectForm = { index -> actions.selectForm(index) },
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
          }
          completedSnapshot != null -> {
            BalloonSubmissionReceipt(
              config = uiState.config,
              submission = completedSnapshot,
              onCopyJson = onCopyText,
              onStartAnother = { actions.startAnotherEntry() },
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
          }
          pendingAuthSnapshot != null -> {
            BalloonAuthHandoffCard(
              config = uiState.config,
              pending = pendingAuthSnapshot,
              onOpenSignIn = { actions.openSignIn() },
              onCancel = { actions.cancelPendingAuth() },
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
          }
          failedSnapshot != null -> {
            BalloonUploadErrorCard(
              config = uiState.config,
              failed = failedSnapshot,
              isSubmitting = uiState.isSubmitting,
              onRetry = { actions.retrySubmission() },
              onSwitchAccount = { actions.switchAccount() },
              onBackToForm = { actions.backToFormFromError() },
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
          }
          else -> {
            val activeController = uiState.formController ?: return@Column
            Column(
              modifier =
                Modifier.weight(1f)
                  .fillMaxWidth()
                  .verticalScroll(rememberScrollState())
                  .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
              if (uiState.showDraftRestoredBanner && uiState.restoredDraftFormId == activeForm.id) {
                BalloonDraftRestoredBanner(onDiscardDraft = { actions.discardRestoredDraft() })
                Spacer(Modifier.height(8.dp))
              }
              key(
                uiState.config.surveyId,
                uiState.config.entityId,
                activeForm.id,
                uiState.sessionGeneration,
              ) {
                CompactFormRunner(
                  controller = activeController,
                  showHeader = false,
                  variant = FormVariant.GOOGLE_EARTH,
                  questionFilter = { step -> !isEntityReferenceStep(step) },
                  onSubmitted = { result -> actions.submitForm(result) },
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
