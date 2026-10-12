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
package org.groundplatform.v2.app.domain.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan
import org.groundplatform.v2.app.domain.model.editor.DatasetKind
import org.groundplatform.v2.app.domain.model.editor.GeometryKind
import org.groundplatform.v2.app.domain.model.editor.SurveyArea
import org.groundplatform.v2.app.domain.model.editor.SurveyAreaGeometry
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDerivation
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.app.domain.model.editor.parseHexColor
import org.groundplatform.v2.map.LatLng
import org.groundplatform.v2.map.LngLatBounds

/**
 * A single map feature stored in a [SurveyThumbnail] for rendering on top of the satellite basemap
 * and area of interest boundary.
 */
data class SurveyThumbnailFeature(
  val id: String,
  val kind: EntityShapeKind,
  val vertices: List<LatLng>,
  val colorHex: Long,
)

/**
 * A geographic place label (`mapbox.places` gazetteer / reverse-geocoded place) rendered on top of
 * the satellite imagery in a [SurveyThumbnail].
 */
data class SurveyThumbnailPlaceLabel(val name: String, val position: LatLng)

/**
 * Client-side generated satellite map thumbnail specification stored alongside a
 * [SurveyPreviewItem].
 *
 * Holds the simplified area of interest boundary rings ([areaParts]), the survey's map features
 * ([features]), Mapbox place labels ([placeLabels]), the framed geographic [bounds], [center], and
 * [zoom], and the XYZ raster satellite [tileUrlTemplate] and reference [labelTileUrlTemplate] used
 * to render the thumbnail on web and mobile survey cards.
 */
data class SurveyThumbnail(
  val areaName: String = "",
  val areaParts: List<List<LatLng>> = emptyList(),
  val features: List<SurveyThumbnailFeature> = emptyList(),
  val placeLabels: List<SurveyThumbnailPlaceLabel> = emptyList(),
  val center: LatLng = DEFAULT_CENTER,
  val bounds: LngLatBounds = DEFAULT_BOUNDS,
  val zoom: Double = DEFAULT_ZOOM,
  val tileUrlTemplate: String = DEFAULT_SATELLITE_TILE_URL,
  val labelTileUrlTemplate: String = DEFAULT_LABELS_TILE_URL,
) {
  /** Whether the thumbnail has an area of interest or map features to render. */
  val hasContent: Boolean
    get() = areaParts.isNotEmpty() || features.isNotEmpty()

  companion object {
    const val DEFAULT_SATELLITE_TILE_URL: String =
      "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"

    const val DEFAULT_LABELS_TILE_URL: String =
      "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"

    val DEFAULT_CENTER: LatLng = LatLng(-0.4198, 36.9512)
    val DEFAULT_BOUNDS: LngLatBounds =
      LngLatBounds(west = 36.9442, south = -0.4288, east = 36.9582, north = -0.4108)
    const val DEFAULT_ZOOM: Double = 14.5
  }
}

/** Coordinates of a single 256×256 Web Mercator XYZ raster tile and its normalized `[0, 1]` box. */
data class SurveyThumbnailTileCoord(
  val z: Int,
  val x: Int,
  val y: Int,
  val normLeft: Double,
  val normTop: Double,
  val normRight: Double,
  val normBottom: Double,
) {
  fun resolveUrl(template: String): String =
    template.replace("{z}", z.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())
}

/**
 * Generates [SurveyThumbnail] instances client-side from a survey's selected [SurveyArea], map
 * layers, and map features (either from an editor [SurveyEditorDraft] or from runtime collections).
 */
object SurveyThumbnailGenerator {
  /** Cap stored thumbnail features so 5K-site stress-test layers keep thumbnails lightweight. */
  const val MAX_THUMBNAIL_FEATURES: Int = 150

  /** Max vertices per simplified area-of-interest polygon ring stored on the thumbnail. */
  const val MAX_AREA_RING_VERTICES: Int = 120

  /** Max place labels rendered on a compact survey thumbnail. */
  const val MAX_PLACE_LABELS: Int = 2

  private const val MIN_BOUNDS_SPAN_DEG: Double = 0.0035
  private const val DEFAULT_LAYER_COLOR_HEX: Long = 0xFF2E7D32L

