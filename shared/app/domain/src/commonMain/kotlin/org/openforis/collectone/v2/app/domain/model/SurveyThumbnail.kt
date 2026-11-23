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
package org.openforis.collectone.v2.app.domain.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.tan
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAreaGeometry
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDerivation
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.parseHexColor
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * A single map feature stored in a [SurveyThumbnail] for rendering on top of the offline fallback
 * canvas when a Mapbox Static Images snapshot is unavailable.
 */
data class SurveyThumbnailFeature(
  val id: String,
  val type: GeometryType,
  val vertices: List<LatLng>,
  val colorHex: Long,
)

/**
 * A geographic place label (`mapbox.places` gazetteer or survey area name) rendered on the offline
 * vector fallback map in [SurveyThumbnail].
 */
data class SurveyThumbnailPlaceLabel(val name: String, val position: LatLng)

/**
 * Client-side satellite map thumbnail specification stored alongside a [SurveyPreviewItem].
 *
 * Holds the simplified area of interest boundary rings ([areaParts]), in-bounds map features
 * ([features]), offline fallback place labels ([placeLabels]), and the framed rectangular
 * geographic [bounds], [center], and [zoom]. Online hosts render a real Mapbox Static Images API
 * (`mapbox/satellite-streets-v12`) hero image built by [staticImageUrl]; offline and tokenless
 * hosts render the framed vector overlay inside [bounds].
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
) {
  /** Whether the thumbnail carries an explicit area of interest, area name, or map features. */
  val hasContent: Boolean
    get() = areaParts.isNotEmpty() || features.isNotEmpty() || areaName.isNotBlank()

  /**
   * Deterministic Mapbox Static Images API URL (`mapbox/satellite-streets-v12`) for this thumbnail.
   *
   * Always frames the viewport using explicit `[west,south,east,north]` bounds (`6` decimals) and
   * never emits the survey area boundary ([areaParts]) in the GeoJSON overlay. When [features] has
   * valid map features, greedily encodes an RFC 7946 `FeatureCollection` (`Polygon`, `LineString`,
   * and `Point` simplestyle features) while keeping the full URL at or below
   * [MAX_STATIC_URL_LENGTH]. Omits `geojson(...)` when [features] has no valid geometries.
   */
  fun staticImageUrl(
    accessToken: String,
    widthPx: Int = DEFAULT_STATIC_WIDTH_PX,
    heightPx: Int = DEFAULT_STATIC_HEIGHT_PX,
    highDpi: Boolean = true,
  ): String {
    val width = widthPx.coerceIn(1, MAX_STATIC_IMAGE_PX)
    val height = heightPx.coerceIn(1, MAX_STATIC_IMAGE_PX)
    val retina = if (highDpi) "@2x" else ""
    val encodedToken = MapboxStaticUrlEncoder.encodeComponent(accessToken.trim())
    val query = "attribution=false&logo=false&access_token=$encodedToken"

    val safeBounds = sanitizeThumbnailBounds(bounds)
    val west = MapboxStaticUrlEncoder.formatCoord(safeBounds.west, decimals = 6)
    val south = MapboxStaticUrlEncoder.formatCoord(safeBounds.south, decimals = 6)
    val east = MapboxStaticUrlEncoder.formatCoord(safeBounds.east, decimals = 6)
    val north = MapboxStaticUrlEncoder.formatCoord(safeBounds.north, decimals = 6)
    val viewportSuffix = "[$west,$south,$east,$north]/${width}x${height}$retina?$query"

    if (features.isNotEmpty()) {
      val geoJsonUrl =
        MapboxStaticUrlEncoder.buildGreedyFeatureCollectionUrl(
          baseUrl = STATIC_STYLE_BASE_URL,
          viewportSuffix = viewportSuffix,
          features = features,
          bounds = safeBounds,
          maxUrlLength = MAX_STATIC_URL_LENGTH,
        )
      if (geoJsonUrl != null) {
        return geoJsonUrl
      }
    }

    return "$STATIC_STYLE_BASE_URL/$viewportSuffix"
  }

  companion object {
    /**
     * Mapbox Satellite Streets raster tiles template retained for compatibility with custom tile
     * overrides.
     */
    const val DEFAULT_SATELLITE_TILE_URL: String =
      "https://api.mapbox.com/styles/v1/mapbox/satellite-streets-v12/tiles/256/{z}/{x}/{y}"

    /**
     * Mapbox Satellite Streets Static Images endpoint (satellite imagery + native place labels).
     */
    const val STATIC_STYLE_BASE_URL: String =
      "https://api.mapbox.com/styles/v1/mapbox/satellite-streets-v12/static"

    const val DEFAULT_STATIC_WIDTH_PX: Int = 640
    const val DEFAULT_STATIC_HEIGHT_PX: Int = 280
    const val MAX_STATIC_IMAGE_PX: Int = 1280
    const val MAX_STATIC_FEATURE_VERTICES: Int = 24

    /** Conservative ceiling below Mapbox's 8,192-character Static Images URL limit. */
    const val MAX_STATIC_URL_LENGTH: Int = 7500

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
  /** Cap stored thumbnail features so large layers keep thumbnails lightweight. */
  const val MAX_THUMBNAIL_FEATURES: Int = 150

  /** Max vertices per simplified area-of-interest polygon ring stored on the thumbnail. */
  const val MAX_AREA_RING_VERTICES: Int = 120

  /** Max place labels rendered on an offline fallback survey thumbnail. */
  const val MAX_PLACE_LABELS: Int = 2

  /** Minimum geographic span (~1.3 km) when framing map features so small clusters stay legible. */
  const val MIN_FEATURE_FRAME_SPAN_DEG: Double = 0.012

  internal const val MIN_BOUNDS_SPAN_DEG: Double = 0.0035
  internal const val MAX_THUMBNAIL_LAT: Double = 85.0
  private const val BOUNDS_PADDING_FRACTION: Double = 0.14
  private const val FEATURE_BOUNDS_PADDING_FRACTION: Double = 0.18
  private const val DEFAULT_SURVEY_AREA_ZOOM: Double = 12.5
  private const val HERO_ASPECT_HEIGHT_OVER_WIDTH: Double = 280.0 / 640.0
  private const val DEFAULT_LAYER_COLOR_HEX: Long = 0xFF2E7D32L

  /**
   * Seeded gazetteer coordinates used for client-side thumbnail place labeling when rendering the
   * offline Canvas fallback without a live Mapbox token.
   */
  private val MAPBOX_GAZETTEER_PLACES: List<SurveyThumbnailPlaceLabel> =
    listOf(
      SurveyThumbnailPlaceLabel("Othaya", LatLng(-0.4192, 36.9498)),
      SurveyThumbnailPlaceLabel("Karima Forest", LatLng(-0.4160, 36.9465)),
      SurveyThumbnailPlaceLabel("Iriaini", LatLng(-0.4238, 36.9478)),
      SurveyThumbnailPlaceLabel("Gura River", LatLng(-0.4215, 36.9535)),
      SurveyThumbnailPlaceLabel("Chinga Dam", LatLng(-0.4258, 36.9574)),
      SurveyThumbnailPlaceLabel("Nyeri", LatLng(-0.4148, 36.9510)),
      SurveyThumbnailPlaceLabel("Arusha", LatLng(-3.3869, 36.6830)),
      SurveyThumbnailPlaceLabel("Kumasi", LatLng(6.6885, -1.6244)),
      SurveyThumbnailPlaceLabel("Kakamega", LatLng(0.2827, 34.7519)),
      SurveyThumbnailPlaceLabel("Serengeti", LatLng(-2.3333, 34.8328)),
      SurveyThumbnailPlaceLabel("Pará", LatLng(-3.4653, -62.2159)),
      SurveyThumbnailPlaceLabel("Cần Thơ", LatLng(9.8249, 106.3422)),
      SurveyThumbnailPlaceLabel("Palangka Raya", LatLng(-2.2136, 113.9213)),
      SurveyThumbnailPlaceLabel("Sebangau", LatLng(-2.2185, 113.9160)),
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
            val entityId = SurveyEditorDerivation.entityIdOf(dataset, row, surveyId)
            draftRowGeometriesById[entityId] = row.geometry
          }
        }
      }

    val features =
      if (entities.isNotEmpty()) {
        extractFeaturesFromRuntime(
            mapLayers = mapLayers,
            entities = entities,
            customGeometriesById = draftRowGeometriesById,
          )
          .ifEmpty { extractFeaturesFromDraft(draft) }
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
   * Generates a [SurveyThumbnail] from a catalog overview's [surveyArea] and pre-extracted
   * [features] before full entity datasets are downloaded.
   */
  fun fromOverviewFeatures(
    surveyId: String,
    surveyArea: SurveyArea?,
    features: List<SurveyThumbnailFeature>,
    tileUrlTemplate: String = SurveyThumbnail.DEFAULT_SATELLITE_TILE_URL,
  ): SurveyThumbnail {
    val anchor = SurveyMapAnchor.forSurvey(surveyId)
    return buildThumbnail(
      anchor = anchor,
      surveyArea = surveyArea,
      features = features.sampleEvenly(MAX_THUMBNAIL_FEATURES),
      tileUrlTemplate = tileUrlTemplate,
    )
  }

  /**
   * Refreshes the map features on an [existing] [SurveyThumbnail] when runtime [entities] change
   * (for example after field form submissions or random site generation), preserving any survey
   * area already stored on [existing].
   */
  fun updateFeatures(
    existing: SurveyThumbnail,
    surveyId: String,
    mapLayers: List<MapLayerItem>,
    entities: List<GeospatialEntityItem>,
  ): SurveyThumbnail {
    val preservedArea =
      if (existing.areaParts.isNotEmpty() || existing.areaName.isNotBlank()) {
        SurveyArea(
          name = existing.areaName,
          center = existing.center,
          zoom = existing.zoom,
          parts = existing.areaParts,
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
    val allMapDatasets = draft.datasets.filter { it.kind == DatasetKind.MAP_LAYER }
    val datasetsToUse =
      allMapDatasets.filter { it.style.visibleByDefault }.ifEmpty { allMapDatasets }
    return datasetsToUse
      .flatMap { dataset ->
        val layerColor = resolveLayerColorHex(dataset)
        dataset.rows.mapNotNull { row -> draftRowFeature(dataset, row, layerColor) }
      }
      .sampleEvenly(MAX_THUMBNAIL_FEATURES)
  }

  private fun resolveLayerColorHex(dataset: EntityDataset): Long =
    0xFF000000L or
      ((parseHexColor(dataset.style.colorHex) ?: DEFAULT_LAYER_COLOR_HEX) and 0xFFFFFFL)

  private fun draftRowFeature(
    dataset: EntityDataset,
    row: EntityRow,
    layerColor: Long,
  ): SurveyThumbnailFeature? {
    val validVertices =
      when (dataset.geometryType) {
        GeometryType.POLYGON -> row.geometry.takeIf { it.size >= 3 }
        GeometryType.LINE_STRING -> row.geometry.takeIf { it.size >= 2 }
        GeometryType.POINT -> row.geometry.firstOrNull()?.let(::listOf)
      } ?: return null
    return SurveyThumbnailFeature(
      id = SurveyEditorDerivation.entityIdOf(dataset, row),
      type = dataset.geometryType,
      vertices = validVertices,
      colorHex = layerColor,
    )
  }

  private fun extractFeaturesFromRuntime(
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

    return sourceEntities
      .flatMap { entity -> runtimeEntityFeatures(entity, layersById, customGeometriesById) }
      .sampleEvenly(MAX_THUMBNAIL_FEATURES)
  }

  private fun runtimeEntityFeatures(
    entity: GeospatialEntityItem,
    layersById: Map<String, MapLayerItem>,
    customGeometriesById: Map<String, List<LatLng>>,
  ): Sequence<SurveyThumbnailFeature> {
    val geometry = entity.geometry ?: return emptySequence()
    val outlines =
      customGeometriesById[entity.id]?.takeIf { it.isNotEmpty() }?.let(::listOf)
        ?: geometry.parts.map { it.outline }
    val colorHex = entity.mapColorHex(layersById[entity.layerId])
    return outlines.asSequence().map { vertices ->
      SurveyThumbnailFeature(
        id = entity.id,
        type = geometry.type,
        vertices = vertices,
        colorHex = colorHex,
      )
    }
  }

  /**
   * Frames the thumbnail around in-area map [features] (with [FEATURE_BOUNDS_PADDING_FRACTION] and
   * [MIN_FEATURE_FRAME_SPAN_DEG]) when present so plots, lines, and points render at a legible
   * neighborhood zoom. Filters [features] against [surveyArea] bounds first so cross-region
   * placeholder entities never distort another survey's viewport, and falls back to [surveyArea]
   * bounds or [anchor] when no in-area features exist.
   */
  private data class FramedViewport(
    val areaParts: List<List<LatLng>>,
    val bounds: LngLatBounds,
    val center: LatLng,
    val zoom: Double,
    val features: List<SurveyThumbnailFeature>,
  )

  private fun buildThumbnail(
    anchor: SurveyMapAnchor,
    surveyArea: SurveyArea?,
    features: List<SurveyThumbnailFeature>,
    tileUrlTemplate: String,
  ): SurveyThumbnail {
    val explicitParts =
      surveyArea
        ?.parts
        ?.map(::stripClosingVertex)
        ?.let { SurveyAreaGeometry.displayParts(it, maxVerticesPerPart = MAX_AREA_RING_VERTICES) }
        ?.filter { it.size >= 3 }
        .orEmpty()

    val framed =
      if (surveyArea != null) {
        frameWithSurveyArea(surveyArea, explicitParts, features)
      } else {
        frameWithoutSurveyArea(anchor, features)
      }

    val placeLabels =
      resolvePlaceLabels(
        anchor = anchor,
        surveyArea = surveyArea,
        bounds = framed.bounds,
        center = framed.center,
      )

    return SurveyThumbnail(
      areaName = surveyArea?.name?.trim().orEmpty(),
      areaParts = framed.areaParts,
      features = framed.features,
      placeLabels = placeLabels,
      center = framed.center,
      bounds = framed.bounds,
      zoom = framed.zoom,
      tileUrlTemplate = tileUrlTemplate,
    )
  }

  private fun frameWithSurveyArea(
    surveyArea: SurveyArea,
    explicitParts: List<List<LatLng>>,
    features: List<SurveyThumbnailFeature>,
  ): FramedViewport {
    val effectiveZoom =
      (if (surveyArea.zoom.isFinite() && surveyArea.zoom > 0.0) surveyArea.zoom
        else DEFAULT_SURVEY_AREA_ZOOM)
        .coerceIn(2.0, 18.0)
    val areaBounds =
      if (explicitParts.isNotEmpty()) {
        rectangularBoundsOf(
          points = explicitParts.flatten(),
          paddingFraction = BOUNDS_PADDING_FRACTION,
          minSpanDeg = MIN_BOUNDS_SPAN_DEG,
        )
      } else {
        boundsAroundCenterAndZoom(surveyArea.center, effectiveZoom)
      }
    val inAreaFeatures = filterFeaturesInBounds(features, areaBounds)
    if (inAreaFeatures.isNotEmpty()) {
      val bounds =
        rectangularBoundsOf(
          points = inAreaFeatures.flatMap { it.vertices },
          paddingFraction = FEATURE_BOUNDS_PADDING_FRACTION,
          minSpanDeg = MIN_FEATURE_FRAME_SPAN_DEG,
        )
      return FramedViewport(
        areaParts = explicitParts,
        bounds = bounds,
        center = roundedCenterOf(bounds),
        zoom = WebMercatorThumbnailProjection.continuousZoomForBounds(bounds),
        features = inAreaFeatures,
      )
    }
    val center =
      if (explicitParts.isNotEmpty()) {
        roundedCenterOf(areaBounds)
      } else {
        LatLng(
          latitude =
            surveyArea.center.latitude.coerceIn(-MAX_THUMBNAIL_LAT, MAX_THUMBNAIL_LAT).roundTo(6),
          longitude = surveyArea.center.longitude.coerceIn(-180.0, 180.0).roundTo(6),
        )
      }
    val zoom =
      if (explicitParts.isNotEmpty()) {
        WebMercatorThumbnailProjection.continuousZoomForBounds(areaBounds)
      } else {
        (effectiveZoom * 100.0).roundToInt() / 100.0
      }
    return FramedViewport(
      areaParts = explicitParts,
      bounds = areaBounds,
      center = center,
      zoom = zoom,
      features = emptyList(),
    )
  }

  private fun frameWithoutSurveyArea(
    anchor: SurveyMapAnchor,
    features: List<SurveyThumbnailFeature>,
  ): FramedViewport {
    val featureVertices = features.flatMap { it.vertices }
    val bounds =
      if (featureVertices.isNotEmpty()) {
        rectangularBoundsOf(
          points = featureVertices,
          paddingFraction = FEATURE_BOUNDS_PADDING_FRACTION,
          minSpanDeg = MIN_FEATURE_FRAME_SPAN_DEG,
        )
      } else {
        rectangularBoundsOf(
          points = listOf(anchor.toLatLng(0.05, 0.14), anchor.toLatLng(0.95, 0.88)),
          paddingFraction = BOUNDS_PADDING_FRACTION,
          minSpanDeg = MIN_BOUNDS_SPAN_DEG,
        )
      }
    return FramedViewport(
      areaParts = emptyList(),
      bounds = bounds,
      center = roundedCenterOf(bounds),
      zoom = WebMercatorThumbnailProjection.continuousZoomForBounds(bounds),
      features = if (featureVertices.isNotEmpty()) features else emptyList(),
    )
  }

  private fun filterFeaturesInBounds(
    features: List<SurveyThumbnailFeature>,
    bounds: LngLatBounds,
  ): List<SurveyThumbnailFeature> = features.filter { feature ->
    feature.vertices.any { vertex -> vertex in bounds }
  }

  private fun rectangularBoundsOf(
    points: List<LatLng>,
    paddingFraction: Double,
    minSpanDeg: Double = MIN_BOUNDS_SPAN_DEG,
  ): LngLatBounds {
    val rawMinLat = points.minOf { it.latitude }
    val rawMaxLat = points.maxOf { it.latitude }
    val rawMinLng = points.minOf { it.longitude }
    val rawMaxLng = points.maxOf { it.longitude }

    val centerLat = (rawMinLat + rawMaxLat) / 2.0
    val centerLng = (rawMinLng + rawMaxLng) / 2.0
    val latSpan = max(rawMaxLat - rawMinLat, minSpanDeg)
    val lngSpan = max(rawMaxLng - rawMinLng, minSpanDeg)
    val halfLat = latSpan * (0.5 + paddingFraction)
    val halfLng = lngSpan * (0.5 + paddingFraction)

    return sanitizeThumbnailBounds(
      LngLatBounds(
        west = centerLng - halfLng,
        south = centerLat - halfLat,
        east = centerLng + halfLng,
        north = centerLat + halfLat,
      )
    )
  }

  private fun boundsAroundCenterAndZoom(center: LatLng, zoom: Double): LngLatBounds {
    val clampedLat = center.latitude.coerceIn(-MAX_THUMBNAIL_LAT, MAX_THUMBNAIL_LAT)
    val clampedLng = center.longitude.coerceIn(-180.0, 180.0)
    val lngSpan = max(450.0 / 2.0.pow(zoom), MIN_BOUNDS_SPAN_DEG)
    val cosLat = cos(clampedLat * PI / 180.0).coerceAtLeast(0.2)
    val latSpan = max(lngSpan * HERO_ASPECT_HEIGHT_OVER_WIDTH * cosLat, MIN_BOUNDS_SPAN_DEG)
    return sanitizeThumbnailBounds(
      LngLatBounds(
        west = clampedLng - lngSpan / 2.0,
        south = clampedLat - latSpan / 2.0,
        east = clampedLng + lngSpan / 2.0,
        north = clampedLat + latSpan / 2.0,
      )
    )
  }

  private fun roundedCenterOf(bounds: LngLatBounds): LatLng =
    LatLng(
      latitude = bounds.center.latitude.coerceIn(-MAX_THUMBNAIL_LAT, MAX_THUMBNAIL_LAT).roundTo(6),
      longitude = bounds.center.longitude.coerceIn(-180.0, 180.0).roundTo(6),
    )

  /**
   * Resolves 1–2 concise place labels (`mapbox.places` gazetteer + survey area name) inside
   * [bounds] for rendering on the offline Canvas fallback.
   */
  internal fun resolvePlaceLabels(
    anchor: SurveyMapAnchor,
    surveyArea: SurveyArea?,
    bounds: LngLatBounds,
    center: LatLng,
  ): List<SurveyThumbnailPlaceLabel> {
    val inBounds = MAPBOX_GAZETTEER_PLACES.filter { place -> place.position in bounds }
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
        val labelPos = if (surveyArea.center in bounds) surveyArea.center else center
        chosen.add(SurveyThumbnailPlaceLabel(name = areaShortName, position = labelPos))
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
}

private fun stripClosingVertex(ring: List<LatLng>): List<LatLng> =
  if (ring.size >= 2 && ring.first() == ring.last()) ring.dropLast(1) else ring

/**
 * Clamps [raw] into Mapbox Static Images bounds (`[-180, 180]` × `[-85, 85]`) and guarantees `west
 * < east` and `south < north` with at least [SurveyThumbnailGenerator.MIN_BOUNDS_SPAN_DEG].
 */
internal fun sanitizeThumbnailBounds(raw: LngLatBounds): LngLatBounds {
  val minSpan = SurveyThumbnailGenerator.MIN_BOUNDS_SPAN_DEG
  val maxLat = SurveyThumbnailGenerator.MAX_THUMBNAIL_LAT
  val (west, east) =
    sanitizeAxisRange(raw.west, raw.east, minVal = -180.0, maxVal = 180.0, minSpan = minSpan)
  val (south, north) =
    sanitizeAxisRange(raw.south, raw.north, minVal = -maxLat, maxVal = maxLat, minSpan = minSpan)
  return LngLatBounds(
    west = west.roundTo(6),
    south = south.roundTo(6),
    east = east.roundTo(6),
    north = north.roundTo(6),
  )
}

private fun sanitizeAxisRange(
  rawLo: Double,
  rawHi: Double,
  minVal: Double,
  maxVal: Double,
  minSpan: Double,
): Pair<Double, Double> {
  val finiteLo = if (rawLo.isFinite()) rawLo else 0.0
  val finiteHi = if (rawHi.isFinite()) rawHi else 0.0
  val clampedLo = finiteLo.coerceIn(minVal, maxVal)
  val clampedHi = finiteHi.coerceIn(minVal, maxVal)
  var lo = if (clampedLo <= clampedHi) clampedLo else clampedHi
  var hi = if (clampedLo <= clampedHi) clampedHi else clampedLo
  if (hi - lo < minSpan) {
    val mid = (lo + hi) / 2.0
    lo = mid - minSpan / 2.0
    hi = mid + minSpan / 2.0
    if (lo < minVal) {
      lo = minVal
      hi = (minVal + minSpan).coerceAtMost(maxVal)
    } else if (hi > maxVal) {
      hi = maxVal
      lo = (maxVal - minSpan).coerceAtLeast(minVal)
    }
  }
  return lo to hi
}

/**
 * Compact RFC 7946 GeoJSON and RFC 3986 percent-encoding helpers for
 * [SurveyThumbnail.staticImageUrl].
 */
internal object MapboxStaticUrlEncoder {
  private const val HEX_DIGITS = "0123456789ABCDEF"
  private const val FEATURE_COLLECTION_PREFIX = """{"type":"FeatureCollection","features":["""
  private const val FEATURE_COLLECTION_SUFFIX = """]}"""
  private const val MIN_FEATURE_VIEWPORT_FRACTION = 0.022
  private const val MAX_VIEWPORT_SPAN_FOR_MIN_EXPANSION_DEG = 60.0
  private const val COMPACT_MULTIPOLYGON_THRESHOLD = 20
  private const val MAX_COMPACT_PLOT_VERTICES = 8

  /**
   * Greedily encodes [features] as an RFC 7946 `FeatureCollection` embedded in
   * `$baseUrl/geojson($encoded)/$viewportSuffix`, keeping the full URL `<= maxUrlLength`. When
   * [features] contains a dense grid of small polygons (`> 20` plots with `<= 8` vertices), groups
   * same-color polygons into compact `MultiPolygon` features and samples evenly across the grid so
   * the full spatial distribution fits within the URL budget. Returns `null` when zero valid
   * features fit.
   */
  fun buildGreedyFeatureCollectionUrl(
    baseUrl: String,
    viewportSuffix: String,
    features: List<SurveyThumbnailFeature>,
    bounds: LngLatBounds? = null,
    maxUrlLength: Int,
  ): String? {
    val encodedPrefix = encodeComponent(FEATURE_COLLECTION_PREFIX)
    val encodedSuffix = encodeComponent(FEATURE_COLLECTION_SUFFIX)
    val encodedComma = "%2C"
    val fixedUrlOverhead =
      baseUrl.length +
        "/geojson(".length +
        encodedPrefix.length +
        encodedSuffix.length +
        ")/".length +
        viewportSuffix.length

    val smallPolygonCount = features.count {
      it.type == GeometryType.POLYGON &&
        stripClosingVertex(it.vertices).size in 3..MAX_COMPACT_PLOT_VERTICES
    }

    if (smallPolygonCount > COMPACT_MULTIPOLYGON_THRESHOLD) {
      val compactUrl =
        buildBestCompactMultiPolygonUrl(
          baseUrl = baseUrl,
          viewportSuffix = viewportSuffix,
          encodedPrefix = encodedPrefix,
          encodedSuffix = encodedSuffix,
          encodedComma = encodedComma,
          fixedUrlOverhead = fixedUrlOverhead,
          features = features,
          bounds = bounds,
          maxUrlLength = maxUrlLength,
        )
      if (compactUrl != null) return compactUrl
    }

    val allEncodedFeatures = features.mapNotNull { feature ->
      buildFeatureGeoJsonOrNull(feature, bounds)?.let(::encodeComponent)
    }
    if (allEncodedFeatures.isEmpty()) return null

    val acceptedEncodedFeatures =
      selectFittingSubset(
        allEncodedFeatures = allEncodedFeatures,
        fixedUrlOverhead = fixedUrlOverhead,
        separatorLength = encodedComma.length,
        maxUrlLength = maxUrlLength,
      )
    if (acceptedEncodedFeatures.isEmpty()) return null

    return assembleCollectionUrl(
      baseUrl = baseUrl,
      viewportSuffix = viewportSuffix,
      encodedPrefix = encodedPrefix,
      encodedSuffix = encodedSuffix,
      encodedComma = encodedComma,
      encodedFeatures = acceptedEncodedFeatures,
    )
  }

  private fun buildBestCompactMultiPolygonUrl(
    baseUrl: String,
    viewportSuffix: String,
    encodedPrefix: String,
    encodedSuffix: String,
    encodedComma: String,
    fixedUrlOverhead: Int,
    features: List<SurveyThumbnailFeature>,
    bounds: LngLatBounds?,
    maxUrlLength: Int,
  ): String? {
    var low = 1
    var high = features.size
    var bestEncodedFeatures: List<String>? = null

    while (low <= high) {
      val mid = (low + high) ushr 1
      val subset = features.sampleEvenly(mid)
      val encodedCandidate = buildCompactFeatureJsonList(subset, bounds).map(::encodeComponent)
      val totalLen =
        fixedUrlOverhead +
          encodedCandidate.sumOf { it.length } +
          (encodedCandidate.size - 1).coerceAtLeast(0) * encodedComma.length
      if (encodedCandidate.isNotEmpty() && totalLen <= maxUrlLength) {
        bestEncodedFeatures = encodedCandidate
        low = mid + 1
      } else {
        high = mid - 1
      }
    }

    val accepted = bestEncodedFeatures ?: return null
    return assembleCollectionUrl(
      baseUrl = baseUrl,
      viewportSuffix = viewportSuffix,
      encodedPrefix = encodedPrefix,
      encodedSuffix = encodedSuffix,
      encodedComma = encodedComma,
      encodedFeatures = accepted,
    )
  }

  private fun buildCompactFeatureJsonList(
    features: List<SurveyThumbnailFeature>,
    bounds: LngLatBounds?,
  ): List<String> {
    val result = mutableListOf<String>()
    val smallPolygonsByColor = LinkedHashMap<String, MutableList<List<Pair<String, String>>>>()

    for (feature in features) {
      val colorCss = formatColorCss(feature.colorHex)
      val isSmallPolygon =
        feature.type == GeometryType.POLYGON &&
          stripClosingVertex(feature.vertices).size in 3..MAX_COMPACT_PLOT_VERTICES
      if (isSmallPolygon) {
        val scaledVertices = ensureMinGeographicSpan(feature.vertices, bounds)
        val openRing = simplifiedOpenPolygonRingOrNull(scaledVertices)
        if (openRing != null) {
          val closedRing = openRing + openRing.first()
          smallPolygonsByColor.getOrPut(colorCss) { mutableListOf() }.add(closedRing)
        }
      } else {
        buildFeatureGeoJsonOrNull(feature, bounds)?.let(result::add)
      }
    }

    for ((colorCss, closedRings) in smallPolygonsByColor) {
      if (closedRings.size == 1) {
        result.add(buildClosedRingPolygonFeature(closedRings.single(), colorCss))
      } else if (closedRings.size > 1) {
        result.add(buildMultiPolygonFeature(closedRings, colorCss))
      }
    }
    return result
  }

  private fun selectFittingSubset(
    allEncodedFeatures: List<String>,
    fixedUrlOverhead: Int,
    separatorLength: Int,
    maxUrlLength: Int,
  ): List<String> {
    fun fits(candidate: List<String>): Boolean {
      if (candidate.isEmpty()) return false
      val contentLen = candidate.sumOf { it.length } + (candidate.size - 1) * separatorLength
      return fixedUrlOverhead + contentLen <= maxUrlLength
    }
    if (fits(allEncodedFeatures)) return allEncodedFeatures

    var low = 1
    var high = allEncodedFeatures.size - 1
    var best: List<String> = emptyList()
    while (low <= high) {
      val mid = (low + high) ushr 1
      val candidate = allEncodedFeatures.sampleEvenly(mid)
      if (fits(candidate)) {
        best = candidate
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    return best
  }

  private fun assembleCollectionUrl(
    baseUrl: String,
    viewportSuffix: String,
    encodedPrefix: String,
    encodedSuffix: String,
    encodedComma: String,
    encodedFeatures: List<String>,
  ): String {
    val encodedFeaturesLength =
      encodedFeatures.sumOf { it.length } +
        (encodedFeatures.size - 1).coerceAtLeast(0) * encodedComma.length
    val encodedCollection =
      buildString(encodedPrefix.length + encodedFeaturesLength + encodedSuffix.length) {
        append(encodedPrefix)
        encodedFeatures.forEachIndexed { index, encoded ->
          if (index > 0) append(encodedComma)
          append(encoded)
        }
        append(encodedSuffix)
      }
    return "$baseUrl/geojson($encodedCollection)/$viewportSuffix"
  }

  internal fun buildFeatureGeoJsonOrNull(
    feature: SurveyThumbnailFeature,
    bounds: LngLatBounds? = null,
  ): String? {
    val colorCss = formatColorCss(feature.colorHex)
    return when (feature.type) {
      GeometryType.POLYGON ->
        buildPolygonFeatureOrNull(ensureMinGeographicSpan(feature.vertices, bounds), colorCss)
      GeometryType.LINE_STRING ->
        buildLineFeatureOrNull(ensureMinGeographicSpan(feature.vertices, bounds), colorCss)
      GeometryType.POINT -> buildPointFeatureOrNull(feature.vertices, colorCss)
    }
  }

  private fun ensureMinGeographicSpan(
    vertices: List<LatLng>,
    bounds: LngLatBounds?,
  ): List<LatLng> {
    if (bounds == null || vertices.size < 2) return vertices
    val viewportSpan = max(bounds.east - bounds.west, bounds.north - bounds.south)
    if (
      !viewportSpan.isFinite() || viewportSpan !in 1e-4..MAX_VIEWPORT_SPAN_FOR_MIN_EXPANSION_DEG
    ) {
      return vertices
    }
    val minLat = vertices.minOf { it.latitude }
    val maxLat = vertices.maxOf { it.latitude }
    val minLng = vertices.minOf { it.longitude }
    val maxLng = vertices.maxOf { it.longitude }
    val featureSpan = max(maxLat - minLat, maxLng - minLng)
    val targetMinSpan = viewportSpan * MIN_FEATURE_VIEWPORT_FRACTION
    if (featureSpan <= 1e-7 || featureSpan >= targetMinSpan) return vertices

    val centerLat = (minLat + maxLat) / 2.0
    val centerLng = (minLng + maxLng) / 2.0
    val scale = targetMinSpan / featureSpan
    return vertices.map { v ->
      LatLng(
        latitude =
          (centerLat + (v.latitude - centerLat) * scale).coerceIn(
            -SurveyThumbnailGenerator.MAX_THUMBNAIL_LAT,
            SurveyThumbnailGenerator.MAX_THUMBNAIL_LAT,
          ),
        longitude = (centerLng + (v.longitude - centerLng) * scale).coerceIn(-180.0, 180.0),
      )
    }
  }

  private fun buildPolygonFeatureOrNull(vertices: List<LatLng>, colorCss: String): String? {
    val openRing = simplifiedOpenPolygonRingOrNull(vertices) ?: return null
    val closedRing = openRing + openRing.first()
    return buildClosedRingPolygonFeature(closedRing, colorCss)
  }

  private fun buildClosedRingPolygonFeature(
    closedRing: List<Pair<String, String>>,
    colorCss: String,
  ): String = buildString {
    append("""{"type":"Feature","properties":{"stroke":"""")
    append(colorCss)
    append("""","stroke-width":2,"stroke-opacity":0.95,"fill":"""")
    append(colorCss)
    append("""","fill-opacity":0.32},"geometry":{"type":"Polygon","coordinates":[""")
    appendCoordList(closedRing)
    append("""]}}""")
  }

  private fun buildMultiPolygonFeature(
    closedRings: List<List<Pair<String, String>>>,
    colorCss: String,
  ): String = buildString {
    append("""{"type":"Feature","properties":{"stroke":"""")
    append(colorCss)
    append("""","stroke-width":2,"stroke-opacity":0.95,"fill":"""")
    append(colorCss)
    append("""","fill-opacity":0.32},"geometry":{"type":"MultiPolygon","coordinates":[""")
    closedRings.forEachIndexed { index, ring ->
      if (index > 0) append(',')
      append('[')
      appendCoordList(ring)
      append(']')
    }
    append("""]}}""")
  }

  private fun simplifiedOpenPolygonRingOrNull(vertices: List<LatLng>): List<Pair<String, String>>? {
    val openVertices = stripClosingVertex(vertices)
    val simplified =
      if (openVertices.size >= 3) {
        SurveyAreaGeometry.displayParts(
            listOf(openVertices),
            maxVerticesPerPart = SurveyThumbnail.MAX_STATIC_FEATURE_VERTICES,
          )
          .firstOrNull()
          ?.let { limitVertices(it, SurveyThumbnail.MAX_STATIC_FEATURE_VERTICES) }
      } else {
        null
      } ?: return null
    val distinctRounded = distinctRoundedCoords(simplified)
    if (distinctRounded.size >= 2 && distinctRounded.first() == distinctRounded.last()) {
      distinctRounded.removeAt(distinctRounded.lastIndex)
    }
    return distinctRounded.takeIf { it.size >= 3 }
  }

  private fun buildLineFeatureOrNull(vertices: List<LatLng>, colorCss: String): String? {
    if (vertices.size < 2) return null
    val simplified = limitVertices(vertices, SurveyThumbnail.MAX_STATIC_FEATURE_VERTICES)
    val distinctRounded = distinctRoundedCoords(simplified)
    if (distinctRounded.size < 2) return null
    return buildString {
      append("""{"type":"Feature","properties":{"stroke":"""")
      append(colorCss)
      append(
        """","stroke-width":2.5,"stroke-opacity":0.95},"geometry":{"type":"LineString","coordinates":"""
      )
      appendCoordList(distinctRounded)
      append("""}}""")
    }
  }

  private fun buildPointFeatureOrNull(vertices: List<LatLng>, colorCss: String): String? {
    val first = vertices.firstOrNull() ?: return null
    val (lng, lat) = roundedLngLat(first)
    return buildString {
      append("""{"type":"Feature","properties":{"marker-size":"small","marker-color":"""")
      append(colorCss)
      append(""""},"geometry":{"type":"Point","coordinates":[""")
      append(lng)
      append(',')
      append(lat)
      append("""]}}""")
    }
  }

  private fun distinctRoundedCoords(vertices: List<LatLng>): MutableList<Pair<String, String>> {
    val distinct = mutableListOf<Pair<String, String>>()
    for (vertex in vertices) {
      val pair = roundedLngLat(vertex)
      if (distinct.lastOrNull() != pair) {
        distinct.add(pair)
      }
    }
    return distinct
  }

  private fun roundedLngLat(vertex: LatLng): Pair<String, String> =
    formatCoord(vertex.longitude.coerceIn(-180.0, 180.0), decimals = 5) to
      formatCoord(
        vertex.latitude.coerceIn(
          -SurveyThumbnailGenerator.MAX_THUMBNAIL_LAT,
          SurveyThumbnailGenerator.MAX_THUMBNAIL_LAT,
        ),
        decimals = 5,
      )

  private fun limitVertices(vertices: List<LatLng>, maxVertices: Int): List<LatLng> {
    if (vertices.size <= maxVertices) return vertices
    if (maxVertices <= 1) return listOf(vertices.first())
    return List(maxVertices) { index ->
      val sourceIndex = ((index.toLong() * (vertices.size - 1)) / (maxVertices - 1)).toInt()
      vertices[sourceIndex]
    }
  }

  private fun formatColorCss(colorHex: Long): String {
    val rgb = (colorHex and 0xFFFFFFL).toString(16).padStart(6, '0').lowercase()
    return "#$rgb"
  }

  private fun StringBuilder.appendCoordList(coords: List<Pair<String, String>>) {
    append('[')
    coords.forEachIndexed { index, (lng, lat) ->
      if (index > 0) append(',')
      append('[')
      append(lng)
      append(',')
      append(lat)
      append(']')
    }
    append(']')
  }

  /**
   * Formats [value] rounded to at most [decimals] fractional digits without trailing zeros or `-0`.
   */
  fun formatCoord(value: Double, decimals: Int): String {
    if (!value.isFinite()) return "0"
    var factor = 1L
    repeat(decimals) { factor *= 10L }
    val scaled = round(abs(value) * factor).toLong()
    if (scaled == 0L) return "0"
    val whole = scaled / factor
    val frac = scaled % factor
    val sign = if (value < 0.0) "-" else ""
    if (frac == 0L || decimals == 0) return "$sign$whole"
    val fracTrimmed = frac.toString().padStart(decimals, '0').trimEnd('0')
    return "$sign$whole.$fracTrimmed"
  }

  /**
   * Percent-encodes every byte outside RFC 3986 unreserved characters (`A-Za-z0-9-_.~`) so GeoJSON
   * punctuation (`[`, `]`, `{`, `}`, `"`, `:`, `,`, `#`, `(`, `)`, spaces) is safely embedded in a
   * Mapbox `geojson(...)` path overlay.
   */
  fun encodeComponent(value: String): String {
    val bytes = value.encodeToByteArray()
    val out = StringBuilder(bytes.size * 2)
    for (b in bytes) {
      val code = b.toInt() and 0xFF
      if (isUnreservedAscii(code)) {
        out.append(code.toChar())
      } else {
        out.append('%')
        out.append(HEX_DIGITS[code ushr 4])
        out.append(HEX_DIGITS[code and 0x0F])
      }
    }
    return out.toString()
  }

  private fun isUnreservedAscii(code: Int): Boolean =
    code in 'A'.code..'Z'.code ||
      code in 'a'.code..'z'.code ||
      code in '0'.code..'9'.code ||
      code == '-'.code ||
      code == '_'.code ||
      code == '.'.code ||
      code == '~'.code
}

/**
 * Pure Web Mercator (`EPSG:3857`) projection and XYZ slippy-map tile math used to project framed
 * bounds and vector AOI/feature overlays proportionally inside [SurveyMapThumbnail].
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

  /** Continuous Web Mercator zoom that fits [bounds] inside a logical thumbnail viewport. */
  fun continuousZoomForBounds(bounds: LngLatBounds): Double {
    val dx = abs(lngToNormX(bounds.east) - lngToNormX(bounds.west))
    val dy = abs(latToNormY(bounds.south) - latToNormY(bounds.north))
    val spanNorm = max(max(dx, dy), 1e-6)
    val zoom = ln(160.0 / (256.0 * spanNorm)) / ln(2.0)
    return (zoom.coerceIn(2.0, 17.5) * 100.0).roundToInt() / 100.0
  }

  /**
   * Computes the covering XYZ raster tiles for [bounds] at an integer zoom chosen so the viewport
   * spans at most [maxTilesPerAxis] tiles along each axis.
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

/**
 * Returns at most [maxCount] elements sampled evenly across this list (always including the first
 * and last elements when [maxCount] `>= 2`) so spatial distributions and bounding boxes are
 * preserved when subsampling large datasets.
 */
fun <T> List<T>.sampleEvenly(maxCount: Int): List<T> {
  if (maxCount <= 0) return emptyList()
  if (size <= maxCount) return this
  if (maxCount == 1) return listOf(first())
  val lastIndex = size - 1
  val step = lastIndex.toDouble() / (maxCount - 1).toDouble()
  return List(maxCount) { i -> this[(i * step).roundToInt().coerceIn(0, lastIndex)] }
}
