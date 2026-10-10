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
package org.groundplatform.v2.devtools.prototypeapp.ui.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImagerySource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImagerySourceType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MembershipStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.canChangeMember
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolvedLibrary
import org.groundplatform.v2.devtools.prototypeapp.ui.dashboard.CreateSurveyDialog
import org.groundplatform.v2.devtools.prototypeapp.ui.dashboard.CreateSurveyRequest
import org.groundplatform.v2.devtools.prototypeapp.ui.dashboard.ImpactMessageSnackbar
import org.groundplatform.v2.devtools.prototypeapp.ui.dashboard.SidePanelSeparator
import org.groundplatform.v2.devtools.prototypeapp.ui.dashboard.SidePanelSeparatorWidth
import org.groundplatform.v2.devtools.prototypeapp.ui.dashboard.WebSurveyCard
import org.groundplatform.v2.devtools.prototypeapp.ui.dashboard.WebSurveysList
import org.groundplatform.v2.devtools.prototypeapp.ui.formeditor.DropdownSelector
import org.groundplatform.v2.devtools.prototypeapp.ui.formeditor.SectionLabel
import org.groundplatform.v2.devtools.prototypeapp.ui.impact.GlobalImpactPane
import org.groundplatform.v2.devtools.prototypeapp.ui.impact.OrganizationImpactPane
import org.groundplatform.v2.devtools.prototypeapp.ui.state.ImpactUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OrganizationLibraryUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OrganizationUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.surveyeditor.AcceptInviteDialog
import org.groundplatform.v2.devtools.prototypeapp.ui.surveyeditor.NavItem
import org.groundplatform.v2.devtools.prototypeapp.ui.surveyeditor.PaneScaffold
import org.groundplatform.v2.devtools.prototypeapp.ui.surveyeditor.PendingInviteLinkRow
import org.groundplatform.v2.devtools.prototypeapp.ui.surveyeditor.PersonRow
import org.groundplatform.v2.devtools.prototypeapp.ui.surveyeditor.ProfileAvatar
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.ImpactActions
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.OrganizationActions
import org.groundplatform.v2.devtools.prototypeapp.ui.workbench.WebAppHeader
import org.groundplatform.v2.devtools.prototypeapp.ui.workbench.WebHeaderButton
import org.groundplatform.v2.devtools.prototypeapp.ui.workbench.WebHeaderContext
import org.groundplatform.v2.devtools.prototypeapp.ui.workbench.WebHeaderSupportingText
import org.groundplatform.v2.devtools.prototypeapp.ui.workbench.WebMobilePrototypeButton

/**
 * One organization, laid out like the survey editor: a resizable left panel switches between
 * Organization details, Surveys, Members, Imagery sources, and the library's Purposes, Dictionary,
 * and Templates, and the content pane shows the selected section. Members can create surveys in it;
 * Managers can also edit and delete the organization, invite people, approve join requests, change
 * roles, remove members, and edit its library. The last Manager can't be demoted or removed.
 */
@Composable
internal fun OrganizationPage(
  state: PrototypeAppState,
  onOpenSurvey: (surveyId: String) -> Unit,
  onCreateSurvey: (CreateSurveyRequest) -> Unit,
  onSignOut: () -> Unit = { state.signOut() },
) {
  val uiState by state.organization.uiState.collectAsState()
  val dashboardState by state.dashboard.uiState.collectAsState()
  val impactState by state.impact.uiState.collectAsState()
  Box(modifier = Modifier.fillMaxSize()) {
    OrganizationPage(
      uiState = uiState,
      actions = state.organization,
      impactState = impactState,
      impactActions = state.impact,
      sidePanelWidthDp = dashboardState.sidePanelWidthDp,
      onSidePanelWidthChange = state.dashboard::updateSidePanelWidth,
      onOpenSurvey = onOpenSurvey,
      onCreateSurvey = onCreateSurvey,
      surveyLibrary = dashboardState::surveyLibrary,
      header = { onCreateSurveyClick ->
        val organization = uiState.openOrganization
        WebAppHeader(
          state = state,
          onSignOut = onSignOut,
          navigationIcon = {
            IconButton(onClick = { state.organization.openOrganizations() }) {
              Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back to organizations",
              )
            }
          },
          context = {
            if (organization != null) {
              OrganizationHeaderContext(
                organization = organization,
                surveyCount = uiState.surveyCountInOrganization(organization.id),
              )
            } else {
              WebHeaderContext(title = "Organization")
            }
          },
          actions = {
            WebMobilePrototypeButton(state)
            // Nobody creates surveys in "All users": isMemberOf is false for it.
            if (organization != null && uiState.isMemberOf(organization)) {
              WebHeaderButton(
                text = "Create survey",
                icon = Icons.Outlined.Add,
                onClick = onCreateSurveyClick,
                tonal = true,
              )
            }
          },
        )
      },
    )
    ImpactMessageSnackbar(state, modifier = Modifier.align(Alignment.BottomCenter))
  }
}

