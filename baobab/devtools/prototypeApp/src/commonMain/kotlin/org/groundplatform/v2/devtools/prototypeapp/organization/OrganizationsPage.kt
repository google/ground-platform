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
import org.groundplatform.v2.devtools.prototypeapp.WebAppHeader
import org.groundplatform.v2.devtools.prototypeapp.WebHeaderButton
import org.groundplatform.v2.devtools.prototypeapp.WebHeaderContext
import org.groundplatform.v2.devtools.prototypeapp.WebHeaderSupportingText
import org.groundplatform.v2.devtools.prototypeapp.WebMobilePrototypeButton
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.ProfileAvatar

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
  var query by remember { mutableStateOf("") }
  var isCreating by remember { mutableStateOf(false) }
  val email = state.signedInUserEmail
  val mine = OrganizationPages.search(OrganizationPages.mine(state.organizations, email), query)
  val others =
    OrganizationPages.search(OrganizationPages.discoverable(state.organizations, email), query)

  Column(modifier = Modifier.fillMaxSize()) {
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
            "${state.signedInUserOrganizations.size} yours · ${state.organizations.size} total"
          )
        }
      },
      actions = {
        WebMobilePrototypeButton(state)
        WebHeaderButton(
          text = "Create organization",
          icon = Icons.Outlined.Add,
          onClick = { isCreating = true },
          tonal = true,
        )
      },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
      Column(
        modifier =
          Modifier.widthIn(max = OrganizationPageMaxWidth)
            .align(Alignment.TopCenter)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        state.organizationNotice?.let { notice ->
          OrganizationNotice(notice, onDismiss = state::dismissOrganizationNotice)
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
          state = state,
        )
        OrganizationSection(
          title = "Other organizations",
          emptyText =
            if (query.isBlank()) "No other listed organizations."
            else "No other organizations match your search.",
          organizations = others,
          state = state,
        )
      }
    }
  }

  if (isCreating) {
    CreateOrganizationDialog(
      onCreate = { name, description, isListed ->
        isCreating = false
        state.createOrganization(name, description, isListed)
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
  state: PrototypeAppState,
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
          relation = OrganizationPages.relationOf(organization, state.signedInUserEmail),
          surveyCount = state.surveysInOrganization(organization.id).size,
          onOpen = { state.openOrganization(organization.id) },
          onRequestToJoin = { state.requestToJoinOrganization(organization.id) },
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
        Text(
          "${organization.activeMembers.size} members · $surveyCount surveys" +
            (if (
              relation == OrganizationRelation.MANAGER && organization.pendingRequests.isNotEmpty()
            )
              " · ${organization.pendingRequests.size} requests"
            else ""),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
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
internal fun RelationBadge(relation: OrganizationRelation) {
  Surface(
    shape = MaterialTheme.shapes.extraSmall,
    color =
      if (relation == OrganizationRelation.MANAGER) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Text(
      relation.label,
      style = MaterialTheme.typography.labelSmall,
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
