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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.sampling.GeoCoord

/** Hand-written fixtures; coordinates are arbitrary small squares, not real boundaries. */
class GeoJsonReaderTest {

  private val square =
    "[[36.90, -0.40], [36.95, -0.40], [36.95, -0.45], [36.90, -0.45], [36.90, -0.40]]"

  @Test
  fun polygon_isReadAsOpenExteriorRing() {
    val result = GeoJsonReader.read("""{"type": "Polygon", "coordinates": [$square]}""")
    assertEquals(emptyList(), result.issues)
    val ring = result.polygonRings.single()
    assertEquals(4, ring.size)
    assertEquals(GeoCoord(lat = -0.40, lng = 36.90), ring.first())
  }

  @Test
  fun multiPolygon_isSplitIntoParts_andHolesAreDroppedWithWarning() {
    val hole = "[[36.91, -0.41], [36.92, -0.41], [36.92, -0.42], [36.91, -0.41]]"
    val other = "[[37.0, -0.5], [37.1, -0.5], [37.1, -0.6], [37.0, -0.5]]"
    val text =
      """
      {"type": "Feature", "id": 7, "properties": {"name": "Two islands", "zone": 3},
       "geometry": {"type": "MultiPolygon", "coordinates": [[$square, $hole], [$other]]}}
      """
    val result = GeoJsonReader.read(text)
    assertEquals(2, result.polygonRings.size)
    assertEquals(1, result.droppedHoleCount)
    assertEquals(1, result.warnings.size)
    assertTrue(result.warnings.single().message.contains("hole"))
    val props = result.features.first().properties
    assertEquals("Two islands", props["name"])
    assertEquals("3", props["zone"])
    assertEquals("7", props["id"])
    assertTrue(result.features.all { it.sourceIndex == 0 })
  }

  @Test
  fun featureCollection_keepsPropertiesPerFeature_andMixedGeometry() {
    val text =
      """
      {"type": "FeatureCollection", "features": [
        {"type": "Feature", "properties": {"stratum": "forest"},
         "geometry": {"type": "Polygon", "coordinates": [$square]}},
        {"type": "Feature", "properties": {"stratum": "cropland", "tags": {"a": 1}},
         "geometry": {"type": "Point", "coordinates": [36.92, -0.42, 1800]}},
        {"type": "Feature", "properties": {}, "geometry": null}
      ]}
      """
    val result = GeoJsonReader.read(text)
    assertEquals(2, result.features.size)
    assertEquals("forest", result.features[0].properties["stratum"])
    val point = assertIs<GeoGeometry.Point>(result.features[1].geometry)
    assertEquals(GeoCoord(-0.42, 36.92), point.coord)
    assertEquals("""{"a":1}""", result.features[1].properties["tags"])
    assertEquals(1, result.features[1].sourceIndex)
    // The feature without geometry is skipped with a warning.
    assertEquals(listOf(2), result.warnings.mapNotNull { it.featureIndex })
  }

  @Test
  fun unclosedRing_isClosedWithWarning() {
    val text = """{"type": "Polygon", "coordinates": [[[0, 0], [1, 0], [1, 1], [0, 1]]]}"""
    val result = GeoJsonReader.read(text)
    assertEquals(4, result.polygonRings.single().size)
    assertTrue(result.warnings.single().message.contains("wasn't closed"))
  }

  @Test
  fun degenerateAndOutOfRangeRings_areErrors() {
    val text =
      """
      {"type": "FeatureCollection", "features": [
        {"type": "Feature", "properties": {},
         "geometry": {"type": "Polygon", "coordinates": [[[0, 0], [1, 1], [0, 0]]]}},
        {"type": "Feature", "properties": {},
         "geometry": {"type": "Polygon", "coordinates": [[[0, 0], [200, 0], [1, 1], [0, 0]]]}}
      ]}
      """
    val result = GeoJsonReader.read(text)
    assertTrue(result.features.isEmpty())
    assertEquals(listOf(0, 1), result.errors.mapNotNull { it.featureIndex })
  }

  @Test
  fun selfIntersectingRing_isKeptButReported() {
    // A bow tie: (0,0) → (1,1) → (1,0) → (0,1) crosses itself.
    val text = """{"type": "Polygon", "coordinates": [[[0, 0], [1, 1], [1, 0], [0, 1], [0, 0]]]}"""
    val result = GeoJsonReader.read(text)
    assertEquals(1, result.polygonRings.size)
    assertTrue(result.warnings.any { it.message.contains("crosses itself") })
  }

