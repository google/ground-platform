/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.app.ui.datacollection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_close
import org.openforis.collectone.v2.app.resources.app_download
import org.openforis.collectone.v2.app.resources.app_download_pdf
import org.openforis.collectone.v2.app.resources.app_entity_qr_code_hint
import org.openforis.collectone.v2.app.resources.app_entity_qr_code_title
import org.openforis.collectone.v2.app.resources.app_entity_qr_matrix_description
import org.openforis.collectone.v2.app.resources.app_geo_id_label
import org.openforis.collectone.v2.app.resources.app_ok
import org.openforis.collectone.v2.app.resources.app_pdf_created_can_share
import org.openforis.collectone.v2.app.resources.app_pdf_created_download_to_send
import org.openforis.collectone.v2.app.resources.app_pdf_page_count
import org.openforis.collectone.v2.app.resources.app_preview_pdf
import org.openforis.collectone.v2.app.resources.app_share
import org.openforis.collectone.v2.app.resources.app_share_pdf
import org.openforis.collectone.v2.app.ui.common.GeoIdPendingSyncIcon
import org.openforis.collectone.v2.app.ui.text.typeText
import org.openforis.collectone.v2.core.forms.ui.AlertDialogOverlay
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.ModalBottomSheetOverlay
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton

/**
 * Base of the link a map feature's QR code opens, followed by its GeoID. Placeholder: Collect One
 * has no public link resolver for GeoIDs yet, so this points at the placeholder web host.
 */
internal const val ENTITY_QR_LINK_BASE_URL = "https://collect-one.example.org/features?geoId="

/**
 * Modal dialog displaying a scannable QR Code for a survey location (`Icons.Outlined.QrCode`),
 * allowing offline field verification and rapid lookup of the location's `GeoID`. Its PDF action is
 * `Share PDF` on mobile and a direct `Download PDF` on the web dashboard ([isWeb]). Closing the
 * dialog and the PDF action go to [actions].
 */
@Composable
fun EntityQrCodeModalDialog(
  actions: DataCollectionActions,
  entity: GeospatialEntityItem,
  isWeb: Boolean = false,
) {
  AlertDialogOverlay(
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
        text = stringResource(Res.string.app_entity_qr_code_title, entity.typeText.resolve()),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // High-contrast ISO/IEC 18004 QR matrix surface for scanning
        val qrDescription =
          stringResource(Res.string.app_entity_qr_matrix_description, entity.label)
        val qrPayload =
          remember(entity.id, entity.geoId) {
            "$ENTITY_QR_LINK_BASE_URL${entity.geoId.ifBlank { entity.id }}"
          }
        val qrMatrix = remember(qrPayload) { encodeQrMatrix(qrPayload) }
        Surface(
          modifier = Modifier.size(164.dp),
          shape = MaterialTheme.shapes.medium,
          color = Color.White,
          border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        ) {
          Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
            Canvas(
              modifier = Modifier.size(140.dp).semantics { contentDescription = qrDescription }
            ) {
              val n = qrMatrix.size
              if (n > 0) {
                val cellSize = size.width / n
                val darkColor = Color(0xFF111827)
                for (r in 0 until n) {
                  for (c in 0 until n) {
                    if (qrMatrix[r][c]) {
                      drawRect(
                        color = darkColor,
                        topLeft = Offset(c * cellSize, r * cellSize),
                        size = Size(cellSize + 0.5f, cellSize + 0.5f),
                      )
                    }
                  }
                }
              }
            }
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
          TonalBadge(
            text = stringResource(Res.string.app_geo_id_label, entity.geoId),
            tone = BadgeTone.PRIMARY,
            monospace = true,
          )
          if (entity.isGeoIdPendingSync) {
            GeoIdPendingSyncIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        Text(
          text =
            stringResource(
              Res.string.app_entity_qr_code_hint,
              entity.typeText.resolve().lowercase(),
            ),
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
        Text(stringResource(if (isWeb) Res.string.app_download_pdf else Res.string.app_share_pdf))
      }
    },
    dismissButton = {
      TextButton(onClick = { actions.closeEntityQrCode() }) {
        Text(stringResource(Res.string.app_close))
      }
    },
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
fun SharePdfToAppModalDialog(
  uiState: DataCollectionUiState,
  actions: DataCollectionActions,
  sheet: SharedPdfSheetState,
) {
  val canShare = uiState.canSharePdfFiles
  ModalBottomSheetOverlay(onDismissRequest = { actions.closeSharePdfSheet() }) {
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
          text =
            stringResource(if (canShare) Res.string.app_share_pdf else Res.string.app_download_pdf),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = { actions.closeSharePdfSheet() }) {
          Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(Res.string.app_close),
          )
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
                  pluralStringResource(
                    Res.plurals.app_pdf_page_count,
                    sheet.pageCount,
                    sheet.pageCount,
                  ) + " • " + sheet.fileSizeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Icon(
            imageVector = Icons.Outlined.Visibility,
            contentDescription = stringResource(Res.string.app_preview_pdf),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Text(
        text =
          if (canShare) {
            stringResource(Res.string.app_pdf_created_can_share)
          } else {
            stringResource(Res.string.app_pdf_created_download_to_send)
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (canShare) {
        val shareTitle = sheet.shareTitle.resolve()
        Button(
          onClick = { actions.shareActivePdf(shareTitle) },
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
        ) {
          Icon(
            imageVector = Icons.Outlined.Share,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(Res.string.app_share))
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
          Text(stringResource(Res.string.app_download))
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
          Text(stringResource(Res.string.app_download))
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
fun PdfExportMessageSnackbar(
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
    action = {
      TextButton(onClick = { actions.dismissPdfExportMessage() }) {
        Text(stringResource(Res.string.app_ok))
      }
    },
  ) {
    Text(text = message.resolve(), maxLines = 2, overflow = TextOverflow.Ellipsis)
  }
}
