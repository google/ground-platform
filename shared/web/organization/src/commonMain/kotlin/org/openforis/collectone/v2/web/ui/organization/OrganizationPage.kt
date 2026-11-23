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
package org.openforis.collectone.v2.web.ui.organization

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.MembershipStatus
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.domain.usecase.ResolvedLibrary
import org.openforis.collectone.v2.app.ui.common.AdminOnlyBadge
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.descriptionRes
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.Checkbox
import org.openforis.collectone.v2.core.forms.ui.controls.ElevatedCard
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.domain.model.canChangeMember
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_admin_panel
import org.openforis.collectone.v2.web.resources.org_admin_panel_subtitle
import org.openforis.collectone.v2.web.resources.org_approve
import org.openforis.collectone.v2.web.resources.org_ask_manager_change
import org.openforis.collectone.v2.web.resources.org_back_to_organizations
import org.openforis.collectone.v2.web.resources.org_cancel
import org.openforis.collectone.v2.web.resources.org_cost_controls_subtitle_global
import org.openforis.collectone.v2.web.resources.org_cost_controls_subtitle_manager
import org.openforis.collectone.v2.web.resources.org_cost_controls_subtitle_member
import org.openforis.collectone.v2.web.resources.org_cost_controls_title_global
import org.openforis.collectone.v2.web.resources.org_cost_tracking_subtitle
import org.openforis.collectone.v2.web.resources.org_cost_tracking_title
import org.openforis.collectone.v2.web.resources.org_create_survey
import org.openforis.collectone.v2.web.resources.org_danger_zone
import org.openforis.collectone.v2.web.resources.org_danger_zone_body
import org.openforis.collectone.v2.web.resources.org_decline
import org.openforis.collectone.v2.web.resources.org_delete_body_no_surveys
import org.openforis.collectone.v2.web.resources.org_delete_body_surveys
import org.openforis.collectone.v2.web.resources.org_delete_item_title
import org.openforis.collectone.v2.web.resources.org_delete_organization
import org.openforis.collectone.v2.web.resources.org_details_description_help
import org.openforis.collectone.v2.web.resources.org_details_listed_help
import org.openforis.collectone.v2.web.resources.org_details_logo_help
import org.openforis.collectone.v2.web.resources.org_details_name_required
import org.openforis.collectone.v2.web.resources.org_details_subtitle_manager
import org.openforis.collectone.v2.web.resources.org_details_subtitle_member
import org.openforis.collectone.v2.web.resources.org_dictionary_subtitle
import org.openforis.collectone.v2.web.resources.org_dictionary_subtitle_admins
import org.openforis.collectone.v2.web.resources.org_directory_listed
import org.openforis.collectone.v2.web.resources.org_directory_unlisted
import org.openforis.collectone.v2.web.resources.org_discard
import org.openforis.collectone.v2.web.resources.org_error_enter_name
import org.openforis.collectone.v2.web.resources.org_field_country
import org.openforis.collectone.v2.web.resources.org_field_description
import org.openforis.collectone.v2.web.resources.org_field_directory
import org.openforis.collectone.v2.web.resources.org_field_logo
import org.openforis.collectone.v2.web.resources.org_field_name
import org.openforis.collectone.v2.web.resources.org_field_name_short
import org.openforis.collectone.v2.web.resources.org_field_role
import org.openforis.collectone.v2.web.resources.org_field_type
import org.openforis.collectone.v2.web.resources.org_field_website
import org.openforis.collectone.v2.web.resources.org_imagery_subtitle_admins
import org.openforis.collectone.v2.web.resources.org_imagery_subtitle_manager
import org.openforis.collectone.v2.web.resources.org_imagery_subtitle_member
import org.openforis.collectone.v2.web.resources.org_invite
import org.openforis.collectone.v2.web.resources.org_invite_label
import org.openforis.collectone.v2.web.resources.org_leave
import org.openforis.collectone.v2.web.resources.org_leave_body
import org.openforis.collectone.v2.web.resources.org_leave_organization
import org.openforis.collectone.v2.web.resources.org_leave_title
import org.openforis.collectone.v2.web.resources.org_list_in_directory
import org.openforis.collectone.v2.web.resources.org_logo_change
import org.openforis.collectone.v2.web.resources.org_logo_remove
import org.openforis.collectone.v2.web.resources.org_logo_upload
import org.openforis.collectone.v2.web.resources.org_member_you
import org.openforis.collectone.v2.web.resources.org_members_admins_help
import org.openforis.collectone.v2.web.resources.org_members_asked_to_join
import org.openforis.collectone.v2.web.resources.org_members_invite_pending
import org.openforis.collectone.v2.web.resources.org_members_invited_title
import org.openforis.collectone.v2.web.resources.org_members_manager_help
import org.openforis.collectone.v2.web.resources.org_members_page_name
import org.openforis.collectone.v2.web.resources.org_members_requested
import org.openforis.collectone.v2.web.resources.org_members_requests_body
import org.openforis.collectone.v2.web.resources.org_members_requests_title
import org.openforis.collectone.v2.web.resources.org_members_subtitle
import org.openforis.collectone.v2.web.resources.org_members_subtitle_admins
import org.openforis.collectone.v2.web.resources.org_missing_body
import org.openforis.collectone.v2.web.resources.org_missing_title
import org.openforis.collectone.v2.web.resources.org_mobile_version_subtitle
import org.openforis.collectone.v2.web.resources.org_mobile_version_title
import org.openforis.collectone.v2.web.resources.org_nav_cost_quotas
import org.openforis.collectone.v2.web.resources.org_nav_global_library
import org.openforis.collectone.v2.web.resources.org_nav_governance
import org.openforis.collectone.v2.web.resources.org_no_description
import org.openforis.collectone.v2.web.resources.org_not_provided
import org.openforis.collectone.v2.web.resources.org_organization_label
import org.openforis.collectone.v2.web.resources.org_platform_admins
import org.openforis.collectone.v2.web.resources.org_platform_numbers_exclude
import org.openforis.collectone.v2.web.resources.org_platform_numbers_help
import org.openforis.collectone.v2.web.resources.org_platform_numbers_title
import org.openforis.collectone.v2.web.resources.org_profile_title
import org.openforis.collectone.v2.web.resources.org_published_body
import org.openforis.collectone.v2.web.resources.org_published_manager
import org.openforis.collectone.v2.web.resources.org_published_member
import org.openforis.collectone.v2.web.resources.org_published_see
import org.openforis.collectone.v2.web.resources.org_published_title
import org.openforis.collectone.v2.web.resources.org_purposes_subtitle
import org.openforis.collectone.v2.web.resources.org_purposes_subtitle_admins
import org.openforis.collectone.v2.web.resources.org_remove_member
import org.openforis.collectone.v2.web.resources.org_revoke_invite
import org.openforis.collectone.v2.web.resources.org_save
import org.openforis.collectone.v2.web.resources.org_surveys_empty
import org.openforis.collectone.v2.web.resources.org_surveys_empty_member
import org.openforis.collectone.v2.web.resources.org_surveys_subtitle_manager
import org.openforis.collectone.v2.web.resources.org_surveys_subtitle_member
import org.openforis.collectone.v2.web.resources.org_tab_cost_controls
import org.openforis.collectone.v2.web.resources.org_tab_details
import org.openforis.collectone.v2.web.resources.org_tab_dictionary
import org.openforis.collectone.v2.web.resources.org_tab_imagery
import org.openforis.collectone.v2.web.resources.org_tab_members
import org.openforis.collectone.v2.web.resources.org_tab_purposes
import org.openforis.collectone.v2.web.resources.org_tab_surveys
import org.openforis.collectone.v2.web.resources.org_tab_templates
import org.openforis.collectone.v2.web.resources.org_templates_subtitle
import org.openforis.collectone.v2.web.resources.org_templates_subtitle_admins
import org.openforis.collectone.v2.web.resources.org_unsaved_changes
import org.openforis.collectone.v2.web.ui.common.AcceptInviteDialog
import org.openforis.collectone.v2.web.ui.common.DEFAULT_LOGO_MAX_DIMENSION_PX
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.ImageFilePickResult
import org.openforis.collectone.v2.web.ui.common.ImageFilePicker
import org.openforis.collectone.v2.web.ui.common.NavItem
import org.openforis.collectone.v2.web.ui.common.OrganizationAvatar
import org.openforis.collectone.v2.web.ui.common.PaneScaffold
import org.openforis.collectone.v2.web.ui.common.PendingInviteLinkRow
import org.openforis.collectone.v2.web.ui.common.PersonRow
import org.openforis.collectone.v2.web.ui.common.SectionLabel
import org.openforis.collectone.v2.web.ui.common.SidePanelSeparator
import org.openforis.collectone.v2.web.ui.common.SidePanelSeparatorWidth
import org.openforis.collectone.v2.web.ui.common.WebHeaderContext
import org.openforis.collectone.v2.web.ui.common.WebHeaderSupportingText
import org.openforis.collectone.v2.web.ui.common.WizardOverviewHeroGraphic
import org.openforis.collectone.v2.web.ui.common.effectiveOrganizationLogoUrl
import org.openforis.collectone.v2.web.ui.common.openPlatformImageFilePicker
import org.openforis.collectone.v2.web.ui.surveys.CreateSurveyDialog
import org.openforis.collectone.v2.web.ui.surveys.CreateSurveyRequest
import org.openforis.collectone.v2.web.ui.surveys.WebSurveyCard
import org.openforis.collectone.v2.web.ui.surveys.WebSurveysList
import org.openforis.collectone.v2.web.ui.text.OrganizationMessage
import org.openforis.collectone.v2.web.ui.text.publishedRules
import org.openforis.collectone.v2.web.ui.text.resolve

