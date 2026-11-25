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
package org.openforis.collectone.v2.app.ui.datacollection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.StraightLineVector
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_cancel
import org.openforis.collectone.v2.app.resources.app_clear_search
import org.openforis.collectone.v2.app.resources.app_completed
import org.openforis.collectone.v2.app.resources.app_consent_accept
import org.openforis.collectone.v2.app.resources.app_consent_identity_hidden_body
import org.openforis.collectone.v2.app.resources.app_consent_identity_hidden_title
import org.openforis.collectone.v2.app.resources.app_consent_organizer_terms
import org.openforis.collectone.v2.app.resources.app_consent_protect_privacy_body
import org.openforis.collectone.v2.app.resources.app_consent_protect_privacy_title
import org.openforis.collectone.v2.app.resources.app_consent_public_intro
import org.openforis.collectone.v2.app.resources.app_consent_public_title
import org.openforis.collectone.v2.app.resources.app_consent_title
import org.openforis.collectone.v2.app.resources.app_consent_what_is_public_body
import org.openforis.collectone.v2.app.resources.app_consent_what_is_public_title
import org.openforis.collectone.v2.app.resources.app_continue_with_entity
import org.openforis.collectone.v2.app.resources.app_data_collection_form_fallback_title
import org.openforis.collectone.v2.app.resources.app_dataset_on_map
import org.openforis.collectone.v2.app.resources.app_entity_ref_map_hint
import org.openforis.collectone.v2.app.resources.app_entity_ref_no_preselection
import org.openforis.collectone.v2.app.resources.app_entity_ref_step_badge
import org.openforis.collectone.v2.app.resources.app_filter_dataset_placeholder
import org.openforis.collectone.v2.app.resources.app_no_dataset_items_yet
import org.openforis.collectone.v2.app.resources.app_no_matching_dataset
import org.openforis.collectone.v2.app.resources.app_public_data_banner
import org.openforis.collectone.v2.app.resources.app_ready
import org.openforis.collectone.v2.app.resources.app_required
import org.openforis.collectone.v2.app.resources.app_search_map_features
import org.openforis.collectone.v2.app.resources.app_select
import org.openforis.collectone.v2.app.resources.app_select_from_list
import org.openforis.collectone.v2.app.resources.app_select_on_map
import org.openforis.collectone.v2.app.resources.app_select_target_to_continue
import org.openforis.collectone.v2.app.resources.app_select_target_type
import org.openforis.collectone.v2.app.resources.app_select_target_with_dataset
import org.openforis.collectone.v2.app.resources.app_selected
import org.openforis.collectone.v2.app.resources.app_selected_check
import org.openforis.collectone.v2.app.resources.app_selected_entity
import org.openforis.collectone.v2.app.resources.app_standalone_field_log_with_location
import org.openforis.collectone.v2.app.resources.app_submission_count
import org.openforis.collectone.v2.app.resources.app_target_type_fallback
import org.openforis.collectone.v2.app.ui.common.GeoIdText
import org.openforis.collectone.v2.app.ui.common.PlatformMediaCaptureHandler
import org.openforis.collectone.v2.app.ui.common.appendGeoId
import org.openforis.collectone.v2.app.ui.common.geoIdInlineContent
import org.openforis.collectone.v2.app.ui.common.geoIdLabelAffixes
import org.openforis.collectone.v2.app.ui.common.geoIdPendingSyncDescription
import org.openforis.collectone.v2.app.ui.map.SurveyMapActions
import org.openforis.collectone.v2.app.ui.map.SurveyMapUiState
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.app.ui.text.targetTypeText
import org.openforis.collectone.v2.app.ui.text.wayfindingBadge
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.ui.AlertDialogOverlay
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.core.forms.ui.GeoPointMapViewportState
import org.openforis.collectone.v2.core.forms.ui.MobileFormRunner
import org.openforis.collectone.v2.core.forms.ui.MobileFormTopBar
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.AssistChip
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.SegmentedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton

/**
 * Embedded Data Collection Form overlay rendered inside the mobile/tablet device frame when
 * `DataCollectionUiState.isDataCollectionFormOpen` is `true`.
 *
 * Uses [MobileFormRunner] and [FormWizardController]. The runner's banner is the only top strip: it
 * shows the form title, the target map feature's name and GeoID, and one close button.
 *
 * Once the submission is recorded ([DataCollectionUiState.isSubmissionConfirmationShown]), shows
 * [SubmissionSuccessScreen] instead, whose button leaves the form for the saved map feature.
 *
 * When the form was launched from the bottom-centered FAB without a pre-selected entity and the
 * current wizard step requires an `entityref` ([DataCollectionUiState.isCurrentFormStepEntityRef]),
 * presents an interactive **Map or List** entity selector right inside the data collection step so
 * the collector can choose the target geospatial entity.
 */
