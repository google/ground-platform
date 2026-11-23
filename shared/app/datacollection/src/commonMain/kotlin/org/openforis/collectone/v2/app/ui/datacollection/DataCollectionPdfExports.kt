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
package org.openforis.collectone.v2.app.ui.datacollection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.ImpactCoverage
import org.openforis.collectone.v2.app.domain.model.ImpactEventType
import org.openforis.collectone.v2.app.domain.model.updatedAtLabel
import org.openforis.collectone.v2.app.domain.usecase.RecordImpactEventUseCase
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_pdf_saved
import org.openforis.collectone.v2.app.resources.app_pdf_saved_with_summary
import org.openforis.collectone.v2.app.resources.app_pdf_share_failed
import org.openforis.collectone.v2.app.resources.app_pdf_share_title_map_feature
import org.openforis.collectone.v2.app.resources.app_pdf_share_title_submission
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.typeText

/**
 * UI-layer metadata and bytes of a generated PDF report, decoupled from `app.client.pdf` so only
 * [DataCollectionViewModel] touches the client PDF boundary (`LayerDependencyArchitectureTest`).
 */
internal class ExportedPdf(
  val fileName: String,
  val bytes: ByteArray,
  val pageCount: Int,
  val sizeLabel: String,
  val summaryText: UiText,
)

/** Outcome of handing an [ExportedPdf] to the platform share sheet. */
internal enum class PdfShareOutcome {
  SHARED,
  SAVED,
  CANCELLED,
  FAILED,
}

/**
 * Callback boundary through which [DataCollectionPdfExports] asks [DataCollectionViewModel] to
 * render and deliver PDFs via `app.client.pdf` without importing client packages directly.
 */
internal interface PdfReportPort {
  suspend fun generateEntityReport(entityId: String, ignoreOffline: Boolean): ExportedPdf?

  suspend fun generateSubmissionReport(submissionId: String): ExportedPdf?

  fun save(fileName: String, bytes: ByteArray)

  fun preview(fileName: String, bytes: ByteArray)

  fun share(fileName: String, title: String, bytes: ByteArray, onOutcome: (PdfShareOutcome) -> Unit)
}

/**
 * Resolves [entityId] among [entities] for a PDF report, filtering out other collectors'
 * submissions when [includeOthersSubmissions] is `false` (offline on mobile) so stale or
 * peer-restricted records on the device are never printed from local data.
 */
internal fun effectiveEntityForPdf(
  entities: List<GeospatialEntityItem>,
  entityId: String,
  collectorEmail: String,
  includeOthersSubmissions: Boolean,
): GeospatialEntityItem? {
  val entity = entities.firstOrNull { it.id == entityId } ?: return null
  if (includeOthersSubmissions) return entity
  return entity.copy(
    submissions =
      entity.submissions.filter { sub ->
        collectorEmail.isNotBlank() && sub.collectorEmail.equals(collectorEmail, ignoreCase = true)
      }
  )
}

/** The survey and map feature coverage a generated PDF report is a receipt for. */
internal data class PdfReceipt(val surveyId: String, val coverage: ImpactCoverage)

/** The PDF export part of data collection's screen state. */
internal data class PdfExportState(
  val activeSharedPdfSheet: SharedPdfSheetState? = null,
  /** The PDF generated for [activeSharedPdfSheet] (`null` when the sheet is closed). */
  val activePdf: ExportedPdf? = null,
  /** What [activePdf] is a receipt for, to record when it's shared or saved. */
  val activeReceipt: PdfReceipt? = null,
  val pdfExportMessage: UiText? = null,
)

/**
 * The record PDF export coordinator of [DataCollectionViewModel]: serializes report generation,
 * hands generated files to [port] to share, save, or preview, holds the Share PDF sheet and export
 * message ([state]), and records each report as a receipt through [recordImpactEvent].
 *
 * It reads the ViewModel's current data and screen state through [data] and [ui] at the moment an
 * export is asked for, and runs in the ViewModel's [scope], so exports stop with the ViewModel.
 * Calls are expected on the ViewModel's (main) dispatcher; only report generation suspends.
 */
