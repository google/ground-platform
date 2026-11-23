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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.core.geo.GeoCoord

/** Hand-written fixtures; coordinates are arbitrary small squares, not real boundaries. */
class GeoJsonReaderTest {

  private val square =
    "[[36.90, -0.40], [36.95, -0.40], [36.95, -0.45], [36.90, -0.45], [36.90, -0.40]]"

  @Test
  fun polygon_valid_hasNoIssues() {
    val result = GeoJsonReader.read("""{"type": "Polygon", "coordinates": [$square]}""")

    assertEquals(emptyList(), result.issues)
  }

  @Test
  fun polygon_isReadAsOpenExteriorRing() {
    val result = GeoJsonReader.read("""{"type": "Polygon", "coordinates": [$square]}""")

    assertEquals(4, result.polygonRings.single().size)
  }

  @Test
  fun polygon_coordinates_areReadInLngLatOrder() {
    val result = GeoJsonReader.read("""{"type": "Polygon", "coordinates": [$square]}""")

    assertEquals(GeoCoord(lat = -0.40, lng = 36.90), result.polygonRings.single().first())
  }

  @Test
  fun multiPolygon_isSplitIntoParts() {
    val result = GeoJsonReader.read(twoIslandsFeature)

    assertEquals(2, result.polygonRings.size)
  }

  @Test
  fun multiPolygon_holes_areDroppedWithWarning() {
    val result = GeoJsonReader.read(twoIslandsFeature)

    assertEquals(1, result.droppedHoleCount)
    assertEquals(1, result.warnings.size)
    assertEquals(GeoIssue.HolesRemoved(count = 1), result.warnings.single())
  }

  @Test
  fun multiPolygon_parts_keepTheirFeatureSourceIndex() {
    val result = GeoJsonReader.read(twoIslandsFeature)

    assertTrue(result.features.all { it.sourceIndex == 0 })
  }

  @Test
  fun feature_properties_areReadAsStrings() {
    val result = GeoJsonReader.read(twoIslandsFeature)

    val props = result.features.first().properties
    assertEquals("Two islands", props["name"])
    assertEquals("3", props["zone"])
  }

  @Test
  fun feature_id_isKeptAsIdProperty() {
    val result = GeoJsonReader.read(twoIslandsFeature)

    assertEquals("7", result.features.first().properties["id"])
  }

  private val twoIslandsFeature: String
    get() {
      val hole = "[[36.91, -0.41], [36.92, -0.41], [36.92, -0.42], [36.91, -0.41]]"
      val other = "[[37.0, -0.5], [37.1, -0.5], [37.1, -0.6], [37.0, -0.5]]"
      return """
        {"type": "Feature", "id": 7, "properties": {"name": "Two islands", "zone": 3},
         "geometry": {"type": "MultiPolygon", "coordinates": [[$square, $hole], [$other]]}}
        """
    }

  @Test
  fun featureCollection_featureWithoutGeometry_isSkippedWithWarning() {
    val result = GeoJsonReader.read(mixedFeatureCollection)

    assertEquals(2, result.features.size)
    assertEquals(listOf(2), result.warnings.mapNotNull { it.featureIndex })
  }

  @Test
  fun featureCollection_keepsPropertiesPerFeature() {
    val result = GeoJsonReader.read(mixedFeatureCollection)

    assertEquals("forest", result.features[0].properties["stratum"])
  }

  @Test
  fun featureCollection_pointFeature_isReadAsPoint() {
    val result = GeoJsonReader.read(mixedFeatureCollection)

    val point = assertIs<GeoGeometry.Point>(result.features[1].geometry)
    assertEquals(GeoCoord(-0.42, 36.92), point.coord)
  }

  @Test
  fun featureCollection_nestedObjectProperty_isKeptAsJson() {
    val result = GeoJsonReader.read(mixedFeatureCollection)

    assertEquals("""{"a":1}""", result.features[1].properties["tags"])
  }

  @Test
  fun featureCollection_features_keepTheirSourceIndex() {
    val result = GeoJsonReader.read(mixedFeatureCollection)

    assertEquals(1, result.features[1].sourceIndex)
  }