  /**
   * Seeded Mapbox Places (`mapbox.places`) gazetteer coordinates used for client-side thumbnail
   * place labeling when offline or when no live Mapbox token is configured.
   */
  private val MAPBOX_GAZETTEER_PLACES: List<SurveyThumbnailPlaceLabel> =
    listOf(
      SurveyThumbnailPlaceLabel("Othaya", LatLng(-0.4192, 36.9498)),
      SurveyThumbnailPlaceLabel("Karima Forest", LatLng(-0.4160, 36.9465)),
      SurveyThumbnailPlaceLabel("Iriaini", LatLng(-0.4238, 36.9478)),
      SurveyThumbnailPlaceLabel("Gura River", LatLng(-0.4215, 36.9535)),
      SurveyThumbnailPlaceLabel("Chinga Dam", LatLng(-0.4258, 36.9574)),
      SurveyThumbnailPlaceLabel("Nyeri", LatLng(-0.4148, 36.9510)),
      SurveyThumbnailPlaceLabel("Serengeti", LatLng(-2.3333, 34.8328)),
      SurveyThumbnailPlaceLabel("Pará", LatLng(-3.4653, -62.2159)),
      SurveyThumbnailPlaceLabel("Cần Thơ", LatLng(9.8249, 106.3422)),
    )

  /**
   * Generates a default [SurveyThumbnail] for a newly created survey before any custom survey area
   * or map features have been added.
   */
  fun forNewSurvey(
    surveyId: String,
    tileUrlTemplate: String = SurveyThumbnail.DEFAULT_SATELLITE_TILE_URL,
  ): SurveyThumbnail =
    fromRuntime(
      surveyId = surveyId,
      surveyArea = null,
      mapLayers = emptyList(),
      entities = emptyList(),
      tileUrlTemplate = tileUrlTemplate,
    )

  /**
   * Generates a [SurveyThumbnail] from a [SurveyEditorDraft] alone (used when previewing or saving
   * from the Survey Creation Wizard or Survey Editor).
   */
  fun fromDraft(
    surveyId: String,
    draft: SurveyEditorDraft,
    tileUrlTemplate: String = SurveyThumbnail.DEFAULT_SATELLITE_TILE_URL,
  ): SurveyThumbnail {
    val anchor = SurveyMapAnchor.forSurvey(surveyId)
    val features = extractFeaturesFromDraft(draft)
    return buildThumbnail(
      anchor = anchor,
      surveyArea = draft.details.surveyArea,
      features = features,
      tileUrlTemplate = tileUrlTemplate,
    )
  }

  /**
   * Generates a [SurveyThumbnail] combining the editor [draft]'s [SurveyArea] and custom-drawn row
   * geometries with the projected runtime [mapLayers] and [entities].
   */
  fun fromDraftAndRuntime(
    surveyId: String,
    draft: SurveyEditorDraft,
    mapLayers: List<MapLayerItem>,
    entities: List<GeospatialEntityItem>,
    tileUrlTemplate: String = SurveyThumbnail.DEFAULT_SATELLITE_TILE_URL,
  ): SurveyThumbnail {
    val anchor = SurveyMapAnchor.forSurvey(surveyId)
    val draftRowGeometriesById = mutableMapOf<String, List<LatLng>>()
    draft.datasets
      .filter { it.kind == DatasetKind.MAP_LAYER }
      .forEach { dataset ->
        dataset.rows.forEach { row ->
          if (row.geometry.isNotEmpty()) {
            val entityId = SurveyEditorDerivation.entityIdOf(dataset, row)
            draftRowGeometriesById[entityId] = row.geometry.map { LatLng(it.lat, it.lng) }
          }
        }
      }

    val features =
      if (entities.isNotEmpty() || mapLayers.isNotEmpty()) {
        extractFeaturesFromRuntime(
          anchor = anchor,
          mapLayers = mapLayers,
          entities = entities,
          customGeometriesById = draftRowGeometriesById,
        )
      } else {
        extractFeaturesFromDraft(draft)
      }

    return buildThumbnail(
      anchor = anchor,
      surveyArea = draft.details.surveyArea,
      features = features,
      tileUrlTemplate = tileUrlTemplate,
    )
  }

