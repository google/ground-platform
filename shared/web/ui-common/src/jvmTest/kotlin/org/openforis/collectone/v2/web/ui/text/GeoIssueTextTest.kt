/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.web.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.core.geo.io.GeoFileReader
import org.openforis.collectone.v2.core.geo.io.GeoIssue

/**
 * File problems read in English exactly as they did when the geometry readers wrote English text
 * themselves.
 */
class GeoIssueTextTest {

  private fun english(issue: GeoIssue): String = runBlocking {
    geoIssueText(issue).resolveString()
  }

  private fun englishIssues(fileName: String, text: String): List<String> =
    GeoFileReader.read(fileName, text).issues.map(::english)

  @Test
  fun fileIssues() {
    assertEquals(
      listOf(
        "This file type isn't supported. Upload a GeoJSON (.geojson, .json) or KML (.kml) file.",
        "This file isn't valid GeoJSON. Check that it was exported as GeoJSON and try again.",
        "This file isn't valid GeoJSON. It should contain an object.",
        "This file isn't valid GeoJSON.",
        "This file isn't valid KML. Check that it was exported as KML (not KMZ) and try again.",
        "This file doesn't contain any geometry.",
        "The FeatureCollection has no features.",
      ),
      listOf(
          GeoIssue.UnsupportedFileType,
          GeoIssue.UnreadableGeoJson,
          GeoIssue.GeoJsonNotAnObject,
          GeoIssue.GeoJsonWithoutType,
          GeoIssue.InvalidKml,
          GeoIssue.NoGeometry,
          GeoIssue.FeatureCollectionWithoutFeatures,
        )
        .map(::english),
    )
  }

  @Test
  fun holesRemoved_isWordedForTheCount() {
    assertEquals(
      "1 hole was removed from polygons. Collect One uses outer boundaries only.",
      english(GeoIssue.HolesRemoved(count = 1)),
    )
    assertEquals(
      "3 holes were removed from polygons. Collect One uses outer boundaries only.",
      english(GeoIssue.HolesRemoved(count = 3)),
    )
  }

  @Test
  fun featureIssues_numberFeaturesFromOne() {
    assertEquals(
      listOf(
        "Feature 1 isn't a valid GeoJSON Feature.",
        "Feature 2 has no geometry and was skipped.",
        "Feature 3 has invalid geometry.",
        "Feature 4 has an unsupported geometry type \"Circle\".",
        "Feature 5 has an unsupported geometry type \"unknown\".",
        "Feature 6 has missing or invalid coordinates and was skipped.",
        "Feature 7 has coordinates outside the valid range and was skipped. Use WGS 84 decimal " +
          "degrees (latitude −90 to 90, longitude −180 to 180).",
        "Feature 8 has fewer than 3 distinct corners and was skipped.",
        "Feature 9's outline wasn't closed, so it was closed automatically.",
        "Feature 10's outline crosses itself. Areas and sample plots may be wrong; fix the " +
          "boundary in a GIS tool and upload it again.",
        "Feature 11 may cross the 180° meridian, which isn't supported yet. Split it into parts " +
          "on either side of the meridian.",
      ),
      listOf(
          GeoIssue.InvalidFeature(featureIndex = 0),
          GeoIssue.FeatureWithoutGeometry(featureIndex = 1),
          GeoIssue.InvalidGeometry(featureIndex = 2),
          GeoIssue.UnsupportedGeometryType(featureIndex = 3, type = "Circle"),
          GeoIssue.UnsupportedGeometryType(featureIndex = 4, type = null),
          GeoIssue.InvalidCoordinates(featureIndex = 5),
          GeoIssue.CoordinatesOutOfRange(featureIndex = 6),
          GeoIssue.TooFewCorners(featureIndex = 7),
          GeoIssue.OutlineClosed(featureIndex = 8),
          GeoIssue.SelfIntersection(featureIndex = 9),
          GeoIssue.MayCrossAntimeridian(featureIndex = 10),
        )
        .map(::english),
    )
  }

  @Test
  fun readFile_reportsIssuesInEnglish() {
    val collection =
      """
      {"type": "FeatureCollection", "features": [
        {"type": "Feature", "properties": {}, "geometry": null},
        {"type": "Feature", "properties": {},
         "geometry": {"type": "Polygon", "coordinates": [[[0, 0], [1, 0], [1, 1]]]}}
      ]}
      """

    assertEquals(
      listOf(
        "Feature 1 has no geometry and was skipped.",
        "Feature 2's outline wasn't closed, so it was closed automatically.",
      ),
      englishIssues("plots.geojson", collection),
    )
  }
}
