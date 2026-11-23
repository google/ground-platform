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
package org.openforis.collectone.v2.web.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Carpenter
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.LibraryPrograms
import org.openforis.collectone.v2.app.domain.model.PurposePack
import org.openforis.collectone.v2.app.domain.usecase.ResolvedLibrary
import org.openforis.collectone.v2.app.ui.common.FlatFilterChip
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.RadioButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_purpose_custom
import org.openforis.collectone.v2.web.resources.wizard_purpose_error
import org.openforis.collectone.v2.web.resources.wizard_purpose_forms
import org.openforis.collectone.v2.web.resources.wizard_purpose_loading
import org.openforis.collectone.v2.web.resources.wizard_purpose_other
import org.openforis.collectone.v2.web.resources.wizard_purpose_other_description
import org.openforis.collectone.v2.web.resources.wizard_purpose_other_label
import org.openforis.collectone.v2.web.resources.wizard_purpose_other_placeholder
import org.openforis.collectone.v2.web.resources.wizard_purpose_other_supporting
import org.openforis.collectone.v2.web.resources.wizard_purpose_programs_supporting
import org.openforis.collectone.v2.web.resources.wizard_purpose_programs_title
import org.openforis.collectone.v2.web.resources.wizard_purpose_selected
import org.openforis.collectone.v2.web.resources.wizard_purpose_source_organization
import org.openforis.collectone.v2.web.resources.wizard_purpose_standard_fields
import org.openforis.collectone.v2.web.resources.wizard_starter_template_programs
import org.openforis.collectone.v2.web.resources.wizard_validation_other_purpose_required
import org.openforis.collectone.v2.web.resources.wizard_validation_purpose_required

/** Prominent high-contrast alert banner for required field validation errors. */
@Composable
fun ValidationErrorBanner(
  message: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        Icons.Outlined.ErrorOutline,
        contentDescription = stringResource(Res.string.wizard_purpose_error),
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onErrorContainer,
      )
    }
  }
}

/** Icon of a Purpose Pack's Material icon name (`PurposePack.icon`), or a flag. */
fun purposePackIcon(name: String): ImageVector =
  when (name) {
    "verified" -> Icons.Outlined.Verified
    "forest" -> Icons.Outlined.Forest
    "person_add" -> Icons.Outlined.PersonAdd
    "fact_check" -> Icons.Outlined.FactCheck
    "layers" -> Icons.Outlined.Layers
    "public" -> Icons.Outlined.Public
    "shield" -> Icons.Outlined.Shield
    "groups" -> Icons.Outlined.Groups
    "carpenter" -> Icons.Outlined.Carpenter
    "satellite_alt" -> Icons.Outlined.SatelliteAlt
    else -> Icons.Outlined.Flag
  }

/**
 * Multi-select card grid of [packs] for "What will this data be used for?": each card shows the
 * pack's icon, title, description, source (organization name or "Global"), and what it adds, plus
 * an "Other" card for custom survey goals.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PurposePackGrid(
  packs: List<PurposePack>,
  library: ResolvedLibrary,
  organizationName: String?,
  language: String,
  selectedIds: List<String>,
  onToggle: (packId: String) -> Unit,
  modifier: Modifier = Modifier,
  otherPurposeText: String = "",
  onOtherPurposeTextChange: ((String) -> Unit)? = null,
  isError: Boolean = false,
  onOtherInputPositioned: ((Int) -> Unit)? = null,
  showOtherCard: Boolean = true,
  showSourceBadge: Boolean = true,
  showValidationBanner: Boolean = true,
  /** Shows a spinner instead of the cards while the purpose library is still loading. */
  isLoading: Boolean = false,
  enabled: Boolean = true,
) {
  if (isLoading) {
    Row(
      modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Spinner only; the label stays available to screen readers.
      val loadingLabel = stringResource(Res.string.wizard_purpose_loading)
      CircularProgressIndicator(
        modifier = Modifier.size(24.dp).semantics { contentDescription = loadingLabel },
        strokeWidth = 3.dp,
      )
    }
    return
  }
  val otherSelected = SurveyWizardRules.OTHER_PURPOSE_ID in selectedIds
  val otherBlankError = enabled && isError && otherSelected && otherPurposeText.isBlank()
  val otherFocusRequester = remember { FocusRequester() }

  LaunchedEffect(enabled, otherSelected, otherBlankError) {
    if (enabled && otherSelected && (otherPurposeText.isBlank() || otherBlankError)) {
      runCatching { otherFocusRequester.requestFocus() }
    }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (enabled && showValidationBanner && isError && selectedIds.isEmpty()) {
      ValidationErrorBanner(message = stringResource(Res.string.wizard_validation_purpose_required))
    }

    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      packs.forEach { pack ->
        PurposePackCard(
          pack = pack,
          library = library,
          sourceLabel =
            if (showSourceBadge && library.isOrganizationEntry(pack.id)) {
              organizationName ?: stringResource(Res.string.wizard_purpose_source_organization)
            } else {
              null
            },
          language = language,
          selected = pack.id in selectedIds,
          enabled = enabled,
          onClick = { onToggle(pack.id) },
        )
      }
      if (showOtherCard) {
        OtherPurposeCard(
          selected = otherSelected,
          enabled = enabled,
          onClick = { onToggle(SurveyWizardRules.OTHER_PURPOSE_ID) },
        )
      }
    }

    if (showOtherCard && otherSelected && onOtherPurposeTextChange != null) {
      Column(
        modifier =
          Modifier.fillMaxWidth().onGloballyPositioned { coords ->
            onOtherInputPositioned?.invoke(coords.positionInParent().y.toInt())
          },
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (otherBlankError) {
          ValidationErrorBanner(
            message = stringResource(Res.string.wizard_validation_other_purpose_required)
          )
        }
        OutlinedTextField(
          value = otherPurposeText,
          onValueChange = onOtherPurposeTextChange,
          enabled = enabled,
          label = { Text(stringResource(Res.string.wizard_purpose_other_label)) },
          placeholder = { Text(stringResource(Res.string.wizard_purpose_other_placeholder)) },
          singleLine = true,
          isError = otherBlankError,
          supportingText = {
            Text(stringResource(Res.string.wizard_purpose_other_supporting))
          },
          modifier = Modifier.fillMaxWidth().focusRequester(otherFocusRequester),
        )
      }
    }
  }
}

