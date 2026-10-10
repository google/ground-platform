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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Countries
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationType

/** Text shown for an optional organization type or country that hasn't been set. */
internal const val NOT_SPECIFIED = "Not specified"

/** Read-only text for an organization's type on the Details tab. */
internal fun organizationTypeText(type: OrganizationType?): String = type?.label ?: NOT_SPECIFIED

/** Read-only text for an organization's country (`Kenya (KE)`) on the Details tab. */
internal fun countryText(countryCode: String?): String =
  Countries.byCode(countryCode)?.label ?: NOT_SPECIFIED

/**
 * M3 exposed dropdown for the optional organization type, with a "Not specified" option first. Used
 * in the Create organization dialog and on the Details tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrganizationTypeDropdown(
  type: OrganizationType?,
  onTypeChange: (OrganizationType?) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier.fillMaxWidth(),
  ) {
    OutlinedTextField(
      value = organizationTypeText(type),
      onValueChange = {},
      readOnly = true,
      singleLine = true,
      label = { Text("Type") },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier =
        Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      (listOf(null) + OrganizationType.entries).forEach { option ->
        DropdownMenuItem(
          text = {
            Text(organizationTypeText(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
          },
          onClick = {
            onTypeChange(option)
            expanded = false
          },
        )
      }
    }
  }
}

/**
 * Searchable M3 exposed dropdown for the optional country: typing filters the ISO 3166-1 list by
 * name or code ([Countries.search]), and choosing a country shows it as plain text such as `Kenya
 * (KE)`. Clearing the field or choosing "Not specified" clears the country; typing without choosing
 * reverts to the current country when the menu closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CountryPicker(
  countryCode: String?,
  onCountryCodeChange: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val selectedLabel = Countries.byCode(countryCode)?.label.orEmpty()
  var query by remember(selectedLabel) { mutableStateOf(selectedLabel) }
  var expanded by remember { mutableStateOf(false) }
  // While the field still shows the current choice, offer the whole list rather than one match.
  val matches =
    remember(query, selectedLabel) {
      if (query == selectedLabel) Countries.all else Countries.search(query)
    }
  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier.fillMaxWidth(),
  ) {
    OutlinedTextField(
      value = query,
      onValueChange = {
        query = it
        expanded = true
        if (it.isBlank()) onCountryCodeChange(null)
      },
      singleLine = true,
      label = { Text("Country") },
      placeholder = { Text("Search by name or code") },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = {
        expanded = false
        query = selectedLabel
      },
    ) {
      DropdownMenuItem(
        text = { Text(NOT_SPECIFIED) },
        onClick = {
          onCountryCodeChange(null)
          query = ""
          expanded = false
        },
      )
      if (matches.isEmpty()) {
        DropdownMenuItem(text = { Text("No matching countries") }, onClick = {}, enabled = false)
      }
      matches.forEach { country ->
        DropdownMenuItem(
          text = { Text(country.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          onClick = {
            onCountryCodeChange(country.code)
            query = country.label
            expanded = false
          },
        )
      }
    }
  }
}