/** The organization's logo, name, and summary line in the web header. */
@Composable
fun OrganizationHeaderContext(organization: Organization, surveyCount: Int) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (organization.isSynthetic) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(32.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.AdminPanelSettings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      WebHeaderContext(title = stringResource(Res.string.org_admin_panel)) {
        WebHeaderSupportingText(
          stringResource(Res.string.org_admin_panel_subtitle),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      OrganizationAvatar(name = organization.name, logoUrl = organization.logoUrl, size = 32.dp)
      WebHeaderContext(title = organization.name) {
        WebHeaderSupportingText(
          stringResource(Res.string.org_organization_label),
          color = MaterialTheme.colorScheme.primary,
        )
        WebHeaderSupportingText("·")
        WebHeaderSupportingText(
          organizationCountsText(
            memberCount = organization.activeMembers.size,
            surveyCount = surveyCount,
            isUnlisted = !organization.isListed,
          )
        )
      }
    }
  }
}

/**
 * One organization, laid out like the survey editor: a resizable left panel switches between
 * Organization details, Surveys, Members, Imagery, and the library's Purposes, Dictionary, and
 * Templates, and the content pane shows the selected section. Its impact numbers, which every
 * organization publishes, are on the Impact page (stringResource(Res.string.org_view_impact)).
 * Members can create surveys in it; Managers can also edit and delete the organization, invite
 * people, approve join requests, change roles, remove members, and edit its library. The last
 * Manager can't be demoted or removed.
 *
 * The page shows the open organization of [uiState] with its navigation panel, section panes,
 * notice banner, and stringResource(Res.string.org_create_survey) dialog.
 *
 * @param sidePanelWidthDp width of the left panel, shared with the dashboard and Survey editor.
 * @param header the page header; it receives the click handler of its
 *   stringResource(Res.string.org_create_survey) button.
 * @param onOpenImpact opens the Impact page on an organization's published numbers.
 * @param initialTab the section to open on, instead of the default one.
 * @param onTabChange receives each section the user switches to.
 */
