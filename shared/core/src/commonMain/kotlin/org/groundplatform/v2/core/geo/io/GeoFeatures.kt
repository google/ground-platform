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
import org.groundplatform.v2.core.sampling.Ring

/**
 * Geometry read from a GeoJSON or KML file, reduced to the shapes Ground stores: points, lines and
 * single-ring polygons (`forms.GeoShape`).
 *
 * Multi-part geometries are split into one [GeoFeature] per part, and polygon holes are dropped
 * (with a warning), so every polygon is a single exterior ring.
 */
sealed interface GeoGeometry {
  data class Point(val coord: GeoCoord) : GeoGeometry

  data class LineString(val coords: List<GeoCoord>) : GeoGeometry

  /**
   * A polygon's exterior ring, **open**: the closing vertex that repeats the first one is removed.
   * [droppedHoles] counts the interior rings that were discarded.
   */
  data class Polygon(val ring: Ring, val droppedHoles: Int = 0) : GeoGeometry
}

/**
 * One feature (or one part of a multi-part feature) read from a file.
 *
 * @property sourceIndex 0-based index of the feature (or KML placemark) in the file, shared by all
 *   parts of a multi-part feature.
 * @property properties Feature properties as text. Nested JSON values are kept as their JSON text.
 */
data class GeoFeature(
  val geometry: GeoGeometry,
  val properties: Map<String, String> = emptyMap(),
  val sourceIndex: Int = 0,
)

/** How serious a [GeoIssue] is. */
enum class GeoIssueSeverity {
  /** The file was read, but something was changed or may need attention. */
  WARNING,

  /** Part of the file (or all of it) couldn't be used. */
  ERROR,
}

/**
 * A problem found while reading or validating geometry. Messages are written for organizers (see
 * `docs/ux/content-guidelines.md`): what happened and how to fix it.
 */
data class GeoIssue(
  val severity: GeoIssueSeverity,
  val message: String,
  /** [GeoFeature.sourceIndex] of the feature the issue is about, if any. */
  val featureIndex: Int? = null,
)

/** Everything read from one file: the usable features plus warnings and errors. */
data class GeoReadResult(val features: List<GeoFeature>, val issues: List<GeoIssue> = emptyList()) {
  val errors: List<GeoIssue>
    get() = issues.filter { it.severity == GeoIssueSeverity.ERROR }

  val warnings: List<GeoIssue>
    get() = issues.filter { it.severity == GeoIssueSeverity.WARNING }

  /** Polygon features only. */
  val polygons: List<GeoFeature>
    get() = features.filter { it.geometry is GeoGeometry.Polygon }

  /** Exterior rings of all polygon features, one per part (open rings). */
  val polygonRings: List<Ring>
    get() = features.mapNotNull { (it.geometry as? GeoGeometry.Polygon)?.ring }

  /** Total number of polygon holes that were dropped. */
  val droppedHoleCount: Int
    get() = features.sumOf { (it.geometry as? GeoGeometry.Polygon)?.droppedHoles ?: 0 }

  companion object {
    fun failure(message: String): GeoReadResult =
      GeoReadResult(emptyList(), listOf(GeoIssue(GeoIssueSeverity.ERROR, message)))
  }
}

/** Supported geometry file formats. */
enum class GeoFileFormat(val label: String, val extensions: List<String>) {
  GEOJSON("GeoJSON", listOf("geojson", "json")),
  KML("KML", listOf("kml"));

  companion object {
    /**
     * Picks the format from [fileName]'s extension, falling back to sniffing [text] (XML starts
     * with `<`, JSON with `{`). Returns `null` if neither works.
     */
    fun detect(fileName: String, text: String): GeoFileFormat? {
      val ext = fileName.substringAfterLast('.', "").lowercase()
      entries
        .firstOrNull { ext in it.extensions }
        ?.let {
          return it
        }
      val start = text.trimStart().firstOrNull()
      return when (start) {
        '{' -> GEOJSON
        '<' -> KML
        else -> null
      }
    }
  }
}

/** Reads a GeoJSON or KML file, choosing the reader with [GeoFileFormat.detect]. */
object GeoFileReader {
  fun read(fileName: String, text: String): GeoReadResult =
    when (GeoFileFormat.detect(fileName, text)) {
      GeoFileFormat.GEOJSON -> GeoJsonReader.read(text)
      GeoFileFormat.KML -> KmlReader.read(text)
      null ->
        GeoReadResult.failure(
          "This file type isn't supported. Upload a GeoJSON (.geojson, .json) or KML (.kml) file."
        )
    }
}
