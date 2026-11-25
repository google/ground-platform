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
package org.openforis.collectone.v2.app.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.MapFeatureCluster
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.StraightLineNavigationState
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.editor.toLatLng
import org.openforis.collectone.v2.app.domain.model.formatHexColorCss
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.nameText
import org.openforis.collectone.v2.app.ui.text.wayfindingBadgeText
import org.openforis.collectone.v2.core.forms.ui.AppStaticColors
import org.openforis.collectone.v2.core.forms.ui.MapDrawingKind
import org.openforis.collectone.v2.core.sampling.SampleDesign
import org.openforis.collectone.v2.map.FeatureFilter
import org.openforis.collectone.v2.map.GeoJsonSource
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.MapContent
import org.openforis.collectone.v2.map.MapFeature
import org.openforis.collectone.v2.map.MapIcon
import org.openforis.collectone.v2.map.MapLayer
import org.openforis.collectone.v2.map.MapMarker
import org.openforis.collectone.v2.map.MarkerAnchor
import org.openforis.collectone.v2.map.StyleValue

/** What a survey map shows: native [content] plus the Compose UI for each marker, by id. */
data class SurveyMap(val content: MapContent, val markers: Map<String, SurveyMarker>)

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

/**
 * What a survey map draws, for [SurveyMapContent.build].
 *
 * @property pendingIds entities with pending uploads, drawn in the pending style.
 * @property userGps the user's location, in normalized coordinates around [anchor].
 * @property clusters the clusters to draw instead of individual features, or `null` when the map
 *   isn't clustering.
 * @property layers the survey's map layers; each feature is drawn in its layer's color, and points
 *   show the layer's icon.
 * @property draftGeometry a geometry being drawn for a form question, drawn above everything else
 *   in the draft style: its vertices, the line through them, and (for a polygon with at least 3
 *   vertices) the closed shape.
 * @property formGeometries the geometry answers already held by the open form, drawn in the in-flow
 *   style above the entities and below [draftGeometry], each labelled with its question title.
 * @property imagerySources enabled organization imagery sources (`"All users"` and survey-specific
 *   organization XYZ tile URLs) rendered on top of the selected basemap.
 * @property savedImagery the map areas saved for offline use to outline and draw.
 */
data class SurveyMapScene(
  val anchor: SurveyMapAnchor,
  val basemapType: BasemapType,
  val entities: List<GeospatialEntityItem>,
  val selectedEntityId: String?,
  val pendingIds: Set<String>,
  val userGps: Pair<Float, Float>,
  val isFollowingUser: Boolean,
  val clusters: List<MapFeatureCluster>? = null,
  val selectedClusterId: String? = null,
  val clusterHeader: (MapFeatureCluster) -> UiText = { UiText.Plain("") },
  val navigation: StraightLineNavigationState? = null,
  val place: SurveyPlaceItem? = null,
  val layers: List<MapLayerItem> = emptyList(),
  val draftGeometry: DraftGeometry? = null,
  val formGeometries: List<FormGeometryOverlay> = emptyList(),
  val imagerySources: List<ImagerySource> = emptyList(),
  val savedImagery: SavedImageryOnMap = SavedImageryOnMap(),
  val userLatLng: LatLng? = null,
  /** The entity under the mouse pointer, drawn slightly emphasized (web dashboard). */
  val hoveredEntityId: String? = null,
) {
  /** The position at normalized coordinates ([nx], [ny]) around [anchor]. */
  internal fun at(nx: Float, ny: Float): LatLng = anchor.toLatLng(nx.toDouble(), ny.toDouble())

  /** Where the user is. */
  internal val userLocation: LatLng
    get() = userLatLng ?: at(userGps.first, userGps.second)
}

/**
 * The layers of saved map areas a map shows. Each area in [satellite] and [terrain] is outlined
 * (dashed for satellite, solid for terrain). The basemap already uses the saved tiles of its own
 * kind, so each type is drawn only over the other basemap: satellite imagery inside its areas over
 * Terrain when [drawSatellite], and contour lines inside terrain areas over Satellite when
 * [drawTerrain].
 */
data class SavedImageryOnMap(
  val satellite: List<SavedImageryArea> = emptyList(),
  val terrain: List<SavedImageryArea> = emptyList(),
  val drawSatellite: Boolean = false,
  val drawTerrain: Boolean = false,
)

