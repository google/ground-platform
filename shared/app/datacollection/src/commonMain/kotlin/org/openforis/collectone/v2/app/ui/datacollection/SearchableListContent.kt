/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.ui.datacollection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.EntityDatasetFeaturesGroup
import org.openforis.collectone.v2.app.domain.model.EntityStatusFilterOption
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_clear_search_title_case
import org.openforis.collectone.v2.app.resources.app_collapse_dataset
import org.openforis.collectone.v2.app.resources.app_device_offline
import org.openforis.collectone.v2.app.resources.app_expand_dataset
import org.openforis.collectone.v2.app.resources.app_features_lowercase
import org.openforis.collectone.v2.app.resources.app_fly_to_place
import org.openforis.collectone.v2.app.resources.app_map_layers_heading
import org.openforis.collectone.v2.app.resources.app_no_layers_or_places_match
import org.openforis.collectone.v2.app.resources.app_no_local_layers_match
import org.openforis.collectone.v2.app.resources.app_no_map_features_yet
import org.openforis.collectone.v2.app.resources.app_no_map_layers_yet
import org.openforis.collectone.v2.app.resources.app_offline_search_local_only
import org.openforis.collectone.v2.app.resources.app_offline_searching_local
import org.openforis.collectone.v2.app.resources.app_places_heading
import org.openforis.collectone.v2.app.resources.app_search
import org.openforis.collectone.v2.app.resources.app_search_placeholder
import org.openforis.collectone.v2.app.resources.app_showing_first_features
import org.openforis.collectone.v2.app.resources.app_stop_navigation
import org.openforis.collectone.v2.app.resources.domain_dataset_kind_data_table_plural
import org.openforis.collectone.v2.app.ui.common.CompactFilterChip
import org.openforis.collectone.v2.app.ui.common.horizontalScrollWithMouseDrag
import org.openforis.collectone.v2.app.ui.map.LayerVisibilityToggle
import org.openforis.collectone.v2.app.ui.map.MapFeatureNoun
import org.openforis.collectone.v2.app.ui.map.layerNameColor
import org.openforis.collectone.v2.app.ui.map.pluralText
import org.openforis.collectone.v2.app.ui.text.categoryText
import org.openforis.collectone.v2.app.ui.text.coordinatesText
import org.openforis.collectone.v2.app.ui.text.nameText
import org.openforis.collectone.v2.app.ui.text.regionText
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalIconButton
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.clickable

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

  @Suppress("DEPRECATION")
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
          text = stringResource(Res.string.app_search_placeholder),
          style =
            if (compact) MaterialTheme.typography.bodyMedium
            else MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      leadingIcon = {
        Icon(
          imageVector = Icons.Outlined.Search,
          contentDescription = stringResource(Res.string.app_search),
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
                contentDescription = stringResource(Res.string.app_clear_search_title_case),
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
 * - On mobile, peeks at the bottom of the map with the active survey title and area name in the
 *   bottom card heading alongside an Expand/Collapse sheet button.
 * - Expands to display **Map layers** (grouped by spatial layer with un-nested entity records,
 *   reusing [EntitySummaryHeader] and [EntityMetadataAndActionsRow]), **Data tables** (tabular
 *   datasets), and **Places** (geographic places, landmarks, and coordinates in the survey region).
 *
 * When [isSidePanel] is `true` (the web dashboard's left-hand panel), the header displays the
 * compact search field ([ListSearchField]), the Expand/Collapse sheet button is hidden, and the
 * list uses a denser, mouse-oriented layout: dataset header rows and map feature rows share one
 * compact [SidePanelListRowHeight], each dataset header has a chevron left of its name that
 * collapses or expands its map features ([SearchableListActions.toggleListDatasetCollapsed]; all
 * datasets show expanded while searching), and the layer visibility toggle appears only while the
 * header row is hovered or focused, or while the layer is hidden.
 */
@Composable
fun BottomSheetSearchableListContent(
  uiState: SearchableListUiState,
  actions: SearchableListActions,
  modifier: Modifier = Modifier,
  isSidePanel: Boolean = false,
) {
  val matchedPlaces = uiState.places
  val groupedEntities = uiState.entityGroups
  val mapLayerGroups = groupedEntities.filter { it.isMapLayer }
  val dataTableGroups = groupedEntities.filterNot { it.isMapLayer }

  Column(modifier = modifier) {
    SearchableListHeader(uiState = uiState, actions = actions, isSidePanel = isSidePanel)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // Scrollable Searchable List of Map features (grouped by dataset), Data tables, and Places.
    // The web side panel uses a denser layout (mouse pointer) than the touch-first mobile sheet.
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
      if (mapLayerGroups.isNotEmpty()) {
        DatasetGroupsSection(
          icon = Icons.Outlined.Layers,
          heading = stringResource(Res.string.app_map_layers_heading),
          groups = mapLayerGroups,
          uiState = uiState,
          actions = actions,
          isSidePanel = isSidePanel,
        )
      }

      if (dataTableGroups.isNotEmpty()) {
        if (mapLayerGroups.isNotEmpty()) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        DatasetGroupsSection(
          icon = Icons.Outlined.TableRows,
          heading = stringResource(Res.string.domain_dataset_kind_data_table_plural).uppercase(),
          groups = dataTableGroups,
          uiState = uiState,
          actions = actions,
          isSidePanel = isSidePanel,
        )
      }

      // Places are only available online.
      val visiblePlaces = if (uiState.isOnline) matchedPlaces else emptyList()
      if (visiblePlaces.isNotEmpty()) {
        if (groupedEntities.isNotEmpty()) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        PlacesSection(places = visiblePlaces, actions = actions, isSidePanel = isSidePanel)
      }

      if (visiblePlaces.isEmpty() && groupedEntities.all { it.entities.isEmpty() }) {
        EmptySearchableListMessage(
          query =
            uiState.query.ifBlank { uiState.activeStatusFilters.values.firstOrNull().orEmpty() },
          isOnline = uiState.isOnline,
          hasMapLayers = uiState.hasMapLayers,
        )
      }
    }
  }
}

/**
 * Sticky top of the searchable list: the compact search field in the web side panel, or the
 * survey's title and area on mobile, followed by the mobile offline banner while the sheet is
 * expanded.
 */
@Composable
private fun SearchableListHeader(
  uiState: SearchableListUiState,
  actions: SearchableListActions,
  isSidePanel: Boolean,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (isSidePanel) {
      ListSearchField(
        query = uiState.query,
        onQueryChange = {
          actions.updateListSearchQuery(it)
          if (it.isNotEmpty() && !uiState.isSheetExpanded) {
            actions.updateEntityBottomSheetExpanded(true)
          }
        },
        onClear = { actions.clearListSearchQuery() },
        compact = true,
        modifier = Modifier.fillMaxWidth(),
      )
    } else {
      SurveyPeekHeading(title = uiState.surveyTitle, location = uiState.surveyLocation)
    }

    if (!uiState.isOnline && !isSidePanel && uiState.isSheetExpanded) {
      OfflineSearchBanner(entitiesCountNoun = uiState.entitiesCountNoun)
    }
  }
}

/** The mobile sheet's peek heading: the survey [title] and, when known, its area [location]. */
@Composable
private fun SurveyPeekHeading(title: String, location: String) {
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (location.isNotBlank()) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.LocationOn,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(13.dp),
        )
        Text(
          text = location,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/**
 * Mobile offline banner explaining that search only covers local map features (named by
 * [entitiesCountNoun]) and that Places search is not available offline.
 */
@Composable
private fun OfflineSearchBanner(entitiesCountNoun: MapFeatureNoun) {
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
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.CloudOff,
        contentDescription = stringResource(Res.string.app_device_offline),
        tint = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.size(16.dp),
      )
      val countNoun = entitiesCountNoun.pluralText.resolve()
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(Res.string.app_offline_searching_local, countNoun),
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
          text = stringResource(Res.string.app_offline_search_local_only, countNoun),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
        )
      }
    }
  }
}

