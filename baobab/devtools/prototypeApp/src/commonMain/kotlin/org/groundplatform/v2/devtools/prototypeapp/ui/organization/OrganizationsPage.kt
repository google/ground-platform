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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.PrototypeWorkbenchPage
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationRelation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.relationTo
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OrganizationUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.surveyeditor.ProfileAvatar
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.OrganizationActions
import org.groundplatform.v2.devtools.prototypeapp.ui.workbench.WebAppHeader
import org.groundplatform.v2.devtools.prototypeapp.ui.workbench.WebHeaderButton
import org.groundplatform.v2.devtools.prototypeapp.ui.workbench.WebHeaderContext
import org.groundplatform.v2.devtools.prototypeapp.ui.workbench.WebHeaderSupportingText
import org.groundplatform.v2.devtools.prototypeapp.ui.workbench.WebMobilePrototypeButton

internal val OrganizationCardWidth = 320.dp
internal val OrganizationPageMaxWidth = 1240.dp

/**
 * The organizations the signed-in user belongs to (Managers first) and the listed organizations
 * they can ask to join, with search and a "Create organization" dialog. Selecting a card opens the
 * [OrganizationPage].
 */
@Composable
internal fun OrganizationsPage(
  state: PrototypeAppState,
  onSignOut: () -> Unit = { state.signOut() },
) {
  val uiState by state.organization.uiState.collectAsState()
  OrganizationsPage(
    uiState = uiState,
    actions = state.organization,
    header = { onCreateOrganizationClick ->
      WebAppHeader(
        state = state,
        onSignOut = onSignOut,
        navigationIcon = {
          IconButton(onClick = { state.selectWorkbenchPage(PrototypeWorkbenchPage.WEB_SURVEYS) }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to surveys")
          }
        },
        context = {
          WebHeaderContext(title = "Organizations") {
            WebHeaderSupportingText(
              "${uiState.signedInUserOrganizations.size} yours · ${uiState.organizations.size} total"
            )
          }
        },
        actions = {
          WebMobilePrototypeButton(state)
          WebHeaderButton(
            text = "Create organization",
            icon = Icons.Outlined.Add,
            onClick = onCreateOrganizationClick,
            tonal = true,
          )
        },
      )
    },
  )
}

/**
 * Stateless organizations directory: the signed-in user's organizations and the listed ones they
 * can ask to join from [uiState], with search, the notice banner, and the "Create organization"
 * dialog.
 *
 * @param header the page header; it receives the click handler of its "Create organization" button.
 */
@Composable
internal fun OrganizationsPage(
  uiState: OrganizationUiState,
  actions: OrganizationActions,
  header: @Composable (onCreateOrganizationClick: () -> Unit) -> Unit,
) {
  var query by remember { mutableStateOf("") }
  var isCreating by remember { mutableStateOf(false) }
  val mine = uiState.searchMyOrganizations(query)
  val others = uiState.searchDiscoverableOrganizations(query)

  Column(modifier = Modifier.fillMaxSize()) {
    header { isCreating = true }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
      Column(
        modifier =
          Modifier.widthIn(max = OrganizationPageMaxWidth)
            .align(Alignment.TopCenter)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        uiState.notice?.let { notice ->
          OrganizationNotice(notice, onDismiss = actions::dismissNotice)
        }
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Search organizations") },
          leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
          trailingIcon =
            query
              .takeIf { it.isNotEmpty() }
              ?.let {
                {
                  IconButton(onClick = { query = "" }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                  }
                }
              },
          singleLine = true,
          modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        )

        OrganizationSection(
          title = "Your organizations",
          emptyText =
            if (query.isBlank()) {
              "You don't belong to any organization yet. Create one, or ask to join one below."
            } else {
              "None of your organizations match your search."
            },
          organizations = mine,
          uiState = uiState,
          actions = actions,
        )
        OrganizationSection(
          title = "Other organizations",
          emptyText =
            if (query.isBlank()) "No other listed organizations."
            else "No other organizations match your search.",
          organizations = others,
          uiState = uiState,
          actions = actions,
        )
      }
    }
  }

  if (isCreating) {
    CreateOrganizationDialog(
      onCreate = { name, description, isListed ->
        isCreating = false
        actions.createOrganization(name, description, isListed)
      },
      onDismiss = { isCreating = false },
    )
  }
}

