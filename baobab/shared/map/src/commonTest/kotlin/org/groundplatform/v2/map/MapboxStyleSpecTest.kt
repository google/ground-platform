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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MapboxStyleSpecTest {
  private fun JsonObject.obj(key: String) = getValue(key).jsonObject

  private fun JsonObject.arr(key: String) = getValue(key).jsonArray

  private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

  @Test
  fun style_mapboxStyleIsItsUrl() {
    assertEquals(
      "mapbox://styles/mapbox/outdoors-v12",
      MapboxStyleSpec.style(Basemap.MapboxOutdoors),
    )
  }

  @Test
  fun style_rasterTilesIsJsonWithOneSourceAndLayerPerTileLayer() {
    val basemap =
      Basemap.RasterTiles(
        listOf(
          RasterTileLayer("https://tiles.example/a/{z}/{x}/{y}.png", opacity = 0.5f),
          RasterTileLayer("https://tiles.example/b/{z}/{x}/{y}.png", tileSize = 512),
        ),
        attribution = "© Example",
        backgroundColor = Color(0xFF000000),
      )
    val json = MapboxStyleSpec.style(basemap)
    assertEquals('{', json.first())
    val style = MapboxStyleSpec.styleJson(basemap)
    val sources = style.obj("sources")
    assertEquals(listOf("ground-map-raster-0", "ground-map-raster-1"), sources.keys.toList())
    assertEquals(512, sources.obj("ground-map-raster-1").str("tileSize").toInt())
    val layers = style.arr("layers").map { it.jsonObject }
    assertEquals(
      listOf("ground-map-background", "ground-map-raster-0", "ground-map-raster-1"),
      layers.map { it.str("id") },
    )
    assertEquals("rgba(0, 0, 0, 1.0)", layers[0].obj("paint").str("background-color"))
    assertEquals(0.5, layers[1].obj("paint").str("raster-opacity").toDouble())
  }

  @Test
  fun styleJson_noneHasOnlyBackground() {
    val style = MapboxStyleSpec.styleJson(Basemap.None())
    assertEquals(emptySet(), style.obj("sources").keys)
    assertEquals(1, style.arr("layers").size)
  }

  @Test
  fun userLocationLayers_drawFromUserLocationSource() {
    val layers = MapboxStyleSpec.userLocationLayers()
    assertEquals(
      listOf(MapboxStyleSpec.USER_LOCATION_HALO_LAYER, MapboxStyleSpec.USER_LOCATION_DOT_LAYER),
      layers.map { it.str("id") },
    )
    layers.forEach { assertEquals(MapboxStyleSpec.USER_LOCATION_SOURCE, it.str("source")) }
    assertEquals("geojson", MapboxStyleSpec.userLocationSource().str("type"))
  }

  @Test
  fun geoJson_closesRingsAndCarriesId() {
    val a = LatLng(0.0, 0.0)
    val b = LatLng(0.0, 1.0)
    val c = LatLng(1.0, 1.0)
    val feature =
      MapFeature("entity:1", Geometry.Polygon(listOf(listOf(a, b, c))), mapOf("kind" to "plot"))
    val json = MapboxStyleSpec.geoJson(listOf(feature)).arr("features").single().jsonObject
    val ring = json.obj("geometry").arr("coordinates").single().jsonArray
    assertEquals(4, ring.size)
    assertEquals(ring.first(), ring.last())
    // GeoJSON positions are [longitude, latitude].
    assertEquals(JsonArray(listOf(JsonPrimitive(1.0), JsonPrimitive(0.0))), ring[1])
    val properties = json.obj("properties")
    assertEquals("entity:1", properties.str(MapboxStyleSpec.FEATURE_ID_PROPERTY))
    assertEquals("plot", properties.str("kind"))
  }

  @Test
  fun userLocationGeoJson_isEmptyWithoutLocation() {
    assertEquals(0, MapboxStyleSpec.userLocationGeoJson(null).arr("features").size)
    assertEquals(1, MapboxStyleSpec.userLocationGeoJson(LatLng(1.0, 2.0)).arr("features").size)
  }

  @Test
  fun expression_matchEncodesCasesAndDefault() {
    val value = StyleValue.Match("variant", linkedMapOf("selected" to 3.dp), 2.dp)
    val json = MapboxStyleSpec.expression(value) { JsonPrimitive(it.value) }
    assertEquals("""["match",["get","variant"],"selected",3.0,2.0]""", json.toString())
  }

  @Test
  fun expression_emptyMatchIsTheDefault() {
    val value = StyleValue.Match("variant", emptyMap(), 1f)
    assertEquals(JsonPrimitive(1f), MapboxStyleSpec.expression(value) { JsonPrimitive(it) })
  }

  @Test
  fun color_isCssRgba() {
    assertEquals("rgba(255, 0, 0, 0.4)", MapboxStyleSpec.color(Color(0x66FF0000)).content)
  }

  @Test
  fun filter_translatesEachKind() {
    val filter =
      FeatureFilter.All(
        listOf(
          FeatureFilter.GeometryTypeIs(GeometryType.LINE_STRING),
          FeatureFilter.Not(FeatureFilter.Equals("kind", "a")),
          FeatureFilter.In("id", setOf("y", "x")),
        )
      )
    assertEquals(
      """["all",["==",["geometry-type"],"LineString"],["!",["==",["get","kind"],"a"]],""" +
        """["match",["get","id"],["x","y"],true,false]]""",
      MapboxStyleSpec.filter(filter).toString(),
    )
  }

  @Test
  fun filter_emptyInMatchesNothing() {
    assertEquals(JsonPrimitive(false), MapboxStyleSpec.filter(FeatureFilter.In("id", emptySet())))
  }

  @Test
  fun symbolLayer_alwaysDrawsIcons() {
    val layer =
      MapLayer.Symbol(
        id = "pins",
        sourceId = "s",
        iconId = StyleValue.Constant("pin"),
        iconAnchor = MarkerAnchor.BOTTOM,
        iconOffset = DpOffset(0.dp, 3.dp),
      )
    val json = MapboxStyleSpec.layer(layer)
    assertEquals("symbol", json.str("type"))
    val layout = json.obj("layout")
    assertEquals("bottom", layout.str("icon-anchor"))
    assertEquals(true, layout.getValue("icon-allow-overlap").jsonPrimitive.content.toBoolean())
    assertEquals(true, layout.getValue("icon-ignore-placement").jsonPrimitive.content.toBoolean())
    assertEquals(
      listOf(0.0, 3.0),
      layout.arr("icon-offset").map { it.jsonPrimitive.content.toDouble() },
    )
  }

  @Test
  fun lineLayer_dashedLinesUseButtCaps() {
    val dashed =
      MapLayer.Line(
        "l",
        "s",
        color = StyleValue.Constant(Color.Black),
        dashPattern = listOf(2f, 1f),
      )
    val json = MapboxStyleSpec.layer(dashed)
    assertEquals("butt", json.obj("layout").str("line-cap"))
    assertEquals(2, json.obj("paint").arr("line-dasharray").size)
  }
}
