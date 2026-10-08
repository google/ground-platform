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
package org.groundplatform.v2.devtools.prototypeapp.client.pdf

/** Outcome of handing a generated PDF to the platform. */
enum class PdfExportResult {
  /** The system share sheet completed (the user picked an app). */
  SHARED,

  /** The user dismissed the share sheet. */
  CANCELLED,

  /** The file was saved (downloaded) instead, e.g. because the platform can't share files. */
  SAVED,

  /** The platform reported an error. */
  FAILED,
}

/**
 * Delivers PDFs generated in `commonMain` ([org.groundplatform.v2.devtools.prototypeapp.pdf]) to
 * the platform: the system share sheet, a download, or the platform's viewer.
 *
 * This is **presentation / UI infrastructure** in the sense of
 * `docs/technical/client/architecture.md` ("Presentation / UI Infrastructure (For Foreground UI
 * Utilities)"): it produces no domain data, so it bypasses the data layer and is called straight
 * from `DataCollectionViewModel`, like the share sheet or an external-navigation intent. Generation
 * itself is shared; only delivery differs per platform (see [PlatformPdfExportClient]).
 */
interface PdfExportClient {
  /** True when the platform can hand files to other apps via the system share sheet. */
  val canShareFiles: Boolean

  /** Saves [bytes] as [fileName] (a browser download on the web). */
  fun save(fileName: String, bytes: ByteArray)

  /** Opens [bytes] in the platform's PDF viewer (a new tab on the web), saving it if that fails. */
  fun preview(fileName: String, bytes: ByteArray)

  /**
   * Opens the system share sheet for [bytes] named [fileName]; falls back to [save] where file
   * sharing isn't supported. [onResult] may be called asynchronously.
   */
  fun share(fileName: String, title: String, bytes: ByteArray, onResult: (PdfExportResult) -> Unit)
}

/**
 * The platform's [PdfExportClient]:
 * - **Web**: saves through a download link, previews in a new browser tab, and shares through the
 *   Web Share API (`navigator.share` with files), which opens the OS share sheet on Android, iOS,
 *   and other supporting browsers (`pdf-export-bridge.js`).
 * - **Android / iOS** (future `shared/mobile` actuals): `FileProvider` + `ACTION_SEND` and
 *   `UIActivityViewController` respectively.
 * - **JVM** (unit tests): writes to the temp directory.
 */
class PlatformPdfExportClient : PdfExportClient {
  override val canShareFiles: Boolean
    get() = platformCanShareFiles()

  override fun save(fileName: String, bytes: ByteArray) = platformSavePdf(fileName, bytes)

  override fun preview(fileName: String, bytes: ByteArray) = platformPreviewPdf(fileName, bytes)

  override fun share(
    fileName: String,
    title: String,
    bytes: ByteArray,
    onResult: (PdfExportResult) -> Unit,
  ) = platformSharePdf(fileName, title, bytes, onResult)
}

internal expect fun platformCanShareFiles(): Boolean

internal expect fun platformSavePdf(fileName: String, bytes: ByteArray)

internal expect fun platformPreviewPdf(fileName: String, bytes: ByteArray)

internal expect fun platformSharePdf(
  fileName: String,
  title: String,
  bytes: ByteArray,
  onResult: (PdfExportResult) -> Unit,
)
