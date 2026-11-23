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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.InvitationStatus
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.Checkbox
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_details_remove_item
import org.openforis.collectone.v2.web.resources.surveyeditor_nav_sharing
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_add_by_email
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_data_access
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_invite
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_joined
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_joined_now
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_manager_of
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_no_managers
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_org_managers
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_org_managers_hint
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_owner
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_owner_detail
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_pending_detail
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_people_with_access
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_profile_note
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_role
import org.openforis.collectone.v2.web.resources.surveyeditor_sharing_subtitle
import org.openforis.collectone.v2.web.resources.wizard_consent_supporting
import org.openforis.collectone.v2.web.resources.wizard_consent_terms_label
import org.openforis.collectone.v2.web.resources.wizard_consent_title
import org.openforis.collectone.v2.web.ui.common.AcceptInviteDialog
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.PaneScaffold
import org.openforis.collectone.v2.web.ui.common.PendingInviteLinkRow
import org.openforis.collectone.v2.web.ui.common.PersonRow
import org.openforis.collectone.v2.web.ui.common.SectionLabel
import org.openforis.collectone.v2.web.ui.surveyeditor.care.CareCardPosition
import org.openforis.collectone.v2.web.ui.surveyeditor.care.CareConformityCard
import org.openforis.collectone.v2.web.ui.surveyeditor.care.CareStickyChip
import org.openforis.collectone.v2.web.ui.surveyeditor.care.careConformity
import org.openforis.collectone.v2.web.ui.surveyeditor.care.withCareDefaults
import org.openforis.collectone.v2.web.ui.text.resolve

@Composable
internal fun SharingPane(uiState: SurveyEditorUiState, actions: SharingActions) {
  val scrollState = rememberScrollState()
  val careCardPosition = remember { CareCardPosition() }
  var isCareCardExpanded by rememberSaveable { mutableStateOf(false) }
  val organization = uiState.organization(uiState.details.organizationId)
  val careConformity =
    remember(uiState.draft, organization) { careConformity(uiState.draft, organization) }
  Box(
    modifier =
      Modifier.fillMaxSize().onGloballyPositioned {
        careCardPosition.viewportTop = it.positionInRoot().y
      }
  ) {
    PaneScaffold(
      title = stringResource(Res.string.surveyeditor_nav_sharing),
      subtitle = stringResource(Res.string.surveyeditor_sharing_subtitle),
      scrollState = scrollState,
    ) {
      CareConformityCard(
        conformity = careConformity,
        expanded = isCareCardExpanded,
        onExpandedChange = { isCareCardExpanded = it },
        onApplyDefaults =
          if (uiState.isReadOnly) null
          else {
            { actions.updateSharing { it.withCareDefaults() } }
          },
        modifier = Modifier.widthIn(max = 760.dp),
        position = careCardPosition,
      )
      SharingContent(
        uiState = uiState,
        actions = actions,
        modifier = Modifier.widthIn(max = 760.dp),
      )
    }
    // Keeps CARE conformity in view while the organizer edits the sharing settings below.
    CareStickyChip(
      conformity = careConformity,
      position = careCardPosition,
      scrollState = scrollState,
      onShowCard = { isCareCardExpanded = true },
      modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
    )
  }
}

/**
 * Scroll-free body of the Sharing pane (people with access, organization managers, general access
 * policy, and peer data visibility), reused inside [SharingPane] and the Survey Creation Wizard.
 */