/** A dataset section (`MAP LAYERS` or `DATA TABLES`): one [EntityDatasetGroup] per dataset. */
@Composable
private fun DatasetGroupsSection(
  icon: ImageVector,
  heading: String,
  groups: List<EntityDatasetFeaturesGroup>,
  uiState: SearchableListUiState,
  actions: SearchableListActions,
  isSidePanel: Boolean,
) {
  ListSectionHeading(icon = icon, text = heading)
  groups.forEach { group ->
    val datasetId = group.datasetId
    key(datasetId) {
      EntityDatasetGroup(
        group = group,
        // Selecting and collapsing a dataset is only offered in the web side panel.
        isLayerSelected = isSidePanel && uiState.selectedLayerDatasetId == datasetId,
        isCollapsed = isSidePanel && datasetId in uiState.collapsedDatasetIds,
        // While a search query or a status filter on this dataset is active, it stays expanded
        // (see SearchableListUiState.collapsedDatasetIds) and its chevron is disabled.
        canCollapse = uiState.query.isBlank() && group.selectedStatusFilter == null,
        selectedEntityId = uiState.selectedEntityId,
        actions = actions,
        isSidePanel = isSidePanel,
      )
    }
  }
}

/**
 * Row of compact status filter chips shown beneath a map layer or data table header when that
 * dataset uses workflow status, allowing the user to filter its entities by status on both mobile
 * and web.
 */