/** Compose-drawn map annotations; see `SurveyMapMarkers.kt` for their UI. */
sealed interface SurveyMarker {
  /**
   * Name pill at the middle of a line or polygon, filled with its layer's [color], with a [status]
   * badge after the label. Layer pin icons are only for points.
   */
  data class GeometryPill(
    val label: String,
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
    val header: UiText,
    val chips: List<ClusterChip>,
    val selected: Boolean,
  ) : SurveyMarker

  /** [symbol] is empty for features without a marker symbol. */
  data class ClusterChip(val symbol: String, val color: Color, val count: Int)

  /** Label under the user's location; [bearing] (e.g. `"319° NW"`) is set while navigating. */
  data class UserChip(val mode: UserChipMode, val bearing: String = "") : SurveyMarker

  /** What the [UserChip] says about the user's location. */
  enum class UserChipMode {
    NAVIGATING,
    CENTERED,
    GPS,
  }

  /**
   * Distance and bearing ([text], e.g. `"495 m • 319° NW"`) halfway along the navigation line,
   * marked as arrived when [hasArrived].
   */
  data class NavigationPill(val text: UiText, val hasArrived: Boolean = false) : SurveyMarker

  /**
   * A place picked from search (a generic label is shown for a blank [name]); tapping dismisses it.
   */
  data class Place(val name: UiText) : SurveyMarker

  /**
   * The question title over a geometry answer of the form open in the web dashboard panel; tapping
   * it jumps to the question.
   */
  data class FormQuestion(val title: String, val kind: MapDrawingKind) : SurveyMarker
}

/** Feature and marker ids on survey maps, and how taps map back to domain ids. */
object SurveyMapIds {
  private const val ENTITY = "entity:"
  private const val CLUSTER = "cluster:"
  private const val PLACE = "place:"
  private const val FORM_GEOMETRY = "form-geometry:"
  private const val SAVED_AREA = "saved-area:"
  const val USER = "user"
  const val NAVIGATION = "navigation"

  fun entity(id: String) = ENTITY + id

  fun vertex(entityId: String, index: Int) = "$ENTITY$entityId#v$index"

  fun subPlot(entityId: String, index: Int) = "$ENTITY$entityId#s$index"

  fun cluster(id: String) = CLUSTER + id

  fun place(id: String) = PLACE + id

  /** Feature and marker id of the open form's geometry answer at [path] (e.g. `/data/plot`). */
  fun formGeometry(path: String) = FORM_GEOMETRY + path

  fun formGeometryVertex(path: String, index: Int) = "$FORM_GEOMETRY$path#v$index"

  /** Feature id of the outline of saved area [areaId]'s layer of [kind]. */
  fun savedArea(areaId: String, kind: String) = "$SAVED_AREA$areaId:$kind"

  /**
   * The entity a feature or marker id belongs to, if any (vertices and sub-plots map to their
   * entity).
   */
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
object SurveyMapContent {
  const val ENTITY_SOURCE = "survey-entities"
  const val OVERLAY_SOURCE = "survey-overlay"

  /** Most cluster balloons drawn at once, to keep the marker layer light. */
  private const val MAX_CLUSTER_MARKERS = 120

  /**
   * The main survey map (mobile and web dashboard) from the map viewport's [uiState].
   *
   * @param pendingIds entities with pending uploads, drawn in the pending style.
   * @param draftGeometry the geometry being drawn for a form question, if any (web dashboard).
   * @param formGeometries geometry answers held by the open form, if any (web dashboard).
   * @param hoveredEntityId the map feature under the mouse pointer, if any (web dashboard).
   */
  fun main(
    uiState: SurveyMapUiState,
    pendingIds: Set<String>,
    showNavigation: Boolean,
    draftGeometry: DraftGeometry? = null,
    formGeometries: List<FormGeometryOverlay> = emptyList(),
    hoveredEntityId: String? = null,
  ): SurveyMap =
    build(
      uiState
        .scene(
          entities = uiState.visibleMapEntities,
          selectedEntityId = uiState.selectedEntity?.id,
          pendingIds = pendingIds,
          isFollowingUser = uiState.isCameraFollowingUser,
        )
        .copy(
          clusters = if (uiState.isMapClusteringActive) uiState.mapFeatureClusters else null,
          selectedClusterId = uiState.selectedClusterId,
          clusterHeader = { uiState.clusterCountText(it.siteCount) },
          navigation = if (showNavigation) uiState.activeNavigation else null,
          place = uiState.selectedPlace,
          draftGeometry = draftGeometry,
          formGeometries = formGeometries,
          hoveredEntityId = hoveredEntityId,
        )
    )