  private val mixedFeatureCollection: String
    get() =
      """
      {"type": "FeatureCollection", "features": [
        {"type": "Feature", "properties": {"stratum": "forest"},
         "geometry": {"type": "Polygon", "coordinates": [$square]}},
        {"type": "Feature", "properties": {"stratum": "cropland", "tags": {"a": 1}},
         "geometry": {"type": "Point", "coordinates": [36.92, -0.42, 1800]}},
        {"type": "Feature", "properties": {}, "geometry": null}
      ]}
      """

  @Test
  fun unclosedRing_isClosed() {
    val result = GeoJsonReader.read(UNCLOSED_SQUARE)

    assertEquals(4, result.polygonRings.single().size)
  }

  @Test
  fun unclosedRing_isWarned() {
    val result = GeoJsonReader.read(UNCLOSED_SQUARE)

    assertEquals(GeoIssue.OutlineClosed(featureIndex = 0), result.warnings.single())
  }

  @Test
  fun degenerateRing_isAnError() {
    val result = GeoJsonReader.read(singlePolygonCollection("[[[0, 0], [1, 1], [0, 0]]]"))

    assertTrue(result.features.isEmpty())
    assertEquals(listOf(0), result.errors.mapNotNull { it.featureIndex })
  }

  @Test
  fun outOfRangeRing_isAnError() {
    val result = GeoJsonReader.read(singlePolygonCollection("[[[0, 0], [200, 0], [1, 1], [0, 0]]]"))

    assertTrue(result.features.isEmpty())
    assertEquals(listOf(0), result.errors.mapNotNull { it.featureIndex })
  }

  private fun singlePolygonCollection(coordinates: String) =
    """
    {"type": "FeatureCollection", "features": [
      {"type": "Feature", "properties": {},
       "geometry": {"type": "Polygon", "coordinates": $coordinates}}
    ]}
    """

  @Test
  fun selfIntersectingRing_isKept() {
    val result = GeoJsonReader.read(BOW_TIE)

    assertEquals(1, result.polygonRings.size)
  }

  @Test
  fun selfIntersectingRing_isReported() {
    val result = GeoJsonReader.read(BOW_TIE)

    assertTrue(result.warnings.any { it is GeoIssue.SelfIntersection })
  }

  @Test
  fun invalidJson_isAnError() {
    val result = GeoJsonReader.read("{not json")
    assertTrue(result.features.isEmpty())
    assertEquals(1, result.errors.size)
  }

  @Test
  fun detect_geojsonExtension_isGeoJson() {
    assertEquals(GeoFileFormat.GEOJSON, GeoFileFormat.detect("area.geojson", ""))
  }

  @Test
  fun detect_kmlExtension_isKmlIgnoringCase() {
    assertEquals(GeoFileFormat.KML, GeoFileFormat.detect("area.KML", ""))
  }

  @Test
  fun detect_jsonContentWithoutExtension_isGeoJson() {
    assertEquals(GeoFileFormat.GEOJSON, GeoFileFormat.detect("download", " {\"type\": 1}"))
  }

  @Test
  fun detect_xmlContentWithoutExtension_isKml() {
    assertEquals(GeoFileFormat.KML, GeoFileFormat.detect("download", "<?xml version=\"1.0\"?>"))
  }

  @Test
  fun detect_unsupportedFile_isNull() {
    assertNull(GeoFileFormat.detect("area.shp", "binary"))
  }

  @Test
  fun fileReader_unsupportedFile_isAnError() {
    assertEquals(1, GeoFileReader.read("area.shp", "binary").errors.size)
  }

  private companion object {
    const val UNCLOSED_SQUARE =
      """{"type": "Polygon", "coordinates": [[[0, 0], [1, 0], [1, 1], [0, 1]]]}"""

    /** A bow tie: (0,0) → (1,1) → (1,0) → (0,1) crosses itself. */
    const val BOW_TIE =
      """{"type": "Polygon", "coordinates": [[[0, 0], [1, 1], [1, 0], [0, 1], [0, 0]]]}"""
  }
}

