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

import org.groundplatform.v2.core.sampling.GeoCoord

/**
 * Reads the geometry of a KML 2.2 document ([OGC 07-147r2](https://www.ogc.org/standard/kml/)).
 *
 * Each `Placemark` becomes one or more [GeoFeature]s (one per `Point`, `LineString` or `Polygon`,
 * including those inside `MultiGeometry`). For polygons only `outerBoundaryIs` is kept;
 * `innerBoundaryIs` rings (holes) are dropped with a warning. Placemark properties come from
 * `name`, `description`, `ExtendedData/Data` and `ExtendedData/SchemaData/SimpleData`.
 *
 * This is a small, dependency-free scanner rather than a full XML parser: it ignores styles,
 * namespaces prefixes, and everything else that doesn't carry geometry or properties. KMZ (zipped
 * KML) isn't supported.
 */
object KmlReader {

  fun read(text: String): GeoReadResult {
    val doc = stripComments(text)
    if (!Regex("<(?:\\w+:)?kml\\b", RegexOption.IGNORE_CASE).containsMatchIn(doc)) {
      if (!doc.trimStart().startsWith("<")) {
        return GeoReadResult.failure(
          "This file isn't valid KML. Check that it was exported as KML (not KMZ) and try again."
        )
      }
    }
    val features = mutableListOf<GeoFeature>()
    val issues = mutableListOf<GeoIssue>()
    val placemarks = elements(doc, "Placemark")
    val blocks = placemarks.ifEmpty { listOf(doc) }
    blocks.forEachIndexed { index, block ->
      val props = if (placemarks.isEmpty()) emptyMap() else placemarkProperties(block)
      val before = features.size
      // Geometry elements in document order.
      geometryRegex.findAll(block).forEach { m ->
        val tag = m.groupValues[1]
        val body = m.groupValues[2]
        when (tag) {
          "Point" -> {
            val c = coordinates(body).firstOrNull()
            if (c == null || !RingValidator.isValidCoord(c)) {
              issues += badCoordinates(index)
            } else {
              features += GeoFeature(GeoGeometry.Point(c), props, index)
            }
          }
          "LineString" -> {
            val cs = coordinates(body)
            if (cs.size < 2 || cs.any { !RingValidator.isValidCoord(it) }) {
              issues += badCoordinates(index)
            } else {
              features += GeoFeature(GeoGeometry.LineString(cs), props, index)
            }
          }
          "Polygon" -> {
            val outer = elements(body, "outerBoundaryIs").firstOrNull()
            if (outer == null) {
              issues += badCoordinates(index)
            } else {
              val normalized = RingValidator.normalize(coordinates(outer), index)
              issues += normalized.issues
              normalized.ring?.let { ring ->
                val holes = elements(body, "innerBoundaryIs").size
                features += GeoFeature(GeoGeometry.Polygon(ring, holes), props, index)
              }
            }
          }
        }
      }
      if (
        placemarks.isNotEmpty() &&
          features.size == before &&
          issues.none { it.featureIndex == index }
      ) {
        issues +=
          GeoIssue(
            GeoIssueSeverity.WARNING,
            "Feature ${index + 1} has no geometry and was skipped.",
            index,
          )
      }
    }
    if (features.isEmpty() && issues.none { it.severity == GeoIssueSeverity.ERROR }) {
      issues += GeoIssue(GeoIssueSeverity.ERROR, "This file doesn't contain any geometry.")
    }
    val holes = features.sumOf { (it.geometry as? GeoGeometry.Polygon)?.droppedHoles ?: 0 }
    if (holes > 0) {
      issues +=
        GeoIssue(
          GeoIssueSeverity.WARNING,
          "$holes ${if (holes == 1) "hole was" else "holes were"} removed from polygons. " +
            "Ground uses outer boundaries only.",
        )
    }
    return GeoReadResult(features, issues)
  }

  /** Matches `Point`, `LineString` and `Polygon` elements (with optional namespace prefix). */
  private val geometryRegex =
    Regex("<(?:\\w+:)?(Point|LineString|Polygon)\\b[^>]*>([\\s\\S]*?)</(?:\\w+:)?\\1\\s*>")

  private fun stripComments(text: String): String = text.replace(Regex("<!--[\\s\\S]*?-->"), "")

  /** Inner text of every `<tag>…</tag>` element (non-nested; namespace prefixes allowed). */
  private fun elements(text: String, tag: String): List<String> =
    Regex("<(?:\\w+:)?$tag\\b[^>]*>([\\s\\S]*?)</(?:\\w+:)?$tag\\s*>")
      .findAll(text)
      .map { it.groupValues[1] }
      .toList()

  private fun placemarkProperties(block: String): Map<String, String> = buildMap {
    // Only direct text children; geometry and ExtendedData are handled separately.
    val withoutGeometry = geometryRegex.replace(block, "")
    elements(withoutGeometry, "name").firstOrNull()?.let { put("name", text(it)) }
    elements(withoutGeometry, "description").firstOrNull()?.let { put("description", text(it)) }
    Regex(
        "<(?:\\w+:)?Data\\b[^>]*\\bname\\s*=\\s*\"([^\"]*)\"[^>]*>([\\s\\S]*?)</(?:\\w+:)?Data\\s*>"
      )
      .findAll(withoutGeometry)
      .forEach { m ->
        val value = elements(m.groupValues[2], "value").firstOrNull().orEmpty()
        put(decode(m.groupValues[1]), text(value))
      }
    Regex(
        "<(?:\\w+:)?SimpleData\\b[^>]*\\bname\\s*=\\s*\"([^\"]*)\"[^>]*>([\\s\\S]*?)</(?:\\w+:)?SimpleData\\s*>"
      )
      .findAll(withoutGeometry)
      .forEach { m -> put(decode(m.groupValues[1]), text(m.groupValues[2])) }
  }

  /** Parses a KML `coordinates` list: whitespace-separated `lng,lat[,alt]` tuples. */
  private fun coordinates(text: String): List<GeoCoord> {
    val body = elements(text, "coordinates").firstOrNull() ?: return emptyList()
    return body
      .trim()
      .split(Regex("\\s+"))
      .filter { it.isNotEmpty() }
      .map { tuple ->
        val parts = tuple.split(',')
        val lng = parts.getOrNull(0)?.toDoubleOrNull() ?: Double.NaN
        val lat = parts.getOrNull(1)?.toDoubleOrNull() ?: Double.NaN
        GeoCoord(lat = lat, lng = lng)
      }
  }

  private fun text(raw: String): String {
    val trimmed = raw.trim()
    val cdata = Regex("^<!\\[CDATA\\[([\\s\\S]*)\\]\\]>$").find(trimmed)
    return cdata?.groupValues?.get(1)?.trim() ?: decode(trimmed)
  }

  private fun decode(s: String): String =
    s.replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .replace("&apos;", "'")
      .replace("&amp;", "&")

  private fun badCoordinates(index: Int) =
    GeoIssue(
      GeoIssueSeverity.ERROR,
      "${RingValidator.featureName(index)} has missing or invalid coordinates and was skipped.",
      index,
    )
}
