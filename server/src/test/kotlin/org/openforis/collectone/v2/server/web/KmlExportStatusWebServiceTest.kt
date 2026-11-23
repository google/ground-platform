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
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.ids.RecordIds
import org.openforis.collectone.v2.server.app.UserDataServer
import org.openforis.collectone.v2.server.service.UserDataBackend
import org.openforis.collectone.v2.server.service.sampleBackend

class KmlExportStatusWebServiceTest {
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
  fun unsetStatusPlacemarkShowsNoPrefixDuringDraftingAndShowsCheckmarkAfterSubmit() = runTest {
    val surveyId = RecordIds.newId()
    createEarthPlotSurvey(surveyId)
    createUnsetStatusPlot(surveyId)

    // Before submission, an entity with no status/marker-symbol shows no prefix (matches main UI).
    val initialKml = fetchKml(surveyId)
    assertTrue("<name>P000001</name>" in initialKml, initialKml)
    assertFalse("<name>○ P000001</name>" in initialKml, initialKml)
    assertTrue("<styleUrl>#collectone-layer-1</styleUrl>" in initialKml, initialKml)

    // Saving an unsubmitted balloon draft does not turn the placemark into a half-filled circle.
    val draftResp = saveDraft(surveyId, "plot-entity-1")
    assertEquals(200, draftResp.statusCode(), draftResp.body())
    val whileDraftingKml = fetchKml(surveyId)
    assertTrue("<name>P000001</name>" in whileDraftingKml, whileDraftingKml)
    assertFalse("<name>◐ P000001</name>" in whileDraftingKml, whileDraftingKml)
    assertTrue("<styleUrl>#collectone-layer-1</styleUrl>" in whileDraftingKml, whileDraftingKml)

    // Once submitted (patching status + marker-symbol), KML shows the completed checkmark & style.
    patchEntityStatus(surveyId, "plot-entity-1", "Completed", "✓", "#1E8E3E")
    val afterSubmitKml = fetchKml(surveyId)
    assertTrue("<name>✓ P000001</name>" in afterSubmitKml, afterSubmitKml)
    assertTrue(
      "<styleUrl>#collectone-layer-1-completed</styleUrl>" in afterSubmitKml,
      afterSubmitKml,
    )
  }

  private fun fetchKml(surveyId: String): String =
    client
      .send(
        HttpRequest.newBuilder(URI("$baseUrl/kml/surveys/$surveyId.kml")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
      )
      .body()

  private fun saveDraft(surveyId: String, entityId: String): HttpResponse<String> =
    client.send(
      HttpRequest.newBuilder(URI("$baseUrl/balloon/state/$surveyId/$entityId"))
        .header("Content-Type", "application/json")
        .POST(
          HttpRequest.BodyPublishers.ofString(
            """{"formId":"form-interp","recordBase64":"dGVzdC1kcmFmdA"}"""
          )
        )
        .build(),
      HttpResponse.BodyHandlers.ofString(),
    )

  private suspend fun createEarthPlotSurvey(surveyId: String) {
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = surveyId,
        mutations =
          listOf(
            SurveyMutation(
              mutation_id = "mut-survey-1",
              client_id = "test-client",
              client_seq = 1L,
              upsert =
                SurveyDef(
                  survey_id = surveyId,
                  title = "Plot Interpretation Survey",
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
                        geometry_type = DataType.TYPE_GEOPOINT,
                      )
                    ),
                ),
            )
          ),
      )
    )
  }

  private suspend fun createUnsetStatusPlot(surveyId: String) {
    backend.MutateData(
      MutateDataRequest(
        survey_id = surveyId,
        mutations =
          listOf(
            DataMutation(
              mutation_id = "mut-plot-1",
              client_id = "test-client",
              client_seq = 2L,
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
                      geometry = GeometryProtos.point(-0.4200, 36.9500),
                      properties = mapOf("plot_id" to TypedValue(string_value = "P000001")),
                    ),
                ),
            )
          ),
      )
    )
  }

  private suspend fun patchEntityStatus(
    surveyId: String,
    entityId: String,
    status: String,
    markerSymbol: String,
    markerColor: String,
  ) {
    backend.MutateData(
      MutateDataRequest(
        survey_id = surveyId,
        mutations =
          listOf(
            DataMutation(
              mutation_id = "mut-plot-status-$status",
              client_id = "test-client",
              client_seq = 3L,
              entity_mutation =
                EntityMutation(
                  dataset_id = "sample_plots",
                  entity_id = entityId,
                  base_revision = 0L,
                  upsert =
                    EntityRecord(
                      properties =
                        mapOf(
                          "status" to TypedValue(string_value = status),
                          "marker-symbol" to TypedValue(string_value = markerSymbol),
                          "marker-color" to TypedValue(string_value = markerColor),
                        )
                    ),
                ),
            )
          ),
      )
    )
  }
}
