/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.web.ui.impact

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.ui.common.AdminOnlyBadge
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.Checkbox
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.web.domain.model.ImpactFormat
import org.openforis.collectone.v2.web.domain.model.ImpactSummary
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_impact_adding_up
import org.openforis.collectone.v2.web.resources.wizard_impact_all_of_collect_one
import org.openforis.collectone.v2.web.resources.wizard_impact_all_of_collect_one_supporting
import org.openforis.collectone.v2.web.resources.wizard_impact_cancel
import org.openforis.collectone.v2.web.resources.wizard_impact_choose_organizations
import org.openforis.collectone.v2.web.resources.wizard_impact_choose_organizations_body
import org.openforis.collectone.v2.web.resources.wizard_impact_dashboard_subtitle
import org.openforis.collectone.v2.web.resources.wizard_impact_dashboard_title
import org.openforis.collectone.v2.web.resources.wizard_impact_explainer_body
import org.openforis.collectone.v2.web.resources.wizard_impact_explainer_title
import org.openforis.collectone.v2.web.resources.wizard_impact_follow_more_organizations
import org.openforis.collectone.v2.web.resources.wizard_impact_map_features_label
import org.openforis.collectone.v2.web.resources.wizard_impact_member
import org.openforis.collectone.v2.web.resources.wizard_impact_no_followed_organizations
import org.openforis.collectone.v2.web.resources.wizard_impact_no_numbers_yet
import org.openforis.collectone.v2.web.resources.wizard_impact_no_organizations_match
import org.openforis.collectone.v2.web.resources.wizard_impact_not_enough_data_short
import org.openforis.collectone.v2.web.resources.wizard_impact_notice_body
import org.openforis.collectone.v2.web.resources.wizard_impact_notice_body_manager
import org.openforis.collectone.v2.web.resources.wizard_impact_notice_full_manager
import org.openforis.collectone.v2.web.resources.wizard_impact_notice_full_member
import org.openforis.collectone.v2.web.resources.wizard_impact_notice_published_by
import org.openforis.collectone.v2.web.resources.wizard_impact_notice_published_member
import org.openforis.collectone.v2.web.resources.wizard_impact_organization
import org.openforis.collectone.v2.web.resources.wizard_impact_organization_page
import org.openforis.collectone.v2.web.resources.wizard_impact_organization_published_subtitle
import org.openforis.collectone.v2.web.resources.wizard_impact_organization_subtitle
import org.openforis.collectone.v2.web.resources.wizard_impact_organizations_you_follow
import org.openforis.collectone.v2.web.resources.wizard_impact_overview
import org.openforis.collectone.v2.web.resources.wizard_impact_platform_notice_body
import org.openforis.collectone.v2.web.resources.wizard_impact_platform_notice_full
import org.openforis.collectone.v2.web.resources.wizard_impact_platform_notice_published
import org.openforis.collectone.v2.web.resources.wizard_impact_save
import org.openforis.collectone.v2.web.resources.wizard_impact_search_organizations
import org.openforis.collectone.v2.web.resources.wizard_impact_show_published
import org.openforis.collectone.v2.web.resources.wizard_impact_stop_following
import org.openforis.collectone.v2.web.resources.wizard_impact_unfollow
import org.openforis.collectone.v2.web.resources.wizard_impact_unique_hectares
import org.openforis.collectone.v2.web.resources.wizard_impact_unlisted
import org.openforis.collectone.v2.web.resources.wizard_impact_you_manage
import org.openforis.collectone.v2.web.ui.common.NavItem
import org.openforis.collectone.v2.web.ui.common.OrganizationAvatar
import org.openforis.collectone.v2.web.ui.common.SidePanelSeparator
import org.openforis.collectone.v2.web.ui.common.SidePanelSeparatorWidth
import org.openforis.collectone.v2.web.ui.text.impactMeasurementText
import org.openforis.collectone.v2.web.ui.text.publishedRules

