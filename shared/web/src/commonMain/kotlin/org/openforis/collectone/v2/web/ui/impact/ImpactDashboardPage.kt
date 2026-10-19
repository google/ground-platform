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

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.web.domain.model.ImpactFormat
import org.openforis.collectone.v2.web.domain.model.ImpactSummary
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.WebPage
import org.openforis.collectone.v2.web.ui.common.WebAppHeader
import org.openforis.collectone.v2.web.ui.common.WebHeaderButton
import org.openforis.collectone.v2.web.ui.common.WebHeaderContext
import org.openforis.collectone.v2.web.ui.common.WebHeaderSupportingText
import org.openforis.collectone.v2.web.ui.dashboard.ImpactMessageSnackbar
import org.openforis.collectone.v2.web.ui.dashboard.SidePanelSeparator
import org.openforis.collectone.v2.web.ui.dashboard.SidePanelSeparatorWidth
import org.openforis.collectone.v2.web.ui.state.ImpactDashboardFocus
import org.openforis.collectone.v2.web.ui.state.ImpactDashboardOrganization
import org.openforis.collectone.v2.web.ui.state.ImpactUiState
import org.openforis.collectone.v2.web.ui.surveyeditor.NavItem
import org.openforis.collectone.v2.web.ui.surveyeditor.ProfileAvatar
import org.openforis.collectone.v2.web.ui.viewmodel.ImpactActions

/**
 * The Impact page (`#impact`): published impact numbers of the organizations the signed-in user
 * chose to follow, and of all of Collect One.
 *
 * Every organization publishes anonymous totals from its surveys to everyone signed in; its
 * submissions, map features, and answers are never shown. Members of an organization also see its
 * full numbers, and can switch to the published view to check what everyone else sees.
 */
@Composable
internal fun ImpactDashboardPage(state: WebAppState, onSignOut: () -> Unit = { state.signOut() }) {
  val uiState by state.impact.uiState.collectAsState()
  val dashboardState by state.dashboard.uiState.collectAsState()
  Box(modifier = Modifier.fillMaxSize()) {
    ImpactDashboardPage(
      uiState = uiState,
      actions = state.impact,
      sidePanelWidthDp = dashboardState.sidePanelWidthDp,
      onSidePanelWidthChange = state.dashboard::updateSidePanelWidth,
      onFocus = state::focusImpactDashboard,
      onOpenOrganization = state::openOrganization,
      header = { onChooseOrganizations ->
        WebAppHeader(
          state = state,
          onSignOut = onSignOut,
          onLogoClick = { state.selectPage(WebPage.WEB_SURVEYS) },
          navigationIcon = {
            IconButton(onClick = { state.selectPage(WebPage.WEB_SURVEYS) }) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to surveys")
            }
          },
          context = {
            WebHeaderContext(title = "Impact") {
              WebHeaderSupportingText(
                "Published by ${
                  ImpactFormat.count(uiState.dashboard.selected.size.toLong(), "organization")
                } you follow"
              )
            }
          },
          actions = {
            WebHeaderButton(
              text = "Choose organizations",
              icon = Icons.Outlined.Tune,
              onClick = onChooseOrganizations,
              tonal = true,
            )
          },
        )
      },
    )
    ImpactMessageSnackbar(state, modifier = Modifier.align(Alignment.BottomCenter))
  }
}

/**
 * Stateless Impact page: a resizable left panel with the overview, all of Collect One, and the
 * organizations on the user's dashboard, and the selected view in the content pane.
 *
 * @param header the page header; it receives the click handler of its "Choose organizations"
 *   button.
 * @param onFocus shows a view (the stateful page also updates the URL hash).
 * @param onOpenOrganization opens an organization's page (offered to its members).
 */
