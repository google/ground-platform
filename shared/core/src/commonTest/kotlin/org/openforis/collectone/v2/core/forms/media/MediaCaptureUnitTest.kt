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
package org.openforis.collectone.v2.core.forms.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.ByteString.Companion.encodeUtf8
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.core.forms.engine.FormSession
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer

class MediaCaptureUnitTest {

  // XLSForm `image`, `video`, and `audio` question types compile to `<upload>` controls bound to
  // `binary` fields (ODK XForms spec, "Upload" body element).
  private val mediaFormXml =
    """
    <?xml version="1.0"?>
    <h:html xmlns="http://www.w3.org/2002/xforms"
            xmlns:h="http://www.w3.org/1999/xhtml"
            xmlns:jr="http://openrosa.org/javarosa"
            xmlns:orx="http://openrosa.org/xforms">
      <h:head>
        <h:title>Media capture</h:title>
        <model>
          <instance>
            <data id="media_capture">
              <include_extras/>
              <leaf_photo/>
              <selfie/>
              <walkthrough_video/>
              <interview_audio/>
              <extra_photo/>
              <orx:meta><orx:instanceID/></orx:meta>
            </data>
          </instance>
          <bind nodeset="/data/include_extras" type="string"/>
          <bind nodeset="/data/leaf_photo" type="binary" required="true()" orx:max-pixels="1024"/>
          <bind nodeset="/data/selfie" type="binary"/>
          <bind nodeset="/data/walkthrough_video" type="binary"/>
          <bind nodeset="/data/interview_audio" type="binary"/>
          <bind nodeset="/data/extra_photo" type="binary" relevant="/data/include_extras = 'yes'"/>
        </model>
      </h:head>
      <h:body>
        <input ref="/data/include_extras"><label>Include extras?</label></input>
        <upload ref="/data/leaf_photo" mediatype="image/*"><label>Leaf photo</label></upload>
        <upload ref="/data/selfie" mediatype="image/*" appearance="new-front"><label>Selfie</label></upload>
        <upload ref="/data/walkthrough_video" mediatype="video/*" appearance="new"><label>Video</label></upload>
        <upload ref="/data/interview_audio" mediatype="audio/*"><label>Audio</label></upload>
        <upload ref="/data/extra_photo" mediatype="image/*"><label>Extra</label></upload>
      </h:body>
    </h:html>
    """
      .trimIndent()

  private fun newSession(): FormSession =
    FormSession(XFormsXmlSerializer.deserializeFormDef(mediaFormXml))

  private fun mediaControls(session: FormSession): Map<String, ComponentState.ControlState> =
    session.state.rootComponents
      .filterIsInstance<ComponentState.ControlState>()
      .filter { it.mediaCapture != null }
      .associateBy { it.canonicalPath.substringAfterLast('/') }

  private fun jpeg(name: String, payload: String = "jpeg-bytes-$name") =
    MediaAttachment(fileName = name, mimeType = "image/jpeg", bytes = payload.encodeUtf8())

  private fun mediaCaptureOf(fieldName: String): MediaCaptureSpec =
    assertNotNull(mediaControls(newSession()).getValue(fieldName).mediaCapture)

  @Test
  fun resolveSpec_uploadControls_haveMediaCapture() {
    assertEquals(
      setOf("leaf_photo", "selfie", "walkthrough_video", "interview_audio", "extra_photo"),
      mediaControls(newSession()).keys,
    )
  }

  @Test
  fun resolveSpec_imageUpload_isPhotoAcceptingImages() {
    val photo = mediaCaptureOf("leaf_photo")

    assertEquals(MediaCaptureKind.PHOTO, photo.kind)
    assertEquals("image/*", photo.acceptedMediaType)
  }

  @Test
  fun resolveSpec_maxPixelsBind_setsPhotoMaxPixels() {
    assertEquals(1024, mediaCaptureOf("leaf_photo").maxPixels)
  }

  @Test
  fun resolveSpec_noAppearance_allowsExistingFile() {
    val photo = mediaCaptureOf("leaf_photo")

    assertFalse(photo.requireNewCapture)
    assertTrue(photo.allowsExistingFile)
  }

  @Test
  fun resolveSpec_newFrontAppearance_requiresNewCaptureWithFrontCamera() {
    val selfie = mediaCaptureOf("selfie")

    assertTrue(selfie.preferFrontCamera)
    assertTrue(selfie.requireNewCapture)
  }

  @Test
  fun resolveSpec_withoutMaxPixelsBind_hasNoMaxPixels() {
    assertNull(mediaCaptureOf("selfie").maxPixels)
  }

