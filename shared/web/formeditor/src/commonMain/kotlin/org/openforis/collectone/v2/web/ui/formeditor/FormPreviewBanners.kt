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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.controls.SegmentedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_enable_on_platform
import org.openforis.collectone.v2.web.resources.formeditor_enable_open_in_google_earth
import org.openforis.collectone.v2.web.resources.formeditor_google_earth_incompatible_questions_summary
import org.openforis.collectone.v2.web.resources.formeditor_not_available_on_mobile
import org.openforis.collectone.v2.web.resources.formeditor_not_available_on_mobile_body
import org.openforis.collectone.v2.web.resources.formeditor_not_available_on_web
import org.openforis.collectone.v2.web.resources.formeditor_not_available_on_web_body
import org.openforis.collectone.v2.web.resources.formeditor_preview_target_mobile_sentence
import org.openforis.collectone.v2.web.resources.formeditor_preview_target_web_sentence

/** Icon of each [FormPreviewTarget] in the editor's toggle. */
fun previewTargetIcon(target: FormPreviewTarget): ImageVector =
  when (target) {
    FormPreviewTarget.MOBILE -> Icons.Outlined.Smartphone
    FormPreviewTarget.WEB -> Icons.Outlined.Computer
  }

/** Segmented Mobile / Web toggle for the Form editor toolbar. */
@Composable
internal fun PreviewTargetToggle(
  uiState: FormEditorUiState,
  actions: FormSelectionActions,
  modifier: Modifier = Modifier,
) {
  SingleChoiceSegmentedButtonRow(modifier = modifier.height(36.dp)) {
    FormPreviewTarget.entries.forEachIndexed { index, target ->
      SegmentedButton(
        selected = uiState.previewTarget == target,
        onClick = { actions.selectPreviewTarget(target) },
        shape =
          SegmentedButtonDefaults.itemShape(index = index, count = FormPreviewTarget.entries.size),
        icon = {
          Icon(
            imageVector = previewTargetIcon(target),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        },
        label = {
          Text(stringResource(target.labelRes), style = MaterialTheme.typography.labelMedium)
        },
      )
    }
  }
}

/** Short name of the platform a [FormPreviewTarget] stands for, for use in sentences. */
@Composable
internal fun FormPreviewTarget.sentenceName(): String =
  when (this) {
    FormPreviewTarget.MOBILE -> stringResource(Res.string.formeditor_preview_target_mobile_sentence)
    FormPreviewTarget.WEB -> stringResource(Res.string.formeditor_preview_target_web_sentence)
  }

/**
 * Banner shown above the canvas when the Form is switched off for the platform being previewed.
 * **Enable** turns it on for that platform; the same switches live in Form settings.
 */
@Composable
internal fun PlatformDisabledBanner(
  uiState: FormEditorUiState,
  actions: FormSettingsActions,
  modifier: Modifier = Modifier,
) {
  if (uiState.isEnabledOnPreviewTarget) return
  val target = uiState.previewTarget
  var confirmingEnableWeb by remember { mutableStateOf(false) }
  if (confirmingEnableWeb) {
    EnableWebConfirmDialog(
      onConfirm = {
        confirmingEnableWeb = false
        actions.enableOnPreviewTarget()
      },
      onDismiss = { confirmingEnableWeb = false },
    )
  }
  val colors = MaterialTheme.colorScheme
  CanvasBanner(
    icon = Icons.Outlined.VisibilityOff,
    title =
      when (target) {
        FormPreviewTarget.MOBILE -> stringResource(Res.string.formeditor_not_available_on_mobile)
        FormPreviewTarget.WEB -> stringResource(Res.string.formeditor_not_available_on_web)
      },
    body =
      when (target) {
        FormPreviewTarget.MOBILE ->
          stringResource(Res.string.formeditor_not_available_on_mobile_body)
        FormPreviewTarget.WEB -> stringResource(Res.string.formeditor_not_available_on_web_body)
      },
    actionLabel =
      stringResource(Res.string.formeditor_enable_on_platform).takeIf { !uiState.isReadOnly },
    onAction =
      if (uiState.isReadOnly) {
        null
      } else {
        {
          if (target == FormPreviewTarget.WEB) {
            confirmingEnableWeb = true
          } else {
            actions.enableOnPreviewTarget()
          }
        }
      },
    containerColor = colors.tertiaryContainer,
    contentColor = colors.onTertiaryContainer,
    modifier = modifier,
  )
}

/**
 * Error banner shown above the web canvas when **Open in Google Earth** is enabled on the Form,
 * while one or more questions use field types (location, line, polygon, photo, video, audio, or
 * file) that aren't supported in Google Earth map balloons.
 */
@Composable
internal fun GoogleEarthIncompatibleQuestionsBanner(
  uiState: FormEditorUiState,
  modifier: Modifier = Modifier,
) {
  if (uiState.previewTarget != FormPreviewTarget.WEB || !uiState.isEnabledOnPreviewTarget) return
  val incompatible = uiState.googleEarthIncompatibleQuestions
  if (incompatible.isEmpty()) return
  val colors = MaterialTheme.colorScheme
  CanvasBanner(
    icon = Icons.Outlined.ErrorOutline,
    title = stringResource(Res.string.formeditor_enable_open_in_google_earth),
    body =
      pluralStringResource(
        Res.plurals.formeditor_google_earth_incompatible_questions_summary,
        incompatible.size,
        incompatible.size,
      ),
    containerColor = colors.errorContainer,
    contentColor = colors.onErrorContainer,
    modifier = modifier,
  )
}

/** Full-width notice above a canvas preview: icon, title, body, and optional text action. */
@Composable
private fun CanvasBanner(
  icon: ImageVector,
  title: String,
  body: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
) {
  Surface(modifier = modifier.fillMaxWidth(), color = containerColor) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(18.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = contentColor,
        )
        Text(text = body, style = MaterialTheme.typography.bodySmall, color = contentColor)
      }
      if (actionLabel != null && onAction != null) {
        TextButton(onClick = onAction) { Text(actionLabel, color = contentColor) }
      }
    }
  }
}

/**
 * Wraps a canvas preview area so that, while the Form is off for the previewed platform, it is
 * greyed out and none of its controls respond: pointer events are consumed before they reach the
 * content, mirroring the fact that collectors can't interact with the Form there.
 */
@Composable
internal fun UnavailablePreviewArea(
  uiState: FormEditorUiState,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val enabled = uiState.isEnabledOnPreviewTarget
  Box(modifier = modifier) {
    Box(modifier = Modifier.fillMaxSize().alpha(if (enabled) 1f else 0.4f)) { content() }
    if (!enabled) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
            .pointerInput(Unit) {
              awaitPointerEventScope {
                while (true) {
                  awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
              }
            }
      )
    }
  }
}
