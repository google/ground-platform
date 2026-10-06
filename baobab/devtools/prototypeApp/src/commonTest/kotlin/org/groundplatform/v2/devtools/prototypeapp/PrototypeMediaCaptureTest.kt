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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.media.MediaCaptureKind
import org.groundplatform.v2.core.forms.media.MediaCaptureSpec
import org.groundplatform.v2.core.forms.ui.FormWizardStep
import org.groundplatform.v2.core.forms.ui.MediaCaptureRequest
import org.groundplatform.v2.core.forms.ui.MediaCaptureResult
import org.groundplatform.v2.core.forms.ui.MediaCaptureSource
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm

class PrototypeMediaCaptureTest {

  @Test
  fun browserPickResult_mapsBridgeCallbackArguments() {
    assertEquals(PlatformPickResult.Cancelled, browserPickResult("", "", "", -1.0, -1.0, -1.0, ""))
    assertEquals(
      PlatformPickResult.Failed("Could not read a.jpg"),
      browserPickResult("", "", "", -1.0, -1.0, -1.0, "Could not read a.jpg"),
    )
    val picked =
      assertIs<PlatformPickResult.Picked>(
        browserPickResult("clip.webm", "video/webm", "aGk=", 4200.0, 1280.0, 720.0, "")
      )
    assertEquals(4200L, picked.file.durationMillis)
    assertEquals(1280, picked.file.widthPx)
    val audio =
      assertIs<PlatformPickResult.Picked>(
        browserPickResult("a.m4a", "audio/mp4", "aGk=", 900.0, -1.0, -1.0, "")
      )
    assertNull(audio.file.widthPx)
  }

  @Test
  fun handler_fallsBackToSimulatedCaptureOffWeb() {
    val handler = PrototypeMediaCaptureHandler()
    var result: MediaCaptureResult? = null
    handler.launch(
      MediaCaptureRequest(
        fieldPath = "/data/farmer_interview_audio",
        spec = MediaCaptureSpec(kind = MediaCaptureKind.AUDIO, acceptedMediaType = "audio/*"),
        source = MediaCaptureSource.CAPTURE,
        suggestedFileName = "farmer_interview_audio_1.m4a",
      )
    ) {
      result = it
    }
    val captured = assertIs<MediaCaptureResult.Captured>(result)
    assertEquals("audio/mp4", captured.attachment.mimeType)
  }

  @Test
  fun chooseCapturePath_neverDegradesCaptureToFilePicker() {
    val capture = MediaCaptureSource.CAPTURE
    val existing = MediaCaptureSource.EXISTING_FILE
    val photo = MediaCaptureKind.PHOTO

    // Mobile browser: HTML Media Capture opens the native camera.
    assertEquals(
      CapturePath.FILE_INPUT_CAPTURE,
      chooseCapturePath(capture, photo, prefersFileInput = true, liveAvailable = true, true),
    )
    // Desktop browser with getUserMedia: in-page live overlay.
    assertEquals(
      CapturePath.LIVE_CAPTURE,
      chooseCapturePath(capture, photo, prefersFileInput = false, liveAvailable = true, true),
    )
    // Desktop browser without getUserMedia: fail rather than open a file picker.
    assertEquals(
      CapturePath.UNAVAILABLE,
      chooseCapturePath(capture, photo, prefersFileInput = false, liveAvailable = false, true),
    )
    // Mobile UA but no picker bridge at all: live capture if possible.
    assertEquals(
      CapturePath.LIVE_CAPTURE,
      chooseCapturePath(capture, photo, prefersFileInput = true, liveAvailable = true, false),
    )
    // Generic files are never captured live.
    assertEquals(
      CapturePath.UNAVAILABLE,
      chooseCapturePath(capture, MediaCaptureKind.FILE, false, liveAvailable = true, true),
    )
    // Existing files always go through the picker when one exists.
    for (kind in MediaCaptureKind.entries) {
      assertEquals(
        CapturePath.FILE_PICKER,
        chooseCapturePath(existing, kind, prefersFileInput = false, liveAvailable = false, true),
      )
      assertEquals(
        CapturePath.UNAVAILABLE,
        chooseCapturePath(existing, kind, prefersFileInput = true, liveAvailable = true, false),
      )
    }
  }

  @Test
  fun handler_supportsReflectsCapturePaths() {
    val desktopNoCamera =
      PrototypeMediaCaptureHandler(
        pickerAvailable = true,
        liveAvailable = false,
        prefersFileInput = false,
      )
    assertFalse(desktopNoCamera.supports(MediaCaptureKind.PHOTO, MediaCaptureSource.CAPTURE))
    assertTrue(desktopNoCamera.supports(MediaCaptureKind.PHOTO, MediaCaptureSource.EXISTING_FILE))

    val desktopWithCamera =
      PrototypeMediaCaptureHandler(
        pickerAvailable = true,
        liveAvailable = true,
        prefersFileInput = false,
      )
    assertTrue(desktopWithCamera.supports(MediaCaptureKind.VIDEO, MediaCaptureSource.CAPTURE))
    assertFalse(desktopWithCamera.supports(MediaCaptureKind.FILE, MediaCaptureSource.CAPTURE))
    assertTrue(desktopWithCamera.supports(MediaCaptureKind.FILE, MediaCaptureSource.EXISTING_FILE))

    val mobile =
      PrototypeMediaCaptureHandler(
        pickerAvailable = true,
        liveAvailable = false,
        prefersFileInput = true,
      )
    assertTrue(mobile.supports(MediaCaptureKind.AUDIO, MediaCaptureSource.CAPTURE))
  }

  @Test
  fun handler_failsCaptureOnlyRequestWhenBrowserCannotCapture() {
    val handler =
      PrototypeMediaCaptureHandler(
        pickerAvailable = true,
        liveAvailable = false,
        prefersFileInput = false,
      )
    var result: MediaCaptureResult? = null
    handler.launch(
      MediaCaptureRequest(
        fieldPath = "/data/leaf_voucher_photo",
        spec =
          MediaCaptureSpec(
            kind = MediaCaptureKind.PHOTO,
            acceptedMediaType = "image/*",
            requireNewCapture = true,
          ),
        source = MediaCaptureSource.CAPTURE,
        suggestedFileName = "leaf_voucher_photo_1.jpg",
      )
    ) {
      result = it
    }
    val failed = assertIs<MediaCaptureResult.Failed>(result)
    assertEquals("This browser can't capture a photo directly.", failed.message)
  }

  @Test
  fun allFieldTypesForm_launchesWithPhotoVideoAndAudioQuestions() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    state.selectWorkbenchExampleForm(WorkbenchExampleForm.ALL_FIELD_TYPES, launchImmediately = true)
    val kinds =
      state.activeFormWizardController!!
        .steps
        .filterIsInstance<FormWizardStep.QuestionStep>()
        .mapNotNull { it.control.mediaCapture?.kind }
    assertTrue(
      kinds.containsAll(
        listOf(MediaCaptureKind.PHOTO, MediaCaptureKind.VIDEO, MediaCaptureKind.AUDIO)
      ),
      kinds.toString(),
    )
  }
}