/**
 * The Impact page (`#impact`): published impact numbers of the organizations the signed-in user
 * chose to follow, and of all of Collect One.
 *
 * Every organization publishes anonymous totals from its surveys to everyone signed in; its
 * submissions, map features, and answers are never shown. Members of an organization also see its
 * full numbers, and can switch to the published view to check what everyone else sees.
 *
 * The page has a resizable left panel with the overview, all of Collect One, and the organizations
 * on the user's dashboard, and the selected view in the content pane.
 *
 * @param header the page header; it receives the click handler of its "Choose organizations"
 *   button.
 * @param onFocus shows a view (its route also updates the URL hash).
 * @param onOpenOrganization opens an organization's page (offered to its members).
 */
@Composable
fun ImpactDashboardPage(
  uiState: ImpactUiState,
  actions: ImpactActions,
  sidePanelWidthDp: Float,
  onSidePanelWidthChange: (Float) -> Unit,
  header: @Composable (onChooseOrganizations: () -> Unit) -> Unit,
  onFocus: (ImpactDashboardFocus) -> Unit = actions::focusDashboard,
  onOpenOrganization: (organizationId: String) -> Unit = {},
) {
  val dashboard = uiState.dashboard
  var isChoosing by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxSize()) {
    header { isChoosing = true }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
      ImpactNavigation(
        uiState = uiState,
        onFocus = onFocus,
        onChooseOrganizations = { isChoosing = true },
        modifier = Modifier.width(sidePanelWidthDp.dp).fillMaxHeight(),
      )
      SidePanelSeparator(
        widthDp = sidePanelWidthDp,
        onWidthChange = onSidePanelWidthChange,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(SidePanelSeparatorWidth).fillMaxHeight(),
      )
      Surface(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
      ) {
        when (val focus = dashboard.focus) {
          ImpactDashboardFocus.Overview ->
            ImpactOverviewPane(
              uiState = uiState,
              onFocus = onFocus,
              onRemove = actions::removeFromDashboard,
              onChooseOrganizations = { isChoosing = true },
            )
          ImpactDashboardFocus.Platform ->
            GlobalImpactPane(
              summary = uiState.global,
              isLoading = uiState.isLoading,
              onDownload = actions::downloadGlobalSummary,
              banner = { PlatformNotice(isFullView = uiState.global?.isPublished == false) },
            )
          is ImpactDashboardFocus.OrganizationFocus -> {
            val entry = dashboard.organization(focus.organizationId)
            if (entry != null) {
              OrganizationDashboardPane(
                entry = entry,
                uiState = uiState,
                actions = actions,
                onOpenOrganization = onOpenOrganization,
                onRemove = {
                  actions.removeFromDashboard(entry.id)
                  onFocus(ImpactDashboardFocus.Overview)
                },
              )
            }
          }
        }
      }
    }
  }

  if (isChoosing) {
    ChooseOrganizationsDialog(
      available = dashboard.available,
      selectedIds = dashboard.selectedIds,
      onSave = { ids ->
        isChoosing = false
        actions.setDashboardOrganizations(ids)
      },
      onDismiss = { isChoosing = false },
    )
  }
}

/** Left panel: overview, all of Collect One, and the organizations on the user's dashboard. */
@Composable
private fun ImpactNavigation(
  uiState: ImpactUiState,
  onFocus: (ImpactDashboardFocus) -> Unit,
  onChooseOrganizations: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val dashboard = uiState.dashboard
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      NavItem(
        label = stringResource(Res.string.wizard_impact_overview),
        icon = Icons.Outlined.Dashboard,
        selected = dashboard.focus == ImpactDashboardFocus.Overview,
        onClick = { onFocus(ImpactDashboardFocus.Overview) },
      )
      NavItem(
        label = stringResource(Res.string.wizard_impact_all_of_collect_one),
        icon = Icons.Outlined.Public,
        selected = dashboard.focus == ImpactDashboardFocus.Platform,
        onClick = { onFocus(ImpactDashboardFocus.Platform) },
      )
      Spacer(Modifier.height(12.dp))
      Text(
        stringResource(Res.string.wizard_impact_organizations_you_follow),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
      )
      dashboard.selected.forEach { entry ->
        NavItem(
          label = entry.organization.name,
          icon = if (entry.isMember) Icons.Outlined.Groups else Icons.Outlined.Insights,
          selected =
            dashboard.focus == ImpactDashboardFocus.OrganizationFocus(entry.organization.id),
          onClick = { onFocus(ImpactDashboardFocus.OrganizationFocus(entry.organization.id)) },
          trailing = if (entry.isMember) stringResource(Res.string.wizard_impact_member) else null,
        )
      }
      if (dashboard.selected.isEmpty() && !uiState.isLoading) {
        Text(
          stringResource(Res.string.wizard_impact_no_followed_organizations),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
      }
      TextButton(onClick = onChooseOrganizations, modifier = Modifier.padding(start = 4.dp)) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.wizard_impact_choose_organizations))
      }
    }
  }
}

