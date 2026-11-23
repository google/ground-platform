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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.Countries
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.ui.common.LocalAppLocale
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_country_no_matches
import org.openforis.collectone.v2.web.resources.org_country_search_placeholder
import org.openforis.collectone.v2.web.resources.org_field_country
import org.openforis.collectone.v2.web.resources.org_field_type
import org.openforis.collectone.v2.web.resources.org_not_specified
import org.openforis.collectone.v2.web.ui.common.DropdownFieldClickTarget
import org.openforis.collectone.v2.web.ui.text.countriesInDisplayOrder
import org.openforis.collectone.v2.web.ui.text.labelText
import org.openforis.collectone.v2.web.ui.text.searchCountries

/** Read-only text for an organization's type on the Details tab. */
@Composable
internal fun organizationTypeText(type: OrganizationType?): String =
  type?.labelRes?.let { stringResource(it) } ?: stringResource(Res.string.org_not_specified)

/** Read-only text for an organization's country (`Kenya (KE)`) on the Details tab. */
@Composable
internal fun countryText(countryCode: String?): String =
  Countries.byCode(countryCode)?.labelText() ?: stringResource(Res.string.org_not_specified)

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
    Box {
      OutlinedTextField(
        value = organizationTypeText(type),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(stringResource(Res.string.org_field_type)) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        modifier =
          Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
      )
      // Opens the menu on the first click, even if the mouse moves slightly during it.
      DropdownFieldClickTarget(expanded = expanded, onExpandedChange = { expanded = it })
    }
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
 * Searchable M3 exposed dropdown for the optional country, listed by name in the current language
 * ([countriesInDisplayOrder]): typing filters the ISO 3166-1 list by English or localized name, or
 * code ([searchCountries]), and choosing a country shows it as plain text such as `Kenya (KE)`.
 * Clearing the field or choosing "Not specified" clears the country; typing without choosing
 * reverts to the current country when the menu closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CountryPicker(
  countryCode: String?,
  onCountryCodeChange: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val selectedLabel = Countries.byCode(countryCode)?.labelText().orEmpty()
  var query by remember(selectedLabel) { mutableStateOf(selectedLabel) }
  var expanded by remember { mutableStateOf(false) }
  val languageTag = LocalAppLocale.current
  // While the field still shows the current choice, offer the whole list rather than one match.
  val matches =
    remember(query, selectedLabel, languageTag) {
      if (query == selectedLabel) countriesInDisplayOrder(languageTag)
      else searchCountries(query, languageTag)
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
      label = { Text(stringResource(Res.string.org_field_country)) },
      placeholder = { Text(stringResource(Res.string.org_country_search_placeholder)) },
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
        text = { Text(stringResource(Res.string.org_not_specified)) },
        onClick = {
          onCountryCodeChange(null)
          query = ""
          expanded = false
        },
      )
      if (matches.isEmpty()) {
        DropdownMenuItem(
          text = { Text(stringResource(Res.string.org_country_no_matches)) },
          onClick = {},
          enabled = false,
        )
      }
      matches.forEach { country ->
        val label = country.labelText()
        DropdownMenuItem(
          text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          onClick = {
            onCountryCodeChange(country.code)
            query = label
            expanded = false
          },
        )
      }
    }
  }
}
