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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.EarthEnginePresets
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.ui.common.displayLabel
import org.openforis.collectone.v2.core.forms.ui.appMonospaceFontFamily
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_cancel
import org.openforis.collectone.v2.web.resources.org_edit_item
import org.openforis.collectone.v2.web.resources.org_imagery_add
import org.openforis.collectone.v2.web.resources.org_imagery_add_body
import org.openforis.collectone.v2.web.resources.org_imagery_configured_title
import org.openforis.collectone.v2.web.resources.org_imagery_ee_description
import org.openforis.collectone.v2.web.resources.org_imagery_ee_preset
import org.openforis.collectone.v2.web.resources.org_imagery_ee_preset_placeholder
import org.openforis.collectone.v2.web.resources.org_imagery_empty_manager
import org.openforis.collectone.v2.web.resources.org_imagery_empty_member
import org.openforis.collectone.v2.web.resources.org_imagery_not_permitted
import org.openforis.collectone.v2.web.resources.org_imagery_offline_allowed
import org.openforis.collectone.v2.web.resources.org_imagery_offline_disabled
import org.openforis.collectone.v2.web.resources.org_imagery_offline_help_add
import org.openforis.collectone.v2.web.resources.org_imagery_offline_help_edit
import org.openforis.collectone.v2.web.resources.org_imagery_offline_title
import org.openforis.collectone.v2.web.resources.org_imagery_permitted
import org.openforis.collectone.v2.web.resources.org_imagery_source_name
import org.openforis.collectone.v2.web.resources.org_imagery_source_name_placeholder
import org.openforis.collectone.v2.web.resources.org_imagery_source_type
import org.openforis.collectone.v2.web.resources.org_imagery_url
import org.openforis.collectone.v2.web.resources.org_imagery_url_help_add
import org.openforis.collectone.v2.web.resources.org_imagery_url_help_edit
import org.openforis.collectone.v2.web.resources.org_remove_imagery_source
import org.openforis.collectone.v2.web.resources.org_save
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.text.OrganizationMessage

// ---------------------------------------------------------------------------------------------
// Imagery sources
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ImagerySourcesPane(
  actions: OrganizationActions,
  organization: Organization,
  isManager: Boolean,
) {
  var editingSourceId by remember(organization.id) { mutableStateOf<String?>(null) }

  Column(
    modifier = Modifier.widthIn(max = OrganizationPaneMaxWidth),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    DetailsCard(stringResource(Res.string.org_imagery_configured_title)) {
      if (organization.imagerySources.isEmpty()) {
        Text(
          if (isManager) {
            stringResource(Res.string.org_imagery_empty_manager)
          } else {
            stringResource(Res.string.org_imagery_empty_member, organization.name)
          },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          organization.imagerySources.forEach { source ->
            key(source.id) {
              ImagerySourceRow(
                actions = actions,
                organization = organization,
                source = source,
                isManager = isManager,
                isEditing = editingSourceId == source.id,
                onStartEdit = { editingSourceId = source.id },
                onDoneEdit = { editingSourceId = null },
              )
            }
          }
        }
      }
    }

    if (isManager) {
      AddImagerySourceCard(actions = actions, organization = organization)
    }
  }
}

