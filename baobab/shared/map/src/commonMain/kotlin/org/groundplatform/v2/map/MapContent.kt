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
package org.groundplatform.v2.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Everything drawn on the map: basemap, data sources, style layers, icons, and markers.
 *
 * `MapContent` is immutable and rebuilt whenever the app's state changes. It deliberately excludes
 * the camera, which both gestures and the app change and therefore lives in the hoisted, mutable
 * [MapCameraState]. Platform renderers apply the differences between successive values (see
 * [diffMapContent]) rather than rebuilding the map.
 */
@Immutable
data class MapContent(
  val basemap: Basemap = Basemap.MapboxOutdoors,
  val sources: List<GeoJsonSource> = emptyList(),
  /** Style layers, bottom to top. */
  val layers: List<MapLayer> = emptyList(),
  /** Images referenced by [MapLayer.Symbol.iconId]. */
  val icons: List<MapIcon> = emptyList(),
  /** Compose-rendered annotations, drawn above all layers (see [GroundMap]). */
  val markers: List<MapMarker> = emptyList(),
  /** Shows the device-location puck at this position when non-null. */
  val userLocation: LatLng? = null,
) {
  init {
    val sourceIds = sources.map { it.id }
    require(sourceIds.size == sourceIds.toSet().size) { "duplicate source ids: $sourceIds" }
    val layerIds = layers.map { it.id }
    require(layerIds.size == layerIds.toSet().size) { "duplicate layer ids: $layerIds" }
    val missing = layers.map { it.sourceId }.toSet() - sourceIds.toSet()
    require(missing.isEmpty()) { "layers reference unknown sources: $missing" }
  }
}

/**
 * Base map imagery shown below all [MapLayer]s. Apps choose the provider; this module has none
 * built in, apart from the [MapboxOutdoors] and [MapboxSatelliteStreets] shorthands.
 *
 * Renderers draw [attribution] on the map whenever it is non-empty, since most imagery providers
 * require a visible credit.
 */
@Immutable
sealed interface Basemap {
  /** Credit for the imagery, e.g. `© Mapbox © OpenStreetMap`. */
  val attribution: String
  /** Drawn behind the imagery, and in place of it by [PreviewMap]. */
  val backgroundColor: Color

  /**
   * A hosted Mapbox style, e.g. `mapbox://styles/mapbox/outdoors-v12`. Needs a Mapbox access token,
   * configured by the host app for its platform.
   */
  data class MapboxStyle(
    val styleUrl: String,
    override val attribution: String = MAPBOX_ATTRIBUTION,
    override val backgroundColor: Color = LIGHT_BACKGROUND,
  ) : Basemap

  /** XYZ raster tile layers from any tile server, bottom to top. */
  data class RasterTiles(
    val layers: List<RasterTileLayer>,
    override val attribution: String,
    override val backgroundColor: Color = LIGHT_BACKGROUND,
  ) : Basemap

  /** No imagery, e.g. offline without downloaded tiles. */
  data class None(override val backgroundColor: Color = Color(0xFFE6E6E6)) : Basemap {
    override val attribution: String
      get() = ""
  }

  companion object {
    private const val MAPBOX_ATTRIBUTION = "© Mapbox © OpenStreetMap"
    private val LIGHT_BACKGROUND = Color(0xFFF2EFE9)

    val MapboxOutdoors = MapboxStyle("mapbox://styles/mapbox/outdoors-v12")
    val MapboxSatelliteStreets =
      MapboxStyle(
        "mapbox://styles/mapbox/satellite-streets-v12",
        attribution = "$MAPBOX_ATTRIBUTION © Maxar",
        backgroundColor = Color(0xFF142E21),
      )
  }
}

/** One XYZ raster tile layer of a [Basemap.RasterTiles]. */
@Immutable
data class RasterTileLayer(
  /** Tile URL with `{z}`, `{x}`, and `{y}` placeholders. */
  val urlTemplate: String,
  val tileSize: Int = 256,
  val maxZoom: Int = 19,
  val opacity: Float = 1f,
  /** Style Specification `raster-contrast`, from -1 to 1. */
  val contrast: Float = 0f,
  /** Style Specification `raster-saturation`, from -1 to 1. */
  val saturation: Float = 0f,
)

/** A GeoJSON FeatureCollection (RFC 7946 section 3.3) addressable by [id]. */
@Immutable data class GeoJsonSource(val id: String, val features: List<MapFeature>)

/**
 * A GeoJSON Feature (RFC 7946 section 3.2). Property values are strings so every renderer can match
 * on them the same way; encode numbers and booleans as their string form.
 */
@Immutable
data class MapFeature(
  val id: String,
  val geometry: Geometry,
  val properties: Map<String, String> = emptyMap(),
)

/** An image that [MapLayer.Symbol] layers can reference by [id]. */
@Immutable data class MapIcon(val id: String, val svg: String)

/**
 * A Compose-rendered annotation pinned to [position], used for UI that style layers can't express
 * (cluster balloons, the selected-place pin). Rendering is supplied by the caller of [GroundMap].
 */
