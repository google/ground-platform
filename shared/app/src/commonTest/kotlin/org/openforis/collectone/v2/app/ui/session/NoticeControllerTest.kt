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
package org.openforis.collectone.v2.app.ui.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.ui.text.UiText

/** A localized notice that is still being resolved never overrides a later notice or clear. */
@OptIn(ExperimentalCoroutinesApi::class)
class NoticeControllerTest {
  private val dispatcher = StandardTestDispatcher()
  private val notices = NoticeController(TestScope(dispatcher))

  @Test
  fun localizedNotice_isShownOnceResolved() =
    runTest(dispatcher) {
      notices.show(UiText.Plain("Centered map"))
      advanceUntilIdle()

      assertEquals("Centered map", notices.notice.value)
    }

  @Test
  fun clear_winsOverANoticeStillBeingResolved() =
    runTest(dispatcher) {
      notices.show(UiText.Plain("Straight-line navigation"))
      notices.clear()
      advanceUntilIdle()

      assertNull(notices.notice.value)
    }

  @Test
  fun laterNotice_winsOverANoticeStillBeingResolved() =
    runTest(dispatcher) {
      notices.show(UiText.Plain("Straight-line navigation"))
      notices.show("Uploaded")
      advanceUntilIdle()

      assertEquals("Uploaded", notices.notice.value)
    }
}