@Composable
fun DataCollectionFormScreen(
  uiState: DataCollectionUiState,
  actions: DataCollectionActions,
  mapUiState: SurveyMapUiState,
  mapActions: SurveyMapActions,
  pendingIds: Set<String>,
  distanceAndBearingToEntity: (entityId: String) -> StraightLineVector?,
) {
  DataCollectionFormScreen(
    uiState = uiState,
    actions = actions,
    userGpsCoordinatesLabel = mapUiState.userGpsCoordinatesLabel,
    distanceAndBearingToEntity = distanceAndBearingToEntity,
    geoPointMap = { viewportState, modifier ->
      GeoPointFormMap(
        mapUiState = mapUiState,
        uiState = uiState,
        pendingIds = pendingIds,
        viewportState = viewportState,
        onToggleBasemap = mapActions::toggleBasemapType,
        modifier = modifier,
      )
    },
    entityRefMap = { form, modifier ->
      EntityRefFormMap(
        mapUiState = mapUiState,
        mapActions = mapActions,
        uiState = uiState,
        actions = actions,
        form = form,
        pendingIds = pendingIds,
        modifier = modifier,
      )
    },
  )
}

/**
 * [DataCollectionFormScreen] over the data collection [uiState] and [actions]. The map pieces come
 * from the host: [userGpsCoordinatesLabel] and [distanceAndBearingToEntity] (device GPS), the
 * `geopoint` question's [geoPointMap], and the `entityref` step's [entityRefMap].
 */
@Composable
fun DataCollectionFormScreen(
  uiState: DataCollectionUiState,
  actions: DataCollectionActions,
  userGpsCoordinatesLabel: String,
  distanceAndBearingToEntity: (entityId: String) -> StraightLineVector?,
  geoPointMap: @Composable (viewportState: GeoPointMapViewportState, modifier: Modifier) -> Unit,
  entityRefMap: @Composable (form: FormPreviewItem, modifier: Modifier) -> Unit,
) {
  val controller = uiState.activeFormWizardController ?: return
  if (uiState.isSubmissionConfirmationShown) {
    SubmissionSuccessScreen(onContinue = { actions.dismissSubmissionConfirmation() })
    return
  }
  val entity = uiState.activeDataCollectionEntity
  val form = uiState.activeDataCollectionForm
  val resolvedTitle =
    controller.formState.formDef.title.takeIf { it.isNotBlank() }
      ?: form?.title
      ?: stringResource(Res.string.app_data_collection_form_fallback_title)
  // What the form collects data for, shown under the title in the form's banner.
  val formTargetContext: @Composable () -> Unit = {
    FormTargetContext(
      entity = entity,
      form = form,
      userGpsCoordinatesLabel = userGpsCoordinatesLabel,
    )
  }

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    if (uiState.isActiveSurveyPublicData) {
      PublicDataInFormBanner()
    }

    if (uiState.isCurrentFormStepEntityRef && form != null) {
      // The picker step replaces the form runner, so it shows the runner's banner itself.
      MobileFormTopBar(
        title = resolvedTitle,
        progress = (controller.currentStepIndex + 1f) / controller.totalSteps.coerceAtLeast(1),
        onClose = { actions.closeActiveFormRunner() },
        subtitle = formTargetContext,
      )
      EntityRefStepMapOrListSelector(
        uiState = uiState,
        actions = actions,
        form = form,
        controller = controller,
        distanceAndBearingToEntity = distanceAndBearingToEntity,
        entityRefMap = entityRefMap,
        modifier = Modifier.weight(1f).fillMaxWidth(),
      )
    } else {
      // Shared MobileFormRunner component from org.openforis.collectone.v2.core.forms.ui
      val mediaCaptureHandler = remember { PlatformMediaCaptureHandler() }
      androidx.compose.runtime.CompositionLocalProvider(
        org.openforis.collectone.v2.core.forms.ui.LocalGeoPointMapViewport provides
          { viewportState ->
            geoPointMap(viewportState, Modifier.fillMaxSize())
          },
        org.openforis.collectone.v2.core.forms.ui.LocalMediaCaptureHandler provides
          mediaCaptureHandler,
      ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          MobileFormRunner(
            controller = controller,
            onClose = { actions.closeActiveFormRunner() },
            onSubmitted = { result: FinalizationResult.Success ->
              actions.completeActiveFormSubmission(
                recordInstance = result.recordInstance,
                entityStates = result.entityStates,
                showConfirmation = true,
              )
            },
            headerContext = formTargetContext,
          )
        }
      }
    }
  }
}

