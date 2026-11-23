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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.domain.model.PlaceSource
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.FormDatasetLinks
import org.openforis.collectone.v2.app.domain.model.editor.LayerStyle
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAreaSource
import org.openforis.collectone.v2.app.domain.model.editor.toLatLng
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.core.geo.io.GeoFileReader
import org.openforis.collectone.v2.core.geo.io.GeoGeometry
import org.openforis.collectone.v2.core.geo.io.GeoReadResult
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_area_shapefile_coming_soon
import org.openforis.collectone.v2.web.resources.surveyeditor_area_tab_draw
import org.openforis.collectone.v2.web.resources.surveyeditor_area_tab_search
import org.openforis.collectone.v2.web.resources.surveyeditor_area_tab_upload
import org.openforis.collectone.v2.web.ui.common.TextFilePickResult

/** Above this many vertices, uploaded or searched areas aren't loaded into the Draw tab. */
internal const val MAX_DRAW_EDIT_VERTICES = 2_000

/** Tabs of the survey area editor. */
enum class SurveyAreaEditorTab(val label: StringResource) {
  SEARCH(Res.string.surveyeditor_area_tab_search),
  DRAW(Res.string.surveyeditor_area_tab_draw),
  UPLOAD(Res.string.surveyeditor_area_tab_upload),
}

/** A file read in the Upload tab. */
data class UploadedGeometry(val fileName: String, val result: GeoReadResult)

/**
 * State of the survey area editor (everything except the place search, which lives in the
 * composable). Plain and platform-independent so the Draw and Upload flows can be unit tested.
 *
 * The Draw tab edits parts as polygon features of a scratch [DrawnPartsEditor], so it reuses the
 * Map layer editor's drawing and vertex-editing interaction ([InteractiveLayerMapCard]).
 */
class SurveyAreaEditorState(val initial: SurveyArea?) {
  var tab by mutableStateOf(SurveyAreaEditorTab.SEARCH)

  // Draw ---------------------------------------------------------------------------------------

  /** Scratch editor holding one polygon feature per drawn part. */
  internal val drawEditor: DrawnPartsEditor = DrawnPartsEditor(partsDataset(initial))

  internal val partsDatasetKey: String = PARTS_DATASET_KEY

  internal val partsDataset: EntityDataset
    get() = drawEditor.dataset

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
      source = SurveyAreaSource.Drawn,
    )
  }

  // Upload -------------------------------------------------------------------------------------

  var upload: UploadedGeometry? by mutableStateOf(null)
    private set

  /** A message about the last upload attempt that isn't tied to a parsed file. */
  var uploadMessage: UiText? by mutableStateOf(null)
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
    uploadMessage = Res.string.surveyeditor_area_shapefile_coming_soon.toUiText()
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
      source = SurveyAreaSource.Uploaded(file.fileName),
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
    private const val PARTS_DATASET_KEY = "survey-area-parts"

    private fun partsDataset(initial: SurveyArea?): EntityDataset {
      val editable = initial != null && initial.vertexCount <= MAX_DRAW_EDIT_VERTICES
      return EntityDataset(
        key = PARTS_DATASET_KEY,
        kind = DatasetKind.MAP_LAYER,
        id = "part",
        displayName = "Survey area parts",
        geometryType = GeometryType.POLYGON,
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
 * A single scratch polygon Map layer backing the Draw tab, exposed to [InteractiveLayerMapCard]
 * through [MapFeatureEditor]. Rows get keys `r1000`, `r1001`, ... and IDs `PAR-n`.
 */
internal class DrawnPartsEditor(initial: EntityDataset) : MapFeatureEditor {
  var dataset: EntityDataset by mutableStateOf(initial)
    private set

  private var nextKeyId = 1_000

  override fun addRow(key: String, at: LatLng?, geometry: List<LatLng>?): String {
    val d = dataset
    if (key != d.key) return ""
    val rowKey = "r${nextKeyId++}"
    val id = "${d.id.take(3).uppercase()}-${d.rows.size + 1}"
    val shape = geometry ?: FormDatasetLinks.defaultGeometry(d, at)
    dataset = d.copy(rows = d.rows + EntityRow(rowKey, mapOf(d.keyProperty to id), shape))
    return rowKey
  }

  override fun updateGeometry(key: String, rowKey: String, geometry: List<LatLng>) {
    val d = dataset
    if (key != d.key) return
    dataset =
      d.copy(rows = d.rows.map { if (it.key == rowKey) it.copy(geometry = geometry) else it })
  }

  fun removeRow(key: String, rowKey: String) {
    val d = dataset
    if (key != d.key) return
    dataset = d.copy(rows = d.rows.filterNot { it.key == rowKey })
  }
}

/** Converts a [SurveyPlaceItem] to a single-part [SurveyArea] from its bounding box. */
internal fun placeToSurveyArea(place: SurveyPlaceItem): SurveyArea {
  val lat = place.position.latitude
  val lng = place.position.longitude
  val bounds = place.bounds
  val boundary =
    if (bounds != null) {
      val minLng = bounds.west
      val maxLng = bounds.east
      val minLat = bounds.south
      val maxLat = bounds.north
      // If a country bounding box spans global bounds [-180, 180], restrict the boundary
      // polygon to reasonable geographic extent centered around the place center
      // so it does not wrap the whole globe into an invalid full-world box.
      if (maxLng - minLng >= 350.0) {
        val spanLat = (maxLat - minLat).coerceAtLeast(10.0)
        val halfLng = (spanLat * 1.4).coerceAtMost(35.0)
        listOf(
          LatLng.coerced(maxLat, lng - halfLng),
          LatLng.coerced(maxLat, lng + halfLng),
          LatLng.coerced(minLat, lng + halfLng),
          LatLng.coerced(minLat, lng - halfLng),
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
        LatLng.coerced(lat + delta, lng - delta),
        LatLng.coerced(lat + delta, lng + delta),
        LatLng.coerced(lat - delta, lng + delta),
        LatLng.coerced(lat - delta, lng - delta),
      )
    }
  return SurveyArea(
    name = place.name,
    parts = listOf(boundary),
    center = LatLng(lat, lng),
    zoom = place.targetZoom.toDouble(),
    source =
      when (place.source) {
        PlaceSource.PLACES_API -> SurveyAreaSource.PlaceSearch
        null -> SurveyAreaSource.Coordinates
      },
  )
}
