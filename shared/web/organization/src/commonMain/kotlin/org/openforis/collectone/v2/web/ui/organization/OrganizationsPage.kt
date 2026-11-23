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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.Card
import org.openforis.collectone.v2.core.forms.ui.controls.Checkbox
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.domain.model.OrganizationRelation
import org.openforis.collectone.v2.web.domain.model.relationTo
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_badge_synthetic
import org.openforis.collectone.v2.web.resources.org_cancel
import org.openforis.collectone.v2.web.resources.org_card_all_users_summary
import org.openforis.collectone.v2.web.resources.org_clear_search
import org.openforis.collectone.v2.web.resources.org_count_imagery
import org.openforis.collectone.v2.web.resources.org_count_members
import org.openforis.collectone.v2.web.resources.org_count_requests
import org.openforis.collectone.v2.web.resources.org_count_surveys
import org.openforis.collectone.v2.web.resources.org_create
import org.openforis.collectone.v2.web.resources.org_create_listed_help
import org.openforis.collectone.v2.web.resources.org_create_manager_help
import org.openforis.collectone.v2.web.resources.org_create_organization
import org.openforis.collectone.v2.web.resources.org_create_publish_help
import org.openforis.collectone.v2.web.resources.org_dismiss
import org.openforis.collectone.v2.web.resources.org_field_description
import org.openforis.collectone.v2.web.resources.org_field_name
import org.openforis.collectone.v2.web.resources.org_list_in_directory
import org.openforis.collectone.v2.web.resources.org_no_description
import org.openforis.collectone.v2.web.resources.org_request_to_join
import org.openforis.collectone.v2.web.resources.org_search_placeholder
import org.openforis.collectone.v2.web.resources.org_section_others
import org.openforis.collectone.v2.web.resources.org_section_others_empty
import org.openforis.collectone.v2.web.resources.org_section_others_no_matches
import org.openforis.collectone.v2.web.resources.org_section_yours
import org.openforis.collectone.v2.web.resources.org_section_yours_empty
import org.openforis.collectone.v2.web.resources.org_section_yours_no_matches
import org.openforis.collectone.v2.web.resources.org_unlisted
import org.openforis.collectone.v2.web.ui.common.OrganizationAvatar
import org.openforis.collectone.v2.web.ui.text.OrganizationMessage
import org.openforis.collectone.v2.web.ui.text.labelRes

internal val OrganizationCardWidth = 320.dp
internal val OrganizationPageMaxWidth = 1240.dp

/**
 * The organizations the signed-in user belongs to (Managers first) and the listed organizations
 * they can ask to join, with search and a "Create organization" dialog. Selecting a card opens the
 * [OrganizationPage].
 *
 * The directory shows the signed-in user's organizations and the listed ones they can ask to join
 * from [uiState], with search, the notice banner, and the "Create organization" dialog.
 *
 * @param header the page header; it receives the click handler of its "Create organization" button.
 */
@Composable
fun OrganizationsPage(
  uiState: OrganizationUiState,
  actions: OrganizationActions,
  header: @Composable (onCreateOrganizationClick: () -> Unit) -> Unit,
  isLoading: Boolean = false,
) {
  var query by remember { mutableStateOf("") }
  var isCreating by remember { mutableStateOf(false) }
  val mine = uiState.searchMyOrganizations(query)
  val others = uiState.searchDiscoverableOrganizations(query)

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
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
            placeholder = { Text(stringResource(Res.string.org_search_placeholder)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon =
              query
                .takeIf { it.isNotEmpty() }
                ?.let {
                  {
                    IconButton(onClick = { query = "" }) {
                      Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(Res.string.org_clear_search),
                      )
                    }
                  }
                },
            singleLine = true,
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
              ),
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
          )

          if (isLoading && uiState.organizations.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          } else {
            OrganizationSection(
              title = stringResource(Res.string.org_section_yours),
              emptyText =
                if (query.isBlank()) {
                  stringResource(Res.string.org_section_yours_empty)
                } else {
                  stringResource(Res.string.org_section_yours_no_matches)
                },
              organizations = mine,
              uiState = uiState,
              actions = actions,
            )
            OrganizationSection(
              title = stringResource(Res.string.org_section_others),
              emptyText =
                if (query.isBlank()) stringResource(Res.string.org_section_others_empty)
                else stringResource(Res.string.org_section_others_no_matches),
              organizations = others,
              uiState = uiState,
              actions = actions,
            )
          }
        }
      }
    }
  }

  if (isCreating) {
    CreateOrganizationDialog(
      onCreate = { name, description, isListed, type, countryCode ->
        isCreating = false
        actions.createOrganization(name, description, isListed, type, countryCode)
      },
      onDismiss = { isCreating = false },
      isSaving = uiState.isSaving,
    )
  }
}