  /**
   * Generates a [SurveyThumbnail] from runtime survey collections ([surveyArea], [mapLayers], and
   * [entities]).
   */
  fun fromRuntime(
    surveyId: String,
    surveyArea: SurveyArea?,
    mapLayers: List<MapLayerItem>,
    entities: List<GeospatialEntityItem>,
    tileUrlTemplate: String = SurveyThumbnail.DEFAULT_SATELLITE_TILE_URL,
  ): SurveyThumbnail {
    val anchor = SurveyMapAnchor.forSurvey(surveyId)
    val features =
      extractFeaturesFromRuntime(
        anchor = anchor,
        mapLayers = mapLayers,
        entities = entities,
        customGeometriesById = emptyMap(),
      )
    return buildThumbnail(
      anchor = anchor,
      surveyArea = surveyArea,
      features = features,
      tileUrlTemplate = tileUrlTemplate,
    )
  }

  /**
   * Refreshes the map features on an [existing] [SurveyThumbnail] when runtime [entities] change
   * (for example after field form submissions or random site generation), preserving any explicit
   * survey area polygon rings already stored on [existing].
   */
  fun updateFeatures(
    existing: SurveyThumbnail,
    surveyId: String,
    mapLayers: List<MapLayerItem>,
    entities: List<GeospatialEntityItem>,
  ): SurveyThumbnail {
    val preservedArea =
      if (existing.areaName.isNotBlank() && existing.areaParts.isNotEmpty()) {
        SurveyArea(
          name = existing.areaName,
          center =
            org.groundplatform.v2.app.domain.model.editor.LatLng(
              existing.center.latitude,
              existing.center.longitude,
            ),
          zoom = existing.zoom,
          parts =
            existing.areaParts.map { ring ->
              ring.map {
                org.groundplatform.v2.app.domain.model.editor.LatLng(
                  it.latitude,
                  it.longitude,
                )
              }
            },
        )
      } else {
        null
      }
    return fromRuntime(
      surveyId = surveyId,
      surveyArea = preservedArea,
      mapLayers = mapLayers,
      entities = entities,
      tileUrlTemplate = existing.tileUrlTemplate,
    )
  }

  private fun extractFeaturesFromDraft(draft: SurveyEditorDraft): List<SurveyThumbnailFeature> {
    val result = mutableListOf<SurveyThumbnailFeature>()
    val mapDatasets =
      draft.datasets.filter { it.kind == DatasetKind.MAP_LAYER && it.style.visibleByDefault }
    val datasetsToUse = mapDatasets.ifEmpty {
      draft.datasets.filter { it.kind == DatasetKind.MAP_LAYER }
    }

    for (dataset in datasetsToUse) {
      val shapeKind =
        when (dataset.geometryKind) {
          GeometryKind.POINT -> EntityShapeKind.POINT
          GeometryKind.LINE -> EntityShapeKind.LINE
          GeometryKind.POLYGON -> EntityShapeKind.POLYGON
        }
      val layerColor =
        0xFF000000L or
          ((parseHexColor(dataset.style.colorHex) ?: DEFAULT_LAYER_COLOR_HEX) and 0xFFFFFFL)
      for (row in dataset.rows) {
        if (result.size >= MAX_THUMBNAIL_FEATURES) break
        val vertices = row.geometry.map { LatLng(it.lat, it.lng) }
        if (vertices.isEmpty()) continue
        val validVertices =
          when (shapeKind) {
            EntityShapeKind.POLYGON -> if (vertices.size >= 3) vertices else continue
            EntityShapeKind.LINE -> if (vertices.size >= 2) vertices else continue
            EntityShapeKind.POINT -> listOf(vertices.first())
          }
        result.add(
          SurveyThumbnailFeature(
            id = SurveyEditorDerivation.entityIdOf(dataset, row),
            kind = shapeKind,
            vertices = validVertices,
            colorHex = layerColor,
          )
        )
      }
      if (result.size >= MAX_THUMBNAIL_FEATURES) break
    }
    return result
  }

