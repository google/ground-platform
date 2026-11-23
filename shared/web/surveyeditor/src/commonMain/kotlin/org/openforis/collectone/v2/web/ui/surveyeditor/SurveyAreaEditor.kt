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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.PlaceSource
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAreaGeometry
import org.openforis.collectone.v2.app.domain.model.editor.formatFixed
import org.openforis.collectone.v2.app.domain.model.parsePlaceCoordinates
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.coordinatesText
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.Tab
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.core.geo.io.GeoIssue
import org.openforis.collectone.v2.core.geo.io.GeoIssueSeverity
import org.openforis.collectone.v2.map.CameraPosition
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_area_choose_file
import org.openforis.collectone.v2.web.resources.surveyeditor_area_choose_shapefile
import org.openforis.collectone.v2.web.resources.surveyeditor_area_close_editor
import org.openforis.collectone.v2.web.resources.surveyeditor_area_custom_coordinates
import org.openforis.collectone.v2.web.resources.surveyeditor_area_delete_part
import org.openforis.collectone.v2.web.resources.surveyeditor_area_direct_coordinates_input
import org.openforis.collectone.v2.web.resources.surveyeditor_area_draw_boundary_hint
import org.openforis.collectone.v2.web.resources.surveyeditor_area_draw_instructions
import org.openforis.collectone.v2.web.resources.surveyeditor_area_drawing_corners
import org.openforis.collectone.v2.web.resources.surveyeditor_area_edit_in_draw_tab
import org.openforis.collectone.v2.web.resources.surveyeditor_area_ignored_features
import org.openforis.collectone.v2.web.resources.surveyeditor_area_issue_error
import org.openforis.collectone.v2.web.resources.surveyeditor_area_issue_warning
import org.openforis.collectone.v2.web.resources.surveyeditor_area_locations_found
import org.openforis.collectone.v2.web.resources.surveyeditor_area_more_issues
import org.openforis.collectone.v2.web.resources.surveyeditor_area_name
import org.openforis.collectone.v2.web.resources.surveyeditor_area_no_parts
import org.openforis.collectone.v2.web.resources.surveyeditor_area_no_places_matching
import org.openforis.collectone.v2.web.resources.surveyeditor_area_no_polygons
import org.openforis.collectone.v2.web.resources.surveyeditor_area_part_number
import org.openforis.collectone.v2.web.resources.surveyeditor_area_part_vertices_area
import org.openforis.collectone.v2.web.resources.surveyeditor_area_parts
import org.openforis.collectone.v2.web.resources.surveyeditor_area_parts_count
import org.openforis.collectone.v2.web.resources.surveyeditor_area_save
import org.openforis.collectone.v2.web.resources.surveyeditor_area_search_label
import org.openforis.collectone.v2.web.resources.surveyeditor_area_search_placeholder
import org.openforis.collectone.v2.web.resources.surveyeditor_area_searching_places
import org.openforis.collectone.v2.web.resources.surveyeditor_area_selected
import org.openforis.collectone.v2.web.resources.surveyeditor_area_source_online
import org.openforis.collectone.v2.web.resources.surveyeditor_area_title
import org.openforis.collectone.v2.web.resources.surveyeditor_area_upload_empty
import org.openforis.collectone.v2.web.resources.surveyeditor_area_upload_geojson_body
import org.openforis.collectone.v2.web.resources.surveyeditor_area_upload_geojson_title
import org.openforis.collectone.v2.web.resources.surveyeditor_area_upload_shapefile_body
import org.openforis.collectone.v2.web.resources.surveyeditor_area_upload_shapefile_title
import org.openforis.collectone.v2.web.resources.surveyeditor_area_vertices_count
import org.openforis.collectone.v2.web.resources.surveyeditor_cancel
import org.openforis.collectone.v2.web.resources.surveyeditor_clear_search
import org.openforis.collectone.v2.web.resources.surveyeditor_map_add_part
import org.openforis.collectone.v2.web.resources.surveyeditor_select
import org.openforis.collectone.v2.web.ui.common.TextFilePicker
import org.openforis.collectone.v2.web.ui.common.openPlatformTextFilePicker
import org.openforis.collectone.v2.web.ui.text.geoIssueText
import org.openforis.collectone.v2.web.ui.text.resolve