/** Dismissible notice banner used by both organization pages. */
@Composable
internal fun OrganizationNotice(message: OrganizationMessage, onDismiss: () -> Unit) {
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
        message.asString(),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
      )
      IconButton(onClick = onDismiss) {
        Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.org_dismiss))
      }
    }
  }
}

/** Resolves this message's string resource and arguments for display. */
@Composable
internal fun OrganizationMessage.asString(): String =
  when (this) {
    is OrganizationMessage.Resource -> stringResource(resource, *args.toTypedArray())
    is OrganizationMessage.Text -> text
  }

/**
 * The summary of an organization's counts, such as `3 members · 2 surveys · 1 imagery`: its
 * members, its surveys, and, when given, its imagery sources, pending requests, and unlisted
 * status.
 */
@Composable
internal fun organizationCountsText(
  memberCount: Int,
  surveyCount: Int,
  imageryCount: Int? = null,
  requestCount: Int? = null,
  isUnlisted: Boolean = false,
): String =
  listOfNotNull(
      pluralStringResource(Res.plurals.org_count_members, memberCount, memberCount),
      pluralStringResource(Res.plurals.org_count_surveys, surveyCount, surveyCount),
      imageryCount?.let { stringResource(Res.string.org_count_imagery, it) },
      requestCount?.let { pluralStringResource(Res.plurals.org_count_requests, it, it) },
      if (isUnlisted) stringResource(Res.string.org_unlisted) else null,
    )
    .joinToString(" · ")

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
  Card(
    onClick = onOpen,
    modifier = Modifier.width(OrganizationCardWidth),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        OrganizationAvatar(
          name = organization.name,
          logoUrl = organization.logoUrl,
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
            organization.description.ifBlank { stringResource(Res.string.org_no_description) },
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
            pluralStringResource(Res.plurals.org_card_all_users_summary, count, count)
          } else {
            organizationCountsText(
              memberCount = organization.activeMembers.size,
              surveyCount = surveyCount,
              imageryCount = organization.imagerySources.size.takeIf { it > 0 },
              requestCount =
                organization.pendingRequests.size.takeIf {
                  relation == OrganizationRelation.MANAGER && it > 0
                },
            )
          }
        Text(
          summaryText,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
        )
        when {
          organization.isSynthetic -> SyntheticOrgBadge()
          isMember -> RelationBadge(relation)
          relation == OrganizationRelation.REQUESTED || relation == OrganizationRelation.INVITED ->
            RelationBadge(relation)
          else ->
            OutlinedButton(onClick = onRequestToJoin, modifier = Modifier.height(32.dp)) {
              Text(
                stringResource(Res.string.org_request_to_join),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
              )
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
      stringResource(Res.string.org_badge_synthetic),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onTertiaryContainer,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      softWrap = false,
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
      relation.labelRes?.let { stringResource(it) }.orEmpty(),
      style = MaterialTheme.typography.labelSmall,
      color = contentColor,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      softWrap = false,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}

/** Name, description, optional type and country, and directory listing for a new organization. */
@Composable
private fun CreateOrganizationDialog(
  onCreate:
    (
      name: String,
      description: String,
      isListed: Boolean,
      type: OrganizationType?,
      countryCode: String?,
    ) -> Unit,
  onDismiss: () -> Unit,
  isSaving: Boolean = false,
) {
  var name by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var type by remember { mutableStateOf<OrganizationType?>(null) }
  var countryCode by remember { mutableStateOf<String?>(null) }
  var isListed by remember { mutableStateOf(true) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.org_create_organization)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(Res.string.org_field_name)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text(stringResource(Res.string.org_field_description)) },
          minLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
        OrganizationTypeDropdown(type = type, onTypeChange = { type = it })
        CountryPicker(countryCode = countryCode, onCountryCodeChange = { countryCode = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = isListed, onCheckedChange = { isListed = it })
          Spacer(Modifier.width(4.dp))
          Column {
            Text(
              stringResource(Res.string.org_list_in_directory),
              style = MaterialTheme.typography.bodyMedium,
            )
            Text(
              stringResource(Res.string.org_create_listed_help),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Text(
          stringResource(Res.string.org_create_manager_help),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          stringResource(Res.string.org_create_publish_help),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onCreate(name, description, isListed, type, countryCode) },
        enabled = name.isNotBlank() && !isSaving,
      ) {
        if (isSaving) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
          Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(Res.string.org_create))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.org_cancel)) }
    },
  )
}