  private fun extractFeaturesFromRuntime(
    anchor: SurveyMapAnchor,
    mapLayers: List<MapLayerItem>,
    entities: List<GeospatialEntityItem>,
    customGeometriesById: Map<String, List<LatLng>>,
  ): List<SurveyThumbnailFeature> {
    val layersById = mapLayers.associateBy { it.id }
    val visibleLayerIds = mapLayers.filter { it.isVisible }.map { it.id }.toSet()
    val filteredEntities = entities.filter {
      visibleLayerIds.isEmpty() || it.layerId in visibleLayerIds
    }
    val sourceEntities = filteredEntities.ifEmpty { entities }

    val result = ArrayList<SurveyThumbnailFeature>(min(sourceEntities.size, MAX_THUMBNAIL_FEATURES))
    for (entity in sourceEntities) {
      if (result.size >= MAX_THUMBNAIL_FEATURES) break
      val kind = EntityShapeKind.of(entity.geometryTypeLabel) ?: continue
      val vertices =
        customGeometriesById[entity.id]?.takeIf { it.isNotEmpty() }
          ?: EntityShape.vertices(entity, anchor)
      if (vertices.isEmpty()) continue
      val layer = layersById[entity.layerId]
      val colorHex = entity.mapColorHex(layer)
      result.add(
        SurveyThumbnailFeature(
          id = entity.id,
          kind = kind,
          vertices = vertices,
          colorHex = colorHex,
        )
      )
    }
    return result
  }

  private fun buildThumbnail(
    anchor: SurveyMapAnchor,
    surveyArea: SurveyArea?,
    features: List<SurveyThumbnailFeature>,
    tileUrlTemplate: String,
  ): SurveyThumbnail {
    val explicitParts =
      surveyArea
        ?.parts
        ?.let { SurveyAreaGeometry.displayParts(it, maxVerticesPerPart = MAX_AREA_RING_VERTICES) }
        ?.filter { it.size >= 3 }
        ?.map { ring -> ring.map { LatLng(it.lat, it.lng) } }
        .orEmpty()

    val areaParts =
      if (explicitParts.isNotEmpty()) {
        explicitParts
      } else {
        listOf(synthesizeDefaultAreaRing(anchor, features))
      }

    val allPoints = buildList {
      areaParts.forEach { addAll(it) }
      features.forEach { addAll(it.vertices) }
      if (isEmpty() && surveyArea != null) {
        add(LatLng(surveyArea.center.lat, surveyArea.center.lng))
      }
      if (isEmpty()) {
        add(anchor.center)
      }
    }

    val rawMinLat = allPoints.minOf { it.latitude }
    val rawMaxLat = allPoints.maxOf { it.latitude }
    val rawMinLng = allPoints.minOf { it.longitude }
    val rawMaxLng = allPoints.maxOf { it.longitude }

    val centerLat = ((rawMinLat + rawMaxLat) / 2.0).roundTo(6)
    val centerLng = ((rawMinLng + rawMaxLng) / 2.0).roundTo(6)
    val latSpan = max(rawMaxLat - rawMinLat, MIN_BOUNDS_SPAN_DEG)
    val lngSpan = max(rawMaxLng - rawMinLng, MIN_BOUNDS_SPAN_DEG)
    val halfSpan = max(latSpan, lngSpan) * 0.58

    val bounds =
      LngLatBounds(
        west = (centerLng - halfSpan).roundTo(6),
        south = (centerLat - halfSpan).roundTo(6),
        east = (centerLng + halfSpan).roundTo(6),
        north = (centerLat + halfSpan).roundTo(6),
      )
    val zoom = WebMercatorThumbnailProjection.continuousZoomForBounds(bounds)
    val placeLabels =
      resolvePlaceLabels(
        anchor = anchor,
        surveyArea = surveyArea,
        bounds = bounds,
        center = LatLng(centerLat, centerLng),
      )

    return SurveyThumbnail(
      areaName = surveyArea?.name?.trim().orEmpty(),
      areaParts = areaParts,
      features = features,
      placeLabels = placeLabels,
      center = LatLng(centerLat, centerLng),
      bounds = bounds,
      zoom = zoom,
      tileUrlTemplate = tileUrlTemplate,
    )
  }

