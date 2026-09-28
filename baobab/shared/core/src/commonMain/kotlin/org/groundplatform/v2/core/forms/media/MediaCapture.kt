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
package org.groundplatform.v2.core.forms.media

import groundplatform.v2.forms.ControlDef
import groundplatform.v2.forms.ControlType
import groundplatform.v2.forms.FieldBinding
import groundplatform.v2.forms.FieldValue
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import org.groundplatform.v2.core.forms.model.ComponentState
import org.groundplatform.v2.core.forms.model.FormState

/**
 * Kind of media a capture question (`<upload>` control bound to a `binary` field) collects.
 *
 * Derived from the control's `mediatype` attribute, following the ODK XForms spec's upload widget
 * table: any `image` type → photo, any `video` type → video, any `audio` type → audio. Anything
 * else (for example `application/pdf` or the all-types wildcard) is treated as a generic file.
 * (Wildcard MIME patterns are spelled out in prose here because Kotlin nests block comments.)
 */
enum class MediaCaptureKind(
  /** Default MIME wildcard used when a control omits `mediatype`. */
  val defaultMediaType: String,
  /** Short human-readable noun for UI copy (e.g. `"photo"`). */
  val noun: String,
) {
  PHOTO(defaultMediaType = "image/*", noun = "photo"),
  VIDEO(defaultMediaType = "video/*", noun = "video"),
  AUDIO(defaultMediaType = "audio/*", noun = "audio recording"),
  FILE(defaultMediaType = "*/*", noun = "file"),
}

/**
 * Resolved capture configuration for a single media question.
 *
 * Everything here is derived from standard XForms / ODK constructs so that forms authored in
 * XLSForm (`image`, `video`, `audio`, `file` question types) behave the same way they do in ODK
 * Collect and Enketo.
 */
data class MediaCaptureSpec(
  /** The kind of media collected by this question. */
  val kind: MediaCaptureKind,
  /** Accepted MIME type pattern(s) from `mediatype` (e.g. any image type), never blank. */
  val acceptedMediaType: String,
  /**
   * Maximum long-edge pixel size for photos (`orx:max-pixels` on the bind, XLSForm `parameters:
   * max-pixels=N`), or `null` when unrestricted. Hosts should downscale captured images
   * proportionally to fit.
   */
  val maxPixels: Int? = null,
  /**
   * True when the `new` appearance is present: the enumerator must capture fresh media with the
   * device camera or microphone and may not pick an existing file (ODK XForms spec, upload
   * appearances).
   */
  val requireNewCapture: Boolean = false,
  /**
   * True for the `new-front` (and legacy `selfie`) appearance: photo/video capture should default
   * to the front-facing camera.
   */
  val preferFrontCamera: Boolean = false,
  /** All appearance tokens declared on the control. */
  val appearanceTokens: Set<String> = emptySet(),
) {
  /** Whether a file of [mimeType] may be attached to this question. */
  fun accepts(mimeType: String): Boolean =
    MediaCapture.mediaTypeMatches(acceptedMediaType, mimeType)

  /** Whether the host may offer a "choose existing file" option in addition to live capture. */
  val allowsExistingFile: Boolean
    get() = !requireNewCapture
}

/**
 * A captured or selected media file attached to a record.
 *
 * Per the OpenRosa Form Submission API, the record itself only stores the attachment [fileName]
 * (`<photo>leaf_1727465921000.jpg</photo>`); the bytes travel alongside the submission as a
 * separate multipart part. [org.groundplatform.v2.core.forms.engine.FormSession] keeps the two
 * together for the duration of an editing session.
 */
