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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.FormDef
import org.openforis.collectone.v2.core.geo.GeoCoord
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.sampling.PlotGeometry
import org.openforis.collectone.v2.core.sampling.PlotLayout
import org.openforis.collectone.v2.core.sampling.PlotShape
import org.openforis.collectone.v2.core.sampling.SampleDesign
import org.openforis.collectone.v2.core.sampling.SampleEncoding
import org.openforis.collectone.v2.core.sampling.SamplingArea
import org.openforis.collectone.v2.core.sampling.SamplingEngine
import org.openforis.collectone.v2.core.sampling.SamplingResult
import org.openforis.collectone.v2.core.sampling.SubPlotLayout

class KmlBalloonWriterSubPlotsTest {
  private val area =
    SamplingArea.fromSurveyArea(
      listOf(
        listOf(
          GeoCoord(-1.0020, 37.0000),
          GeoCoord(-1.0020, 37.0040),
          GeoCoord(-0.9980, 37.0040),
          GeoCoord(-0.9980, 37.0000),
        )
      )
    )

  @Test
  fun writeDocument_withoutSubPlots_emitsPlainPlotPolygonAndStandardFill() {
    val design =
      SampleDesign(
        layout = PlotLayout.SimpleRandom(count = 1),
        plotShape = PlotShape.SQUARE,
        plotSizeM = 100.0,
        subPlot = SubPlotLayout.None,
        seed = 42L,
      )
    val generated = (SamplingEngine.Default.generate(area, design) as SamplingResult.Success).plots
    val plot = generated.single()
    val kml =
      KmlBalloonWriter.writeDocument(
        surveyId = "survey-no-subplots",
        surveyTitle = "Baseline Forest Survey",
        documentName = "Baseline Forest Survey",
        layers =
          listOf(
            KmlBalloonWriter.LayerFolderSpec(
              layerId = "layer-plots",
              datasetId = "sample_plots",
              name = "Sample plots",
              colorHex = 0xFFF9A825L,
              sampleDesign = design,
              placemarks =
                listOf(
                  KmlBalloonWriter.PlacemarkSpec(
                    id = "ent-${plot.plotId}",
                    title = plot.plotId,
                    geoId = plot.plotId,
                    geometry =
                      GeometryProtos.polygon(
                        listOf(plot.boundary.map { GeometryProtos.position(it.lat, it.lng) })
                      ),
                    properties =
                      mapOf(
                        "plot_id" to plot.plotId,
                        // Even if a stale geotrace lingered, SubPlotLayout.None suppresses subplots
                        "samples" to SampleEncoding.encodeSamples(listOf(plot.center)),
                      ),
                  )
                ),
            )
          ),
      )

    assertFalse("<MultiGeometry>" in kml, kml)
    assertFalse("-subplot-" in kml, kml)
    assertFalse("c1_subplot_count" in kml, kml)
    assertTrue("<Polygon>" in kml, kml)
    assertTrue("<width>2.5</width>" in kml, kml)
    assertTrue("<color>4d25a8f9</color>" in kml, kml)
    assertTrue("<range>450</range>" in kml, kml)
  }