  /**
   * Resolves 1–2 concise Mapbox place labels (`mapbox.places` gazetteer + survey area name) inside
   * [bounds] for rendering on the thumbnail.
   */
  internal fun resolvePlaceLabels(
    anchor: SurveyMapAnchor,
    surveyArea: SurveyArea?,
    bounds: LngLatBounds,
    center: LatLng,
  ): List<SurveyThumbnailPlaceLabel> {
    val inBounds = MAPBOX_GAZETTEER_PLACES.filter { place ->
      place.position.latitude in bounds.south..bounds.north &&
        place.position.longitude in bounds.west..bounds.east
    }
    val chosen = mutableListOf<SurveyThumbnailPlaceLabel>()
    val areaShortName = surveyArea?.name?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() }
    if (areaShortName != null) {
      val matchingGazetteer = inBounds.firstOrNull {
        areaShortName.contains(it.name, ignoreCase = true) ||
          it.name.contains(areaShortName, ignoreCase = true)
      }
      if (matchingGazetteer != null) {
        chosen.add(matchingGazetteer)
      } else {
        chosen.add(
          SurveyThumbnailPlaceLabel(
            name = areaShortName,
            position = LatLng(surveyArea.center.lat, surveyArea.center.lng),
          )
        )
      }
    }

    for (candidate in inBounds) {
      if (chosen.size >= MAX_PLACE_LABELS) break
      if (chosen.none { it.name.equals(candidate.name, ignoreCase = true) }) {
        chosen.add(candidate)
      }
    }

    if (chosen.isEmpty()) {
      val anchorName =
        anchor.label
          .substringBefore(" 0.")
          .substringBefore(" 1")
          .substringBefore(" 2")
          .substringBefore(" 3")
          .substringBefore(" 9")
          .trim()
          .ifBlank { "Survey Area" }
      chosen.add(SurveyThumbnailPlaceLabel(name = anchorName, position = center))
    }
    return chosen.take(MAX_PLACE_LABELS)
  }

  /**
   * Synthesizes the 4-corner rectangular AOI sector boundary enclosing the survey's [features] (or
   * the default `[0.05..0.95] × [0.14..0.88]` sector rectangle around [anchor], matching
   * `offline-sector` on the main map in `SurveyMapContent`).
   */
  internal fun synthesizeDefaultAreaRing(
    anchor: SurveyMapAnchor,
    features: List<SurveyThumbnailFeature>,
  ): List<LatLng> {
    val nwSector = anchor.toLatLng(0.05, 0.14)
    val seSector = anchor.toLatLng(0.95, 0.88)
    val sectorMinLat = min(nwSector.latitude, seSector.latitude)
    val sectorMaxLat = max(nwSector.latitude, seSector.latitude)
    val sectorMinLng = min(nwSector.longitude, seSector.longitude)
    val sectorMaxLng = max(nwSector.longitude, seSector.longitude)

    val featureVertices = features.flatMap { it.vertices }
    val (minLat, maxLat, minLng, maxLng) =
      if (featureVertices.isNotEmpty()) {
        val fMinLat = featureVertices.minOf { it.latitude }
        val fMaxLat = featureVertices.maxOf { it.latitude }
        val fMinLng = featureVertices.minOf { it.longitude }
        val fMaxLng = featureVertices.maxOf { it.longitude }
        val padLat = max((fMaxLat - fMinLat) * 0.18, SurveyMapAnchor.SPAN_LATITUDE * 0.12)
        val padLng = max((fMaxLng - fMinLng) * 0.18, SurveyMapAnchor.SPAN_LONGITUDE * 0.12)
        listOf(
          min(sectorMinLat, fMinLat - padLat),
          max(sectorMaxLat, fMaxLat + padLat),
          min(sectorMinLng, fMinLng - padLng),
          max(sectorMaxLng, fMaxLng + padLng),
        )
      } else {
        listOf(sectorMinLat, sectorMaxLat, sectorMinLng, sectorMaxLng)
      }

    // 4-vertex rectangular AOI square matching the main map's sector boundary
    return listOf(
      LatLng(maxLat.roundTo(6), minLng.roundTo(6)),
      LatLng(maxLat.roundTo(6), maxLng.roundTo(6)),
      LatLng(minLat.roundTo(6), maxLng.roundTo(6)),
      LatLng(minLat.roundTo(6), minLng.roundTo(6)),
    )
  }
}

