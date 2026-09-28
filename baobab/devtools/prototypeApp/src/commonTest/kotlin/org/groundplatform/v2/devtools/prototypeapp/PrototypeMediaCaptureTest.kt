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