@Immutable
data class MapMarker(
  val id: String,
  val position: LatLng,
  val anchor: MarkerAnchor = MarkerAnchor.BOTTOM,
  /** Shift applied after anchoring, e.g. to place a label chip just below a pin. */
  val offset: DpOffset = DpOffset.Zero,
)

/** Which point of a marker's or icon's content sits on its position. */
enum class MarkerAnchor {
  CENTER,
  TOP,
  BOTTOM,
}

/** Space in dp that the camera should keep clear, e.g. for a bottom sheet or side card. */
@Immutable
data class MapInsets(
  val left: Dp = 0.dp,
  val top: Dp = 0.dp,
  val right: Dp = 0.dp,
  val bottom: Dp = 0.dp,
) {
  companion object {
    val Zero = MapInsets()
  }
}

/**
 * A style layer: a typed subset of the Mapbox Style Specification's `fill`, `line`, `circle`, and
 * `symbol` layers. Renderers translate these one-to-one into their SDK's layer types.
 */
@Immutable
sealed interface MapLayer {
  val id: String
  val sourceId: String
  /** Features the layer draws; `null` draws every feature in the source. */
  val filter: FeatureFilter?

  data class Fill(
    override val id: String,
    override val sourceId: String,
    override val filter: FeatureFilter? = FeatureFilter.GeometryTypeIs(GeometryType.POLYGON),
    val color: StyleValue<Color>,
    val opacity: StyleValue<Float> = StyleValue.Constant(0.3f),
  ) : MapLayer

  data class Line(
    override val id: String,
    override val sourceId: String,
    override val filter: FeatureFilter? = null,
    val color: StyleValue<Color>,
    val width: StyleValue<Dp> = StyleValue.Constant(2.dp),
    /** Alternating dash and gap lengths, in multiples of the line width; `null` for solid. */
    val dashPattern: List<Float>? = null,
    val opacity: StyleValue<Float> = StyleValue.Constant(1f),
  ) : MapLayer

  data class Circle(
    override val id: String,
    override val sourceId: String,
    override val filter: FeatureFilter? = FeatureFilter.GeometryTypeIs(GeometryType.POINT),
    val color: StyleValue<Color>,
    val radius: StyleValue<Dp> = StyleValue.Constant(6.dp),
    val strokeColor: StyleValue<Color> = StyleValue.Constant(Color.White),
    val strokeWidth: Dp = 1.dp,
  ) : MapLayer

  /**
   * Icons and/or text at point features. Icons always draw, even where they overlap, so no feature
   * silently disappears.
   */
  data class Symbol(
    override val id: String,
    override val sourceId: String,
    override val filter: FeatureFilter? = FeatureFilter.GeometryTypeIs(GeometryType.POINT),
    /** A [MapIcon.id], possibly chosen per feature. */
    val iconId: StyleValue<String>? = null,
    /** Scale factor for the icon's natural size. */
    val iconSize: StyleValue<Float> = StyleValue.Constant(1f),
    /** Which point of the icon sits on the feature. */
    val iconAnchor: MarkerAnchor = MarkerAnchor.CENTER,
    /** Shift applied after anchoring, at [iconSize] 1; scales with the icon. */
    val iconOffset: DpOffset = DpOffset.Zero,
    /** Name of the feature property whose value is drawn as a text label. */
    val textProperty: String? = null,
    val textColor: StyleValue<Color> = StyleValue.Constant(Color.Black),
  ) : MapLayer
}

/**
 * A style property value, either constant or chosen per feature (the Style Specification's `match`
 * expression over a string property).
 */
@Immutable
sealed interface StyleValue<out T> {
  fun evaluate(properties: Map<String, String>): T

  data class Constant<T>(val value: T) : StyleValue<T> {
    override fun evaluate(properties: Map<String, String>): T = value
  }

  data class Match<T>(val property: String, val cases: Map<String, T>, val default: T) :
    StyleValue<T> {
    override fun evaluate(properties: Map<String, String>): T =
      properties[property]?.let { cases[it] } ?: default
  }
}

/** Selects which features a [MapLayer] draws (a subset of Style Specification filters). */
@Immutable
sealed interface FeatureFilter {
  fun matches(feature: MapFeature): Boolean

  data class GeometryTypeIs(val type: GeometryType) : FeatureFilter {
    override fun matches(feature: MapFeature) = feature.geometry.type == type
  }

  data class Equals(val property: String, val value: String) : FeatureFilter {
    override fun matches(feature: MapFeature) = feature.properties[property] == value
  }

  data class In(val property: String, val values: Set<String>) : FeatureFilter {
    override fun matches(feature: MapFeature) = feature.properties[property] in values
  }

  data class Not(val filter: FeatureFilter) : FeatureFilter {
    override fun matches(feature: MapFeature) = !filter.matches(feature)
  }

  data class All(val filters: List<FeatureFilter>) : FeatureFilter {
    override fun matches(feature: MapFeature) = filters.all { it.matches(feature) }
  }
}