internal class DataCollectionPdfExports(
  private val scope: CoroutineScope,
  private val port: PdfReportPort,
  /** Records receipt activity (PDF reports generated and shared); `null` records nothing. */
  private val recordImpactEvent: RecordImpactEventUseCase?,
  private val data: () -> DataCollectionData,
  private val ui: () -> DataCollectionUiState,
) {
  private val _state = MutableStateFlow(PdfExportState())
  val state: StateFlow<PdfExportState> = _state.asStateFlow()

  /** Serializes exports; see [exportPdf]. */
  private val pdfExports = Mutex()

  /** Closes the Share PDF sheet and drops the export message (the workbench's Reset). */
  fun reset() {
    _state.value = PdfExportState()
  }

  /**
   * Runs [export] once the PDF exports asked for before it are done. Reports load their text
   * asynchronously (string resources), so without the lock a later, shorter report could finish
   * first; this way exports finish in the order they were asked for.
   */
  private fun exportPdf(export: suspend () -> Unit) {
    scope.launch { pdfExports.withLock { export() } }
  }

  fun shareEntityPdf(entityId: String) {
    val entity = ui().entities.firstOrNull { it.id == entityId } ?: return
    exportPdf {
      val pdf = port.generateEntityReport(entityId, ignoreOffline = false) ?: return@exportPdf
      val receipt = recordReceiptGenerated(entity)
      openPdfSheet(
        pdf,
        receipt,
        SharedPdfSheetState(
          targetId = entity.id,
          shareTitle =
            UiText.Resource(
              Res.string.app_pdf_share_title_map_feature,
              listOf(entity.typeText),
            ),
          subtitle = "${entity.label} • GeoID ${entity.geoId}",
          pdfFileName = pdf.fileName,
          pageCount = pdf.pageCount,
          fileSizeLabel = pdf.sizeLabel,
        ),
      )
    }
  }

  fun shareSubmissionPdf(submissionId: String) {
    val sub = ui().allSubmissions.firstOrNull { it.id == submissionId } ?: return
    exportPdf {
      val pdf = port.generateSubmissionReport(submissionId) ?: return@exportPdf
      val receipt = recordReceiptGenerated(ui().entities.firstOrNull { it.id == sub.entityId })
      openPdfSheet(
        pdf,
        receipt,
        SharedPdfSheetState(
          targetId = sub.id,
          shareTitle =
            UiText.Resource(Res.string.app_pdf_share_title_submission, listOf(sub.formTitle)),
          subtitle = "${sub.collectorName} • ${sub.updatedAtLabel}",
          pdfFileName = pdf.fileName,
          pageCount = pdf.pageCount,
          fileSizeLabel = pdf.sizeLabel,
        ),
      )
    }
  }

  fun downloadEntityPdf(entityId: String, ignoreOffline: Boolean) {
    if (ui().entities.none { it.id == entityId }) return
    exportPdf {
      val pdf =
        port.generateEntityReport(entityId, ignoreOffline = ignoreOffline) ?: return@exportPdf
      savePdf(
        pdf = pdf,
        receipt =
          recordReceiptGenerated(
            entity = ui().entities.firstOrNull { it.id == entityId },
            ignoreOffline = ignoreOffline,
          ),
        ignoreOffline = ignoreOffline,
      )
    }
  }

  fun downloadSubmissionPdf(submissionId: String, ignoreOffline: Boolean) {
    val entityId = (ui().allSubmissions.firstOrNull { it.id == submissionId } ?: return).entityId
    exportPdf {
      val pdf = port.generateSubmissionReport(submissionId) ?: return@exportPdf
      savePdf(
        pdf = pdf,
        receipt =
          recordReceiptGenerated(
            entity = ui().entities.firstOrNull { it.id == entityId },
            ignoreOffline = ignoreOffline,
          ),
        ignoreOffline = ignoreOffline,
      )
    }
  }

  /**
   * Shares the PDF in the Share PDF sheet. Platforms without a share target may save the file
   * instead ([PdfShareOutcome.SAVED]); both count as the receipt being shared and close the sheet.
   * A cancelled share keeps the sheet open so the collector can pick another target.
   */
  fun shareActivePdf(title: String) {
    val pdf = _state.value.activePdf ?: return
    val receipt = _state.value.activeReceipt
    port.share(pdf.fileName, title, pdf.bytes) { outcome ->
      when (outcome) {
        PdfShareOutcome.SHARED -> {
          recordReceiptShared(receipt)
          closeSharePdfSheet()
        }
        PdfShareOutcome.SAVED -> {
          recordReceiptShared(receipt)
          closeSharePdfSheet()
          _state.update {
            it.copy(
              pdfExportMessage = UiText.Resource(Res.string.app_pdf_saved, listOf(pdf.fileName))
            )
          }
        }
        PdfShareOutcome.CANCELLED -> Unit
        PdfShareOutcome.FAILED ->
          _state.update {
            it.copy(
              pdfExportMessage =
                UiText.Resource(Res.string.app_pdf_share_failed, listOf(pdf.fileName))
            )
          }
      }
    }
  }

  fun saveActivePdf() {
    val pdf = _state.value.activePdf ?: return
    savePdf(pdf, _state.value.activeReceipt)
    closeSharePdfSheet()
  }

  fun previewActivePdf() {
    val pdf = _state.value.activePdf ?: return
    port.preview(pdf.fileName, pdf.bytes)
  }

  fun closeSharePdfSheet() {
    _state.update { it.copy(activeSharedPdfSheet = null, activePdf = null, activeReceipt = null) }
  }

  fun dismissPdfExportMessage() {
    _state.update { it.copy(pdfExportMessage = null) }
  }

  private fun openPdfSheet(pdf: ExportedPdf, receipt: PdfReceipt, sheet: SharedPdfSheetState) {
    _state.update {
      it.copy(activePdf = pdf, activeReceipt = receipt, activeSharedPdfSheet = sheet)
    }
  }

  /** Saves [pdf] to the device, recording it as a shared [receipt]. */
  private fun savePdf(pdf: ExportedPdf, receipt: PdfReceipt?, ignoreOffline: Boolean = false) {
    port.save(pdf.fileName, pdf.bytes)
    recordReceiptShared(receipt, ignoreOffline = ignoreOffline)
    _state.update {
      it.copy(
        pdfExportMessage =
          UiText.Resource(
            Res.string.app_pdf_saved_with_summary,
            listOf(pdf.fileName, pdf.summaryText),
          )
      )
    }
  }

  // --- Receipt activity ---

  /**
   * Records that a PDF report was generated for [entity] (a map feature's report, or the feature of
   * a submission's report; `null` for a standalone submission) in the active survey, and returns
   * the receipt to record again when it's shared. Only counts and area are recorded, no geometry or
   * personal details. Recorded offline, it uploads on the next sync.
   */
  private fun recordReceiptGenerated(
    entity: GeospatialEntityItem?,
    ignoreOffline: Boolean = false,
  ): PdfReceipt {
    val receipt = PdfReceipt(data().activeSurveyId, ImpactCoverage.of(listOfNotNull(entity)))
    recordReceiptEvent(ImpactEventType.RECEIPT_GENERATED, receipt, ignoreOffline = ignoreOffline)
    return receipt
  }

  /** Records that the PDF report [receipt] was shared or saved to the device. */
  private fun recordReceiptShared(receipt: PdfReceipt?, ignoreOffline: Boolean = false) {
    receipt?.let {
      recordReceiptEvent(ImpactEventType.RECEIPT_SHARED, it, ignoreOffline = ignoreOffline)
    }
  }

  /** Records [type] for [receipt], skipping receipts outside any survey (no active survey yet). */
  private fun recordReceiptEvent(
    type: ImpactEventType,
    receipt: PdfReceipt,
    ignoreOffline: Boolean = false,
  ) {
    val record = recordImpactEvent ?: return
    if (receipt.surveyId.isBlank()) return
    scope.launch { record(type, receipt.surveyId, receipt.coverage, ignoreOffline = ignoreOffline) }
  }
}
