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
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.core.geo.io.GeoFileReader
import org.groundplatform.v2.core.geo.io.GeoGeometry
import org.groundplatform.v2.core.geo.io.GeoIssue
import org.groundplatform.v2.core.geo.io.GeoIssueSeverity
import org.groundplatform.v2.core.geo.io.GeoReadResult
import org.groundplatform.v2.devtools.prototypeapp.TextFilePickResult
import org.groundplatform.v2.devtools.prototypeapp.TextFilePicker
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityProperty
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityRow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LayerStyle
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyArea
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyAreaGeometry
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.formatFixed
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.toLatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.parsePlaceCoordinates
import org.groundplatform.v2.devtools.prototypeapp.openPlatformTextFilePicker
import org.groundplatform.v2.map.CameraPosition

/** File types offered by the Upload tab's file picker. */
internal const val GEOMETRY_FILE_ACCEPT =
  ".geojson,.json,.kml,application/geo+json,application/vnd.google-earth.kml+xml"

/** Above this many vertices, uploaded or searched areas aren't loaded into the Draw tab. */
private const val MAX_DRAW_EDIT_VERTICES = 2_000

/** Tabs of the survey area editor. */
enum class SurveyAreaEditorTab(val label: String) {
  SEARCH("Search"),
  DRAW("Draw"),
  UPLOAD("Upload"),
}

/** A file read in the Upload tab. */
data class UploadedGeometry(val fileName: String, val result: GeoReadResult)

/**
 * State of the survey area editor (everything except the place search, which lives in the
 * composable). Plain and platform-independent so the Draw and Upload flows can be unit tested.
 *
 * The Draw tab edits parts as polygon features of a scratch [SurveyEditorState], so it reuses the
 * Map layer editor's drawing and vertex-editing interaction ([InteractiveLayerMapCard]).
 */
class SurveyAreaEditorState(val initial: SurveyArea?) {
  var tab by mutableStateOf(SurveyAreaEditorTab.SEARCH)

  // Draw ---------------------------------------------------------------------------------------

  /** Scratch editor holding one polygon feature per drawn part. */
  internal val drawEditor: SurveyEditorState =
    SurveyEditorState(
      SurveyEditorDraft.blank(surveyId = "survey_area")
        .copy(datasets = listOf(partsDataset(initial)), nextKeyId = 1_000)
    )

  internal val partsDatasetKey: String = PARTS_DATASET_KEY

  internal val partsDataset: EntityDataset
    get() = drawEditor.datasets.first { it.key == PARTS_DATASET_KEY }

  var drawName by mutableStateOf(initial?.name ?: "Drawn area")

  /** The drawn parts with at least three vertices. */
  val drawnParts: List<List<LatLng>>
    get() = partsDataset.rows.map { it.geometry }.filter { it.size >= 3 }

  /** Adds [part] as a new drawn part (as drawing on the map does). Returns its row key. */
  fun addDrawnPart(part: List<LatLng>): String =
    drawEditor.addRow(PARTS_DATASET_KEY, geometry = part)

  /** Deletes the drawn part at [index] (in [partsDataset] row order). */
  fun deleteDrawnPart(index: Int) {
    val row = partsDataset.rows.getOrNull(index) ?: return
    drawEditor.removeRow(PARTS_DATASET_KEY, row.key)
  }

  /** The drawn parts as a survey area, or `null` if nothing usable has been drawn. */
  fun drawnArea(): SurveyArea? {
    val parts = drawnParts
    if (parts.isEmpty()) return null
    return SurveyArea(
      name = drawName.trim().ifEmpty { "Drawn area" },
      parts = parts,
      sourceLabel = SOURCE_DRAWN,
    )
  }

  // Upload -------------------------------------------------------------------------------------

  var upload: UploadedGeometry? by mutableStateOf(null)
    private set

  /** A message about the last upload attempt that isn't tied to a parsed file. */
  var uploadMessage: String? by mutableStateOf(null)
    private set

  /** Reads [text] as GeoJSON or KML (chosen by [fileName] or content). */
  fun loadFile(fileName: String, text: String) {
    upload = UploadedGeometry(fileName, GeoFileReader.read(fileName, text))
    uploadMessage = null
  }

  /** Handles the result of the platform file picker. */
  fun onFilePicked(result: TextFilePickResult) {
    when (result) {
      is TextFilePickResult.Picked -> loadFile(result.fileName, result.text)
      is TextFilePickResult.Failed -> uploadMessage = result.message
      TextFilePickResult.Cancelled -> Unit
    }
  }

