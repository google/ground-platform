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
package org.openforis.collectone.v2.web.ui.surveys

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
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.ui.common.ClosedSurveyThumbnailScrim
import org.openforis.collectone.v2.app.ui.common.FlatFilterChip
import org.openforis.collectone.v2.app.ui.common.SurveyVisibilityIcon
import org.openforis.collectone.v2.app.ui.common.grayscale
import org.openforis.collectone.v2.app.ui.dashboard.DashboardUiState
import org.openforis.collectone.v2.app.ui.map.SurveyMapThumbnail
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.Card
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_clear_search
import org.openforis.collectone.v2.web.resources.web_create_survey
import org.openforis.collectone.v2.web.resources.web_no_description_yet
import org.openforis.collectone.v2.web.resources.web_no_surveys_match_search
import org.openforis.collectone.v2.web.resources.web_no_surveys_yet
import org.openforis.collectone.v2.web.resources.web_organization_role_member
import org.openforis.collectone.v2.web.resources.web_organization_role_you_manage
import org.openforis.collectone.v2.web.resources.web_outcome_prompt_label
import org.openforis.collectone.v2.web.resources.web_private_submissions
import org.openforis.collectone.v2.web.resources.web_public_data
import org.openforis.collectone.v2.web.resources.web_search_surveys
import org.openforis.collectone.v2.web.resources.web_search_surveys_placeholder
import org.openforis.collectone.v2.web.resources.web_survey_location_count
import org.openforis.collectone.v2.web.ui.common.OrganizationAvatar
import org.openforis.collectone.v2.web.ui.common.WizardOverviewHeroGraphic
import org.openforis.collectone.v2.web.ui.surveyoutcome.SurveyOutcomeDialog

private val SurveyCardWidth = 320.dp
private val SurveyCardHeroHeight = 140.dp
private val PageMaxWidth = 1240.dp

/**
 * Landing page of the Collect One web app: every survey the signed-in user can open, grouped by
 * organization, with search and filter chips (`All`, `My surveys`, one per organization). Selecting
 * a card opens the survey in the dashboard; "Create survey" opens a new survey in the Survey
 * editor.
 *
 * The page shows the survey cards grouped by organization from [uiState], with search and filter
 * chips and the "Create survey" dialog.
 *
 * Closed surveys whose outcome question is due again ([SurveyOutcomePromptUiState.dueSurveyIds])
 * show a "Was this data used?" badge that opens the "What happened with this data?" card.
 *
 * @param header the page header; it receives the click handler of its "Create survey" button.
 */
@Composable
fun WebSurveysPage(
  uiState: DashboardUiState,
  onOpenSurvey: (surveyId: String) -> Unit,
  onCreateSurvey: (CreateSurveyRequest) -> Unit,
  header: @Composable (onCreateSurveyClick: () -> Unit) -> Unit,
  onOpenCreateSurveyWizard: (() -> Unit)? = null,
  outcomePrompt: SurveyOutcomePromptUiState = SurveyOutcomePromptUiState(),
  outcomeActions: SurveyOutcomePromptActions? = null,
  isLoading: Boolean = false,
) {
  var filter by remember { mutableStateOf<SurveyListFilter>(SurveyListFilter.All) }
  var query by remember { mutableStateOf("") }
  var isCreating by remember { mutableStateOf(false) }

  val userEmail = uiState.signedInUserEmail
  val organizations = uiState.organizations
  val chips = SurveyListFilter.chipsFor(organizations, userEmail)
  val visible = WebSurveysList.filter(uiState.surveys, organizations, userEmail, filter, query)
  val sections = WebSurveysList.sections(visible, organizations)
  val openCreateSurvey: () -> Unit = {
    if (onOpenCreateSurveyWizard != null) {
      onOpenCreateSurveyWizard()
    } else {
      isCreating = true
    }
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      header(openCreateSurvey)
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
          modifier =
            Modifier.widthIn(max = PageMaxWidth)
              .align(Alignment.TopCenter)
              .padding(horizontal = 32.dp, vertical = 24.dp)
              .semantics { isTraversalGroup = true },
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
            if (isLoading) {
              Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator()
              }
            } else {
              EmptySurveysMessage(
                hasQuery = query.isNotBlank(),
                onCreateSurvey = openCreateSurvey,
              )
            }
          }

          sections.forEachIndexed { index, section ->
            SurveySection(
              section = section,
              userEmail = userEmail,
              organizations = organizations,
              activeSurveyId = uiState.activeSurveyId,
              onOpenSurvey = onOpenSurvey,
              outcomePromptSurveyIds =
                if (outcomeActions != null) outcomePrompt.dueSurveyIds else emptySet(),
              onOutcomePromptClick = { surveyId -> outcomeActions?.openOutcomeCard(surveyId) },
              traversalIndex = (index + 1).toFloat(),
            )
          }
        }
      }
    }
  }

  if (isCreating) {
    CreateSurveyDialog(
      organizations = uiState.signedInUserOrganizations,
      surveyLibrary = uiState::surveyLibrary,
      onCreate = { request ->
        isCreating = false
        onCreateSurvey(request)
      },
      onDismiss = { isCreating = false },
    )
  }

  val card = outcomePrompt.card
  if (card != null && outcomeActions != null) SurveyOutcomeDialog(card, outcomeActions)
}

