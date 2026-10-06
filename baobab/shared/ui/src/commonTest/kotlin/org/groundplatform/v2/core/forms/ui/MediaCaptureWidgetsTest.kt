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

import groundplatform.v2.forms.DataType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.ByteString.Companion.encodeUtf8
import org.groundplatform.v2.core.forms.media.MediaAttachment
import org.groundplatform.v2.core.forms.media.MediaCapture
import org.groundplatform.v2.core.forms.media.MediaCaptureKind
import org.groundplatform.v2.core.forms.media.MediaCaptureSpec
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer

class MediaCaptureWidgetsTest {

  private fun controllerForExampleForm(): FormWizardController =
    FormWizardController(
      formDef =
        XFormsXmlSerializer.deserializeFormDef(WorkbenchExampleForm.ALL_FIELD_TYPES.xformsXml)
    )

  private fun mediaSteps(controller: FormWizardController) =
    controller.steps
      .filterIsInstance<FormWizardStep.QuestionStep>()
      .map { it.control }
      .filter { it.mediaCapture != null }
      .associateBy { it.canonicalPath }

  @Test
  fun exampleForm_exposesPhotoVideoAndAudioQuestions() {
    val media = mediaSteps(controllerForExampleForm())
    assertEquals(
      MediaCaptureKind.PHOTO,
      media.getValue("/data/leaf_voucher_photo").mediaCapture!!.kind,
    )
    assertEquals(1600, media.getValue("/data/leaf_voucher_photo").mediaCapture!!.maxPixels)
    val video = media.getValue("/data/canopy_walkthrough_video").mediaCapture!!
    assertEquals(MediaCaptureKind.VIDEO, video.kind)
    assertTrue(video.requireNewCapture)
    assertEquals(
      MediaCaptureKind.AUDIO,
      media.getValue("/data/farmer_interview_audio").mediaCapture!!.kind,
    )
  }

  @Test
  fun simulatedHandler_answersEachMediaQuestionThroughController() {
    val controller = controllerForExampleForm()
    val handler = SimulatedMediaCaptureHandler()

    for ((path, control) in mediaSteps(controller)) {
      val spec = control.mediaCapture!!
      var result: MediaCaptureResult? = null
      handler.launch(
        MediaCaptureRequest(
          fieldPath = path,
          spec = spec,
          source = MediaCaptureSource.CAPTURE,
          suggestedFileName = MediaCapture.generateFileName(path, "image/jpeg", 42L),
        )
      ) {
        result = it
      }
      val captured = assertIs<MediaCaptureResult.Captured>(result)
      assertTrue(spec.accepts(captured.attachment.mimeType), captured.attachment.mimeType)
      assertNull(controller.attachMedia(path, captured.attachment))
      assertEquals(captured.attachment, controller.attachmentFor(path))
    }

    val video = controller.attachmentFor("/data/canopy_walkthrough_video")!!
    assertEquals("canopy_walkthrough_video_42.mp4", video.fileName)
    assertEquals(5_000L, video.durationMillis)
    val photo = controller.attachmentFor("/data/leaf_voucher_photo")!!
    assertEquals(1600, photo.widthPx)
    assertEquals(3, controller.referencedAttachments.size)

    // The review screen shows the stored file name for binary answers.
    val audioField = controller.formState.findFieldState("/data/farmer_interview_audio")!!
    assertEquals(
      "farmer_interview_audio_42.m4a",
      formatFieldValueForDisplay(audioField.value, DataType.TYPE_BINARY),
    )

    val result = controller.finalizeForm()
    if (result is FinalizationResult.Success) {
      val xml = XFormsXmlSerializer.serializeRecordInstance(result.recordInstance)
      assertTrue(xml.contains("<canopy_walkthrough_video>canopy_walkthrough_video_42.mp4<"), xml)
    }
  }

  @Test
  fun controller_reportsRejectedAttachmentsAndRemovesMedia() {
    val controller = controllerForExampleForm()
    val wrongType = MediaAttachment("note.m4a", "audio/mp4", "x".encodeUtf8())
    val error = controller.attachMedia("/data/leaf_voucher_photo", wrongType)
    assertNotNull(error)
    assertTrue(error.contains("image/*"), error)

    val audio = MediaAttachment("note.m4a", "audio/mp4", "x".encodeUtf8())
    assertNull(controller.attachMedia("/data/farmer_interview_audio", audio))
    controller.removeMedia("/data/farmer_interview_audio")
    assertNull(controller.attachmentFor("/data/farmer_interview_audio"))
    assertTrue(controller.formState.findFieldState("/data/farmer_interview_audio")!!.isEmpty)
  }

  @Test
  fun formattingHelpers() {
    assertEquals("512 B", formatByteSize(512))
    assertEquals("1.5 KB", formatByteSize(1536))
    assertEquals("2.0 MB", formatByteSize(2L * 1024 * 1024))
    assertEquals("0:05", formatMediaDuration(4_600))
    assertEquals("1:02:03", formatMediaDuration(3_723_000))
    assertEquals(
      "Accepts image/* · Max 1024 px · Capture only · Front camera",
      describeMediaConstraints(
        MediaCaptureSpec(
          kind = MediaCaptureKind.PHOTO,
          acceptedMediaType = "image/*",
          maxPixels = 1024,
          requireNewCapture = true,
          preferFrontCamera = true,
        )
      ),
    )
    assertEquals(
      "Accepts video/*",
      describeMediaConstraints(MediaCaptureSpec(MediaCaptureKind.VIDEO, "video/*")),
    )
    assertEquals("Capture only", mediaSourceModeLabel(captureOnly = true))
    assertEquals("Capture or upload", mediaSourceModeLabel(captureOnly = false))
    assertEquals(
      "This question needs a live audio recording, which this device/browser can't capture.",
      captureUnavailableMessage(MediaCaptureKind.AUDIO, captureOnly = true),
    )
    assertEquals(
      "This device can't capture or choose a photo.",
      captureUnavailableMessage(MediaCaptureKind.PHOTO, captureOnly = false),
    )
    assertEquals(
      "a.jpg · 3 B · 4×3",
      describeAttachment(
        MediaAttachment("a.jpg", "image/jpeg", "abc".encodeUtf8(), widthPx = 4, heightPx = 3)
      ),
    )
    assertEquals("Take photo", captureActionLabel(MediaCaptureKind.PHOTO))
    assertEquals("Record audio", captureActionLabel(MediaCaptureKind.AUDIO))
  }
}