  /** The map in a form's entity-reference step, showing the form's candidate [entities]. */
  fun entityRefForm(
    uiState: SurveyMapUiState,
    entities: List<GeospatialEntityItem>,
    selectedEntityId: String?,
    pendingIds: Set<String>,
  ): SurveyMap =
    build(
      uiState.scene(
        entities = entities,
        selectedEntityId = selectedEntityId,
        pendingIds = pendingIds,
        isFollowingUser = uiState.isCameraFollowingUser,
      )
    )

  /** The map behind a GeoPoint question, highlighting the form's [selectedEntityId]. */
  fun geoPointForm(
    uiState: SurveyMapUiState,
    selectedEntityId: String?,
    pendingIds: Set<String>,
    isFollowingUser: Boolean,
  ): SurveyMap =
    build(
      uiState.scene(
        entities = uiState.visibleMapEntities,
        selectedEntityId = selectedEntityId,
        pendingIds = pendingIds,
        isFollowingUser = isFollowingUser,
      )
    )

  /** A scene of [entities] over the basemap, layers, and imagery of the map viewport. */
  private fun SurveyMapUiState.scene(
    entities: List<GeospatialEntityItem>,
    selectedEntityId: String?,
    pendingIds: Set<String>,
    isFollowingUser: Boolean,
  ) =
    SurveyMapScene(
      anchor = anchor,
      basemapType = selectedBasemapType,
      entities = entities,
      selectedEntityId = selectedEntityId,
      pendingIds = pendingIds,
      userGps = userGpsNormalizedX to userGpsNormalizedY,
      isFollowingUser = isFollowingUser,
      layers = mapLayers,
      imagerySources = enabledImagerySources,
      savedImagery = savedImageryOnMap(),
      userLatLng = userLatLng,
    )

  /**
   * The saved areas the map shows: those whose layers sheet switch is on, and every one while the
   * user frames a new area (outlined only, unless its switch is on too).
   */
  private fun SurveyMapUiState.savedImageryOnMap() =
    SavedImageryOnMap(
      satellite =
        savedSatelliteAreas
          .takeIf { isDownloadedSatelliteVisible || isSelectingOfflineArea }
          .orEmpty(),
      terrain =
        savedTerrainAreas.takeIf { isDownloadedTerrainVisible || isSelectingOfflineArea }.orEmpty(),
      drawSatellite = isDownloadedSatelliteVisible,
      drawTerrain = isDownloadedTerrainVisible,
    )

  /** The native content and marker UI of [scene]. */
  fun build(scene: SurveyMapScene): SurveyMap {
    val satellite = scene.basemapType == BasemapType.SATELLITE
    val entities = EntityParts(scene)
    val overlays = overlayParts(scene)
    val allMarkers =
      entities.markers + clusterMarkers(scene) + overlays.markers + userAndPlaceMarkers(scene)
    val baseTiles = if (satellite) SurveyBasemaps.Satellite else SurveyBasemaps.Streets
    val content =
      MapContent(
        basemap =
          SurveyBasemaps.withImagerySources(
            SurveyBasemaps.withSavedImagery(baseTiles, scene.savedImagery),
            scene.imagerySources,
          ),
        sources =
          listOf(
            GeoJsonSource(
              OVERLAY_SOURCE,
              savedAreaOutlines(scene.savedImagery) +
                SurveyMapHover.features(scene, entities.features) +
                overlays.features,
            ),
            GeoJsonSource(ENTITY_SOURCE, entities.features),
          ),
        layers = layers(satellite, entities.palette, entities.icons.keys.toList()),
        icons = entities.icons.values.toList(),
        markers = allMarkers.map { it.first },
        userLocation = scene.userLocation,
      )
    return SurveyMap(content, allMarkers.associate { (marker, ui) -> marker.id to ui })
  }

