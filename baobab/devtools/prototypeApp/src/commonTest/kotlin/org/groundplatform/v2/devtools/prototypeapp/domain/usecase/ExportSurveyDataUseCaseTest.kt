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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.AuthRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.ImpactEventRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LibraryRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptLink
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEventType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorChoice
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityProperty
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.ConnectivityRepository

/** Export profiles (EUDR GeoJSON), plain GeoJSON, and export impact events. */
class ExportSurveyDataUseCaseTest {
  private val kfs = PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE
  private val store = seededStore()
  private val library =
    ResolveLibraryUseCase()
      .forOrganization(runNow { LibraryRepositoryImpl(store).observeLibraries().first() }, kfs)
  private val useCase = ExportSurveyDataUseCase()
  private val eudr = assertNotNull(library.exportProfiles.firstOrNull { it.id == "eudr_geojson" })

  private val plots =
    EntityDataset(
      key = "d1",
      kind = DatasetKind.MAP_LAYER,
      id = "plots",
      displayName = "Plots",
      geometryKind = GeometryKind.POLYGON,
      keyProperty = "id",
      labelProperty = "id",
      linkedFormKey = "f1",
      properties =
        listOf(
          EntityProperty("id", "ID"),
          EntityProperty("commodity", "Commodity"),
          EntityProperty("owner", "Owner", conceptLink = ConceptLink("core.producer_name")),
        ),
    )

  private val form =
    SurveyEditorForm(
      "f1",
      EditorForm(
        formId = "plots",
        title = "Plots",
        questions =
          listOf(
            EditorQuestion(
              "q1",
              "commodity",
              EditorQuestionType.SELECT_ONE,
              "Main crop",
              choices = listOf(EditorChoice("cafe", "Café", code = "coffee")),
              conceptLink = ConceptLink("eudr.commodity"),
            )
          ),
      ),
    )

  private fun entity(
    id: String,
    geoId: String = "",
    type: String = "Polygon",
    area: Double = 1.5,
    properties: Map<String, String> = mapOf("commodity" to "cafe", "Owner" to "Josephat Kamau"),
  ) =
    GeospatialEntityItem(
      id = id,
      label = id,
      datasetId = "plots",
      datasetName = "Plots",
      layerId = "plots",
      geoId = geoId,
      geometryTypeLabel = type,
      areaHectares = area,
      perimeterMeters = 0,
      coordinatesLabel = "",
      normalizedX = 0.5f,
      normalizedY = 0.5f,
      colorHex = 0xFF000000,
      properties = properties,
      submissions = emptyList(),
    )

  @Test
  fun enabledProfiles_comeFromTheSurveysPurposePacks() {
    assertEquals(
      listOf("eudr_geojson"),
      useCase.enabledProfiles(library, listOf("eudr_due_diligence")).map { it.id },
    )
    assertEquals(emptyList(), useCase.enabledProfiles(library, emptyList()))
    assertEquals(emptyList(), useCase.enabledProfiles(library, listOf("producer_registration")))
  }

  @Test
  fun plan_mapsFieldsThroughConceptLinks_andWarnsAboutUnlinkedOnes() {
    val plan = useCase.plan(eudr, plots, listOf(form), library, listOf(entity("p1")))
    val byField = plan.fields.associateBy { it.outputField }
    assertTrue(byField.getValue("production_place").isGeometry)
    // Through the linked question's save_to property, with its choice codes.
    assertEquals("commodity", byField.getValue("commodity").propertyName)
    assertEquals(mapOf("cafe" to "coffee"), byField.getValue("commodity").choiceCodes)
    // Through the property that inherited the concept.
    assertEquals("owner", byField.getValue("producer_name").propertyName)
    assertEquals(setOf("area_ha", "country"), plan.unmappedFields.map { it.outputField }.toSet())
    assertEquals(2, plan.warnings.size)
    assertTrue(plan.warnings[0].startsWith("Not linked to a standard field in this survey"))
    assertTrue("Country" in plan.warnings[0])
    assertTrue("mapped area" in plan.warnings[1])
  }

  @Test
  fun plan_warnsAboutEmptyDatasetsAndLargePointPlots() {
    val empty = useCase.plan(eudr, plots, listOf(form), library, emptyList())
    assertTrue(empty.warnings.any { "no map features" in it })
    val points =
      useCase.plan(
        eudr,
        plots,
        listOf(form),
        library,
        listOf(entity("p1", type = "Point", area = 6.0), entity("p2", type = "Point", area = 2.0)),
      )
    assertTrue(points.warnings.any { it.startsWith("1 plot is over 4 ha but mapped as a point") })
  }