  @Test
  fun resolveSpec_videoUpload_isVideo() {
    assertEquals(MediaCaptureKind.VIDEO, mediaCaptureOf("walkthrough_video").kind)
  }

  @Test
  fun resolveSpec_newAppearance_requiresNewCaptureWithoutFrontCamera() {
    val video = mediaCaptureOf("walkthrough_video")

    assertTrue(video.requireNewCapture)
    assertFalse(video.preferFrontCamera)
  }

  @Test
  fun resolveSpec_audioUpload_isAudio() {
    assertEquals(MediaCaptureKind.AUDIO, mediaCaptureOf("interview_audio").kind)
  }

  @Test
  fun resolveSpec_nonUploadControl_isNull() {
    assertNull(MediaCapture.resolveSpec(ControlDef(type = ControlType.CONTROL_INPUT), null))
  }

  @Test
  fun resolveSpec_uploadWithoutMediatype_defaultsToPhoto() {
    val spec =
      assertNotNull(
        MediaCapture.resolveSpec(
          ControlDef(type = ControlType.CONTROL_UPLOAD),
          FieldBinding(max_pixels = 640),
        )
      )

    assertEquals(MediaCaptureKind.PHOTO, spec.kind)
    assertEquals("image/*", spec.acceptedMediaType)
    assertEquals(640, spec.maxPixels)
  }

  @Test
  fun resolveSpec_maxPixelsOnNonPhoto_isIgnored() {
    // max-pixels only applies to photos.
    val audio =
      assertNotNull(
        MediaCapture.resolveSpec(
          ControlDef(type = ControlType.CONTROL_UPLOAD, media_type = "audio/*"),
          FieldBinding(max_pixels = 640),
        )
      )

    assertNull(audio.maxPixels)
  }

  @Test
  fun resolveSpec_nonMediaMediatype_isFile() {
    val spec =
      assertNotNull(
        MediaCapture.resolveSpec(
          ControlDef(type = ControlType.CONTROL_UPLOAD, media_type = "application/pdf"),
          null,
        )
      )

    assertEquals(MediaCaptureKind.FILE, spec.kind)
  }

  @Test
  fun mediaTypeMatches_wildcardSubtype_matchesSameTypeOnly() {
    assertTrue(MediaCapture.mediaTypeMatches("image/*", "image/jpeg"))
    assertFalse(MediaCapture.mediaTypeMatches("image/*", "video/mp4"))
  }

  @Test
  fun mediaTypeMatches_ignoresCase() {
    assertTrue(MediaCapture.mediaTypeMatches("IMAGE/*", "image/PNG"))
  }

  @Test
  fun mediaTypeMatches_ignoresParameters() {
    assertTrue(MediaCapture.mediaTypeMatches("audio/*", "audio/webm;codecs=opus"))
  }

  @Test
  fun mediaTypeMatches_list_matchesAnyEntry() {
    assertTrue(MediaCapture.mediaTypeMatches("video/mp4, video/webm", "video/webm"))
  }

  @Test
  fun mediaTypeMatches_exactType_rejectsOtherSubtypes() {
    assertFalse(MediaCapture.mediaTypeMatches("video/mp4", "video/webm"))
  }

  @Test
  fun mediaTypeMatches_anyOrBlankAccept_matchesEverything() {
    assertTrue(MediaCapture.mediaTypeMatches("*/*", "application/pdf"))
    assertTrue(MediaCapture.mediaTypeMatches("", "application/pdf"))
  }

  @Test
  fun mediaTypeMatches_blankActualType_doesNotMatch() {
    assertFalse(MediaCapture.mediaTypeMatches("image/*", ""))
  }

  @Test
  fun extensionForMimeType_knownType_returnsItsExtension() {
    assertEquals("jpg", MediaCapture.extensionForMimeType("image/jpeg"))
    assertEquals("weba", MediaCapture.extensionForMimeType("audio/webm;codecs=opus"))
  }

  @Test
  fun extensionForMimeType_unknownSubtype_fallsBackPerTopLevelType() {
    assertEquals("mp4", MediaCapture.extensionForMimeType("video/x-unknown"))
    assertEquals("bin", MediaCapture.extensionForMimeType("application/x-unknown"))
  }

  @Test
  fun mimeTypeForFileName_knownExtension_returnsTypeIgnoringCase() {
    assertEquals("image/jpeg", MediaCapture.mimeTypeForFileName("IMG_1.JPEG"))
    assertEquals("video/webm", MediaCapture.mimeTypeForFileName("clip.webm"))
  }

  @Test
  fun mimeTypeForFileName_noExtension_isNull() {
    assertNull(MediaCapture.mimeTypeForFileName("README"))
  }