  /** Map features and markers of a scene's entities, with the colors and pins they use. */
  private class EntityParts(scene: SurveyMapScene) {
    val palette = linkedMapOf<String, Color>()
    val icons = linkedMapOf<String, MapIcon>()
    val features = mutableListOf<MapFeature>()
    val markers = mutableListOf<Pair<MapMarker, SurveyMarker>>()
    private val satellite = scene.basemapType == BasemapType.SATELLITE

    init {
      val layersById = scene.layers.associateBy { it.id }
      val layersByDatasetId =
        scene.layers
          .mapNotNull { layer -> layer.datasetId?.takeIf { it.isNotBlank() }?.let { it to layer } }
          .toMap()
      // Selected last, so it draws above the rest in its layers and among the markers.
      val ordered =
        if (scene.clusters != null) emptyList()
        else scene.entities.sortedBy { it.id == scene.selectedEntityId }
      for (entity in ordered) {
        val geometry = entity.geometry ?: continue
        val layer = layersById[entity.layerId] ?: layersByDatasetId[entity.datasetId]
        add(
          entity,
          geometry,
          layer,
          selected = entity.id == scene.selectedEntityId,
          pending = entity.id in scene.pendingIds,
        )
      }
    }

    private fun colorKey(hex: Long): String {
      val key = "#" + (hex and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
      palette.getOrPut(key) { Color(0xFF000000 or (hex and 0xFFFFFF)) }
      return key
    }

    private fun add(
      entity: GeospatialEntityItem,
      geometry: Geometry,
      layer: MapLayerItem?,
      selected: Boolean,
      pending: Boolean,
    ) {
      val variant =
        when {
          selected && pending -> Variant.SELECTED_PENDING
          selected -> Variant.SELECTED
          pending -> Variant.PENDING
          else -> Variant.NORMAL
        }
      val layerColor = entity.mapColorHex(layer)
      val stroke = if (selected && satellite) colorKey(0xFFFFFF) else colorKey(layerColor)
      val common = mapOf(PROP_VARIANT to variant, PROP_STROKE to stroke)
      val subPlots = if (selected) EntitySubPlots.resolve(entity, layer) else emptyList()
      val hasSubPlots = subPlots.isNotEmpty()
      if (geometry.type == GeometryType.POINT) {
        val pinColor = formatHexColorCss(layerColor)
        val pinId = AppPin.iconId(pinColor, layer?.iconName, pending)
        icons.getOrPut(pinId) { MapIcon(pinId, AppPin.svg(pinColor, layer?.iconName, pending)) }
        features +=
          MapFeature(
            SurveyMapIds.entity(entity.id),
            geometry,
            common + mapOf(PROP_KIND to KIND_POINT, PROP_PIN to pinId),
          )
      } else {
        addShape(entity.id, geometry, common, colorKey(layerColor), selected, hasSubPlots)
      }
      if (hasSubPlots) {
        addSubPlots(entity.id, subPlots, layer?.sampleDesign, common, colorKey(layerColor))
      }
      markers += marker(entity, geometry, Color(layerColor), selected, pending, hasSubPlots)
    }

    /** A line or polygon of entity [id], plus a circle per vertex. */
    private fun addShape(
      id: String,
      geometry: Geometry,
      common: Map<String, String>,
      fill: String,
      selected: Boolean,
      hasSubPlots: Boolean,
    ) {
      val isPolygon = geometry.type == GeometryType.POLYGON
      val fillStyle =
        if (isPolygon && selected && hasSubPlots) {
          FillStyle.SELECTED_SUBPLOTS
        } else {
          common.getValue(PROP_VARIANT)
        }
      features +=
        MapFeature(
          SurveyMapIds.entity(id),
          geometry,
          common +
            mapOf(
              PROP_KIND to if (isPolygon) KIND_POLYGON else KIND_LINE,
              PROP_FILL to fill,
              PROP_FILL_STYLE to fillStyle,
            ),
        )
      val size = (if (isPolygon) "polygon" else "line") + if (selected) "-selected" else ""
      EntityGeometry.vertices(geometry).forEachIndexed { i, vertex ->
        features +=
          MapFeature(
            SurveyMapIds.vertex(id, i),
            Geometry.Point(vertex),
            common + mapOf(PROP_KIND to KIND_VERTEX, PROP_VERTEX_SIZE to size),
          )
      }
    }

    /** Hollow ground-scaled square boxes for the selected plot's sub-plot sample points. */
    private fun addSubPlots(
      id: String,
      subPlots: List<LatLng>,
      design: SampleDesign?,
      common: Map<String, String>,
      fill: String,
    ) {
      val sizeM = EntitySubPlots.sizeM(design, subPlots)
      subPlots.forEachIndexed { index, center ->
        features +=
          MapFeature(
            SurveyMapIds.subPlot(id, index),
            Geometry.Polygon(listOf(EntitySubPlots.groundSquare(center, sizeM))),
            common + mapOf(PROP_KIND to KIND_SUBPLOT, PROP_FILL to fill),
          )
      }
    }

    private fun marker(
      entity: GeospatialEntityItem,
      geometry: Geometry,
      color: Color,
      selected: Boolean,
      pending: Boolean,
      hasSubPlots: Boolean,
    ): Pair<MapMarker, SurveyMarker> {
      val label = entity.label.substringBefore(" •").ifBlank { entity.id }
      val status =
        if (entity.hasMarkerSymbol) {
          SurveyMarker.StatusBadge(entity.markerSymbol, Color(entity.markerColorHex))
        } else {
          null
        }
      return when {
        geometry.type == GeometryType.POINT ->
          MapMarker(
            SurveyMapIds.entity(entity.id),
            geometry.representativePoint,
            MarkerAnchor.TOP,
            DpOffset(0.dp, 6.dp),
            collides = true,
          ) to SurveyMarker.PinLabel(label, color, selected, pending, status)
        selected && hasSubPlots && geometry.type == GeometryType.POLYGON -> {
          // Place the pill below the selected plot so it doesn't obscure internal sub-plot rings.
          val bounds = geometry.bounds()
          MapMarker(
            SurveyMapIds.entity(entity.id),
            LatLng(bounds.south, bounds.center.longitude),
            MarkerAnchor.TOP,
            DpOffset(0.dp, 8.dp),
            collides = true,
          ) to SurveyMarker.GeometryPill(label, color, selected, pending, status)
        }
        else ->
          MapMarker(
            SurveyMapIds.entity(entity.id),
            EntityGeometry.labelPosition(geometry),
            MarkerAnchor.CENTER,
            collides = true,
          ) to SurveyMarker.GeometryPill(label, color, selected, pending, status)
      }
    }
  }