  @Test
  fun writeDocument_withGridSubPlots_emitsCollectEarthStyleMultiGeometryAndPreservesBalloon() {
    val design =
      SampleDesign(
        layout = PlotLayout.SimpleRandom(count = 1),
        plotShape = PlotShape.SQUARE,
        plotSizeM = 100.0,
        subPlot = SubPlotLayout.Grid(n = 3, spacingM = 20.0),
        seed = 42L,
      )
    val plot =
      (SamplingEngine.Default.generate(area, design) as SamplingResult.Success).plots.single()
    val placemark =
      KmlBalloonWriter.PlacemarkSpec(
        id = "ent-${plot.plotId}",
        title = plot.plotId,
        geoId = plot.plotId,
        geometry =
          GeometryProtos.polygon(
            listOf(plot.boundary.map { GeometryProtos.position(it.lat, it.lng) })
          ),
        properties =
          mapOf(
            "plot_id" to plot.plotId,
            "samples" to SampleEncoding.encodeSamples(plot.samples),
            "sample_ids" to
              SampleEncoding.encodeSampleIds(SampleEncoding.defaultSampleIds(plot.samples.size)),
          ),
      )
    val kml =
      KmlBalloonWriter.writeDocument(
        surveyId = "survey-grid-subplots",
        surveyTitle = "1ha Collect Earth Grid",
        documentName = "1ha Collect Earth Grid",
        layers = listOf(plotLayerSpec(design, placemark)),
        session =
          KmlBalloonWriter.SessionConfig(
            balloonAppUrl = "https://example.org/balloon/balloon-form.html",
            serviceUrl = "https://api.example.org",
          ),
      )

    // Clean hierarchy: one Folder for the layer and one interactive Placemark for the plot.
    assertEquals(1, Regex("<Folder\\b").findAll(kml).count(), kml)
    assertEquals(1, Regex("<Placemark\\b").findAll(kml).count(), kml)
    assertTrue("""<Placemark id="ent-${plot.plotId}">""" in kml, kml)
    assertTrue("<styleUrl>#collectone-layer-1</styleUrl>" in kml, kml)
    assertTrue("<gx:displayMode>panel</gx:displayMode>" in kml, kml)
    assertTrue("https://example.org/balloon/balloon-form.html" in kml, kml)
    assertTrue("$[c1_entity_id]" in kml, kml)
    assertTrue("<width>2.0</width>" in kml, kml)
    assertTrue("<color>1425a8f9</color>" in kml, kml)
    assertTrue("<range>400</range>" in kml, kml)
    assertTrue("""<Data name="c1_subplot_count"><value>9</value></Data>""" in kml, kml)

    assertEquals(1, Regex("<MultiGeometry>").findAll(kml).count(), kml)
    assertEquals(1, Regex("<Polygon>").findAll(kml).count(), kml)
    for (sampleIdx in 1..9) {
      val lineId = "ent-${plot.plotId}-subplot-$sampleIdx"
      assertTrue("""<LineString id="$lineId">""" in kml, "Missing $lineId in:\n$kml")
    }
    val subPlotCoords = extractLineStringCoords(kml, "ent-${plot.plotId}-subplot-1")
    assertEquals(5, subPlotCoords.size, "Expected closed 5-vertex square ring: $subPlotCoords")
    assertEquals(subPlotCoords.first(), subPlotCoords.last())
  }

  @Test
  fun writeDocument_synthesizesDeterministicSubPlotsFromDesignWhenSamplesPropertyOmitted() {
    val design =
      SampleDesign(
        layout = PlotLayout.SimpleRandom(count = 1),
        plotShape = PlotShape.CIRCLE,
        plotSizeM = 30.0,
        subPlot = SubPlotLayout.Random(count = 5),
        seed = 99L,
      )
    val center = GeoCoord(0.2500, 32.5000)
    val boundary = PlotGeometry.boundary(center, PlotShape.CIRCLE, 30.0)
    val kml =
      KmlBalloonWriter.writeDocument(
        surveyId = "survey-circle-random",
        surveyTitle = "Circular Random Subplots",
        documentName = "Circular Random Subplots",
        layers =
          listOf(
            KmlBalloonWriter.LayerFolderSpec(
              layerId = "layer-circles",
              datasetId = "circle_plots",
              name = "Circular plots",
              sampleDesign = design,
              placemarks =
                listOf(
                  KmlBalloonWriter.PlacemarkSpec(
                    id = "plot-c1",
                    title = "P000001",
                    geoId = "P000001",
                    geometry =
                      GeometryProtos.polygon(
                        listOf(boundary.map { GeometryProtos.position(it.lat, it.lng) })
                      ),
                    properties = mapOf("plot_id" to "P000001"),
                  )
                ),
            )
          ),
      )

    assertTrue("""<Data name="c1_subplot_count"><value>5</value></Data>""" in kml, kml)
    assertTrue("""<Data name="samples"><value>""" in kml, kml)
    assertTrue("""<Data name="sample_ids"><value>1 2 3 4 5</value></Data>""" in kml, kml)
    // 30m plot frames tightly at 200m LookAt range.
    assertTrue("<range>200</range>" in kml, kml)
    for (sampleIdx in 1..5) {
      assertTrue("""<LineString id="plot-c1-subplot-$sampleIdx">""" in kml, kml)
    }
    val boxCoords = extractLineStringCoords(kml, "plot-c1-subplot-1")
    assertEquals(5, boxCoords.size, "Expected closed 5-vertex square box: $boxCoords")
    assertEquals(boxCoords.first(), boxCoords.last())
  }

