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
package org.groundplatform.v2.devtools.prototypeapp.ui.datacollection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormSubmissionsGroup
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MainSurveyViewMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus
import org.groundplatform.v2.devtools.prototypeapp.ui.common.GroundFilterChip
import org.groundplatform.v2.devtools.prototypeapp.ui.common.horizontalScrollWithMouseDrag
import org.groundplatform.v2.devtools.prototypeapp.ui.map.LayerVisibilityToggle
import org.groundplatform.v2.devtools.prototypeapp.ui.map.layerNameColor
import org.groundplatform.v2.devtools.prototypeapp.ui.navigation.SurveyMapView

/**
 * Unified Persistent Bottom Sheet content for [SurveyMapView] (Option A):
 * 1. When a [GeospatialEntityItem] is selected (via map tap or list selection), renders
 *
 * ```
 *    [EntityBottomSheetCard] with a `"All map features"` back pill, `simplestyle-spec` marker, baseline
 *    attributes, form launchers, and `1:N` submissions.
 * ```
 * 2. When a standalone [SubmissionPreviewItem] is selected from the searchable list, renders
 *
 * ```
 *    [SubmissionFullDetailsCard] with a back button returning to the searchable list.
 * ```
 * 3. When no specific item is selected, renders [SurveyListView] — peeking at the bottom of the map
 *
 * ```
 *    with a Search bar and category filter chips (`All`, `Places`, `Map features`) and expanding
 *    into the full grouped list.
 * ```
 *
 * When [isSidePanel] is `true` (the web dashboard's left-hand panel), the sheet expand/collapse
 * toggles and mobile-only field actions (data collection launchers, `Navigate`, sync status chips)
 * are hidden.
 */
@Composable
internal fun SurveyPersistentBottomSheetContent(
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
  isSidePanel: Boolean = false,
) {
  val dataCollectionUiState = state.dataCollectionUiState
  val dataCollection = state.dataCollection
  val selectedEntity = dataCollectionUiState.selectedEntity
  val selectedSubmission = dataCollectionUiState.selectedSubmission
  val isDark = state.isDarkTheme
  val textColor = MaterialTheme.colorScheme.onSurface

  when {
    // The web dashboard's left-hand panel always lists map features; details open in the floating
    // card on the right (`WebEntityDetailsCard`), so browsing and inspecting stay side by side.
    isSidePanel -> {
      BottomSheetSearchableListContent(state = state, modifier = modifier, isSidePanel = true)
    }
    selectedEntity != null -> {
      EntityBottomSheetCard(entity = selectedEntity, state = state, modifier = modifier)
    }
    selectedSubmission != null -> {
      Column(
        modifier =
          modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp)
      ) {
        SubmissionFullDetailsCard(
          submission = selectedSubmission,
          state = state,
          isDark = isDark,
          textColor = textColor,
          backLabel = "Back to Searchable List",
          onBack = { dataCollection.returnToBottomSheetList() },
          onSharePdf = {
            if (isSidePanel) {
              dataCollection.downloadSubmissionPdf(selectedSubmission.id)
            } else {
              dataCollection.shareSubmissionPdf(selectedSubmission.id)
            }
          },
          isSidePanel = isSidePanel,
        )
      }
    }
    else -> {
      BottomSheetSearchableListContent(
        state = state,
        modifier = modifier,
        isSidePanel = isSidePanel,
      )
    }
  }
}

/**
 * Shared component that renders chronological submissions grouped by form (`FormSubmissionGroup`),
 * used in both [EntityBottomSheetCard] (for a single entity's `1:N` submissions) and
 * [BottomSheetSearchableListContent] (for survey-wide submissions).
 */
