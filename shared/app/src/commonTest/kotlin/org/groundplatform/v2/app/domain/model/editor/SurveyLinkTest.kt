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
package org.groundplatform.v2.app.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [SurveyAccess.surveyIdFromLink] reads survey IDs from share links and pasted IDs. */
class SurveyLinkTest {
  private val id = "00000033-6b50-51cd-ac94-baea2ea06d65"

  @Test
  fun shareLink_roundTrips() {
    assertEquals(id, SurveyAccess.surveyIdFromLink(SurveyAccess.surveyUrl(id)))
  }

  @Test
  fun linksFromAnyHost_withQueryFragmentOrTrailingSlash_areRecognized() {
    assertEquals(id, SurveyAccess.surveyIdFromLink("  http://localhost:8092/surveys/$id/  "))
    assertEquals(id, SurveyAccess.surveyIdFromLink("https://example.com/app/surveys/$id?ref=qr"))
    assertEquals(id, SurveyAccess.surveyIdFromLink("https://example.com/surveys/$id#map"))
  }

  @Test
  fun bareSurveyId_isRecognized() {
    assertEquals(id, SurveyAccess.surveyIdFromLink(id))
  }

  @Test
  fun searchTextAndOtherLinks_areNotSurveyLinks() {
    assertNull(SurveyAccess.surveyIdFromLink("Kenya"))
    assertNull(SurveyAccess.surveyIdFromLink("https://example.com/join/k7q2-mx4p"))
    assertNull(SurveyAccess.surveyIdFromLink("https://example.com/surveys/draft"))
    assertNull(SurveyAccess.surveyIdFromLink("https://example.com/other/$id"))
  }
}
