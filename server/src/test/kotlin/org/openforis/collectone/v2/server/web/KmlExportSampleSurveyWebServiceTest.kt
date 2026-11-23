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
package org.openforis.collectone.v2.server.web

import com.linecorp.armeria.server.Server
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.survey.EntityDatasetDef
import openforis.collectone.v1.survey.EntityType
import openforis.collectone.v1.survey.FormLaunchConfig
import openforis.collectone.v1.survey.LayerDef
import openforis.collectone.v1.survey.MapConfig
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.SamplingDesign
import openforis.collectone.v1.survey.SubPlotDesign
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import org.openforis.collectone.v2.core.geo.GeoCoord
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.ids.RecordIds
import org.openforis.collectone.v2.core.sampling.PlotGeometry
import org.openforis.collectone.v2.core.sampling.PlotShape
import org.openforis.collectone.v2.core.sampling.SampleEncoding
import org.openforis.collectone.v2.core.sampling.SubPlotLayout
import org.openforis.collectone.v2.server.app.UserDataServer
import org.openforis.collectone.v2.server.service.UserDataBackend
import org.openforis.collectone.v2.server.service.sampleBackend

class KmlExportSampleSurveyWebServiceTest {
  private lateinit var backend: UserDataBackend
  private lateinit var server: Server
  private val client = HttpClient.newHttpClient()
  private val baseUrl
    get() = "http://127.0.0.1:${server.activeLocalPort()}"

  @BeforeTest
  fun start() {
    backend = sampleBackend()
    server = UserDataServer.create(backend, port = 0)
    server.start().join()
  }

  @AfterTest
  fun stop() {
    server.stop().join()
  }

  @Test
  fun getSurveyKmlIncludesSamplingDesignSubPlotsAndOmitsThemWhenSubPlotModeIsNone() = runTest {
    val surveyId = RecordIds.newId()
    upsertSubPlotSurvey(
      surveyId = surveyId,
      seq = 1L,
      subPlot = SubPlotDesign(mode = SubPlotDesign.Mode.GRID, grid_n = 2, spacing_m = 10.0),
    )
    insertSubPlotEntity(surveyId = surveyId, seq = 2L)

    val withSubPlots =
      get("/kml/surveys/$surveyId.kml?balloonUrl=https://example.org/balloon/balloon-form.html")
    assertEquals(200, withSubPlots.statusCode(), withSubPlots.body())
    val kmlWithSubPlots = withSubPlots.body()
    assertTrue("<Folder id=\"layer-sample-plots\">" in kmlWithSubPlots, kmlWithSubPlots)
    assertTrue("<Placemark id=\"plot-entity-1\">" in kmlWithSubPlots, kmlWithSubPlots)
    assertTrue("<MultiGeometry>" in kmlWithSubPlots, kmlWithSubPlots)
    assertTrue("<Polygon>" in kmlWithSubPlots, kmlWithSubPlots)
    for (idx in 1..4) {
      assertTrue(
        """<LineString id="plot-entity-1-subplot-$idx">""" in kmlWithSubPlots,
        kmlWithSubPlots,
      )
    }
    assertTrue(
      """<Data name="c1_subplot_count"><value>4</value></Data>""" in kmlWithSubPlots,
      kmlWithSubPlots,
    )
    assertTrue("<gx:displayMode>panel</gx:displayMode>" in kmlWithSubPlots, kmlWithSubPlots)
    assertTrue("https://example.org/balloon/balloon-form.html" in kmlWithSubPlots, kmlWithSubPlots)

    // Switch sampling design subplot mode to NONE -> exported KML omits subplot geometries.
    upsertSubPlotSurvey(
      surveyId = surveyId,
      seq = 3L,
      subPlot = SubPlotDesign(mode = SubPlotDesign.Mode.NONE),
    )
    val withoutSubPlots = get("/kml/surveys/$surveyId.kml")
    assertEquals(200, withoutSubPlots.statusCode(), withoutSubPlots.body())
    val kmlWithoutSubPlots = withoutSubPlots.body()
    assertTrue("<Placemark id=\"plot-entity-1\">" in kmlWithoutSubPlots, kmlWithoutSubPlots)
    assertTrue("<Polygon>" in kmlWithoutSubPlots, kmlWithoutSubPlots)
    assertFalse("<MultiGeometry>" in kmlWithoutSubPlots, kmlWithoutSubPlots)
    assertFalse("plot-entity-1-subplot-" in kmlWithoutSubPlots, kmlWithoutSubPlots)
    assertFalse("c1_subplot_count" in kmlWithoutSubPlots, kmlWithoutSubPlots)
  }