@Composable
private fun OtherPurposeCard(
  selected: Boolean,
  enabled: Boolean = true,
  role: Role = Role.Checkbox,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  OutlinedCard(
    onClick = onClick,
    enabled = enabled,
    modifier =
      Modifier.width(232.dp).height(188.dp).semantics {
        this.role = role
        this.selected = selected
      },
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = if (selected) colors.secondaryContainer else colors.surface,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
        disabledContainerColor =
          if (selected) colors.secondaryContainer.copy(alpha = 0.6f)
          else colors.surface.copy(alpha = 0.6f),
        disabledContentColor =
          if (selected) colors.onSecondaryContainer.copy(alpha = 0.7f)
          else colors.onSurface.copy(alpha = 0.6f),
      ),
    border =
      BorderStroke(
        if (selected) 2.dp else 1.dp,
        if (selected) colors.secondary else colors.outlineVariant,
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Outlined.EditNote,
          contentDescription = null,
          tint = if (selected) colors.onSecondaryContainer else colors.primary,
          modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
          Icon(
            Icons.Filled.CheckCircle,
            contentDescription = stringResource(Res.string.wizard_purpose_selected),
            tint = colors.secondary,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      Text(
        stringResource(Res.string.wizard_purpose_other),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        stringResource(Res.string.wizard_purpose_other_description),
        style = MaterialTheme.typography.bodySmall,
        color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.weight(1f))
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TonalBadge(
          text = stringResource(Res.string.wizard_purpose_custom),
          tone = BadgeTone.NEUTRAL,
        )
      }
    }
  }
}

