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

import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.client.pdf.PdfExportClient
import org.groundplatform.v2.devtools.prototypeapp.client.pdf.PdfExportResult
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.InMemoryLocalStore
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.AuthRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.ImpactEventRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LocalStoreTransactionRunner
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LocationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.MutationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SettingsRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.ConnectivityRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CompleteFormSubmissionUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.LaunchFormUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.RecordImpactEventUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolveFormDefForLaunchUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.DataCollectionEvent

/**
 * [PdfExportClient] that records every call instead of touching the platform. [share] answers with
 * [nextShareResult] straight away.
 */
internal class RecordingPdfExportClient(override val canShareFiles: Boolean = true) :
  PdfExportClient {
  val saved = mutableListOf<String>()
  val previewed = mutableListOf<String>()
  val shared = mutableListOf<Pair<String, String>>()
  var nextShareResult: PdfExportResult = PdfExportResult.SHARED

  override fun save(fileName: String, bytes: ByteArray) {
    saved += fileName
  }

  override fun preview(fileName: String, bytes: ByteArray) {
    previewed += fileName
  }

  override fun share(
    fileName: String,
    title: String,
    bytes: ByteArray,
    onResult: (PdfExportResult) -> Unit,
  ) {
    shared += fileName to title
    onResult(nextShareResult)
  }
}

/**
 * A [DataCollectionViewModel] over the seeded sample data with a [RecordingPdfExportClient], a
 * [RecordImpactEventUseCase] writing to the same store, and a recorder of its
 * [DataCollectionEvent]s. Build it outside `runNow`, like other ViewModel fixtures.
 */
internal class DataCollectionFixture(
  val store: InMemoryLocalStore = seededStore(),
  val pdfExportClient: RecordingPdfExportClient = RecordingPdfExportClient(),
) {
  val scope = testScope()
  val surveyRepository = SurveyRepositoryImpl(store)
  val locationRepository = LocationRepositoryImpl()
  val connectivity = ConnectivityRepository()
  val authRepository = AuthRepositoryImpl()
  val impactEventRepository = ImpactEventRepositoryImpl(store)
  private var nextImpactEventId = 0
  val recordImpactEvent =
    RecordImpactEventUseCase(
      impactEventRepository = impactEventRepository,
      surveyRepository = surveyRepository,
      surveyEditorRepository = SurveyEditorRepositoryImpl(store),
      authRepository = authRepository,
      connectivityRepository = connectivity,
      now = { "2026-10-01T09:00:00Z" },
      newId = { "impact-${++nextImpactEventId}" },
    )
  private val resolveFormDef = ResolveFormDefForLaunchUseCase()
  val viewModel =
    DataCollectionViewModel(
      surveyRepository = surveyRepository,
      settingsRepository = SettingsRepositoryImpl(store),
      authRepository = authRepository,
      locationRepository = locationRepository,
      completeFormSubmission =
        CompleteFormSubmissionUseCase(
          surveyRepository = surveyRepository,
          mutationRepository = MutationRepositoryImpl(store),
          transactionRunner = LocalStoreTransactionRunner(store),
          resolveFormDefForLaunchUseCase = resolveFormDef,
        ),
      launchForm = LaunchFormUseCase(resolveFormDef),
      pdfExportClient = pdfExportClient,
      now = { 1_700_000_000_000L },
      connectivityRepository = connectivity,
      scope = scope,
      recordImpactEvent = recordImpactEvent,
    )
  val events = mutableListOf<DataCollectionEvent>()

  init {
    scope.launch { viewModel.events.collect { events += it } }
  }

  val uiState
    get() = viewModel.uiState.value

  /** Drops the events recorded so far, to assert on the next action alone. */
  fun clearEvents() = events.clear()
}