  /** An outline of each saved area of [imagery], by its kind. */
  private fun savedAreaOutlines(imagery: SavedImageryOnMap): List<MapFeature> =
    imagery.satellite.map { savedAreaOutline(it, KIND_SAVED_SATELLITE) } +
      imagery.terrain.map { savedAreaOutline(it, KIND_SAVED_TERRAIN) }

  private fun savedAreaOutline(area: SavedImageryArea, kind: String): MapFeature {
    val b = area.bounds
    val ring =
      listOf(
        LatLng(b.north, b.west),
        LatLng(b.north, b.east),
        LatLng(b.south, b.east),
        LatLng(b.south, b.west),
        LatLng(b.north, b.west),
      )
    return MapFeature(
      SurveyMapIds.savedArea(area.id, kind),
      Geometry.LineString(ring),
      mapOf(PROP_KIND to kind),
    )
  }

  /** Features and markers drawn over the entities: navigation, the form's geometries, a draft. */
  private class OverlayParts(
    val features: List<MapFeature>,
    val markers: List<Pair<MapMarker, SurveyMarker>>,
  )

  private fun overlayParts(scene: SurveyMapScene): OverlayParts {
    val features = mutableListOf<MapFeature>()
    val markers = mutableListOf<Pair<MapMarker, SurveyMarker>>()
    scene.navigation?.let { navigation ->
      val v = navigation.vector
      val from = scene.userLocation
      val to = scene.at(v.toNormalizedX, v.toNormalizedY)
      features +=
        MapFeature(
          "navigation-line",
          Geometry.LineString(listOf(from, to)),
          mapOf(PROP_KIND to KIND_NAV),
        )
      features +=
        MapFeature("navigation-target", Geometry.Point(to), mapOf(PROP_KIND to KIND_NAV_TARGET))
      markers +=
        MapMarker(
          SurveyMapIds.NAVIGATION,
          LatLng((from.latitude + to.latitude) / 2.0, (from.longitude + to.longitude) / 2.0),
          MarkerAnchor.CENTER,
          DpOffset(0.dp, (-10).dp),
        ) to SurveyMarker.NavigationPill(wayfindingBadgeText(v), hasArrived = v.hasArrived)
    }
    scene.draftGeometry?.let { features += draftFeatures(it) }
    for (formGeometry in scene.formGeometries) {
      features += formGeometryFeatures(formGeometry)
      formGeometryMarker(formGeometry)?.let { markers += it }
    }
    return OverlayParts(features, markers)
  }