  @Test
  fun googleEarthSampleSurveyServesNetworkLinkPolygonPlotsAndLiveStatusPrefixes() {
    val earthSurveyId = "00000037-7e4a-5b2c-9d18-3f8a61c4e902"
    val balloonParam = "balloonUrl=https://example.org/balloon/balloon-form.html"
    val earthOrigin = mapOf("Origin" to "https://earth.google.com")

    val nlResponse = get("/kml/surveys/$earthSurveyId.kml?mode=networklink&$balloonParam")
    assertEquals(200, nlResponse.statusCode())
    assertTrue(
      "<NetworkLink id=\"collectone-networklink-$earthSurveyId\">" in nlResponse.body(),
      nlResponse.body(),
    )
    assertTrue("<name>Live Layers</name>" in nlResponse.body(), nlResponse.body())
    assertTrue("&amp;source=networklink" in nlResponse.body(), nlResponse.body())

    val nlTargetResponse =
      get("/kml/surveys/$earthSurveyId.kml?$balloonParam&source=networklink", earthOrigin)
    assertEquals(200, nlTargetResponse.statusCode())
    val nlTargetBody = nlTargetResponse.body()
    assertTrue("<Document id=\"layer-interpretation-plots\">" in nlTargetBody, nlTargetBody)
    assertFalse("<Folder" in nlTargetBody, nlTargetBody)
    assertTrue("<name>✓ PLT-GE-01 • Sebangau North Dome</name>" in nlTargetBody, nlTargetBody)

    val response = get("/kml/surveys/$earthSurveyId.kml?$balloonParam", earthOrigin)
    assertEquals(200, response.statusCode())
    val body = response.body()
    assertTrue("<Folder id=\"layer-interpretation-plots\">" in body, body)
    assertTrue("<Polygon>" in body, body)
    assertTrue("<name>✓ PLT-GE-01 • Sebangau North Dome</name>" in body, body)
    assertTrue("<name>✓ PLT-GE-02 • Kahayan Canal Block</name>" in body, body)
    assertTrue("<name>◐ PLT-GE-03 • Central Peat Core</name>" in body, body)
    assertTrue("<name>○ PLT-GE-04 • West Blackwater Buffer</name>" in body, body)
    assertTrue("<name>○ PLT-GE-05 • South Drainage Margin</name>" in body, body)
    val stylePayloads =
      Regex("""var sharedForms = "([^"]+)";""")
        .findAll(body)
        .map { match -> String(Base64.getUrlDecoder().decode(match.groupValues[1])) }
        .toList()
    assertTrue(
      stylePayloads.any { "\"id\":\"form-google-earth-interpretation\"" in it },
      "Expected embedded form-google-earth-interpretation payload in KML",
    )

    assertReadOnlySampleSurveyKmls(balloonParam)
  }

  private fun assertReadOnlySampleSurveyKmls(balloonParam: String) {
    // Sample surveys with GPS/photo questions do not embed incompatible forms in Google Earth KML
    // and instead render a read-only properties balloon rather than the interactive form iframe.
    val photoSurveyKml = get("/kml/surveys/00000034-c088-5ab9-8b98-e6bccb3dca71.kml").body()
    assertFalse("balloon-form.html" in photoSurveyKml, photoSurveyKml)
    assertFalse("sharedForms" in photoSurveyKml, photoSurveyKml)
    assertTrue("id=\"c1-ro-table\"" in photoSurveyKml, photoSurveyKml)

    // Simple Point & Land Use Survey has a CREATE + geopoint form (not Google Earth-enabled), so
    // its KML serves read-only feature properties without triggering "No Google Earth forms".
    val simplePointKml =
      get("/kml/surveys/00000036-1a1c-5c51-997f-28b67d813aa3.kml?$balloonParam").body()
    assertFalse("balloon-form.html" in simplePointKml, simplePointKml)
    assertFalse("sharedForms" in simplePointKml, simplePointKml)
    assertTrue("<th>Land use</th><td data-c1-prop-val=\"true\">\$[land_use]</td>" in simplePointKml)
    assertTrue(
      "<th>Observer note</th><td data-c1-prop-val=\"true\">\$[observer_note]</td>" in simplePointKml
    )
  }