/** The organization's logo, name, and summary line in the web header. */
@Composable
private fun OrganizationHeaderContext(organization: Organization, surveyCount: Int) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    ProfileAvatar(nameOrEmail = organization.name, photoUrl = organization.logoUrl, size = 32.dp)
    WebHeaderContext(title = organization.name) {
      WebHeaderSupportingText(
        if (organization.isSynthetic) "Synthetic organization" else "Organization",
        color = MaterialTheme.colorScheme.primary,
      )
      WebHeaderSupportingText("·")
      WebHeaderSupportingText(
        if (organization.isSynthetic) {
          val count = organization.imagerySources.size
          "Shared across all surveys · $count imagery ${if (count == 1) "source" else "sources"}"
        } else {
          "${organization.activeMembers.size} members · $surveyCount surveys" +
            if (!organization.isListed) " · Unlisted" else ""
        }
      )
    }
  }
}

/**
 * Stateless organization page: the open organization of [uiState] with its navigation panel,
 * section panes, notice banner, and "Create survey" dialog.
 *
 * @param sidePanelWidthDp width of the left panel, shared with the dashboard and Survey editor.
 * @param header the page header; it receives the click handler of its "Create survey" button.
 */
@Composable
internal fun OrganizationPage(
  uiState: OrganizationUiState,
  actions: OrganizationActions,
  sidePanelWidthDp: Float,
  onSidePanelWidthChange: (Float) -> Unit,
  onOpenSurvey: (surveyId: String) -> Unit,
  onCreateSurvey: (CreateSurveyRequest) -> Unit,
  surveyLibrary: (organizationId: String?) -> ResolvedLibrary,
  header: @Composable (onCreateSurveyClick: () -> Unit) -> Unit,
  impactState: ImpactUiState = ImpactUiState(),
  impactActions: ImpactActions? = null,
) {
  val organization = uiState.openOrganization
  val isManager = organization != null && uiState.managesOrganization(organization)
  val isMember = organization != null && uiState.isMemberOf(organization)
  var tab by remember(organization?.id) { mutableStateOf(OrganizationTab.DETAILS) }
  var isCreatingSurvey by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxSize()) {
    header { isCreatingSurvey = true }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    if (organization == null) {
      MissingOrganization(onBack = actions::openOrganizations)
      return@Column
    }

    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
      OrganizationNavigation(
        organization = organization,
        surveyCount = uiState.surveyCountInOrganization(organization.id),
        library = uiState.library,
        isManager = isManager,
        showsImpact = showsImpact(organization, isManager, isMember, impactActions),
        selected = tab,
        onSelect = { tab = it },
        modifier = Modifier.width(sidePanelWidthDp.dp).fillMaxHeight(),
      )
      SidePanelSeparator(
        widthDp = sidePanelWidthDp,
        onWidthChange = onSidePanelWidthChange,
        modifier = Modifier.width(SidePanelSeparatorWidth).fillMaxHeight(),
      )
      Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        when (tab) {
          OrganizationTab.DETAILS ->
            PaneScaffold(
              title = "Organization details",
              subtitle =
                if (isManager) {
                  "How ${organization.name} appears to members and in the directory. Only " +
                    "Managers can change these."
                } else {
                  "How ${organization.name} appears to members and in the directory. Ask a " +
                    "Manager to change these."
                },
            ) {
              NoticeSlot(uiState, actions)
              DetailsPane(uiState, actions, organization, isManager)
            }
          OrganizationTab.SURVEYS ->
            PaneScaffold(
              title = "Surveys",
              subtitle =
                if (isManager) {
                  "Surveys run on behalf of ${organization.name}. As a Manager you can edit " +
                    "every one of them, manage its sharing, and export its data."
                } else {
                  "Surveys run on behalf of ${organization.name}. Open one to see what you can " +
                    "do in it."
                },
            ) {
              NoticeSlot(uiState, actions)
              SurveysPane(uiState, organization, isMember, onOpenSurvey) { isCreatingSurvey = true }
            }
          OrganizationTab.IMPACT ->
            if (organization.isSynthetic) {
              GlobalImpactPane(
                summary = impactState.global,
                isLoading = impactState.isLoading,
                onDownload = { impactActions?.downloadGlobalSummary() },
              )
            } else {
              OrganizationImpactPane(
                state = impactState.organizations[organization.id],
                isLoading = impactState.isLoading,
                onSurveyFilterChange = {
                  impactActions?.setOrganizationSurveyFilter(organization.id, it)
                },
                onCountryFilterChange = {
                  impactActions?.setOrganizationCountryFilter(organization.id, it)
                },
                onDownload = { impactActions?.downloadOrganizationSummary(organization.id) },
              )
            }
          OrganizationTab.MEMBERS ->
            PaneScaffold(
              title = "Members",
              subtitle =
                "People affiliated with ${organization.name}. Managers run the organization " +
                  "and all of its surveys; Members can create surveys in it.",
            ) {
              NoticeSlot(uiState, actions)
              MembersPane(uiState, actions, organization, isManager)
            }
          OrganizationTab.IMAGERY_SOURCES ->
            PaneScaffold(
              title = "Imagery sources",
              subtitle =
                if (organization.isSynthetic) {
                  "Custom basemap imagery layers available to all Ground users across every " +
                    "survey in the basemap layers dialog on mobile and web."
                } else if (isManager) {
                  "Custom basemap imagery layers available in the basemap layers dialog on " +
                    "mobile and web for surveys in ${organization.name}."
                } else {
                  "Custom basemap imagery layers configured for surveys in ${organization.name}. " +
                    "Ask a Manager to add or change imagery sources."
                },
            ) {
              NoticeSlot(uiState, actions)
              ImagerySourcesPane(actions, organization, isManager)
            }
          OrganizationTab.PURPOSES ->
            PaneScaffold(
              title = "Purposes",
              subtitle =
                if (organization.isSynthetic) {
                  "Choices every organization sees for \"What will this data be used for?\" when " +
                    "creating a survey. Each purpose adds its templates to the new survey."
                } else {
                  "Choices offered for \"What will this data be used for?\" when creating a survey " +
                    "in ${organization.name}: its own purposes first, then global ones."
                },
            ) {
              NoticeSlot(uiState, actions)
              PurposesPane(uiState.library, actions, organization)
            }
          OrganizationTab.DICTIONARY ->
            PaneScaffold(
              title = "Dictionary",
              subtitle =
                if (organization.isSynthetic) {
                  "Global concepts: standard fields that link form questions to a shared meaning, " +
                    "so answers can be compared and added up across surveys and languages."
                } else {
                  "Concepts available to surveys in ${organization.name}: standard fields that " +
                    "give form questions a shared meaning, so answers can be compared and added up."
                },
            ) {
              NoticeSlot(uiState, actions)
              DictionaryPane(uiState.library, actions, organization)
            }
          OrganizationTab.TEMPLATES ->
            PaneScaffold(
              title = "Templates",
              subtitle =
                if (organization.isSynthetic) {
                  "Global form templates that every organization can add to its surveys."
                } else {
                  "Form templates available to surveys in ${organization.name}: its own " +
                    "templates first, then global ones."
                },
            ) {
              NoticeSlot(uiState, actions)
              TemplatesPane(uiState.library, actions, organization)
            }
        }
      }
    }
  }

  if (isCreatingSurvey && organization != null) {
    CreateSurveyDialog(
      organizations = listOf(organization),
      surveyLibrary = surveyLibrary,
      onCreate = { request ->
        isCreatingSurvey = false
        onCreateSurvey(request)
      },
      onDismiss = { isCreatingSurvey = false },
      organization = organization,
    )
  }
}

