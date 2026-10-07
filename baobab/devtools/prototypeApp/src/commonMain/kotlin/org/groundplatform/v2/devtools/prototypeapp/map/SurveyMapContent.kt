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
package org.groundplatform.v2.devtools.prototypeapp.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.core.forms.ui.MapDrawingKind
import org.groundplatform.v2.devtools.prototypeapp.EntityGeometryKind
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.BasemapType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImagerySource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapFeatureCluster
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.StraightLineNavigationState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPlaceItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.formatHexColorCss
import org.groundplatform.v2.devtools.prototypeapp.geometryKind
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyMapUiState
import org.groundplatform.v2.map.FeatureFilter
import org.groundplatform.v2.map.GeoJsonSource
import org.groundplatform.v2.map.Geometry
import org.groundplatform.v2.map.LatLng
import org.groundplatform.v2.map.MapContent
import org.groundplatform.v2.map.MapFeature
import org.groundplatform.v2.map.MapIcon
import org.groundplatform.v2.map.MapLayer
import org.groundplatform.v2.map.MapMarker
import org.groundplatform.v2.map.MarkerAnchor
import org.groundplatform.v2.map.StyleValue

/** What a survey map shows: native [content] plus the Compose UI for each marker, by id. */
internal data class SurveyMap(val content: MapContent, val markers: Map<String, SurveyMarker>)

/**
 * A geometry being drawn on the map for a form question (web dashboard): the [vertices] clicked so
 * far, in order, and the [kind] they are meant to form. Drawn above the entities in the draft
 * style; see [SurveyMapContent.build].
 */
data class DraftGeometry(val kind: MapDrawingKind, val vertices: List<LatLng>) {
  /** True once there are enough vertices for a valid geometry of [kind]. */
  val isComplete: Boolean
    get() = vertices.size >= kind.minVertices
}

/**
 * A geometry answer held by the form open in the web dashboard panel (any `geopoint`, `geotrace`,
 * or `geoshape` field with a value): the question's canonical [path] and [title], the geometry
 * [kind], and its [vertices]. Drawn above the entities in the "in-flow" style, between them and the
 * draft being drawn; clicking one jumps to its question. See [SurveyMapContent.build].
 */
data class FormGeometryOverlay(
  val path: String,
  val title: String,
  val kind: MapDrawingKind,
  val vertices: List<LatLng>,
)

/** Compose-drawn map annotations; see `SurveyMapMarkers.kt` for their UI. */
internal sealed interface SurveyMarker {
  /**
   * Name pill at the middle of a line or polygon, filled with its layer's [color], with the
   * geometry glyph before the label and a [status] badge after it. Layer pin icons are only for
   * points.
   */
  data class GeometryPill(
    val label: String,
    val kind: EntityGeometryKind,
    val color: Color,
    val selected: Boolean,
    val pending: Boolean,
    val status: StatusBadge? = null,
  ) : SurveyMarker

  /** Name chip just below a point's pin, filled with its layer's [color], with a [status] badge. */
  data class PinLabel(
    val label: String,
    val color: Color,
    val selected: Boolean,
    val pending: Boolean,
    val status: StatusBadge? = null,
  ) : SurveyMarker

  /** A feature's workflow status (`marker-symbol`) on a disc of its status (`marker-color`). */
  data class StatusBadge(val symbol: String, val color: Color)

  /** A zoomed-out group of features, with a chip per marker symbol. */
  data class ClusterBalloon(
    val header: String,
    val chips: List<ClusterChip>,
    val selected: Boolean,
  ) : SurveyMarker

  /** [symbol] is empty for features without a marker symbol. */
  data class ClusterChip(val symbol: String, val color: Color, val count: Int)

  /** Label under the user's location. */
  data class UserChip(val text: String) : SurveyMarker

  /** Distance and bearing halfway along the navigation line. */
  data class NavigationPill(val text: String) : SurveyMarker

  /** A place picked from search; tapping it dismisses it. */
  data class Place(val name: String) : SurveyMarker

  /**
   * The question title over a geometry answer of the form open in the web dashboard panel; tapping
   * it jumps to the question.
   */
  data class FormQuestion(val title: String, val kind: MapDrawingKind) : SurveyMarker
}