  @Test
  fun profileGeoJson_writesClosedPolygonsWith6Decimals_codesAndFallbackArea() {
    val plan = useCase.plan(eudr, plots, listOf(form), library, listOf(entity("p1")))
    val file =
      useCase.profileGeoJson(plan, plots, listOf(entity("p1")), SurveyMapAnchor.forSurvey("s"))
    assertEquals("Plots EUDR GeoJSON.geojson", file.fileName)
    assertEquals("application/geo+json", file.mimeType)
    val json = file.content
    assertTrue(json.startsWith("{\"type\":\"FeatureCollection\""), json)
    assertTrue("\"type\":\"Polygon\"" in json)
    assertTrue("\"commodity\":\"coffee\"" in json, json)
    assertTrue("\"producer_name\":\"Josephat Kamau\"" in json, json)
    assertTrue("\"area_ha\":\"1.5000\"" in json, json)
    assertTrue("\"country\":\"\"" in json)
    assertTrue("production_place" !in json)
    val coordinates =
      Regex("""-?\d+\.(\d+)""")
        .findAll(json.substringAfter("\"coordinates\":").substringBefore("}"))
        .toList()
    assertTrue(coordinates.isNotEmpty() && coordinates.all { it.groupValues[1].length == 6 }, json)
    val ring = Regex("""\[\[\[(.*)]]]""").find(json)!!.groupValues[1].split("],[")
    assertEquals(ring.first().trim('['), ring.last().trim(']'))
  }

  @Test
  fun geoJson_includesEveryPropertyButPresentationKeys() {
    val file =
      useCase.geoJson(
        plots,
        listOf(entity("p1", properties = mapOf("Owner" to "A \"B\"", "marker-color" to "#fff"))),
        SurveyMapAnchor.forSurvey("s"),
      )
    assertEquals("Plots.geojson", file.fileName)
    assertTrue("\"Owner\":\"A \\\"B\\\"\"" in file.content, file.content)
    assertTrue("marker-color" !in file.content)
  }

  @Test
  fun formatting_isLocaleIndependent() {
    assertEquals("36.950000", ExportSurveyDataUseCase.formatDecimal(36.95, 6))
    assertEquals("-0.415012", ExportSurveyDataUseCase.formatDecimal(-0.4150123, 6))
    assertEquals("0.000000", ExportSurveyDataUseCase.formatDecimal(-0.0000001, 6))
    assertEquals("\"a\\nb\\u0001\"", ExportSurveyDataUseCase.jsonString("a\nb\u0001"))
    assertEquals("a_b.csv", ExportSurveyDataUseCase.fileName("a/b", "csv"))
  }

  @Test
  fun recordExport_logsAnExportEventWithDedupedCoverage() {
    val events = ImpactEventRepositoryImpl(store)
    val surveys = SurveyRepositoryImpl(store)
    val recorder =
      RecordImpactEventUseCase(
        impactEventRepository = events,
        surveyRepository = surveys,
        surveyEditorRepository = SurveyEditorRepositoryImpl(store),
        authRepository = AuthRepositoryImpl(),
        connectivityRepository = ConnectivityRepository(),
        now = { "2026-10-10T00:00:00Z" },
        newId = { "event-1" },
      )
    val exporting = ExportSurveyDataUseCase(recorder)
    val shared =
      listOf(
        entity("p1", geoId = "G1", area = 2.0),
        entity("p2", geoId = "G1", area = 2.0),
        entity("p3", area = 1.0),
      )
    runNow { exporting.recordExport("survey-kenya-coffee", shared, "eudr_geojson") }
    val event = runNow { events.getEvents() }.single()
    assertEquals(ImpactEventType.EXPORT, event.type)
    assertEquals("eudr_geojson", event.exportProfileId)
    assertEquals(2, event.featureCount)
    assertEquals(3.0, event.areaHa)
    assertEquals(kfs, event.organizationId)
    assertEquals(listOf("eudr_due_diligence"), event.purposeIds)
    assertEquals("2026-10-10T00:00:00Z", event.occurredAt)
    assertTrue(event.actorUserId.isNotBlank())
  }
}