/** Headline published numbers of all of Collect One and of every organization the user follows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImpactOverviewPane(
  uiState: ImpactUiState,
  onFocus: (ImpactDashboardFocus) -> Unit,
  onRemove: (organizationId: String) -> Unit,
  onChooseOrganizations: () -> Unit,
) {
  val dashboard = uiState.dashboard
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Column(modifier = Modifier.widthIn(max = OverviewMaxWidth)) {
      Text(
        stringResource(Res.string.wizard_impact_dashboard_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )
      Text(
        stringResource(Res.string.wizard_impact_dashboard_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Box(modifier = Modifier.widthIn(max = OverviewMaxWidth)) { PublishingExplainer() }
    FlowRow(
      modifier = Modifier.widthIn(max = OverviewMaxWidth),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      OverviewCard(
        title = stringResource(Res.string.wizard_impact_all_of_collect_one),
        supporting = stringResource(Res.string.wizard_impact_all_of_collect_one_supporting),
        summary = uiState.global,
        isLoading = uiState.isLoading,
        avatar = {
          Icon(
            Icons.Outlined.Public,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
          )
        },
        onClick = { onFocus(ImpactDashboardFocus.Platform) },
      )
      dashboard.selected.forEach { entry ->
        OverviewCard(
          title = entry.organization.name,
          supporting = organizationLine(entry),
          summary = entry.published.summary,
          isLoading = uiState.isLoading,
          avatar = {
            OrganizationAvatar(
              name = entry.organization.name,
              logoUrl = entry.organization.logoUrl,
              size = 32.dp,
            )
          },
          onClick = { onFocus(ImpactDashboardFocus.OrganizationFocus(entry.organization.id)) },
          onRemove = { onRemove(entry.organization.id) },
        )
      }
      OutlinedCard(
        modifier = Modifier.width(OverviewCardWidth).heightIn(min = 160.dp),
        onClick = onChooseOrganizations,
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(Icons.Outlined.Add, contentDescription = null)
          Text(
            stringResource(Res.string.wizard_impact_follow_more_organizations),
            style = MaterialTheme.typography.titleSmall,
          )
        }
      }
    }
  }
}

private val OverviewMaxWidth = 1040.dp
private val OverviewCardWidth = 320.dp

/** One organization's (or the platform's) headline published numbers, opening its full view. */
@Composable
private fun OverviewCard(
  title: String,
  supporting: String,
  summary: ImpactSummary?,
  isLoading: Boolean,
  avatar: @Composable () -> Unit,
  onClick: () -> Unit,
  onRemove: (() -> Unit)? = null,
) {
  OutlinedCard(
    modifier = Modifier.width(OverviewCardWidth).heightIn(min = 160.dp),
    onClick = onClick,
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        avatar()
        Column(modifier = Modifier.weight(1f)) {
          Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            supporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (onRemove != null) {
          IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
              Icons.Outlined.Close,
              contentDescription = stringResource(Res.string.wizard_impact_stop_following, title),
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
      when {
        summary == null ->
          Text(
            stringResource(
              if (isLoading) Res.string.wizard_impact_adding_up
              else Res.string.wizard_impact_no_numbers_yet
            ),
            style = MaterialTheme.typography.bodyMedium,
          )
        summary.isBelowPublicationThreshold ->
          Text(
            stringResource(Res.string.wizard_impact_not_enough_data_short),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        else ->
          Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            HeadlineNumber(
              impactMeasurementText(ImpactFormat.hectares(summary.features.areaHa)).resolve(),
              stringResource(Res.string.wizard_impact_unique_hectares),
            )
            HeadlineNumber(
              ImpactFormat.count(summary.features.uniqueCount),
              stringResource(Res.string.wizard_impact_map_features_label),
            )
          }
      }
    }
  }
}

@Composable
private fun HeadlineNumber(value: String, label: String) {
  Column {
    Text(value, style = MaterialTheme.typography.headlineSmall)
    Text(
      label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** E.g. `"NGO · Kenya · Member"`. */
@Composable
private fun organizationLine(entry: ImpactDashboardOrganization): String =
  listOfNotNull(
      entry.organization.organizationType?.labelRes?.let { stringResource(it) },
      entry.organization.country?.name,
      when {
        entry.isManager -> stringResource(Res.string.wizard_impact_you_manage)
        entry.isMember -> stringResource(Res.string.wizard_impact_member)
        else -> null
      },
    )
    .joinToString(" · ")
    .ifEmpty { stringResource(Res.string.wizard_impact_organization) }

/** One organization's numbers: the full view for members, the published view for everyone else. */
@Composable
private fun OrganizationDashboardPane(
  entry: ImpactDashboardOrganization,
  uiState: ImpactUiState,
  actions: ImpactActions,
  onOpenOrganization: (String) -> Unit,
  onRemove: () -> Unit,
) {
  val id = entry.organization.id
  val name = entry.organization.name
  val isFullView = entry.isMember && !uiState.dashboard.isPreviewingPublished
  OrganizationImpactPane(
    state = if (isFullView) uiState.organizations[id] else entry.published,
    isLoading = uiState.isLoading,
    onSurveyFilterChange = { actions.setOrganizationSurveyFilter(id, it) },
    onCountryFilterChange = { actions.setOrganizationCountryFilter(id, it) },
    onDownload = {
      if (isFullView) actions.downloadOrganizationSummary(id)
      else actions.downloadPublishedOrganizationSummary(id)
    },
    title = name,
    subtitle =
      if (isFullView) {
        stringResource(Res.string.wizard_impact_organization_subtitle)
      } else {
        stringResource(Res.string.wizard_impact_organization_published_subtitle, name)
      },
    banner = {
      PublishedNotice(
        entry = entry,
        isFullView = isFullView,
        isPreviewing = uiState.dashboard.isPreviewingPublished,
        onPreviewChange = actions::setPreviewingPublished,
      )
    },
    headerActions = {
      if (entry.isMember) {
        TextButton(onClick = { onOpenOrganization(id) }) {
          Text(stringResource(Res.string.wizard_impact_organization_page))
        }
      }
      TextButton(onClick = onRemove) { Text(stringResource(Res.string.wizard_impact_unfollow)) }
    },
  )
}

/**
 * Says what an organization publishes, and to whom. Members can switch between their full numbers
 * and the published view everyone else sees.
 */
@Composable
private fun PublishedNotice(
  entry: ImpactDashboardOrganization,
  isFullView: Boolean,
  isPreviewing: Boolean,
  onPreviewChange: (Boolean) -> Unit,
) {
  val name = entry.organization.name
  NoticeSurface(
    icon = if (isFullView) Icons.Outlined.Groups else Icons.Outlined.Public,
    title =
      when {
        isFullView && entry.isManager ->
          stringResource(Res.string.wizard_impact_notice_full_manager, name)
        isFullView -> stringResource(Res.string.wizard_impact_notice_full_member, name)
        entry.isMember -> stringResource(Res.string.wizard_impact_notice_published_member)
        else -> stringResource(Res.string.wizard_impact_notice_published_by, name)
      },
    body =
      if (entry.isManager) {
        stringResource(Res.string.wizard_impact_notice_body_manager, name, publishedRules())
      } else {
        stringResource(Res.string.wizard_impact_notice_body, publishedRules())
      },
    trailing =
      if (entry.isMember) {
        {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              Icons.Outlined.Visibility,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Text(
              stringResource(Res.string.wizard_impact_show_published),
              style = MaterialTheme.typography.labelLarge,
            )
            Switch(checked = isPreviewing, onCheckedChange = onPreviewChange)
          }
        }
      } else {
        null
      },
  )
}

/** What the platform-wide view includes. */
@Composable
private fun PlatformNotice(isFullView: Boolean) {
  NoticeSurface(
    icon = Icons.Outlined.Public,
    title =
      stringResource(
        if (isFullView) Res.string.wizard_impact_platform_notice_full
        else Res.string.wizard_impact_platform_notice_published
      ),
    body = stringResource(Res.string.wizard_impact_platform_notice_body, publishedRules()),
    trailing = if (isFullView) ({ AdminOnlyBadge() }) else null,
  )
}

/** The overview's explanation of what organizations publish. */
@Composable
private fun PublishingExplainer() {
  NoticeSurface(
    icon = Icons.Outlined.Public,
    title = stringResource(Res.string.wizard_impact_explainer_title),
    body = stringResource(Res.string.wizard_impact_explainer_body, publishedRules()),
  )
}

@Composable
private fun NoticeSurface(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  body: String,
  trailing: (@Composable () -> Unit)? = null,
) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Icon(icon, contentDescription = null)
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium)
      }
      trailing?.invoke()
    }
  }
}

