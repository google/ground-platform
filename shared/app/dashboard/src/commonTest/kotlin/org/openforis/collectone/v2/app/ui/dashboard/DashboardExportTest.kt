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
@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package org.openforis.collectone.v2.app.ui.dashboard

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.AuthRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.ImpactEventRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.LibraryRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.LocalStoreTransactionRunner
import org.openforis.collectone.v2.app.data.repository.MutationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.domain.model.ImpactEventType
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.usecase.CreateSurveyUseCase
import org.openforis.collectone.v2.app.domain.usecase.ExportSurveyDataUseCase
import org.openforis.collectone.v2.app.domain.usecase.RecordImpactEventUseCase
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.app.domain.usecase.SyncMutationsUseCase
import org.openforis.collectone.v2.app.testing.FakeClock

/** Exports from the web dashboard's data tables, and their export events. */
class DashboardExportTest {
  private val store = seededStore()
  private val surveys = SurveyRepositoryImpl(store)
  private val editor = SurveyEditorRepositoryImpl(store)
  private val mutations = MutationRepositoryImpl(store)
  private val runner = LocalStoreTransactionRunner(store)
  private val events = ImpactEventRepositoryImpl(store)
  private var nextId = 0
  private val recorder =
    RecordImpactEventUseCase(
      impactEventRepository = events,
      surveyRepository = surveys,
      surveyEditorRepository = editor,
      authRepository = AuthRepositoryImpl(),
      connectivityRepository = ConnectivityRepository(),
      clock = FakeClock(Instant.parse("2026-10-10T00:00:00Z")),
      newId = { "event-${++nextId}" },
    )
  private val viewModel =
    DashboardViewModel(
      surveyRepository = surveys,
      organizationRepository = OrganizationRepositoryImpl(store),
      authRepository = AuthRepositoryImpl(),
      mutationRepository = mutations,
      createSurveyUseCase = CreateSurveyUseCase(surveys, editor, runner),
      syncMutationsUseCase =
        SyncMutationsUseCase(mutations, surveys, runner, ConnectivityRepository()),
      libraryRepository = LibraryRepositoryImpl(store),
      resolveLibraryUseCase = ResolveLibraryUseCase(),
      surveyEditorRepository = editor,
      exportSurveyData = ExportSurveyDataUseCase(recorder),
    )

  private val ui
    get() = viewModel.uiState.value

  private fun recorded() = runNow { events.getEvents() }

  @Test
  fun eudrPurpose_offersTheEudrProfile_forMapLayersOnly() {
    val parcels = assertNotNull(ui.exportOptions["coffee_parcels"])
    assertTrue(parcels.hasGeometry)
    val plan = parcels.profilePlans.single()
    assertEquals("eudr_geojson", plan.profile.id)
    // The seeded coffee parcels link the owner and commodity to standard fields, not the country.
    assertEquals(
      "farmer_owner",
      plan.fields.first { it.outputField == "producer_name" }.propertyName,
    )
    assertTrue(plan.fields.first { it.conceptId == "eudr.commodity" }.isMapped)
    assertTrue(
      plan.warnings.any { "Country" in it && "Commodity" !in it },
      plan.warnings.toString(),
    )
    val farmers = assertNotNull(ui.exportOptions["farmers"])
    assertFalse(farmers.hasGeometry)
    assertTrue(farmers.profilePlans.isEmpty())
  }

  @Test
  fun exportWithProfile_returnsGeoJson_andRecordsAnExportWithTheProfile() {
    val file = assertNotNull(viewModel.exportWithProfile("coffee_parcels", "eudr_geojson"))
    assertTrue("\"producer_name\":\"Josephat Kamau\"" in file.content, file.content)
    val event = recorded().single()
    assertEquals(ImpactEventType.EXPORT, event.type)
    assertEquals("eudr_geojson", event.exportProfileId)
    assertEquals(3, event.featureCount)
    assertEquals(1.84 + 2.45 + 1.56, event.areaHa, 1e-9)
    assertNull(viewModel.exportWithProfile("coffee_parcels", "missing_profile"))
  }

  @Test
  fun plainExports_recordExportEventsWithoutAProfile() {
    viewModel.recordCsvExport("coffee_parcels")
    assertNotNull(viewModel.exportGeoJson("washing_stations"))
    assertNull(viewModel.exportGeoJson("farmers"))
    val logged = recorded()
    assertEquals(listOf(3, 1), logged.map { it.featureCount })
    assertTrue(logged.all { it.type == ImpactEventType.EXPORT && it.exportProfileId == null })
  }

  @Test
  fun exportKml_buildsBalloonFormKmlForMapLayers_andRecordsExportEvent() {
    val kml =
      assertNotNull(
        viewModel.exportKml(
          datasetId = "coffee_parcels",
          balloonAppUrl = "https://example.org/balloon/balloon-form.html",
        )
      )
    assertTrue(kml.fileName.endsWith(".kml"), kml.fileName)
    assertTrue("<gx:displayMode>panel</gx:displayMode>" in kml.content)
    assertTrue("https://example.org/balloon/balloon-form.html" in kml.content)
    assertTrue("<Folder id=" in kml.content)
    assertTrue("<Placemark" in kml.content)
    assertNull(viewModel.exportKml("farmers"))
    val event = recorded().single()
    assertEquals(ImpactEventType.EXPORT, event.type)
    assertEquals(3, event.featureCount)
  }