/** File types offered by the Upload tab's file picker. */
const val GEOMETRY_FILE_ACCEPT =
  ".geojson,.json,.kml,application/geo+json,application/vnd.google-earth.kml+xml"

/**
 * Full-size dialog for setting the survey area by place search, by drawing parts on a map, or by
 * uploading a GeoJSON or KML file. A Shapefile option is shown but not supported yet.
 *
 * @param pickTextFile opens a file picker; tests and non-browser platforms can replace it.
 */
@Composable
internal fun SurveyAreaEditorDialog(
  surveyId: String,
  surveyLocationLabel: String,
  current: SurveyArea?,
  localPlaces: List<SurveyPlaceItem>,
  searchPlaces: PlaceSearch,
  onSave: (SurveyArea) -> Unit,
  onDismiss: () -> Unit,
  pickTextFile: TextFilePicker = ::openPlatformTextFilePicker,
) {
  val editor = remember { SurveyAreaEditorState(current) }
  val activeArea = editor.drawnArea() ?: editor.uploadedArea() ?: current
  val surveyCenter = activeArea?.center ?: LatLng(-0.4198, 36.9512)
  val surveyZoom = activeArea?.zoom ?: 13.0
  val pending =
    when (editor.tab) {
      SurveyAreaEditorTab.SEARCH -> null
      SurveyAreaEditorTab.DRAW -> editor.drawnArea()
      SurveyAreaEditorTab.UPLOAD -> editor.uploadedArea()
    }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      modifier = Modifier.widthIn(max = 1080.dp).fillMaxWidth(0.94f).fillMaxHeight(0.9f),
    ) {
      Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Outlined.Map,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Spacer(Modifier.width(8.dp))
          Text(
            stringResource(Res.string.surveyeditor_area_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = onDismiss) {
            Icon(
              Icons.Outlined.Close,
              contentDescription = stringResource(Res.string.surveyeditor_area_close_editor),
            )
          }
        }
        PrimaryTabRow(
          selectedTabIndex = editor.tab.ordinal,
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
          SurveyAreaEditorTab.entries.forEach { t ->
            Tab(
              selected = editor.tab == t,
              onClick = { editor.tab = t },
              text = { Text(stringResource(t.label)) },
            )
          }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp)) {
          when (editor.tab) {
            SurveyAreaEditorTab.SEARCH ->
              SearchTab(
                surveyId = surveyId,
                surveyLocationLabel = surveyLocationLabel,
                surveyCenter = surveyCenter,
                currentAreaName = current?.name,
                localPlaces = localPlaces,
                searchPlaces = searchPlaces,
                onSelect = { place ->
                  onSave(placeToSurveyArea(place))
                  onDismiss()
                },
              )
            SurveyAreaEditorTab.DRAW -> DrawTab(editor, surveyCenter, surveyZoom)
            SurveyAreaEditorTab.UPLOAD -> UploadTab(editor, pickTextFile)
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (pending != null) {
            Text(
              areaSummaryText(pending),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
          }
          TextButton(onClick = onDismiss) { Text(stringResource(Res.string.surveyeditor_cancel)) }
          if (editor.tab != SurveyAreaEditorTab.SEARCH) {
            Button(
              onClick = {
                pending?.let(onSave)
                onDismiss()
              },
              enabled = pending != null,
            ) {
              Text(stringResource(Res.string.surveyeditor_area_save))
            }
          }
        }
      }
    }
  }
}

/** Summary of [area], e.g. "3 parts • 1,204 vertices • 850 ha". */
@Composable
internal fun areaSummaryText(area: SurveyArea): String {
  val parts = area.parts.size
  val vertices = area.vertexCount
  return listOf(
      pluralStringResource(Res.plurals.surveyeditor_area_parts_count, parts, parts),
      pluralStringResource(
        Res.plurals.surveyeditor_area_vertices_count,
        vertices,
        SurveyAreaGeometry.groupThousands(vertices.toLong()),
      ),
      surveyAreaText(SurveyAreaGeometry.areaSquareMeters(area.parts)).resolve(),
    )
    .joinToString(" • ")
}

// ---------------------------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------------------------

/**
 * Looks up places matching [query] near [center] for [surveyId]; [regionSubtitle] labels results
 * without a region of their own. Results are delivered to [onResults], which may be called after
 * this returns.
 */
