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
package org.openforis.collectone.v2.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem

/** Test tag prefix for a survey card's visibility icon (`$SURVEY_VISIBILITY_ICON_TAG-<id>`). */
const val SURVEY_VISIBILITY_ICON_TAG = "survey-visibility-icon"

/** Translucent white scrim drawn over a closed survey's thumbnail so it reads slightly lighter. */
private val ClosedSurveyThumbnailScrimColor = Color.White.copy(alpha = 0.24f)

/**
 * Compact icon representing a survey's visibility (`Public data` or `Private submissions`), with
 * the localized visibility text shown in a tooltip on hover (web) or long-press (mobile). Renders
 * nothing for surveys with neither visibility badge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyVisibilityIcon(
  survey: SurveyPreviewItem,
  publicDataLabel: String,
  privateSubmissionsLabel: String,
  modifier: Modifier = Modifier,
  tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  val (icon, label) =
    when {
      survey.isPublicData -> Icons.Outlined.Public to publicDataLabel
      survey.isPrivateSubmissions -> Icons.Outlined.Lock to privateSubmissionsLabel
      else -> return
    }
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text(label) } },
    state = rememberTooltipState(),
    modifier = modifier,
  ) {
    Box(modifier = Modifier.testTag("$SURVEY_VISIBILITY_ICON_TAG-${survey.id}")) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

/** Lightening scrim overlay placed over a closed survey's map thumbnail. */
@Composable
fun ClosedSurveyThumbnailScrim(modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize().background(ClosedSurveyThumbnailScrimColor))
}

/**
 * Renders this composable and all of its descendants in grayscale (black and white) when [enabled]
 * is `true`, leaving rendering untouched otherwise.
 */
fun Modifier.grayscale(enabled: Boolean): Modifier =
  if (!enabled) {
    this
  } else {
    drawWithCache {
      val paint =
        Paint().apply {
          colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        }
      onDrawWithContent {
        val bounds = Rect(Offset.Zero, size)
        drawContext.canvas.saveLayer(bounds, paint)
        try {
          drawContent()
        } finally {
          drawContext.canvas.restore()
        }
      }
    }
  }
