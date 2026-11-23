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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.LibraryPrograms
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.usecase.ResolvedLibrary
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_back
import org.openforis.collectone.v2.web.resources.web_cancel
import org.openforis.collectone.v2.web.resources.web_create
import org.openforis.collectone.v2.web.resources.web_create_survey
import org.openforis.collectone.v2.web.resources.web_create_survey_in_organization
import org.openforis.collectone.v2.web.resources.web_create_survey_purposes_description
import org.openforis.collectone.v2.web.resources.web_create_survey_purposes_title
import org.openforis.collectone.v2.web.resources.web_next
import org.openforis.collectone.v2.web.resources.web_no_organization_personal
import org.openforis.collectone.v2.web.resources.web_organization
import org.openforis.collectone.v2.web.resources.web_organization_survey_access_hint
import org.openforis.collectone.v2.web.resources.web_personal_survey_access_hint
import org.openforis.collectone.v2.web.resources.web_programs_description
import org.openforis.collectone.v2.web.resources.web_programs_optional
import org.openforis.collectone.v2.web.resources.web_skip
import org.openforis.collectone.v2.web.resources.web_survey_title_label
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.ProgramPicker
import org.openforis.collectone.v2.web.ui.common.PurposePackGrid

/** What the Create survey dialog asks for. */
data class CreateSurveyRequest(
  val title: String,
  val organizationId: String?,
  /** Purpose Packs chosen in "What will this data be used for?" (empty when skipped). */
  val purposeIds: List<String> = emptyList(),
  /** Programs chosen among those the purposes offer. */
  val programIds: List<String> = emptyList(),
)

private enum class CreateSurveyStep {
  DETAILS,
  PURPOSES,
}

/**
 * Creates a survey in two steps (see
 * `docs/technical/model/library/02-templates-and-purpose-packs.md`, "Purpose Packs"): its title and
 * organization, then the skippable **What will this data be used for?** card grid of the Purpose
 * Packs available in that organization (its own first, then global ones it hasn't hidden; global
 * only for a personal survey), with optional program chips. The survey starts with the chosen
 * packs' forms.
 *
 * [organization] fixes the organization (when creating from its page); otherwise the user picks one
 * of [organizations] or a personal survey. [surveyLibrary] returns a new survey's resolved library
 * per organization.
 */
@Composable
fun CreateSurveyDialog(
  organizations: List<Organization>,
  surveyLibrary: (organizationId: String?) -> ResolvedLibrary,
  onCreate: (CreateSurveyRequest) -> Unit,
  onDismiss: () -> Unit,
  organization: Organization? = null,
) {
  var step by remember { mutableStateOf(CreateSurveyStep.DETAILS) }
  var title by remember { mutableStateOf("") }
  var selectedOrganization by remember { mutableStateOf(organization) }
  var purposeIds by remember { mutableStateOf(emptyList<String>()) }
  var programIds by remember { mutableStateOf(emptyList<String>()) }

  val library = surveyLibrary(selectedOrganization?.id)
  val packs = library.pickablePurposePacks
  // Purposes chosen for another organization don't apply once it changes.
  val validPurposeIds = purposeIds.filter { id -> packs.any { it.id == id } }
  val offeredPrograms = LibraryPrograms.offeredFor(packs.filter { it.id in validPurposeIds })
  val validProgramIds = programIds.filter { it in offeredPrograms }

  fun create(withPurposes: Boolean) =
    onCreate(
      CreateSurveyRequest(
        title = title,
        organizationId = selectedOrganization?.id,
        purposeIds = if (withPurposes) validPurposeIds else emptyList(),
        programIds = if (withPurposes) validProgramIds else emptyList(),
      )
    )

  when (step) {
    CreateSurveyStep.DETAILS ->
      AlertDialog(
        onDismissRequest = onDismiss,
        title = {
          Text(
            organization?.let {
              stringResource(Res.string.web_create_survey_in_organization, it.name)
            } ?: stringResource(Res.string.web_create_survey)
          )
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
              value = title,
              onValueChange = { title = it },
              label = { Text(stringResource(Res.string.web_survey_title_label)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            if (organization == null) {
              val options: List<Organization?> = listOf(null) + organizations
              val personalSurveyOption = stringResource(Res.string.web_no_organization_personal)
              DropdownSelector(
                label = stringResource(Res.string.web_organization),
                selectedText = selectedOrganization?.name ?: personalSurveyOption,
                options = options,
                optionText = { it?.name ?: personalSurveyOption },
                onSelect = { selectedOrganization = it },
                modifier = Modifier.fillMaxWidth(),
              )
            }
            Text(
              text =
                if (selectedOrganization == null) {
                  stringResource(Res.string.web_personal_survey_access_hint)
                } else {
                  stringResource(
                    Res.string.web_organization_survey_access_hint,
                    selectedOrganization?.name.orEmpty(),
                  )
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        confirmButton = {
          if (packs.isEmpty()) {
            Button(onClick = { create(withPurposes = false) }, enabled = title.isNotBlank()) {
              Text(stringResource(Res.string.web_create))
            }
          } else {
            Button(
              onClick = { step = CreateSurveyStep.PURPOSES },
              enabled = title.isNotBlank(),
            ) {
              Text(stringResource(Res.string.web_next))
            }
          }
        },
        dismissButton = {
          TextButton(onClick = onDismiss) { Text(stringResource(Res.string.web_cancel)) }
        },
      )
    CreateSurveyStep.PURPOSES ->
      AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 800.dp).padding(24.dp),
        title = { Text(stringResource(Res.string.web_create_survey_purposes_title)) },
        text = {
          Column(
            modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            Text(
              stringResource(Res.string.web_create_survey_purposes_description),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PurposePackGrid(
              packs = packs,
              library = library,
              organizationName = selectedOrganization?.name,
              language = LocalizedText.DEFAULT_LANGUAGE,
              selectedIds = validPurposeIds,
              onToggle = { id ->
                purposeIds = if (id in purposeIds) emptyList() else listOf(id)
              },
            )
            if (offeredPrograms.isNotEmpty()) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  stringResource(Res.string.web_programs_optional),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  stringResource(Res.string.web_programs_description),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ProgramPicker(
                  programIds = offeredPrograms,
                  selectedIds = validProgramIds,
                  onToggle = { id ->
                    programIds = if (id in programIds) programIds - id else programIds + id
                  },
                )
              }
            }
          }
        },
        confirmButton = {
          Button(
            onClick = { create(withPurposes = true) },
            enabled = validPurposeIds.isNotEmpty(),
          ) {
            Text(stringResource(Res.string.web_create_survey))
          }
        },
        dismissButton = {
          TextButton(onClick = { step = CreateSurveyStep.DETAILS }) {
            Text(stringResource(Res.string.web_back))
          }
          TextButton(onClick = { create(withPurposes = false) }) {
            Text(stringResource(Res.string.web_skip))
          }
        },
      )
  }
}
