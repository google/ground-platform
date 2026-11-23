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
package org.openforis.collectone.v2.mobile.ui.state

import org.openforis.collectone.v2.mobile.ui.MobileAppState

/**
 * The mobile workbench's hooks into the mobile app, set on [MobileAppState.workbenchHooks] only
 * when the app runs inside the workbench. Outside it, uploads are real and nothing calls these.
 */
interface MobileWorkbenchHooks {
  /** The app made survey [surveyId] active (the workbench loads its primary form). */
  fun onSurveyActivated(surveyId: String)

  /** Cycles the simulated sync status of map feature [entityId] from its sync status badge. */
  fun cycleEntitySyncStatus(entityId: String)

  /** Cycles the simulated sync status of submission [submissionId] from its sync status badge. */
  fun cycleSubmissionSyncStatus(submissionId: String)

  /** Retries the failed uploads by cycling the simulated status of the failed records. */
  fun retryFailedUploads()
}