data class MediaAttachment(
  /** File name stored as the field's answer. Must be unique within a record. */
  val fileName: String,
  /** Concrete MIME type of the payload (e.g. `"image/jpeg"`). */
  val mimeType: String,
  /** Raw media bytes. */
  val bytes: ByteString,
  /** Wall-clock capture time in epoch milliseconds, or `0` if unknown. */
  val capturedAtEpochMillis: Long = 0L,
  /** Playback duration for audio and video, if known. */
  val durationMillis: Long? = null,
  /** Pixel width for photos and videos, if known. */
  val widthPx: Int? = null,
  /** Pixel height for photos and videos, if known. */
  val heightPx: Int? = null,
) {
  /** Payload size in bytes. */
  val sizeBytes: Long
    get() = bytes.size.toLong()

  /** Media kind inferred from [mimeType]. */
  val kind: MediaCaptureKind
    get() = MediaCapture.kindForMediaType(mimeType)

  /** Raw payload as a [ByteArray] (copy), for platform image decoders and file writers. */
  fun toByteArray(): ByteArray = bytes.toByteArray()

  override fun toString(): String =
    "MediaAttachment(fileName=$fileName, mimeType=$mimeType, sizeBytes=$sizeBytes, " +
      "durationMillis=$durationMillis, widthPx=$widthPx, heightPx=$heightPx)"

  companion object {
    /**
     * Builds an attachment from a base64 payload, as produced by browser `FileReader` bridges.
     * Returns `null` if [base64] is not valid base64.
     */
    fun fromBase64(
      fileName: String,
      mimeType: String,
      base64: String,
      capturedAtEpochMillis: Long = 0L,
      durationMillis: Long? = null,
      widthPx: Int? = null,
      heightPx: Int? = null,
    ): MediaAttachment? {
      val bytes = base64.decodeBase64() ?: return null
      return MediaAttachment(
        fileName = fileName,
        mimeType = mimeType,
        bytes = bytes,
        capturedAtEpochMillis = capturedAtEpochMillis,
        durationMillis = durationMillis,
        widthPx = widthPx,
        heightPx = heightPx,
      )
    }

    /** Builds an attachment from raw [bytes]. */
    fun fromBytes(
      fileName: String,
      mimeType: String,
      bytes: ByteArray,
      capturedAtEpochMillis: Long = 0L,
      durationMillis: Long? = null,
      widthPx: Int? = null,
      heightPx: Int? = null,
    ): MediaAttachment =
      MediaAttachment(
        fileName = fileName,
        mimeType = mimeType,
        bytes = ByteString.of(*bytes),
        capturedAtEpochMillis = capturedAtEpochMillis,
        durationMillis = durationMillis,
        widthPx = widthPx,
        heightPx = heightPx,
      )
  }
}

/** Thrown when an attachment is rejected for a media question. */
class MediaAttachmentRejectedException(message: String) : IllegalArgumentException(message)

/** Pure helpers for resolving and validating photo, video, and audio capture questions. */
object MediaCapture {

  /**
   * Resolves the [MediaCaptureSpec] for [control], or `null` if it is not a media question.
   *
   * Only `CONTROL_UPLOAD` controls are media questions. [binding] supplies `orx:max-pixels`.
   */
  fun resolveSpec(control: ControlDef, binding: FieldBinding?): MediaCaptureSpec? {
    if (control.type != ControlType.CONTROL_UPLOAD) return null
    val tokens = control.appearance.split(' ', '\t', '\n').filter { it.isNotBlank() }.toSet()
    val declaredType = control.media_type.trim()
    val kind =
      if (declaredType.isEmpty()) {
        // ODK Collect falls back to an image widget for an upload without mediatype.
        MediaCaptureKind.PHOTO
      } else {
        kindForMediaType(declaredType)
      }
    return MediaCaptureSpec(
      kind = kind,
      acceptedMediaType = declaredType.ifEmpty { kind.defaultMediaType },
      maxPixels = binding?.max_pixels?.takeIf { it > 0 && kind == MediaCaptureKind.PHOTO },
      requireNewCapture = "new" in tokens || "new-front" in tokens,
      preferFrontCamera = "new-front" in tokens || "selfie" in tokens,
      appearanceTokens = tokens,
    )
  }

  /** Resolves the [MediaCaptureKind] implied by a MIME type or MIME pattern. */
  fun kindForMediaType(mediaType: String): MediaCaptureKind {
    val first = mediaType.split(',').firstOrNull()?.trim()?.lowercase().orEmpty()
    return when {
      first.startsWith("image/") -> MediaCaptureKind.PHOTO
      first.startsWith("video/") -> MediaCaptureKind.VIDEO
      first.startsWith("audio/") -> MediaCaptureKind.AUDIO
      else -> MediaCaptureKind.FILE
    }
  }

  /**
   * Returns true if the concrete [actual] MIME type satisfies [accepted], which may be a single
   * pattern (a concrete type such as `audio/mp4`, a subtype wildcard such as "image/" followed by
   * an asterisk, or the all-types wildcard) or a comma-separated list of patterns. Parameters such
   * as `;codecs=opus` are ignored. Comparison is case-insensitive (RFC 2045 section 5.1).
   */
  fun mediaTypeMatches(accepted: String, actual: String): Boolean {
    val normalizedActual = actual.substringBefore(';').trim().lowercase()
    if (normalizedActual.isEmpty()) return false
    val patterns =
      accepted
        .split(',')
        .map { it.substringBefore(';').trim().lowercase() }
        .filter { it.isNotEmpty() }
    if (patterns.isEmpty()) return true
    return patterns.any { pattern ->
      when {
        pattern == "*" || pattern == "*/*" -> true
        pattern.endsWith("/*") -> normalizedActual.startsWith(pattern.removeSuffix("*"))
        else -> pattern == normalizedActual
      }
    }
  }