@Composable
internal fun ImpactDashboardPage(
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
        label = "Overview",
        icon = Icons.Outlined.Dashboard,
        selected = dashboard.focus == ImpactDashboardFocus.Overview,
        onClick = { onFocus(ImpactDashboardFocus.Overview) },
      )
      NavItem(
        label = "All of Collect One",
        icon = Icons.Outlined.Public,
        selected = dashboard.focus == ImpactDashboardFocus.Platform,
        onClick = { onFocus(ImpactDashboardFocus.Platform) },
      )
      Spacer(Modifier.height(12.dp))
      Text(
        "ORGANIZATIONS YOU FOLLOW",
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
          trailing = if (entry.isMember) "Member" else null,
        )
      }
      if (dashboard.selected.isEmpty() && !uiState.isLoading) {
        Text(
          "You don't follow any organizations yet.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
      }
      TextButton(onClick = onChooseOrganizations, modifier = Modifier.padding(start = 4.dp)) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Choose organizations")
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
        "Your impact dashboard",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )
      Text(
        "Published numbers of the organizations you follow. Pick any listed organization to see " +
          "what its data adds up to.",
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
        title = "All of Collect One",
        supporting = "Every organization taking part in platform-wide numbers",
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
            ProfileAvatar(
              nameOrEmail = entry.organization.name,
              photoUrl = entry.organization.logoUrl,
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
          Text("Follow more organizations", style = MaterialTheme.typography.titleSmall)
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
              contentDescription = "Stop following $title",
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
      when {
        summary == null ->
          Text(
            if (isLoading) "Adding up the numbers…" else "No numbers yet.",
            style = MaterialTheme.typography.bodyMedium,
          )
        summary.isBelowPublicationThreshold ->
          Text(
            "Not enough data to publish yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        else ->
          Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            HeadlineNumber(ImpactFormat.hectares(summary.features.areaHa), "Unique hectares")
            HeadlineNumber(ImpactFormat.count(summary.features.uniqueCount), "Map features")
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
private fun organizationLine(entry: ImpactDashboardOrganization): String =
  listOfNotNull(
      entry.organization.organizationType?.label,
      entry.organization.country?.name,
      when {
        entry.isManager -> "You manage"
        entry.isMember -> "Member"
        else -> null
      },
    )
    .joinToString(" · ")
    .ifEmpty { "Organization" }

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
        "What your surveys' data adds up to, and how it was used. Plots mapped in more than one " +
          "survey count once."
      } else {
        "What $name's surveys add up to, as published to everyone on Collect One. Plots mapped " +
          "in more than one survey count once."
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
        TextButton(onClick = { onOpenOrganization(id) }) { Text("Organization page") }
      }
      TextButton(onClick = onRemove) { Text("Unfollow") }
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
        isFullView && entry.isManager -> "You're seeing your full numbers as a Manager of $name"
        isFullView -> "You're seeing $name's full numbers as a member"
        entry.isMember -> "This is what everyone on Collect One sees"
        else -> "Published by $name"
      },
    body =
      if (entry.isManager) {
        "$name publishes anonymous totals from its surveys for anyone signed in to Collect One. " +
          PUBLISHED_RULES
      } else {
        "Every organization on Collect One publishes anonymous totals from its surveys. " +
          PUBLISHED_RULES
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
            Text("Show what's published", style = MaterialTheme.typography.labelLarge)
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
      if (isFullView) "You're seeing every standard field as a Manager of All users"
      else "Published for everyone on Collect One",
    body =
      "Totals across every organization that takes part in platform-wide numbers. " +
        PUBLISHED_RULES,
  )
}

/** The overview's explanation of what organizations publish. */
@Composable
private fun PublishingExplainer() {
  NoticeSurface(
    icon = Icons.Outlined.Public,
    title = "Every organization publishes anonymous totals",
    body =
      "Organizers on Collect One publish what their surveys add up to, like hectares mapped and " +
        "how the data was used, for anyone signed in to see. $PUBLISHED_RULES",
  )
}

/** What's never published, shared by the Impact page's notices and the organization page. */
internal val PUBLISHED_RULES =
  "Submissions, map features, locations, and answers are never published, nor are standard " +
    "fields kept for the organization only or marked sensitive. Organizations with fewer than " +
    "$PUBLICATION_MIN_FEATURES map features show no numbers."

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
  val matches = available.filter { entry -> matchesQuery(entry, query) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Choose organizations") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          "Every organization publishes anonymous totals from its surveys. Pick whose numbers to " +
            "follow on your Impact page.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Search organizations") },
          leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Column(
          modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          if (matches.isEmpty()) {
            Text("No organizations match.", style = MaterialTheme.typography.bodyMedium)
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
              ProfileAvatar(
                nameOrEmail = entry.organization.name,
                photoUrl = entry.organization.logoUrl,
                size = 28.dp,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(entry.organization.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                  organizationLine(entry) +
                    if (!entry.organization.isListed) " · Unlisted, only members see it" else "",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = { Button(onClick = { onSave(chosen) }) { Text("Save") } },
    dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

/** Whether [entry]'s name, type, or country contains [query] (ignoring case). */
internal fun matchesQuery(entry: ImpactDashboardOrganization, query: String): Boolean {
  val q = query.trim()
  if (q.isEmpty()) return true
  val organization = entry.organization
  return listOfNotNull(
      organization.name,
      organization.organizationType?.label,
      organization.country?.name,
    )
    .any { it.contains(q, ignoreCase = true) }
}
