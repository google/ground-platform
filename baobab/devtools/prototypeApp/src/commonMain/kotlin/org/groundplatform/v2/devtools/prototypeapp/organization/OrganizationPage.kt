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
package org.groundplatform.v2.devtools.prototypeapp.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.WebAppHeader
import org.groundplatform.v2.devtools.prototypeapp.WebHeaderButton
import org.groundplatform.v2.devtools.prototypeapp.WebHeaderContext
import org.groundplatform.v2.devtools.prototypeapp.WebHeaderSupportingText
import org.groundplatform.v2.devtools.prototypeapp.WebMobilePrototypeButton
import org.groundplatform.v2.devtools.prototypeapp.WebSurveyCard
import org.groundplatform.v2.devtools.prototypeapp.WebSurveysList
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MembershipStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole
import org.groundplatform.v2.devtools.prototypeapp.formeditor.DropdownSelector
import org.groundplatform.v2.devtools.prototypeapp.formeditor.SectionLabel
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.AcceptInviteDialog
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.PendingInviteLinkRow
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.PersonRow
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.ProfileAvatar

/**
 * One organization: its surveys, members, and (for Managers) settings. Members can create surveys
 * in it; Managers can invite people, approve join requests, change roles, remove members, edit the
 * profile, and delete the organization. The last Manager can't be demoted or removed.
 */
@Composable
internal fun OrganizationPage(
  state: PrototypeAppState,
  onOpenSurvey: (surveyId: String) -> Unit,
  onCreateSurvey: (title: String, organizationId: String) -> Unit,
  onSignOut: () -> Unit = { state.signOut() },
) {
  val organization = state.openOrganization
  val email = state.signedInUserEmail
  val isManager = organization != null && state.managesOrganization(organization)
  val isMember = organization != null && organization.isMember(email)
  val tabs = organization?.let { OrganizationPages.tabsFor(it, email) }.orEmpty()
  var tab by remember(organization?.id) { mutableStateOf(OrganizationTab.SURVEYS) }
  if (tab !in tabs && tabs.isNotEmpty()) tab = tabs.first()
  var isCreatingSurvey by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxSize()) {
    WebAppHeader(
      state = state,
      onSignOut = onSignOut,
      navigationIcon = {
        IconButton(onClick = { state.openOrganizations() }) {
          Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to organizations")
        }
      },
      context = {
        if (organization != null) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            ProfileAvatar(
              nameOrEmail = organization.name,
              photoUrl = organization.logoUrl,
              size = 32.dp,
            )
            WebHeaderContext(title = organization.name) {
              WebHeaderSupportingText(
                "${organization.activeMembers.size} members · " +
                  "${state.surveysInOrganization(organization.id).size} surveys" +
                  if (!organization.isListed) " · Unlisted" else ""
              )
            }
          }
        } else {
          WebHeaderContext(title = "Organization")
        }
      },
      actions = {
        WebMobilePrototypeButton(state)
        if (isMember) {
          WebHeaderButton(
            text = "Create survey",
            icon = Icons.Outlined.Add,
            onClick = { isCreatingSurvey = true },
            tonal = true,
          )
        }
      },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    if (organization == null) {
      MissingOrganization(onBack = { state.openOrganizations() })
      return@Column
    }

    TabRow(
      selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0),
      modifier = Modifier.widthIn(max = 560.dp),
    ) {
      tabs.forEach { t ->
        Tab(
          selected = t == tab,
          onClick = { tab = t },
          text = {
            val count =
              when (t) {
                OrganizationTab.SURVEYS -> state.surveysInOrganization(organization.id).size
                OrganizationTab.MEMBERS ->
                  organization.activeMembers.size +
                    (if (isManager) organization.pendingRequests.size else 0)
                OrganizationTab.SETTINGS -> null
              }
            Text(if (count != null) "${t.label} · $count" else t.label)
          },
        )
      }
    }

    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
      Column(
        modifier =
          Modifier.widthIn(max = OrganizationPageMaxWidth)
            .align(Alignment.TopCenter)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        state.organizationNotice?.let { notice ->
          OrganizationNotice(notice, onDismiss = state::dismissOrganizationNotice)
        }
        when (tab) {
          OrganizationTab.SURVEYS ->
            SurveysTab(state, organization, isMember, onOpenSurvey) { isCreatingSurvey = true }
          OrganizationTab.MEMBERS -> MembersTab(state, organization, isManager)
          OrganizationTab.SETTINGS -> SettingsTab(state, organization)
        }
      }
    }
  }

  if (isCreatingSurvey && organization != null) {
    CreateSurveyInOrganizationDialog(
      organization = organization,
      onCreate = { title ->
        isCreatingSurvey = false
        onCreateSurvey(title, organization.id)
      },
      onDismiss = { isCreatingSurvey = false },
    )
  }
}

