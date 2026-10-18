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
import groundplatform.v2.forms.TypedValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.ByteString.Companion.encodeUtf8
import org.groundplatform.v2.core.forms.engine.FormSession
import org.groundplatform.v2.core.forms.model.ComponentState
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer

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

  @Test
  fun resolveSpec_derivesKindAndOptionsFromXFormsUploadControls() {
    val controls = mediaControls(newSession())
    assertEquals(
      setOf("leaf_photo", "selfie", "walkthrough_video", "interview_audio", "extra_photo"),
      controls.keys,
    )

    val photo = controls.getValue("leaf_photo").mediaCapture!!
    assertEquals(MediaCaptureKind.PHOTO, photo.kind)
    assertEquals("image/*", photo.acceptedMediaType)
    assertEquals(1024, photo.maxPixels)
    assertFalse(photo.requireNewCapture)
    assertTrue(photo.allowsExistingFile)

    val selfie = controls.getValue("selfie").mediaCapture!!
    assertTrue(selfie.preferFrontCamera)
    assertTrue(selfie.requireNewCapture)
    assertNull(selfie.maxPixels)

    val video = controls.getValue("walkthrough_video").mediaCapture!!
    assertEquals(MediaCaptureKind.VIDEO, video.kind)
    assertTrue(video.requireNewCapture)
    assertFalse(video.preferFrontCamera)

    assertEquals(MediaCaptureKind.AUDIO, controls.getValue("interview_audio").mediaCapture!!.kind)
  }

  @Test
  fun resolveSpec_returnsNullForNonUploadAndDefaultsToPhotoWithoutMediatype() {
    assertNull(MediaCapture.resolveSpec(ControlDef(type = ControlType.CONTROL_INPUT), null))
    val spec =
      MediaCapture.resolveSpec(
        ControlDef(type = ControlType.CONTROL_UPLOAD),
        FieldBinding(max_pixels = 640),
      )!!
    assertEquals(MediaCaptureKind.PHOTO, spec.kind)
    assertEquals("image/*", spec.acceptedMediaType)
    assertEquals(640, spec.maxPixels)

    // max-pixels only applies to photos.
    val audio =
      MediaCapture.resolveSpec(
        ControlDef(type = ControlType.CONTROL_UPLOAD, media_type = "audio/*"),
        FieldBinding(max_pixels = 640),
      )!!
    assertNull(audio.maxPixels)
    assertEquals(
      MediaCaptureKind.FILE,
      MediaCapture.resolveSpec(
          ControlDef(type = ControlType.CONTROL_UPLOAD, media_type = "application/pdf"),
          null,
        )!!
        .kind,
    )
  }

  @Test
  fun mediaTypeMatches_handlesWildcardsListsParametersAndCase() {
    assertTrue(MediaCapture.mediaTypeMatches("image/*", "image/jpeg"))
    assertTrue(MediaCapture.mediaTypeMatches("IMAGE/*", "image/PNG"))
    assertFalse(MediaCapture.mediaTypeMatches("image/*", "video/mp4"))
    assertTrue(MediaCapture.mediaTypeMatches("audio/*", "audio/webm;codecs=opus"))
    assertTrue(MediaCapture.mediaTypeMatches("video/mp4, video/webm", "video/webm"))
    assertFalse(MediaCapture.mediaTypeMatches("video/mp4", "video/webm"))
    assertTrue(MediaCapture.mediaTypeMatches("*/*", "application/pdf"))
    assertTrue(MediaCapture.mediaTypeMatches("", "application/pdf"))
    assertFalse(MediaCapture.mediaTypeMatches("image/*", ""))
  }

  @Test
  fun fileNamesAndExtensions() {
    assertEquals("jpg", MediaCapture.extensionForMimeType("image/jpeg"))
    assertEquals("weba", MediaCapture.extensionForMimeType("audio/webm;codecs=opus"))
    assertEquals("mp4", MediaCapture.extensionForMimeType("video/x-unknown"))
    assertEquals("bin", MediaCapture.extensionForMimeType("application/x-unknown"))
    assertEquals("image/jpeg", MediaCapture.mimeTypeForFileName("IMG_1.JPEG"))
    assertEquals("video/webm", MediaCapture.mimeTypeForFileName("clip.webm"))
    assertNull(MediaCapture.mimeTypeForFileName("README"))
    assertEquals(
      "leaf_photo_1727465921000.jpg",
      MediaCapture.generateFileName("/data/plots[2]/leaf_photo", "image/jpeg", 1727465921000L),
    )
    assertEquals("my_field_5.m4a", MediaCapture.generateFileName("/data/my field", "audio/mp4", 5L))
  }

  @Test
  fun attachmentFileName_readsBinaryOrStringValues() {
    assertNull(MediaCapture.attachmentFileName(null))
    assertEquals(
      "a.jpg",
      MediaCapture.attachmentFileName(
        FieldValue(scalar_value = TypedValue(binary_value = "a.jpg".encodeUtf8()))
      ),
    )
    assertEquals(
      "b.jpg",
      MediaCapture.attachmentFileName(
        FieldValue(scalar_value = TypedValue(string_value = " b.jpg "))
      ),
    )
    assertNull(
      MediaCapture.attachmentFileName(FieldValue(scalar_value = TypedValue(string_value = "")))
    )
  }

  @Test
  fun attachMedia_storesFileNameInRecordAndBytesInSession() {
    val session = newSession()
    assertTrue(session.state.validationErrors.any { it.fieldPath == "/data/leaf_photo" })

    val photo = jpeg("leaf_photo_1.jpg")
    session.attachMedia("/data/leaf_photo", photo)

    val fieldState = session.state.findFieldState("/data/leaf_photo")!!
    assertEquals("leaf_photo_1.jpg", MediaCapture.attachmentFileName(fieldState.value))
    assertFalse(session.state.validationErrors.any { it.fieldPath == "/data/leaf_photo" })
    assertEquals(photo, session.attachmentFor("/data/leaf_photo"))
    assertEquals(listOf(photo), session.referencedAttachments())

    // OpenRosa: the submission XML carries only the file name.
    val xml = XFormsXmlSerializer.serializeRecordInstance(session.state.recordInstance)
    assertTrue(xml.contains("<leaf_photo>leaf_photo_1.jpg</leaf_photo>"), xml)
    assertFalse(xml.contains("jpeg-bytes"))
  }

  @Test
  fun attachMedia_rejectsWrongMimeTypeAndNonMediaQuestions() {
    val session = newSession()
    assertFailsWith<MediaAttachmentRejectedException> {
      session.attachMedia(
        "/data/walkthrough_video",
        MediaAttachment("clip.m4a", "audio/mp4", "x".encodeUtf8()),
      )
    }
    assertFailsWith<MediaAttachmentRejectedException> {
      session.attachMedia("/data/include_extras", jpeg("x.jpg"))
    }
    assertFailsWith<MediaAttachmentRejectedException> {
      session.attachMedia("/data/leaf_photo", jpeg("../escape.jpg"))
    }
    assertTrue(session.attachments.isEmpty())

    val audio = MediaAttachment("talk.weba", "audio/webm;codecs=opus", "opus".encodeUtf8())
    session.attachMedia("/data/interview_audio", audio)
    val video = MediaAttachment("walk.mp4", "video/mp4", "mp4".encodeUtf8(), durationMillis = 4200)
    session.attachMedia("/data/walkthrough_video", video)
    assertEquals(setOf("talk.weba", "walk.mp4"), session.attachments.keys)
  }

  @Test
  fun retakeAndRemove_discardOrphanedAttachments() {
    val session = newSession()
    session.attachMedia("/data/leaf_photo", jpeg("first.jpg"))
    session.attachMedia("/data/leaf_photo", jpeg("second.jpg"))
    assertEquals(setOf("second.jpg"), session.attachments.keys)

    session.removeMedia("/data/leaf_photo")
    assertTrue(session.attachments.isEmpty())
    assertNull(session.attachmentFor("/data/leaf_photo"))
    assertTrue(session.state.findFieldState("/data/leaf_photo")!!.isEmpty)
  }

  @Test
  fun referencedAttachments_excludesNonRelevantQuestions() {
    val session = newSession()
    session.updateString("/data/include_extras", "yes")
    session.attachMedia("/data/leaf_photo", jpeg("leaf.jpg"))
    session.attachMedia("/data/extra_photo", jpeg("extra.jpg"))
    assertEquals(
      setOf("leaf.jpg", "extra.jpg"),
      session.referencedAttachments().map { it.fileName }.toSet(),
    )

    // Toggling relevance off keeps the bytes (the answer comes back if toggled on again) but
    // leaves them out of the submission set.
    session.updateString("/data/include_extras", "no")
    assertEquals(listOf("leaf.jpg"), session.referencedAttachments().map { it.fileName })
    assertTrue("extra.jpg" in session.attachments)
  }

  @Test
  fun resumedDraft_reportsMissingAttachmentBytes() {
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
    val saved = MediaAttachment("leaf.jpg", "image/jpeg", "leaf".encodeUtf8())
    val session = FormSession(formDef, existingRecord = record, existingAttachments = listOf(saved))

    assertNotNull(session.attachmentFor("/data/leaf_photo"))
    assertEquals(listOf(saved), session.referencedAttachments())
    assertEquals(setOf("walk.mp4"), session.missingAttachmentFileNames())
  }

  @Test
  fun fromBase64_decodesPayload() {
    val attachment = MediaAttachment.fromBase64("a.png", "image/png", "aGVsbG8=", widthPx = 4)!!
    assertEquals("hello", attachment.bytes.utf8())
    assertEquals(5L, attachment.sizeBytes)
    assertEquals(MediaCaptureKind.PHOTO, attachment.kind)
    assertNull(MediaAttachment.fromBase64("a.png", "image/png", "!!not base64!!"))
  }
}
