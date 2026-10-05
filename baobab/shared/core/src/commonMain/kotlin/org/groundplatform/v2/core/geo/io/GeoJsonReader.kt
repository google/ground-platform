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
package org.groundplatform.v2.core.geo.io

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.groundplatform.v2.core.sampling.GeoCoord

/**
 * Reads GeoJSON ([RFC 7946](https://www.rfc-editor.org/rfc/rfc7946)): a `FeatureCollection`, a
 * single `Feature`, or a bare geometry (including `GeometryCollection`).
 * - `Polygon` / `MultiPolygon` become one [GeoGeometry.Polygon] per part, keeping only the exterior
 *   ring (RFC 7946 section 3.1.6: the first ring is the exterior); holes are dropped with a
 *   warning.
 * - `Point` / `MultiPoint` and `LineString` / `MultiLineString` are split the same way.
 * - Feature properties are kept as text (for strata and attribute columns), plus the feature `id`
 *   as `id` when the properties don't already have one.
 *
 * Coordinates are `[longitude, latitude, (altitude)]` in WGS 84 (RFC 7946 section 4); altitude is
 * ignored.
 */
object GeoJsonReader {

  private val json = Json { isLenient = false }

  fun read(text: String): GeoReadResult {
    val root =
      try {
        json.parseToJsonElement(text)
      } catch (e: Exception) {
        return GeoReadResult.failure(
          "This file isn't valid GeoJSON. Check that it was exported as GeoJSON and try again."
        )
      }
    val reader = Reader()
    when (val obj = root as? JsonObject) {
      null ->
        return GeoReadResult.failure("This file isn't valid GeoJSON. It should contain an object.")
      else -> reader.readRoot(obj)
    }
    if (reader.features.isEmpty() && reader.issues.none { it.severity == GeoIssueSeverity.ERROR }) {
      reader.issues += GeoIssue(GeoIssueSeverity.ERROR, "This file doesn't contain any geometry.")
    }
    val holes = reader.features.sumOf { (it.geometry as? GeoGeometry.Polygon)?.droppedHoles ?: 0 }
    if (holes > 0) {
      reader.issues +=
        GeoIssue(
          GeoIssueSeverity.WARNING,
          "$holes ${if (holes == 1) "hole was" else "holes were"} removed from polygons. " +
            "Ground uses outer boundaries only.",
        )
    }
    return GeoReadResult(reader.features, reader.issues)
  }

  private class Reader {
    val features = mutableListOf<GeoFeature>()
    val issues = mutableListOf<GeoIssue>()

    fun readRoot(obj: JsonObject) {
      when (obj.type) {
        "FeatureCollection" -> {
          val list = obj["features"] as? JsonArray
          if (list == null) {
            issues += GeoIssue(GeoIssueSeverity.ERROR, "The FeatureCollection has no features.")
            return
          }
          list.forEachIndexed { i, f ->
            val feature = f as? JsonObject
            if (feature == null || feature.type != "Feature") {
              issues +=
                GeoIssue(
                  GeoIssueSeverity.ERROR,
                  "Feature ${i + 1} isn't a valid GeoJSON Feature.",
                  i,
                )
            } else {
              readFeature(feature, i)
            }
          }
        }
        "Feature" -> readFeature(obj, 0)
        null -> issues += GeoIssue(GeoIssueSeverity.ERROR, "This file isn't valid GeoJSON.")
        else -> readGeometry(obj, emptyMap(), 0)
      }
    }

    fun readFeature(feature: JsonObject, index: Int) {
      val props = buildMap {
        (feature["properties"] as? JsonObject)?.forEach { (k, v) -> put(k, v.asText()) }
        feature["id"]?.let { id -> if ("id" !in this) put("id", id.asText()) }
      }
      val geometry = feature["geometry"]
      if (geometry == null || geometry is JsonNull) {
        issues +=
          GeoIssue(
            GeoIssueSeverity.WARNING,
            "Feature ${index + 1} has no geometry and was skipped.",
            index,
          )
        return
      }
      val obj = geometry as? JsonObject
      if (obj == null) {
        issues +=
          GeoIssue(GeoIssueSeverity.ERROR, "Feature ${index + 1} has invalid geometry.", index)
        return
      }
      readGeometry(obj, props, index)
    }

