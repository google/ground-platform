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

/**
 * Whether this platform can capture photos, video, and audio in-page with a live preview
 * ([openPlatformLiveCapture]); on the web this means `getUserMedia` + `MediaRecorder` exist.
 */
internal expect val isPlatformLiveCaptureAvailable: Boolean

/**
 * Whether a capture request is better served by the platform's native camera / recorder app via
 * HTML Media Capture (`<input type="file" capture>`), i.e. on mobile browsers. On desktop browsers
 * that attribute is ignored and would silently degrade capture to a file picker.
 */
internal expect val platformPrefersFileInputCapture: Boolean

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
 * Captures live media in-page with a preview overlay (camera for [MediaCaptureKind.PHOTO] /
 * [MediaCaptureKind.VIDEO], microphone for [MediaCaptureKind.AUDIO]). Never falls back to a file
 * picker.
 *
 * @param facing `"environment"` (rear camera) or `"user"` (front camera).
 * @param maxPixels long-edge photo limit (`orx:max-pixels`), or `0` for none.
 */
internal expect fun openPlatformLiveCapture(
  kind: MediaCaptureKind,
  facing: String,
  maxPixels: Int,
  onResult: (PlatformPickResult) -> Unit,
)

/** How [PrototypeMediaCaptureHandler] fulfils a [MediaCaptureRequest]; see [chooseCapturePath]. */
internal enum class CapturePath {
  /** `<input type="file" capture=…>`: the native camera / recorder app on mobile browsers. */
  FILE_INPUT_CAPTURE,

  /** In-page `getUserMedia` overlay with a live preview (desktop browsers). */
  LIVE_CAPTURE,

  /** `<input type="file">` without `capture`: pick an existing file. */
  FILE_PICKER,

  /** Nothing on this platform can satisfy the request; report a failure, never a file picker. */
  UNAVAILABLE,
}

/**
 * Decides how to satisfy a media request without ever degrading a capture request into a file
 * picker (which would let collectors upload existing files on "capture only" questions).
 *
 * | source        | kind              | condition                           | path               |
 * |---------------|-------------------|-------------------------------------|--------------------|
 * | EXISTING_FILE | any               | pickerAvailable                     | FILE_PICKER        |
 * | EXISTING_FILE | any               | !pickerAvailable                    | UNAVAILABLE        |
 * | CAPTURE       | FILE              | always                              | UNAVAILABLE        |
 * | CAPTURE       | photo/video/audio | prefersFileInput && pickerAvailable | FILE_INPUT_CAPTURE |
 * | CAPTURE       | photo/video/audio | otherwise, liveAvailable            | LIVE_CAPTURE       |
 * | CAPTURE       | photo/video/audio | otherwise                           | UNAVAILABLE        |
 */
internal fun chooseCapturePath(
  source: MediaCaptureSource,
  kind: MediaCaptureKind,
  prefersFileInput: Boolean,
  liveAvailable: Boolean,
  pickerAvailable: Boolean,
): CapturePath =
  when {
    source == MediaCaptureSource.EXISTING_FILE ->
      if (pickerAvailable) CapturePath.FILE_PICKER else CapturePath.UNAVAILABLE
    kind == MediaCaptureKind.FILE -> CapturePath.UNAVAILABLE
    prefersFileInput && pickerAvailable -> CapturePath.FILE_INPUT_CAPTURE
    liveAvailable -> CapturePath.LIVE_CAPTURE
    else -> CapturePath.UNAVAILABLE
  }

/**
 * [MediaCaptureHandler] for the prototype app. On the web, capture requests open an in-page live
 * camera / microphone overlay on desktop browsers and the native camera / recorder (HTML Media
 * Capture) on mobile browsers; existing-file requests open a file picker. Capture requests are
 * never degraded to a file picker, so "capture only" questions hold on the web. Off the web it
 * falls back to [SimulatedMediaCaptureHandler].
 */
internal class PrototypeMediaCaptureHandler(
  private val fallback: MediaCaptureHandler = SimulatedMediaCaptureHandler(),
  private val pickerAvailable: Boolean = isPlatformMediaPickerAvailable,
  private val liveAvailable: Boolean = isPlatformLiveCaptureAvailable,
  private val prefersFileInput: Boolean = platformPrefersFileInputCapture,
) : MediaCaptureHandler {

  private val usesPlatform: Boolean
    get() = pickerAvailable || liveAvailable

  override val displayName: String =
    when {
      !usesPlatform -> fallback.displayName
      prefersFileInput && pickerAvailable ->
        "Uses the device camera or microphone; uploads open the device's file picker."
      liveAvailable ->
        "Captures live with the browser camera or microphone; uploads open a file picker."
      else -> "This browser can't capture media directly; only file uploads are available."
    }

  override fun supports(kind: MediaCaptureKind, source: MediaCaptureSource): Boolean {
    if (!usesPlatform) return fallback.supports(kind, source)
    return chooseCapturePath(source, kind, prefersFileInput, liveAvailable, pickerAvailable) !=
      CapturePath.UNAVAILABLE
  }

  override fun launch(request: MediaCaptureRequest, onResult: (MediaCaptureResult) -> Unit) {
    if (!usesPlatform) {
      fallback.launch(request, onResult)
      return
    }
    val spec = request.spec
    val facing = if (spec.preferFrontCamera) "user" else "environment"
    val maxPixels = spec.maxPixels ?: 0
    val deliver: (PlatformPickResult) -> Unit = { result ->
      onResult(
        when (result) {
          is PlatformPickResult.Cancelled -> MediaCaptureResult.Cancelled
          is PlatformPickResult.Failed -> MediaCaptureResult.Failed(result.message)
          is PlatformPickResult.Picked -> toCaptureResult(request, result.file)
        }
      )
    }
    when (
      chooseCapturePath(request.source, spec.kind, prefersFileInput, liveAvailable, pickerAvailable)
    ) {
      CapturePath.FILE_PICKER ->
        openPlatformMediaPicker(spec.acceptedMediaType, capture = "", maxPixels, deliver)
      CapturePath.FILE_INPUT_CAPTURE ->
        openPlatformMediaPicker(spec.acceptedMediaType, capture = facing, maxPixels, deliver)
      CapturePath.LIVE_CAPTURE -> openPlatformLiveCapture(spec.kind, facing, maxPixels, deliver)
      CapturePath.UNAVAILABLE ->
        onResult(
          MediaCaptureResult.Failed(
            if (request.source == MediaCaptureSource.CAPTURE) {
              "This browser can't capture a ${spec.kind.noun} directly."
            } else {
              "This browser can't choose a ${spec.kind.noun} from the device."
            }
          )
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