/** Shows the pending organization notice, if any, at the top of a pane. */
@Composable
private fun NoticeSlot(uiState: OrganizationUiState, actions: OrganizationActions) {
  uiState.notice?.let { notice ->
    Box(modifier = Modifier.widthIn(max = OrganizationPaneMaxWidth)) {
      OrganizationNotice(notice, onDismiss = actions::dismissNotice)
    }
  }
}

/** Left panel listing the page's sections, styled like the survey editor's navigation. */
@Composable
private fun OrganizationNavigation(
  organization: Organization,
  surveyCount: Int,
  library: OrganizationLibraryUiState,
  isManager: Boolean,
  showsImpact: Boolean,
  selected: OrganizationTab,
  onSelect: (OrganizationTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  val memberCount =
    organization.activeMembers.size + (if (isManager) organization.pendingRequests.size else 0)
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      NavItem(
        label = OrganizationTab.DETAILS.label,
        icon = Icons.Outlined.Info,
        selected = selected == OrganizationTab.DETAILS,
        onClick = { onSelect(OrganizationTab.DETAILS) },
      )
      NavItem(
        label = OrganizationTab.SURVEYS.label,
        icon = Icons.Outlined.Map,
        selected = selected == OrganizationTab.SURVEYS,
        onClick = { onSelect(OrganizationTab.SURVEYS) },
        trailing = "$surveyCount",
      )
      if (showsImpact) {
        NavItem(
          label = OrganizationTab.IMPACT.label,
          icon = Icons.Outlined.Insights,
          selected = selected == OrganizationTab.IMPACT,
          onClick = { onSelect(OrganizationTab.IMPACT) },
        )
      }
      NavItem(
        label = OrganizationTab.MEMBERS.label,
        icon = Icons.Outlined.Group,
        selected = selected == OrganizationTab.MEMBERS,
        onClick = { onSelect(OrganizationTab.MEMBERS) },
        trailing = "$memberCount",
      )
      NavItem(
        label = OrganizationTab.IMAGERY_SOURCES.label,
        icon = Icons.Outlined.Layers,
        selected = selected == OrganizationTab.IMAGERY_SOURCES,
        onClick = { onSelect(OrganizationTab.IMAGERY_SOURCES) },
        trailing = "${organization.imagerySources.size}",
      )
      NavItem(
        label = OrganizationTab.PURPOSES.label,
        icon = Icons.Outlined.Flag,
        selected = selected == OrganizationTab.PURPOSES,
        onClick = { onSelect(OrganizationTab.PURPOSES) },
        trailing = "${library.purposePacks.size}",
      )
      NavItem(
        label = OrganizationTab.DICTIONARY.label,
        icon = Icons.Outlined.Book,
        selected = selected == OrganizationTab.DICTIONARY,
        onClick = { onSelect(OrganizationTab.DICTIONARY) },
        trailing = "${library.totalConceptCount}",
      )
      NavItem(
        label = OrganizationTab.TEMPLATES.label,
        icon = Icons.Outlined.Description,
        selected = selected == OrganizationTab.TEMPLATES,
        onClick = { onSelect(OrganizationTab.TEMPLATES) },
        trailing = "${library.formTemplates.size}",
      )
    }
  }
}

