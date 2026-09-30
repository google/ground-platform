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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.RecordInstance
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.model.FormState
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.FormWizardStep
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.core.forms.ui.MobileFormRunner

/**
 * Canonical XForms `entityref` nodeset path (`/data/target_entity`) representing the geospatial
 * entity reference step (`select_one_from_file <dataset>.csv` / `appearance="map-select"`) when a
 * form requires a target geospatial entity.
 */
const val ENTITY_REF_FIELD_PATH: String = PrototypeAppDataStore.ENTITY_REF_FIELD_PATH

private val defaultResolveFormDefUseCase =
  org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolveFormDefForLaunchUseCase()

/**
 * Ensures that when [form] requires a geospatial entity (`form.requiresEntity == true`), the
 * [FormDef] contains an `entityref` step (`[ENTITY_REF_FIELD_PATH] = "/data/target_entity"`,
 * `appearance = "map-select"`, `required = "true()"`) bound to the eligible candidate entities in
 * `form.targetDatasetId`.
 */
fun ensureEntityRefStepInFormDef(
  baseFormDef: FormDef,
  form: FormPreviewItem,
  candidateEntities: List<GeospatialEntityItem> = emptyList(),
  defaultSelectedEntityId: String = "",
): FormDef =
  defaultResolveFormDefUseCase.ensureEntityRefStepInFormDef(
    baseFormDef = baseFormDef,
    form = form,
    candidateEntities = candidateEntities,
    defaultSelectedEntityId = defaultSelectedEntityId,
  )

/**
 * Resolves the [FormDef] to execute in [FormWizardController] for a given [form] via
 * [org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolveFormDefForLaunchUseCase].
 */
fun resolveFormDefForLaunch(
  customFormDef: FormDef?,
  form: FormPreviewItem,
  candidateEntities: List<GeospatialEntityItem> = emptyList(),
  defaultSelectedEntityId: String = "",
  includeEntityRefStep: Boolean = false,
): FormDef =
  defaultResolveFormDefUseCase(
    customFormDef = customFormDef,
    form = form,
    candidateEntities = candidateEntities,
    defaultSelectedEntityId = defaultSelectedEntityId,
    includeEntityRefStep = includeEntityRefStep,
  )

/**
 * Returns `true` if [step] represents an `entityref` question step (e.g. bound to
 * [ENTITY_REF_FIELD_PATH] `/data/target_entity`, `/data/entity_id`, or configured with
 * `appearance="map-select"` / `"entityref"`).
 */
fun isWizardStepEntityRef(step: FormWizardStep?): Boolean {
  if (step == null) return false
  val controls =
    when (step) {
      is FormWizardStep.QuestionStep -> listOf(step.control)
      is FormWizardStep.FieldListGroupStep -> step.controls
      else -> emptyList()
    }
  return controls.any { ctrl ->
    ctrl.canonicalPath == ENTITY_REF_FIELD_PATH ||
      ctrl.canonicalPath.endsWith("/target_entity") ||
      ctrl.canonicalPath.endsWith("/entity_id") ||
      ctrl.canonicalPath.endsWith("/sample_plot_entity") ||
      ctrl.canonicalPath.endsWith("/past_individual_id") ||
      ctrl.canonicalPath.endsWith("/primary_respondent_id") ||
      ctrl.appearance.contains("map-select", ignoreCase = true) ||
      ctrl.appearance.contains("entityref", ignoreCase = true)
  }
}

/**
 * Returns `true` if [step] contains a geometry (`geopoint`, `geotrace`, or `geoshape`) question
 * control.
 */
fun isWizardStepGeoPoint(step: FormWizardStep?): Boolean {
  if (step == null) return false
  val controls =
    when (step) {
      is FormWizardStep.QuestionStep -> listOf(step.control)
      is FormWizardStep.FieldListGroupStep -> step.controls
      else -> emptyList()
    }
  return controls.any {
    val dt = it.fieldState.dataType
    dt == groundplatform.v2.forms.DataType.TYPE_GEOPOINT ||
      dt == groundplatform.v2.forms.DataType.TYPE_GEOTRACE ||
      dt == groundplatform.v2.forms.DataType.TYPE_GEOSHAPE
  }
}

/**
 * Extracts all answered fields from [recordInstance] (and [controller]'s [FormState]) into a list
 * of [SubmissionFieldEntry] items via
 * [org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CompleteFormSubmissionUseCase].
 */