@Composable
private fun EntityStatusFilterRow(
  options: List<EntityStatusFilterOption>,
  selectedStatus: String?,
  onToggleStatus: (String) -> Unit,
  isSidePanel: Boolean,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .padding(
          start = if (isSidePanel) 6.dp else 4.dp,
          top = if (isSidePanel) 2.dp else 0.dp,
          bottom = if (isSidePanel) 4.dp else 0.dp,
        )
        .horizontalScrollWithMouseDrag(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    options.forEach { option ->
      val isSelected =
        selectedStatus != null &&
          (selectedStatus.equals(option.status, ignoreCase = true) ||
            selectedStatus.equals(option.badgeText, ignoreCase = true))
      CompactFilterChip(
        selected = isSelected,
        onClick = { onToggleStatus(option.status) },
        label = option.badgeText,
      )
    }
  }
}

/** The `PLACES` section: one card per matching place. */
@Composable
private fun PlacesSection(
  places: List<PlaceListItem>,
  actions: SearchableListActions,
  isSidePanel: Boolean,
) {
  ListSectionHeading(
    icon = Icons.Outlined.Explore,
    text = stringResource(Res.string.app_places_heading),
  )
  Column(verticalArrangement = Arrangement.spacedBy(if (isSidePanel) 6.dp else 8.dp)) {
    places.forEach { item ->
      PlaceCard(
        item = item,
        onSelect = { actions.selectPlace(item.place.id) },
        onToggleNavigation = { actions.toggleNavigationToPlace(item.place.id) },
        isSidePanel = isSidePanel,
      )
    }
  }
}

/** Heading of a section of the searchable list (`MAP LAYERS`, `PLACES`). */
@Composable
private fun ListSectionHeading(icon: ImageVector, text: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(15.dp),
    )
    Text(
      text = text,
      style =
        MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          letterSpacing = 0.6.sp,
        ),
    )
  }
}

/** Most map features listed per dataset; larger datasets show a notice instead of the rest. */
private const val MAX_LISTED_FEATURES_PER_DATASET = 200

/**
 * One entity dataset of the list: its header row ([EntityDatasetHeaderRow]) and, unless
 * [isCollapsed], one single-line row per map feature (at most [MAX_LISTED_FEATURES_PER_DATASET]).
 */