/** Feature and marker ids on survey maps, and how taps map back to domain ids. */
internal object SurveyMapIds {
  private const val ENTITY = "entity:"
  private const val CLUSTER = "cluster:"
  private const val PLACE = "place:"
  private const val FORM_GEOMETRY = "form-geometry:"
  const val USER = "user"
  const val NAVIGATION = "navigation"

  fun entity(id: String) = ENTITY + id

  fun vertex(entityId: String, index: Int) = "$ENTITY$entityId#v$index"

  fun cluster(id: String) = CLUSTER + id

  fun place(id: String) = PLACE + id

  /** Feature and marker id of the open form's geometry answer at [path] (e.g. `/data/plot`). */
  fun formGeometry(path: String) = FORM_GEOMETRY + path

  fun formGeometryVertex(path: String, index: Int) = "$FORM_GEOMETRY$path#v$index"

  /** The entity a feature or marker id belongs to, if any (vertices map to their entity). */
  fun entityIdOf(id: String): String? =
    if (id.startsWith(ENTITY)) id.removePrefix(ENTITY).substringBefore('#') else null

  fun clusterIdOf(id: String): String? =
    if (id.startsWith(CLUSTER)) id.removePrefix(CLUSTER) else null

  fun isPlace(id: String) = id.startsWith(PLACE)

  /** The question path a form-geometry feature or marker id belongs to, if any. */
  fun formGeometryPathOf(id: String): String? =
    if (id.startsWith(FORM_GEOMETRY)) id.removePrefix(FORM_GEOMETRY).substringBefore('#') else null
}

/**
 * Builds [SurveyMap]s from app state. Pure functions of their inputs: the platform renderers and
 * the Compose marker layer only draw what these return.
 */
internal object SurveyMapContent {
  const val ENTITY_SOURCE = "survey-entities"
  const val OVERLAY_SOURCE = "survey-overlay"

  /** Most cluster balloons drawn at once, to keep the marker layer light. */
  private const val MAX_CLUSTER_MARKERS = 60

  /** The main survey map (mobile and web dashboard). */
  fun main(state: PrototypeAppState, showNavigation: Boolean): SurveyMap =
    main(
      uiState = state.surveyMapUiState,
      pendingIds = state.pendingUploadEntityIds,
      showNavigation = showNavigation,
      draftGeometry = state.webMapDraftGeometry,
      formGeometries = state.webFormGeometries,
    )

  /**
   * The main survey map (mobile and web dashboard) from the map viewport's [uiState].
   *
   * @param pendingIds entities with pending uploads, drawn in the pending style.
   * @param draftGeometry the geometry being drawn for a form question, if any (web dashboard).
   * @param formGeometries geometry answers held by the open form, if any (web dashboard).
   */
  fun main(
    uiState: SurveyMapUiState,
    pendingIds: Set<String>,
    showNavigation: Boolean,
    draftGeometry: DraftGeometry? = null,
    formGeometries: List<FormGeometryOverlay> = emptyList(),
  ): SurveyMap =
    build(
      anchor = uiState.anchor,
      basemapType = uiState.selectedBasemapType,
      showOfflineSector = uiState.isOfflineBasemapVisible,
      entities = uiState.visibleMapEntities,
      selectedEntityId = uiState.selectedEntity?.id,
      pendingIds = pendingIds,
      clusters = if (uiState.isMapClusteringActive) uiState.mapFeatureClusters else null,
      selectedClusterId = uiState.selectedClusterId,
      clusterHeader = { uiState.formatClusterSitesCountLabel(it.siteCount) },
      navigation = if (showNavigation) uiState.activeNavigation else null,
      userGps = uiState.userGpsNormalizedX to uiState.userGpsNormalizedY,
      isFollowingUser = uiState.isCameraFollowingUser,
      place = uiState.selectedPlace,
      layers = uiState.mapLayers,
      draftGeometry = draftGeometry,
      formGeometries = formGeometries,
      imagerySources = uiState.enabledImagerySources,
    )

  /** The map in a form's entity-reference step, listing the form's candidate entities. */
  fun entityRefForm(state: PrototypeAppState, form: FormPreviewItem): SurveyMap =
    entityRefForm(
      uiState = state.surveyMapUiState,
      entities = state.allDatasetEntitiesForForm(form),
      selectedEntityId = state.activeDataCollectionEntityId,
      pendingIds = state.pendingUploadEntityIds,
    )