class KmlReaderTest {

  private val kml =
    """
    <?xml version="1.0" encoding="UTF-8"?>
    <kml xmlns="http://www.opengis.net/kml/2.2">
      <Document>
        <!-- A comment with <Placemark> inside should be ignored -->
        <Placemark>
          <name>North block &amp; ridge</name>
          <ExtendedData>
            <Data name="stratum"><value>forest</value></Data>
            <SchemaData schemaUrl="#s"><SimpleData name="code">N1</SimpleData></SchemaData>
          </ExtendedData>
          <Polygon>
            <outerBoundaryIs><LinearRing><coordinates>
              36.90,-0.40,0 36.95,-0.40,0 36.95,-0.45,0 36.90,-0.45,0 36.90,-0.40,0
            </coordinates></LinearRing></outerBoundaryIs>
            <innerBoundaryIs><LinearRing><coordinates>
              36.91,-0.41 36.92,-0.41 36.92,-0.42 36.91,-0.41
            </coordinates></LinearRing></innerBoundaryIs>
          </Polygon>
        </Placemark>
        <Placemark>
          <name>Islands</name>
          <MultiGeometry>
            <Polygon><outerBoundaryIs><LinearRing><coordinates>
              37.0,-0.5 37.1,-0.5 37.1,-0.6 37.0,-0.5
            </coordinates></LinearRing></outerBoundaryIs></Polygon>
            <Polygon><outerBoundaryIs><LinearRing><coordinates>
              37.2,-0.5 37.3,-0.5 37.3,-0.6 37.2,-0.5
            </coordinates></LinearRing></outerBoundaryIs></Polygon>
          </MultiGeometry>
        </Placemark>
        <Placemark>
          <name>Well</name>
          <Point><coordinates>36.93,-0.43,1700</coordinates></Point>
        </Placemark>
      </Document>
    </kml>
    """
      .trimIndent()

  @Test
  fun placemarks_multiGeometry_isSplitIntoOneFeaturePerPart() {
    val result = KmlReader.read(kml)

    assertEquals(4, result.features.size)
    assertEquals(3, result.polygonRings.size)
    assertEquals(listOf(0, 1, 1, 2), result.features.map { it.sourceIndex })
  }

  @Test
  fun placemark_name_isEntityDecoded() {
    val result = KmlReader.read(kml)

    assertEquals("North block & ridge", result.features.first().properties["name"])
  }

  @Test
  fun placemark_extendedData_readsDataAndSimpleDataProperties() {
    val result = KmlReader.read(kml)

    val properties = result.features.first().properties
    assertEquals("forest", properties["stratum"])
    assertEquals("N1", properties["code"])
  }

  @Test
  fun placemark_polygon_isReadAsOpenRing() {
    val result = KmlReader.read(kml)

    assertEquals(4, (result.features.first().geometry as GeoGeometry.Polygon).ring.size)
  }

  @Test
  fun placemark_point_isReadInLngLatOrder() {
    val result = KmlReader.read(kml)

    val well = assertIs<GeoGeometry.Point>(result.features.last().geometry)
    assertEquals(GeoCoord(-0.43, 36.93), well.coord)
  }

  @Test
  fun innerBoundaries_areDroppedWithWarning() {
    val result = KmlReader.read(kml)

    assertEquals(1, result.droppedHoleCount)
    assertTrue(result.warnings.any { it is GeoIssue.HolesRemoved })
  }

  @Test
  fun validDocument_hasNoErrors() {
    val result = KmlReader.read(kml)

    assertEquals(emptyList(), result.errors)
  }

  @Test
  fun emptyDocument_isAnError() {
    val result = KmlReader.read("<kml><Document></Document></kml>")
    assertTrue(result.features.isEmpty())
    assertEquals(1, result.errors.size)
  }

