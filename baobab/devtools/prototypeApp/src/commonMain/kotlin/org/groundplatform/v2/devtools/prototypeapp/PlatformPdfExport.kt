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
package org.groundplatform.v2.devtools.prototypeapp

/** Outcome of handing a generated PDF to the platform. */
internal enum class PdfExportResult {
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
 * Platform hooks for PDFs generated in `commonMain`
 * ([org.groundplatform.v2.devtools.prototypeapp.pdf]). Generation itself is shared; only delivery
 * differs:
 * - **Web**: saves through a download link, previews in a new browser tab, and shares through the
 *   Web Share API (`navigator.share` with files), which opens the OS share sheet on Android, iOS,
 *   and other supporting browsers.
 * - **Android / iOS** (future `shared/mobile` actuals): `FileProvider` + `ACTION_SEND` and
 *   `UIActivityViewController` respectively.
 * - **JVM** (unit tests): writes to the temp directory.
 */
internal expect fun platformCanShareFiles(): Boolean

/** Saves [bytes] as [fileName] (a browser download on the web). */
internal expect fun platformSavePdf(fileName: String, bytes: ByteArray)

/** Opens [bytes] in the platform's PDF viewer (a new tab on the web), saving it if that fails. */
internal expect fun platformPreviewPdf(fileName: String, bytes: ByteArray)

/**
 * Opens the system share sheet for [bytes] named [fileName]; falls back to [platformSavePdf] where
 * file sharing isn't supported. [onResult] may be called asynchronously.
 */
internal expect fun platformSharePdf(
  fileName: String,
  title: String,
  bytes: ByteArray,
  onResult: (PdfExportResult) -> Unit,
)
