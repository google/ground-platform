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
package org.groundplatform.v2.devtools.prototypeapp

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.formeditor.DropdownSelector
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.ProfileAvatar

private val SurveyCardWidth = 320.dp
private val PageMaxWidth = 1240.dp

/**
 * Landing page of the Ground 2.0 web app: every survey the signed-in user can open, grouped by
 * organization, with search and filter chips (`All`, `My surveys`, one per organization). Selecting
 * a card opens the survey in the dashboard; "Create survey" opens a new survey in the Survey
 * editor.
 */
@Composable
internal fun WebSurveysPage(
  state: PrototypeAppState,
  onOpenSurvey: (surveyId: String) -> Unit,
  onCreateSurvey: (title: String, organizationId: String?) -> Unit,
  onSignOut: () -> Unit = { state.signOut() },
) {
  var filter by remember { mutableStateOf<SurveyListFilter>(SurveyListFilter.All) }
  var query by remember { mutableStateOf("") }
  var isCreating by remember { mutableStateOf(false) }

  val userEmail = state.signedInUserEmail
  val organizations = state.organizations
  val chips = SurveyListFilter.chipsFor(organizations, userEmail)
  val visible = WebSurveysList.filter(state.surveys, organizations, userEmail, filter, query)
  val sections = WebSurveysList.sections(visible, organizations)

  Column(modifier = Modifier.fillMaxSize()) {
    WebAppHeader(
      state = state,
      onSignOut = onSignOut,
      context = {
        WebHeaderContext(title = "Surveys") {
          WebHeaderSupportingText(
            "${state.surveys.size} surveys · ${state.signedInUserOrganizations.size} organizations"
          )
        }
      },
      actions = {
        WebMobilePrototypeButton(state)
        WebHeaderButton(
          text = "Create survey",
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
          Modifier.widthIn(max = PageMaxWidth)
            .align(Alignment.TopCenter)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        SurveysToolbar(
          query = query,
          onQueryChange = { query = it },
          chips = chips,
          selected = filter,
          onSelect = { filter = it },
        )

        if (sections.isEmpty()) {
          EmptySurveysMessage(hasQuery = query.isNotBlank())
        }

        sections.forEach { section ->
          SurveySection(
            section = section,
            userEmail = userEmail,
            organizations = organizations,
            activeSurveyId = state.activeSurveyId,
            onOpenSurvey = onOpenSurvey,
          )
        }
      }
    }
  }

  if (isCreating) {
    CreateSurveyDialog(
      organizations = state.signedInUserOrganizations,
      onCreate = { title, organizationId ->
        isCreating = false
        onCreateSurvey(title, organizationId)
      },
      onDismiss = { isCreating = false },
    )
  }
}

@Composable
private fun SurveysToolbar(
  query: String,
  onQueryChange: (String) -> Unit,
  chips: List<SurveyListFilter>,
  selected: SurveyListFilter,
  onSelect: (SurveyListFilter) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    OutlinedTextField(
      value = query,
      onValueChange = onQueryChange,
      placeholder = { Text("Search surveys by name, location, or organization") },
      leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
      trailingIcon =
        query
          .takeIf { it.isNotEmpty() }
          ?.let {
            {
              IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
              }
            }
          },
      singleLine = true,
      modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      chips.forEach { chip ->
        GroundFilterChip(
          selected = chip == selected,
          onClick = { onSelect(chip) },
          label = { Text(chip.label) },
          leadingIcon =
            (chip as? SurveyListFilter.InOrganization)?.let { org ->
              {
                ProfileAvatar(
                  nameOrEmail = org.organization.name,
                  photoUrl = org.organization.logoUrl,
                  size = 18.dp,
                )
              }
            },
        )
      }
    }
  }
}

