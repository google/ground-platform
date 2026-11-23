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
package org.openforis.collectone.v2.app.data.datasource.local.session

/**
 * Keeps the ID of the survey the user last opened on this device between launches, so a relaunch (a
 * reload of the mobile app in the browser, or Android restoring the app after process death)
 * reopens that survey.
 */
interface ActiveSurveyStore {
  /** The stored survey ID, or null when none is stored. */
  suspend fun load(): String?

  /** Replaces the stored survey ID with [surveyId]. */
  suspend fun save(surveyId: String)

  /** Forgets the stored survey ID (signing out, or the survey was removed from the device). */
  suspend fun clear()
}

/**
 * [ActiveSurveyStore] that keeps the survey ID only while the app runs, for hosts without a local
 * database (the web console, tests, the workbench).
 */
class InMemoryActiveSurveyStore(private var stored: String? = null) : ActiveSurveyStore {
  override suspend fun load(): String? = stored

  override suspend fun save(surveyId: String) {
    stored = surveyId
  }

  override suspend fun clear() {
    stored = null
  }
}