@Composable
private fun EntityDatasetGroup(
  group: EntityDatasetFeaturesGroup,
  isLayerSelected: Boolean,
  isCollapsed: Boolean,
  canCollapse: Boolean,
  selectedEntityId: String?,
  actions: SearchableListActions,
  isSidePanel: Boolean,
) {
  val layer = group.layer
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(if (isSidePanel) 0.dp else 6.dp),
  ) {
    EntityDatasetHeaderRow(
      group = group,
      isLayerSelected = isLayerSelected,
      isCollapsed = isCollapsed,
      canCollapse = canCollapse,
      actions = actions,
      isSidePanel = isSidePanel,
    )

    if (!isCollapsed) {
      if (group.statusFilterOptions.isNotEmpty()) {
        EntityStatusFilterRow(
          options = group.statusFilterOptions,
          selectedStatus = group.selectedStatusFilter,
          onToggleStatus = { status -> actions.toggleListStatusFilter(group.datasetId, status) },
          isSidePanel = isSidePanel,
        )
      }
      // One single-line row per entity record: layer marker, label, and status chip.
      Column(modifier = Modifier.fillMaxWidth()) {
        group.entities.take(MAX_LISTED_FEATURES_PER_DATASET).forEach { entity ->
          EntityListRow(
            entity = entity,
            isSelected = entity.id == selectedEntityId,
            onClick = { actions.selectEntityFromList(entity.id) },
            compact = isSidePanel,
            compactHeight = SidePanelListRowHeight,
            compactStartIndent = SidePanelEntityRowIndent,
            layer = layer,
          )
        }
      }
      if (group.entities.size > MAX_LISTED_FEATURES_PER_DATASET) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text =
              stringResource(
                Res.string.app_showing_first_features,
                MAX_LISTED_FEATURES_PER_DATASET,
                group.entities.size,
                layer?.pluralNoun?.takeIf { it.isNotBlank() }
                  ?: stringResource(Res.string.app_features_lowercase),
                group.datasetName,
              ),
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

/**
 * Header row of an entity dataset: its layer swatch (or a pin without a layer), name, and layer
 * visibility toggle (or its feature count without a layer). In the web side panel clicking it
 * selects the layer, the chevron left of the name collapses or expands its map features (enabled
 * when [canCollapse]), and the visibility toggle appears only while the row is hovered or focused,
 * or while the layer is hidden.
 */
@Composable
private fun EntityDatasetHeaderRow(
  group: EntityDatasetFeaturesGroup,
  isLayerSelected: Boolean,
  isCollapsed: Boolean,
  canCollapse: Boolean,
  actions: SearchableListActions,
  isSidePanel: Boolean,
) {
  val layer = group.layer
  val datasetId = group.datasetId
  val headerInteractionSource = remember { MutableInteractionSource() }
  val isHeaderHovered by headerInteractionSource.collectIsHoveredAsState()
  var isHeaderFocused by remember { mutableStateOf(false) }
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
          .clickable { actions.selectLayer(datasetId) }
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
        DatasetCollapseChevron(
          datasetLabel = datasetLabel,
          isCollapsed = isCollapsed,
          enabled = canCollapse,
          isLayerSelected = isLayerSelected,
          onClick = { actions.toggleListDatasetCollapsed(datasetId) },
        )
      }
      DatasetSwatch(
        layer = layer,
        isMapLayer = group.isMapLayer,
        isLayerSelected = isLayerSelected,
      )
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
      LayerVisibilityControl(
        isVisible = layer.isVisible,
        // Web: the eye toggle appears while the header row is hovered or focused, and stays
        // visible while the layer is hidden as a state indicator.
        isRevealed = !isSidePanel || isHeaderHovered || isHeaderFocused,
        onToggle = { actions.toggleLayerVisibility(layer.id) },
        isSidePanel = isSidePanel,
      )
    } else {
      TonalBadge(text = "${group.entities.size}", tone = BadgeTone.PRIMARY)
    }
  }
}

/**
 * Web chevron left of a dataset's name that collapses or expands its map features, disabled (and
 * dimmed) unless [enabled].
 */
@Composable
private fun DatasetCollapseChevron(
  datasetLabel: String,
  isCollapsed: Boolean,
  enabled: Boolean,
  isLayerSelected: Boolean,
  onClick: () -> Unit,
) {
  IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(24.dp)) {
    Icon(
      imageVector =
        if (isCollapsed) {
          Icons.AutoMirrored.Outlined.KeyboardArrowRight
        } else {
          Icons.Outlined.KeyboardArrowDown
        },
      contentDescription =
        stringResource(
          if (isCollapsed) Res.string.app_expand_dataset else Res.string.app_collapse_dataset,
          datasetLabel,
        ),
      tint =
        (if (isLayerSelected) {
            MaterialTheme.colorScheme.onSecondaryContainer
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          })
          .copy(alpha = if (enabled) 1f else 0.38f),
      modifier = Modifier.size(18.dp),
    )
  }
}

/** A dataset's [layer] color swatch, or a pin / table icon when the dataset has no map layer. */
@Composable
private fun DatasetSwatch(layer: MapLayerItem?, isMapLayer: Boolean, isLayerSelected: Boolean) {
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
      imageVector = if (isMapLayer) Icons.Outlined.LocationOn else Icons.Outlined.TableRows,
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
}

/**
 * A dataset header's layer visibility toggle. In the web side panel it shows only while
 * [isRevealed] or while the layer is hidden (as a state indicator), and its space is always
 * reserved so the row does not jitter on hover.
 */
