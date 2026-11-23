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
package org.openforis.collectone.v2.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ContributorVisibilityTest {
  private val samuelsSubmission =
    SubmissionPreviewItem(
      id = "submission-1",
      formId = "form-1",
      formTitle = "Plot visit",
      formVersion = "1",
      collectorName = "Samuel Kariuki",
      collectorEmail = "s.kariuki@example.org",
      fields = emptyList(),
    )

  @Test
  fun organizerSeesAnotherContributorsIdentity() {
    val organizer = SubmissionViewer(email = "maya@example.org", isOrganizer = true)

    assertEquals(
      ContributorVisibility.NAMED,
      ContributorVisibility.resolve(organizer, samuelsSubmission),
    )
  }

  @Test
  fun organizerSeesTheirOwnIdentityInFull() {
    val samuelAsOrganizer = SubmissionViewer(email = "s.kariuki@example.org", isOrganizer = true)

    assertEquals(
      ContributorVisibility.NAMED,
      ContributorVisibility.resolve(samuelAsOrganizer, samuelsSubmission),
    )
  }

  @Test
  fun contributorSeesTheirOwnSubmissionAsYou() {
    val samuel = SubmissionViewer(email = "s.kariuki@example.org", isOrganizer = false)

    assertEquals(
      ContributorVisibility.YOU,
      ContributorVisibility.resolve(samuel, samuelsSubmission),
    )
  }

  @Test
  fun ownSubmissionMatchesEmailIgnoringCase() {
    val samuel = SubmissionViewer(email = "S.Kariuki@Example.org", isOrganizer = false)

    assertEquals(
      ContributorVisibility.YOU,
      ContributorVisibility.resolve(samuel, samuelsSubmission),
    )
  }

  @Test
  fun nonOrganizerDoesNotSeeAnotherContributorsIdentity() {
    val maya = SubmissionViewer(email = "maya@example.org", isOrganizer = false)

    assertEquals(
      ContributorVisibility.HIDDEN,
      ContributorVisibility.resolve(maya, samuelsSubmission),
    )
  }

  @Test
  fun signedOutViewerDoesNotSeeASubmissionWithoutCollectorEmail() {
    val signedOut = SubmissionViewer(email = "", isOrganizer = false)
    val anonymousSubmission = samuelsSubmission.copy(collectorEmail = "")

    assertEquals(
      ContributorVisibility.HIDDEN,
      ContributorVisibility.resolve(signedOut, anonymousSubmission),
    )
  }
}