  @Test
  fun writeDocument_withoutForms_rendersReadOnlyPropertiesBalloonInsteadOfFormIframe() {
    val kml =
      KmlBalloonWriter.writeDocument(
        surveyId = "00000036-1a1c-5c51-997f-28b67d813aa3",
        surveyTitle = "Simple Point & Land Use Survey",
        documentName = "Simple Point & Land Use Survey",
        layers =
          listOf(
            KmlBalloonWriter.LayerFolderSpec(
              layerId = "layer-land-use-observations",
              datasetId = "land_use_observations",
              name = "Land Use Observations",
              forms = emptyList(),
              placemarks =
                listOf(
                  KmlBalloonWriter.PlacemarkSpec(
                    id = "ent-1",
                    title = "Primary Forest",
                    geoId = "ENT-SPLU-01",
                    geometry = GeometryProtos.point(latitude = -3.37935, longitude = 36.67765),
                    properties =
                      mapOf(
                        "land_use" to "primary_forest",
                        "observer_note" to "Closed-canopy montane forest stand.",
                        "status" to "Completed",
                      ),
                  )
                ),
            )
          ),
        session =
          KmlBalloonWriter.SessionConfig(
            balloonAppUrl = "https://example.org/balloon/balloon-form.html",
            serviceUrl = "https://api.example.org",
          ),
      )

    assertTrue("<bgColor>ffffffff</bgColor>" in kml, kml)
    assertTrue("<textColor>ff1f1f1f</textColor>" in kml, kml)
    assertTrue("<gx:displayMode>panel</gx:displayMode>" in kml, kml)
    assertTrue("background: #FFFFFF;" in kml, kml)
    assertFalse("balloon-form.html" in kml, kml)
    assertFalse("sharedForms" in kml, kml)
    assertFalse("<iframe" in kml, kml)
    assertFalse("\$[c1_status]" in kml, kml)
    assertFalse("\$[c1_marker_symbol]" in kml, kml)
    assertTrue("Land Use Observations • Simple Point &amp; Land Use Survey" in kml, kml)
    assertTrue("\$[c1_entity_title]" in kml, kml)
    assertTrue("\$[c1_geo_id]" in kml, kml)
    assertTrue("\$[c1_latitude]" in kml, kml)
    assertTrue("\$[c1_longitude]" in kml, kml)
    assertTrue("<th>Land use</th><td data-c1-prop-val=\"true\">\$[land_use]</td>" in kml, kml)
    assertTrue(
      "<th>Observer note</th><td data-c1-prop-val=\"true\">\$[observer_note]</td>" in kml,
      kml,
    )
  }

