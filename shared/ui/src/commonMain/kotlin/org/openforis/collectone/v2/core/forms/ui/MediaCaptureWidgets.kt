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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.media.MediaAttachment
import org.openforis.collectone.v2.core.forms.media.MediaCapture
import org.openforis.collectone.v2.core.forms.media.MediaCaptureKind
import org.openforis.collectone.v2.core.forms.media.MediaCaptureSpec
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_attached_with_duration
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_audio_attached
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_capture_only
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_capture_or_upload
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_captured_photo_description
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_choose_file
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_choose_from_device
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_file_attached
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_file_unavailable_capture
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_file_unavailable_choose
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_front_camera
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_kind_audio
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_kind_file
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_kind_photo
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_kind_video
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_live_unavailable_audio
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_live_unavailable_file
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_live_unavailable_photo
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_live_unavailable_video
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_max_pixels
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_photo_attached
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_record_again
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_record_audio
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_record_video
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_remove
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_replace_file
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_retake_photo
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_take_photo
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_unavailable_audio
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_unavailable_file
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_unavailable_photo
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_unavailable_video
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_upload
import org.openforis.collectone.v2.core.forms.ui.resources.forms_media_video_attached

/**
 * Photo, video, and audio capture question (`<upload>` bound to a `binary` field).
 *
 * Capture itself is delegated to the host's [LocalMediaCaptureHandler]; this widget renders the
 * question's constraints, the capture / choose-file actions, and a preview of the current answer.
 *
 * The organizer's source mode is made explicit: with the ODK `new` appearance
 * ([MediaCaptureSpec.requireNewCapture]) the card is badged **Capture only**, offers only live
 * capture (and only live retakes), and shows an error when the host can't capture; otherwise it is
 * badged **Capture or upload** and offers both the primary capture action and a secondary "Choose
 * from device" upload. The same widget serves the mobile runner and the compact web layout.
 */
