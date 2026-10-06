/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.formeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.core.forms.ui.CompactFormRunner
import org.groundplatform.v2.core.forms.ui.CompactGeometryInput
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.devtools.prototypeapp.WebFormPanelChrome
import org.groundplatform.v2.devtools.prototypeapp.WebFormPanelWidth

/**
 * Geometry input of the editor's web canvas and web preview: like the dashboard, geometry questions
 * are answered by drawing on the map, so they render as "draw on the map" request cards. The editor
 * has no map to draw on, hence no host.
 */
private val WebPreviewGeometryInput: CompactGeometryInput = CompactGeometryInput.MapDrawing()

/** Icon of each [FormPreviewTarget] in the editor's toggle. */
internal fun previewTargetIcon(target: FormPreviewTarget): ImageVector =
  when (target) {
    FormPreviewTarget.MOBILE -> Icons.Outlined.Smartphone
    FormPreviewTarget.WEB -> Icons.Outlined.Computer
  }

/** Segmented Mobile / Web toggle for the Form editor toolbar. */
@Composable
internal fun PreviewTargetToggle(state: FormEditorState, modifier: Modifier = Modifier) {
  SingleChoiceSegmentedButtonRow(modifier = modifier.height(36.dp)) {
    FormPreviewTarget.entries.forEachIndexed { index, target ->
      SegmentedButton(
        selected = state.previewTarget == target,
        onClick = { state.selectPreviewTarget(target) },
        shape =
          SegmentedButtonDefaults.itemShape(index = index, count = FormPreviewTarget.entries.size),
        icon = {
          Icon(
            imageVector = previewTargetIcon(target),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        },
        label = { Text(target.label, style = MaterialTheme.typography.labelMedium) },
      )
    }
  }
}

/** Short name of the platform a [FormPreviewTarget] stands for, for use in sentences. */
internal fun FormPreviewTarget.sentenceName(): String =
  when (this) {
    FormPreviewTarget.MOBILE -> "the mobile app"
    FormPreviewTarget.WEB -> "the web dashboard"
  }

/**
 * Banner shown above the canvas when the Form is switched off for the platform being previewed.
 * **Enable** turns it on for that platform; the same switches live in Form settings.
 */
@Composable
internal fun PlatformDisabledBanner(state: FormEditorState, modifier: Modifier = Modifier) {
  if (state.isEnabledOnPreviewTarget) return
  val target = state.previewTarget
  val colors = MaterialTheme.colorScheme
  CanvasBanner(
    icon = Icons.Outlined.VisibilityOff,
    title = "Not available on ${target.label.lowercase()}",
    body =
      "Collectors won't find this form in ${target.sentenceName()}. Enable it to offer it " +
        "there (also in Form settings).",
    actionLabel = "Enable",
    onAction = state::enableOnPreviewTarget,
    containerColor = colors.tertiaryContainer,
    contentColor = colors.onTertiaryContainer,
    modifier = modifier,
  )
}

/**
 * Error banner shown above the web canvas when the Form is on for web but has GPS-only geometry
 * questions, which the web dashboard can't capture. **Fix all** switches them to
 * [GeometryCapture.GPS_OR_MAP]. Only shown while the Form is enabled on web, after
 * [PlatformDisabledBanner]'s check.
 */
@Composable
internal fun WebIncompatibleGeometryBanner(state: FormEditorState, modifier: Modifier = Modifier) {
  if (state.previewTarget != FormPreviewTarget.WEB || !state.isEnabledOnPreviewTarget) return
  val incompatible = state.webIncompatibleGeometryQuestions
  if (incompatible.isEmpty()) return
  val colors = MaterialTheme.colorScheme
  CanvasBanner(
    icon = Icons.Outlined.ErrorOutline,
    title = "Can't be used on web yet",
    body =
      "This form can't be used on web until its GPS-only questions allow drawing on the map. " +
        webIncompatibleSummary(incompatible.size),
    actionLabel = "Fix all",
    onAction = state::makeGeometryQuestionsWebCompatible,
    containerColor = colors.errorContainer,
    contentColor = colors.onErrorContainer,
    modifier = modifier,
  )
}

/** Full-width notice above a canvas preview: icon, title, body, and one text action. */
@Composable
private fun CanvasBanner(
  icon: ImageVector,
  title: String,
  body: String,
  actionLabel: String,
  onAction: () -> Unit,
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
      TextButton(onClick = onAction) { Text(actionLabel, color = contentColor) }
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
  state: FormEditorState,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val enabled = state.isEnabledOnPreviewTarget
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

/**
 * The editor canvas in [FormPreviewTarget.WEB] mode: the Form as collectors see it in the web
 * dashboard's right-hand panel, i.e. every question stacked as a read-only collapsible card (
 * [CompactFormRunner]). Clicking a card selects that question in the properties panel; clicking the
 * empty canvas selects the Form itself. The cards are rebuilt from the generated XForms on every
 * edit, so labels, hints, choices, and display logic are always current.
 */
@Composable
internal fun WebLayoutCanvasPanel(state: FormEditorState, modifier: Modifier = Modifier) {
  val form = state.form
  val xml = state.previewXml
  val parsed = remember(xml) { state.parsePreviewController() }
  val controller = parsed.getOrNull()
  val error = parsed.exceptionOrNull()?.let { it.message ?: it.toString() }
  val colors = MaterialTheme.colorScheme

  ElevatedCard(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.elevatedCardColors(containerColor = colors.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${form.questions.size} questions • Web layout",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          softWrap = false,
        )
        GroundTonalBadge(text = "Read-only preview", tone = GroundBadgeTone.NEUTRAL)
        Spacer(Modifier.weight(1f))
        Text(
          text =
            "Click a question to edit it. Collectors fill these in from the dashboard's side panel.",
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
        )
      }
      HorizontalDivider(color = colors.outlineVariant)
      PlatformDisabledBanner(state)
      WebIncompatibleGeometryBanner(state)
      UnavailablePreviewArea(state, Modifier.weight(1f).fillMaxWidth()) {
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(colors.surfaceContainer)
              .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = state::selectForm,
              )
              .verticalScroll(rememberScrollState())
              .padding(24.dp),
          contentAlignment = Alignment.TopCenter,
        ) {
          WebFormPanelChrome(
            title = form.title.ifBlank { "Untitled form" },
            subtitle = "Collect data",
            modifier = Modifier.width(WebFormPanelWidth),
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
              if (controller != null) {
                key(controller) {
                  CompactFormRunner(
                    controller = controller,
                    readOnly = true,
                    selectedPath = state.selectedKey?.let(state::pathOf),
                    onSelectQuestion = { path -> state.keyForPath(path)?.let(state::select) },
                    geometryInput = WebPreviewGeometryInput,
                  )
                }
              } else {
                Text(
                  text = "The generated XForms couldn't be loaded:\n${error.orEmpty()}",
                  style = MaterialTheme.typography.bodySmall,
                  color = colors.error,
                )
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Interactive web preview shown by **Preview** in [FormPreviewTarget.WEB] mode: a browser-like
 * frame around a simplified dashboard (muted map area) with the live [CompactFormRunner] in the
 * right-hand panel, where collectors fill the Form in.
 */
@Composable
internal fun WebPreviewBrowserFrame(
  state: FormEditorState,
  controller: FormWizardController,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = "Preview • ${state.form.title} • Web",
      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
      color = colors.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 8.dp),
    )
    Surface(
      modifier = Modifier.width(1040.dp).height(720.dp),
      shape = MaterialTheme.shapes.extraLarge,
      color = colors.surface,
      shadowElevation = 8.dp,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Browser chrome: traffic lights and an address pill.
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(colors.surfaceContainerHigh)
              .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          repeat(3) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(colors.outlineVariant))
          }
          Spacer(Modifier.width(8.dp))
          Surface(
            shape = CircleShape,
            color = colors.surfaceContainerLowest,
            modifier = Modifier.weight(1f).height(26.dp),
          ) {
            Box(contentAlignment = Alignment.CenterStart) {
              Text(
                text = "ground.app/#dashboard",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
              )
            }
          }
        }
        // Simplified dashboard: a muted map behind the right-hand data collection panel.
        Box(modifier = Modifier.fillMaxSize().background(colors.surfaceContainerHighest)) {
          Text(
            text = "Survey map",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.Center),
          )
          WebFormPanelChrome(
            title = state.form.title.ifBlank { "Untitled form" },
            subtitle = "Collect data",
            onClose = state::closePreview,
            modifier =
              Modifier.align(Alignment.TopEnd)
                .padding(14.dp)
                .width(WebFormPanelWidth)
                .heightIn(max = 720.dp - 46.dp - 28.dp),
          ) {
            Column(
              modifier =
                Modifier.weight(1f, fill = false)
                  .fillMaxWidth()
                  .verticalScroll(rememberScrollState())
                  .padding(16.dp)
            ) {
              key(controller) {
                CompactFormRunner(
                  controller = controller,
                  onCancel = state::closePreview,
                  onSubmitted = { state.markPreviewSubmitted() },
                  geometryInput = WebPreviewGeometryInput,
                )
              }
            }
          }
        }
      }
    }
  }
}