  @Test
  fun invalidJson_isAnError() {
    val result = GeoJsonReader.read("{not json")
    assertTrue(result.features.isEmpty())
    assertEquals(1, result.errors.size)
  }

  @Test
  fun fileReader_detectsFormatByExtensionOrContent() {
    assertEquals(GeoFileFormat.GEOJSON, GeoFileFormat.detect("area.geojson", ""))
    assertEquals(GeoFileFormat.KML, GeoFileFormat.detect("area.KML", ""))
    assertEquals(GeoFileFormat.GEOJSON, GeoFileFormat.detect("download", " {\"type\": 1}"))
    assertEquals(GeoFileFormat.KML, GeoFileFormat.detect("download", "<?xml version=\"1.0\"?>"))
    assertNull(GeoFileFormat.detect("area.shp", "binary"))
    assertEquals(1, GeoFileReader.read("area.shp", "binary").errors.size)
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
  fun placemarks_areReadWithPropertiesAndParts() {
    val result = KmlReader.read(kml)
    assertEquals(4, result.features.size)
    assertEquals(3, result.polygonRings.size)
    val first = result.features.first()
    assertEquals("North block & ridge", first.properties["name"])
    assertEquals("forest", first.properties["stratum"])
    assertEquals("N1", first.properties["code"])
    assertEquals(4, (first.geometry as GeoGeometry.Polygon).ring.size)
    assertEquals(listOf(0, 1, 1, 2), result.features.map { it.sourceIndex })
    val well = assertIs<GeoGeometry.Point>(result.features.last().geometry)
    assertEquals(GeoCoord(-0.43, 36.93), well.coord)
  }

  @Test
  fun innerBoundaries_areDroppedWithWarning() {
    val result = KmlReader.read(kml)
    assertEquals(1, result.droppedHoleCount)
    assertTrue(result.warnings.any { it.message.contains("hole") })
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
  fun findSelfIntersection_detectsCrossingInLargeRing() {
    // A 400-vertex circle-ish ring is simple...
    val n = 400
    val circle =
      (0 until n).map { i ->
        val a = 2 * kotlin.math.PI * i / n
        GeoCoord(lat = kotlin.math.sin(a), lng = kotlin.math.cos(a))
      }
    assertNull(RingValidator.findSelfIntersection(circle))
    // ...until two vertices are swapped, which makes edges cross.
    val crossed = circle.toMutableList().apply { add(10, removeAt(200)) }
    assertNotNull(RingValidator.findSelfIntersection(crossed))
  }

  @Test
  fun isCounterClockwise() {
    val ccw = listOf(GeoCoord(0.0, 0.0), GeoCoord(0.0, 1.0), GeoCoord(1.0, 1.0))
    assertTrue(RingValidator.isCounterClockwise(ccw))
    assertTrue(!RingValidator.isCounterClockwise(ccw.reversed()))
  }

  @Test
  fun antimeridianCrossing_isWarned() {
    val ring = listOf(GeoCoord(0.0, 179.0), GeoCoord(0.0, -179.0), GeoCoord(1.0, -179.0))
    val result = RingValidator.normalize(ring + ring.first())
    assertNotNull(result.ring)
    assertTrue(result.issues.any { it.message.contains("180°") })
  }
}

class GeoSimplifierTest {

  @Test
  fun simplifyLine_removesNearlyCollinearPoints() {
    val line = (0..10).map { GeoCoord(lat = if (it == 5) 0.00001 else 0.0, lng = it * 0.1) }
    assertEquals(2, GeoSimplifier.simplifyLine(line, 0.001).size)
    // Points next to the bump are ~8e-6° off the simplified segments, so keep them out too.
    assertEquals(3, GeoSimplifier.simplifyLine(line, 0.000009).size)
  }

  @Test
  fun simplifyRingToMaxVertices_capsVertexCountAndKeepsAPolygon() {
    val n = 5000
    val ring =
      (0 until n).map { i ->
        val a = 2 * kotlin.math.PI * i / n
        val r = 1.0 + 0.01 * kotlin.math.sin(a * 50)
        GeoCoord(lat = r * kotlin.math.sin(a), lng = r * kotlin.math.cos(a))
      }
    val simplified = GeoSimplifier.simplifyRingToMaxVertices(ring, 200)
    assertTrue(simplified.size in 3..200, "size ${simplified.size}")
    assertEquals(ring, GeoSimplifier.simplifyRingToMaxVertices(ring, n))
  }
}