  @Test
  fun badCoordinates_areReported() {
    val text =
      """
      <kml><Placemark><Polygon><outerBoundaryIs><LinearRing>
        <coordinates>a,b 1,1 2,2 a,b</coordinates>
      </LinearRing></outerBoundaryIs></Polygon></Placemark></kml>
      """
    val result = KmlReader.read(text)
    assertTrue(result.features.isEmpty())
    assertNotNull(result.errors.singleOrNull())
  }
}

class RingValidatorTest {

  @Test
  fun findSelfIntersection_simpleLargeRing_findsNone() {
    assertNull(RingValidator.findSelfIntersection(circle(vertexCount = 400)))
  }

  @Test
  fun findSelfIntersection_largeRingWithSwappedVertices_detectsCrossing() {
    // Swapping two vertices of a simple ring makes its edges cross.
    val crossed = circle(vertexCount = 400).toMutableList().apply { add(10, removeAt(200)) }

    assertNotNull(RingValidator.findSelfIntersection(crossed))
  }

  private fun circle(vertexCount: Int): List<GeoCoord> =
    (0 until vertexCount).map { i ->
      val a = 2 * kotlin.math.PI * i / vertexCount
      GeoCoord(lat = kotlin.math.sin(a), lng = kotlin.math.cos(a))
    }

  @Test
  fun isCounterClockwise_counterClockwiseRing_isTrue() {
    assertTrue(RingValidator.isCounterClockwise(counterClockwiseTriangle))
  }

  @Test
  fun isCounterClockwise_clockwiseRing_isFalse() {
    assertTrue(!RingValidator.isCounterClockwise(counterClockwiseTriangle.reversed()))
  }

  private val counterClockwiseTriangle =
    listOf(GeoCoord(0.0, 0.0), GeoCoord(0.0, 1.0), GeoCoord(1.0, 1.0))

  @Test
  fun antimeridianCrossing_keepsRing() {
    val result = RingValidator.normalize(antimeridianRing, featureIndex = 0)

    assertNotNull(result.ring)
  }

  @Test
  fun antimeridianCrossing_isWarned() {
    val result = RingValidator.normalize(antimeridianRing, featureIndex = 0)

    assertEquals(listOf(GeoIssue.MayCrossAntimeridian(featureIndex = 0)), result.issues)
  }

  private val antimeridianRing =
    listOf(GeoCoord(0.0, 179.0), GeoCoord(0.0, -179.0), GeoCoord(1.0, -179.0), GeoCoord(0.0, 179.0))
}

class GeoSimplifierTest {

  /** A straight line along the equator with one tiny bump at its middle point. */
  private val lineWithBump =
    (0..10).map { GeoCoord(lat = if (it == 5) 0.00001 else 0.0, lng = it * 0.1) }

  @Test
  fun simplifyLine_toleranceAboveBump_keepsOnlyEndpoints() {
    assertEquals(2, GeoSimplifier.simplifyLine(lineWithBump, 0.001).size)
  }

  @Test
  fun simplifyLine_toleranceBelowBump_keepsOnlyTheBump() {
    // Points next to the bump are ~8e-6° off the simplified segments, so keep them out too.
    assertEquals(3, GeoSimplifier.simplifyLine(lineWithBump, 0.000009).size)
  }

  @Test
  fun simplifyRingToMaxVertices_capsVertexCountAndKeepsAPolygon() {
    val simplified = GeoSimplifier.simplifyRingToMaxVertices(wavyRing, 200)

    assertTrue(simplified.size in 3..200, "size ${simplified.size}")
  }

  @Test
  fun simplifyRingToMaxVertices_ringWithinCap_isUnchanged() {
    assertEquals(wavyRing, GeoSimplifier.simplifyRingToMaxVertices(wavyRing, WAVY_RING_VERTICES))
  }

  private val wavyRing =
    (0 until WAVY_RING_VERTICES).map { i ->
      val a = 2 * kotlin.math.PI * i / WAVY_RING_VERTICES
      val r = 1.0 + 0.01 * kotlin.math.sin(a * 50)
      GeoCoord(lat = r * kotlin.math.sin(a), lng = r * kotlin.math.cos(a))
    }

  private companion object {
    const val WAVY_RING_VERTICES = 5000
  }
}
