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
package org.openforis.collectone.v2.web.ui.surveyeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy

/** Sharing intents of the Survey editor: collaborators, roles, and the access policy. */
class SurveyEditorSharingTest {

  @Test
  fun removeCollaborator_dropsOnlyThatPerson() {
    val viewModel = surveyEditorViewModel()
    viewModel.inviteCollaborator("first@example.org", CollaboratorRole.VIEWER)
    viewModel.inviteCollaborator("second@example.org", CollaboratorRole.VIEWER)

    viewModel.removeCollaborator("first@example.org")

    val emails = viewModel.ui.sharing.collaborators.map { it.email }
    assertFalse("first@example.org" in emails)
    assertTrue("second@example.org" in emails)
  }

  @Test
  fun removeCollaborator_ofSomeoneNotOnTheSurvey_leavesTheEditorUntouched() {
    val viewModel = surveyEditorViewModel()

    viewModel.removeCollaborator("stranger@example.org")

    assertFalse(viewModel.ui.isDirty)
  }

  @Test
  fun setCollaboratorRole_changesOnlyThatPerson() {
    val viewModel = surveyEditorViewModel()
    viewModel.inviteCollaborator("first@example.org", CollaboratorRole.VIEWER)
    viewModel.inviteCollaborator("second@example.org", CollaboratorRole.VIEWER)

    viewModel.setCollaboratorRole("first@example.org", CollaboratorRole.DATA_COLLECTOR)

    val roles = viewModel.ui.sharing.collaborators.associate { it.email to it.role }
    assertEquals(CollaboratorRole.DATA_COLLECTOR, roles["first@example.org"])
    assertEquals(CollaboratorRole.VIEWER, roles["second@example.org"])
  }

  @Test
  fun updateSharing_changesThePolicy_asAnUnpublishedEdit() {
    val viewModel = surveyEditorViewModel()

    viewModel.updateSharing { it.copy(policy = SharingPolicy.ANYONE_WITH_LINK) }

    assertEquals(SharingPolicy.ANYONE_WITH_LINK, viewModel.ui.sharing.policy)
    assertTrue(viewModel.ui.hasUnpublishedChanges)
  }

  @Test
  fun inviteCollaborator_returnsNoError_andAddsThemAsPending() {
    val viewModel = surveyEditorViewModel()

    val error = viewModel.inviteCollaborator("new@example.org", CollaboratorRole.DATA_COLLECTOR)

    assertNull(error)
    val invited = viewModel.ui.sharing.collaborators.single { it.email == "new@example.org" }
    assertEquals(CollaboratorRole.DATA_COLLECTOR, invited.role)
    assertTrue(viewModel.ui.hasUnpublishedChanges)
  }

  @Test
  fun leavingAnOrganization_turnsOrganizationAccessIntoRestricted_withANotice() {
    val viewModel = surveyEditorViewModel()
    viewModel.updateSharing { it.copy(policy = SharingPolicy.ORGANIZATION) }

    viewModel.setOrganization(null)

    assertEquals(SharingPolicy.RESTRICTED, viewModel.ui.sharing.policy)
    assertNotNull(viewModel.ui.organizationNotice)
  }
}
