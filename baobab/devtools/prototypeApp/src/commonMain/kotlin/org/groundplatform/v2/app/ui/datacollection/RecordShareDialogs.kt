/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.app.ui.datacollection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.groundplatform.v2.app.domain.model.GeospatialEntityItem
import org.groundplatform.v2.app.domain.model.SharedPdfSheetState
import org.groundplatform.v2.app.ui.common.GeoIdPendingSyncIcon
import org.groundplatform.v2.app.ui.state.DataCollectionUiState
import org.groundplatform.v2.app.ui.viewmodel.DataCollectionActions
import org.groundplatform.v2.core.forms.ui.GroundAlertDialogOverlay
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundModalBottomSheetOverlay
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge

/**
 * Modal dialog displaying a scannable QR Code for a survey location (`Icons.Outlined.QrCode`),
 * allowing offline field verification and rapid lookup of the location's `GeoID`. Its PDF action is
 * `Share PDF` on mobile and a direct `Download PDF` on the web dashboard ([isWeb]). Closing the
 * dialog and the PDF action go to [actions].
 */
@Composable
internal fun EntityQrCodeModalDialog(
  actions: DataCollectionActions,
  entity: GeospatialEntityItem,
  isWeb: Boolean = false,
) {
  GroundAlertDialogOverlay(
    onDismissRequest = { actions.closeEntityQrCode() },
    icon = {
      Icon(
        imageVector = Icons.Outlined.QrCode,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = {
      Text(
        text = "${entity.singularTypeLabel} QR Code",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // High-contrast QR matrix surface for scanning
        Surface(
          modifier = Modifier.size(148.dp),
          shape = MaterialTheme.shapes.medium,
          color = Color.White,
          border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        ) {
          Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Outlined.QrCode,
              contentDescription = "${entity.label} QR Matrix",
              tint = Color(0xFF111827),
              modifier = Modifier.size(116.dp),
            )
          }
        }

        Text(
          text = entity.label,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          GroundTonalBadge(
            text = "GeoID: ${entity.geoId}",
            tone = GroundBadgeTone.PRIMARY,
            monospace = true,
          )
          if (entity.isGeoIdPendingSync) {
            GeoIdPendingSyncIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        Text(
          text =
            "Scan with Collect One or any EUDR compliance reader to verify ${entity.singularTypeLabel.lowercase()} geometry & GeoID.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          actions.closeEntityQrCode()
          if (isWeb) actions.downloadEntityPdf(entity.id) else actions.shareEntityPdf(entity.id)
        }
      ) {
        Icon(
          imageVector = if (isWeb) Icons.Outlined.Download else Icons.Outlined.Share,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(if (isWeb) "Download PDF" else "Share PDF")
      }
    },
    dismissButton = { TextButton(onClick = { actions.closeEntityQrCode() }) { Text("Close") } },
  )
}

/**
 * Modal bottom sheet for a PDF report of a map feature or a submission
 * ([DataCollectionUiState.activeSharedPdfSheet]), generated on the device so it works offline.
 * **Share** opens the system share sheet (WhatsApp, Gmail, Drive, Bluetooth, ...) where the
 * platform supports sharing files ([DataCollectionUiState.canSharePdfFiles]); **Download** saves
 * the file; tapping the file card previews it. All actions go to [actions].
 */
@Composable
internal fun SharePdfToAppModalDialog(
  uiState: DataCollectionUiState,
  actions: DataCollectionActions,
  sheet: SharedPdfSheetState,
) {
  val canShare = uiState.canSharePdfFiles
  GroundModalBottomSheetOverlay(onDismissRequest = { actions.closeSharePdfSheet() }) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (canShare) "Share PDF" else "Download PDF",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = { actions.closeSharePdfSheet() }) {
          Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close")
        }
      }

      // The generated file. Tap to preview it.
      OutlinedCard(
        onClick = { actions.previewActivePdf() },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.PictureAsPdf,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(32.dp),
          )
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = sheet.pdfFileName,
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = sheet.subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            if (sheet.pageCount > 0) {
              Text(
                text =
                  "${sheet.pageCount} ${if (sheet.pageCount == 1) "page" else "pages"} • " +
                    sheet.fileSizeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Icon(
            imageVector = Icons.Outlined.Visibility,
            contentDescription = "Preview PDF",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Text(
        text =
          if (canShare) {
            "Created on this device, so you can share it even when you're offline."
          } else {
            "Created on this device. To send it, download it, then attach it in any app."
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (canShare) {
        Button(
          onClick = { actions.shareActivePdf() },
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
        ) {
          Icon(
            imageVector = Icons.Outlined.Share,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Share")
        }
        OutlinedButton(
          onClick = { actions.saveActivePdf() },
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
        ) {
          Icon(
            imageVector = Icons.Outlined.Download,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Download")
        }
      } else {
        Button(
          onClick = { actions.saveActivePdf() },
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
        ) {
          Icon(
            imageVector = Icons.Outlined.Download,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Download")
        }
      }
    }
  }
}

/**
 * Confirmation or error after a PDF action ([DataCollectionUiState.pdfExportMessage]), shown as a
 * snackbar that dismisses itself after a few seconds (through [actions]).
 */
@Composable
internal fun PdfExportMessageSnackbar(
  uiState: DataCollectionUiState,
  actions: DataCollectionActions,
  modifier: Modifier = Modifier,
) {
  val message = uiState.pdfExportMessage ?: return
  LaunchedEffect(message) {
    delay(4_000)
    actions.dismissPdfExportMessage()
  }
  Snackbar(
    modifier = modifier.padding(16.dp),
    action = { TextButton(onClick = { actions.dismissPdfExportMessage() }) { Text("OK") } },
  ) {
    Text(text = message, maxLines = 2, overflow = TextOverflow.Ellipsis)
  }
}