@Composable
private fun PurposePackCard(
  pack: PurposePack,
  library: ResolvedLibrary,
  /** Organization name of an organization pack, or `null` for a global pack. */
  sourceLabel: String?,
  language: String,
  selected: Boolean,
  enabled: Boolean = true,
  role: Role = Role.Checkbox,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val templates = library.templatesForPurposes(listOf(pack.id))
  val linked = templates.sumOf { it.linkedQuestionCount }
  val summary =
    listOfNotNull(
        pluralStringResource(Res.plurals.wizard_purpose_forms, templates.size, templates.size)
          .takeIf { templates.isNotEmpty() },
        pluralStringResource(Res.plurals.wizard_purpose_standard_fields, linked, linked).takeIf {
          linked > 0
        },
      )
      .joinToString(" · ")
  OutlinedCard(
    onClick = onClick,
    enabled = enabled,
    modifier =
      Modifier.width(232.dp).height(188.dp).semantics {
        this.role = role
        this.selected = selected
      },
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = if (selected) colors.secondaryContainer else colors.surface,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
        disabledContainerColor =
          if (selected) colors.secondaryContainer.copy(alpha = 0.6f)
          else colors.surface.copy(alpha = 0.6f),
        disabledContentColor =
          if (selected) colors.onSecondaryContainer.copy(alpha = 0.7f)
          else colors.onSurface.copy(alpha = 0.6f),
      ),
    border =
      BorderStroke(
        if (selected) 2.dp else 1.dp,
        if (selected) colors.secondary else colors.outlineVariant,
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          purposePackIcon(pack.icon),
          contentDescription = null,
          tint = if (selected) colors.onSecondaryContainer else colors.primary,
          modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
          Icon(
            Icons.Filled.CheckCircle,
            contentDescription = stringResource(Res.string.wizard_purpose_selected),
            tint = colors.secondary,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      Text(
        pack.title.get(language),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        pack.description.get(language),
        style = MaterialTheme.typography.bodySmall,
        color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.weight(1f))
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (sourceLabel != null) {
          TonalBadge(
            text = sourceLabel,
            tone = BadgeTone.TERTIARY,
          )
        }
        if (summary.isNotEmpty()) {
          Text(
            summary,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/**
 * Program chips ([LibraryPrograms]) offered by the selected purpose: tap to select or clear; hover
 * (or long-press) a chip for a description of the program. Shared by the survey creation wizard,
 * the Create survey dialog, and Survey details.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProgramPicker(
  programIds: List<String>,
  selectedIds: List<String>,
  onToggle: (programId: String) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  if (programIds.isEmpty()) return
  FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    programIds.forEach { id ->
      val selected = id in selectedIds
      val description = LibraryPrograms.description(id)
      val chip: @Composable () -> Unit = {
        FlatFilterChip(
          selected = selected,
          onClick = { onToggle(id) },
          enabled = enabled,
          label = { Text(LibraryPrograms.label(id)) },
          leadingIcon =
            if (selected) {
              {
                Icon(
                  Icons.Outlined.Check,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              }
            } else {
              null
            },
        )
      }
      if (description.isEmpty()) {
        chip()
      } else {
        TooltipBox(
          positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
          tooltip = {
            PlainTooltip(modifier = Modifier.widthIn(max = 320.dp)) { Text(description) }
          },
          state = rememberTooltipState(),
        ) {
          chip()
        }
      }
    }
  }
}

/**
 * "Reporting programs & frameworks" section: a title, a note that programs are for tracking only,
 * and the [ProgramPicker] chips. Shared by the survey creation wizard and Survey details.
 */
@Composable
fun ProgramPickerSection(
  programIds: List<String>,
  selectedIds: List<String>,
  onToggle: (programId: String) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  if (programIds.isEmpty()) return
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      stringResource(Res.string.wizard_purpose_programs_title),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      stringResource(Res.string.wizard_purpose_programs_supporting),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ProgramPicker(
      programIds = programIds,
      selectedIds = selectedIds,
      onToggle = onToggle,
      enabled = enabled,
    )
  }
}

/**
 * Single-choice cards of a purpose's starter templates ([PurposePack.effectiveStarterTemplates]):
 * each shows the template's title, description, and the programs it preselects. [library] supplies
 * the title and description of a pack's default template from its first form template.
 */
@Composable
fun StarterTemplatePicker(
  pack: PurposePack,
  library: ResolvedLibrary,
  language: String,
  selectedId: String,
  onSelect: (starterTemplateId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    pack.effectiveStarterTemplates.forEach { starter ->
      val selected = starter.id == pack.starterTemplate(selectedId).id
      val firstForm = starter.formTemplateIds.firstNotNullOfOrNull(library::formTemplate)
      val isDefault = starter.id == PurposePack.DEFAULT_STARTER_TEMPLATE_ID
      val title =
        (if (isDefault) firstForm?.title?.get(language) else null)?.ifBlank { null }
          ?: starter.title.get(language)
      val description =
        (if (isDefault) firstForm?.description?.get(language) else null)?.ifBlank { null }
          ?: starter.description.get(language)
      OutlinedCard(
        onClick = { onSelect(starter.id) },
        modifier =
          Modifier.fillMaxWidth().semantics {
            role = Role.RadioButton
            this.selected = selected
          },
        colors =
          CardDefaults.outlinedCardColors(
            containerColor = if (selected) colors.secondaryContainer else colors.surface,
            contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
          ),
        border =
          BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) colors.secondary else colors.outlineVariant,
          ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
          Column(
            modifier = Modifier.weight(1f).padding(vertical = 6.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            if (description.isNotBlank()) {
              Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
              )
            }
            if (starter.programIds.isNotEmpty()) {
              Text(
                stringResource(
                  Res.string.wizard_starter_template_programs,
                  starter.programIds.joinToString { LibraryPrograms.label(it) },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}