  @Test
  fun generateFileName_usesLastPathStepTimestampAndExtension() {
    assertEquals(
      "leaf_photo_1727465921000.jpg",
      MediaCapture.generateFileName("/data/plots[2]/leaf_photo", "image/jpeg", 1727465921000L),
    )
  }

  @Test
  fun generateFileName_replacesUnsafeCharacters() {
    assertEquals("my_field_5.m4a", MediaCapture.generateFileName("/data/my field", "audio/mp4", 5L))
  }

  @Test
  fun attachmentFileName_noValue_isNull() {
    assertNull(MediaCapture.attachmentFileName(null))
  }

  @Test
  fun attachmentFileName_binaryValue_isDecoded() {
    assertEquals(
      "a.jpg",
      MediaCapture.attachmentFileName(
        FieldValue(scalar_value = TypedValue(binary_value = "a.jpg".encodeUtf8()))
      ),
    )
  }

  @Test
  fun attachmentFileName_stringValue_isTrimmed() {
    assertEquals(
      "b.jpg",
      MediaCapture.attachmentFileName(
        FieldValue(scalar_value = TypedValue(string_value = " b.jpg "))
      ),
    )
  }

  @Test
  fun attachmentFileName_blankString_isNull() {
    assertNull(
      MediaCapture.attachmentFileName(FieldValue(scalar_value = TypedValue(string_value = "")))
    )
  }

  @Test
  fun requiredMediaQuestion_withoutAttachment_isInvalid() {
    val session = newSession()

    assertTrue(session.state.validationErrors.any { it.fieldPath == "/data/leaf_photo" })
  }

  @Test
  fun attachMedia_storesFileNameInRecord() {
    val session = newSession()

    session.attachMedia("/data/leaf_photo", jpeg("leaf_photo_1.jpg"))

    val fieldState = assertNotNull(session.state.findFieldState("/data/leaf_photo"))
    assertEquals("leaf_photo_1.jpg", MediaCapture.attachmentFileName(fieldState.value))
  }

  @Test
  fun attachMedia_satisfiesRequired() {
    val session = newSession()

    session.attachMedia("/data/leaf_photo", jpeg("leaf_photo_1.jpg"))

    assertFalse(session.state.validationErrors.any { it.fieldPath == "/data/leaf_photo" })
  }

  @Test
  fun attachMedia_keepsBytesInSession() {
    val session = newSession()
    val photo = jpeg("leaf_photo_1.jpg")

    session.attachMedia("/data/leaf_photo", photo)

    assertEquals(photo, session.attachmentFor("/data/leaf_photo"))
    assertEquals(listOf(photo), session.referencedAttachments())
  }

  @Test
  fun attachMedia_submissionXml_carriesOnlyFileName() {
    // OpenRosa: the submission XML carries only the file name.
    val session = newSession()

    session.attachMedia("/data/leaf_photo", jpeg("leaf_photo_1.jpg"))

    val xml = XFormsXmlSerializer.serializeRecordInstance(session.state.recordInstance)
    assertTrue(xml.contains("<leaf_photo>leaf_photo_1.jpg</leaf_photo>"), xml)
    assertFalse(xml.contains("jpeg-bytes"))
  }

  @Test
  fun attachMedia_wrongMimeType_isRejected() {
    val session = newSession()

    assertFailsWith<MediaAttachmentRejectedException> {
      session.attachMedia(
        "/data/walkthrough_video",
        MediaAttachment("clip.m4a", "audio/mp4", "x".encodeUtf8()),
      )
    }
    assertTrue(session.attachments.isEmpty())
  }

  @Test
  fun attachMedia_nonMediaQuestion_isRejected() {
    val session = newSession()

    assertFailsWith<MediaAttachmentRejectedException> {
      session.attachMedia("/data/include_extras", jpeg("x.jpg"))
    }
    assertTrue(session.attachments.isEmpty())
  }

  @Test
  fun attachMedia_pathTraversalFileName_isRejected() {
    val session = newSession()

    assertFailsWith<MediaAttachmentRejectedException> {
      session.attachMedia("/data/leaf_photo", jpeg("../escape.jpg"))
    }
    assertTrue(session.attachments.isEmpty())
  }

  @Test
  fun attachMedia_matchingAudioAndVideo_areAccepted() {
    val session = newSession()
    val audio = MediaAttachment("talk.weba", "audio/webm;codecs=opus", "opus".encodeUtf8())
    val video = MediaAttachment("walk.mp4", "video/mp4", "mp4".encodeUtf8(), durationMillis = 4200)

    session.attachMedia("/data/interview_audio", audio)
    session.attachMedia("/data/walkthrough_video", video)

    assertEquals(setOf("talk.weba", "walk.mp4"), session.attachments.keys)
  }

