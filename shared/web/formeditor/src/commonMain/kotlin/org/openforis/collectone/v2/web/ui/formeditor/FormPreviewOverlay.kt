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
package org.openforis.collectone.v2.web.ui.formeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.DeviceFormFactor
import org.openforis.collectone.v2.app.domain.model.DeviceOrientation
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.ui.common.DevicePreviewFrame
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.core.forms.ui.FormWizardStep
import org.openforis.collectone.v2.core.forms.ui.MobileFormRunner
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.backdropClickable
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.core.forms.ui.resolve
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_back_to_editor
import org.openforis.collectone.v2.web.resources.formeditor_close_preview
import org.openforis.collectone.v2.web.resources.formeditor_current_path
import org.openforis.collectone.v2.web.resources.formeditor_flow_preview
import org.openforis.collectone.v2.web.resources.formeditor_language
import org.openforis.collectone.v2.web.resources.formeditor_language_default
import org.openforis.collectone.v2.web.resources.formeditor_preview_mobile_description
import org.openforis.collectone.v2.web.resources.formeditor_preview_title
import org.openforis.collectone.v2.web.resources.formeditor_preview_web_description
import org.openforis.collectone.v2.web.resources.formeditor_restart
import org.openforis.collectone.v2.web.resources.formeditor_submission_passed_validation
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.SectionLabel

// ---------------------------------------------------------------------------------------------
// Overlays: flow preview
// ---------------------------------------------------------------------------------------------

@Composable
private fun ModalScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
          .backdropClickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
          ) {},
      contentAlignment = Alignment.Center,
    ) {
      content()
    }
  }
}

@Composable
internal fun FormPreviewOverlay(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  controller: FormWizardController?,
  isDarkTheme: Boolean,
) {
  var formFactor by remember { mutableStateOf(DeviceFormFactor.MOBILE) }
  var orientation by remember { mutableStateOf(DeviceFormFactor.MOBILE.defaultOrientation) }
  var previewDarkTheme by remember(isDarkTheme) { mutableStateOf(isDarkTheme) }
  ModalScrim(onDismiss = actions::closePreview) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val maxPanelHeight = (maxHeight - 48.dp).coerceAtLeast(320.dp)
      Row(
        modifier =
          Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
      ) {
        if (controller != null && uiState.previewTarget == FormPreviewTarget.WEB) {
          WebPreviewBrowserFrame(uiState = uiState, actions = actions, controller = controller)
        } else if (controller != null) {
          DevicePreviewFrame(
            deviceTitle = stringResource(Res.string.formeditor_preview_title, uiState.form.title),
            isDarkTheme = previewDarkTheme,
            formFactor = formFactor,
            orientation = orientation,
            onSelectFormFactor = {
              formFactor = it
              orientation = it.defaultOrientation
            },
            onRotateDevice = {
              orientation =
                if (orientation == DeviceOrientation.PORTRAIT) DeviceOrientation.LANDSCAPE
                else DeviceOrientation.PORTRAIT
            },
            onToggleDarkTheme = { previewDarkTheme = !previewDarkTheme },
          ) {
            key(controller) {
              MobileFormRunner(
                controller = controller,
                modifier = Modifier.fillMaxSize(),
                onClose = actions::closePreview,
                onSubmitted = { actions.markPreviewSubmitted() },
              )
            }
          }
        }
        PreviewSidePanel(
          uiState = uiState,
          actions = actions,
          controller = controller,
          modifier = Modifier.heightIn(max = maxPanelHeight),
        )
      }
    }
  }
}

@Composable
private fun PreviewSidePanel(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  controller: FormWizardController?,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  ElevatedCard(modifier = modifier.width(340.dp)) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = stringResource(Res.string.formeditor_flow_preview),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = actions::closePreview) {
          Icon(
            Icons.Outlined.Close,
            contentDescription = stringResource(Res.string.formeditor_close_preview),
          )
        }
      }
      Column(
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (uiState.supportedLanguages.size > 1) {
          val languageOptionTexts =
            uiState.supportedLanguages.associateWith { code ->
              val name = languageDisplayName(code)
              if (code == uiState.defaultLanguage) {
                stringResource(Res.string.formeditor_language_default, name)
              } else {
                name
              }
            }
          DropdownSelector(
            label = stringResource(Res.string.formeditor_language),
            selectedText = languageDisplayName(uiState.activeLanguage),
            options = uiState.supportedLanguages,
            optionText = { code -> languageOptionTexts.getValue(code) },
            onSelect = { actions.selectLanguage(it) },
          )
        }
        val error = uiState.previewError
        if (error != null) {
          Text(
            text = error.text(),
            style = MaterialTheme.typography.bodySmall,
            color = colors.error,
          )
        }
        if (controller != null) {
          Text(
            text =
              if (uiState.previewTarget == FormPreviewTarget.WEB) {
                stringResource(Res.string.formeditor_preview_web_description)
              } else {
                stringResource(Res.string.formeditor_preview_mobile_description)
              },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
          )
          if (uiState.previewSubmitted) {
            Surface(
              color = colors.primaryContainer,
              contentColor = colors.onPrimaryContainer,
              shape = MaterialTheme.shapes.small,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                // Filled: indicates the validation-passed uiState.
                Icon(
                  Icons.Filled.CheckCircle,
                  contentDescription = null,
                  tint = colors.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  stringResource(Res.string.formeditor_submission_passed_validation),
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.onPrimaryContainer,
                )
              }
            }
          }
          SectionLabel(stringResource(Res.string.formeditor_current_path))
          controller.steps.forEachIndexed { i, step ->
            val isCurrent = i == controller.currentStepIndex
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(MaterialTheme.shapes.small)
                  .background(if (isCurrent) colors.secondaryContainer else Color.Transparent)
                  .clickable { controller.jumpToStep(i) }
                  .padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = if (step is FormWizardStep.SummaryStep) "✓" else "${i + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) colors.onSecondaryContainer else colors.primary,
                modifier = Modifier.width(24.dp),
              )
              Text(
                text = step.title.resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent) colors.onSecondaryContainer else colors.onSurface,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              if (step is FormWizardStep.QuestionStep) {
                val matchedQ =
                  uiState.form.questions.firstOrNull {
                    step.control.canonicalPath.endsWith("/${it.name}") ||
                      step.control.canonicalPath == it.name
                  }
                val ruleSummary =
                  matchedQ?.let { validationSummaryText(it.type, it.validation) }?.resolve()
                if (!ruleSummary.isNullOrBlank()) {
                  Spacer(Modifier.width(6.dp))
                  TonalBadge(text = ruleSummary, tone = BadgeTone.NEUTRAL)
                }
              }
            }
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = actions::restartPreview) {
          Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(Res.string.formeditor_restart))
        }
        Button(onClick = actions::closePreview) {
          Text(stringResource(Res.string.formeditor_back_to_editor))
        }
      }
    }
  }
}