/**
 * One line naming what the open form collects data for: the target map feature and its GeoID, the
 * collector's location for a form without a target, or a prompt to pick the target. Drawn in the
 * form banner's subtitle, whose text style and color it inherits.
 */
@Composable
private fun FormTargetContext(
  entity: GeospatialEntityItem?,
  form: FormPreviewItem?,
  userGpsCoordinatesLabel: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      imageVector = Icons.Outlined.LocationOn,
      contentDescription = null,
      modifier = Modifier.size(14.dp),
    )
    Text(
      text =
        buildAnnotatedString {
          if (entity != null) {
            append("${entity.label} • ")
            appendGeoId(entity, geoIdPendingSyncDescription())
          } else if (form != null && !form.requiresEntity) {
            append(
              stringResource(
                Res.string.app_standalone_field_log_with_location,
                userGpsCoordinatesLabel,
              )
            )
          } else {
            append(
              stringResource(
                Res.string.app_select_target_with_dataset,
                form?.targetTypeText?.resolve()
                  ?: stringResource(Res.string.app_target_type_fallback),
                form?.targetDatasetName ?: stringResource(Res.string.app_required),
              )
            )
          }
        },
      inlineContent = geoIdInlineContent(tint = LocalContentColor.current),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * Interactive **Map or List** selector presented at the `entityref` step (`/data/target_entity`) of
 * a data collection form when the form was launched without a pre-selected geospatial entity from
 * the map (e.g., via the bottom-centered FAB).
 */
@Composable
private fun EntityRefStepMapOrListSelector(
  uiState: DataCollectionUiState,
  actions: DataCollectionActions,
  form: FormPreviewItem,
  controller: FormWizardController,
  distanceAndBearingToEntity: (entityId: String) -> StraightLineVector?,
  entityRefMap: @Composable (form: FormPreviewItem, modifier: Modifier) -> Unit,
  modifier: Modifier = Modifier,
) {
  val selectedEntity = uiState.activeDataCollectionEntity
  val allDatasetCandidates = uiState.allDatasetEntitiesForForm(form)
  val filteredCandidates = uiState.filteredEntityRefCandidates
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
      // Step Header Card: shares the selector's background so only its outline sets it apart.
      OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
          CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
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
            TonalBadge(
              text =
                stringResource(Res.string.app_entity_ref_step_badge, currentStepNum, totalSteps),
              tone = BadgeTone.PRIMARY,
            )
            TonalBadge(text = form.targetDatasetName, tone = BadgeTone.SECONDARY)
          }
          Text(
            text = stringResource(Res.string.app_select_target_type, form.targetTypeText.resolve()),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              stringResource(
                Res.string.app_entity_ref_no_preselection,
                form.targetTypeText.resolve().lowercase(),
              ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          // Segmented Toggle: Map vs List selector
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            MainSurveyViewMode.entries.forEachIndexed { idx, mode ->
              val isSelected = uiState.entityRefSelectorViewMode == mode
              SegmentedButton(
                selected = isSelected,
                onClick = { actions.updateEntityRefSelectorViewMode(mode) },
                shape =
                  SegmentedButtonDefaults.itemShape(
                    index = idx,
                    count = MainSurveyViewMode.entries.size,
                  ),
                icon = {
                  Icon(
                    imageVector =
                      if (mode == MainSurveyViewMode.MAP) {
                        Icons.Outlined.Map
                      } else {
                        Icons.AutoMirrored.Outlined.List
                      },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = {
                  Text(
                    text =
                      if (mode == MainSurveyViewMode.MAP) {
                        stringResource(Res.string.app_select_on_map)
                      } else {
                        stringResource(Res.string.app_select_from_list, allDatasetCandidates.size)
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

      // Currently selected map feature (if chosen): an outlined card on the selector's own
      // background (`surface`), with a primary outline marking the selection.
      if (selectedEntity != null) {
        OutlinedCard(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          colors =
            CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(width = 1.5.dp, color = MaterialTheme.colorScheme.primary),
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
                imageVector = Icons.Filled.CheckCircle, // Filled: indicates the selected state.
                contentDescription = stringResource(Res.string.app_selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Column {
                Text(
                  text = stringResource(Res.string.app_selected_entity, selectedEntity.label),
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                val (geoIdPrefix, geoIdSuffix) = geoIdLabelAffixes()
                GeoIdText(
                  entity = selectedEntity,
                  prefix = geoIdPrefix,
                  suffix =
                    "$geoIdSuffix • ${wayfindingBadge(distanceAndBearingToEntity(selectedEntity.id))}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            TonalBadge(
              text = stringResource(Res.string.app_ready),
              tone = BadgeTone.PRIMARY,
            )
          }
        }
      }

      // MAP MODE vs LIST MODE
      if (uiState.entityRefSelectorViewMode == MainSurveyViewMode.MAP) {
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
              entityRefMap(form, Modifier.fillMaxSize())

              // Top-left map instructions badge
              Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.90f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
              ) {
                Text(
                  text =
                    stringResource(
                      Res.string.app_entity_ref_map_hint,
                      form.targetTypeText.resolve().lowercase(),
                    ),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.inverseOnSurface,
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
                text = stringResource(Res.string.app_dataset_on_map, form.targetDatasetName),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
              allDatasetCandidates.take(40).forEach { candidate ->
                EntityRefCandidateOptionCard(
                  isEligible = uiState.isFormButtonEnabled(candidate, form),
                  wayfindingBadge = wayfindingBadge(distanceAndBearingToEntity(candidate.id)),
                  candidate = candidate,
                  isSelected = selectedEntity?.id == candidate.id,
                  onSelect = { actions.selectEntityRefForActiveForm(candidate.id) },
                )
              }
            }
          }
        }
      } else {
        // LIST MODE: Searchable list of candidate entities for the target dataset
        OutlinedTextField(
          value = uiState.entityRefSearchQuery,
          onValueChange = { actions.updateEntityRefSearchQuery(it) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.Search,
              contentDescription = stringResource(Res.string.app_search_map_features),
              modifier = Modifier.size(18.dp),
            )
          },
          trailingIcon = {
            if (uiState.entityRefSearchQuery.isNotEmpty()) {
              IconButton(onClick = { actions.clearEntityRefSearchQuery() }) {
                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription = stringResource(Res.string.app_clear_search),
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          },
          placeholder = {
            Text(
              text =
                stringResource(
                  Res.string.app_filter_dataset_placeholder,
                  form.targetDatasetName.lowercase(),
                ),
              style = MaterialTheme.typography.bodySmall,
            )
          },
        )

        if (filteredCandidates.isEmpty()) {
          OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
              text =
                stringResource(
                  if (uiState.entityRefSearchQuery.isBlank()) {
                    Res.string.app_no_dataset_items_yet
                  } else {
                    Res.string.app_no_matching_dataset
                  },
                  form.targetDatasetName.lowercase(),
                ),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp),
            )
          }
        } else {
          filteredCandidates.take(40).forEach { candidate ->
            EntityRefCandidateOptionCard(
              isEligible = uiState.isFormButtonEnabled(candidate, form),
              wayfindingBadge = wayfindingBadge(distanceAndBearingToEntity(candidate.id)),
              candidate = candidate,
              isSelected = selectedEntity?.id == candidate.id,
              onSelect = { actions.selectEntityRefForActiveForm(candidate.id) },
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
        TextButton(onClick = { actions.closeActiveFormRunner() }) {
          Text(
            stringResource(Res.string.app_cancel),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
          )
        }

        Button(
          onClick = { controller.nextStep() },
          enabled = selectedEntity != null,
          modifier = Modifier.weight(1f, fill = false),
        ) {
          Text(
            text =
              if (selectedEntity != null) {
                stringResource(
                  Res.string.app_continue_with_entity,
                  selectedEntity.label.substringBefore(" •"),
                )
              } else {
                stringResource(
                  Res.string.app_select_target_to_continue,
                  form.targetTypeText.resolve().lowercase(),
                )
              },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
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
  isEligible: Boolean,
  wayfindingBadge: String,
  candidate: GeospatialEntityItem,
  isSelected: Boolean,
  onSelect: () -> Unit,
) {
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
            MaterialTheme.colorScheme.secondaryContainer
          } else {
            MaterialTheme.colorScheme.surface
          },
        contentColor =
          if (isSelected) {
            MaterialTheme.colorScheme.onSecondaryContainer
          } else {
            MaterialTheme.colorScheme.onSurface
          },
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
            text =
              listOf(candidate.markerSymbol, candidate.label)
                .filter { it.isNotEmpty() }
                .joinToString(" "),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color =
              if (isSelected) {
                MaterialTheme.colorScheme.onSecondaryContainer
              } else {
                MaterialTheme.colorScheme.onSurface
              },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
          )
          if (candidate.hasWorkflowStatus) {
            TonalBadge(
              text = candidate.mapStatusSummaryBadge,
              tone =
                when (candidate.markerSymbol) {
                  "✓" -> BadgeTone.PRIMARY
                  "◐" -> BadgeTone.TERTIARY
                  else -> BadgeTone.WARNING
                },
            )
          }
          SyncStatusIndicatorBadge(syncStatus = candidate.syncStatus)
        }
        val (geoIdPrefix, geoIdSuffix) = geoIdLabelAffixes()
        GeoIdText(
          entity = candidate,
          prefix = geoIdPrefix,
          suffix = "$geoIdSuffix • $wayfindingBadge",
          style = MaterialTheme.typography.labelSmall,
          color =
            if (isSelected) {
              MaterialTheme.colorScheme.onSecondaryContainer
            } else {
              MaterialTheme.colorScheme.primary
            },
        )
        Text(
          text =
            candidate.geometry?.type?.let { "${stringResource(it.labelRes)} • " }.orEmpty() +
              pluralStringResource(
                Res.plurals.app_submission_count,
                candidate.submissionCount,
                candidate.submissionCount,
              ),
          style = MaterialTheme.typography.labelSmall,
          color =
            if (isSelected) {
              MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      if (isSelected) {
        TonalBadge(
          text = stringResource(Res.string.app_selected_check),
          tone = BadgeTone.PRIMARY,
        )
      } else if (isEligible) {
        AssistChip(
          onClick = onSelect,
          label = {
            Text(
              text = stringResource(Res.string.app_select),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false,
            )
          },
        )
      } else {
        TonalBadge(
          text = stringResource(Res.string.app_completed),
          tone = BadgeTone.NEUTRAL,
        )
      }
    }
  }
}

/**
 * Persistent reminder banner shown inside an open data collection form when the active survey uses
 * **Public data** (`isPublicData == true`).
 */
@Composable
fun PublicDataInFormBanner(modifier: Modifier = Modifier) {
  Surface(
    color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Outlined.Public,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = stringResource(Res.string.app_public_data_banner),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
      )
    }
  }
}

/**
 * Just-in-time informed consent dialog shown once per survey when a participant starts collecting
 * data in a **Public data** survey or a survey with custom organizer consent terms.
 */
@Composable
fun CollectorConsentModalDialog(
  uiState: DataCollectionUiState,
  actions: DataCollectionActions,
) {
  val prompt = uiState.pendingCollectorConsent ?: return

  AlertDialogOverlay(
    onDismissRequest = { actions.dismissCollectorConsent() },
    icon = {
      Icon(
        imageVector = if (prompt.isPublicData) Icons.Outlined.Public else Icons.Outlined.Shield,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = {
      Text(
        text =
          if (prompt.isPublicData) {
            stringResource(Res.string.app_consent_public_title)
          } else {
            stringResource(Res.string.app_consent_title)
          },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (prompt.isPublicData) {
          Text(
            text = stringResource(Res.string.app_consent_public_intro, prompt.surveyTitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          ConsentBulletItem(
            title = stringResource(Res.string.app_consent_what_is_public_title),
            body = stringResource(Res.string.app_consent_what_is_public_body),
          )
          ConsentBulletItem(
            title = stringResource(Res.string.app_consent_identity_hidden_title),
            body = stringResource(Res.string.app_consent_identity_hidden_body),
          )
          ConsentBulletItem(
            title = stringResource(Res.string.app_consent_protect_privacy_title),
            body = stringResource(Res.string.app_consent_protect_privacy_body),
          )
        }
        if (prompt.customConsentText.isNotBlank()) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = stringResource(Res.string.app_consent_organizer_terms),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = prompt.customConsentText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = { actions.acceptCollectorConsent() }) {
        Text(stringResource(Res.string.app_consent_accept), fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      OutlinedButton(onClick = { actions.dismissCollectorConsent() }) {
        Text(stringResource(Res.string.app_cancel))
      }
    },
  )
}

@Composable
private fun ConsentBulletItem(title: String, body: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = "• $title",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 10.dp),
    )
  }
}
