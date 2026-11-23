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
package org.openforis.collectone.v2.app.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem

/** The English names of feature types, as the app wrote them when it stored English. */
class FeatureTypeTextTest {
  private fun english(text: UiText): String = runBlocking { text.resolveString() }

  private fun submission(entityId: String, targetTypeNoun: String) =
    SubmissionPreviewItem(
      id = "s1",
      entityId = entityId,
      formId = "f1",
      formTitle = "Form",
      formVersion = "1",
      collectorName = "Amina Otieno",
      collectorEmail = "amina@example.org",
      fields = emptyList(),
      targetTypeNoun = targetTypeNoun,
    )

  @Test
  fun authorNoun_isShownAsTyped() {
    assertEquals("Coffee Parcel", english(featureTypeText("Coffee Parcel")))
  }

  @Test
  fun blankNoun_isLocation() {
    assertEquals("Location", english(featureTypeText("")))
  }

  @Test
  fun submissionOfAFeature_isNamedByTheFeatureType() {
    assertEquals("Coffee Parcel", english(submission("e1", "Coffee Parcel").targetTypeText))
    assertEquals("Location", english(submission("e1", "").targetTypeText))
  }

  @Test
  fun submissionWithoutAFeature_isAStandaloneFieldLog() {
    assertEquals("Standalone Field Log", english(submission("", "").targetTypeText))
  }
}
