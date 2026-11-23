/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openforis.collectone.v2.web.ui.header

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.SaveStatus

/** How the web console header combines save signals and when it confirms "Saved". */
class WebHeaderSaveStatusTest {
  @Test
  fun nothingSaving_isIdle() {
    assertEquals(SaveStatus.IDLE, headerSaveStatus(isSavingLocally = false, SaveStatus.IDLE))
  }

  @Test
  fun editorOrOrganizationSaving_isSaving() {
    assertEquals(SaveStatus.SAVING, headerSaveStatus(isSavingLocally = true, SaveStatus.IDLE))
    // A local save in progress takes precedence over an earlier failed send.
    assertEquals(SaveStatus.SAVING, headerSaveStatus(isSavingLocally = true, SaveStatus.FAILED))
  }

  @Test
  fun outboxSending_isSaving() {
    assertEquals(SaveStatus.SAVING, headerSaveStatus(isSavingLocally = false, SaveStatus.SAVING))
    assertEquals(
      SaveStatus.SAVING,
      headerSaveStatus(
        isSavingLocally = false,
        SaveStatus.SAVING,
        hasUnacknowledgedRejection = true,
      ),
    )
  }

  @Test
  fun outboxWaitingToRetry_isFailed() {
    assertEquals(SaveStatus.FAILED, headerSaveStatus(isSavingLocally = false, SaveStatus.FAILED))
  }

  @Test
  fun unacknowledgedRejection_isFailed() {
    assertEquals(
      SaveStatus.FAILED,
      headerSaveStatus(
        isSavingLocally = false,
        SaveStatus.IDLE,
        hasUnacknowledgedRejection = true,
      ),
    )
  }

  @Test
  fun savedIsConfirmed_onlyWhenASaveFinishesWithoutFailing() {
    assertTrue(showsSavedConfirmation(SaveStatus.SAVING, SaveStatus.IDLE))

    assertFalse(showsSavedConfirmation(SaveStatus.SAVING, SaveStatus.FAILED))
    assertFalse(showsSavedConfirmation(SaveStatus.FAILED, SaveStatus.IDLE))
    assertFalse(showsSavedConfirmation(SaveStatus.IDLE, SaveStatus.IDLE))
    assertFalse(showsSavedConfirmation(SaveStatus.IDLE, SaveStatus.SAVING))
    assertFalse(showsSavedConfirmation(SaveStatus.FAILED, SaveStatus.SAVING))
  }
}