@Composable
private fun ImagerySourceRow(
  actions: OrganizationActions,
  organization: Organization,
  source: ImagerySource,
  isManager: Boolean,
  isEditing: Boolean,
  onStartEdit: () -> Unit,
  onDoneEdit: () -> Unit,
) {
  var editName by remember(source.id, source.name) { mutableStateOf(source.name) }
  var editUrl by remember(source.id, source.urlTemplate) { mutableStateOf(source.urlTemplate) }
  var editOffline by
    remember(source.id, source.allowOfflineDownload) { mutableStateOf(source.allowOfflineDownload) }
  var editError by remember(source.id) { mutableStateOf<OrganizationMessage?>(null) }

  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (isEditing && isManager) {
        OutlinedTextField(
          value = editName,
          onValueChange = {
            editName = it
            editError = null
          },
          label = { Text(stringResource(Res.string.org_imagery_source_name)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (source.type != ImagerySourceType.EARTH_ENGINE) {
          OutlinedTextField(
            value = editUrl,
            onValueChange = {
              editUrl = it
              editError = null
            },
            label = { Text(stringResource(Res.string.org_imagery_url)) },
            placeholder = { Text("https://tile.opentopomap.org/{z}/{x}/{y}.png") },
            singleLine = true,
            isError = editError != null,
            supportingText =
              editError?.let { { Text(it.asString()) } }
                ?: {
                  Text(stringResource(Res.string.org_imagery_url_help_edit))
                },
            textStyle =
              MaterialTheme.typography.bodyMedium.copy(fontFamily = appMonospaceFontFamily()),
            modifier = Modifier.fillMaxWidth(),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              stringResource(Res.string.org_imagery_offline_title),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
            )
            Text(
              stringResource(Res.string.org_imagery_offline_help_edit),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(checked = editOffline, onCheckedChange = { editOffline = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = {
              val err =
                actions.updateImagerySource(
                  organizationId = organization.id,
                  sourceId = source.id,
                  name = editName,
                  urlTemplate = editUrl,
                  allowOfflineDownload = editOffline,
                )
              if (err == null) {
                onDoneEdit()
              } else {
                editError = err
              }
            }
          ) {
            Text(stringResource(Res.string.org_save))
          }
          TextButton(
            onClick = {
              editName = source.name
              editUrl = source.urlTemplate
              editOffline = source.allowOfflineDownload
              editError = null
              onDoneEdit()
            }
          ) {
            Text(stringResource(Res.string.org_cancel))
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                source.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
              Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              ) {
                Text(
                  source.type.displayLabel(),
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
            Text(
              if (source.type == ImagerySourceType.EARTH_ENGINE) {
                stringResource(Res.string.org_imagery_ee_description)
              } else {
                source.urlTemplate
              },
              style =
                MaterialTheme.typography.bodySmall.copy(fontFamily = appMonospaceFontFamily()),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (isManager) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = onStartEdit) {
                Icon(
                  Icons.Outlined.Edit,
                  contentDescription = stringResource(Res.string.org_edit_item, source.name),
                )
              }
              IconButton(onClick = { actions.removeImagerySource(organization.id, source.id) }) {
                Icon(
                  Icons.Outlined.Close,
                  contentDescription =
                    stringResource(Res.string.org_remove_imagery_source, source.name),
                )
              }
            }
          }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              stringResource(Res.string.org_imagery_offline_title),
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Medium,
            )
            Text(
              if (source.allowOfflineDownload) {
                stringResource(Res.string.org_imagery_offline_allowed)
              } else {
                stringResource(Res.string.org_imagery_offline_disabled)
              },
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (isManager) {
            Switch(
              checked = source.allowOfflineDownload,
              onCheckedChange = { allowed ->
                actions.setImagerySourceOfflineAllowed(
                  organizationId = organization.id,
                  sourceId = source.id,
                  allowOfflineDownload = allowed,
                )
              },
            )
          } else {
            Text(
              if (source.allowOfflineDownload) stringResource(Res.string.org_imagery_permitted)
              else stringResource(Res.string.org_imagery_not_permitted),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AddImagerySourceCard(actions: OrganizationActions, organization: Organization) {
  val availableTypes =
    if (organization.isSynthetic) ImagerySourceType.entries else listOf(ImagerySourceType.XYZ_TILES)
  val typeLabels = ImagerySourceType.entries.associateWith { it.displayLabel() }

  var name by remember(organization.id) { mutableStateOf("") }
  var urlTemplate by remember(organization.id) { mutableStateOf("") }
  var sourceType by remember(organization.id) { mutableStateOf(ImagerySourceType.XYZ_TILES) }
  var selectedPreset by remember(organization.id) { mutableStateOf<ImagerySource?>(null) }
  var allowOfflineDownload by remember(organization.id) { mutableStateOf(true) }
  var error by remember(organization.id) { mutableStateOf<OrganizationMessage?>(null) }

  DetailsCard(stringResource(Res.string.org_imagery_add)) {
    Text(
      stringResource(Res.string.org_imagery_add_body),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(modifier = Modifier.widthIn(max = 280.dp)) {
      DropdownSelector(
        label = stringResource(Res.string.org_imagery_source_type),
        selectedText = typeLabels.getValue(sourceType),
        options = availableTypes,
        optionText = { typeLabels.getValue(it) },
        onSelect = { sourceType = it },
      )
    }
    if (sourceType == ImagerySourceType.EARTH_ENGINE) {
      Box(modifier = Modifier.widthIn(max = 360.dp)) {
        DropdownSelector(
          label = stringResource(Res.string.org_imagery_ee_preset),
          selectedText =
            selectedPreset?.name ?: stringResource(Res.string.org_imagery_ee_preset_placeholder),
          options = EarthEnginePresets.presets,
          optionText = { it.name },
          onSelect = {
            selectedPreset = it
            name = it.name
            error = null
          },
        )
      }
    }
    OutlinedTextField(
      value = name,
      onValueChange = {
        name = it
        error = null
      },
      label = { Text(stringResource(Res.string.org_imagery_source_name)) },
      placeholder = { Text(stringResource(Res.string.org_imagery_source_name_placeholder)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    if (sourceType != ImagerySourceType.EARTH_ENGINE) {
      OutlinedTextField(
        value = urlTemplate,
        onValueChange = {
          urlTemplate = it
          error = null
        },
        label = { Text(stringResource(Res.string.org_imagery_url)) },
        placeholder = { Text("https://tile.opentopomap.org/{z}/{x}/{y}.png") },
        singleLine = true,
        isError = error != null,
        supportingText =
          error?.let { { Text(it.asString()) } }
            ?: {
              Text(stringResource(Res.string.org_imagery_url_help_add))
            },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = appMonospaceFontFamily()),
        modifier = Modifier.fillMaxWidth(),
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          stringResource(Res.string.org_imagery_offline_title),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
        )
        Text(
          stringResource(Res.string.org_imagery_offline_help_add),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = allowOfflineDownload, onCheckedChange = { allowOfflineDownload = it })
    }
    Button(
      onClick = {
        val validationError =
          actions.addImagerySource(
            organizationId = organization.id,
            name = name,
            urlTemplate = if (sourceType == ImagerySourceType.EARTH_ENGINE) "" else urlTemplate,
            type = sourceType,
            allowOfflineDownload = allowOfflineDownload,
            expression =
              if (sourceType == ImagerySourceType.EARTH_ENGINE) {
                selectedPreset?.expression ?: EarthEnginePresets.presets.first().expression
              } else {
                null
              },
          )
        if (validationError == null) {
          name = ""
          urlTemplate = ""
          selectedPreset = null
          allowOfflineDownload = true
          error = null
        } else {
          error = validationError
        }
      },
      enabled =
        name.isNotBlank() &&
          (sourceType == ImagerySourceType.EARTH_ENGINE || urlTemplate.isNotBlank()),
    ) {
      Icon(Icons.Outlined.Add, contentDescription = null)
      Spacer(Modifier.width(6.dp))
      Text(stringResource(Res.string.org_imagery_add))
    }
  }
}