/** Dismissible notice banner used by both organization pages. */
@Composable
internal fun OrganizationNotice(text: String, onDismiss: () -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.widthIn(max = 760.dp),
  ) {
    Row(
      modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
      Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
      )
      IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Dismiss") }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrganizationSection(
  title: String,
  emptyText: String,
  organizations: List<Organization>,
  uiState: OrganizationUiState,
  actions: OrganizationActions,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(
        "${organizations.size}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (organizations.isEmpty()) {
      Text(
        emptyText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      organizations.forEach { organization ->
        OrganizationCard(
          organization = organization,
          relation = uiState.relationTo(organization),
          surveyCount = uiState.surveyCountInOrganization(organization.id),
          onOpen = { actions.openOrganization(organization.id) },
          onRequestToJoin = { actions.requestToJoin(organization.id) },
        )
      }
    }
  }
}

@Composable
private fun OrganizationCard(
  organization: Organization,
  relation: OrganizationRelation,
  surveyCount: Int,
  onOpen: () -> Unit,
  onRequestToJoin: () -> Unit,
) {
  val isMember = relation == OrganizationRelation.MANAGER || relation == OrganizationRelation.MEMBER
  OutlinedCard(
    onClick = onOpen,
    modifier = Modifier.width(OrganizationCardWidth),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        ProfileAvatar(
          nameOrEmail = organization.name,
          photoUrl = organization.logoUrl,
          size = 48.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            organization.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            organization.description.ifBlank { "No description yet." },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val summaryText =
          if (organization.isSynthetic) {
            val count = organization.imagerySources.size
            "All users · $count imagery ${if (count == 1) "source" else "sources"}"
          } else {
            "${organization.activeMembers.size} members · $surveyCount surveys" +
              (if (organization.imagerySources.isNotEmpty())
                " · ${organization.imagerySources.size} imagery"
              else "") +
              (if (
                relation == OrganizationRelation.MANAGER &&
                  organization.pendingRequests.isNotEmpty()
              )
                " · ${organization.pendingRequests.size} requests"
              else "")
          }
        Text(
          summaryText,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
          organization.isSynthetic -> SyntheticOrgBadge()
          isMember -> RelationBadge(relation)
          relation == OrganizationRelation.REQUESTED || relation == OrganizationRelation.INVITED ->
            RelationBadge(relation)
          else ->
            OutlinedButton(onClick = onRequestToJoin, modifier = Modifier.height(32.dp)) {
              Text("Request to join", style = MaterialTheme.typography.labelMedium)
            }
        }
      }
    }
  }
}

@Composable
private fun SyntheticOrgBadge() {
  Surface(
    shape = MaterialTheme.shapes.extraSmall,
    color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
  ) {
    Text(
      "Synthetic",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onTertiaryContainer,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}

@Composable
internal fun RelationBadge(relation: OrganizationRelation) {
  val isManager = relation == OrganizationRelation.MANAGER
  val contentColor =
    if (isManager) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
  Surface(
    shape = MaterialTheme.shapes.extraSmall,
    color =
      if (isManager) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = contentColor,
  ) {
    Text(
      relation.label,
      style = MaterialTheme.typography.labelSmall,
      color = contentColor,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}

/** Name, description, and directory listing for a new organization. */
@Composable
private fun CreateOrganizationDialog(
  onCreate: (name: String, description: String, isListed: Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var isListed by remember { mutableStateOf(true) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create organization") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Organization name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Description") },
          minLines = 2,
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
        Text(
          "You'll be its first Manager. Managers can edit the organization, manage members, and " +
            "manage all of its surveys.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Button(onClick = { onCreate(name, description, isListed) }, enabled = name.isNotBlank()) {
        Text("Create")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