@Composable
fun OrganizationPage(
  uiState: OrganizationUiState,
  actions: OrganizationActions,
  sidePanelWidthDp: Float,
  onSidePanelWidthChange: (Float) -> Unit,
  onOpenSurvey: (surveyId: String) -> Unit,
  onCreateSurvey: (CreateSurveyRequest) -> Unit,
  surveyLibrary: (organizationId: String?) -> ResolvedLibrary,
  header: @Composable (onCreateSurveyClick: () -> Unit) -> Unit,
  onOpenCreateSurveyWizard: ((organizationId: String?) -> Unit)? = null,
  onOpenImpact: (organizationId: String) -> Unit = {},
  isLoading: Boolean = false,
  initialTab: OrganizationTab? = null,
  onTabChange: (OrganizationTab) -> Unit = {},
  imageFilePicker: ImageFilePicker = ::openPlatformImageFilePicker,
) {
  val organization = uiState.openOrganization
  val isManager = organization != null && uiState.managesOrganization(organization)
  val isMember = organization != null && uiState.isMemberOf(organization)
  var tab by rememberOrganizationTab(organization, isManager, initialTab)
  var isCreatingSurvey by remember { mutableStateOf(false) }

  val triggerCreateSurvey: () -> Unit = {
    if (onOpenCreateSurveyWizard != null) {
      onOpenCreateSurveyWizard(organization?.id)
    } else {
      isCreatingSurvey = true
    }
  }

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    header(triggerCreateSurvey)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    if (organization == null) {
      OrganizationEmptyOrLoading(isLoading = isLoading, onBack = actions::openOrganizations)
      return@Column
    }

    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
      OrganizationNavigation(
        organization = organization,
        surveyCount = uiState.surveyCountInOrganization(organization.id),
        library = uiState.library,
        isManager = isManager,
        selected = tab,
        onSelect = {
          tab = it
          onTabChange(it)
        },
        modifier = Modifier.width(sidePanelWidthDp.dp).fillMaxHeight(),
      )
      SidePanelSeparator(
        widthDp = sidePanelWidthDp,
        onWidthChange = onSidePanelWidthChange,
        modifier = Modifier.width(SidePanelSeparatorWidth).fillMaxHeight(),
      )
      Surface(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
      ) {
        OrganizationTabContent(
          tab = tab,
          uiState = uiState,
          actions = actions,
          organization = organization,
          isManager = isManager,
          isMember = isMember,
          onOpenSurvey = onOpenSurvey,
          onCreateSurvey = triggerCreateSurvey,
          onOpenImpact = onOpenImpact,
          isLoading = isLoading,
          imageFilePicker = imageFilePicker,
        )
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

@Composable
private fun rememberOrganizationTab(
  organization: Organization?,
  isManager: Boolean,
  initialTab: OrganizationTab?,
) =
  remember(organization?.id) {
    val defaultTab =
      if (organization?.isSynthetic == true) OrganizationTab.MEMBERS else OrganizationTab.DETAILS
    val shownInitialTab = initialTab?.takeIf {
      organization != null && it.isShownFor(organization, isManager)
    }
    mutableStateOf(shownInitialTab ?: defaultTab)
  }

@Composable
private fun OrganizationEmptyOrLoading(isLoading: Boolean, onBack: () -> Unit) {
  if (isLoading) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
  } else {
    MissingOrganization(onBack = onBack)
  }
}

@Composable
private fun OrganizationTabContent(
  tab: OrganizationTab,
  uiState: OrganizationUiState,
  actions: OrganizationActions,
  organization: Organization,
  isManager: Boolean,
  isMember: Boolean,
  onOpenSurvey: (surveyId: String) -> Unit,
  onCreateSurvey: () -> Unit,
  onOpenImpact: (organizationId: String) -> Unit,
  isLoading: Boolean,
  imageFilePicker: ImageFilePicker,
) {
  PaneScaffold(
    title = organizationTabTitle(tab, organization),
    subtitle = organizationTabSubtitle(tab, organization, isManager),
    titleTrailing =
      if (organizationTabShowsAdminBadge(tab, organization)) ({ AdminOnlyBadge() }) else null,
  ) {
    NoticeSlot(uiState, actions)
    when (tab) {
      OrganizationTab.DETAILS ->
        DetailsPane(uiState, actions, organization, isManager, onOpenImpact, imageFilePicker)
      OrganizationTab.SURVEYS ->
        SurveysPane(uiState, organization, isMember, onOpenSurvey, onCreateSurvey, isLoading)
      OrganizationTab.MEMBERS -> MembersPane(uiState, actions, organization, isManager)
      OrganizationTab.IMAGERY_SOURCES -> ImagerySourcesPane(actions, organization, isManager)
      OrganizationTab.PURPOSES -> PurposesPane(uiState.library, actions, organization, isLoading)
      OrganizationTab.DICTIONARY ->
        DictionaryPane(uiState.library, actions, organization, isLoading)
      OrganizationTab.TEMPLATES -> TemplatesPane(uiState.library, actions, organization, isLoading)
      OrganizationTab.COST_CONTROLS -> CostControlsPane(organization, isManager, actions)
      OrganizationTab.COST_TRACKING -> CostTrackingPane(uiState, isLoading)
      OrganizationTab.MOBILE_APP -> MobileClientVersionPane(LocalClientVersionRepository.current)
    }
  }
}

private fun organizationTabShowsAdminBadge(
  tab: OrganizationTab,
  organization: Organization,
): Boolean =
  when (tab) {
    OrganizationTab.COST_TRACKING,
    OrganizationTab.MOBILE_APP -> true
    OrganizationTab.DETAILS,
    OrganizationTab.SURVEYS -> false
    else -> organization.isSynthetic
  }

@Composable
private fun organizationTabTitle(tab: OrganizationTab, organization: Organization): String =
  when (tab) {
    OrganizationTab.DETAILS -> stringResource(Res.string.org_tab_details)
    OrganizationTab.SURVEYS -> stringResource(Res.string.org_tab_surveys)
    OrganizationTab.MEMBERS ->
      if (organization.isSynthetic) stringResource(Res.string.org_platform_admins)
      else stringResource(Res.string.org_tab_members)
    OrganizationTab.IMAGERY_SOURCES -> stringResource(Res.string.org_tab_imagery)
    OrganizationTab.PURPOSES -> stringResource(Res.string.org_tab_purposes)
    OrganizationTab.DICTIONARY -> stringResource(Res.string.org_tab_dictionary)
    OrganizationTab.TEMPLATES -> stringResource(Res.string.org_tab_templates)
    OrganizationTab.COST_CONTROLS ->
      if (organization.isSynthetic) stringResource(Res.string.org_cost_controls_title_global)
      else stringResource(Res.string.org_tab_cost_controls)
    OrganizationTab.COST_TRACKING -> stringResource(Res.string.org_cost_tracking_title)
    OrganizationTab.MOBILE_APP -> stringResource(Res.string.org_mobile_version_title)
  }

@Composable
private fun organizationTabSubtitle(
  tab: OrganizationTab,
  organization: Organization,
  isManager: Boolean,
): String =
  if (organization.isSynthetic) {
    syntheticOrganizationTabSubtitle(tab)
  } else {
    regularOrganizationTabSubtitle(tab, organization.name, isManager)
  }

@Composable
private fun syntheticOrganizationTabSubtitle(tab: OrganizationTab): String =
  when (tab) {
    OrganizationTab.MEMBERS -> stringResource(Res.string.org_members_subtitle_admins)
    OrganizationTab.IMAGERY_SOURCES -> stringResource(Res.string.org_imagery_subtitle_admins)
    OrganizationTab.PURPOSES -> stringResource(Res.string.org_purposes_subtitle_admins)
    OrganizationTab.DICTIONARY -> stringResource(Res.string.org_dictionary_subtitle_admins)
    OrganizationTab.TEMPLATES -> stringResource(Res.string.org_templates_subtitle_admins)
    OrganizationTab.COST_CONTROLS -> stringResource(Res.string.org_cost_controls_subtitle_global)
    OrganizationTab.COST_TRACKING -> stringResource(Res.string.org_cost_tracking_subtitle)
    OrganizationTab.MOBILE_APP -> stringResource(Res.string.org_mobile_version_subtitle)
    OrganizationTab.DETAILS,
    OrganizationTab.SURVEYS -> ""
  }

@Composable
private fun regularOrganizationTabSubtitle(
  tab: OrganizationTab,
  organizationName: String,
  isManager: Boolean,
): String =
  when (tab) {
    OrganizationTab.DETAILS ->
      stringResource(
        if (isManager) Res.string.org_details_subtitle_manager
        else Res.string.org_details_subtitle_member,
        organizationName,
      )
    OrganizationTab.SURVEYS ->
      stringResource(
        if (isManager) Res.string.org_surveys_subtitle_manager
        else Res.string.org_surveys_subtitle_member,
        organizationName,
      )
    OrganizationTab.MEMBERS -> stringResource(Res.string.org_members_subtitle, organizationName)
    OrganizationTab.IMAGERY_SOURCES ->
      stringResource(
        if (isManager) Res.string.org_imagery_subtitle_manager
        else Res.string.org_imagery_subtitle_member,
        organizationName,
      )
    OrganizationTab.PURPOSES -> stringResource(Res.string.org_purposes_subtitle, organizationName)
    OrganizationTab.DICTIONARY ->
      stringResource(Res.string.org_dictionary_subtitle, organizationName)
    OrganizationTab.TEMPLATES -> stringResource(Res.string.org_templates_subtitle, organizationName)
    OrganizationTab.COST_CONTROLS ->
      stringResource(
        if (isManager) Res.string.org_cost_controls_subtitle_manager
        else Res.string.org_cost_controls_subtitle_member,
        organizationName,
      )
    OrganizationTab.COST_TRACKING -> stringResource(Res.string.org_cost_tracking_subtitle)
    OrganizationTab.MOBILE_APP -> stringResource(Res.string.org_mobile_version_subtitle)
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
  selected: OrganizationTab,
  onSelect: (OrganizationTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  val memberCount =
    organization.activeMembers.size + (if (isManager) organization.pendingRequests.size else 0)
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      if (!organization.isSynthetic) {
        NavItem(
          label = stringResource(OrganizationTab.DETAILS.label),
          icon = Icons.Outlined.Info,
          selected = selected == OrganizationTab.DETAILS,
          onClick = { onSelect(OrganizationTab.DETAILS) },
        )
        NavItem(
          label = stringResource(OrganizationTab.SURVEYS.label),
          icon = Icons.Outlined.Map,
          selected = selected == OrganizationTab.SURVEYS,
          onClick = { onSelect(OrganizationTab.SURVEYS) },
          trailing = "$surveyCount",
        )
      }
      NavItem(
        label =
          if (organization.isSynthetic) stringResource(Res.string.org_platform_admins)
          else stringResource(OrganizationTab.MEMBERS.label),
        icon =
          if (organization.isSynthetic) Icons.Outlined.AdminPanelSettings else Icons.Outlined.Group,
        selected = selected == OrganizationTab.MEMBERS,
        onClick = { onSelect(OrganizationTab.MEMBERS) },
        trailing = "$memberCount",
      )
      NavItem(
        label = stringResource(OrganizationTab.IMAGERY_SOURCES.label),
        icon = Icons.Outlined.Layers,
        selected = selected == OrganizationTab.IMAGERY_SOURCES,
        onClick = { onSelect(OrganizationTab.IMAGERY_SOURCES) },
        trailing = "${organization.imagerySources.size}",
      )
      if (organization.isSynthetic) {
        Spacer(Modifier.height(8.dp))
        SectionLabel(stringResource(Res.string.org_nav_global_library))
      }
      NavItem(
        label = stringResource(OrganizationTab.PURPOSES.label),
        icon = Icons.Outlined.Flag,
        selected = selected == OrganizationTab.PURPOSES,
        onClick = { onSelect(OrganizationTab.PURPOSES) },
        trailing = "${library.purposePacks.size}",
      )
      NavItem(
        label = stringResource(OrganizationTab.TEMPLATES.label),
        icon = Icons.Outlined.Description,
        selected = selected == OrganizationTab.TEMPLATES,
        onClick = { onSelect(OrganizationTab.TEMPLATES) },
        trailing = "${library.formTemplates.size}",
      )
      NavItem(
        label = stringResource(OrganizationTab.DICTIONARY.label),
        icon = Icons.Outlined.Book,
        selected = selected == OrganizationTab.DICTIONARY,
        onClick = { onSelect(OrganizationTab.DICTIONARY) },
        trailing = "${library.totalConceptCount}",
      )
      if (organization.isSynthetic) {
        Spacer(Modifier.height(8.dp))
        SectionLabel(stringResource(Res.string.org_nav_cost_quotas))
        NavItem(
          label = stringResource(OrganizationTab.COST_CONTROLS.label),
          icon = Icons.Outlined.Tune,
          selected = selected == OrganizationTab.COST_CONTROLS,
          onClick = { onSelect(OrganizationTab.COST_CONTROLS) },
        )
        NavItem(
          label = stringResource(OrganizationTab.COST_TRACKING.label),
          icon = Icons.Outlined.Insights,
          selected = selected == OrganizationTab.COST_TRACKING,
          onClick = { onSelect(OrganizationTab.COST_TRACKING) },
        )
        NavItem(
          label = stringResource(OrganizationTab.MOBILE_APP.label),
          icon = Icons.Outlined.SystemUpdate,
          selected = selected == OrganizationTab.MOBILE_APP,
          onClick = { onSelect(OrganizationTab.MOBILE_APP) },
        )
      } else if (isManager) {
        Spacer(Modifier.height(8.dp))
        SectionLabel(stringResource(Res.string.org_nav_governance))
        NavItem(
          label = stringResource(OrganizationTab.COST_CONTROLS.label),
          icon = Icons.Outlined.Tune,
          selected = selected == OrganizationTab.COST_CONTROLS,
          onClick = { onSelect(OrganizationTab.COST_CONTROLS) },
        )
      }
    }
  }
}