  @Test
  fun exportSurveyKmlAndGoogleEarthDeepLinkIncludeAllMapLayerFolders() {
    val surveyKml =
      assertNotNull(
        viewModel.exportSurveyKml(
          balloonAppUrl = "https://example.org/balloon/balloon-form.html",
          serviceUrl = "https://api.example.org",
        )
      )
    assertTrue("<Folder id=\"layer-coffee-parcels\">" in surveyKml.content, surveyKml.content)
    assertTrue("<Folder id=\"layer-water-points\">" in surveyKml.content, surveyKml.content)
    assertFalse("token:" in surveyKml.content, surveyKml.content)
    assertFalse("devRole:" in surveyKml.content, surveyKml.content)
    assertTrue("collectone:open-external-url" in surveyKml.content, surveyKml.content)

    val earthUrl =
      assertNotNull(
        viewModel.googleEarthOpenUrl(
          balloonAppUrl = "https://example.org/balloon/balloon-form.html",
          serviceUrl = "https://api.example.org",
        )
      )
    assertTrue(earthUrl.startsWith("https://earth.google.com/web/c/"), earthUrl)
    assertNull(viewModel.googleEarthOpenUrl(serviceUrl = ""))
  }

  @Test
  fun exportKml_includesOnlyFormsWithOpenInGoogleEarthEnabled() {
    val activeId = ui.activeSurveyId
    val content = runNow { surveys.observeSurveyContent(activeId).first() }
    val draft = assertNotNull(runNow { editor.observeDraft(activeId).first() })
    val exports = DashboardExports(ExportSurveyDataUseCase(recorder), ResolveLibraryUseCase())
    val baseData =
      DashboardData(
        surveys = ui.surveys,
        activeSurveyId = activeId,
        content = content,
        draft = draft,
      )

    val bothEnabledKml =
      assertNotNull(
        exports.kml(
          data = baseData,
          datasetId = "coffee_parcels",
          balloonAppUrl = "https://example.org/balloon/balloon-form.html",
        )
      )
    val bothFormsJson = decodeSharedFormsJson(bothEnabledKml.content)
    assertTrue("\"id\":\"form-eudr-baseline\"" in bothFormsJson, bothFormsJson)
    assertTrue("\"id\":\"form-household-interview\"" in bothFormsJson, bothFormsJson)
    assertFalse("\"id\":\"form-pest-disease-sighting\"" in bothFormsJson, bothFormsJson)

    val oneDisabledData =
      baseData.copy(
        content =
          content.copy(
            forms =
              content.forms.map { form ->
                if (form.id == "form-household-interview") {
                  form.copy(openInGoogleEarth = false)
                } else {
                  form
                }
              }
          )
      )
    val oneEnabledKml =
      assertNotNull(
        exports.kml(
          data = oneDisabledData,
          datasetId = "coffee_parcels",
          balloonAppUrl = "https://example.org/balloon/balloon-form.html",
        )
      )
    val oneFormJson = decodeSharedFormsJson(oneEnabledKml.content)
    assertTrue("\"id\":\"form-eudr-baseline\"" in oneFormJson, oneFormJson)
    assertFalse("\"id\":\"form-household-interview\"" in oneFormJson, oneFormJson)
  }

  @Test
  fun googleEarthOpenActionIsGatedOnEarthEnabledForms_andKmlFallsBackToReadOnlyProperties() {
    // Kenya Coffee has Google Earth-enabled forms on its map layers.
    assertTrue(ui.hasGoogleEarthForms)
    assertTrue(assertNotNull(ui.exportOptions["coffee_parcels"]).hasGoogleEarthForms)
    assertNotNull(
      viewModel.googleEarthOpenUrl(
        balloonAppUrl = "https://example.org/balloon/balloon-form.html",
        serviceUrl = "https://api.example.org",
      )
    )

    // Simple Point & Land Use Survey has no Google Earth-enabled forms (CREATE + geopoint).
    viewModel.openSurveyOnWeb("00000036-1a1c-5c51-997f-28b67d813aa3")
    assertFalse(ui.hasGoogleEarthForms)
    val landUseOptions = assertNotNull(ui.exportOptions["land_use_observations"])
    assertTrue(landUseOptions.hasGeometry)
    assertFalse(landUseOptions.hasGoogleEarthForms)
    assertNull(
      viewModel.googleEarthOpenUrl(
        balloonAppUrl = "https://example.org/balloon/balloon-form.html",
        serviceUrl = "https://api.example.org",
      )
    )

    // Manual KML download still works and renders a read-only properties balloon without iframe.
    val kml =
      assertNotNull(
        viewModel.exportKml(
          datasetId = "land_use_observations",
          balloonAppUrl = "https://example.org/balloon/balloon-form.html",
          serviceUrl = "https://api.example.org",
        )
      )
    assertFalse("balloon-form.html" in kml.content, kml.content)
    assertFalse("sharedForms" in kml.content, kml.content)
    assertTrue("\$[land_use]" in kml.content, kml.content)
  }

  private fun decodeSharedFormsJson(kmlContent: String): String {
    val encoded =
      Regex("""var sharedForms = "([^"]*)";""").find(kmlContent)?.groupValues?.get(1).orEmpty()
    if (encoded.isEmpty()) return ""
    return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
      .decode(encoded)
      .decodeToString()
  }
}
