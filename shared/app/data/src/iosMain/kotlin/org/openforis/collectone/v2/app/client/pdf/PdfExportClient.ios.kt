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
package org.openforis.collectone.v2.app.client.pdf

import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile

private val log = Logger.withTag("PdfExport")

/** Writes exported PDFs to the iOS temporary directory. */
internal actual fun platformCanShareFiles(): Boolean = false

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun platformSavePdf(fileName: String, bytes: ByteArray) {
  val dir = NSTemporaryDirectory().trimEnd('/')
  val path = "$dir/$fileName"
  val nsData =
    if (bytes.isEmpty()) {
      NSData()
    } else {
      bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
      }
    }
  nsData.writeToFile(path, atomically = true)
  log.d { "Saved PDF to $path" }
}

internal actual fun platformPreviewPdf(fileName: String, bytes: ByteArray) {
  platformSavePdf(fileName, bytes)
}

internal actual fun platformSharePdf(
  fileName: String,
  title: String,
  bytes: ByteArray,
  onResult: (PdfExportResult) -> Unit,
) {
  platformSavePdf(fileName, bytes)
  onResult(PdfExportResult.SAVED)
}
