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
package org.openforis.collectone.v2.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Translates [MapContent] into the Mapbox Style Specification (sources, layers, expressions), the
 * format every Mapbox SDK accepts. Renderers pass the JSON to their SDK instead of re-implementing
 * the translation per platform.
 */
internal object MapboxStyleSpec {
  /** Source and layers the renderer draws [MapContent.userLocation] with, above all content. */
  const val USER_LOCATION_SOURCE = "app-map-user-location"
  const val USER_LOCATION_HALO_LAYER = "app-map-user-location-halo"
  const val USER_LOCATION_DOT_LAYER = "app-map-user-location-dot"

  /** Property that carries [MapFeature.id]; SDKs only keep numeric GeoJSON feature ids. */
  const val FEATURE_ID_PROPERTY = "__id"

  /** The highest layer `maxzoom` the Style Specification allows, its default. */
  private const val MAX_LAYER_ZOOM = 24

  /**
   * What to pass to the SDK's style loader for [basemap]: the style URL of a [Basemap.MapboxStyle],
   * otherwise style JSON. Every Mapbox SDK accepts either; JSON always starts with `{`.
   *
   * The style has no content or user-location layers. After each style load, renderers add
   * [overlays] (for a [Basemap.MapboxStyle]), then [userLocationSource] and [userLocationLayers],
   * then insert content layers below [USER_LOCATION_HALO_LAYER].
   */
  fun style(basemap: Basemap): String =
    when (basemap) {
      is Basemap.MapboxStyle -> basemap.styleUrl
      is Basemap.RasterTiles,
      is Basemap.None -> styleJson(basemap).toString()
    }

  /** Style JSON for a basemap drawn from raster tiles, or from its background color alone. */
  fun styleJson(basemap: Basemap): JsonObject = buildJsonObject {
    val tiles = (basemap as? Basemap.RasterTiles)?.layers.orEmpty()
    put("version", 8)
    put("name", "Collect One basemap")
    // Only fetched when a symbol layer draws text; resolved with the SDK's access token.
    put("glyphs", "mapbox://fonts/mapbox/{fontstack}/{range}.pbf")
    putJsonObject("sources") {
      tiles.forEachIndexed { i, tile -> put(rasterSourceId(i), rasterSource(tile)) }
    }
    putJsonArray("layers") {
      add(
        buildJsonObject {
          put("id", "app-map-background")
          put("type", "background")
          putJsonObject("paint") { put("background-color", color(basemap.backgroundColor)) }
        }
      )
      tiles.forEachIndexed { i, tile -> add(rasterLayer(rasterSourceId(i), tile)) }
    }
  }

  /** The source [userLocationLayers] draw from; its data is [userLocationGeoJson]. */
  fun userLocationSource(): JsonObject = geoJsonSource(userLocationGeoJson(null))

  /** The user-location halo and dot, which stay above all content layers. */
  fun userLocationLayers(): List<JsonObject> =
    listOf(
      circleLayer(
        USER_LOCATION_HALO_LAYER,
        radius = 24.0,
        color = "rgba(66, 133, 244, 0.24)",
        strokeWidth = 1.2,
        strokeColor = "#4285F4",
      ),
      circleLayer(
        USER_LOCATION_DOT_LAYER,
        radius = 7.5,
        color = "#1A73E8",
        strokeWidth = 2.5,
        strokeColor = "#FFFFFF",
      ),
    )