  private fun get(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
    val builder = HttpRequest.newBuilder(URI("$baseUrl$path")).GET()
    headers.forEach { (name, value) -> builder.header(name, value) }
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
  }

  private suspend fun upsertSubPlotSurvey(
    surveyId: String,
    seq: Long,
    subPlot: SubPlotDesign,
  ) {
    val sampling =
      SamplingDesign(
        method = SamplingDesign.Method.SYSTEMATIC_GRID,
        survey_area = SamplingDesign.SurveyAreaRef(),
        spacing_m = 250.0,
        plot_shape = SamplingDesign.PlotShape.SQUARE,
        plot_size_m = 30.0,
        seed = 7L,
        engine_version = 1,
        sub_plot = subPlot,
      )
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = surveyId,
        mutations =
          listOf(
            SurveyMutation(
              mutation_id = "mut-survey-$seq",
              client_id = "test-client",
              client_seq = seq,
              upsert =
                SurveyDef(
                  survey_id = surveyId,
                  title = "Subplot Interpretation Survey",
                  forms = listOf(FormDef(form_id = "form-interp", title = "Plot Interpretation")),
                  form_launch_configs =
                    listOf(
                      FormLaunchConfig(
                        form_id = "form-interp",
                        title = "Plot Interpretation",
                        target_entity_dataset_id = "sample_plots",
                        requires_entity = true,
                        open_in_google_earth = true,
                      )
                    ),
                  map_config =
                    MapConfig(
                      layers =
                        listOf(
                          LayerDef(
                            id = "layer-sample-plots",
                            label = "Sample plots",
                            entity_dataset_id = "sample_plots",
                            visible_by_default = true,
                          )
                        )
                    ),
                  entity_datasets =
                    listOf(
                      EntityDatasetDef(
                        id = "sample_plots",
                        display_name = "Sample plots",
                        type = EntityType.GEOSPATIAL,
                        key_property = "plot_id",
                        label_property = "plot_id",
                        geometry_type = DataType.TYPE_GEOSHAPE,
                        sampling = sampling,
                      )
                    ),
                ),
            )
          ),
      )
    )
  }

  private suspend fun insertSubPlotEntity(surveyId: String, seq: Long) {
    val center = GeoCoord(-0.4200, 36.9500)
    val boundary = PlotGeometry.boundary(center, PlotShape.SQUARE, 30.0)
    val samples =
      PlotGeometry.subPlotSamples(
        center = center,
        boundary = boundary,
        shape = PlotShape.SQUARE,
        sizeM = 30.0,
        layout = SubPlotLayout.Grid(n = 2, spacingM = 10.0),
        plotSeed = 1L,
      )
    backend.MutateData(
      MutateDataRequest(
        survey_id = surveyId,
        mutations =
          listOf(
            DataMutation(
              mutation_id = "mut-plot-1",
              client_id = "test-client",
              client_seq = seq,
              entity_mutation =
                EntityMutation(
                  dataset_id = "sample_plots",
                  entity_id = "plot-entity-1",
                  base_revision = 0L,
                  upsert =
                    EntityRecord(
                      entity_id = "plot-entity-1",
                      survey_id = surveyId,
                      dataset_id = "sample_plots",
                      layer_id = "layer-sample-plots",
                      geo_id = "P000001",
                      label = "P000001",
                      geometry =
                        GeometryProtos.polygon(
                          listOf(boundary.map { GeometryProtos.position(it.lat, it.lng) })
                        ),
                      properties =
                        mapOf(
                          "plot_id" to TypedValue(string_value = "P000001"),
                          "samples" to
                            TypedValue(string_value = SampleEncoding.encodeSamples(samples)),
                          "sample_ids" to TypedValue(string_value = "1 2 3 4"),
                        ),
                    ),
                ),
            )
          ),
      )
    )
  }
}
