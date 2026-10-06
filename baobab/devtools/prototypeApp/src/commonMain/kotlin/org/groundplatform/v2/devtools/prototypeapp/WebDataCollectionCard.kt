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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.ui.CompactFormRunner
import org.groundplatform.v2.core.forms.ui.CompactGeometryInput
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.core.forms.ui.LocalMediaCaptureHandler

/**
 * Web dashboard's data collection panel: the floating right-hand card that hosts the active form (
 * [PrototypeAppState.activeFormWizardController]) in the compact stacked layout
 * ([CompactFormRunner]) instead of mobile's one-question-per-screen runner.
 *
 * Web adaptation of the mobile entry points:
 * - Forms opened from the **Collect data** toolbar menu ([WebCollectDataMenuButton], the
 *   counterpart of mobile's bottom FAB) with no target feature first show a picker
 *   ([WebEntityRefPicker]): clicking a matching feature on the map, in the left-hand list, or in
 *   the picker's list fills the form's `entityref` and reveals the questions. That step is then
 *   left out of the stack.
 * - Forms opened from a map feature's card (**Collect data** buttons) go straight to the questions.
 * - Geometry questions (`geopoint`, `geotrace`, `geoshape`) are drawn on the main map through
 *   [PrototypeAppState.webMapDrawing] instead of captured from a device GPS: **Draw on map** routes
 *   map clicks to the question until the point is placed or the collector clicks **Done**.
 *
 * Submitting runs the same [PrototypeAppState.completeActiveFormSubmission] as mobile, which adds
 * the submission to the feature's history and closes the panel.
 */
@Composable
internal fun WebDataCollectionCard(state: PrototypeAppState, modifier: Modifier = Modifier) {
  val controller = state.activeFormWizardController ?: return
  val form = state.activeDataCollectionForm
  val entity = state.activeDataCollectionEntity
  val needsEntity = form != null && form.requiresEntity && entity == null
  val title =
    controller.formState.formDef.title.takeIf { it.isNotBlank() } ?: form?.title ?: "Collect data"

  // Picking a map feature anywhere in the dashboard while the form waits for its target answers the
  // `entityref` step, the way the mobile Map or List selector does. The feature that happened to be
  // selected when the form opened doesn't count: the collector has to pick one deliberately.
  val selected = state.selectedEntity
  val selectionWhenOpened = remember(controller) { state.selectedEntityId }
  LaunchedEffect(selected?.id, needsEntity) {
    val target = selected ?: return@LaunchedEffect
    if (
      needsEntity &&
        target.id != selectionWhenOpened &&
        form != null &&
        target.datasetId == form.targetDatasetId &&
        state.isFormButtonEnabled(target, form)
    ) {
      state.selectEntityRefForActiveForm(target.id)
    }
  }

  WebFormPanelChrome(
    title = title,
    subtitle =
      when {
        entity != null -> "${entity.label} • ${entity.datasetName}"
        form != null && !form.requiresEntity -> "New record • ${state.userGpsCoordinatesLabel}"
        form != null -> "Select ${form.targetSingularTypeLabel.lowercase()} to begin"
        else -> null
      },
    onClose = { state.closeActiveFormRunner() },
    modifier = modifier,
  ) {
    Column(
      modifier =
        Modifier.weight(1f, fill = false)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(16.dp)
    ) {
      if (needsEntity && form != null) {
        WebEntityRefPicker(state = state, form = form)
      } else {
        val mediaCaptureHandler = remember { PrototypeMediaCaptureHandler() }
        CompositionLocalProvider(LocalMediaCaptureHandler provides mediaCaptureHandler) {
          CompactFormRunner(
            controller = controller,
            onCancel = { state.closeActiveFormRunner() },
            onSubmitted = { result: FinalizationResult.Success ->
              state.completeActiveFormSubmission(result.recordInstance, result.entityStates)
            },
            // The target feature is picked on the map or in the lists, not in a card.
            questionFilter = { step -> !isWizardStepEntityRef(step) },
            // No field GPS in a browser: every geometry question is drawn on the main map.
            geometryInput = CompactGeometryInput.MapDrawing(host = state.webMapDrawing),
          )
        }
      }
    }
  }
}