@Composable
private fun EmptySurveysMessage(hasQuery: Boolean) {
  Text(
    text =
      if (hasQuery) "No surveys match your search."
      else "No surveys yet. Create one to get started.",
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SurveySection(
  section: SurveyListSection,
  userEmail: String,
  organizations: List<Organization>,
  activeSurveyId: String,
  onOpenSurvey: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    SectionHeading(section = section, userEmail = userEmail)
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      section.surveys.forEach { survey ->
        WebSurveyCard(
          survey = survey,
          access = WebSurveysList.accessLabel(survey, organizations, userEmail),
          isActive = survey.id == activeSurveyId,
          onClick = { onOpenSurvey(survey.id) },
        )
      }
    }
  }
}

@Composable
private fun SectionHeading(section: SurveyListSection, userEmail: String) {
  val organization = section.organization
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (organization != null) {
      ProfileAvatar(nameOrEmail = organization.name, photoUrl = organization.logoUrl, size = 28.dp)
    } else {
      Icon(
        imageVector = Icons.Outlined.Groups,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(28.dp),
      )
    }
    Text(
      text = section.title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    organization?.roleOf(userEmail)?.let { role ->
      Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      ) {
        Text(
          text = if (role == OrganizationRole.MANAGER) "You manage" else "Member",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
      }
    }
    Text(
      text = "${section.surveys.size}",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** One survey on the web surveys page (and, later, on an organization's Surveys tab). */
@Composable
internal fun WebSurveyCard(
  survey: SurveyPreviewItem,
  access: SurveyAccessLabel,
  isActive: Boolean,
  onClick: () -> Unit,
) {
  OutlinedCard(
    onClick = onClick,
    modifier = Modifier.width(SurveyCardWidth),
    shape = MaterialTheme.shapes.large,
    colors =
      CardDefaults.outlinedCardColors(
        containerColor =
          if (isActive) MaterialTheme.colorScheme.surfaceContainerLow
          else MaterialTheme.colorScheme.surface
      ),
    border =
      BorderStroke(
        width = if (isActive) 1.5.dp else 1.dp,
        color =
          if (isActive) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outlineVariant,
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        SurveyMapThumbnail(theme = survey.thumbnailTheme, isDownloaded = false)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = survey.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.LocationOn,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = survey.location,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Text(
            text = survey.description.ifBlank { "No description yet." },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val isOwner = access == SurveyAccessLabel.OWNER
        val badgeContentColor =
          if (isOwner) MaterialTheme.colorScheme.onPrimaryContainer
          else MaterialTheme.colorScheme.onSurfaceVariant
        Surface(
          shape = MaterialTheme.shapes.extraSmall,
          color =
            if (isOwner) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = badgeContentColor,
        ) {
          Text(
            text = access.label,
            style = MaterialTheme.typography.labelSmall,
            color = badgeContentColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          )
        }
        Text(
          text = if (isActive) "Open now" else "${survey.entityCount} locations",
          style = MaterialTheme.typography.labelSmall,
          color =
            if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
        )
      }
    }
  }
}

/** Title and optional organization for a new survey. */
@Composable
private fun CreateSurveyDialog(
  organizations: List<Organization>,
  onCreate: (title: String, organizationId: String?) -> Unit,
  onDismiss: () -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var organization by remember { mutableStateOf<Organization?>(null) }
  val options: List<Organization?> = listOf(null) + organizations
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create survey") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Survey title") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        DropdownSelector(
          label = "Organization",
          selectedText = organization?.name ?: "None (personal survey)",
          options = options,
          optionText = { it?.name ?: "None (personal survey)" },
          onSelect = { organization = it },
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          text =
            if (organization == null) {
              "Only people you share it with can open a personal survey."
            } else {
              "You stay the owner. Managers of ${organization?.name} can also edit this survey, " +
                "manage sharing, and export data."
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(0.dp))
      }
    },
    confirmButton = {
      Button(onClick = { onCreate(title, organization?.id) }, enabled = title.isNotBlank()) {
        Text("Create")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