  private val EXTENSIONS_BY_MIME =
    mapOf(
      "image/jpeg" to "jpg",
      "image/jpg" to "jpg",
      "image/png" to "png",
      "image/gif" to "gif",
      "image/webp" to "webp",
      "image/heic" to "heic",
      "image/heif" to "heif",
      "image/svg+xml" to "svg",
      "video/mp4" to "mp4",
      "video/webm" to "webm",
      "video/quicktime" to "mov",
      "video/3gpp" to "3gp",
      "audio/mp4" to "m4a",
      "audio/x-m4a" to "m4a",
      "audio/aac" to "aac",
      "audio/mpeg" to "mp3",
      "audio/ogg" to "ogg",
      "audio/webm" to "weba",
      "audio/wav" to "wav",
      "audio/x-wav" to "wav",
      "audio/amr" to "amr",
      "audio/3gpp" to "3gpp",
      "application/pdf" to "pdf",
    )

  private val MIME_BY_EXTENSION: Map<String, String> = buildMap {
    EXTENSIONS_BY_MIME.forEach { (mime, ext) -> if (ext !in this) put(ext, mime) }
    put("jpeg", "image/jpeg")
    put("webm", "video/webm")
  }

  /** Best-effort file extension (without dot) for [mimeType]. */
  fun extensionForMimeType(mimeType: String): String {
    val normalized = mimeType.substringBefore(';').trim().lowercase()
    EXTENSIONS_BY_MIME[normalized]?.let {
      return it
    }
    return when (kindForMediaType(normalized)) {
      MediaCaptureKind.PHOTO -> "jpg"
      MediaCaptureKind.VIDEO -> "mp4"
      MediaCaptureKind.AUDIO -> "m4a"
      MediaCaptureKind.FILE -> "bin"
    }
  }

  /** Best-effort MIME type for [fileName] based on its extension, or `null` if unknown. */
  fun mimeTypeForFileName(fileName: String): String? {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return MIME_BY_EXTENSION[ext]
  }

  /**
   * Generates an attachment file name for a new capture: `<field>_<epochMillis>.<ext>`.
   *
   * ODK Collect names captures `<epochMillis>.<ext>`; prefixing the field name keeps names unique
   * when several questions are answered within the same millisecond and makes exported media
   * folders self-describing. Only `[A-Za-z0-9_-]` survive in the field-name part.
   */
  fun generateFileName(fieldPath: String, mimeType: String, epochMillis: Long): String {
    val fieldName =
      fieldPath
        .substringAfterLast('/')
        .substringBefore('[')
        .map { ch -> if (ch.isLetterOrDigit() || ch == '_' || ch == '-') ch else '_' }
        .joinToString("")
        .ifEmpty { "media" }
    return "${fieldName}_$epochMillis.${extensionForMimeType(mimeType)}"
  }

  /**
   * Extracts the attachment file name stored in a binary field [value], or `null` if empty.
   *
   * Values may arrive as `binary_value` (UTF-8 file name, as produced by the XForms instance parser
   * and by coercion of string input to `TYPE_BINARY`) or as a plain `string_value`.
   */
  fun attachmentFileName(value: FieldValue?): String? {
    val scalar = value?.scalar_value ?: return null
    val name = scalar.string_value ?: scalar.binary_value?.utf8()
    return name?.trim()?.takeIf { it.isNotEmpty() }
  }

  /** Finds the materialized media question at [canonicalPath] in [state], if any. */
  fun findMediaControl(state: FormState, canonicalPath: String): ComponentState.ControlState? {
    fun visit(components: List<ComponentState>): ComponentState.ControlState? {
      for (component in components) {
        when (component) {
          is ComponentState.ControlState ->
            if (component.canonicalPath == canonicalPath && component.mediaCapture != null) {
              return component
            }
          is ComponentState.GroupState ->
            visit(component.children)?.let {
              return it
            }
          is ComponentState.RepeatGroupState ->
            component.instances.forEach { inst ->
              visit(inst.children)?.let {
                return it
              }
            }
        }
      }
      return null
    }
    return visit(state.rootComponents)
  }

  /**
   * Returns the file names referenced by relevant, non-empty media questions in [state]: the set of
   * attachments that must accompany a submission of the current record.
   */
  fun referencedFileNames(state: FormState): Set<String> {
    val names = linkedSetOf<String>()
    fun visit(components: List<ComponentState>) {
      for (component in components) {
        if (!component.isRelevant) continue
        when (component) {
          is ComponentState.ControlState ->
            if (component.mediaCapture != null) {
              attachmentFileName(component.fieldState.value)?.let { names.add(it) }
            }
          is ComponentState.GroupState -> visit(component.children)
          is ComponentState.RepeatGroupState ->
            component.instances.filter { it.isRelevant }.forEach { visit(it.children) }
        }
      }
    }
    visit(state.rootComponents)
    return names
  }
}
