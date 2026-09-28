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
package org.groundplatform.v2.devtools.prototypeapp

import org.groundplatform.v2.core.forms.media.MediaAttachment
import org.groundplatform.v2.core.forms.media.MediaCapture
import org.groundplatform.v2.core.forms.media.MediaCaptureKind
import org.groundplatform.v2.core.forms.ui.MediaCaptureHandler
import org.groundplatform.v2.core.forms.ui.MediaCaptureRequest
import org.groundplatform.v2.core.forms.ui.MediaCaptureResult
import org.groundplatform.v2.core.forms.ui.MediaCaptureSource
import org.groundplatform.v2.core.forms.ui.SimulatedMediaCaptureHandler

/** Raw file returned by a platform picker / camera bridge. */
internal data class PickedMediaFile(
  val fileName: String,
  val mimeType: String,
  val base64: String,
  val durationMillis: Long?,
  val widthPx: Int?,
  val heightPx: Int?,
)

/** Outcome of [openPlatformMediaPicker]. */
internal sealed interface PlatformPickResult {
  data class Picked(val file: PickedMediaFile) : PlatformPickResult

  data object Cancelled : PlatformPickResult

  data class Failed(val message: String) : PlatformPickResult
}

/** Whether this platform has a real camera / file picker bridge ([openPlatformMediaPicker]). */
internal expect val isPlatformMediaPickerAvailable: Boolean

/** Epoch milliseconds from the platform wall clock (used to name new captures). */
internal expect fun platformEpochMillis(): Long

/**
 * Opens the platform camera, recorder, or file picker.
 *
 * @param accept MIME pattern(s) to accept.
 * @param capture `"environment"` / `"user"` to request live capture with the rear / front camera
 *   (or microphone for audio), or `""` to allow picking existing files.
 * @param maxPixels long-edge photo limit (`orx:max-pixels`), or `0` for none.
 */
internal expect fun openPlatformMediaPicker(
  accept: String,
  capture: String,
  maxPixels: Int,
  onResult: (PlatformPickResult) -> Unit,
)

/**
 * [MediaCaptureHandler] for the prototype app. On the web it uses HTML Media Capture file inputs
 * (camera / recorder on mobile browsers, file picker on desktop); elsewhere it falls back to
 * [SimulatedMediaCaptureHandler].
 */
internal class PrototypeMediaCaptureHandler(
  private val fallback: MediaCaptureHandler = SimulatedMediaCaptureHandler()
) : MediaCaptureHandler {

  override val displayName: String =
    if (isPlatformMediaPickerAvailable) {
      "Uses the device camera or microphone on mobile browsers, or a file picker on desktop."
    } else {
      fallback.displayName
    }

  override fun supports(kind: MediaCaptureKind, source: MediaCaptureSource): Boolean = true

  override fun launch(request: MediaCaptureRequest, onResult: (MediaCaptureResult) -> Unit) {
    if (!isPlatformMediaPickerAvailable) {
      fallback.launch(request, onResult)
      return
    }
    val spec = request.spec
    val capture =
      when {
        request.source == MediaCaptureSource.EXISTING_FILE -> ""
        spec.kind == MediaCaptureKind.FILE -> ""
        spec.preferFrontCamera -> "user"
        else -> "environment"
      }
    openPlatformMediaPicker(
      accept = spec.acceptedMediaType,
      capture = capture,
      maxPixels = spec.maxPixels ?: 0,
    ) { result ->
      onResult(
        when (result) {
          is PlatformPickResult.Cancelled -> MediaCaptureResult.Cancelled
          is PlatformPickResult.Failed -> MediaCaptureResult.Failed(result.message)
          is PlatformPickResult.Picked -> toCaptureResult(request, result.file)
        }
      )
    }
  }

  private fun toCaptureResult(
    request: MediaCaptureRequest,
    file: PickedMediaFile,
  ): MediaCaptureResult {
    val mimeType = file.mimeType.ifBlank { MediaCapture.mimeTypeForFileName(file.fileName) ?: "" }
    if (!request.spec.accepts(mimeType)) {
      return MediaCaptureResult.Failed(
        "${file.fileName} isn't a ${request.spec.kind.noun} (${mimeType.ifBlank { "unknown type" }})."
      )
    }
    val now = platformEpochMillis()
    val attachment =
      MediaAttachment.fromBase64(
        // Name by field + capture time rather than the device's file name, which is often a
        // generic `image.jpg` and may collide across questions.
        fileName = MediaCapture.generateFileName(request.fieldPath, mimeType, now),
        mimeType = mimeType,
        base64 = file.base64,
        capturedAtEpochMillis = now,
        durationMillis = file.durationMillis,
        widthPx = file.widthPx,
        heightPx = file.heightPx,
      ) ?: return MediaCaptureResult.Failed("Couldn't read ${file.fileName}.")
    return MediaCaptureResult.Captured(attachment)
  }
}

/**
 * Converts the flat callback arguments of the browser bridge (`media-capture-bridge.js`) into a
 * [PlatformPickResult]. Unknown numeric metadata arrives as `-1`.
 */
internal fun browserPickResult(
  fileName: String,
  mimeType: String,
  base64: String,
  durationMs: Double,
  widthPx: Double,
  heightPx: Double,
  error: String,
): PlatformPickResult =
  when {
    error.isNotEmpty() -> PlatformPickResult.Failed(error)
    fileName.isEmpty() -> PlatformPickResult.Cancelled
    else ->
      PlatformPickResult.Picked(
        PickedMediaFile(
          fileName = fileName,
          mimeType = mimeType,
          base64 = base64,
          durationMillis = durationMs.takeIf { it >= 0 }?.toLong(),
          widthPx = widthPx.takeIf { it > 0 }?.toInt(),
          heightPx = heightPx.takeIf { it > 0 }?.toInt(),
        )
      )
  }
