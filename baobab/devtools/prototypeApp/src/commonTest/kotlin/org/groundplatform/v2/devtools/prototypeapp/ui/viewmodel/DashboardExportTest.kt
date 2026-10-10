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
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.AuthRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.ImpactEventRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LibraryRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LocalStoreTransactionRunner
import org.groundplatform.v2.devtools.prototypeapp.data.repository.MutationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEventType
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.ConnectivityRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CreateSurveyUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ExportSurveyDataUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.RecordImpactEventUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SyncMutationsUseCase

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
      now = { "2026-10-10T00:00:00Z" },
      newId = { "event-${++nextId}" },
    )
  private val viewModel =
    DashboardViewModel(
      surveyRepository = surveys,
      organizationRepository = OrganizationRepositoryImpl(store),
      authRepository = AuthRepositoryImpl(),
      mutationRepository = mutations,
      createSurveyUseCase = CreateSurveyUseCase(surveys, editor, runner),
      syncMutationsUseCase = SyncMutationsUseCase(mutations, surveys, runner),
      scope = CoroutineScope(Dispatchers.Unconfined + Job()),
      libraryRepository = LibraryRepositoryImpl(store),
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
    // The seeded coffee parcels link the owner to the producer name concept.
    assertEquals(
      "farmer_owner",
      plan.fields.first { it.outputField == "producer_name" }.propertyName,
    )
    assertTrue(plan.warnings.any { "Commodity" in it && "Country" in it }, plan.warnings.toString())
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
}