@Composable
internal fun MediaCaptureWidget(
  control: ComponentState.ControlState,
  controller: FormWizardController,
) {
  val spec =
    control.mediaCapture
      ?: MediaCapture.resolveSpec(control.controlDef, control.fieldState.binding)
      ?: return
  val handler = LocalMediaCaptureHandler.current
  val colors = MaterialTheme.colorScheme
  val path = control.canonicalPath
  val fileName = MediaCapture.attachmentFileName(control.fieldState.value)
  // The field value changes whenever the attachment does, so keying on it keeps this in sync.
  val attachment = remember(path, fileName) { fileName?.let { controller.attachmentFor(path) } }
  var errorMessage by remember(path) { mutableStateOf<FormUiText?>(null) }
  var isCapturing by remember(path) { mutableStateOf(false) }

  val launch: (MediaCaptureSource) -> Unit = { source ->
    errorMessage = null
    isCapturing = true
    val request =
      MediaCaptureRequest(
        fieldPath = path,
        spec = spec,
        source = source,
        suggestedFileName =
          MediaCapture.generateFileName(
            fieldPath = path,
            mimeType = defaultMimeTypeFor(spec.kind),
            epochMillis = controller.session.environment.clockEpochMillis(),
          ),
      )
    handler.launch(request) { result ->
      isCapturing = false
      when (result) {
        is MediaCaptureResult.Captured ->
          errorMessage = controller.attachMedia(path, result.attachment)
        is MediaCaptureResult.Cancelled -> Unit
        is MediaCaptureResult.Failed -> errorMessage = FormUiText.Verbatim(result.message)
      }
    }
  }

  // "Capture only" (ODK `new` appearance) locks the question to live camera / microphone capture;
  // otherwise collectors may capture or upload an existing file. Generic file questions only pick.
  val captureOnly = spec.requireNewCapture && spec.kind != MediaCaptureKind.FILE
  val canCapture =
    spec.kind != MediaCaptureKind.FILE && handler.supports(spec.kind, MediaCaptureSource.CAPTURE)
  val canPick =
    !captureOnly &&
      spec.allowsExistingFile &&
      handler.supports(spec.kind, MediaCaptureSource.EXISTING_FILE)

  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      MediaCaptureHeader(spec)

      when {
        attachment != null -> MediaAttachmentPreview(attachment)
        fileName != null ->
          Text(
            text =
              stringResource(
                if (canCapture) Res.string.forms_media_file_unavailable_capture
                else Res.string.forms_media_file_unavailable_choose,
                fileName,
              ),
            style = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant),
          )
        else -> Unit
      }

      if (fileName == null) {
        if (canCapture) {
          Button(
            onClick = { launch(MediaCaptureSource.CAPTURE) },
            enabled = !isCapturing,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(mediaKindIcon(spec.kind), contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
              stringResource(captureActionLabel(spec.kind)),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        if (canPick) {
          val pickLabel =
            stringResource(
              if (canCapture) Res.string.forms_media_choose_from_device
              else Res.string.forms_media_choose_file
            )
          if (canCapture) {
            OutlinedButton(
              onClick = { launch(MediaCaptureSource.EXISTING_FILE) },
              enabled = !isCapturing,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(pickLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          } else {
            Button(
              onClick = { launch(MediaCaptureSource.EXISTING_FILE) },
              enabled = !isCapturing,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(mediaKindIcon(spec.kind), contentDescription = null, Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(pickLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
        if (!canCapture && !canPick) {
          Text(
            text = stringResource(captureUnavailableMessage(spec.kind, captureOnly)),
            style = MaterialTheme.typography.bodySmall.copy(color = colors.error),
          )
        }
        Text(
          text = handler.displayName,
          style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
        )
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Capture-only questions may only be retaken live; never offer a file replace there.
          val retakeSource =
            when {
              canCapture -> MediaCaptureSource.CAPTURE
              canPick -> MediaCaptureSource.EXISTING_FILE
              else -> null
            }
          if (retakeSource != null) {
            OutlinedButton(
              onClick = { launch(retakeSource) },
              enabled = !isCapturing,
              modifier = Modifier.weight(1f),
            ) {
              Text(
                stringResource(
                  if (retakeSource == MediaCaptureSource.CAPTURE) retakeActionLabel(spec.kind)
                  else Res.string.forms_media_replace_file
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          if (canCapture && canPick) {
            TextButton(
              onClick = { launch(MediaCaptureSource.EXISTING_FILE) },
              enabled = !isCapturing,
            ) {
              Text(stringResource(Res.string.forms_media_upload), maxLines = 1)
            }
          }
          TextButton(
            onClick = {
              errorMessage = null
              controller.removeMedia(path)
            },
            enabled = !isCapturing,
          ) {
            Text(stringResource(Res.string.forms_media_remove), maxLines = 1)
          }
        }
      }

      errorMessage?.let { message ->
        Text(
          text = message.resolve(),
          style =
            MaterialTheme.typography.bodySmall.copy(
              color = colors.error,
              fontWeight = FontWeight.SemiBold,
            ),
        )
      }
    }
  }
}

@Composable
private fun MediaCaptureHeader(spec: MediaCaptureSpec) {
  val colors = MaterialTheme.colorScheme
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Surface(
      shape = CircleShape,
      color = colors.primaryContainer,
      contentColor = colors.onPrimaryContainer,
      modifier = Modifier.size(40.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = mediaKindIcon(spec.kind),
          contentDescription = null,
          tint = colors.onPrimaryContainer,
          modifier = Modifier.size(22.dp),
        )
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(mediaKindTitle(spec.kind)),
        style =
          MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
          ),
      )
      val constraintsText = describeMediaConstraints(spec).map { it.resolve() }.joinToString(" · ")
      if (constraintsText.isNotEmpty()) {
        Text(
          text = constraintsText,
          style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
        )
      }
    }
    if (spec.kind != MediaCaptureKind.FILE) MediaSourceBadge(spec.requireNewCapture)
  }
}

/** "Capture only" (locked) or "Capture or upload" badge, so collectors see the mode up front. */
@Composable
private fun MediaSourceBadge(captureOnly: Boolean) {
  val colors = MaterialTheme.colorScheme
  Surface(
    shape = MaterialTheme.shapes.small,
    color = if (captureOnly) colors.tertiaryContainer else colors.surfaceContainerHigh,
    contentColor = if (captureOnly) colors.onTertiaryContainer else colors.onSurfaceVariant,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (captureOnly) {
        Icon(
          imageVector = LockIcon,
          contentDescription = null,
          tint = colors.onTertiaryContainer,
          modifier = Modifier.size(12.dp),
        )
      }
      Text(
        text = stringResource(mediaSourceModeLabel(captureOnly)),
        style =
          MaterialTheme.typography.labelSmall.copy(
            color = if (captureOnly) colors.onTertiaryContainer else colors.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
          ),
        maxLines = 1,
      )
    }
  }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun MediaAttachmentPreview(attachment: MediaAttachment) {
  val colors = MaterialTheme.colorScheme
  val bitmap: ImageBitmap? =
    remember(attachment) {
      if (attachment.kind != MediaCaptureKind.PHOTO) {
        null
      } else {
        runCatching { attachment.toByteArray().decodeToImageBitmap() }.getOrNull()
      }
    }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (bitmap != null) {
      Image(
        bitmap = bitmap,
        contentDescription =
          stringResource(Res.string.forms_media_captured_photo_description, attachment.fileName),
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium),
      )
    } else {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = mediaKindIcon(attachment.kind),
            contentDescription = null,
            tint = colors.onSecondaryContainer,
            modifier = Modifier.size(28.dp),
          )
          val attachedLabel =
            stringResource(
              when (attachment.kind) {
                MediaCaptureKind.PHOTO -> Res.string.forms_media_photo_attached
                MediaCaptureKind.VIDEO -> Res.string.forms_media_video_attached
                MediaCaptureKind.AUDIO -> Res.string.forms_media_audio_attached
                MediaCaptureKind.FILE -> Res.string.forms_media_file_attached
              }
            )
          Text(
            text =
              attachment.durationMillis?.let {
                stringResource(
                  Res.string.forms_media_attached_with_duration,
                  attachedLabel,
                  formatMediaDuration(it),
                )
              } ?: attachedLabel,
            style =
              MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = colors.onSecondaryContainer,
              ),
          )
        }
      }
    }
    Text(
      text = describeAttachment(attachment),
      style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

internal fun mediaKindTitle(kind: MediaCaptureKind): StringResource =
  when (kind) {
    MediaCaptureKind.PHOTO -> Res.string.forms_media_kind_photo
    MediaCaptureKind.VIDEO -> Res.string.forms_media_kind_video
    MediaCaptureKind.AUDIO -> Res.string.forms_media_kind_audio
    MediaCaptureKind.FILE -> Res.string.forms_media_kind_file
  }

internal fun captureActionLabel(kind: MediaCaptureKind): StringResource =
  when (kind) {
    MediaCaptureKind.PHOTO -> Res.string.forms_media_take_photo
    MediaCaptureKind.VIDEO -> Res.string.forms_media_record_video
    MediaCaptureKind.AUDIO -> Res.string.forms_media_record_audio
    MediaCaptureKind.FILE -> Res.string.forms_media_choose_file
  }

internal fun retakeActionLabel(kind: MediaCaptureKind): StringResource =
  when (kind) {
    MediaCaptureKind.PHOTO -> Res.string.forms_media_retake_photo
    MediaCaptureKind.VIDEO -> Res.string.forms_media_record_again
    MediaCaptureKind.AUDIO -> Res.string.forms_media_record_again
    MediaCaptureKind.FILE -> Res.string.forms_media_replace_file
  }

/** Badge label for a media question's source mode (ODK `new` appearance ⇒ capture only). */
internal fun mediaSourceModeLabel(captureOnly: Boolean): StringResource =
  if (captureOnly) Res.string.forms_media_capture_only else Res.string.forms_media_capture_or_upload

/**
 * Error shown on a capture-only question when the host can't capture live media, so collectors
 * learn why there is no action rather than seeing an empty card.
 */
internal fun captureUnavailableMessage(
  kind: MediaCaptureKind,
  captureOnly: Boolean,
): StringResource =
  if (captureOnly) {
    when (kind) {
      MediaCaptureKind.PHOTO -> Res.string.forms_media_live_unavailable_photo
      MediaCaptureKind.VIDEO -> Res.string.forms_media_live_unavailable_video
      MediaCaptureKind.AUDIO -> Res.string.forms_media_live_unavailable_audio
      MediaCaptureKind.FILE -> Res.string.forms_media_live_unavailable_file
    }
  } else {
    when (kind) {
      MediaCaptureKind.PHOTO -> Res.string.forms_media_unavailable_photo
      MediaCaptureKind.VIDEO -> Res.string.forms_media_unavailable_video
      MediaCaptureKind.AUDIO -> Res.string.forms_media_unavailable_audio
      MediaCaptureKind.FILE -> Res.string.forms_media_unavailable_file
    }
  }

/**
 * A question's capture constraints, shown joined with ` · ` on one line (e.g. `Max 1024 px ·
 * Capture only`).
 */
internal fun describeMediaConstraints(spec: MediaCaptureSpec): List<FormUiText> = buildList {
  spec.maxPixels?.let { add(FormUiText.Resource(Res.string.forms_media_max_pixels, it)) }
  if (spec.requireNewCapture) add(FormUiText.Resource(Res.string.forms_media_capture_only))
  if (spec.preferFrontCamera) add(FormUiText.Resource(Res.string.forms_media_front_camera))
}

/** One-line summary of an attachment, e.g. `leaf_1.jpg · 1.2 MB · 1600×1200`. */
internal fun describeAttachment(attachment: MediaAttachment): String = buildList {
  add(attachment.fileName)
  add(formatByteSize(attachment.sizeBytes))
  attachment.durationMillis?.let { add(formatMediaDuration(it)) }
  val w = attachment.widthPx
  val h = attachment.heightPx
  if (w != null && h != null) add("$w×$h")
}
  .joinToString(" · ")

/** Formats a byte count as `B`, `KB`, or `MB` with one decimal place. */
internal fun formatByteSize(bytes: Long): String {
  fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return "${tenths / 10}.${tenths % 10}"
  }
  return when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> "${oneDecimal(bytes / 1024.0)} KB"
    else -> "${oneDecimal(bytes / (1024.0 * 1024.0))} MB"
  }
}

/** Formats a duration as `m:ss` (or `h:mm:ss` past an hour). */
internal fun formatMediaDuration(millis: Long): String {
  val totalSeconds = (millis.coerceAtLeast(0) + 500) / 1000
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = (totalSeconds % 60).toString().padStart(2, '0')
  return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$seconds"
  else "$minutes:$seconds"
}

private fun defaultMimeTypeFor(kind: MediaCaptureKind): String =
  when (kind) {
    MediaCaptureKind.PHOTO -> "image/jpeg"
    MediaCaptureKind.VIDEO -> "video/mp4"
    MediaCaptureKind.AUDIO -> "audio/mp4"
    MediaCaptureKind.FILE -> "application/octet-stream"
  }

// Material Symbols path data (Apache 2.0), inlined because `material-icons-core` omits these.
private fun materialIcon(name: String, pathData: String): ImageVector =
  ImageVector.Builder(
      name = name,
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    )
    .addPath(
      pathData = PathParser().parsePathString(pathData).toNodes(),
      fill = SolidColor(Color.Black),
    )
    .build()

private val CameraIcon: ImageVector by lazy {
  materialIcon(
    "App.Camera",
    "M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0" +
      "M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6" +
      "c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5" +
      " -2.24,5 -5,5z",
  )
}

private val VideocamIcon: ImageVector by lazy {
  materialIcon(
    "App.Videocam",
    "M17,10.5V7c0,-0.55 -0.45,-1 -1,-1H4c-0.55,0 -1,0.45 -1,1v10c0,0.55 0.45,1 1,1h12" +
      "c0.55,0 1,-0.45 1,-1v-3.5l4,4v-11l-4,4z",
  )
}

private val MicIcon: ImageVector by lazy {
  materialIcon(
    "App.Mic",
    "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6" +
      "c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72" +
      "V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z",
  )
}

private val AttachFileIcon: ImageVector by lazy {
  materialIcon(
    "App.AttachFile",
    "M16.5,6v11.5c0,2.21 -1.79,4 -4,4s-4,-1.79 -4,-4V5c0,-1.38 1.12,-2.5 2.5,-2.5" +
      "s2.5,1.12 2.5,2.5v10.5c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1V6H10v9.5c0,1.38 1.12,2.5 2.5,2.5" +
      "s2.5,-1.12 2.5,-2.5V5c0,-2.21 -1.79,-4 -4,-4S7,2.79 7,5v12.5c0,3.04 2.46,5.5 5.5,5.5" +
      "s5.5,-2.46 5.5,-5.5V6h-1.5z",
  )
}

private val LockIcon: ImageVector by lazy {
  materialIcon(
    "App.Lock",
    "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12" +
      "c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2" +
      " -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z",
  )
}

internal fun mediaKindIcon(kind: MediaCaptureKind): ImageVector =
  when (kind) {
    MediaCaptureKind.PHOTO -> CameraIcon
    MediaCaptureKind.VIDEO -> VideocamIcon
    MediaCaptureKind.AUDIO -> MicIcon
    MediaCaptureKind.FILE -> AttachFileIcon
  }