    fun readGeometry(geometry: JsonObject, props: Map<String, String>, index: Int) {
      val coords = geometry["coordinates"]
      when (geometry.type) {
        "Point" -> point(coords, index)?.let { add(GeoGeometry.Point(it), props, index) }
        "MultiPoint" ->
          coords.arrayOrEmpty().forEach { c ->
            point(c, index)?.let { add(GeoGeometry.Point(it), props, index) }
          }
        "LineString" -> line(coords, index)?.let { add(it, props, index) }
        "MultiLineString" ->
          coords.arrayOrEmpty().forEach { c -> line(c, index)?.let { add(it, props, index) } }
        "Polygon" -> polygon(coords, index)?.let { add(it, props, index) }
        "MultiPolygon" ->
          coords.arrayOrEmpty().forEach { c -> polygon(c, index)?.let { add(it, props, index) } }
        "GeometryCollection" ->
          (geometry["geometries"] as? JsonArray)?.forEach { g ->
            (g as? JsonObject)?.let { readGeometry(it, props, index) }
          }
        else ->
          issues +=
            GeoIssue(
              GeoIssueSeverity.ERROR,
              "${RingValidator.featureName(index)} has an unsupported geometry type " +
                "\"${geometry.type ?: "unknown"}\".",
              index,
            )
      }
    }

    private fun add(geometry: GeoGeometry, props: Map<String, String>, index: Int) {
      features += GeoFeature(geometry, props, index)
    }

    private fun point(e: JsonElement?, index: Int): GeoCoord? {
      val c = coord(e)
      if (c == null || !RingValidator.isValidCoord(c)) {
        badCoordinates(index)
        return null
      }
      return c
    }

    private fun line(e: JsonElement?, index: Int): GeoGeometry.LineString? {
      val coords = e.arrayOrEmpty().map { coord(it) }
      if (coords.size < 2 || coords.any { it == null || !RingValidator.isValidCoord(it) }) {
        badCoordinates(index)
        return null
      }
      return GeoGeometry.LineString(coords.filterNotNull())
    }

    private fun polygon(e: JsonElement?, index: Int): GeoGeometry.Polygon? {
      val rings = e.arrayOrEmpty()
      val exterior = rings.firstOrNull().arrayOrEmpty().map { coord(it) }
      if (exterior.any { it == null }) {
        badCoordinates(index)
        return null
      }
      val normalized = RingValidator.normalize(exterior.filterNotNull(), index)
      issues += normalized.issues
      val ring = normalized.ring ?: return null
      return GeoGeometry.Polygon(ring, droppedHoles = (rings.size - 1).coerceAtLeast(0))
    }

    private fun badCoordinates(index: Int) {
      issues +=
        GeoIssue(
          GeoIssueSeverity.ERROR,
          "${RingValidator.featureName(index)} has missing or invalid coordinates and was skipped.",
          index,
        )
    }
  }

  private val JsonObject.type: String?
    get() = (this["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content

  private fun JsonElement?.arrayOrEmpty(): List<JsonElement> = (this as? JsonArray) ?: emptyList()

  private fun coord(e: JsonElement?): GeoCoord? {
    val arr = e as? JsonArray ?: return null
    if (arr.size < 2) return null
    val lng = (arr[0] as? JsonPrimitive)?.doubleOrNull ?: return null
    val lat = (arr[1] as? JsonPrimitive)?.doubleOrNull ?: return null
    return GeoCoord(lat = lat, lng = lng)
  }

  private fun JsonElement.asText(): String =
    when (this) {
      is JsonNull -> ""
      is JsonPrimitive -> content
      else -> toString()
    }
}