/**
 * Picks the organizations on the user's dashboard from [available] (listed organizations and the
 * user's own), searchable by name, type, or country.
 */
@Composable
private fun ChooseOrganizationsDialog(
  available: List<ImpactDashboardOrganization>,
  selectedIds: List<String>,
  onSave: (List<String>) -> Unit,
  onDismiss: () -> Unit,
) {
  var chosen by remember(selectedIds) { mutableStateOf(selectedIds) }
  var query by remember { mutableStateOf("") }
  val typeLabels = OrganizationType.entries.associateWith { stringResource(it.labelRes) }
  val matches = available.filter { entry -> matchesQuery(entry, query, typeLabels::getValue) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.wizard_impact_choose_organizations)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          stringResource(Res.string.wizard_impact_choose_organizations_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text(stringResource(Res.string.wizard_impact_search_organizations)) },
          leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Column(
          modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          if (matches.isEmpty()) {
            Text(
              stringResource(Res.string.wizard_impact_no_organizations_match),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
          matches.forEach { entry ->
            val isChosen = entry.id in chosen
            val toggle = { chosen = if (isChosen) chosen - entry.id else chosen + entry.id }
            Row(
              modifier =
                Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Checkbox(checked = isChosen, onCheckedChange = { toggle() })
              OrganizationAvatar(
                name = entry.organization.name,
                logoUrl = entry.organization.logoUrl,
                size = 28.dp,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(entry.organization.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                  organizationLine(entry) +
                    if (!entry.organization.isListed) {
                      " · " + stringResource(Res.string.wizard_impact_unlisted)
                    } else {
                      ""
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = { onSave(chosen) }) { Text(stringResource(Res.string.wizard_impact_save)) }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) { Text(stringResource(Res.string.wizard_impact_cancel)) }
    },
  )
}

/**
 * Whether [entry]'s name, type (as shown by [typeLabel]), or country contains [query] (ignoring
 * case).
 */
internal fun matchesQuery(
  entry: ImpactDashboardOrganization,
  query: String,
  typeLabel: (OrganizationType) -> String,
): Boolean {
  val q = query.trim()
  if (q.isEmpty()) return true
  val organization = entry.organization
  return listOfNotNull(
      organization.name,
      organization.organizationType?.let(typeLabel),
      organization.country?.name,
    )
    .any { it.contains(q, ignoreCase = true) }
}