  @Test
  fun attachMedia_retake_discardsReplacedAttachment() {
    val session = newSession()
    session.attachMedia("/data/leaf_photo", jpeg("first.jpg"))

    session.attachMedia("/data/leaf_photo", jpeg("second.jpg"))

    assertEquals(setOf("second.jpg"), session.attachments.keys)
  }

  @Test
  fun removeMedia_discardsAttachment() {
    val session = newSession()
    session.attachMedia("/data/leaf_photo", jpeg("first.jpg"))

    session.removeMedia("/data/leaf_photo")

    assertTrue(session.attachments.isEmpty())
    assertNull(session.attachmentFor("/data/leaf_photo"))
  }

  @Test
  fun removeMedia_clearsAnswer() {
    val session = newSession()
    session.attachMedia("/data/leaf_photo", jpeg("first.jpg"))

    session.removeMedia("/data/leaf_photo")

    assertTrue(assertNotNull(session.state.findFieldState("/data/leaf_photo")).isEmpty)
  }

  @Test
  fun referencedAttachments_relevantQuestions_areAllIncluded() {
    val session = sessionWithLeafAndExtraPhotos()

    assertEquals(
      setOf("leaf.jpg", "extra.jpg"),
      session.referencedAttachments().map { it.fileName }.toSet(),
    )
  }

  @Test
  fun referencedAttachments_excludesNonRelevantQuestions() {
    val session = sessionWithLeafAndExtraPhotos()

    session.updateString("/data/include_extras", "no")

    assertEquals(listOf("leaf.jpg"), session.referencedAttachments().map { it.fileName })
  }

  @Test
  fun nonRelevantMediaQuestion_keepsAttachmentBytes() {
    // Toggling relevance off keeps the bytes (the answer comes back if toggled on again) but
    // leaves them out of the submission set.
    val session = sessionWithLeafAndExtraPhotos()

    session.updateString("/data/include_extras", "no")

    assertTrue("extra.jpg" in session.attachments)
  }

  private fun sessionWithLeafAndExtraPhotos(): FormSession {
    val session = newSession()
    session.updateString("/data/include_extras", "yes")
    session.attachMedia("/data/leaf_photo", jpeg("leaf.jpg"))
    session.attachMedia("/data/extra_photo", jpeg("extra.jpg"))
    return session
  }

  @Test
  fun resumedDraft_restoresSavedAttachment() {
    val saved = MediaAttachment("leaf.jpg", "image/jpeg", "leaf".encodeUtf8())

    val session = resumedDraftSession(existingAttachments = listOf(saved))

    assertNotNull(session.attachmentFor("/data/leaf_photo"))
    assertEquals(listOf(saved), session.referencedAttachments())
  }

  @Test
  fun resumedDraft_reportsMissingAttachmentBytes() {
    val saved = MediaAttachment("leaf.jpg", "image/jpeg", "leaf".encodeUtf8())

    val session = resumedDraftSession(existingAttachments = listOf(saved))

    assertEquals(setOf("walk.mp4"), session.missingAttachmentFileNames())
  }

  /** Resumes a draft answering `leaf_photo` (leaf.jpg) and `walkthrough_video` (walk.mp4). */
  private fun resumedDraftSession(existingAttachments: List<MediaAttachment>): FormSession {
    val formDef = XFormsXmlSerializer.deserializeFormDef(mediaFormXml)
    val draftXml =
      """
      <data id="media_capture">
        <include_extras/>
        <leaf_photo>leaf.jpg</leaf_photo>
        <selfie/>
        <walkthrough_video>walk.mp4</walkthrough_video>
        <interview_audio/>
        <extra_photo/>
      </data>
      """
        .trimIndent()
    val record =
      XFormsXmlSerializer.deserializeRecordInstance(
        draftXml,
        formDef.model?.primary_instance?.record_schema,
      )
    return FormSession(formDef, existingRecord = record, existingAttachments = existingAttachments)
  }

  @Test
  fun fromBase64_decodesPayload() {
    val attachment =
      assertNotNull(MediaAttachment.fromBase64("a.png", "image/png", "aGVsbG8=", widthPx = 4))

    assertEquals("hello", attachment.bytes.utf8())
    assertEquals(5L, attachment.sizeBytes)
  }

  @Test
  fun fromBase64_imageMimeType_isPhoto() {
    val attachment =
      assertNotNull(MediaAttachment.fromBase64("a.png", "image/png", "aGVsbG8=", widthPx = 4))

    assertEquals(MediaCaptureKind.PHOTO, attachment.kind)
  }

  @Test
  fun fromBase64_invalidPayload_isNull() {
    assertNull(MediaAttachment.fromBase64("a.png", "image/png", "!!not base64!!"))
  }
}
