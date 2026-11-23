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
package org.openforis.collectone.v2.core.geo.io

import org.openforis.collectone.v2.core.geo.GeoCoord
import org.openforis.collectone.v2.core.sampling.Ring

/**
 * Geometry read from a GeoJSON or KML file, reduced to the shapes Collect One stores: points, lines
 * and single-ring polygons (`forms.GeoShape`).
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
 * A problem found while reading or validating geometry, as a semantic value: the UI words each kind
 * for organizers (see `docs/ux/content-guidelines.md`), saying what happened and how to fix it.
 */
sealed interface GeoIssue {
  val severity: GeoIssueSeverity

  /** [GeoFeature.sourceIndex] of the feature the issue is about, or `null` for the whole file. */
  val featureIndex: Int?
    get() = null

  /** A problem with the whole file. */
  sealed interface FileIssue : GeoIssue

  /** A problem with one feature (or KML placemark) of the file. */
  sealed interface FeatureIssue : GeoIssue {
    override val featureIndex: Int
  }

  /** The file is neither GeoJSON nor KML. */
  data object UnsupportedFileType : FileIssue {
    override val severity = GeoIssueSeverity.ERROR
  }

  /** The file couldn't be parsed as JSON. */
  data object UnreadableGeoJson : FileIssue {
    override val severity = GeoIssueSeverity.ERROR
  }

  /** The JSON file's root isn't an object. */
  data object GeoJsonNotAnObject : FileIssue {
    override val severity = GeoIssueSeverity.ERROR
  }

  /** The JSON object has no GeoJSON `type`. */
  data object GeoJsonWithoutType : FileIssue {
    override val severity = GeoIssueSeverity.ERROR
  }

  /** The XML file isn't KML. */
  data object InvalidKml : FileIssue {
    override val severity = GeoIssueSeverity.ERROR
  }

  /** The file was read but holds no usable geometry. */
  data object NoGeometry : FileIssue {
    override val severity = GeoIssueSeverity.ERROR
  }

  /** A `FeatureCollection` without a `features` array. */
  data object FeatureCollectionWithoutFeatures : FileIssue {
    override val severity = GeoIssueSeverity.ERROR
  }

  /** [count] polygon holes were dropped: polygons keep their outer boundary only. */
  data class HolesRemoved(val count: Int) : FileIssue {
    override val severity = GeoIssueSeverity.WARNING
  }

  /** An entry of a `FeatureCollection` isn't a GeoJSON `Feature`. */
  data class InvalidFeature(override val featureIndex: Int) : FeatureIssue {
    override val severity = GeoIssueSeverity.ERROR
  }

  /** A feature without geometry, skipped. */
  data class FeatureWithoutGeometry(override val featureIndex: Int) : FeatureIssue {
    override val severity = GeoIssueSeverity.WARNING
  }

  /** A feature whose `geometry` isn't an object. */
  data class InvalidGeometry(override val featureIndex: Int) : FeatureIssue {
    override val severity = GeoIssueSeverity.ERROR
  }

  /** A geometry of a GeoJSON [type] Collect One doesn't read (`null` when it has none). */
  data class UnsupportedGeometryType(override val featureIndex: Int, val type: String?) :
    FeatureIssue {
    override val severity = GeoIssueSeverity.ERROR
  }

  /** A geometry with missing or unparseable coordinates, skipped. */
  data class InvalidCoordinates(override val featureIndex: Int) : FeatureIssue {
    override val severity = GeoIssueSeverity.ERROR
  }

  /** A polygon with coordinates outside WGS 84 degrees, skipped. */
  data class CoordinatesOutOfRange(override val featureIndex: Int) : FeatureIssue {
    override val severity = GeoIssueSeverity.ERROR
  }

  /** A polygon with fewer than 3 distinct corners, skipped. */
  data class TooFewCorners(override val featureIndex: Int) : FeatureIssue {
    override val severity = GeoIssueSeverity.ERROR
  }

  /** A polygon whose outline wasn't closed, closed automatically. */
  data class OutlineClosed(override val featureIndex: Int) : FeatureIssue {
    override val severity = GeoIssueSeverity.WARNING
  }

  /** A polygon whose outline crosses itself (kept as is). */
  data class SelfIntersection(override val featureIndex: Int) : FeatureIssue {
    override val severity = GeoIssueSeverity.WARNING
  }

  /** A polygon that may cross the 180° meridian, which isn't supported (kept as is). */
  data class MayCrossAntimeridian(override val featureIndex: Int) : FeatureIssue {
    override val severity = GeoIssueSeverity.WARNING
  }
}

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
    fun failure(issue: GeoIssue.FileIssue): GeoReadResult =
      GeoReadResult(emptyList(), listOf(issue))
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
      null -> GeoReadResult.failure(GeoIssue.UnsupportedFileType)
    }
}