@Composable
private fun SurveysToolbar(
  query: String,
  onQueryChange: (String) -> Unit,
  chips: List<SurveyListFilter>,
  selected: SurveyListFilter,
  onSelect: (SurveyListFilter) -> Unit,
) {
  Column(
    modifier =
      Modifier.semantics {
        isTraversalGroup = true
        traversalIndex = 0f
      },
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    val searchSurveysLabel = stringResource(Res.string.web_search_surveys)
    OutlinedTextField(
      value = query,
      onValueChange = onQueryChange,
      placeholder = { Text(stringResource(Res.string.web_search_surveys_placeholder)) },
      leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
      trailingIcon =
        query
          .takeIf { it.isNotEmpty() }
          ?.let {
            {
              IconButton(onClick = { onQueryChange("") }) {
                Icon(
                  Icons.Outlined.Close,
                  contentDescription = stringResource(Res.string.web_clear_search),
                )
              }
            }
          },
      singleLine = true,
      shape = MaterialTheme.shapes.extraLarge,
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
          focusedBorderColor = MaterialTheme.colorScheme.primary,
          unfocusedBorderColor = Color.Transparent,
        ),
      modifier =
        Modifier.widthIn(max = 560.dp).fillMaxWidth().semantics {
          contentDescription = searchSurveysLabel
          traversalIndex = 0f
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      chips.forEachIndexed { index, chip ->
        val chipLabel = chip.label
        FlatFilterChip(
          selected = chip == selected,
          onClick = { onSelect(chip) },
          label = { Text(chipLabel) },
          containerColor = Color.Transparent,
          modifier =
            Modifier.semantics(mergeDescendants = true) {
              role = Role.Button
              contentDescription = chipLabel
              traversalIndex = (index + 1).toFloat()
            },
          leadingIcon =
            (chip as? SurveyListFilter.InOrganization)?.let { org ->
              {
                OrganizationAvatar(
                  name = org.organization.name,
                  logoUrl = org.organization.logoUrl,
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
private fun EmptySurveysMessage(
  hasQuery: Boolean,
  onCreateSurvey: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    WizardOverviewHeroGraphic(modifier = Modifier.widthIn(max = 460.dp))
    Text(
      text =
        stringResource(
          if (hasQuery) Res.string.web_no_surveys_match_search else Res.string.web_no_surveys_yet
        ),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Button(onClick = onCreateSurvey) {
      Icon(
        imageVector = Icons.Outlined.Add,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(Modifier.width(8.dp))
      Text(stringResource(Res.string.web_create_survey))
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SurveySection(
  section: SurveyListSection,
  userEmail: String,
  organizations: List<Organization>,
  activeSurveyId: String,
  onOpenSurvey: (String) -> Unit,
  outcomePromptSurveyIds: Set<String>,
  onOutcomePromptClick: (String) -> Unit,
  traversalIndex: Float = 0f,
) {
  Column(
    modifier =
      Modifier.semantics {
        isTraversalGroup = true
        this.traversalIndex = traversalIndex
      },
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    SectionHeading(section = section, userEmail = userEmail)
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      section.surveys.forEachIndexed { index, survey ->
        WebSurveyCard(
          survey = survey,
          access = WebSurveysList.accessLabel(survey, organizations, userEmail),
          isActive = survey.id == activeSurveyId,
          onClick = { onOpenSurvey(survey.id) },
          modifier = Modifier.fillMaxRowHeight(),
          showOutcomePrompt = survey.id in outcomePromptSurveyIds,
          onOutcomePromptClick = { onOutcomePromptClick(survey.id) },
          traversalIndex = (index + 1).toFloat(),
        )
      }
    }
  }
}

@Composable
private fun SectionHeading(section: SurveyListSection, userEmail: String) {
  val organization = section.organization
  Row(
    modifier =
      Modifier.semantics(mergeDescendants = true) {
        heading()
        traversalIndex = 0f
      },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (organization != null) {
      OrganizationAvatar(name = organization.name, logoUrl = organization.logoUrl, size = 28.dp)
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
          text =
            stringResource(
              if (role == OrganizationRole.MANAGER) Res.string.web_organization_role_you_manage
              else Res.string.web_organization_role_member
            ),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          softWrap = false,
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

/**
 * One survey on the web surveys page (and, later, on an organization's Surveys tab). Closed and
 * archived surveys are labeled; [showOutcomePrompt] adds the "Was this data used?" badge, which
 * runs [onOutcomePromptClick].
 */
@Composable
fun WebSurveyCard(
  survey: SurveyPreviewItem,
  access: SurveyAccessLabel,
  isActive: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  showOutcomePrompt: Boolean = false,
  onOutcomePromptClick: () -> Unit = {},
  outlined: Boolean = false,
  traversalIndex: Float = 0f,
) {
  Card(
    onClick = onClick,
    modifier =
      modifier.width(SurveyCardWidth).semantics(mergeDescendants = true) {
        role = Role.Button
        contentDescription = survey.title
        this.traversalIndex = traversalIndex
      },
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border =
      when {
        isActive -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        outlined -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        else -> null
      },
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      WebSurveyCardHero(
        survey = survey,
        showOutcomePrompt = showOutcomePrompt,
        onOutcomePromptClick = onOutcomePromptClick,
      )
      HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
      WebSurveyCardBody(
        survey = survey,
        access = access,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun WebSurveyCardHero(
  survey: SurveyPreviewItem,
  showOutcomePrompt: Boolean,
  onOutcomePromptClick: () -> Unit,
) {
  Box(modifier = Modifier.fillMaxWidth().height(SurveyCardHeroHeight)) {
    SurveyMapThumbnail(
      thumbnail = survey.thumbnail,
      isDownloaded = false,
      modifier = Modifier.fillMaxSize().grayscale(survey.isClosed),
    )
    if (survey.isClosed) {
      ClosedSurveyThumbnailScrim()
    }
    val hasOverlayBadge = survey.isDraft || survey.isClosed
    if (hasOverlayBadge) {
      Row(
        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (survey.isDraft) {
          TonalBadge(
            text = stringResource(survey.state.labelRes),
            tone = BadgeTone.WARNING,
            outlined = true,
          )
        } else if (survey.isClosed) {
          TonalBadge(
            text = stringResource(survey.state.labelRes),
            tone = BadgeTone.NEUTRAL,
            outlined = true,
          )
        }
      }
    }
    if (showOutcomePrompt) {
      OutcomePromptBadge(
        onClick = onOutcomePromptClick,
        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
      )
    }
  }
}

@Composable
private fun WebSurveyCardBody(
  survey: SurveyPreviewItem,
  access: SurveyAccessLabel,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.padding(14.dp)) {
    Column(modifier = Modifier.fillMaxWidth().grayscale(survey.isClosed)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
      ) {
        Text(
          text = survey.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          lineHeight = 18.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        SurveyVisibilityIcon(
          survey = survey,
          publicDataLabel = stringResource(Res.string.web_public_data),
          privateSubmissionsLabel = stringResource(Res.string.web_private_submissions),
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
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
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = survey.description.ifBlank { stringResource(Res.string.web_no_description_yet) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 16.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(modifier = Modifier.height(12.dp))
    WebSurveyCardFooter(survey = survey, access = access)
  }
}

@Composable
private fun WebSurveyCardFooter(
  survey: SurveyPreviewItem,
  access: SurveyAccessLabel,
) {
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
      modifier = Modifier.weight(1f, fill = false),
      shape = MaterialTheme.shapes.extraSmall,
      color =
        if (isOwner) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
      contentColor = badgeContentColor,
    ) {
      Text(
        text = stringResource(access.label),
        style = MaterialTheme.typography.labelSmall,
        color = badgeContentColor,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      )
    }
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text =
        pluralStringResource(
          Res.plurals.web_survey_location_count,
          survey.entityCount,
          survey.entityCount,
        ),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = FontWeight.Normal,
      maxLines = 1,
      softWrap = false,
    )
  }
}

/** Gentle tonal badge asking whether a closed survey's data was used; opens the outcome card. */
@Composable
private fun OutcomePromptBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val outcomePromptLabel = stringResource(Res.string.web_outcome_prompt_label)
  Surface(
    onClick = onClick,
    modifier =
      modifier.semantics(mergeDescendants = true) {
        role = Role.Button
        contentDescription = outcomePromptLabel
      },
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Outlined.Lightbulb,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = outcomePromptLabel,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
      )
    }
  }
}