  /** The Shapefile option is shown but not supported yet; it only explains that. */
  fun chooseShapefile() {
    uploadMessage = SHAPEFILE_COMING_SOON
  }

  /** Features in the upload that aren't polygons, which a survey area ignores. */
  val ignoredUploadFeatures: Int
    get() = upload?.result?.features?.count { it.geometry !is GeoGeometry.Polygon } ?: 0

  /** The uploaded polygons as a survey area, or `null` if the file has none. */
  fun uploadedArea(): SurveyArea? {
    val file = upload ?: return null
    val polygons = file.result.polygons
    if (polygons.isEmpty()) return null
    val names = polygons.mapNotNull { it.properties["name"]?.takeIf(String::isNotBlank) }.distinct()
    return SurveyArea(
      name = names.singleOrNull() ?: file.fileName.substringBeforeLast('.'),
      parts = file.result.polygonRings.map { ring -> ring.map { it.toLatLng() } },
      sourceLabel = "$SOURCE_UPLOADED_PREFIX${file.fileName}",
    )
  }

  /** Copies the uploaded polygons into the Draw tab so they can be edited by hand. */
  fun editUploadInDrawTab() {
    val area = uploadedArea() ?: return
    replaceDrawnParts(area.parts)
    drawName = area.name
    tab = SurveyAreaEditorTab.DRAW
  }

  private fun replaceDrawnParts(parts: List<List<LatLng>>) {
    partsDataset.rows.forEach { drawEditor.removeRow(PARTS_DATASET_KEY, it.key) }
    parts.forEach { addDrawnPart(it) }
  }

  companion object {
    const val SOURCE_DRAWN = "Drawn on map"
    const val SOURCE_UPLOADED_PREFIX = "Uploaded: "
    const val SHAPEFILE_COMING_SOON =
      "Shapefile import coming soon. For now, convert the Shapefile to GeoJSON or KML (for " +
        "example in QGIS) and upload that."
    private const val PARTS_DATASET_KEY = "survey-area-parts"

    private fun partsDataset(initial: SurveyArea?): EntityDataset {
      val editable = initial != null && initial.vertexCount <= MAX_DRAW_EDIT_VERTICES
      return EntityDataset(
        key = PARTS_DATASET_KEY,
        kind = DatasetKind.MAP_LAYER,
        id = "part",
        displayName = "Survey area parts",
        geometryKind = GeometryKind.POLYGON,
        keyProperty = "id",
        labelProperty = "id",
        properties = listOf(EntityProperty("id", "ID", required = true)),
        rows =
          if (!editable) emptyList()
          else
            initial.parts.mapIndexed { i, part ->
              EntityRow("part-${i + 1}", mapOf("id" to "PAR-${i + 1}"), part)
            },
        style = LayerStyle(fillOpacity = 0.25),
      )
    }
  }
}

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
  val surveyCenter = current?.center ?: LatLng(-0.4198, 36.9512)
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
            "Survey area",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = onDismiss) {
            Icon(Icons.Outlined.Close, contentDescription = "Close survey area editor")
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
              text = { Text(t.label) },
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
            SurveyAreaEditorTab.DRAW -> DrawTab(editor, surveyCenter, current?.zoom ?: 13.0)
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
              areaSummary(pending),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
          }
          TextButton(onClick = onDismiss) { Text("Cancel") }
          if (editor.tab != SurveyAreaEditorTab.SEARCH) {
            Button(
              onClick = {
                pending?.let(onSave)
                onDismiss()
              },
              enabled = pending != null,
            ) {
              Text("Save survey area")
            }
          }
        }
      }
    }
  }
}

/** "3 parts • 1,204 vertices • 850 ha". */
internal fun areaSummary(area: SurveyArea): String {
  val parts = area.parts.size
  return "$parts ${if (parts == 1) "part" else "parts"} • " +
    "${SurveyAreaGeometry.groupThousands(area.vertexCount.toLong())} vertices • " +
    SurveyAreaGeometry.formatArea(SurveyAreaGeometry.areaSquareMeters(area.parts))
}

// ---------------------------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------------------------

/**
 * Looks up places matching [query] near [center] for [surveyId]; [regionSubtitle] labels results
 * without a region of their own. Results are delivered to [onResults], which may be called after
 * this returns.
 */