  private fun clusterMarkers(scene: SurveyMapScene): List<Pair<MapMarker, SurveyMarker>> =
    scene.clusters
      .orEmpty()
      .take(MAX_CLUSTER_MARKERS)
      .sortedBy { it.id == scene.selectedClusterId }
      .map { cluster ->
        MapMarker(
          SurveyMapIds.cluster(cluster.id),
          scene.at(cluster.normalizedX, cluster.normalizedY),
        ) to
          SurveyMarker.ClusterBalloon(
            header = scene.clusterHeader(cluster),
            chips =
              cluster.siteSymbolGroups.map { group ->
                SurveyMarker.ClusterChip(
                  symbol = if (group.isNoSymbolGroup) "" else group.markerSymbol,
                  color = Color(0xFF000000 or (group.colorHex and 0xFFFFFF)),
                  count = group.count,
                )
              },
            selected = cluster.id == scene.selectedClusterId,
          )
      }

  /** The user's location chip and the place picked from search, if any. */
  private fun userAndPlaceMarkers(scene: SurveyMapScene): List<Pair<MapMarker, SurveyMarker>> {
    val navigation = scene.navigation
    val userChip =
      when {
        navigation != null ->
          SurveyMarker.UserChip(
            SurveyMarker.UserChipMode.NAVIGATING,
            bearing = "${navigation.vector.bearingDegrees}° ${navigation.vector.cardinalDirection}",
          )
        scene.isFollowingUser -> SurveyMarker.UserChip(SurveyMarker.UserChipMode.CENTERED)
        else -> SurveyMarker.UserChip(SurveyMarker.UserChipMode.GPS)
      }
    val user =
      MapMarker(SurveyMapIds.USER, scene.userLocation, MarkerAnchor.TOP, DpOffset(0.dp, 12.dp)) to
        userChip
    val place =
      scene.place?.let {
        MapMarker(SurveyMapIds.place(it.id), it.position, MarkerAnchor.CENTER) to
          SurveyMarker.Place(it.nameText())
      }
    return listOfNotNull(user, place)
  }

  /**
   * Overlay features for a [draft] being drawn: every vertex, the line through them (also the
   * outline of a polygon that is still short of 3 vertices), and the closed polygon once complete.
   */
  fun draftFeatures(draft: DraftGeometry): List<MapFeature> {
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
    return savedAreaLayers(satellite) +
      navigationTargetLayer() +
      entityShapeLayers(palette, strokeColor) +
      SurveyMapHover.shapeLayers(strokeColor) +
      entitySubPlotLayers(palette) +
      navigationLineLayers() +
      entityPointLayers(strokeColor, pinIds) +
      SurveyMapHover.pinLayer(pinIds) +
      formAndDraftLayers()
  }

  private val navigationCyan = Color(0xFF00E5FF)

  /**
   * Outlines of saved areas, below everything else: satellite areas dashed, terrain areas solid, in
   * App static colors that read on the current basemap.
   */
  private fun savedAreaLayers(satellite: Boolean) =
    listOf(
      MapLayer.Line(
        id = "saved-satellite-outline",
        sourceId = OVERLAY_SOURCE,
        filter = kind(KIND_SAVED_SATELLITE),
        color =
          StyleValue.Constant(if (satellite) AppStaticColors.Yellow else AppStaticColors.Blue),
        width = StyleValue.Constant(2.5.dp),
        dashPattern = listOf(3f, 2f),
      ),
      MapLayer.Line(
        id = "saved-terrain-outline",
        sourceId = OVERLAY_SOURCE,
        filter = kind(KIND_SAVED_TERRAIN),
        color = StyleValue.Constant(if (satellite) Color.White else AppStaticColors.Purple),
        width = StyleValue.Constant(2.5.dp),
      ),
    )

