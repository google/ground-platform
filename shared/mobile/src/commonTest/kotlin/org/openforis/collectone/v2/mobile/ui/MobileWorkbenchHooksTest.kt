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
package org.openforis.collectone.v2.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute
import org.openforis.collectone.v2.mobile.ui.state.MobileWorkbenchHooks

/** What the mobile app tells the workbench it runs in. */
class MobileWorkbenchHooksTest {
  /** Records what the app asked of the workbench. */
  private class RecordingWorkbenchHooks : MobileWorkbenchHooks {
    val calls = mutableListOf<String>()

    override fun onSurveyActivated(surveyId: String) {
      calls += "activated $surveyId"
    }

    override fun cycleEntitySyncStatus(entityId: String) {
      calls += "cycle entity $entityId"
    }

    override fun cycleSubmissionSyncStatus(submissionId: String) {
      calls += "cycle submission $submissionId"
    }

    override fun retryFailedUploads() {
      calls += "retry"
    }
  }

  private val state =
    MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
  private val hooks = RecordingWorkbenchHooks().also { state.workbenchHooks = it }

  @Test
  fun openingASurvey_tellsTheWorkbenchItIsActive() {
    state.viewModels.onboarding.openSurvey(MEMBER_SURVEY)

    assertEquals(listOf("activated $MEMBER_SURVEY"), hooks.calls)
  }

  @Test
  fun cycleSubmissionSyncStatus_isForwardedToTheWorkbench() {
    state.entityDetailsActions.cycleSubmissionSyncStatus("submission-1")

    assertEquals(listOf("cycle submission submission-1"), hooks.calls)
  }

  private companion object {
    const val MEMBER_SURVEY = "00000035-caf4-571e-b907-f506f92666ab"
  }
}
