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
package org.groundplatform.v2.devtools.prototypeapp.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryPrograms
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolvedLibrary
import org.groundplatform.v2.devtools.prototypeapp.ui.common.ProgramChips
import org.groundplatform.v2.devtools.prototypeapp.ui.common.PurposePackGrid
import org.groundplatform.v2.devtools.prototypeapp.ui.formeditor.DropdownSelector

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
internal fun CreateSurveyDialog(
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
        title = { Text(organization?.let { "Create survey in ${it.name}" } ?: "Create survey") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
              value = title,
              onValueChange = { title = it },
              label = { Text("Survey title") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            if (organization == null) {
              val options: List<Organization?> = listOf(null) + organizations
              DropdownSelector(
                label = "Organization",
                selectedText = selectedOrganization?.name ?: "None (personal survey)",
                options = options,
                optionText = { it?.name ?: "None (personal survey)" },
                onSelect = { selectedOrganization = it },
                modifier = Modifier.fillMaxWidth(),
              )
            }
            Text(
              text =
                if (selectedOrganization == null) {
                  "Only people you share it with can open a personal survey."
                } else {
                  "You stay the owner. Managers of ${selectedOrganization?.name} can also edit " +
                    "this survey, manage sharing, and export data."
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        confirmButton = {
          if (packs.isEmpty()) {
            Button(onClick = { create(withPurposes = false) }, enabled = title.isNotBlank()) {
              Text("Create")
            }
          } else {
            Button(
              onClick = { step = CreateSurveyStep.PURPOSES },
              enabled = title.isNotBlank(),
            ) {
              Text("Next")
            }
          }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
      )
    CreateSurveyStep.PURPOSES ->
      AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 800.dp).padding(24.dp),
        title = { Text("What will this data be used for?") },
        text = {
          Column(
            modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            Text(
              "Choose any that apply. We'll add their forms, with standard fields, so your data " +
                "can be compared and reported. You can change this later in Survey details.",
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
                purposeIds = if (id in purposeIds) purposeIds - id else purposeIds + id
              },
            )
            if (offeredPrograms.isNotEmpty()) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  "Programs (optional)",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  "Programs you report to with this survey.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ProgramChips(
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
            Text("Create survey")
          }
        },
        dismissButton = {
          TextButton(onClick = { step = CreateSurveyStep.DETAILS }) { Text("Back") }
          TextButton(onClick = { create(withPurposes = false) }) { Text("Skip") }
        },
      )
  }
}