/** Whether [OrganizationNavigation] offers this section for [organization]. */
private fun OrganizationTab.isShownFor(organization: Organization, isManager: Boolean): Boolean =
  when (this) {
    OrganizationTab.DETAILS,
    OrganizationTab.SURVEYS -> !organization.isSynthetic
    OrganizationTab.COST_CONTROLS -> organization.isSynthetic || isManager
    OrganizationTab.COST_TRACKING,
    OrganizationTab.MOBILE_APP -> organization.isSynthetic
    OrganizationTab.MEMBERS,
    OrganizationTab.IMAGERY_SOURCES,
    OrganizationTab.PURPOSES,
    OrganizationTab.DICTIONARY,
    OrganizationTab.TEMPLATES -> true
  }

internal val OrganizationPaneMaxWidth = 760.dp

@Composable
private fun MissingOrganization(onBack: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(48.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(stringResource(Res.string.org_missing_title), style = MaterialTheme.typography.titleMedium)
    Text(
      stringResource(Res.string.org_missing_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onBack) { Text(stringResource(Res.string.org_back_to_organizations)) }
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
  isLoading: Boolean = false,
) {
  val surveys = uiState.surveysInOrganization(organization.id)
  if (surveys.isEmpty()) {
    if (isLoading) {
      Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
      return
    }
    Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      WizardOverviewHeroGraphic(modifier = Modifier.widthIn(max = 460.dp))
      Text(
        if (isMember) stringResource(Res.string.org_surveys_empty_member, organization.name)
        else stringResource(Res.string.org_surveys_empty),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (isMember) {
        Button(onClick = onCreateSurvey) {
          Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text(stringResource(Res.string.org_create_survey))
        }
      }
    }
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
        modifier = Modifier.fillMaxRowHeight(),
        outlined = true,
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
  val enterNameError = stringResource(Res.string.org_error_enter_name)

  organization
    .member(acceptingEmail.orEmpty())
    ?.takeIf { it.status == MembershipStatus.INVITED }
    ?.let { invitee ->
      AcceptInviteDialog(
        title = organization.name,
        inviteeEmail = invitee.email,
        roleLabel = stringResource(invitee.role.labelRes),
        peopleListName = stringResource(Res.string.org_members_page_name),
        onAccept = { name, photo ->
          // The only refusal is a blank name (InviteOrganizationMemberUseCase.acceptError).
          actions.acceptInvite(organization.id, invitee.email, name, photo)?.let {
            (it as? OrganizationMessage.Text)?.text ?: enterNameError
          }
        },
        onDismiss = { acceptingEmail = null },
      )
    }
  if (leaving) {
    AlertDialog(
      onDismissRequest = { leaving = false },
      title = { Text(stringResource(Res.string.org_leave_title, organization.name)) },
      text = { Text(stringResource(Res.string.org_leave_body)) },
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
          Text(stringResource(Res.string.org_leave))
        }
      },
      dismissButton = {
        TextButton(onClick = { leaving = false }) { Text(stringResource(Res.string.org_cancel)) }
      },
    )
  }

  Column(
    modifier = Modifier.widthIn(max = OrganizationPaneMaxWidth),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    if (isManager && view.requests.isNotEmpty()) {
      MembersCard(stringResource(Res.string.org_members_requests_title)) {
        Text(
          stringResource(Res.string.org_members_requests_body, organization.name),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        view.requests.forEach { person ->
          key(person.email) {
            PersonRow(
              displayName = person.displayName,
              email = person.email,
              photoUrl = person.profile?.photoUrl,
              detail = stringResource(Res.string.org_members_asked_to_join),
              pending = true,
              pendingLabel = stringResource(Res.string.org_members_requested),
            ) {
              OutlinedButton(onClick = { actions.removeMember(organization.id, person.email) }) {
                Text(stringResource(Res.string.org_decline))
              }
              Spacer(Modifier.width(8.dp))
              Button(onClick = { actions.approveRequest(organization.id, person.email) }) {
                Icon(Icons.Outlined.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.org_approve))
              }
            }
          }
        }
      }
    }

    MembersCard(
      if (organization.isSynthetic) stringResource(Res.string.org_platform_admins)
      else stringResource(Res.string.org_tab_members)
    ) {
      if (isManager) InviteMemberRow(actions, organization)
      if (organization.isSynthetic) {
        Text(
          stringResource(Res.string.org_members_admins_help),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      view.active.forEach { person ->
        key(person.email) {
          val isSelf = uiState.isSelf(person.email)
          val canChange = uiState.canChangeMember(organization, person)
          PersonRow(
            displayName =
              if (isSelf) stringResource(Res.string.org_member_you, person.displayName)
              else person.displayName,
            email = person.email,
            photoUrl = person.profile?.photoUrl,
            detail =
              if (person.role == OrganizationRole.MANAGER)
                stringResource(OrganizationRole.MANAGER.descriptionRes)
              else stringResource(OrganizationRole.MEMBER.descriptionRes),
          ) {
            if (isManager) {
              Box(modifier = Modifier.width(170.dp)) {
                DropdownSelector(
                  label = stringResource(Res.string.org_field_role),
                  selectedText = stringResource(person.role.labelRes),
                  options =
                    if (organization.isSynthetic) listOf(OrganizationRole.MANAGER)
                    else OrganizationRole.entries,
                  optionText = { stringResource(it.labelRes) },
                  onSelect = { actions.setMemberRole(organization.id, person.email, it) },
                )
              }
              if (!isSelf) {
                IconButton(
                  onClick = { actions.removeMember(organization.id, person.email) },
                  enabled = canChange,
                ) {
                  Icon(
                    Icons.Outlined.Close,
                    contentDescription =
                      stringResource(Res.string.org_remove_member, person.displayName),
                  )
                }
              }
            } else {
              Text(
                stringResource(person.role.labelRes),
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
          stringResource(Res.string.org_members_manager_help),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (isManager && view.invited.isNotEmpty()) {
      MembersCard(stringResource(Res.string.org_members_invited_title)) {
        view.invited.forEach { person ->
          key(person.email) {
            PersonRow(
              displayName = person.displayName,
              email = person.email,
              photoUrl = person.profile?.photoUrl,
              pending = true,
              detail =
                stringResource(
                  Res.string.org_members_invite_pending,
                  stringResource(person.role.labelRes),
                ),
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
                Icon(
                  Icons.Outlined.Close,
                  contentDescription = stringResource(Res.string.org_revoke_invite, person.email),
                )
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
        Text(stringResource(Res.string.org_leave_organization))
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
  var error by remember { mutableStateOf<OrganizationMessage?>(null) }
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
    OutlinedTextField(
      value = email,
      onValueChange = {
        email = it
        error = null
      },
      label = { Text(stringResource(Res.string.org_invite_label)) },
      singleLine = true,
      isError = error != null,
      supportingText = error?.let { { Text(it.asString()) } },
      modifier = Modifier.weight(1f),
    )
    Box(modifier = Modifier.width(170.dp).padding(top = 6.dp)) {
      DropdownSelector(
        label = stringResource(Res.string.org_field_role),
        selectedText = stringResource(role.labelRes),
        options = roles,
        optionText = { stringResource(it.labelRes) },
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
      Text(stringResource(Res.string.org_invite))
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
  onOpenImpact: (organizationId: String) -> Unit,
  imageFilePicker: ImageFilePicker = ::openPlatformImageFilePicker,
) {
  Column(
    modifier = Modifier.widthIn(max = OrganizationPaneMaxWidth),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    if (isManager) {
      EditableDetailsCard(
        actions = actions,
        organization = organization,
        isSaving = uiState.isSaving,
        imageFilePicker = imageFilePicker,
      )
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
      PublishedNumbersCard(
        organization = organization,
        isManager = isManager,
        onOpenImpact = { onOpenImpact(organization.id) },
      )
      PlatformNumbersCard(
        organization = organization,
        isManager = isManager,
        onExcludeChange = { actions.setExcludeFromPlatformAggregates(organization.id, it) },
      )
    }
  }
}

/**
 * What the organization publishes: anonymous totals from its surveys, which anyone signed in to
 * Collect One can follow on the Impact page. Every organization publishes them; this card makes
 * that clear to its members and links to what's published.
 */
@Composable
internal fun PublishedNumbersCard(
  organization: Organization,
  isManager: Boolean,
  onOpenImpact: () -> Unit,
) {
  DetailsCard(stringResource(Res.string.org_published_title)) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Icon(
        Icons.Outlined.Public,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          if (isManager) {
            stringResource(Res.string.org_published_manager, organization.name)
          } else {
            stringResource(Res.string.org_published_member, organization.name)
          },
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          stringResource(Res.string.org_published_body, publishedRules()),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    OutlinedButton(onClick = onOpenImpact) {
      Icon(Icons.Outlined.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text(stringResource(Res.string.org_published_see))
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
  DetailsCard(stringResource(Res.string.org_platform_numbers_title)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          stringResource(Res.string.org_platform_numbers_exclude),
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          stringResource(Res.string.org_platform_numbers_help),
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
        stringResource(Res.string.org_ask_manager_change),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private data class EditableOrganizationDraft(
  val logoUrl: String?,
  val name: String,
  val description: String,
  val websiteUrl: String,
  val isListed: Boolean,
  val type: OrganizationType?,
  val countryCode: String?,
)

@Composable
private fun EditableDetailsCard(
  actions: OrganizationActions,
  organization: Organization,
  isSaving: Boolean = false,
  imageFilePicker: ImageFilePicker = ::openPlatformImageFilePicker,
) {
  val initialLogoUrl = effectiveOrganizationLogoUrl(organization.logoUrl)
  val initialDraft =
    remember(organization, initialLogoUrl) {
      EditableOrganizationDraft(
        logoUrl = initialLogoUrl,
        name = organization.name,
        description = organization.description,
        websiteUrl = organization.websiteUrl,
        isListed = organization.isListed,
        type = organization.organizationType,
        countryCode = organization.countryCode,
      )
    }
  var draft by remember(organization.id, initialDraft) { mutableStateOf(initialDraft) }
  var logoError by remember(organization.id, initialLogoUrl) { mutableStateOf<UiText?>(null) }
  val isDirty = draft != initialDraft

  DetailsCard(stringResource(Res.string.org_profile_title)) {
    if (!organization.isSynthetic) {
      OrganizationLogoEditorRow(
        displayName = draft.name.ifBlank { organization.name },
        logoUrl = draft.logoUrl,
        logoError = logoError,
        isSaving = isSaving,
        imageFilePicker = imageFilePicker,
        onLogoChange = { newUrl, newError ->
          draft = draft.copy(logoUrl = newUrl)
          logoError = newError
        },
      )
    }
    EditableDetailsTextFields(
      name = draft.name,
      onNameChange = { draft = draft.copy(name = it) },
      description = draft.description,
      onDescriptionChange = { draft = draft.copy(description = it) },
      websiteUrl = draft.websiteUrl,
      onWebsiteUrlChange = { draft = draft.copy(websiteUrl = it) },
    )
    // "All users" is platform-wide, so it has no type or country.
    if (organization.hasTypeAndCountry) {
      OrganizationTypeDropdown(type = draft.type, onTypeChange = { draft = draft.copy(type = it) })
      CountryPicker(
        countryCode = draft.countryCode,
        onCountryCodeChange = { draft = draft.copy(countryCode = it) },
      )
    }
    // "All users" is never listed in the directory.
    if (!organization.isSynthetic) {
      OrganizationDirectoryListingCheckbox(
        isListed = draft.isListed,
        onCheckedChange = { draft = draft.copy(isListed = it) },
      )
    }
    EditableDetailsActionsRow(
      isDirty = isDirty,
      canSave = isDirty && draft.name.isNotBlank() && !isSaving,
      isSaving = isSaving,
      onSave = {
        actions.updateOrganizationDetails(
          organizationId = organization.id,
          name = draft.name,
          description = draft.description,
          websiteUrl = draft.websiteUrl,
          isListed = draft.isListed,
          organizationType = draft.type,
          countryCode = draft.countryCode,
          logoUrl = draft.logoUrl,
        )
      },
      onDiscard = {
        draft = initialDraft
        logoError = null
      },
    )
  }
}

@Composable
private fun OrganizationLogoEditorRow(
  displayName: String,
  logoUrl: String?,
  logoError: UiText?,
  isSaving: Boolean,
  imageFilePicker: ImageFilePicker,
  onLogoChange: (logoUrl: String?, logoError: UiText?) -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    OrganizationAvatar(name = displayName, logoUrl = logoUrl, size = 64.dp)
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        stringResource(Res.string.org_field_logo),
        style = MaterialTheme.typography.labelLarge,
      )
      Text(
        stringResource(Res.string.org_details_logo_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp),
      ) {
        OutlinedButton(
          onClick = {
            onLogoChange(logoUrl, null)
            imageFilePicker(DEFAULT_LOGO_MAX_DIMENSION_PX) { result ->
              when (result) {
                is ImageFilePickResult.Picked -> onLogoChange(result.dataUrl, null)
                is ImageFilePickResult.Failed -> onLogoChange(logoUrl, result.message)
                ImageFilePickResult.Cancelled -> Unit
              }
            }
          },
          enabled = !isSaving,
        ) {
          Icon(
            imageVector = Icons.Outlined.Upload,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text(
            stringResource(
              if (logoUrl != null) Res.string.org_logo_change else Res.string.org_logo_upload
            )
          )
        }
        if (logoUrl != null) {
          TextButton(onClick = { onLogoChange(null, null) }, enabled = !isSaving) {
            Text(stringResource(Res.string.org_logo_remove))
          }
        }
      }
      logoError?.let { error ->
        Text(
          text = error.resolve(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun EditableDetailsTextFields(
  name: String,
  onNameChange: (String) -> Unit,
  description: String,
  onDescriptionChange: (String) -> Unit,
  websiteUrl: String,
  onWebsiteUrlChange: (String) -> Unit,
) {
  OutlinedTextField(
    value = name,
    onValueChange = onNameChange,
    label = { Text(stringResource(Res.string.org_field_name)) },
    singleLine = true,
    isError = name.isBlank(),
    supportingText =
      if (name.isBlank()) ({ Text(stringResource(Res.string.org_details_name_required)) })
      else null,
    modifier = Modifier.fillMaxWidth(),
  )
  OutlinedTextField(
    value = description,
    onValueChange = onDescriptionChange,
    label = { Text(stringResource(Res.string.org_field_description)) },
    supportingText = { Text(stringResource(Res.string.org_details_description_help)) },
    minLines = 3,
    modifier = Modifier.fillMaxWidth(),
  )
  OutlinedTextField(
    value = websiteUrl,
    onValueChange = onWebsiteUrlChange,
    label = { Text(stringResource(Res.string.org_field_website)) },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun OrganizationDirectoryListingCheckbox(
  isListed: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(checked = isListed, onCheckedChange = onCheckedChange)
    Spacer(Modifier.width(4.dp))
    Column {
      Text(
        stringResource(Res.string.org_list_in_directory),
        style = MaterialTheme.typography.bodyMedium,
      )
      Text(
        stringResource(Res.string.org_details_listed_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun EditableDetailsActionsRow(
  isDirty: Boolean,
  canSave: Boolean,
  isSaving: Boolean,
  onSave: () -> Unit,
  onDiscard: () -> Unit,
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Button(onClick = onSave, enabled = canSave) {
      if (isSaving) {
        CircularProgressIndicator(
          modifier = Modifier.size(16.dp),
          strokeWidth = 2.dp,
          color = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(Modifier.width(8.dp))
      }
      Text(stringResource(Res.string.org_save))
    }
    TextButton(onClick = onDiscard, enabled = isDirty) {
      Text(stringResource(Res.string.org_discard))
    }
    if (isDirty) {
      Text(
        stringResource(Res.string.org_unsaved_changes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun ReadOnlyDetailsCard(organization: Organization) {
  DetailsCard(stringResource(Res.string.org_profile_title)) {
    DetailRow(stringResource(Res.string.org_field_name_short), organization.name)
    DetailRow(
      stringResource(Res.string.org_field_description),
      organization.description.ifBlank { stringResource(Res.string.org_no_description) },
    )
    DetailRow(
      stringResource(Res.string.org_field_website),
      organization.websiteUrl.ifBlank { stringResource(Res.string.org_not_provided) },
    )
    if (organization.hasTypeAndCountry) {
      DetailRow(
        stringResource(Res.string.org_field_type),
        organizationTypeText(organization.organizationType),
      )
      DetailRow(stringResource(Res.string.org_field_country), countryText(organization.countryCode))
    }
    DetailRow(
      stringResource(Res.string.org_field_directory),
      if (organization.isListed) stringResource(Res.string.org_directory_listed)
      else stringResource(Res.string.org_directory_unlisted),
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
      title = { Text(stringResource(Res.string.org_delete_item_title, organization.name)) },
      text = {
        Text(
          if (surveyCount > 0) {
            pluralStringResource(Res.plurals.org_delete_body_surveys, surveyCount, surveyCount)
          } else {
            stringResource(Res.string.org_delete_body_no_surveys)
          }
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
          Text(stringResource(Res.string.org_delete_organization))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmingDelete = false }) {
          Text(stringResource(Res.string.org_cancel))
        }
      },
    )
  }

  DetailsCard(stringResource(Res.string.org_danger_zone)) {
    Text(
      stringResource(Res.string.org_danger_zone_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
      onClick = { confirmingDelete = true },
      colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
      Text(stringResource(Res.string.org_delete_organization), fontWeight = FontWeight.SemiBold)
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