@Composable
private fun LayerVisibilityControl(
  isVisible: Boolean,
  isRevealed: Boolean,
  onToggle: () -> Unit,
  isSidePanel: Boolean,
) {
  if (!isSidePanel) {
    LayerVisibilityToggle(isVisible = isVisible, onToggle = onToggle)
    return
  }
  Box(
    modifier = Modifier.size(SidePanelListRowHeight - 4.dp),
    contentAlignment = Alignment.Center,
  ) {
    if (isRevealed || !isVisible) {
      LayerVisibilityToggle(
        isVisible = isVisible,
        onToggle = onToggle,
        buttonSize = SidePanelListRowHeight - 4.dp,
        iconSize = 18.dp,
      )
    }
  }
}

/**
 * Card of a place in the list; clicking it flies the map to the place ([onSelect]).
 * - On mobile: its name, its category and region, and its coordinates on three lines, then one
 *   `Navigate` button with the distance and heading to the place ([NavigateAction],
 *   [onToggleNavigation]).
 * - In the web side panel ([isSidePanel]): its name, category and region, a button that also flies
 *   the map to it, and its category and coordinates badges.
 */
@Composable
private fun PlaceCard(
  item: PlaceListItem,
  onSelect: () -> Unit,
  onToggleNavigation: () -> Unit,
  isSidePanel: Boolean,
) {
  val place = item.place
  val isSelectedPlace = item.isSelected
  OutlinedCard(
    onClick = onSelect,
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
      PlaceTitleRow(
        place = place,
        isSelected = isSelectedPlace,
        onFlyTo = onSelect,
        isSidePanel = isSidePanel,
      )
      if (isSidePanel) {
        PlaceBadgesRow(place = place)
      } else {
        NavigateAction(
          isNavigating = item.isNavigating,
          wayfinding = item.wayfinding,
          stopLabel = stringResource(Res.string.app_stop_navigation),
          onToggle = onToggleNavigation,
        )
      }
    }
  }
}

/**
 * A place card's icon, name, and category and region. On mobile its coordinates follow on a third
 * line; in the web side panel ([isSidePanel]) a button flies the map to it ([onFlyTo]).
 */
@Composable
private fun PlaceTitleRow(
  place: SurveyPlaceItem,
  isSelected: Boolean,
  onFlyTo: () -> Unit,
  isSidePanel: Boolean,
) {
  val isSelectedPlace = isSelected
  val supportingColor =
    if (isSelectedPlace) {
      MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    }
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
          text = place.nameText().resolve(),
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
          text = "${place.categoryText().resolve()} • ${place.regionText().resolve()}",
          style = MaterialTheme.typography.labelSmall,
          color = supportingColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!isSidePanel) {
          Text(
            text = coordinatesText(place.position).resolve(),
            style = MaterialTheme.typography.labelSmall,
            color = supportingColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    if (isSidePanel) {
      FilledTonalIconButton(onClick = onFlyTo, modifier = Modifier.size(30.dp)) {
        Icon(
          imageVector = Icons.Outlined.Explore,
          contentDescription = stringResource(Res.string.app_fly_to_place),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

/** A place card's category and coordinates badges in the web side panel. */
@Composable
private fun PlaceBadgesRow(place: SurveyPlaceItem) {
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScrollWithMouseDrag(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TonalBadge(text = place.categoryText().resolve(), tone = BadgeTone.SECONDARY)
    TonalBadge(
      text = coordinatesText(place.position).resolve(),
      tone = BadgeTone.SECONDARY,
      monospace = true,
    )
  }
}

/**
 * Shown when the searchable list has no visible items:
 * - When [query] is blank (no active filter), shows that the survey has no map features yet (when
 *   [hasMapLayers] is `true`) or no map layers yet.
 * - When a non-blank [query] filters out all items, shows the filtered empty state (online: map
 *   layers and places; offline: local map layers only).
 */
@Composable
private fun EmptySearchableListMessage(query: String, isOnline: Boolean, hasMapLayers: Boolean) {
  val trimmedQuery = query.trim()
  Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
    Text(
      text =
        when {
          trimmedQuery.isEmpty() && hasMapLayers ->
            stringResource(Res.string.app_no_map_features_yet)
          trimmedQuery.isEmpty() -> stringResource(Res.string.app_no_map_layers_yet)
          isOnline -> stringResource(Res.string.app_no_layers_or_places_match, trimmedQuery)
          else -> stringResource(Res.string.app_no_local_layers_match, trimmedQuery)
        },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