  /** The map in a form's entity-reference step, showing the form's candidate [entities]. */
  fun entityRefForm(
    uiState: SurveyMapUiState,
    entities: List<GeospatialEntityItem>,
    selectedEntityId: String?,
    pendingIds: Set<String>,
  ): SurveyMap =
    build(
      anchor = uiState.anchor,
      basemapType = uiState.selectedBasemapType,
      showOfflineSector = uiState.isOfflineBasemapVisible,
      entities = entities,
      selectedEntityId = selectedEntityId,
      pendingIds = pendingIds,
      clusters = null,
      selectedClusterId = null,
      clusterHeader = { "" },
      navigation = null,
      userGps = uiState.userGpsNormalizedX to uiState.userGpsNormalizedY,
      isFollowingUser = uiState.isCameraFollowingUser,
      place = null,
      layers = uiState.mapLayers,
      imagerySources = uiState.enabledImagerySources,
    )

  /** The map behind a GeoPoint question. */
  fun geoPointForm(state: PrototypeAppState, isFollowingUser: Boolean): SurveyMap =
    geoPointForm(
      uiState = state.surveyMapUiState,
      selectedEntityId = state.activeDataCollectionEntityId,
      pendingIds = state.pendingUploadEntityIds,
      isFollowingUser = isFollowingUser,
    )

  /** The map behind a GeoPoint question, highlighting the form's [selectedEntityId]. */
  fun geoPointForm(
    uiState: SurveyMapUiState,
    selectedEntityId: String?,
    pendingIds: Set<String>,
    isFollowingUser: Boolean,
  ): SurveyMap =
    build(
      anchor = uiState.anchor,
      basemapType = uiState.selectedBasemapType,
      showOfflineSector = uiState.isOfflineBasemapVisible,
      entities = uiState.visibleMapEntities,
      selectedEntityId = selectedEntityId,
      pendingIds = pendingIds,
      clusters = null,
      selectedClusterId = null,
      clusterHeader = { "" },
      navigation = null,
      userGps = uiState.userGpsNormalizedX to uiState.userGpsNormalizedY,
      isFollowingUser = isFollowingUser,
      place = null,
      layers = uiState.mapLayers,
      imagerySources = uiState.enabledImagerySources,
    )

