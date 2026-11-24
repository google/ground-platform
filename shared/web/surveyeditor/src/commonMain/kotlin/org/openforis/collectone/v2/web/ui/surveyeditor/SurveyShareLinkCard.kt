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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.InviteLinks
import org.openforis.collectone.v2.app.domain.model.editor.surveyUrl
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.appMonospaceFontFamily
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_close
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_badge_public
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_badge_unlisted
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_copied
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_copy_link
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_qr_hint
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_qr_title
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_qr_title_untitled
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_share_link_title
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_show_qr

@Composable
internal fun SurveyShareLinkAndQrCard(
  surveyId: String,
  surveyTitle: String,
  isUnlisted: Boolean,
  modifier: Modifier = Modifier,
) {
  val shareUrl = remember(surveyId) { InviteLinks.surveyUrl(surveyId) }
  val clipboard = LocalClipboardManager.current
  var copied by remember(shareUrl) { mutableStateOf(false) }
  var showQrDialog by remember { mutableStateOf(false) }

  OutlinedCard(
    modifier = modifier.fillMaxWidth(),
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SurveyQrCodeCanvas(
        data = shareUrl,
        modifier =
          Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).clickable { showQrDialog = true },
      )
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(Res.string.surveyeditor_sharing_share_link_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          TonalBadge(
            text =
              if (isUnlisted) stringResource(Res.string.surveyeditor_sharing_badge_unlisted)
              else stringResource(Res.string.surveyeditor_sharing_badge_public),
            tone = if (isUnlisted) BadgeTone.NEUTRAL else BadgeTone.PRIMARY,
          )
        }
        Text(
          text = shareUrl,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = appMonospaceFontFamily()),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = {
            clipboard.setText(AnnotatedString(shareUrl))
            copied = true
          }
        ) {
          Text(
            if (copied) stringResource(Res.string.surveyeditor_sharing_copied)
            else stringResource(Res.string.surveyeditor_sharing_copy_link)
          )
        }
        OutlinedButton(onClick = { showQrDialog = true }) {
          Text(stringResource(Res.string.surveyeditor_sharing_show_qr))
        }
      }
    }
  }

  if (showQrDialog) {
    AlertDialog(
      onDismissRequest = { showQrDialog = false },
      title = {
        Text(
          text =
            if (surveyTitle.isNotBlank()) {
              stringResource(Res.string.surveyeditor_sharing_qr_title, surveyTitle)
            } else {
              stringResource(Res.string.surveyeditor_sharing_qr_title_untitled)
            }
        )
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          SurveyQrCodeCanvas(
            data = shareUrl,
            modifier =
              Modifier.size(216.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
          )
          Text(
            text = stringResource(Res.string.surveyeditor_sharing_qr_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = shareUrl,
              style =
                MaterialTheme.typography.bodySmall.copy(fontFamily = appMonospaceFontFamily()),
              color = MaterialTheme.colorScheme.onSurface,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            clipboard.setText(AnnotatedString(shareUrl))
            copied = true
          }
        ) {
          Text(
            if (copied) stringResource(Res.string.surveyeditor_sharing_copied)
            else stringResource(Res.string.surveyeditor_sharing_copy_link)
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { showQrDialog = false }) {
          Text(stringResource(Res.string.surveyeditor_close))
        }
      },
    )
  }
}

@Composable
private fun SurveyQrCodeCanvas(data: String, modifier: Modifier = Modifier) {
  val matrixSize = 21
  Canvas(modifier = modifier.background(Color.White)) {
    val quietZone = 2
    val totalModules = matrixSize + quietZone * 2
    val cellSize = size.minDimension / totalModules.toFloat()
    val seed = data.fold(5381) { acc, ch -> ((acc shl 5) + acc) xor ch.code }

    fun isFinderModule(r: Int, c: Int, topR: Int, topC: Int): Boolean? {
      val dr = r - topR
      val dc = c - topC
      if (dr !in -1..7 || dc !in -1..7) return null
      if (dr == -1 || dr == 7 || dc == -1 || dc == 7) return false
      if (dr == 0 || dr == 6 || dc == 0 || dc == 6) return true
      if (dr in 2..4 && dc in 2..4) return true
      return false
    }

    for (r in 0 until matrixSize) {
      for (c in 0 until matrixSize) {
        val finder =
          isFinderModule(r, c, 0, 0)
            ?: isFinderModule(r, c, 0, matrixSize - 7)
            ?: isFinderModule(r, c, matrixSize - 7, 0)
        val isDark =
          when {
            finder != null -> finder
            r == 6 -> c % 2 == 0
            c == 6 -> r % 2 == 0
            else -> {
              val charCode =
                if (data.isNotEmpty()) data[(r * matrixSize + c) % data.length].code else 0
              val mix = (seed xor (r * 131 + c * 313) xor (charCode * 17))
              (mix and 1) == 0
            }
          }
        if (isDark) {
          drawRect(
            color = Color(0xFF111827),
            topLeft = Offset((c + quietZone) * cellSize, (r + quietZone) * cellSize),
            size = Size(cellSize, cellSize),
          )
        }
      }
    }
  }
}