fun extractSubmissionFieldsFromRecord(
  recordInstance: RecordInstance,
  controller: FormWizardController?,
  resolvedFormDef: FormDef,
  form: FormPreviewItem,
  gnssBadge: String,
): List<SubmissionFieldEntry> =
  org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CompleteFormSubmissionUseCase
    .extractSubmissionFieldsFromRecord(
      recordInstance = recordInstance,
      controller = controller,
      resolvedFormDef = resolvedFormDef,
      form = form,
      gnssBadge = gnssBadge,
    )

/**
 * Embedded Data Collection Form overlay rendered inside the mobile/tablet device frame when
 * [PrototypeAppState.isDataCollectionFormOpen] is `true`.
 *
 * Uses the same [MobileFormRunner] and [FormWizardController] components as
 * `devtools/formdebugger`, preceded by a compact context banner displaying the target Geospatial
 * Entity name, GeoID, and active form title.
 *
 * When the form was launched from the bottom-centered FAB without a pre-selected entity and the
 * current wizard step requires an `entityref` (`[PrototypeAppState.isCurrentFormStepEntityRef]`),
 * presents an interactive **Map or List** entity selector right inside the data collection step so
 * the collector can choose the target geospatial entity.
 */
@Composable
fun DataCollectionFormScreen(state: PrototypeAppState) {
  val controller = state.activeFormWizardController ?: return
  val entity = state.activeDataCollectionEntity
  val form = state.activeDataCollectionForm
  val resolvedTitle =
    controller.formState.formDef.title.takeIf { it.isNotBlank() }
      ?: form?.title
      ?: "Data Collection Form"

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Compact target entity & form context banner at the top of the device screen
    Surface(
      color = MaterialTheme.colorScheme.inverseSurface,
      contentColor = MaterialTheme.colorScheme.inverseOnSurface,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          IconButton(onClick = { state.closeActiveFormRunner() }, modifier = Modifier.size(36.dp)) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back to Survey",
              tint = Color.White,
              modifier = Modifier.size(18.dp),
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = Color(0xFF8BD6B1),
                modifier = Modifier.size(14.dp),
              )
              Text(
                text =
                  if (entity != null) {
                    "${entity.label} • ${entity.geoId}"
                  } else if (form != null && !form.requiresEntity) {
                    "Standalone Field Log • ${state.userGpsCoordinatesLabel}"
                  } else {
                    "Select ${form?.targetSingularTypeLabel ?: "Feature"} (${form?.targetDatasetName ?: "Required"})"
                  },
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8BD6B1),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Text(
              text =
                if (form != null) {
                  "${form.ctaLabel} — $resolvedTitle"
                } else {
                  resolvedTitle
                },
              style = MaterialTheme.typography.labelSmall,
              color = Color.White.copy(alpha = 0.88f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        AssistChip(
          onClick = { state.closeActiveFormRunner() },
          colors =
            androidx.compose.material3.AssistChipDefaults.assistChipColors(
              containerColor = Color(0xFF1E563D),
              labelColor = Color.White,
              leadingIconContentColor = Color.White,
            ),
          border =
            androidx.compose.material3.AssistChipDefaults.assistChipBorder(
              enabled = true,
              borderColor = Color(0xFF386B52),
            ),
          label = {
            Text(
              "Map",
              style = MaterialTheme.typography.labelSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Cancel Form",
              modifier = Modifier.size(14.dp),
            )
          },
          modifier = Modifier.height(32.dp),
        )
      }
    }

    if (state.isCurrentFormStepEntityRef && form != null) {
      EntityRefStepMapOrListSelector(
        state = state,
        form = form,
        controller = controller,
        modifier = Modifier.weight(1f).fillMaxWidth(),
      )
    } else {
      // Shared MobileFormRunner component from org.groundplatform.v2.core.forms.ui
      val mediaCaptureHandler = remember { PrototypeMediaCaptureHandler() }
      androidx.compose.runtime.CompositionLocalProvider(
        org.groundplatform.v2.core.forms.ui.LocalGeoPointMapViewport provides
          { viewportState ->
            GeoPointFormMapboxViewport(
              state = state,
              viewportState = viewportState,
              modifier = Modifier.fillMaxSize(),
            )
          },
        org.groundplatform.v2.core.forms.ui.LocalMediaCaptureHandler provides mediaCaptureHandler,
      ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          MobileFormRunner(
            controller = controller,
            onClose = { state.closeActiveFormRunner() },
            onSubmitted = { result: FinalizationResult.Success ->
              state.completeActiveFormSubmission(result.recordInstance, result.entityStates)
            },
          )
        }
      }
    }
  }
}