  @Test
  fun writeDocument_withoutFormsOrProperties_rendersEmptyReadOnlyState() {
    val kml =
      KmlBalloonWriter.writeDocument(
        surveyId = "s-empty",
        surveyTitle = "Empty Layer Survey",
        documentName = "Empty Layer Survey",
        layers =
          listOf(
            KmlBalloonWriter.LayerFolderSpec(
              layerId = "layer-1",
              datasetId = "points",
              name = "Points",
              forms = emptyList(),
              placemarks =
                listOf(
                  KmlBalloonWriter.PlacemarkSpec(
                    id = "p-1",
                    title = "Point 1",
                    geometry = GeometryProtos.point(latitude = 0.0, longitude = 0.0),
                  )
                ),
            )
          ),
      )

    assertFalse("balloon-form.html" in kml, kml)
    assertTrue("No recorded properties for this feature." in kml, kml)
    assertTrue("""id="c1-ro-table" style="display:none"""" in kml, kml)
  }

  @Test
  fun writeDocument_flattenSingleLayer_omitsRedundantFolderAndNamesDocumentAfterLayer() {
    val kml =
      KmlBalloonWriter.writeDocument(
        surveyId = "s-single",
        surveyTitle = "Peatland Interpretation",
        documentName = "Map Layers",
        layers =
          listOf(
            KmlBalloonWriter.LayerFolderSpec(
              layerId = "layer-plots",
              datasetId = "plots",
              name = "Interpretation Plots",
              description = "Permanent plots",
              isVisible = true,
              placemarks =
                listOf(
                  KmlBalloonWriter.PlacemarkSpec(
                    id = "p-1",
                    title = "Plot 1",
                    geometry = GeometryProtos.point(latitude = -2.3, longitude = 113.9),
                  )
                ),
            )
          ),
        flattenSingleLayer = true,
      )

    assertTrue("""<Document id="layer-plots">""" in kml, kml)
    assertTrue("    <name>Interpretation Plots</name>" in kml, kml)
    assertTrue("    <visibility>1</visibility>" in kml, kml)
    assertTrue("    <description>Permanent plots</description>" in kml, kml)
    assertFalse("<Folder" in kml, kml)
    assertTrue("""    <Placemark id="p-1">""" in kml, kml)
  }

  @Test
  fun writeDocument_flattenSingleLayerWithMultipleLayers_keepsFoldersAndUsesDocumentName() {
    val kml =
      KmlBalloonWriter.writeDocument(
        surveyId = "s-multi",
        surveyTitle = "Kenya Coffee",
        documentName = "Map Layers",
        layers =
          listOf(
            KmlBalloonWriter.LayerFolderSpec(
              layerId = "layer-parcels",
              datasetId = "parcels",
              name = "Coffee Parcels",
            ),
            KmlBalloonWriter.LayerFolderSpec(
              layerId = "layer-trees",
              datasetId = "trees",
              name = "Shade Trees",
            ),
          ),
        flattenSingleLayer = true,
      )

    assertTrue("  <Document>" in kml, kml)
    assertTrue("    <name>Map Layers</name>" in kml, kml)
    assertTrue("""<Folder id="layer-parcels">""" in kml, kml)
    assertTrue("""<Folder id="layer-trees">""" in kml, kml)
  }

  private fun plotLayerSpec(
    design: SampleDesign,
    placemark: KmlBalloonWriter.PlacemarkSpec,
  ): KmlBalloonWriter.LayerFolderSpec =
    KmlBalloonWriter.LayerFolderSpec(
      layerId = "layer-plots",
      datasetId = "sample_plots",
      name = "Sample plots",
      colorHex = 0xFFF9A825L,
      forms =
        listOf(
          KmlBalloonWriter.EmbeddedForm(
            id = "form-plot-interp",
            title = "Plot Interpretation",
            version = "1",
            formDef = FormDef(form_id = "form-plot-interp", title = "Plot Interpretation"),
          )
        ),
      sampleDesign = design,
      placemarks = listOf(placemark),
    )

  private fun extractLineStringCoords(kml: String, id: String): List<String> =
    Regex("""<LineString id="$id">[\s\S]*?<coordinates>([^<]+)</coordinates>""")
      .find(kml)
      ?.groupValues
      ?.get(1)
      ?.trim()
      ?.split(" ")
      .orEmpty()
}
