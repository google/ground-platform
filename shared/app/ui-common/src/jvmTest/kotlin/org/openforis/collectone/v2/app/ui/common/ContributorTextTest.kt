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
package org.openforis.collectone.v2.app.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SubmissionViewer
import org.openforis.collectone.v2.app.ui.text.UiText

/**
 * How [collectorNameText] and [collectorAttributionText] format contributor identity per viewer.
 */
class ContributorTextTest {
  private val submission =
    SubmissionPreviewItem(
      id = "s1",
      formId = "f1",
      formTitle = "Tree Census",
      formVersion = "1",
      collectorName = "Amina Otieno",
      collectorEmail = "amina@example.org",
      fields = emptyList(),
    )

  private fun english(text: UiText): String = runBlocking { text.resolveString() }

  @Test
  fun peerNonOrganizer_hidesBothNameAndAttribution() {
    val viewer = SubmissionViewer(email = "peer@example.org", isOrganizer = false)

    assertEquals("Participant (identity hidden)", english(collectorNameText(viewer, submission)))
    assertEquals(
      "Participant (identity hidden)",
      english(collectorAttributionText(viewer, submission)),
    )
  }

  @Test
  fun ownSubmissionNonOrganizer_showsNameMarkedAsYouWithoutEmail() {
    val viewer = SubmissionViewer(email = "AMINA@example.org", isOrganizer = false)

    assertEquals("Amina Otieno (You)", english(collectorNameText(viewer, submission)))
    assertEquals("Amina Otieno (You)", english(collectorAttributionText(viewer, submission)))
  }

  @Test
  fun organizer_showsPlainNameInListsAndNameWithEmailInDetails() {
    val viewer = SubmissionViewer(email = "organizer@example.org", isOrganizer = true)

    assertEquals("Amina Otieno", english(collectorNameText(viewer, submission)))
    assertEquals(
      "Amina Otieno (amina@example.org)",
      english(collectorAttributionText(viewer, submission)),
    )
  }
}
