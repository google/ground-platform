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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionActions
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionUiState
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.ModalBottomSheetOverlay
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.FloatingActionButton
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_available_forms_close
import org.openforis.collectone.v2.mobile.resources.mobile_available_forms_subtitle
import org.openforis.collectone.v2.mobile.resources.mobile_available_forms_title
import org.openforis.collectone.v2.mobile.resources.mobile_collect_data
import org.openforis.collectone.v2.mobile.resources.mobile_form_question_count

/**
 * Floating Action Button (`FloatingActionButton`) on the Main Survey screen that opens the
 * [AvailableFormsModalSheet] list of available forms
 * ([DataCollectionActions.openAvailableFormsSheet]) to start data collection without requiring a
 * geospatial entity to be pre-selected from the map. [isDarkTheme] picks the FAB's colors.
 */
@Composable
internal fun DataCollectionFormsFab(
  actions: DataCollectionActions,
  isDarkTheme: Boolean,
  modifier: Modifier = Modifier,
) {
  val formsBg =
    if (isDarkTheme) {
      MaterialTheme.colorScheme.primary
    } else {
      MaterialTheme.colorScheme.primaryContainer
    }
  val formsContent =
    if (isDarkTheme) {
      MaterialTheme.colorScheme.onPrimary
    } else {
      MaterialTheme.colorScheme.onPrimaryContainer
    }
  FloatingActionButton(
    onClick = { actions.openAvailableFormsSheet() },
    modifier = modifier,
    containerColor = formsBg,
    contentColor = formsContent,
  ) {
    Icon(
      imageVector = Icons.Outlined.Add,
      contentDescription = stringResource(Res.string.mobile_collect_data),
    )
  }
}

/**
 * Modal bottom sheet opened by the bottom-centered [DataCollectionFormsFab] listing all available
 * forms in the active survey ([DataCollectionUiState.mobileForms]). Selecting a form launches data
 * collection via [DataCollectionActions.launchFormFromFab], which presents the Map or List entity
 * selector at the step in the data collection process where an `entityref` is required.
 */
@Composable
internal fun AvailableFormsModalSheet(
  uiState: DataCollectionUiState,
  actions: DataCollectionActions,
) {
  ModalBottomSheetOverlay(onDismissRequest = { actions.closeAvailableFormsSheet() }) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(Res.string.mobile_available_forms_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = stringResource(Res.string.mobile_available_forms_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = { actions.closeAvailableFormsSheet() }) {
          Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(Res.string.mobile_available_forms_close),
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      uiState.mobileForms.forEach { form ->
        val eligibleCount = uiState.eligibleEntitiesForForm(form).size
        val canLaunch = !form.requiresEntity || eligibleCount > 0

        OutlinedCard(
          onClick = { if (canLaunch) actions.launchFormFromFab(form.id) },
          enabled = canLaunch,
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          colors =
            CardDefaults.outlinedCardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = form.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
              )
              Spacer(modifier = Modifier.width(8.dp))
              TonalBadge(
                text =
                  pluralStringResource(
                    Res.plurals.mobile_form_question_count,
                    form.questionCount,
                    form.questionCount,
                  ),
                tone = BadgeTone.PRIMARY,
              )
            }

            Text(
              text = form.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
              onClick = { actions.launchFormFromFab(form.id) },
              enabled = canLaunch,
              modifier = Modifier.align(Alignment.End),
            ) {
              Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = form.ctaLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
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
}