@Composable
internal fun FormGroupedSubmissionsSection(
  groups: List<FormSubmissionsGroup>,
  state: PrototypeAppState,
  showTargetEntityLabel: Boolean,
  showFormActionSubtitle: Boolean,
  onSelectSubmission: (SubmissionPreviewItem) -> Unit,
  showSyncStatus: Boolean = true,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    groups.forEach { group ->
      val form = group.form
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        // Form Title Group Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = form.title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            if (showFormActionSubtitle) {
              Text(
                text = "Action: \"${form.ctaLabel}\" • ${form.version}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }

        group.submissions.forEachIndexed { index, sub ->
          OutlinedCard(
            onClick = { onSelectSubmission(sub) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            colors =
              CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                if (showTargetEntityLabel) {
                  val targetDisplayLabel =
                    if (sub.hasAttachedEntity) {
                      "${sub.targetTypeLabel}: ${sub.entityLabel}"
                    } else if (sub.coordinatesLabel.isNotBlank()) {
                      "${sub.targetTypeLabel} • No attached map feature (${sub.coordinatesLabel.substringBefore(" (")})"
                    } else {
                      "${sub.targetTypeLabel} • No attached map feature"
                    }
                  Text(
                    text = targetDisplayLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
                Row(
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint =
                      if (showTargetEntityLabel) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                      } else {
                        MaterialTheme.colorScheme.onSurface
                      },
                    modifier = Modifier.size(13.dp),
                  )
                  Text(
                    text = sub.collectorName,
                    style =
                      if (showTargetEntityLabel) {
                        MaterialTheme.typography.labelSmall
                      } else {
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                      },
                    color =
                      if (showTargetEntityLabel) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                      } else {
                        MaterialTheme.colorScheme.onSurface
                      },
                  )
                  if (index == 0 && !showTargetEntityLabel) {
                    GroundTonalBadge(text = "LATEST", tone = GroundBadgeTone.SECONDARY)
                  }
                  if (showSyncStatus) {
                    SyncStatusIndicatorBadge(
                      syncStatus = sub.syncStatus,
                      onClick = { state.cycleSubmissionSyncStatus(sub.id) },
                    )
                  }
                }
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(11.dp),
                  )
                  Text(
                    text = "${sub.timestamp} • ${sub.formVersion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              IconButton(onClick = { onSelectSubmission(sub) }, modifier = Modifier.size(32.dp)) {
                Icon(
                  imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                  contentDescription = "Submission details",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Height shared by entity dataset header rows and map feature rows in the web dashboard's left-hand
 * panel list, so both kinds of rows have the same height.
 */
private val SidePanelListRowHeight = 40.dp

/**
 * Start indent of map feature rows in the web dashboard's left-hand panel list, lining their
 * geometry icon up under the dataset header's color swatch (right of the collapse chevron).
 */
private val SidePanelEntityRowIndent = 30.dp

/** Vertical content padding of the compact search field in the web dashboard's left-hand panel. */
private val SidePanelSearchFieldVerticalPadding = 8.dp

/**
 * Search field at the top of the searchable list. Built from [BasicTextField] and
 * [OutlinedTextFieldDefaults.DecorationBox] so the [compact] variant (web dashboard side panel) can
 * use tighter vertical padding and `bodyMedium` text, about 48 dp tall (the icon slots' minimum)
 * instead of the 56 dp default kept on mobile for touch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  onClear: () -> Unit,
  compact: Boolean,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val colors =
    OutlinedTextFieldDefaults.colors(
      focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      focusedBorderColor = MaterialTheme.colorScheme.primary,
      unfocusedBorderColor = Color.Transparent,
    )
  val shape = MaterialTheme.shapes.extraLarge
  val textStyle =
    if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
  val contentPadding =
    if (compact) {
      OutlinedTextFieldDefaults.contentPadding(
        top = SidePanelSearchFieldVerticalPadding,
        bottom = SidePanelSearchFieldVerticalPadding,
      )
    } else {
      OutlinedTextFieldDefaults.contentPadding()
    }

  BasicTextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = modifier,
    singleLine = true,
    textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    interactionSource = interactionSource,
  ) { innerTextField ->
    OutlinedTextFieldDefaults.DecorationBox(
      value = query,
      innerTextField = innerTextField,
      enabled = true,
      singleLine = true,
      visualTransformation = VisualTransformation.None,
      interactionSource = interactionSource,
      placeholder = {
        Text(
          text = "Search...",
          style =
            if (compact) MaterialTheme.typography.bodyMedium
            else MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      leadingIcon = {
        Icon(
          imageVector = Icons.Outlined.Search,
          contentDescription = "Search",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
      },
      trailingIcon =
        if (query.isNotEmpty()) {
          {
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Clear Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        } else {
          null
        },
      colors = colors,
      contentPadding = contentPadding,
      container = {
        OutlinedTextFieldDefaults.Container(
          enabled = true,
          isError = false,
          interactionSource = interactionSource,
          colors = colors,
          shape = shape,
        )
      },
    )
  }
}

/**
 * Searchable List content embedded inside the unified persistent bottom sheet (
 * [SurveyPersistentBottomSheetContent]) when no specific entity or submission is selected:
 * - Peeks at the bottom of the map with the Search bar (`"Search..."`) and an Expand/Collapse sheet
 *   button.
 * - Expands to display **Map layers** (grouped by spatial layer with un-nested entity records,
 *   reusing [EntitySummaryHeader] and [EntityMetadataAndActionsRow]), **Data tables** (tabular
 *   datasets), and **Places** (geographic places, landmarks, and coordinates in the survey region).
 *
 * When [isSidePanel] is `true` (the web dashboard's left-hand panel), the Expand/Collapse sheet
 * button is hidden and the list uses a denser, mouse-oriented layout: dataset header rows and map
 * feature rows share one compact [SidePanelListRowHeight], each dataset header has a chevron left
 * of its name that collapses or expands its map features
 * ([PrototypeAppState.toggleListDatasetCollapsed]; all datasets show expanded while searching), and
 * the layer visibility toggle appears only while the header row is hovered or focused, or while the
 * layer is hidden.
 */
@Composable
internal fun BottomSheetSearchableListContent(
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
  isSidePanel: Boolean = false,
) {
  val allPlaces = state.places
  val apiPlaces = state.mapboxPlacesApiResults
  val isAirplaneMode = state.isAirplaneMode
  val allEntities = state.entities
  val listTab = state.listFilterTab
  val listQuery = state.listSearchQuery
  val mapLayers = state.mapLayers

  val matchedPlaces =
    remember(allPlaces, apiPlaces, listTab, listQuery, isAirplaneMode) { state.filteredListPlaces }
  val matchedEntities = remember(allEntities, listTab, listQuery) { state.filteredListEntities }
  val groupedEntities = remember(matchedEntities, mapLayers) { state.groupedFilteredListEntities }

  Column(modifier = modifier) {
    // Sticky Peek Header: Search Bar + Expand/Collapse Toggle
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ListSearchField(
          query = state.listSearchQuery,
          onQueryChange = {
            state.updateListSearchQuery(it)
            if (it.isNotEmpty() && !state.isEntityBottomSheetExpanded) {
              state.updateEntityBottomSheetExpanded(true)
            }
          },
          onClear = { state.clearListSearchQuery() },
          compact = isSidePanel,
          modifier = Modifier.weight(1f),
        )

        if (!isSidePanel) {
          IconButton(
            onClick = {
              if (state.isEntityBottomSheetExpanded) {
                state.setMainSurveyViewMode(MainSurveyViewMode.MAP)
              } else {
                state.setMainSurveyViewMode(MainSurveyViewMode.LIST)
              }
            },
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              imageVector =
                if (state.isEntityBottomSheetExpanded) {
                  Icons.Outlined.KeyboardArrowDown
                } else {
                  Icons.Outlined.KeyboardArrowUp
                },
              contentDescription =
                if (state.isEntityBottomSheetExpanded) {
                  "Collapse list sheet to map"
                } else {
                  "Expand searchable list sheet"
                },
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp),
            )
          }
        }
      }

      // Offline / Airplane mode banner in the bottom sheet explaining that search is only in local
      // map features and that Places search is not available offline.
      if (isAirplaneMode) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = "Device offline",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Device offline • Searching local ${state.activeEntitiesCountNoun} only",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                  text =
                    "Search is only in local ${state.activeEntitiesCountNoun}. Places search is not available offline.",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                )
              }
            }

            Spacer(modifier = Modifier.width(6.dp))

            AssistChip(
              onClick = { state.updateAirplaneMode(false) },
              label = {
                Text(
                  text = "Turn Off",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  softWrap = false,
                )
              },
            )
          }
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // Scrollable Searchable List of Map features (grouped by dataset) and Places. The web side
    // panel uses a denser layout (mouse pointer) than the touch-first mobile sheet.
    Column(
      modifier =
        Modifier.weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(
            horizontal = if (isSidePanel) 8.dp else 14.dp,
            vertical = if (isSidePanel) 8.dp else 10.dp,
          ),
      verticalArrangement = Arrangement.spacedBy(if (isSidePanel) 8.dp else 12.dp),
    ) {
      // 1. MAP LAYERS SECTION
      if (groupedEntities.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Layers,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "MAP LAYERS",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.6.sp,
              ),
          )
        }

        groupedEntities.forEach { group ->
          val layer = group.layer
          val groupDatasetId = group.entities.first().datasetId
          val isLayerSelected = isSidePanel && state.selectedLayerDatasetId == groupDatasetId
          // Collapsing a dataset's map features is only offered in the web side panel. While a
          // search query is active every dataset is shown expanded (see
          // PrototypeAppState.isListDatasetCollapsed) and the chevron is disabled.
          val isCollapsed = isSidePanel && state.isListDatasetCollapsed(groupDatasetId)
          key(groupDatasetId) {
            val headerInteractionSource = remember { MutableInteractionSource() }
            val isHeaderHovered by headerInteractionSource.collectIsHoveredAsState()
            var isHeaderFocused by remember { mutableStateOf(false) }
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(if (isSidePanel) 0.dp else 8.dp),
            ) {
              // Layer Group Header Banner. In the web side panel, clicking it selects the layer and
              // the chevron left of the dataset name collapses or expands its map features.
              Row(
                modifier =
                  if (isSidePanel) {
                    Modifier.fillMaxWidth()
                      .height(SidePanelListRowHeight)
                      .clip(MaterialTheme.shapes.small)
                      .background(
                        if (isLayerSelected) {
                          MaterialTheme.colorScheme.secondaryContainer
                        } else {
                          Color.Transparent
                        }
                      )
                      .hoverable(headerInteractionSource)
                      .onFocusChanged { isHeaderFocused = it.hasFocus }
                      .clickable { state.selectLayer(groupDatasetId) }
                      .padding(start = 2.dp, end = 2.dp)
                  } else {
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
                  },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  modifier = Modifier.weight(1f),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(if (isSidePanel) 6.dp else 8.dp),
                ) {
                  val datasetLabel = layer?.label ?: group.datasetName
                  if (isSidePanel) {
                    IconButton(
                      onClick = { state.toggleListDatasetCollapsed(groupDatasetId) },
                      enabled = state.listSearchQuery.isBlank(),
                      modifier = Modifier.size(24.dp),
                    ) {
                      Icon(
                        imageVector =
                          if (isCollapsed) {
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight
                          } else {
                            Icons.Outlined.KeyboardArrowDown
                          },
                        contentDescription =
                          if (isCollapsed) "Expand $datasetLabel" else "Collapse $datasetLabel",
                        tint =
                          (if (isLayerSelected) {
                              MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                              MaterialTheme.colorScheme.onSurfaceVariant
                            })
                            .copy(alpha = if (state.listSearchQuery.isBlank()) 1f else 0.38f),
                        modifier = Modifier.size(18.dp),
                      )
                    }
                  }
                  if (layer != null) {
                    Box(
                      modifier =
                        Modifier.size(14.dp)
                          .clip(MaterialTheme.shapes.extraSmall)
                          .background(Color(layer.colorHex).copy(alpha = 0.25f))
                          .border(2.dp, Color(layer.colorHex), MaterialTheme.shapes.extraSmall)
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Outlined.LocationOn,
                      contentDescription = null,
                      tint =
                        if (isLayerSelected) {
                          MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                          MaterialTheme.colorScheme.primary
                        },
                      modifier = Modifier.size(14.dp),
                    )
                  }
                  Text(
                    text = datasetLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color =
                      if (isLayerSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                      } else {
                        layerNameColor(isVisible = layer?.isVisible ?: true)
                      },
                    maxLines = if (isSidePanel) 1 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                  )
                }

                if (layer != null) {
                  if (isSidePanel) {
                    // Web: the eye toggle appears while the header row is hovered or focused, and
                    // stays visible while the layer is hidden as a state indicator. Its space is
                    // always reserved so the row does not jitter on hover.
                    Box(
                      modifier = Modifier.size(SidePanelListRowHeight - 4.dp),
                      contentAlignment = Alignment.Center,
                    ) {
                      if (isHeaderHovered || isHeaderFocused || !layer.isVisible) {
                        LayerVisibilityToggle(
                          layer = layer,
                          state = state,
                          buttonSize = SidePanelListRowHeight - 4.dp,
                          iconSize = 18.dp,
                        )
                      }
                    }
                  } else {
                    LayerVisibilityToggle(layer = layer, state = state)
                  }
                } else {
                  GroundTonalBadge(text = "${group.entities.size}", tone = GroundBadgeTone.PRIMARY)
                }
              }

              if (!isCollapsed) {
                // One single-line row per entity record: layer marker, label, and status chip.
                val maxRenderedFeatures = 200
                val displayedFeatures =
                  if (group.entities.size > maxRenderedFeatures) {
                    group.entities.take(maxRenderedFeatures)
                  } else {
                    group.entities
                  }
                Column(modifier = Modifier.fillMaxWidth()) {
                  displayedFeatures.forEach { entity ->
                    EntityListRow(
                      entity = entity,
                      isSelected = entity.id == state.dataCollectionUiState.selectedEntityId,
                      onClick = { state.selectEntityFromList(entity.id) },
                      compact = isSidePanel,
                      compactHeight = SidePanelListRowHeight,
                      compactStartIndent = SidePanelEntityRowIndent,
                      layer = layer,
                    )
                  }
                }
                if (group.entities.size > maxRenderedFeatures) {
                  Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text =
                        "Showing first $maxRenderedFeatures of ${group.entities.size} ${layer?.pluralNoun ?: "features"} in ${group.datasetName}. Use the search bar above to filter all ${group.entities.size} ${layer?.pluralNoun ?: "features"}.",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                      fontWeight = FontWeight.Medium,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 2. DATA TABLES SECTION — only displayed when tabular datasets exist and have matching
      // entries
      // (Currently no non-spatial tabular datasets configured in the active survey)

      // 3. PLACES SECTION — only available when online (!isAirplaneMode) and entries match
      if (!isAirplaneMode && matchedPlaces.isNotEmpty()) {
        if (groupedEntities.isNotEmpty()) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Explore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "PLACES",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.6.sp,
              ),
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(if (isSidePanel) 6.dp else 8.dp)) {
          matchedPlaces.forEach { place ->
            val isSelectedPlace = state.selectedPlaceId == place.id
            val isNavigatingPlace = state.isNavigatingToPlace(place.id)
            val placeWayfindingBadge = state.formattedWayfindingBadgeForPlace(place.id)

            OutlinedCard(
              onClick = { state.selectPlace(place.id) },
              modifier = Modifier.fillMaxWidth(),
              shape = MaterialTheme.shapes.small,
              border =
                if (isSelectedPlace) {
                  BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else {
                  CardDefaults.outlinedCardBorder()
                },
              colors =
                CardDefaults.outlinedCardColors(
                  containerColor =
                    if (isSelectedPlace) {
                      MaterialTheme.colorScheme.secondaryContainer
                    } else {
                      MaterialTheme.colorScheme.surface
                    },
                  contentColor =
                    if (isSelectedPlace) {
                      MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                      MaterialTheme.colorScheme.onSurface
                    },
                ),
            ) {
              Column(
                modifier =
                  Modifier.fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = if (isSidePanel) 8.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(if (isSidePanel) 4.dp else 6.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Surface(
                      modifier = Modifier.size(24.dp),
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.tertiaryContainer,
                      contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = Icons.Outlined.Explore,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.onTertiaryContainer,
                          modifier = Modifier.size(14.dp),
                        )
                      }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = place.name,
                        style =
                          MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color =
                          if (isSelectedPlace) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                          } else {
                            MaterialTheme.colorScheme.onSurface
                          },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = "${place.categoryLabel} • ${place.regionSubtitle}",
                        style = MaterialTheme.typography.labelSmall,
                        color =
                          if (isSelectedPlace) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                          } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                          },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }

                  FilledTonalIconButton(
                    onClick = { state.selectPlace(place.id) },
                    modifier = Modifier.size(30.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Explore,
                      contentDescription = "Fly to place on map",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }

                Row(
                  modifier =
                    Modifier.fillMaxWidth().horizontalScrollWithMouseDrag(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  GroundTonalBadge(text = place.categoryLabel, tone = GroundBadgeTone.SECONDARY)
                  GroundTonalBadge(
                    text = place.coordinatesLabel,
                    tone = GroundBadgeTone.SECONDARY,
                    monospace = true,
                  )
                  if (!isSidePanel && placeWayfindingBadge.isNotEmpty()) {
                    GroundTonalBadge(
                      text = "➤ $placeWayfindingBadge",
                      tone = GroundBadgeTone.TERTIARY,
                      monospace = true,
                    )
                  }
                  if (!isSidePanel) {
                    GroundFilterChip(
                      selected = isNavigatingPlace,
                      onClick = { state.toggleNavigationToPlace(place.id) },
                      label = {
                        Text(
                          text = if (isNavigatingPlace) "Stop Nav" else "Navigate",
                          style =
                            MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          softWrap = false,
                        )
                      },
                      leadingIcon = {
                        Icon(
                          imageVector = Icons.Outlined.Navigation,
                          contentDescription = "Straight-line navigate to place",
                          modifier = Modifier.size(13.dp),
                        )
                      },
                    )
                  }
                }
              }
            }
          }
        }
      }

      if (matchedPlaces.isEmpty() && matchedEntities.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxWidth().padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text =
              when {
                isAirplaneMode ->
                  "No local map layers match \"${state.listSearchQuery}\". Places search is not available offline."
                else -> "No map layers or places match \"${state.listSearchQuery}\"."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

/** Full Submission Details Card shown when a submission in a 1:N list or List view is clicked. */
@Composable
internal fun SubmissionFullDetailsCard(
  submission: SubmissionPreviewItem,
  state: PrototypeAppState,
  isDark: Boolean,
  textColor: Color,
  backLabel: String,
  onBack: () -> Unit,
  /**
   * Shares the submission's PDF on mobile; downloads it from the web side panel's overflow menu.
   */
  onSharePdf: () -> Unit,
  isSidePanel: Boolean = false,
  showBackButton: Boolean = true,
  showHeader: Boolean = true,
  showTitle: Boolean = true,
) {
  // The card uses the same container color as the answer blocks so the submission reads as one
  // document-like surface, set apart from the surrounding panel by a subtle outline.
  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      // Header: back arrow navigation, form title, and (side panel) overflow menu. Hosts that
      // already show the form title and actions (e.g. a tab or their own header) hide it.
      if (showHeader) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (showBackButton) {
            IconButton(onClick = { onBack() }, modifier = Modifier.size(28.dp)) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = backLabel,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
              )
            }
          }
          if (showTitle) {
            Text(
              text = submission.formTitle,
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
          } else {
            Spacer(modifier = Modifier.weight(1f))
          }
          if (isSidePanel) {
            DetailsOverflowMenu(
              actions = listOf(downloadPdfMenuAction(onSharePdf)),
              contentDescription = "More submission actions",
            )
          }
        }
      }

      // Actions & Sync Status Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (!isSidePanel) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            SyncStatusIndicatorBadge(
              syncStatus = submission.syncStatus,
              onClick = { state.cycleSubmissionSyncStatus(submission.id) },
            )

            AssistChip(
              onClick = { onSharePdf() },
              label = {
                Text(
                  text = "Share PDF",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  softWrap = false,
                )
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Outlined.Share,
                  contentDescription = "Share Submission PDF",
                  modifier = Modifier.size(12.dp),
                )
              },
            )
          }
        }

        Text(
          text = "Schema: ${submission.formVersion}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          softWrap = false,
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val detailTargetLabel =
          if (submission.hasAttachedEntity) {
            "${submission.targetTypeLabel}: ${submission.entityLabel}"
          } else if (submission.coordinatesLabel.isNotBlank()) {
            "${submission.targetTypeLabel} • No attached map feature (${submission.coordinatesLabel})"
          } else {
            "${submission.targetTypeLabel} • No attached map feature"
          }
        Text(
          text = detailTargetLabel,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.primary,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = "Data Collector: ${submission.collectorName} (${submission.collectorEmail})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = "Collected: ${submission.timestamp}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      submission.fields.forEach { field ->
        val fieldWayfindingBadge =
          state.formattedWayfindingBadgeForSubmissionField(submission.id, field)
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = field.questionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
              )
              if (!isSidePanel && fieldWayfindingBadge.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                GroundTonalBadge(
                  text = "➤ $fieldWayfindingBadge",
                  tone = GroundBadgeTone.WARNING,
                  monospace = true,
                )
              }
            }
            Text(
              text = field.answerValue,
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}

/**
 * Material Design 3 status badge indicating whether a map feature (`GeospatialEntityItem`) or form
 * submission (`SubmissionPreviewItem`) is [SyncStatus.UPLOADING], [SyncStatus.SYNCED], or
 * [SyncStatus.FAILED].
 */
@Composable
internal fun SyncStatusIndicatorBadge(
  syncStatus: SyncStatus,
  modifier: Modifier = Modifier,
  countSuffix: String? = null,
  isHighlighted: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val (icon, containerColor, contentColor, borderColor) =
    when (syncStatus) {
      SyncStatus.UPLOADING ->
        SyncStatusVisualSpec(
          icon = Icons.Outlined.CloudUpload,
          containerColor = MaterialTheme.colorScheme.tertiaryContainer,
          contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
          borderColor =
            MaterialTheme.colorScheme.tertiary.copy(alpha = if (isHighlighted) 0.9f else 0.35f),
        )
      SyncStatus.SYNCED ->
        SyncStatusVisualSpec(
          icon = Icons.Outlined.CloudDone,
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          borderColor =
            MaterialTheme.colorScheme.primary.copy(alpha = if (isHighlighted) 0.9f else 0.35f),
        )
      SyncStatus.FAILED ->
        SyncStatusVisualSpec(
          icon = Icons.Outlined.CloudOff,
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
          borderColor =
            MaterialTheme.colorScheme.error.copy(alpha = if (isHighlighted) 0.95f else 0.50f),
        )
    }

  val clickModifier =
    if (onClick != null) {
      Modifier.clickable(onClick = onClick)
    } else {
      Modifier
    }

  Surface(
    modifier = modifier.then(clickModifier),
    shape = RoundedCornerShape(6.dp),
    color = containerColor,
    contentColor = contentColor,
    border = BorderStroke(if (isHighlighted) 1.5.dp else 1.dp, borderColor),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = syncStatus.description,
        tint = contentColor,
        modifier = Modifier.size(12.dp),
      )
      Text(
        text =
          if (countSuffix != null) {
            "${syncStatus.label} $countSuffix"
          } else {
            syncStatus.label
          },
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = contentColor,
      )
    }
  }
}

private data class SyncStatusVisualSpec(
  val icon: ImageVector,
  val containerColor: Color,
  val contentColor: Color,
  val borderColor: Color,
)
