/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.app.domain.repository

import org.groundplatform.v2.app.domain.model.SurveyPreviewItem

/**
 * Domain repository contract for finding surveys the survey list doesn't include, and downloading
 * them: public surveys (`SearchPublicSurveys`) and surveys opened from a share link
 * (`BatchGetSurveyOverviews`). Lookups need a connection to the user data service.
 */
interface SurveyDirectoryRepository {
  /** Public surveys whose title or description matches [query]. */
  suspend fun searchPublicSurveys(query: String): List<SurveyPreviewItem>

  /** The survey with [surveyId], or `null` when it doesn't exist or the user can't open it. */
  suspend fun lookUpSurvey(surveyId: String): SurveyPreviewItem?

  /**
   * Adds [survey], found by [searchPublicSurveys] or [lookUpSurvey], to the device's downloaded
   * surveys. The next sync fetches its definition and data.
   */
  suspend fun downloadSurvey(survey: SurveyPreviewItem)
}