internal fun interface PlaceSearch {
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

  val matchingPlaces =
    remember(searchQuery, remotePlaces, localPlaces) {
      val q = searchQuery.trim()
      val directCoords = parsePlaceCoordinates(q)
      val customPlace =
        if (directCoords != null) {
          val (lat, lng) = directCoords
          SurveyPlaceItem(
            id = "custom-coords",
            name = "GPS: ${formatFixed(lat, 4)}°, ${formatFixed(lng, 4)}°",
            categoryLabel = "Custom Coordinates",
            regionSubtitle = "Direct coordinates input",
            coordinatesLabel = "${formatFixed(lat, 4)}, ${formatFixed(lng, 4)}",
            normalizedX = 0.5f,
            normalizedY = 0.5f,
            latitude = lat,
            longitude = lng,
            sourceLabel = "Coordinates",
          )
        } else {
          null
        }

      if (q.isEmpty()) {
        localPlaces
      } else {
        // Prioritize remote Mapbox Places API results, merged with matching local gazetteer entries
        val combined = LinkedHashMap<String, SurveyPlaceItem>()
        if (customPlace != null) {
          combined[customPlace.id] = customPlace
        }
        for (apiPlace in remotePlaces) {
          combined[apiPlace.id] = apiPlace
        }
        val filteredLocal = localPlaces.filter { place ->
          place.name.contains(q, ignoreCase = true) ||
            place.categoryLabel.contains(q, ignoreCase = true) ||
            place.regionSubtitle.contains(q, ignoreCase = true) ||
            place.coordinatesLabel.contains(q, ignoreCase = true)
        }
        for (localPlace in filteredLocal) {
          if (combined.values.none { it.name.equals(localPlace.name, ignoreCase = true) }) {
            combined[localPlace.id] = localPlace
          }
        }
        combined.values.toList()
      }
    }

  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      label = { Text("Search places or enter coordinates") },
      placeholder = { Text("e.g. Nairobi, Othaya, Chinga Dam, or -0.419, 36.950") },
      leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
      trailingIcon = {
        if (isSearching) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { searchQuery = "" }) {
            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
          }
        }
      },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Text(
      text =
        if (searchQuery.isBlank()) "Suggested survey locations:"
        else "${matchingPlaces.size} locations found:",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      modifier = Modifier.weight(1f).fillMaxWidth(),
    ) {
      if (matchingPlaces.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
          Text(
            if (isSearching) "Searching places..." else "No places matching “$searchQuery”",
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
                  val cleanSource =
                    if (place.sourceLabel.equals("Places API", ignoreCase = true)) "Online"
                    else place.sourceLabel
                  if (cleanSource.isNotBlank()) {
                    Text(
                      text = "• $cleanSource",
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
                  text = place.coordinatesLabel,
                  style =
                    MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
              }
              if (isSelected) {
                GroundTonalBadge(text = "Selected", tone = GroundBadgeTone.PRIMARY)
              } else {
                OutlinedButton(onClick = { onSelect(place) }) { Text("Select") }
              }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          }
        }
      }
    }
  }
}

/** Converts a [SurveyPlaceItem] to a single-part [SurveyArea] from its bounding box. */
internal fun placeToSurveyArea(place: SurveyPlaceItem): SurveyArea {
  val lat = place.latitude
  val lng = place.longitude
  val boundary =
    if (
      place.bboxMinLat != null &&
        place.bboxMinLng != null &&
        place.bboxMaxLat != null &&
        place.bboxMaxLng != null
    ) {
      val minLng = place.bboxMinLng
      val maxLng = place.bboxMaxLng
      val minLat = place.bboxMinLat
      val maxLat = place.bboxMaxLat
      // If a country bounding box spans global bounds [-180, 180], restrict the boundary
      // polygon to reasonable geographic extent centered around the place center
      // so it does not wrap the whole globe into an invalid full-world box.
      if (maxLng - minLng >= 350.0) {
        val spanLat = (maxLat - minLat).coerceAtLeast(10.0)
        val halfLng = (spanLat * 1.4).coerceAtMost(35.0)
        listOf(
          LatLng(maxLat, lng - halfLng),
          LatLng(maxLat, lng + halfLng),
          LatLng(minLat, lng + halfLng),
          LatLng(minLat, lng - halfLng),
        )
      } else {
        listOf(
          LatLng(maxLat, minLng),
          LatLng(maxLat, maxLng),
          LatLng(minLat, maxLng),
          LatLng(minLat, minLng),
        )
      }
    } else {
      val delta = 0.015
      listOf(
        LatLng(lat + delta, lng - delta),
        LatLng(lat + delta, lng + delta),
        LatLng(lat - delta, lng + delta),
        LatLng(lat - delta, lng - delta),
      )
    }
  return SurveyArea(
    name = place.name,
    parts = listOf(boundary),
    center = LatLng(lat, lng),
    zoom = place.targetZoom.toDouble(),
    sourceLabel = place.sourceLabel,
  )
}