fun interface PlaceSearch {
  fun search(
    surveyId: String,
    query: String,
    regionSubtitle: String,
    center: LatLng,
    onResults: (List<SurveyPlaceItem>) -> Unit,
  )
}

/** Remote places search (or raw coordinates) plus the local gazetteer, as before. */
@Composable
private fun SearchTab(
  surveyId: String,
  surveyLocationLabel: String,
  surveyCenter: LatLng,
  currentAreaName: String?,
  localPlaces: List<SurveyPlaceItem>,
  searchPlaces: PlaceSearch,
  onSelect: (SurveyPlaceItem) -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  var isSearching by remember { mutableStateOf(false) }
  var remotePlaces by remember { mutableStateOf<List<SurveyPlaceItem>>(emptyList()) }

  // Query live Mapbox Places API when user types a query (with debounce)
  LaunchedEffect(searchQuery, surveyId) {
    val q = searchQuery.trim()
    if (q.isBlank()) {
      remotePlaces = emptyList()
      isSearching = false
      return@LaunchedEffect
    }
    isSearching = true
    delay(250) // Debounce rapid keystrokes
    searchPlaces.search(surveyId, q, surveyLocationLabel, surveyCenter) { results ->
      remotePlaces = results
      isSearching = false
    }
  }

  val customCoordinatesCategory = stringResource(Res.string.surveyeditor_area_custom_coordinates)
  val customCoordinatesSubtitle =
    stringResource(Res.string.surveyeditor_area_direct_coordinates_input)
  val matchingPlaces =
    remember(
      searchQuery,
      remotePlaces,
      localPlaces,
      customCoordinatesCategory,
      customCoordinatesSubtitle,
    ) {
      val q = searchQuery.trim()
      val directCoords = parsePlaceCoordinates(q)
      val customPlace =
        if (directCoords != null) {
          val (lat, lng) = directCoords
          SurveyPlaceItem(
            id = "custom-coords",
            name = "GPS: ${formatFixed(lat, 4)}°, ${formatFixed(lng, 4)}°",
            categoryLabel = customCoordinatesCategory,
            regionSubtitle = customCoordinatesSubtitle,
            normalizedX = 0.5f,
            normalizedY = 0.5f,
            position = directCoords,
            source = null,
          )
        } else {
          null
        }

      if (q.isEmpty()) {
        emptyList()
      } else {
        // Remote results come first; the local gazetteer fills in only when there are none (it is
        // empty where a live geocoder is configured).
        val combined = LinkedHashMap<String, SurveyPlaceItem>()
        if (customPlace != null) {
          combined[customPlace.id] = customPlace
        }
        for (apiPlace in remotePlaces) {
          combined[apiPlace.id] = apiPlace
        }
        if (remotePlaces.isEmpty()) {
          val filteredLocal = localPlaces.filter { place -> place.matches(q) }
          for (localPlace in filteredLocal) {
            if (combined.values.none { it.name.equals(localPlace.name, ignoreCase = true) }) {
              combined[localPlace.id] = localPlace
            }
          }
        }
        combined.values.toList()
      }
    }

  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      label = { Text(stringResource(Res.string.surveyeditor_area_search_label)) },
      placeholder = { Text(stringResource(Res.string.surveyeditor_area_search_placeholder)) },
      leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
      trailingIcon = {
        if (isSearching) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { searchQuery = "" }) {
            Icon(
              Icons.Outlined.Close,
              contentDescription = stringResource(Res.string.surveyeditor_clear_search),
            )
          }
        }
      },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    if (searchQuery.isNotBlank()) {
      Text(
        text =
          pluralStringResource(
            Res.plurals.surveyeditor_area_locations_found,
            matchingPlaces.size,
            matchingPlaces.size,
          ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      modifier = Modifier.weight(1f).fillMaxWidth(),
    ) {
      if (matchingPlaces.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
          Text(
            when {
              isSearching -> stringResource(Res.string.surveyeditor_area_searching_places)
              searchQuery.isBlank() ->
                stringResource(Res.string.surveyeditor_area_search_placeholder)
              else ->
                stringResource(Res.string.surveyeditor_area_no_places_matching, searchQuery.trim())
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
          items(matchingPlaces, key = { it.id }) { place ->
            val isSelected =
              currentAreaName != null && place.name.equals(currentAreaName, ignoreCase = true)
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable(enabled = !isSelected) { onSelect(place) }
                  .padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint =
                  if (isSelected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
              )
              Column(modifier = Modifier.weight(1f)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = place.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                  )
                  val source = place.source
                  if (source != null) {
                    Text(
                      text = "• ${stringResource(source.labelRes)}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                }
                Text(
                  text = "${place.categoryLabel} • ${place.regionSubtitle}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = coordinatesText(place.position).resolve(),
                  style =
                    MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
              }
              if (isSelected) {
                TonalBadge(
                  text = stringResource(Res.string.surveyeditor_area_selected),
                  tone = BadgeTone.PRIMARY,
                )
              } else {
                OutlinedButton(onClick = { onSelect(place) }) {
                  Text(stringResource(Res.string.surveyeditor_select))
                }
              }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Draw
// ---------------------------------------------------------------------------------------------

@Composable
private fun DrawTab(editor: SurveyAreaEditorState, center: LatLng, zoom: Double) {
  val dataset = editor.partsDataset
  var selectedPart by remember { mutableStateOf<String?>(dataset.rows.firstOrNull()?.key) }
  var drawRequestToken by remember { mutableStateOf(0) }
  var isDrawing by remember { mutableStateOf(dataset.rows.isEmpty()) }
  var draftVertexCount by remember { mutableStateOf(0) }
  Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    InteractiveLayerMapCard(
      editor = editor.drawEditor,
      dataset = dataset,
      selectedRow = selectedPart,
      onSelectRow = { selectedPart = it },
      modifier = Modifier.weight(1f).fillMaxHeight(),
      featureNoun = MapFeatureNoun.PART,
      showLabels = false,
      emptyCamera = CameraPosition(center, zoom),
      startInDrawMode = dataset.rows.isEmpty(),
      showAddButtonInHeader = false,
      drawRequestToken = drawRequestToken,
      onDrawStateChange = { drawing, count ->
        isDrawing = drawing
        draftVertexCount = count
      },
    )
    Column(
      modifier = Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      OutlinedTextField(
        value = editor.drawName,
        onValueChange = { editor.drawName = it },
        label = { Text(stringResource(Res.string.surveyeditor_area_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      InfoLine(stringResource(Res.string.surveyeditor_area_draw_instructions))
      Text(
        stringResource(Res.string.surveyeditor_area_parts),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
      if (dataset.rows.isEmpty() && !isDrawing) {
        Text(
          stringResource(Res.string.surveyeditor_area_no_parts),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      dataset.rows.forEachIndexed { index, row ->
        val selected = row.key == selectedPart
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(MaterialTheme.shapes.small)
              .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.shapes.small,
              )
              .clickable { selectedPart = if (selected) null else row.key }
              .padding(start = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              stringResource(Res.string.surveyeditor_area_part_number, index + 1),
              style = MaterialTheme.typography.bodyMedium,
              color =
                if (selected) {
                  MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
              pluralStringResource(
                Res.plurals.surveyeditor_area_part_vertices_area,
                row.geometry.size,
                row.geometry.size,
                surveyAreaText(SurveyAreaGeometry.ringAreaSquareMeters(row.geometry)).resolve(),
              ),
              style = MaterialTheme.typography.bodySmall,
              color =
                if (selected) {
                  MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
          }
          IconButton(
            onClick = {
              if (selected) selectedPart = null
              editor.deleteDrawnPart(index)
            }
          ) {
            Icon(
              Icons.Outlined.Delete,
              contentDescription =
                stringResource(Res.string.surveyeditor_area_delete_part, index + 1),
              tint =
                if (selected) {
                  MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
          }
        }
      }
      if (isDrawing) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.shapes.small,
              )
              .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              stringResource(Res.string.surveyeditor_area_part_number, dataset.rows.size + 1),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
              if (draftVertexCount == 0) {
                stringResource(Res.string.surveyeditor_area_draw_boundary_hint)
              } else {
                pluralStringResource(
                  Res.plurals.surveyeditor_area_drawing_corners,
                  draftVertexCount,
                  draftVertexCount,
                )
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
            )
          }
        }
      }
      OutlinedButton(
        onClick = { drawRequestToken++ },
        enabled = !isDrawing,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(Res.string.surveyeditor_map_add_part))
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Upload
// ---------------------------------------------------------------------------------------------

@Composable
private fun UploadTab(editor: SurveyAreaEditorState, pickTextFile: TextFilePicker) {
  val upload = editor.upload
  val area = editor.uploadedArea()
  Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    Column(
      modifier = Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      UploadOption(
        icon = Icons.Outlined.UploadFile,
        title = stringResource(Res.string.surveyeditor_area_upload_geojson_title),
        body = stringResource(Res.string.surveyeditor_area_upload_geojson_body),
        action = stringResource(Res.string.surveyeditor_area_choose_file),
        onClick = { pickTextFile(GEOMETRY_FILE_ACCEPT, editor::onFilePicked) },
      )
      UploadOption(
        icon = Icons.Outlined.FolderZip,
        title = stringResource(Res.string.surveyeditor_area_upload_shapefile_title),
        body = stringResource(Res.string.surveyeditor_area_upload_shapefile_body),
        action = stringResource(Res.string.surveyeditor_area_choose_shapefile),
        onClick = editor::chooseShapefile,
      )
      editor.uploadMessage?.let { message -> InfoLine(message.resolve()) }
    }
    Column(
      modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (upload == null) {
        Text(
          stringResource(Res.string.surveyeditor_area_upload_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return@Column
      }
      Text(
        upload.fileName,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      if (area != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          SurveyAreaThumbnail(
            area = area,
            modifier = Modifier.size(width = 280.dp, height = 200.dp),
          )
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(area.name, style = MaterialTheme.typography.titleSmall)
            Text(areaSummaryText(area), style = MaterialTheme.typography.bodySmall)
            if (area.vertexCount <= MAX_DRAW_EDIT_VERTICES) {
              OutlinedButton(onClick = editor::editUploadInDrawTab) {
                Text(stringResource(Res.string.surveyeditor_area_edit_in_draw_tab))
              }
            }
          }
        }
      }
      val ignored = editor.ignoredUploadFeatures
      val issues = buildList {
        upload.result.errors.mapTo(this) { it.toIssueLine() }
        upload.result.warnings.mapTo(this) { it.toIssueLine() }
        if (ignored > 0) {
          add(
            IssueLine(
              GeoIssueSeverity.WARNING,
              Res.plurals.surveyeditor_area_ignored_features.toUiText(ignored, ignored),
            )
          )
        }
        if (area == null && upload.result.errors.isEmpty()) {
          add(
            IssueLine(GeoIssueSeverity.ERROR, Res.string.surveyeditor_area_no_polygons.toUiText())
          )
        }
      }
      IssueList(issues)
    }
  }
}

@Composable
private fun UploadOption(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  body: String,
  action: String,
  onClick: () -> Unit,
) {
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      }
      Text(
        body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      OutlinedButton(onClick = onClick) { Text(action) }
    }
  }
}

/** One line of an [IssueList]: an error or a warning, in the user's language. */
data class IssueLine(val severity: GeoIssueSeverity, val text: UiText)

/** This file problem as a line of an [IssueList]. */
fun GeoIssue.toIssueLine(): IssueLine = IssueLine(severity, geoIssueText(this))

/** Errors first, then warnings, capped so huge files don't flood the dialog. */
@Composable
fun IssueList(issues: List<IssueLine>, max: Int = 12) {
  if (issues.isEmpty()) return
  Column(
    modifier = Modifier.heightIn(max = 400.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    issues.take(max).forEach { issue ->
      val isError = issue.severity == GeoIssueSeverity.ERROR
      Row(verticalAlignment = Alignment.Top) {
        Icon(
          if (isError) Icons.Outlined.Error else Icons.Outlined.Warning,
          contentDescription =
            stringResource(
              if (isError) Res.string.surveyeditor_area_issue_error
              else Res.string.surveyeditor_area_issue_warning
            ),
          tint =
            if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(issue.text.resolve(), style = MaterialTheme.typography.bodySmall)
      }
    }
    if (issues.size > max) {
      Text(
        pluralStringResource(
          Res.plurals.surveyeditor_area_more_issues,
          issues.size - max,
          issues.size - max,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** The name of a place's source shown next to it in the boundary search results. */
private val PlaceSource.labelRes: StringResource
  get() =
    when (this) {
      PlaceSource.PLACES_API -> Res.string.surveyeditor_area_source_online
    }
