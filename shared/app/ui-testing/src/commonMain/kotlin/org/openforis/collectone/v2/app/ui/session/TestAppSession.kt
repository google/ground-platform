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

import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.ui.AppViewModels

/**
 * A client session for tests, built as the hosts build theirs: the feature [viewModels] over
 * [dataHolder], wired together by a [SessionCoordinator] with a shell that shows nothing, the
 * session's [notices], and the list and details intents over the ViewModels.
 */
class TestAppSession(
  val dataHolder: AppDataHolder,
  val viewModels: AppViewModels = AppViewModels.create(dataHolder),
) : AutoCloseable {
  val notices = NoticeController()

  private val coordinator =
    SessionCoordinator(dataHolder, viewModels, notices, shell = object : SessionShell {})

  val searchableListActions = SessionSearchableListActions(viewModels)

  val entityDetailsActions = SessionEntityDetailsActions(viewModels)

  /** What the signed-in user may do in the active survey now. */
  val activeSurveyAccess: ActiveSurveyAccess
    get() = ActiveSurveyAccess.of(dataHolder.appData.value, viewModels.onboarding.uiState.value)

  /** The record surfaces (bottom sheet, list, details) as the mobile app builds them now. */
  val surveyRecords: SurveyRecordsUiStates
    get() =
      surveyRecordsUiStatesOf(
        viewModels = viewModels,
        data = dataHolder.appData.value,
        notice = notices.notice.value,
        isAlwaysOnline = false,
      )

  /** Resets every ViewModel and the notice. */
  fun reset() = coordinator.reset()

  override fun close() = coordinator.close()
}
