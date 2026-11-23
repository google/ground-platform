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
package org.openforis.collectone.v2.web.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.openforis.collectone.v2.app.domain.model.InvitationStatus
import org.openforis.collectone.v2.app.domain.model.editor.Collaborator
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.web.domain.usecase.InviteCollaboratorUseCase
import org.openforis.collectone.v2.web.domain.usecase.InviteCollaboratorUseCase.Outcome

/** The English text of the refusals on a survey's access list. */
class SurveyAccessTextTest {
  private val useCase = InviteCollaboratorUseCase(newInviteToken = { "token" })
  private val sharing =
    SharingSettings(
      ownerEmail = "owner@example.org",
      collaborators =
        listOf(
          Collaborator("pending@example.org", CollaboratorRole.DATA_COLLECTOR, inviteToken = "t"),
          Collaborator(
            "joined@example.org",
            CollaboratorRole.DATA_COLLECTOR,
            status = InvitationStatus.ACCEPTED,
          ),
        ),
    )

  private fun english(outcome: Outcome): String = runBlocking {
    getString(assertIs<Outcome.Rejected>(outcome).reason.messageRes)
  }

  @Test
  fun inviteRefusals() {
    assertEquals(
      "Enter a valid email address.",
      english(useCase.invite(sharing, "not an email", CollaboratorRole.DATA_COLLECTOR)),
    )
    assertEquals(
      "That's the survey owner.",
      english(useCase.invite(sharing, "Owner@Example.org", CollaboratorRole.DATA_COLLECTOR)),
    )
  }

  @Test
  fun acceptRefusals() {
    assertEquals(
      "Enter a name.",
      english(useCase.acceptInvite(sharing, "pending@example.org", " ", null)),
    )
    assertEquals(
      "Not invited.",
      english(useCase.acceptInvite(sharing, "stranger@example.org", "Sam", null)),
    )
    assertEquals(
      "Already joined.",
      english(useCase.acceptInvite(sharing, "joined@example.org", "Jo", null)),
    )
  }
}