// ---------------------------------------------------------------------------------------------
// Draw
// ---------------------------------------------------------------------------------------------

@Composable
private fun DrawTab(editor: SurveyAreaEditorState, center: LatLng, zoom: Double) {
  var selectedPart by remember { mutableStateOf<String?>(null) }
  val dataset = editor.partsDataset
  Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    InteractiveLayerMapCard(
      state = editor.drawEditor,
      dataset = dataset,
      selectedRow = selectedPart,
      onSelectRow = { selectedPart = it },
      modifier = Modifier.weight(1f).fillMaxHeight(),
      featureNoun = "part",
      showLabels = false,
      emptyCamera = CameraPosition(center.toMapLatLng(), zoom),
    )
    Column(
      modifier = Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      OutlinedTextField(
        value = editor.drawName,
        onValueChange = { editor.drawName = it },
        label = { Text("Area name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      InfoLine(
        "Select Add part and click the map to draw each polygon. Click a part to select it, then " +
          "drag its corners to reshape it."
      )
      Text("Parts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      if (dataset.rows.isEmpty()) {
        Text(
          "No parts yet. Draw at least one part to save the survey area.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      dataset.rows.forEachIndexed { index, row ->
        val selected = row.key == selectedPart
        Row(
          modifier =
            Modifier.fillMaxWidth()
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
              "Part ${index + 1}",
              style = MaterialTheme.typography.bodyMedium,
              color =
                if (selected) {
                  MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
              "${row.geometry.size} vertices • " +
                SurveyAreaGeometry.formatArea(
                  SurveyAreaGeometry.ringAreaSquareMeters(row.geometry)
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
              contentDescription = "Delete part ${index + 1}",
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
        title = "GeoJSON or KML",
        body = "Polygons and multipolygons become survey area parts. Holes are removed.",
        action = "Choose file",
        onClick = { pickTextFile(GEOMETRY_FILE_ACCEPT, editor::onFilePicked) },
      )
      UploadOption(
        icon = Icons.Outlined.FolderZip,
        title = "Shapefile (.zip)",
        body = "A zipped Shapefile with its .shp, .shx, .dbf and .prj files.",
        action = "Choose Shapefile",
        onClick = editor::chooseShapefile,
      )
      editor.uploadMessage?.let { InfoLine(it) }
    }
    Column(
      modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (upload == null) {
        Text(
          "Upload a boundary file to preview it here.",
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
            Text(areaSummary(area), style = MaterialTheme.typography.bodySmall)
            if (area.vertexCount <= MAX_DRAW_EDIT_VERTICES) {
              OutlinedButton(onClick = editor::editUploadInDrawTab) { Text("Edit in Draw tab") }
            }
          }
        }
      }
      val ignored = editor.ignoredUploadFeatures
      val issues = buildList {
        addAll(upload.result.errors)
        addAll(upload.result.warnings)
        if (ignored > 0) {
          add(
            GeoIssue(
              GeoIssueSeverity.WARNING,
              "$ignored ${if (ignored == 1) "feature isn't a polygon and was" else "features aren't polygons and were"} ignored.",
            )
          )
        }
        if (area == null && upload.result.errors.isEmpty()) {
          add(
            GeoIssue(GeoIssueSeverity.ERROR, "This file has no polygons to use as a survey area.")
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

/** Errors first, then warnings, capped so huge files don't flood the dialog. */
@Composable
internal fun IssueList(issues: List<GeoIssue>, max: Int = 12) {
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
          contentDescription = if (isError) "Error" else "Warning",
          tint =
            if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(issue.message, style = MaterialTheme.typography.bodySmall)
      }
    }
    if (issues.size > max) {
      Text(
        "…and ${issues.size - max} more",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