@Composable
fun SharingContent(
  uiState: SurveyEditorUiState,
  actions: SharingActions,
  modifier: Modifier = Modifier,
) {
  val sharing = uiState.sharing
  val organization = uiState.organization(uiState.details.organizationId)
  var acceptingEmail by remember { mutableStateOf<String?>(null) }
  if (!uiState.isReadOnly) {
    sharing.collaborators
      .firstOrNull { it.email == acceptingEmail && it.status == InvitationStatus.PENDING }
      ?.let { invitee ->
        AcceptInviteDialog(
          surveyTitle = uiState.details.title,
          invitee = invitee,
          onAccept = { name, photo -> actions.acceptInvite(invitee.email, name, photo) },
          onDismiss = { acceptingEmail = null },
        )
      }
  }
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    ElevatedCard(
      colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SectionLabel(stringResource(Res.string.surveyeditor_sharing_people_with_access))
        if (!uiState.isReadOnly) {
          InviteRow(uiState, actions)
        }
        PersonRow(
          displayName = sharing.ownerProfile?.displayName ?: sharing.ownerEmail,
          email = sharing.ownerEmail,
          photoUrl = sharing.ownerProfile?.photoUrl,
          detail = stringResource(Res.string.surveyeditor_sharing_owner_detail),
        ) {
          Text(
            stringResource(Res.string.surveyeditor_sharing_owner),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, end = 12.dp),
          )
        }
        sharing.collaborators.forEach { person ->
          key(person.email) {
            val pending = person.status == InvitationStatus.PENDING
            PersonRow(
              displayName = person.displayName,
              email = person.email,
              photoUrl = person.profile?.photoUrl,
              pending = pending,
              detail =
                when {
                  pending -> stringResource(Res.string.surveyeditor_sharing_pending_detail)
                  person.profile?.cachedOn.isNullOrBlank() ->
                    stringResource(Res.string.surveyeditor_sharing_joined_now)
                  else ->
                    stringResource(
                      Res.string.surveyeditor_sharing_joined,
                      person.profile?.cachedOn.orEmpty(),
                    )
                },
              footer =
                person.inviteToken
                  ?.takeIf { pending }
                  ?.let { token ->
                    {
                      PendingInviteLinkRow(
                        token = token,
                        onResetLink =
                          if (uiState.isReadOnly) null
                          else {
                            { actions.resetInviteLink(person.email) }
                          },
                        onOpenAsInvitee =
                          if (uiState.isReadOnly) null
                          else {
                            { acceptingEmail = person.email }
                          },
                      )
                    }
                  },
            ) {
              Box(modifier = Modifier.width(190.dp)) {
                DropdownSelector(
                  label = stringResource(Res.string.surveyeditor_sharing_role),
                  selectedText = stringResource(person.role.labelRes),
                  options = CollaboratorRole.entries,
                  optionText = { stringResource(it.labelRes) },
                  enabled = !uiState.isReadOnly,
                  onSelect = { actions.setCollaboratorRole(person.email, it) },
                )
              }
              if (!uiState.isReadOnly) {
                IconButton(onClick = { actions.removeCollaborator(person.email) }) {
                  Icon(
                    Icons.Outlined.Close,
                    contentDescription =
                      stringResource(
                        Res.string.surveyeditor_details_remove_item,
                        person.displayName,
                      ),
                  )
                }
              }
            }
          }
        }
        Text(
          stringResource(Res.string.surveyeditor_sharing_profile_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (organization != null) InheritedManagersCard(organization)

    ElevatedCard(
      colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SectionLabel(stringResource(Res.string.surveyeditor_sharing_data_access))
        uiState.sharingIssues.forEach { issue ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              Icons.Outlined.Warning,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp),
            )
            Text(
              issue.toUiText().resolve(),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
        GeneralAccessPermissionSliders(
          sharing = sharing,
          organization = organization,
          onUpdateSharing = actions::updateSharing,
          surveyId = uiState.surveyId,
          surveyTitle = uiState.details.title,
          enabled = !uiState.isReadOnly,
        )
      }
    }

    ElevatedCard(
      colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            SectionLabel(stringResource(Res.string.wizard_consent_title))
            Text(
              text = stringResource(Res.string.wizard_consent_supporting),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = sharing.collectorConsentRequired,
            enabled = !uiState.isReadOnly,
            onCheckedChange = { required ->
              actions.updateSharing { current ->
                current.copy(collectorConsentRequired = required)
              }
            },
          )
        }
        if (sharing.collectorConsentRequired) {
          OutlinedTextField(
            value = sharing.collectorConsentText,
            onValueChange = { text ->
              actions.updateSharing { current -> current.copy(collectorConsentText = text) }
            },
            enabled = !uiState.isReadOnly,
            label = { Text(stringResource(Res.string.wizard_consent_terms_label)) },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

@Composable
private fun InviteRow(uiState: SurveyEditorUiState, actions: SharingActions) {
  var email by remember { mutableStateOf("") }
  var role by remember { mutableStateOf(CollaboratorRole.DATA_COLLECTOR) }
  var error by remember { mutableStateOf<StringResource?>(null) }
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
    OutlinedTextField(
      value = email,
      onValueChange = {
        email = it
        error = null
      },
      label = { Text(stringResource(Res.string.surveyeditor_sharing_add_by_email)) },
      singleLine = true,
      isError = error != null,
      supportingText = error?.let { { Text(stringResource(it)) } },
      modifier = Modifier.weight(1f),
    )
    Box(modifier = Modifier.width(190.dp).padding(top = 6.dp)) {
      DropdownSelector(
        label = stringResource(Res.string.surveyeditor_sharing_role),
        selectedText = stringResource(role.labelRes),
        options = CollaboratorRole.entries,
        optionText = { stringResource(it.labelRes) },
        onSelect = { role = it },
      )
    }
    Button(
      onClick = {
        error = actions.inviteCollaborator(email, role)
        if (error == null) email = ""
      },
      enabled = email.isNotBlank(),
      modifier = Modifier.padding(top = 10.dp),
    ) {
      Text(stringResource(Res.string.surveyeditor_sharing_invite))
    }
  }
}

@Composable
private fun CheckboxOption(
  title: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(role = Role.Checkbox) { onCheckedChange(!checked) }
        .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = checked, onCheckedChange = null)
    Spacer(Modifier.width(12.dp))
    Column {
      Text(
        title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Read-only list of the people who can manage this survey because they manage [organization]. They
 * aren't on the survey's own access list, so they can't be removed here.
 */
@Composable
private fun InheritedManagersCard(organization: Organization) {
  ElevatedCard(
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      SectionLabel(stringResource(Res.string.surveyeditor_sharing_org_managers))
      Text(
        stringResource(Res.string.surveyeditor_sharing_org_managers_hint, organization.name),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (organization.managers.isEmpty()) {
        Text(
          stringResource(Res.string.surveyeditor_sharing_no_managers),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      organization.managers.forEach { manager ->
        key(manager.email) {
          PersonRow(
            displayName = manager.displayName,
            email = manager.email,
            photoUrl = manager.profile?.photoUrl,
            detail = stringResource(Res.string.surveyeditor_sharing_manager_of, organization.name),
          ) {
            Text(
              stringResource(CollaboratorRole.SURVEY_ORGANIZER.labelRes),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 10.dp, end = 12.dp),
            )
          }
        }
      }
    }
  }
}