/**
 * Pure Web Mercator (`EPSG:3857`) projection and XYZ slippy-map tile math used to align raster
 * satellite tiles and vector AOI/feature overlays inside [SurveyMapThumbnail].
 */
object WebMercatorThumbnailProjection {
  private const val MAX_MERCATOR_LAT: Double = 85.05112878

  /** Maps longitude in `[-180, 180]` to normalized Web Mercator `x` in `[0, 1]`. */
  fun lngToNormX(lng: Double): Double = ((lng + 180.0) / 360.0).coerceIn(0.0, 1.0)

  /**
   * Maps latitude in degrees to normalized Web Mercator `y` in `[0, 1]` (`0` at north, `1` south).
   */
  fun latToNormY(lat: Double): Double {
    val clamped = lat.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
    val rad = clamped * PI / 180.0
    val mercN = ln(tan(rad) + 1.0 / cos(rad))
    return ((1.0 - mercN / PI) / 2.0).coerceIn(0.0, 1.0)
  }

  /** Continuous Web Mercator zoom that fits [bounds] inside a 160 px square thumbnail. */
  fun continuousZoomForBounds(bounds: LngLatBounds): Double {
    val dx = abs(lngToNormX(bounds.east) - lngToNormX(bounds.west))
    val dy = abs(latToNormY(bounds.south) - latToNormY(bounds.north))
    val spanNorm = max(max(dx, dy), 1e-6)
    // 160 px logical viewport, 256 px tile size
    val zoom = ln(160.0 / (256.0 * spanNorm)) / ln(2.0)
    return (zoom.coerceIn(2.0, 17.5) * 100.0).roundToInt() / 100.0
  }

  /**
   * Computes the covering XYZ raster tiles for [bounds] at an integer zoom chosen so the viewport
   * spans at most [maxTilesPerAxis] tiles along each axis (typically 1–4 tiles total).
   */
  fun coveringTiles(
    bounds: LngLatBounds,
    fallbackZoom: Double = SurveyThumbnail.DEFAULT_ZOOM,
    maxTilesPerAxis: Int = 3,
  ): List<SurveyThumbnailTileCoord> {
    val minNormX = min(lngToNormX(bounds.west), lngToNormX(bounds.east))
    val maxNormX = max(lngToNormX(bounds.west), lngToNormX(bounds.east))
    val minNormY = min(latToNormY(bounds.north), latToNormY(bounds.south))
    val maxNormY = max(latToNormY(bounds.north), latToNormY(bounds.south))

    val baseZoom =
      continuousZoomForBounds(bounds).takeIf { it.isFinite() }?.roundToInt()
        ?: fallbackZoom.roundToInt()
    var z = baseZoom.coerceIn(2, 17)

    while (z > 2) {
      val n = 1 shl z
      val minTx = floor(minNormX * n).toInt().coerceIn(0, n - 1)
      val maxTx = floor(maxNormX * n).toInt().coerceIn(0, n - 1)
      val minTy = floor(minNormY * n).toInt().coerceIn(0, n - 1)
      val maxTy = floor(maxNormY * n).toInt().coerceIn(0, n - 1)
      if ((maxTx - minTx + 1) <= maxTilesPerAxis && (maxTy - minTy + 1) <= maxTilesPerAxis) {
        break
      }
      z--
    }

    val n = 1 shl z
    val minTx = floor(minNormX * n).toInt().coerceIn(0, n - 1)
    val maxTx = floor(maxNormX * n).toInt().coerceIn(0, n - 1)
    val minTy = floor(minNormY * n).toInt().coerceIn(0, n - 1)
    val maxTy = floor(maxNormY * n).toInt().coerceIn(0, n - 1)

    val tiles = mutableListOf<SurveyThumbnailTileCoord>()
    val invN = 1.0 / n.toDouble()
    for (ty in minTy..maxTy) {
      for (tx in minTx..maxTx) {
        tiles.add(
          SurveyThumbnailTileCoord(
            z = z,
            x = tx,
            y = ty,
            normLeft = tx * invN,
            normTop = ty * invN,
            normRight = (tx + 1) * invN,
            normBottom = (ty + 1) * invN,
          )
        )
      }
    }
    return tiles
  }
}