/**
 * Interactive **Map or List** selector presented at the `entityref` step (`/data/target_entity`) of
 * a data collection form when the form was launched without a pre-selected geospatial entity from
 * the map (e.g., via the bottom-centered FAB).
 */
@Composable
private fun EntityRefStepMapOrListSelector(
  state: PrototypeAppState,
  form: FormPreviewItem,
  controller: FormWizardController,
  modifier: Modifier = Modifier,
) {
  val selectedEntity = state.activeDataCollectionEntity
  val allDatasetCandidates = state.allDatasetEntitiesForForm(form)
  val filteredCandidates = state.filteredEntityRefCandidates
  val currentStepNum = controller.currentStepIndex + 1
  val totalSteps = controller.steps.size.coerceAtLeast(1)

  Column(
    modifier = modifier.background(MaterialTheme.colorScheme.surface),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(
      modifier =
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Step Header Card
      OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
          CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
          ),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          @OptIn(ExperimentalLayoutApi::class)
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            GroundTonalBadge(
              text = "STEP $currentStepNum OF $totalSteps • REQUIRED",
              tone = GroundBadgeTone.PRIMARY,
            )
            GroundTonalBadge(text = form.targetDatasetName, tone = GroundBadgeTone.SECONDARY)
          }
          Text(
            text = "Select ${form.targetSingularTypeLabel}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              "No ${form.targetSingularTypeLabel.lowercase()} was pre-selected on the map. Choose a target ${form.targetSingularTypeLabel.lowercase()} below using the Map or List to continue data collection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          // Segmented Toggle: Map vs List selector
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            MainSurveyViewMode.entries.forEachIndexed { idx, mode ->
              val isSelected = state.entityRefSelectorViewMode == mode
              SegmentedButton(
                selected = isSelected,
                onClick = { state.updateEntityRefSelectorViewMode(mode) },
                shape =
                  SegmentedButtonDefaults.itemShape(
                    index = idx,
                    count = MainSurveyViewMode.entries.size,
                  ),
                icon = {
                  Icon(
                    imageVector =
                      if (mode == MainSurveyViewMode.MAP) {
                        Icons.Default.Map
                      } else {
                        Icons.AutoMirrored.Filled.List
                      },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = {
                  Text(
                    text =
                      if (mode == MainSurveyViewMode.MAP) {
                        "Select on Map"
                      } else {
                        "Select from List (${allDatasetCandidates.size})"
                      },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                  )
                },
              )
            }
          }
        }
      }

      // Currently selected entity summary banner (if chosen)
      if (selectedEntity != null) {
        ElevatedCard(
          modifier =
            Modifier.fillMaxWidth()
              .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.medium,
              ),
          colors =
            CardDefaults.elevatedCardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Column {
                Text(
                  text = "Selected: ${selectedEntity.label}",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text =
                    "GeoID: ${selectedEntity.geoId} • ${state.formattedWayfindingBadgeForEntity(selectedEntity.id)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            GroundTonalBadge(text = "Ready", tone = GroundBadgeTone.PRIMARY)
          }
        }
      }

      // MAP MODE vs LIST MODE
      if (state.entityRefSelectorViewMode == MainSurveyViewMode.MAP) {
        // Interactive Map Picker Canvas + Tappable Feature Chips for the Target Dataset
        OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(240.dp)
                  .clip(MaterialTheme.shapes.medium)
                  .background(Color.Transparent)
            ) {
              EntityRefFormMapboxViewport(
                state = state,
                form = form,
                modifier = Modifier.fillMaxSize(),
              )

              // Top-left map instructions badge
              Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                shape = MaterialTheme.shapes.small,
                color = Color(0xFF0E2219).copy(alpha = 0.90f),
              ) {
                Text(
                  text =
                    "Tap a ${form.targetSingularTypeLabel.lowercase()} on map or below to select",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFF8BD6B1),
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }

            // Interactive map feature chips for quick one-tap selection on map
            Column(
              modifier = Modifier.fillMaxWidth().padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = "${form.targetDatasetName.uppercase()} ON MAP",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
              allDatasetCandidates.take(40).forEach { candidate ->
                EntityRefCandidateOptionCard(
                  state = state,
                  form = form,
                  candidate = candidate,
                  isSelected = selectedEntity?.id == candidate.id,
                  onSelect = { state.selectEntityRefForActiveForm(candidate.id) },
                )
              }
            }
          }
        }
      } else {
        // LIST MODE: Searchable list of candidate entities for the target dataset
        OutlinedTextField(
          value = state.entityRefSearchQuery,
          onValueChange = { state.updateEntityRefSearchQuery(it) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = "Search map features",
              modifier = Modifier.size(18.dp),
            )
          },
          trailingIcon = {
            if (state.entityRefSearchQuery.isNotEmpty()) {
              IconButton(onClick = { state.clearEntityRefSearchQuery() }) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Clear search",
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          },
          placeholder = {
            Text(
              text = "Filter ${form.targetDatasetName.lowercase()} by name or GeoID...",
              style = MaterialTheme.typography.bodySmall,
            )
          },
        )

        if (filteredCandidates.isEmpty()) {
          OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "No matching ${form.targetDatasetName.lowercase()} found.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp),
            )
          }
        } else {
          filteredCandidates.take(40).forEach { candidate ->
            EntityRefCandidateOptionCard(
              state = state,
              form = form,
              candidate = candidate,
              isSelected = selectedEntity?.id == candidate.id,
              onSelect = { state.selectEntityRefForActiveForm(candidate.id) },
            )
          }
        }
      }
    }

    // Bottom Step Action Bar (Cancel vs Continue to Form Questions)
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      tonalElevation = 2.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = { state.closeActiveFormRunner() }) {
          Text("Cancel", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }

        Button(
          onClick = { controller.nextStep() },
          enabled = selectedEntity != null,
          modifier = Modifier.weight(1f, fill = false),
        ) {
          Text(
            text =
              if (selectedEntity != null) {
                "Continue with ${selectedEntity.label.substringBefore(" •")}"
              } else {
                "Select a ${form.targetSingularTypeLabel.lowercase()} to continue"
              },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun EntityRefCandidateOptionCard(
  state: PrototypeAppState,
  form: FormPreviewItem,
  candidate: GeospatialEntityItem,
  isSelected: Boolean,
  onSelect: () -> Unit,
) {
  val isEligible = state.isFormButtonEnabled(candidate, form)
  val wayfindingBadge = state.formattedWayfindingBadgeForEntity(candidate.id)
  OutlinedCard(
    onClick = { if (isEligible) onSelect() },
    enabled = isEligible,
    modifier =
      Modifier.fillMaxWidth()
        .then(
          if (isSelected) {
            Modifier.border(
              width = 1.5.dp,
              color = MaterialTheme.colorScheme.primary,
              shape = MaterialTheme.shapes.small,
            )
          } else {
            Modifier
          }
        ),
    shape = MaterialTheme.shapes.small,
    colors =
      CardDefaults.outlinedCardColors(
        containerColor =
          if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
          } else {
            MaterialTheme.colorScheme.surface
          }
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "${candidate.markerSymbol} ${candidate.label}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
          )
          GroundTonalBadge(
            text = candidate.mapStatusSummaryBadge,
            tone =
              when (candidate.markerSymbol) {
                "✓" -> GroundBadgeTone.PRIMARY
                "◐" -> GroundBadgeTone.TERTIARY
                else -> GroundBadgeTone.WARNING
              },
          )
          SyncStatusIndicatorBadge(syncStatus = candidate.syncStatus)
        }
        Text(
          text = "GeoID: ${candidate.geoId} • $wayfindingBadge",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text =
            "${candidate.geometryTypeLabel} • ${candidate.submissionCount} ${if (candidate.submissionCount == 1) "submission" else "submissions"}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      if (isSelected) {
        GroundTonalBadge(text = "Selected ✓", tone = GroundBadgeTone.PRIMARY)
      } else if (isEligible) {
        AssistChip(
          onClick = onSelect,
          label = {
            Text(
              text = "Select",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false,
            )
          },
        )
      } else {
        GroundTonalBadge(text = "Completed", tone = GroundBadgeTone.NEUTRAL)
      }
    }
  }
}

/** Backward-compatible alias for [DataCollectionFormScreen]. */
@Composable
fun PrototypeDataCollectionFormScreen(state: PrototypeAppState) {
  DataCollectionFormScreen(state)
}