/**
 * Web counterpart of the mobile `entityref` Map or List selector: an instruction to click a
 * matching feature on the map or in the left-hand list, plus a searchable list of candidates.
 */
@Composable
private fun WebEntityRefPicker(state: PrototypeAppState, form: FormPreviewItem) {
  val candidates = state.filteredEntityRefCandidates
  val total = state.allDatasetEntitiesForForm(form).size
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.TouchApp,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text =
            "Click a ${form.targetSingularTypeLabel.lowercase()} on the map or in the list to start this form, or pick one below.",
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
    OutlinedTextField(
      value = state.entityRefSearchQuery,
      onValueChange = { state.updateEntityRefSearchQuery(it) },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      leadingIcon = {
        Icon(
          imageVector = Icons.Outlined.Search,
          contentDescription = "Search map features",
          modifier = Modifier.size(18.dp),
        )
      },
      trailingIcon = {
        if (state.entityRefSearchQuery.isNotEmpty()) {
          IconButton(onClick = { state.clearEntityRefSearchQuery() }) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = "Clear search",
              modifier = Modifier.size(16.dp),
            )
          }
        }
      },
      placeholder = {
        Text(
          text = "Filter ${form.targetDatasetName.lowercase()} ($total)",
          style = MaterialTheme.typography.bodySmall,
        )
      },
    )
    if (candidates.isEmpty()) {
      Text(
        text = "No matching ${form.targetDatasetName.lowercase()} found.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    candidates.take(40).forEach { candidate ->
      val isEligible = state.isFormButtonEnabled(candidate, form)
      OutlinedCard(
        onClick = { state.selectEntityRefForActiveForm(candidate.id) },
        enabled = isEligible,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors =
          CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          EntityGeometryIcon(entity = candidate, size = 18.dp)
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = candidate.label,
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            GeoIdText(
              entity = candidate,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          GroundTonalBadge(
            text = if (isEligible) candidate.mapStatusSummaryBadge else "Completed",
            tone =
              when {
                !isEligible -> GroundBadgeTone.NEUTRAL
                candidate.markerSymbol == "✓" -> GroundBadgeTone.PRIMARY
                candidate.markerSymbol == "◐" -> GroundBadgeTone.TERTIARY
                else -> GroundBadgeTone.WARNING
              },
          )
        }
      }
    }
  }
}

/**
 * **Collect data** action for the web dashboard's toolbar: the counterpart of mobile's bottom FAB
 * and its Available Forms sheet. Opens a menu of the survey's forms; choosing one starts it in the
 * right-hand panel via [PrototypeAppState.launchFormFromFab]. Forms whose features are all
 * completed are listed but disabled.
 */
@Composable
internal fun WebCollectDataMenuButton(state: PrototypeAppState) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    WebHeaderButton(
      text = "Collect data",
      icon = Icons.Outlined.Add,
      onClick = { expanded = true },
      tonal = true,
    )
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
    ) {
      Text(
        text = "Start a form",
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      )
      state.webForms.forEach { form ->
        val eligibleCount = state.eligibleEntitiesForForm(form).size
        val canLaunch = !form.requiresEntity || eligibleCount > 0
        DropdownMenuItem(
          enabled = canLaunch,
          text = {
            Column {
              Text(
                text = form.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text =
                  buildString {
                    append("${form.questionCount} questions")
                    if (form.requiresEntity) {
                      append(" • $eligibleCount ${form.targetDatasetName.lowercase()} remaining")
                    } else {
                      append(" • new record")
                    }
                  },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.Description,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          },
          onClick = {
            expanded = false
            state.launchFormFromFab(form.id)
          },
        )
      }
      if (state.webForms.isEmpty()) {
        Text(
          text =
            if (state.forms.isEmpty()) "This survey has no forms yet."
            else "No forms in this survey are available on web.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
    }
  }
}
