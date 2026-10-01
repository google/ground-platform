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

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// PDF bytes cross the Wasm/JS boundary as base64 strings; `pdf-export-bridge.js` turns them back
// into a `Blob`.

@JsFun("() => typeof window !== 'undefined' && !!window.GroundPdfExportBridge")
private external fun jsHasPdfBridge(): Boolean

@JsFun("() => window.GroundPdfExportBridge.canShareFiles()")
private external fun jsCanShareFiles(): Boolean

@JsFun("(fileName, base64) => window.GroundPdfExportBridge.save(fileName, base64)")
private external fun jsSavePdf(fileName: String, base64: String)

@JsFun("(fileName, base64) => window.GroundPdfExportBridge.preview(fileName, base64)")
private external fun jsPreviewPdf(fileName: String, base64: String)

@JsFun(
  "(fileName, title, base64, callback) => " +
    "window.GroundPdfExportBridge.share(fileName, title, base64, callback)"
)
private external fun jsSharePdf(
  fileName: String,
  title: String,
  base64: String,
  callback: (String) -> Unit,
)

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.toBase64(): String = Base64.encode(this)

internal actual fun platformCanShareFiles(): Boolean = jsHasPdfBridge() && jsCanShareFiles()

internal actual fun platformSavePdf(fileName: String, bytes: ByteArray) {
  if (jsHasPdfBridge()) {
    jsSavePdf(fileName, bytes.toBase64())
  } else {
    println("PDF export bridge (pdf-export-bridge.js) not loaded; cannot save $fileName")
  }
}

internal actual fun platformPreviewPdf(fileName: String, bytes: ByteArray) {
  if (jsHasPdfBridge()) jsPreviewPdf(fileName, bytes.toBase64())
}

internal actual fun platformSharePdf(
  fileName: String,
  title: String,
  bytes: ByteArray,
  onResult: (PdfExportResult) -> Unit,
) {
  if (!jsHasPdfBridge()) {
    onResult(PdfExportResult.FAILED)
    return
  }
  jsSharePdf(fileName, title, bytes.toBase64()) { result ->
    onResult(
      when (result) {
        "shared" -> PdfExportResult.SHARED
        "cancelled" -> PdfExportResult.CANCELLED
        "saved" -> PdfExportResult.SAVED
        else -> PdfExportResult.FAILED
      }
    )
  }
}