  /** A GeoJSON FeatureCollection (RFC 7946) of [features], with rings closed. */
  fun geoJson(features: List<MapFeature>): JsonObject = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") { features.forEach { add(feature(it)) } }
  }

  /** The user-location source's data: one point, or nothing. */
  fun userLocationGeoJson(location: LatLng?): JsonObject = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
      if (location != null) {
        add(
          buildJsonObject {
            put("type", "Feature")
            putJsonObject("properties") {}
            put("geometry", geometry(Geometry.Point(location)))
          }
        )
      }
    }
  }

  /** A GeoJSON source definition wrapping [data]. */
  fun geoJsonSource(data: JsonObject): JsonObject = buildJsonObject {
    put("type", "geojson")
    put("data", data)
  }

  /** The style layer for [layer]. */
  fun layer(layer: MapLayer): JsonObject = buildJsonObject {
    put("id", layer.id)
    put("source", layer.sourceId)
    layer.filter?.let { put("filter", filter(it)) }
    when (layer) {
      is MapLayer.Fill -> {
        put("type", "fill")
        putJsonObject("paint") {
          put("fill-color", expression(layer.color, ::color))
          put("fill-opacity", expression(layer.opacity) { JsonPrimitive(it) })
        }
      }
      is MapLayer.Line -> {
        put("type", "line")
        putJsonObject("layout") {
          put("line-join", "round")
          put("line-cap", if (layer.dashPattern == null) "round" else "butt")
        }
        putJsonObject("paint") {
          put("line-color", expression(layer.color, ::color))
          put("line-width", expression(layer.width, ::dp))
          put("line-opacity", expression(layer.opacity) { JsonPrimitive(it) })
          layer.dashPattern?.let { dashes ->
            putJsonArray("line-dasharray") { dashes.forEach { add(it) } }
          }
        }
      }
      is MapLayer.Circle -> {
        put("type", "circle")
        putJsonObject("paint") {
          put("circle-color", expression(layer.color, ::color))
          put("circle-radius", expression(layer.radius, ::dp))
          put("circle-stroke-color", expression(layer.strokeColor, ::color))
          put("circle-stroke-width", layer.strokeWidth.value)
        }
      }
      is MapLayer.Symbol -> {
        put("type", "symbol")
        putJsonObject("layout") {
          layer.iconId?.let { iconId ->
            put("icon-image", expression(iconId) { JsonPrimitive(it) })
            put("icon-size", expression(layer.iconSize) { JsonPrimitive(it) })
            put("icon-anchor", anchor(layer.iconAnchor))
            putJsonArray("icon-offset") {
              add(layer.iconOffset.x.value)
              add(layer.iconOffset.y.value)
            }
            put("icon-allow-overlap", true)
            put("icon-ignore-placement", true)
          }
          layer.textProperty?.let { property ->
            putJsonArray("text-field") {
              add("get")
              add(property)
            }
            put("text-allow-overlap", true)
          }
        }
        putJsonObject("paint") { put("text-color", expression(layer.textColor, ::color)) }
      }
    }
  }

  /** A Style Specification filter expression for [f]. */
  fun filter(f: FeatureFilter): JsonElement =
    when (f) {
      is FeatureFilter.GeometryTypeIs ->
        buildJsonArray {
          add("==")
          addJsonArray { add("geometry-type") }
          add(geometryTypeName(f.type))
        }
      is FeatureFilter.Equals ->
        buildJsonArray {
          add("==")
          add(get(f.property))
          add(f.value)
        }
      is FeatureFilter.In ->
        if (f.values.isEmpty()) {
          JsonPrimitive(false)
        } else {
          buildJsonArray {
            add("match")
            add(get(f.property))
            addJsonArray { f.values.sorted().forEach { add(it) } }
            add(true)
            add(false)
          }
        }
      is FeatureFilter.Not ->
        buildJsonArray {
          add("!")
          add(filter(f.filter))
        }
      is FeatureFilter.All ->
        buildJsonArray {
          add("all")
          f.filters.forEach { add(filter(it)) }
        }
    }

  /** A literal for constants, or a `match` expression on a string property. */
  fun <T> expression(value: StyleValue<T>, encode: (T) -> JsonElement): JsonElement =
    when (value) {
      is StyleValue.Constant -> encode(value.value)
      is StyleValue.Match ->
        if (value.cases.isEmpty()) {
          encode(value.default)
        } else {
          buildJsonArray {
            add("match")
            add(get(value.property))
            value.cases.forEach { (key, v) ->
              add(key)
              add(encode(v))
            }
            add(encode(value.default))
          }
        }
    }

  /** CSS `rgba()` for [color], which every Mapbox SDK parses. */
  fun color(color: Color): JsonPrimitive {
    val r = (color.red * 255).roundToInt()
    val g = (color.green * 255).roundToInt()
    val b = (color.blue * 255).roundToInt()
    val a = (color.alpha * 1000).roundToInt() / 1000.0
    return JsonPrimitive("rgba($r, $g, $b, $a)")
  }

  private fun dp(value: Dp) = JsonPrimitive(value.value)

  private fun get(property: String) = buildJsonArray {
    add("get")
    add(property)
  }

  private fun anchor(anchor: MarkerAnchor) =
    when (anchor) {
      MarkerAnchor.CENTER -> "center"
      MarkerAnchor.TOP -> "top"
      MarkerAnchor.BOTTOM -> "bottom"
    }

  private fun geometryTypeName(type: GeometryType) =
    when (type) {
      GeometryType.POINT -> "Point"
      GeometryType.LINE_STRING -> "LineString"
      GeometryType.POLYGON -> "Polygon"
    }

  private fun feature(feature: MapFeature) = buildJsonObject {
    put("type", "Feature")
    putJsonObject("properties") {
      feature.properties.forEach { (k, v) -> put(k, v) }
      put(FEATURE_ID_PROPERTY, feature.id)
    }
    put("geometry", geometry(feature.geometry))
  }

  private fun geometry(geometry: Geometry) = buildJsonObject {
    when (geometry) {
      is Geometry.Point -> {
        put("type", "Point")
        put("coordinates", position(geometry.position))
      }
      is Geometry.LineString -> {
        put("type", "LineString")
        put("coordinates", line(geometry))
      }
      is Geometry.Polygon -> {
        put("type", "Polygon")
        put("coordinates", rings(geometry))
      }
      is Geometry.MultiPoint -> {
        put("type", "MultiPoint")
        putJsonArray("coordinates") { geometry.parts.forEach { add(position(it.position)) } }
      }
      is Geometry.MultiLineString -> {
        put("type", "MultiLineString")
        putJsonArray("coordinates") { geometry.parts.forEach { add(line(it)) } }
      }
      is Geometry.MultiPolygon -> {
        put("type", "MultiPolygon")
        putJsonArray("coordinates") { geometry.parts.forEach { add(rings(it)) } }
      }
    }
  }

  private fun line(line: Geometry.LineString) = buildJsonArray {
    line.points.forEach { add(position(it)) }
  }

  /** GeoJSON closes polygon rings by repeating their first position (RFC 7946 section 3.1.6). */
  private fun rings(polygon: Geometry.Polygon) = buildJsonArray {
    polygon.rings.forEach { ring ->
      addJsonArray { (ring + ring.first()).forEach { add(position(it)) } }
    }
  }

  private fun position(p: LatLng) = buildJsonArray {
    add(p.longitude)
    add(p.latitude)
  }

  private fun rasterSourceId(index: Int) = "app-map-raster-$index"

  /** One overlay of a [Basemap.MapboxStyle]: its source id and JSON, and its layer JSON. */
  data class Overlay(val sourceId: String, val source: JsonObject, val layer: JsonObject)

  /**
   * The [Basemap.MapboxStyle.overlays] of [basemap] as sources and layers, bottom to top, for
   * renderers to add after the style loads. Empty for other basemaps, whose style JSON already has
   * its raster layers.
   */
  fun overlays(basemap: Basemap): List<Overlay> =
    (basemap as? Basemap.MapboxStyle)?.overlays.orEmpty().mapIndexed { i, overlay ->
      val id = "app-map-overlay-$i"
      when (overlay) {
        is RasterTileLayer -> Overlay(id, rasterSource(overlay), rasterLayer(id, overlay))
        is VectorLineOverlay -> Overlay(id, vectorSource(overlay), vectorLineLayer(id, overlay))
      }
    }

  private fun rasterSource(tile: RasterTileLayer) = buildJsonObject {
    put("type", "raster")
    val tilesetUrl = tile.tilesetUrl
    if (tilesetUrl.isNullOrBlank()) {
      putJsonArray("tiles") { add(tile.urlTemplate) }
    } else {
      put("url", tilesetUrl)
    }
    put("tileSize", tile.tileSize)
    if (tile.minZoom > 0) put("minzoom", tile.minZoom)
    put("maxzoom", tile.maxZoom)
    tile.bounds?.let { put("bounds", bounds(it)) }
  }

  private fun rasterLayer(source: String, tile: RasterTileLayer) = buildJsonObject {
    put("id", source)
    put("type", "raster")
    put("source", source)
    putJsonObject("paint") {
      put("raster-opacity", tile.opacity)
      put("raster-contrast", tile.contrast)
      put("raster-saturation", tile.saturation)
    }
  }

  /**
   * The zoom range stays on the layer rather than the source: a source `maxzoom` would override the
   * tileset's own and request tiles it doesn't have.
   */
  private fun vectorSource(overlay: VectorLineOverlay) = buildJsonObject {
    put("type", "vector")
    put("url", overlay.tilesetUrl)
    overlay.bounds?.let { put("bounds", bounds(it)) }
  }

  private fun vectorLineLayer(source: String, overlay: VectorLineOverlay) = buildJsonObject {
    put("id", source)
    put("type", "line")
    put("source", source)
    put("source-layer", overlay.sourceLayer)
    if (overlay.minZoom > 0) put("minzoom", overlay.minZoom)
    if (overlay.maxZoom < MAX_LAYER_ZOOM) put("maxzoom", overlay.maxZoom)
    overlay.filter?.let { put("filter", vectorFilter(it)) }
    putJsonObject("paint") {
      put("line-color", color(overlay.color))
      val emphasis = overlay.emphasis
      if (emphasis == null) {
        put("line-width", dp(overlay.width))
      } else {
        putJsonArray("line-width") {
          add("case")
          add(vectorFilter(emphasis.filter))
          add(dp(emphasis.width))
          add(dp(overlay.width))
        }
      }
      put("line-opacity", overlay.opacity)
    }
  }

  /** A `match` filter expression; numeric labels must be integers in Mapbox GL JS. */
  private fun vectorFilter(filter: VectorFeatureFilter): JsonElement {
    val labels =
      when (filter) {
        is VectorFeatureFilter.NumberIn -> filter.values.sorted().map { JsonPrimitive(it) }
        is VectorFeatureFilter.StringIn -> filter.values.sorted().map { JsonPrimitive(it) }
      }
    if (labels.isEmpty()) return JsonPrimitive(false)
    return buildJsonArray {
      add("match")
      add(get(filter.property))
      add(JsonArray(labels))
      add(true)
      add(false)
    }
  }

  /** Style Specification bounds, `[west, south, east, north]`. */
  private fun bounds(bounds: LngLatBounds) = buildJsonArray {
    add(bounds.west)
    add(bounds.south)
    add(bounds.east)
    add(bounds.north)
  }

  private fun circleLayer(
    id: String,
    radius: Double,
    color: String,
    strokeWidth: Double,
    strokeColor: String,
  ) = buildJsonObject {
    put("id", id)
    put("type", "circle")
    put("source", USER_LOCATION_SOURCE)
    putJsonObject("paint") {
      put("circle-radius", radius)
      put("circle-color", color)
      put("circle-stroke-width", strokeWidth)
      put("circle-stroke-color", strokeColor)
    }
  }
}