@Composable
private fun MissingOrganization(onBack: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(48.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("This organization doesn't exist", style = MaterialTheme.typography.titleMedium)
    Text(
      "It may have been deleted, or the link is wrong.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onBack) { Text("Back to organizations") }
  }
}

// ---------------------------------------------------------------------------------------------
// Surveys
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SurveysTab(
  state: PrototypeAppState,
  organization: Organization,
  isMember: Boolean,
  onOpenSurvey: (String) -> Unit,
  onCreateSurvey: () -> Unit,
) {
  val surveys = state.surveysInOrganization(organization.id)
  if (surveys.isEmpty()) {
    Text(
      if (isMember) "No surveys yet. Create the first one for ${organization.name}."
      else "No surveys yet.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (isMember) OutlinedButton(onClick = onCreateSurvey) { Text("Create survey") }
    return
  }
  Text(
    if (state.managesOrganization(organization)) {
      "As a Manager you can edit every survey here, manage its sharing, and export its data."
    } else {
      "Surveys run on behalf of ${organization.name}. Open one to see what you can do in it."
    },
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    surveys.forEach { survey ->
      WebSurveyCard(
        survey = survey,
        access = WebSurveysList.accessLabel(survey, state.organizations, state.signedInUserEmail),
        isActive = survey.id == state.activeSurveyId,
        onClick = { onOpenSurvey(survey.id) },
      )
    }
  }
}

@Composable
private fun CreateSurveyInOrganizationDialog(
  organization: Organization,
  onCreate: (title: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var title by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create survey in ${organization.name}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Survey title") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          "You stay the owner. Managers of ${organization.name} can also edit this survey, " +
            "manage sharing, and export data.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Button(onClick = { onCreate(title) }, enabled = title.isNotBlank()) { Text("Create") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

// ---------------------------------------------------------------------------------------------
// Members
// ---------------------------------------------------------------------------------------------

@Composable
private fun MembersTab(state: PrototypeAppState, organization: Organization, isManager: Boolean) {
  val email = state.signedInUserEmail
  val view = OrganizationPages.membersView(organization, email)
  var acceptingEmail by remember { mutableStateOf<String?>(null) }
  var leaving by remember { mutableStateOf(false) }

  organization
    .member(acceptingEmail.orEmpty())
    ?.takeIf { it.status == MembershipStatus.INVITED }
    ?.let { invitee ->
      AcceptInviteDialog(
        title = organization.name,
        inviteeEmail = invitee.email,
        roleLabel = invitee.role.label,
        peopleListName = "Members page",
        onAccept = { name, photo ->
          state.acceptOrganizationInvite(organization.id, invitee.email, name, photo)
        },
        onDismiss = { acceptingEmail = null },
      )
    }
  if (leaving) {
    AlertDialog(
      onDismissRequest = { leaving = false },
      title = { Text("Leave ${organization.name}?") },
      text = {
        Text(
          "You'll lose access to its surveys unless they're shared with you directly. Surveys you " +
            "own stay yours."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            leaving = false
            state.removeOrganizationMember(organization.id, email)
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Leave")
        }
      },
      dismissButton = { TextButton(onClick = { leaving = false }) { Text("Cancel") } },
    )
  }

  Column(
    modifier = Modifier.widthIn(max = 760.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    if (isManager && view.requests.isNotEmpty()) {
      MembersCard("Requests to join") {
        Text(
          "People who found ${organization.name} in the directory and asked to join.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        view.requests.forEach { person ->
          key(person.email) {
            PersonRow(
              displayName = person.displayName,
              email = person.email,
              photoUrl = person.profile?.photoUrl,
              detail = "Asked to join",
              pending = true,
            ) {
              OutlinedButton(
                onClick = { state.removeOrganizationMember(organization.id, person.email) }
              ) {
                Text("Decline")
              }
              Spacer(Modifier.width(8.dp))
              Button(
                onClick = { state.approveOrganizationRequest(organization.id, person.email) }
              ) {
                Icon(Icons.Outlined.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Approve")
              }
            }
          }
        }
      }
    }

    MembersCard("Members") {
      if (isManager) InviteMemberRow(state, organization)
      view.active.forEach { person ->
        key(person.email) {
          val isSelf = person.email.equals(email, ignoreCase = true)
          val canChange = OrganizationPages.canChange(organization, person)
          PersonRow(
            displayName = person.displayName + if (isSelf) " (you)" else "",
            email = person.email,
            photoUrl = person.profile?.photoUrl,
            detail =
              if (person.role == OrganizationRole.MANAGER) OrganizationRole.MANAGER.description
              else OrganizationRole.MEMBER.description,
          ) {
            if (isManager) {
              Box(modifier = Modifier.width(170.dp)) {
                DropdownSelector(
                  label = "Role",
                  selectedText = person.role.label,
                  options = OrganizationRole.entries,
                  optionText = { it.label },
                  onSelect = { state.setOrganizationMemberRole(organization.id, person.email, it) },
                )
              }
              if (!isSelf) {
                IconButton(
                  onClick = { state.removeOrganizationMember(organization.id, person.email) },
                  enabled = canChange,
                ) {
                  Icon(Icons.Outlined.Close, contentDescription = "Remove ${person.displayName}")
                }
              }
            } else {
              Text(
                person.role.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, end = 12.dp),
              )
            }
          }
        }
      }
      if (isManager) {
        Text(
          "An organization always keeps at least one Manager. Names and photos come from each " +
            "person's account when they accept their invite.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (isManager && view.invited.isNotEmpty()) {
      MembersCard("Invited") {
        view.invited.forEach { person ->
          key(person.email) {
            PersonRow(
              displayName = person.displayName,
              email = person.email,
              photoUrl = person.profile?.photoUrl,
              pending = true,
              detail = "Waiting for them to accept their invite link · ${person.role.label}",
              footer =
                person.inviteToken?.let { token ->
                  {
                    PendingInviteLinkRow(
                      token = token,
                      onResetLink = {
                        state.resetOrganizationInviteLink(organization.id, person.email)
                      },
                      onOpenAsInvitee = { acceptingEmail = person.email },
                    )
                  }
                },
            ) {
              IconButton(
                onClick = { state.removeOrganizationMember(organization.id, person.email) }
              ) {
                Icon(Icons.Outlined.Close, contentDescription = "Revoke invite for ${person.email}")
              }
            }
          }
        }
      }
    }

    if (organization.isMember(email)) {
      TextButton(
        onClick = { leaving = true },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) {
        Text("Leave organization")
      }
    }
  }
}

@Composable
private fun MembersCard(title: String, content: @Composable () -> Unit) {
  ElevatedCard(
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      SectionLabel(title)
      content()
    }
  }
}

@Composable
private fun InviteMemberRow(state: PrototypeAppState, organization: Organization) {
  var email by remember { mutableStateOf("") }
  var role by remember { mutableStateOf(OrganizationRole.MEMBER) }
  var error by remember { mutableStateOf<String?>(null) }
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
    OutlinedTextField(
      value = email,
      onValueChange = {
        email = it
        error = null
      },
      label = { Text("Invite people by email") },
      singleLine = true,
      isError = error != null,
      supportingText = error?.let { { Text(it) } },
      modifier = Modifier.weight(1f),
    )
    Box(modifier = Modifier.width(170.dp).padding(top = 6.dp)) {
      DropdownSelector(
        label = "Role",
        selectedText = role.label,
        options = OrganizationRole.entries,
        optionText = { it.label },
        onSelect = { role = it },
      )
    }
    Button(
      onClick = {
        error = state.inviteOrganizationMember(organization.id, email, role)
        if (error == null) email = ""
      },
      enabled = email.isNotBlank(),
      modifier = Modifier.padding(top = 10.dp),
    ) {
      Icon(Icons.Outlined.PersonAdd, contentDescription = null)
      Spacer(Modifier.width(6.dp))
      Text("Invite")
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

@Composable
private fun SettingsTab(state: PrototypeAppState, organization: Organization) {
  var name by remember(organization.id, organization.name) { mutableStateOf(organization.name) }
  var description by
    remember(organization.id, organization.description) { mutableStateOf(organization.description) }
  var websiteUrl by
    remember(organization.id, organization.websiteUrl) { mutableStateOf(organization.websiteUrl) }
  var isListed by
    remember(organization.id, organization.isListed) { mutableStateOf(organization.isListed) }
  var confirmingDelete by remember { mutableStateOf(false) }
  val isDirty =
    name != organization.name ||
      description != organization.description ||
      websiteUrl != organization.websiteUrl ||
      isListed != organization.isListed

  if (confirmingDelete) {
    val surveyCount = state.surveysInOrganization(organization.id).size
    AlertDialog(
      onDismissRequest = { confirmingDelete = false },
      title = { Text("Delete ${organization.name}?") },
      text = {
        Text(
          "Members lose their affiliation. " +
            (if (surveyCount > 0) {
              "Its $surveyCount surveys become personal surveys of their owners; no data is deleted."
            } else {
              "It has no surveys."
            })
        )
      },
      confirmButton = {
        Button(
          onClick = {
            confirmingDelete = false
            state.deleteOrganization(organization.id)
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Delete organization")
        }
      },
      dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
    )
  }

  Column(
    modifier = Modifier.widthIn(max = 760.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    ElevatedCard(
      colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        SectionLabel("Profile")
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Organization name") },
          singleLine = true,
          isError = name.isBlank(),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Description") },
          minLines = 3,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = websiteUrl,
          onValueChange = { websiteUrl = it },
          label = { Text("Website") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = isListed, onCheckedChange = { isListed = it })
          Spacer(Modifier.width(4.dp))
          Column {
            Text("List in the directory", style = MaterialTheme.typography.bodyMedium)
            Text(
              "Anyone can find the organization and ask to join. Unlisted organizations are " +
                "invite-only.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Button(
            onClick = {
              state.updateOrganization(organization.id) {
                it.copy(
                  name = name.trim(),
                  description = description.trim(),
                  websiteUrl = websiteUrl.trim(),
                  isListed = isListed,
                )
              }
            },
            enabled = isDirty && name.isNotBlank(),
          ) {
            Text("Save")
          }
          TextButton(
            onClick = {
              name = organization.name
              description = organization.description
              websiteUrl = organization.websiteUrl
              isListed = organization.isListed
            },
            enabled = isDirty,
          ) {
            Text("Discard")
          }
        }
      }
    }

    ElevatedCard(
      colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SectionLabel("Danger zone")
        Text(
          "Deleting the organization removes everyone's affiliation. Its surveys become personal " +
            "surveys of their owners and keep all their data.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
          onClick = { confirmingDelete = true },
          colors =
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Delete organization", fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}