  private fun navigationTargetLayer() =
    MapLayer.Circle(
      id = "navigation-target",
      sourceId = OVERLAY_SOURCE,
      filter = kind(KIND_NAV_TARGET),
      color = StyleValue.Constant(navigationCyan.copy(alpha = 0.22f)),
      radius = StyleValue.Constant(28.dp),
      strokeColor = StyleValue.Constant(navigationCyan),
      strokeWidth = 3.dp,
    )

  private fun entityShapeLayers(palette: Map<String, Color>, strokeColor: StyleValue<Color>) =
    listOf(
      MapLayer.Fill(
        id = "entity-fill",
        sourceId = ENTITY_SOURCE,
        filter = kind(KIND_POLYGON),
        color = StyleValue.Match(PROP_FILL, palette, Color.Gray),
        opacity =
          StyleValue.Match(
            PROP_FILL_STYLE,
            mapOf(
              FillStyle.SELECTED_SUBPLOTS to 0.10f,
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
    )

  /**
   * Hollow ground-scaled sub-plot rings of the selected plot: a layer-colored stroke over a wider
   * white halo so each ring stays readable on both satellite and terrain basemaps without obscuring
   * the imagery inside it.
   */
  private fun entitySubPlotLayers(palette: Map<String, Color>) =
    listOf(
      MapLayer.Line(
        id = "entity-subplots-halo",
        sourceId = ENTITY_SOURCE,
        filter = kind(KIND_SUBPLOT),
        color = StyleValue.Constant(Color.White),
        width = StyleValue.Constant(3.dp),
      ),
      MapLayer.Line(
        id = "entity-subplots",
        sourceId = ENTITY_SOURCE,
        filter = kind(KIND_SUBPLOT),
        color = StyleValue.Match(PROP_FILL, palette, Color.Gray),
        width = StyleValue.Constant(1.5.dp),
      ),
    )

  private fun navigationLineLayers() =
    listOf(
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
        color = StyleValue.Constant(navigationCyan),
        width = StyleValue.Constant(3.6.dp),
        dashPattern = listOf(2.2f, 1.3f),
      ),
    )

  private fun entityPointLayers(strokeColor: StyleValue<Color>, pinIds: List<String>) =
    listOf(
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
        iconOffset = DpOffset(0.dp, (AppPin.HEIGHT - AppPin.TIP_Y).toFloat().dp),
      ),
    )

  private fun formAndDraftLayers() =
    listOf(
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

  /** Stroke and fill of a geometry being drawn; legible on streets and satellite basemaps. */
  private val draftColor = Color(0xFFFF8F00)

  private fun kind(kind: String) = FeatureFilter.Equals(PROP_KIND, kind)

  private val pendingFilter =
    FeatureFilter.In(PROP_VARIANT, setOf(Variant.PENDING, Variant.SELECTED_PENDING))

  private val outlineWidth =
    StyleValue.Match(
      PROP_VARIANT,
      mapOf(Variant.SELECTED to 3.8.dp, Variant.SELECTED_PENDING to 3.8.dp),
      2.3.dp,
    )

  /** Feature properties the layers style by. */
  const val PROP_KIND = "kind"
  const val PROP_VARIANT = "variant"
  private const val PROP_STROKE = "stroke"
  private const val PROP_FILL = "fill"
  private const val PROP_FILL_STYLE = "fillStyle"
  internal const val PROP_PIN = "pin"
  private const val PROP_VERTEX_SIZE = "vertexSize"

  const val KIND_POINT = "point"
  const val KIND_LINE = "line"
  const val KIND_POLYGON = "polygon"
  const val KIND_VERTEX = "vertex"
  const val KIND_SUBPLOT = "subplot"
  private const val KIND_SAVED_SATELLITE = "saved-satellite"
  private const val KIND_SAVED_TERRAIN = "saved-terrain"
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
    const val NORMAL = "normal"
  }

  /** Fill-opacity overrides on top of [Variant]. */
  private object FillStyle {
    const val SELECTED_SUBPLOTS = "selected-subplots"
  }
}