  /**
   * @param clusters the clusters to draw instead of individual features, or `null` when the map
   *   isn't clustering.
   * @param layers the survey's map layers; each feature is drawn in its layer's color, and points
   *   show the layer's icon.
   * @param draftGeometry a geometry being drawn for a form question, drawn above everything else in
   *   the draft style: its vertices, the line through them, and (for a polygon with at least 3
   *   vertices) the closed shape.
   * @param formGeometries the geometry answers already held by the open form, drawn in the in-flow
   *   style above the entities and below [draftGeometry], each labelled with its question title.
   * @param imagerySources enabled organization imagery sources (`"All users"` and survey-specific
   *   organization XYZ tile URLs) rendered on top of the selected basemap.
   */
  fun build(
    anchor: SurveyMapAnchor,
    basemapType: BasemapType,
    showOfflineSector: Boolean,
    entities: List<GeospatialEntityItem>,
    selectedEntityId: String?,
    pendingIds: Set<String>,
    clusters: List<MapFeatureCluster>?,
    selectedClusterId: String?,
    clusterHeader: (MapFeatureCluster) -> String,
    navigation: StraightLineNavigationState?,
    userGps: Pair<Float, Float>,
    isFollowingUser: Boolean,
    place: SurveyPlaceItem?,
    layers: List<MapLayerItem> = emptyList(),
    draftGeometry: DraftGeometry? = null,
    formGeometries: List<FormGeometryOverlay> = emptyList(),
    imagerySources: List<ImagerySource> = emptyList(),
  ): SurveyMap {
    val satellite = basemapType == BasemapType.SATELLITE
    fun at(nx: Float, ny: Float) = anchor.toLatLng(nx.toDouble(), ny.toDouble())
    val layersById = layers.associateBy { it.id }

    val palette = linkedMapOf<String, Color>()
    fun colorKey(hex: Long): String {
      val key = "#" + (hex and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
      palette.getOrPut(key) { Color(0xFF000000 or (hex and 0xFFFFFF)) }
      return key
    }

    val entityFeatures = mutableListOf<MapFeature>()
    val icons = linkedMapOf<String, MapIcon>()
    val entityMarkers = mutableListOf<Pair<MapMarker, SurveyMarker>>()
    // Selected last, so it draws above the rest in its layers and among the markers.
    val ordered =
      if (clusters != null) emptyList() else entities.sortedBy { it.id == selectedEntityId }
    for (entity in ordered) {
      val geometry = EntityGeometry.of(entity, anchor) ?: continue
      val selected = entity.id == selectedEntityId
      val pending = entity.id in pendingIds
      val generated = EntityGeometry.isGenerated(entity)
      val variant =
        when {
          selected && pending -> Variant.SELECTED_PENDING
          selected -> Variant.SELECTED
          pending -> Variant.PENDING
          generated -> Variant.GENERATED
          else -> Variant.NORMAL
        }
      val layer = layersById[entity.layerId]
      val layerColor = entity.mapColorHex(layer)
      val iconName = layer?.iconName
      val stroke = if (selected && satellite) colorKey(0xFFFFFF) else colorKey(layerColor)
      val common = mapOf(PROP_VARIANT to variant, PROP_STROKE to stroke)
      when (geometry) {
        is Geometry.Point -> {
          val pinColor = formatHexColorCss(layerColor)
          val pinId = GroundPin.iconId(pinColor, iconName, pending)
          icons.getOrPut(pinId) { MapIcon(pinId, GroundPin.svg(pinColor, iconName, pending)) }
          entityFeatures +=
            MapFeature(
              SurveyMapIds.entity(entity.id),
              geometry,
              common + mapOf(PROP_KIND to KIND_POINT, PROP_PIN to pinId),
            )
        }
        is Geometry.LineString,
        is Geometry.Polygon -> {
          val isPolygon = geometry is Geometry.Polygon
          entityFeatures +=
            MapFeature(
              SurveyMapIds.entity(entity.id),
              geometry,
              common +
                mapOf(
                  PROP_KIND to if (isPolygon) KIND_POLYGON else KIND_LINE,
                  PROP_FILL to colorKey(layerColor),
                ),
            )
          if (!generated || selected) {
            val size = (if (isPolygon) "polygon" else "line") + if (selected) "-selected" else ""
            EntityGeometry.vertices(geometry).forEachIndexed { i, vertex ->
              entityFeatures +=
                MapFeature(
                  SurveyMapIds.vertex(entity.id, i),
                  Geometry.Point(vertex),
                  common + mapOf(PROP_KIND to KIND_VERTEX, PROP_VERTEX_SIZE to size),
                )
            }
          }
        }
      }
      if (generated && !selected) continue
      val label = entity.label.substringBefore(" •").ifBlank { entity.id }
      val color = Color(layerColor)
      val status =
        if (entity.hasMarkerSymbol) {
          SurveyMarker.StatusBadge(entity.markerSymbol, Color(entity.markerColorHex))
        } else {
          null
        }
      entityMarkers +=
        if (geometry is Geometry.Point) {
          MapMarker(
            SurveyMapIds.entity(entity.id),
            geometry.position,
            MarkerAnchor.TOP,
            DpOffset(0.dp, 6.dp),
          ) to SurveyMarker.PinLabel(label, color, selected, pending, status)
        } else {
          MapMarker(
            SurveyMapIds.entity(entity.id),
            at(entity.normalizedX, entity.normalizedY),
            MarkerAnchor.CENTER,
          ) to
            SurveyMarker.GeometryPill(
              label,
              entity.geometryKind,
              color,
              selected,
              pending,
              status,
            )
        }
    }

    val overlayFeatures = mutableListOf<MapFeature>()
    if (showOfflineSector && clusters == null) {
      val corners = listOf(0.05f to 0.14f, 0.95f to 0.14f, 0.95f to 0.88f, 0.05f to 0.88f)
      overlayFeatures +=
        MapFeature(
          "offline-sector",
          Geometry.LineString((corners + corners.first()).map { (x, y) -> at(x, y) }),
          mapOf(PROP_KIND to KIND_SECTOR),
        )
    }
    val otherMarkers = mutableListOf<Pair<MapMarker, SurveyMarker>>()
    if (navigation != null) {
      val v = navigation.vector
      val from = at(v.fromNormalizedX, v.fromNormalizedY)
      val to = at(v.toNormalizedX, v.toNormalizedY)
      overlayFeatures +=
        MapFeature(
          "navigation-line",
          Geometry.LineString(listOf(from, to)),
          mapOf(PROP_KIND to KIND_NAV),
        )
      overlayFeatures +=
        MapFeature("navigation-target", Geometry.Point(to), mapOf(PROP_KIND to KIND_NAV_TARGET))
      val distance = "${v.formattedDistance} • ${v.bearingDegrees}° ${v.cardinalDirection}"
      otherMarkers +=
        MapMarker(
          SurveyMapIds.NAVIGATION,
          at((v.fromNormalizedX + v.toNormalizedX) / 2, (v.fromNormalizedY + v.toNormalizedY) / 2),
          MarkerAnchor.CENTER,
          DpOffset(0.dp, (-10).dp),
        ) to SurveyMarker.NavigationPill((if (v.hasArrived) "✓ Arrived • " else "➤ ") + distance)
    }
    if (draftGeometry != null) overlayFeatures += draftFeatures(draftGeometry)
    for (formGeometry in formGeometries) {
      overlayFeatures += formGeometryFeatures(formGeometry)
      formGeometryMarker(formGeometry)?.let { otherMarkers += it }
    }

    val clusterMarkers =
      clusters
        .orEmpty()
        .take(MAX_CLUSTER_MARKERS)
        .sortedBy { it.id == selectedClusterId }
        .map { cluster ->
          MapMarker(
            SurveyMapIds.cluster(cluster.id),
            at(cluster.normalizedX, cluster.normalizedY),
          ) to
            SurveyMarker.ClusterBalloon(
              header = clusterHeader(cluster),
              chips =
                cluster.siteSymbolGroups.map { group ->
                  SurveyMarker.ClusterChip(
                    symbol = if (group.isNoSymbolGroup) "" else group.markerSymbol,
                    color = Color(0xFF000000 or (group.colorHex and 0xFFFFFF)),
                    count = group.count,
                  )
                },
              selected = cluster.id == selectedClusterId,
            )
        }

    val user = at(userGps.first, userGps.second)
    val userText =
      when {
        navigation != null ->
          "⊕ You • ${navigation.vector.bearingDegrees}° ${navigation.vector.cardinalDirection}"
        isFollowingUser -> "⊕ You (Centered)"
        else -> "⊕ You (GPS)"
      }
    otherMarkers +=
      MapMarker(SurveyMapIds.USER, user, MarkerAnchor.TOP, DpOffset(0.dp, 12.dp)) to
        SurveyMarker.UserChip(userText)
    if (place != null) {
      otherMarkers +=
        MapMarker(
          SurveyMapIds.place(place.id),
          LatLng(place.latitude, place.longitude),
          MarkerAnchor.CENTER,
        ) to SurveyMarker.Place(place.name.ifBlank { "Place" })
    }

    val allMarkers = entityMarkers + clusterMarkers + otherMarkers
    val baseTiles = if (satellite) SurveyBasemaps.Satellite else SurveyBasemaps.Streets
    val content =
      MapContent(
        basemap = SurveyBasemaps.withImagerySources(baseTiles, imagerySources),
        sources =
          listOf(
            GeoJsonSource(OVERLAY_SOURCE, overlayFeatures),
            GeoJsonSource(ENTITY_SOURCE, entityFeatures),
          ),
        layers = layers(satellite, palette, icons.keys.toList()),
        icons = icons.values.toList(),
        markers = allMarkers.map { it.first },
        userLocation = user,
      )
    return SurveyMap(content, allMarkers.associate { (marker, ui) -> marker.id to ui })
  }

  /**
   * Overlay features for a [draft] being drawn: every vertex, the line through them (also the
   * outline of a polygon that is still short of 3 vertices), and the closed polygon once complete.
   */
  internal fun draftFeatures(draft: DraftGeometry): List<MapFeature> {
    val vertices = draft.vertices
    val features = mutableListOf<MapFeature>()
    when {
      draft.kind == MapDrawingKind.POLYGON && vertices.size >= 3 ->
        features +=
          MapFeature(
            DRAFT_SHAPE_ID,
            Geometry.Polygon(listOf(vertices)),
            mapOf(PROP_KIND to KIND_DRAFT_POLYGON),
          )
      draft.kind != MapDrawingKind.POINT && vertices.size >= 2 ->
        features +=
          MapFeature(
            DRAFT_SHAPE_ID,
            Geometry.LineString(vertices),
            mapOf(PROP_KIND to KIND_DRAFT_LINE),
          )
    }
    vertices.forEachIndexed { i, vertex ->
      features +=
        MapFeature(draftVertexId(i), Geometry.Point(vertex), mapOf(PROP_KIND to KIND_DRAFT_VERTEX))
    }
    return features
  }

  /** Feature id of the draft's line or polygon. */
  const val DRAFT_SHAPE_ID = "draft-shape"

  /** Feature id of the draft's [index]th vertex. */
  fun draftVertexId(index: Int) = "draft-vertex-$index"

  /**
   * Overlay features for a geometry answer of the open form: the shape (a point, a line, or a
   * closed polygon; a polygon short of 3 vertices draws as a line) plus a circle per vertex of
   * lines and polygons. All ids map back to the question with [SurveyMapIds.formGeometryPathOf].
   */
  internal fun formGeometryFeatures(overlay: FormGeometryOverlay): List<MapFeature> {
    val vertices = overlay.vertices
    if (vertices.isEmpty()) return emptyList()
    val shapeId = SurveyMapIds.formGeometry(overlay.path)
    val features = mutableListOf<MapFeature>()
    when {
      overlay.kind == MapDrawingKind.POINT ->
        features +=
          MapFeature(shapeId, Geometry.Point(vertices.first()), mapOf(PROP_KIND to KIND_FORM_POINT))
      overlay.kind == MapDrawingKind.POLYGON && vertices.size >= 3 ->
        features +=
          MapFeature(
            shapeId,
            Geometry.Polygon(listOf(vertices)),
            mapOf(PROP_KIND to KIND_FORM_POLYGON),
          )
      vertices.size >= 2 ->
        features +=
          MapFeature(shapeId, Geometry.LineString(vertices), mapOf(PROP_KIND to KIND_FORM_LINE))
    }
    if (overlay.kind != MapDrawingKind.POINT) {
      vertices.forEachIndexed { i, vertex ->
        features +=
          MapFeature(
            SurveyMapIds.formGeometryVertex(overlay.path, i),
            Geometry.Point(vertex),
            mapOf(PROP_KIND to KIND_FORM_VERTEX),
          )
      }
    }
    return features
  }

  /**
   * The question-title label of a form geometry answer: under a point, at the vertex centroid of a
   * line or polygon. `null` for an empty answer.
   */
  internal fun formGeometryMarker(overlay: FormGeometryOverlay): Pair<MapMarker, SurveyMarker>? {
    val vertices = overlay.vertices
    if (vertices.isEmpty()) return null
    val id = SurveyMapIds.formGeometry(overlay.path)
    val label = SurveyMarker.FormQuestion(overlay.title.ifBlank { overlay.path }, overlay.kind)
    val marker =
      if (overlay.kind == MapDrawingKind.POINT) {
        MapMarker(id, vertices.first(), MarkerAnchor.TOP, DpOffset(0.dp, 8.dp))
      } else {
        val center =
          LatLng(
            vertices.sumOf { it.latitude } / vertices.size,
            vertices.sumOf { it.longitude } / vertices.size,
          )
        MapMarker(id, center, MarkerAnchor.CENTER)
      }
    return marker to label
  }

  /** Always the same layers, so content changes only update source data and paint. */
  private fun layers(
    satellite: Boolean,
    palette: Map<String, Color>,
    pinIds: List<String>,
  ): List<MapLayer> {
    val strokeColor = StyleValue.Match(PROP_STROKE, palette, Color.Gray)
    val cyan = Color(0xFF00E5FF)
    return listOf(
      MapLayer.Line(
        id = "offline-sector",
        sourceId = OVERLAY_SOURCE,
        filter = kind(KIND_SECTOR),
        color = StyleValue.Constant(if (satellite) Color(0xFF8BD6B1) else Color(0xFF1E6F50)),
        width = StyleValue.Constant(2.dp),
        opacity = StyleValue.Constant(0.75f),
        dashPattern = listOf(3f, 2f),
      ),
      MapLayer.Circle(
        id = "navigation-target",
        sourceId = OVERLAY_SOURCE,
        filter = kind(KIND_NAV_TARGET),
        color = StyleValue.Constant(cyan.copy(alpha = 0.22f)),
        radius = StyleValue.Constant(28.dp),
        strokeColor = StyleValue.Constant(cyan),
        strokeWidth = 3.dp,
      ),
      MapLayer.Fill(
        id = "entity-fill",
        sourceId = ENTITY_SOURCE,
        filter = kind(KIND_POLYGON),
        color = StyleValue.Match(PROP_FILL, palette, Color.Gray),
        opacity =
          StyleValue.Match(
            PROP_VARIANT,
            mapOf(
              Variant.SELECTED to 0.52f,
              Variant.SELECTED_PENDING to 0.35f,
              Variant.PENDING to 0.14f,
            ),
            0.28f,
          ),
      ),
      MapLayer.Line(
        id = "entity-outline",
        sourceId = ENTITY_SOURCE,
        filter = FeatureFilter.All(listOf(kind(KIND_POLYGON), FeatureFilter.Not(pendingFilter))),
        color = strokeColor,
        width = outlineWidth,
      ),
      MapLayer.Line(
        id = "entity-outline-pending",
        sourceId = ENTITY_SOURCE,
        filter = FeatureFilter.All(listOf(kind(KIND_POLYGON), pendingFilter)),
        color = strokeColor,
        width = outlineWidth,
        dashPattern = listOf(2.6f, 1.8f),
      ),
      MapLayer.Line(
        id = "entity-line-casing",
        sourceId = ENTITY_SOURCE,
        filter = kind(KIND_LINE),
        color = StyleValue.Constant(Color(0xFF0E2219)),
        width = StyleValue.Constant(6.5.dp),
        opacity = StyleValue.Constant(0.85f),
      ),
      MapLayer.Line(
        id = "entity-line",
        sourceId = ENTITY_SOURCE,
        filter = kind(KIND_LINE),
        color = strokeColor,
        width =
          StyleValue.Match(
            PROP_VARIANT,
            mapOf(Variant.SELECTED to 4.4.dp, Variant.SELECTED_PENDING to 4.4.dp),
            3.2.dp,
          ),
      ),
      MapLayer.Line(
        id = "navigation-casing",
        sourceId = OVERLAY_SOURCE,
        filter = kind(KIND_NAV),
        color = StyleValue.Constant(Color(0xFF071812)),
        width = StyleValue.Constant(6.5.dp),
        opacity = StyleValue.Constant(0.9f),
      ),
      MapLayer.Line(
        id = "navigation-line",
        sourceId = OVERLAY_SOURCE,
        filter = kind(KIND_NAV),
        color = StyleValue.Constant(cyan),
        width = StyleValue.Constant(3.6.dp),
        dashPattern = listOf(2.2f, 1.3f),
      ),
      MapLayer.Circle(
        id = "entity-vertices",
        sourceId = ENTITY_SOURCE,
        filter = kind(KIND_VERTEX),
        color = StyleValue.Constant(Color.White),
        radius =
          StyleValue.Match(
            PROP_VERTEX_SIZE,
            mapOf(
              "polygon-selected" to 4.2.dp,
              "polygon" to 3.2.dp,
              "line-selected" to 4.4.dp,
              "line" to 3.4.dp,
            ),
            3.2.dp,
          ),
        strokeColor = strokeColor,
        strokeWidth = 2.dp,
      ),
      MapLayer.Symbol(
        id = "entity-pins",
        sourceId = ENTITY_SOURCE,
        filter = kind(KIND_POINT),
        iconId =
          StyleValue.Match(PROP_PIN, pinIds.associateWith { it }, pinIds.firstOrNull() ?: ""),
        iconSize =
          StyleValue.Match(
            PROP_VARIANT,
            mapOf(Variant.SELECTED to 1.85f, Variant.SELECTED_PENDING to 1.85f),
            1.5f,
          ),
        iconAnchor = MarkerAnchor.BOTTOM,
        // Moves the pin down so its tip, not the bottom of its shadow, sits on the location.
        iconOffset = DpOffset(0.dp, (GroundPin.HEIGHT - GroundPin.TIP_Y).toFloat().dp),
      ),
      // Geometry answers already held by the open form: settled in-flow style, above the entities.
      MapLayer.Fill(
        id = "form-geometry-fill",
        sourceId = OVERLAY_SOURCE,
        filter = kind(KIND_FORM_POLYGON),
        color = StyleValue.Constant(draftColor),
        opacity = StyleValue.Constant(0.18f),
      ),
      MapLayer.Line(
        id = "form-geometry-outline",
        sourceId = OVERLAY_SOURCE,
        filter = FeatureFilter.In(PROP_KIND, setOf(KIND_FORM_LINE, KIND_FORM_POLYGON)),
        color = StyleValue.Constant(draftColor),
        width = StyleValue.Constant(2.5.dp),
      ),
      MapLayer.Circle(
        id = "form-geometry-vertices",
        sourceId = OVERLAY_SOURCE,
        filter = kind(KIND_FORM_VERTEX),
        color = StyleValue.Constant(Color.White),
        radius = StyleValue.Constant(3.6.dp),
        strokeColor = StyleValue.Constant(draftColor),
        strokeWidth = 2.dp,
      ),
      MapLayer.Circle(
        id = "form-geometry-points",
        sourceId = OVERLAY_SOURCE,
        filter = kind(KIND_FORM_POINT),
        color = StyleValue.Constant(Color.White),
        radius = StyleValue.Constant(6.dp),
        strokeColor = StyleValue.Constant(draftColor),
        strokeWidth = 3.dp,
      ),
      // The geometry being drawn for a form question sits above every entity.
      MapLayer.Fill(
        id = "draft-fill",
        sourceId = OVERLAY_SOURCE,
        filter = kind(KIND_DRAFT_POLYGON),
        color = StyleValue.Constant(draftColor),
        opacity = StyleValue.Constant(0.3f),
      ),
      MapLayer.Line(
        id = "draft-outline",
        sourceId = OVERLAY_SOURCE,
        filter = FeatureFilter.In(PROP_KIND, setOf(KIND_DRAFT_LINE, KIND_DRAFT_POLYGON)),
        color = StyleValue.Constant(draftColor),
        width = StyleValue.Constant(3.4.dp),
        dashPattern = listOf(2.2f, 1.3f),
      ),
      MapLayer.Circle(
        id = "draft-vertices",
        sourceId = OVERLAY_SOURCE,
        filter = kind(KIND_DRAFT_VERTEX),
        color = StyleValue.Constant(Color.White),
        radius = StyleValue.Constant(5.dp),
        strokeColor = StyleValue.Constant(draftColor),
        strokeWidth = 2.5.dp,
      ),
    )
  }

  /** Stroke and fill of a geometry being drawn; legible on streets and satellite basemaps. */
  private val draftColor = Color(0xFFFF8F00)

  private fun kind(kind: String) = FeatureFilter.Equals(PROP_KIND, kind)

  private val pendingFilter =
    FeatureFilter.In(PROP_VARIANT, setOf(Variant.PENDING, Variant.SELECTED_PENDING))

  private val outlineWidth =
    StyleValue.Match(
      PROP_VARIANT,
      mapOf(
        Variant.SELECTED to 3.8.dp,
        Variant.SELECTED_PENDING to 3.8.dp,
        Variant.GENERATED to 1.6.dp,
      ),
      2.3.dp,
    )

  /** Feature properties the layers style by. */
  const val PROP_KIND = "kind"
  const val PROP_VARIANT = "variant"
  private const val PROP_STROKE = "stroke"
  private const val PROP_FILL = "fill"
  private const val PROP_PIN = "pin"
  private const val PROP_VERTEX_SIZE = "vertexSize"

  const val KIND_POINT = "point"
  const val KIND_LINE = "line"
  const val KIND_POLYGON = "polygon"
  const val KIND_VERTEX = "vertex"
  private const val KIND_SECTOR = "sector"
  private const val KIND_NAV = "navigation"
  private const val KIND_NAV_TARGET = "navigation-target"
  const val KIND_DRAFT_LINE = "draft-line"
  const val KIND_DRAFT_POLYGON = "draft-polygon"
  const val KIND_DRAFT_VERTEX = "draft-vertex"
  const val KIND_FORM_POINT = "form-point"
  const val KIND_FORM_LINE = "form-line"
  const val KIND_FORM_POLYGON = "form-polygon"
  const val KIND_FORM_VERTEX = "form-vertex"

  /** How a feature is emphasized. */
  object Variant {
    const val SELECTED = "selected"
    const val SELECTED_PENDING = "selected-pending"
    const val PENDING = "pending"
    const val GENERATED = "generated"
    const val NORMAL = "normal"
  }
}
