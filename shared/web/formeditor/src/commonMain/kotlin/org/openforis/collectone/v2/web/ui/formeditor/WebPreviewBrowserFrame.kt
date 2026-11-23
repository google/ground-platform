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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.core.forms.ui.CompactFormRunner
import org.openforis.collectone.v2.core.forms.ui.CompactGeometryInput
import org.openforis.collectone.v2.core.forms.ui.CompactMapDrawingHost
import org.openforis.collectone.v2.core.forms.ui.FitScreenIcon
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.core.forms.ui.MapDrawingKind
import org.openforis.collectone.v2.core.forms.ui.addMapDrawingVertex
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_adjust_view
import org.openforis.collectone.v2.web.resources.formeditor_collect_data
import org.openforis.collectone.v2.web.resources.formeditor_preview_title_web
import org.openforis.collectone.v2.web.resources.formeditor_reset_map_view
import org.openforis.collectone.v2.web.resources.formeditor_untitled_form
import org.openforis.collectone.v2.web.ui.common.WebFormPanelChrome
import org.openforis.collectone.v2.web.ui.common.WebFormPanelWidth

/**
 * Interactive web preview shown by **Preview** in [FormPreviewTarget.WEB] mode: a browser-like
 * frame around a minimalist web dashboard wireframe ([WebDashboardWireframeBackdrop]) with the live
 * [CompactFormRunner] in the right-hand panel and an interactive [CompactMapDrawingHost] supporting
 * map geometry vertex placement and "Adjust view" / "Zoom to fit".
 */
@Composable
internal fun WebPreviewBrowserFrame(
  uiState: FormEditorUiState,
  actions: PreviewActions,
  controller: FormWizardController,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  var isZoomedIn by remember { mutableStateOf(false) }
  val drawingSession = remember(controller) { PreviewMapDrawingSession(controller) }
  val mapHost =
    remember(drawingSession, drawingSession.activeDrawingPath, isZoomedIn) {
      drawingSession.asHost(onToggleZoom = { isZoomedIn = !isZoomedIn })
    }

  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    WebPreviewHeaderCard(
      formTitle = uiState.form.title,
      isZoomedIn = isZoomedIn,
      onToggleZoom = { isZoomedIn = !isZoomedIn },
    )
    Surface(
      modifier = Modifier.width(1040.dp).height(720.dp),
      shape = MaterialTheme.shapes.extraLarge,
      color = colors.surface,
      shadowElevation = 8.dp,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        BrowserAddressBarChrome()
        Box(modifier = Modifier.fillMaxSize().background(colors.surfaceContainerHighest)) {
          WebDashboardWireframeBackdrop(
            isZoomedIn = isZoomedIn,
            onMapClick = drawingSession::onMapClick,
            modifier = Modifier.fillMaxSize(),
          )
          WebFormPanelChrome(
            title =
              uiState.form.title.ifBlank { stringResource(Res.string.formeditor_untitled_form) },
            subtitle = stringResource(Res.string.formeditor_collect_data),
            onClose = actions::closePreview,
            modifier =
              Modifier.align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 14.dp, bottom = 14.dp)
                .width(WebFormPanelWidth)
                .heightIn(max = 720.dp - 46.dp - 70.dp),
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
                  onCancel = actions::closePreview,
                  onSubmitted = { actions.markPreviewSubmitted() },
                  geometryInput = CompactGeometryInput.MapDrawing(host = mapHost),
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
 * Ephemeral map-drawing session for the in-browser Web preview: seeds sample vertices when the
 * author clicks "Draw on map" and appends offset vertices on subsequent backdrop clicks.
 */
private class PreviewMapDrawingSession(private val controller: FormWizardController) {
  var activeDrawingPath: String? by mutableStateOf(null)
    private set

  private var activeDrawingKind: MapDrawingKind? by mutableStateOf(null)
  private var vertexCounter: Int = 0

  fun onMapClick(baseLat: Double, baseLng: Double) {
    val path = activeDrawingPath ?: return
    val kind = activeDrawingKind ?: return
    vertexCounter += 1
    val delta = vertexCounter * 0.0004
    val updated = controller.addMapDrawingVertex(path, kind, baseLat + delta, baseLng + delta)
    if (kind == MapDrawingKind.POINT && updated.isNotEmpty()) {
      activeDrawingPath = null
      activeDrawingKind = null
    }
  }

  fun asHost(onToggleZoom: () -> Unit): CompactMapDrawingHost =
    object : CompactMapDrawingHost {
      override val activeDrawingPath: String? = this@PreviewMapDrawingSession.activeDrawingPath

      override fun startDrawing(path: String, kind: MapDrawingKind) {
        this@PreviewMapDrawingSession.activeDrawingPath = path
        activeDrawingKind = kind
        val sampleOffsets =
          when (kind) {
            MapDrawingKind.POINT -> listOf(0.3420 to 32.5810)
            MapDrawingKind.LINE -> listOf(0.3420 to 32.5810, 0.3425 to 32.5818)
            MapDrawingKind.POLYGON ->
              listOf(0.3420 to 32.5810, 0.3426 to 32.5818, 0.3418 to 32.5822)
          }
        sampleOffsets.forEach { (lat, lng) ->
          controller.addMapDrawingVertex(path, kind, lat, lng)
        }
      }

      override fun stopDrawing() {
        this@PreviewMapDrawingSession.activeDrawingPath = null
        activeDrawingKind = null
      }

      override val canFrameGeometry: Boolean = true

      override fun frameGeometry(path: String, kind: MapDrawingKind) {
        onToggleZoom()
      }
    }
}

@Composable
private fun WebPreviewHeaderCard(
  formTitle: String,
  isZoomedIn: Boolean,
  onToggleZoom: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  ElevatedCard(
    modifier = Modifier.width(1040.dp).padding(bottom = 12.dp),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.elevatedCardColors(containerColor = colors.surface),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = stringResource(Res.string.formeditor_preview_title_web, formTitle),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = colors.onSurface,
      )
      TextButton(onClick = onToggleZoom) {
        Icon(
          imageVector = FitScreenIcon,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          if (isZoomedIn) stringResource(Res.string.formeditor_reset_map_view)
          else stringResource(Res.string.formeditor_adjust_view)
        )
      }
    }
  }
}

@Composable
private fun BrowserAddressBarChrome() {
  val colors = MaterialTheme.colorScheme
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
        SkeletonBlock(
          width = 132.dp,
          height = 8.dp,
          shape = CircleShape,
          color = colors.onSurface.copy(alpha = 0.10f),
          modifier = Modifier.padding(horizontal = 12.dp),
        )
      }
    }
  }
}
