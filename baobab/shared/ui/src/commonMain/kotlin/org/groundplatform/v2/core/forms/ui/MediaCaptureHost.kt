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
package org.groundplatform.v2.core.forms.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import okio.ByteString.Companion.encodeUtf8
import org.groundplatform.v2.core.forms.media.MediaAttachment
import org.groundplatform.v2.core.forms.media.MediaCapture
import org.groundplatform.v2.core.forms.media.MediaCaptureKind
import org.groundplatform.v2.core.forms.media.MediaCaptureSpec

/** Where a media question's answer should come from. */
enum class MediaCaptureSource {
  /** Live capture with the device camera (photo / video) or microphone (audio). */
  CAPTURE,

  /** Pick an existing file from the device. Not offered when the question has `new` appearance. */
  EXISTING_FILE,
}

/** A request from a media question widget to the host platform to obtain an attachment. */
data class MediaCaptureRequest(
  /** Canonical path of the media question being answered. */
  val fieldPath: String,
  /** Resolved capture configuration (kind, accepted types, max pixels, camera facing). */
  val spec: MediaCaptureSpec,
  /** Whether to capture live media or pick an existing file. */
  val source: MediaCaptureSource,
  /**
   * Suggested attachment file name. Hosts may use their own (e.g. with a real wall-clock timestamp)
   * as long as it is unique within the record.
   */
  val suggestedFileName: String,
)

/** Outcome of a [MediaCaptureRequest]. */
sealed interface MediaCaptureResult {
  /** The user captured or selected media. */
  data class Captured(val attachment: MediaAttachment) : MediaCaptureResult

  /** The user dismissed the camera, recorder, or picker. */
  data object Cancelled : MediaCaptureResult

  /** Capture failed (permission denied, hardware unavailable, unreadable file, ...). */
  data class Failed(val message: String) : MediaCaptureResult
}

/**
 * Host-platform bridge that obtains photos, videos, and audio recordings for media questions.
 *
 * Shared UI stays free of platform APIs; each host app (Android, iOS, web, desktop) supplies an
 * implementation via [LocalMediaCaptureHandler].
 */
interface MediaCaptureHandler {
  /** Short description shown under capture buttons (e.g. `"Browser camera"`). */
  val displayName: String

  /** Whether [source] is available for [kind] on this platform. */
  fun supports(kind: MediaCaptureKind, source: MediaCaptureSource): Boolean = true

  /**
   * Launches capture for [request]. [onResult] must be invoked exactly once, on the main thread,
   * when capture completes, is cancelled, or fails.
   */
  fun launch(request: MediaCaptureRequest, onResult: (MediaCaptureResult) -> Unit)
}

/**
 * Deterministic stand-in used when the host provides no real camera or microphone (unit tests, JVM
 * previews). Produces a small placeholder payload with plausible metadata so the full answer →
 * review → submit flow can be exercised end to end.
 */
class SimulatedMediaCaptureHandler : MediaCaptureHandler {
  override val displayName: String = "Simulated capture (no camera available)"

  override fun launch(request: MediaCaptureRequest, onResult: (MediaCaptureResult) -> Unit) {
    val spec = request.spec
    val mimeType = simulatedMimeType(spec)
    val fileName =
      request.suggestedFileName.substringBeforeLast('.') +
        "." +
        MediaCapture.extensionForMimeType(mimeType)
    val longEdge = spec.maxPixels?.coerceAtMost(1600) ?: 1600
    val attachment =
      MediaAttachment(
        fileName = fileName,
        mimeType = mimeType,
        bytes = "Simulated ${spec.kind.noun} for ${request.fieldPath}".encodeUtf8(),
        durationMillis =
          when (spec.kind) {
            MediaCaptureKind.VIDEO,
            MediaCaptureKind.AUDIO -> 5_000L
            else -> null
          },
        widthPx = if (spec.kind == MediaCaptureKind.PHOTO) longEdge else null,
        heightPx = if (spec.kind == MediaCaptureKind.PHOTO) longEdge * 3 / 4 else null,
      )
    onResult(MediaCaptureResult.Captured(attachment))
  }

  private fun simulatedMimeType(spec: MediaCaptureSpec): String {
    val concrete =
      spec.acceptedMediaType
        .split(',')
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.contains('*') }
    if (concrete != null) return concrete
    return when (spec.kind) {
      MediaCaptureKind.PHOTO -> "image/jpeg"
      MediaCaptureKind.VIDEO -> "video/mp4"
      MediaCaptureKind.AUDIO -> "audio/mp4"
      MediaCaptureKind.FILE -> "application/octet-stream"
    }
  }
}

/**
 * Host-provided [MediaCaptureHandler] used by photo, video, and audio questions. Defaults to
 * [SimulatedMediaCaptureHandler] so forms remain fully answerable without a platform bridge.
 */
val LocalMediaCaptureHandler: ProvidableCompositionLocal<MediaCaptureHandler> =
  staticCompositionLocalOf {
    SimulatedMediaCaptureHandler()
  }