internal val OrganizationPaneMaxWidth = 760.dp

/**
 * Whether the Impact tab is shown: to members of a real organization, and, for the synthetic `"All
 * users"` organization, only to its Managers (the platform-wide view).
 */
private fun showsImpact(
  organization: Organization,
  isManager: Boolean,
  isMember: Boolean,
  impactActions: ImpactActions?,
): Boolean =
  impactActions != null && if (organization.isSynthetic) isManager else isMember || isManager

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
private fun SurveysPane(
  uiState: OrganizationUiState,
  organization: Organization,
  isMember: Boolean,
  onOpenSurvey: (String) -> Unit,
  onCreateSurvey: () -> Unit,
) {
  val surveys = uiState.surveysInOrganization(organization.id)
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
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    surveys.forEach { survey ->
      WebSurveyCard(
        survey = survey,
        access =
          WebSurveysList.accessLabel(survey, uiState.organizations, uiState.signedInUserEmail),
        isActive = survey.id == uiState.activeSurveyId,
        onClick = { onOpenSurvey(survey.id) },
      )
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Members
// ---------------------------------------------------------------------------------------------

@Composable
private fun MembersPane(
  uiState: OrganizationUiState,
  actions: OrganizationActions,
  organization: Organization,
  isManager: Boolean,
) {
  val email = uiState.signedInUserEmail
  val view = uiState.membersView(organization)
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
          actions.acceptInvite(organization.id, invitee.email, name, photo)
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
            actions.removeMember(organization.id, email)
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
          Text("Leave")
        }
      },
      dismissButton = { TextButton(onClick = { leaving = false }) { Text("Cancel") } },
    )
  }

  Column(
    modifier = Modifier.widthIn(max = OrganizationPaneMaxWidth),
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
              pendingLabel = "Requested",
            ) {
              OutlinedButton(onClick = { actions.removeMember(organization.id, person.email) }) {
                Text("Decline")
              }
              Spacer(Modifier.width(8.dp))
              Button(onClick = { actions.approveRequest(organization.id, person.email) }) {
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
      if (isManager) InviteMemberRow(actions, organization)
      if (organization.isSynthetic) {
        Text(
          "Everyone is a read-only member of ${organization.name}. Its Managers are the platform " +
            "admins: they edit the global library and imagery sources.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      view.active.forEach { person ->
        key(person.email) {
          val isSelf = uiState.isSelf(person.email)
          val canChange = uiState.canChangeMember(organization, person)
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
                  options =
                    if (organization.isSynthetic) listOf(OrganizationRole.MANAGER)
                    else OrganizationRole.entries,
                  optionText = { it.label },
                  onSelect = { actions.setMemberRole(organization.id, person.email, it) },
                )
              }
              if (!isSelf) {
                IconButton(
                  onClick = { actions.removeMember(organization.id, person.email) },
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
                      onResetLink = { actions.resetInviteLink(organization.id, person.email) },
                      onOpenAsInvitee = { acceptingEmail = person.email },
                    )
                  }
                },
            ) {
              IconButton(onClick = { actions.removeMember(organization.id, person.email) }) {
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
private fun InviteMemberRow(actions: OrganizationActions, organization: Organization) {
  // Everyone is implicitly a member of "All users", so only Managers are invited to it.
  val roles =
    if (organization.isSynthetic) listOf(OrganizationRole.MANAGER) else OrganizationRole.entries
  var email by remember { mutableStateOf("") }
  var role by remember(organization.id) { mutableStateOf(roles.first()) }
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
        options = roles,
        optionText = { it.label },
        onSelect = { role = it },
      )
    }
    Button(
      onClick = {
        error = actions.inviteMember(organization.id, email, role)
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
// Details
// ---------------------------------------------------------------------------------------------

/**
 * Name, description, website, type, country, and directory listing (the synthetic `"All users"`
 * organization has no type or country). Managers edit them in place and **Save** (or **Discard**)
 * their changes, and can delete the organization; everyone else sees them read-only.
 */
@Composable
private fun DetailsPane(
  uiState: OrganizationUiState,
  actions: OrganizationActions,
  organization: Organization,
  isManager: Boolean,
) {
  Column(
    modifier = Modifier.widthIn(max = OrganizationPaneMaxWidth),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    if (isManager) {
      EditableDetailsCard(actions, organization)
      if (!organization.isSynthetic) {
        DangerZoneCard(
          actions = actions,
          organization = organization,
          surveyCount = uiState.surveyCountInOrganization(organization.id),
        )
      }
    } else {
      ReadOnlyDetailsCard(organization)
    }
    if (!organization.isSynthetic) {
      PlatformNumbersCard(
        organization = organization,
        isManager = isManager,
        onExcludeChange = { actions.setExcludeFromPlatformAggregates(organization.id, it) },
      )
    }
  }
}

/**
 * Whether the organization's data counts toward platform-wide impact numbers
 * (`Organization.exclude_from_platform_aggregates`). Managers switch it; others see it.
 */
@Composable
private fun PlatformNumbersCard(
  organization: Organization,
  isManager: Boolean,
  onExcludeChange: (Boolean) -> Unit,
) {
  DetailsCard("Platform-wide numbers") {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Keep our data out of platform totals", style = MaterialTheme.typography.bodyMedium)
        Text(
          "Your surveys still count in your own Impact numbers; only totals across all of Ground " +
            "leave them out.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(Modifier.width(12.dp))
      Switch(
        checked = organization.excludeFromPlatformAggregates,
        onCheckedChange = if (isManager) onExcludeChange else null,
        enabled = isManager,
      )
    }
    if (!isManager) {
      Text(
        "Ask a Manager to change this.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Imagery sources
// ---------------------------------------------------------------------------------------------

@Composable
private fun ImagerySourcesPane(
  actions: OrganizationActions,
  organization: Organization,
  isManager: Boolean,
) {
  var editingSourceId by remember(organization.id) { mutableStateOf<String?>(null) }

  Column(
    modifier = Modifier.widthIn(max = OrganizationPaneMaxWidth),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    DetailsCard("Configured imagery sources") {
      if (organization.imagerySources.isEmpty()) {
        Text(
          if (isManager) {
            "No imagery sources configured yet. Add an XYZ tile URL below to make it " +
              "toggleable in the basemap layers dialog."
          } else {
            "No imagery sources configured for ${organization.name}."
          },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          organization.imagerySources.forEach { source ->
            key(source.id) {
              ImagerySourceRow(
                actions = actions,
                organization = organization,
                source = source,
                isManager = isManager,
                isEditing = editingSourceId == source.id,
                onStartEdit = { editingSourceId = source.id },
                onDoneEdit = { editingSourceId = null },
              )
            }
          }
        }
      }
    }

    if (isManager) {
      AddImagerySourceCard(actions = actions, organization = organization)
    }
  }
}

@Composable
private fun ImagerySourceRow(
  actions: OrganizationActions,
  organization: Organization,
  source: ImagerySource,
  isManager: Boolean,
  isEditing: Boolean,
  onStartEdit: () -> Unit,
  onDoneEdit: () -> Unit,
) {
  var editName by remember(source.id, source.name) { mutableStateOf(source.name) }
  var editUrl by remember(source.id, source.urlTemplate) { mutableStateOf(source.urlTemplate) }
  var editOffline by
    remember(source.id, source.allowOfflineDownload) { mutableStateOf(source.allowOfflineDownload) }
  var editError by remember(source.id) { mutableStateOf<String?>(null) }

  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (isEditing && isManager) {
        OutlinedTextField(
          value = editName,
          onValueChange = {
            editName = it
            editError = null
          },
          label = { Text("Source name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = editUrl,
          onValueChange = {
            editUrl = it
            editError = null
          },
          label = { Text("XYZ tile URL") },
          placeholder = { Text("https://tile.opentopomap.org/{z}/{x}/{y}.png") },
          singleLine = true,
          isError = editError != null,
          supportingText =
            editError?.let { { Text(it) } }
              ?: {
                Text("Must include {z}, {x}, and {y} tile placeholders.")
              },
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
          modifier = Modifier.fillMaxWidth(),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              "Permit offline download on mobile",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
            )
            Text(
              "Allow collectors to download tiles from this source for offline field use.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(checked = editOffline, onCheckedChange = { editOffline = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = {
              val err =
                actions.updateImagerySource(
                  organizationId = organization.id,
                  sourceId = source.id,
                  name = editName,
                  urlTemplate = editUrl,
                  allowOfflineDownload = editOffline,
                )
              if (err == null) {
                onDoneEdit()
              } else {
                editError = err
              }
            }
          ) {
            Text("Save")
          }
          TextButton(
            onClick = {
              editName = source.name
              editUrl = source.urlTemplate
              editOffline = source.allowOfflineDownload
              editError = null
              onDoneEdit()
            }
          ) {
            Text("Cancel")
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                source.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
              Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              ) {
                Text(
                  source.type.label,
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
            Text(
              source.urlTemplate,
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (isManager) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = onStartEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit ${source.name}")
              }
              IconButton(onClick = { actions.removeImagerySource(organization.id, source.id) }) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove ${source.name}")
              }
            }
          }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              "Permit offline download on mobile",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Medium,
            )
            Text(
              if (source.allowOfflineDownload) {
                "Collectors can download tiles from this source onto their mobile device."
              } else {
                "Online streaming only; offline tile download is disabled on mobile."
              },
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (isManager) {
            Switch(
              checked = source.allowOfflineDownload,
              onCheckedChange = { allowed ->
                actions.setImagerySourceOfflineAllowed(
                  organizationId = organization.id,
                  sourceId = source.id,
                  allowOfflineDownload = allowed,
                )
              },
            )
          } else {
            Text(
              if (source.allowOfflineDownload) "Permitted" else "Not permitted",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AddImagerySourceCard(actions: OrganizationActions, organization: Organization) {
  var name by remember(organization.id) { mutableStateOf("") }
  var urlTemplate by remember(organization.id) { mutableStateOf("") }
  var sourceType by remember(organization.id) { mutableStateOf(ImagerySourceType.XYZ_TILES) }
  var allowOfflineDownload by remember(organization.id) { mutableStateOf(true) }
  var error by remember(organization.id) { mutableStateOf<String?>(null) }

  DetailsCard("Add imagery source") {
    Text(
      "Add a raster tile service that collectors and organizers can toggle on in the basemap " +
        "layers dialog.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(modifier = Modifier.widthIn(max = 280.dp)) {
      DropdownSelector(
        label = "Source type",
        selectedText = sourceType.label,
        options = ImagerySourceType.entries,
        optionText = { it.label },
        onSelect = { sourceType = it },
      )
    }
    OutlinedTextField(
      value = name,
      onValueChange = {
        name = it
        error = null
      },
      label = { Text("Source name") },
      placeholder = { Text("e.g. OpenTopoMap Contours") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = urlTemplate,
      onValueChange = {
        urlTemplate = it
        error = null
      },
      label = { Text("XYZ tile URL") },
      placeholder = { Text("https://tile.opentopomap.org/{z}/{x}/{y}.png") },
      singleLine = true,
      isError = error != null,
      supportingText =
        error?.let { { Text(it) } }
          ?: {
            Text("Web Mercator raster tile URL template containing {z}, {x}, and {y} placeholders.")
          },
      textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
      modifier = Modifier.fillMaxWidth(),
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          "Permit offline download on mobile",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
        )
        Text(
          "Allow collectors on mobile devices to download tiles from this XYZ source for " +
            "offline field use.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = allowOfflineDownload, onCheckedChange = { allowOfflineDownload = it })
    }
    Button(
      onClick = {
        val validationError =
          actions.addImagerySource(
            organizationId = organization.id,
            name = name,
            urlTemplate = urlTemplate,
            type = sourceType,
            allowOfflineDownload = allowOfflineDownload,
          )
        if (validationError == null) {
          name = ""
          urlTemplate = ""
          allowOfflineDownload = true
          error = null
        } else {
          error = validationError
        }
      },
      enabled = name.isNotBlank() && urlTemplate.isNotBlank(),
    ) {
      Icon(Icons.Outlined.Add, contentDescription = null)
      Spacer(Modifier.width(6.dp))
      Text("Add imagery source")
    }
  }
}

@Composable
private fun EditableDetailsCard(actions: OrganizationActions, organization: Organization) {
  var name by remember(organization.id, organization.name) { mutableStateOf(organization.name) }
  var description by
    remember(organization.id, organization.description) { mutableStateOf(organization.description) }
  var websiteUrl by
    remember(organization.id, organization.websiteUrl) { mutableStateOf(organization.websiteUrl) }
  var isListed by
    remember(organization.id, organization.isListed) { mutableStateOf(organization.isListed) }
  var type by
    remember(organization.id, organization.organizationType) {
      mutableStateOf(organization.organizationType)
    }
  var countryCode by
    remember(organization.id, organization.countryCode) { mutableStateOf(organization.countryCode) }
  val isDirty =
    name != organization.name ||
      description != organization.description ||
      websiteUrl != organization.websiteUrl ||
      isListed != organization.isListed ||
      type != organization.organizationType ||
      countryCode != organization.countryCode

  DetailsCard("Profile") {
    OutlinedTextField(
      value = name,
      onValueChange = { name = it },
      label = { Text("Organization name") },
      singleLine = true,
      isError = name.isBlank(),
      supportingText = if (name.isBlank()) ({ Text("Enter a name") }) else null,
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = description,
      onValueChange = { description = it },
      label = { Text("Description") },
      supportingText = { Text("Shown in the directory and on the Surveys page.") },
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
    // "All users" is platform-wide, so it has no type or country.
    if (organization.hasTypeAndCountry) {
      OrganizationTypeDropdown(type = type, onTypeChange = { type = it })
      CountryPicker(countryCode = countryCode, onCountryCodeChange = { countryCode = it })
    }
    // "All users" is never listed in the directory.
    if (!organization.isSynthetic)
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
    Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Button(
        onClick = {
          actions.updateOrganizationDetails(
            organizationId = organization.id,
            name = name,
            description = description,
            websiteUrl = websiteUrl,
            isListed = isListed,
            organizationType = type,
            countryCode = countryCode,
          )
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
          type = organization.organizationType
          countryCode = organization.countryCode
        },
        enabled = isDirty,
      ) {
        Text("Discard")
      }
      if (isDirty) {
        Text(
          "Unsaved changes",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun ReadOnlyDetailsCard(organization: Organization) {
  DetailsCard("Profile") {
    DetailRow("Name", organization.name)
    DetailRow("Description", organization.description.ifBlank { "No description yet." })
    DetailRow("Website", organization.websiteUrl.ifBlank { "Not provided." })
    if (organization.hasTypeAndCountry) {
      DetailRow("Type", organizationTypeText(organization.organizationType))
      DetailRow("Country", countryText(organization.countryCode))
    }
    DetailRow(
      "Directory",
      if (organization.isListed) "Listed · anyone can find it and ask to join"
      else "Unlisted · invite-only",
    )
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(value, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun DangerZoneCard(
  actions: OrganizationActions,
  organization: Organization,
  surveyCount: Int,
) {
  var confirmingDelete by remember { mutableStateOf(false) }

  if (confirmingDelete) {
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
            actions.deleteOrganization(organization.id)
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
          Text("Delete organization")
        }
      },
      dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
    )
  }

  DetailsCard("Danger zone") {
    Text(
      "Deleting the organization removes everyone's affiliation. Its surveys become personal " +
        "surveys of their owners and keep all their data.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
      onClick = { confirmingDelete = true },
      colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
      Text("Delete organization", fontWeight = FontWeight.SemiBold)
    }
  }
}

@Composable
internal fun DetailsCard(title: String, content: @Composable () -> Unit) {
  ElevatedCard(
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      SectionLabel(title)
      content()
    }
  }
}
