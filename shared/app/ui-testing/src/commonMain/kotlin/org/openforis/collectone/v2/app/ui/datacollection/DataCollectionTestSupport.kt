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
package org.openforis.collectone.v2.app.ui.datacollection

import app.cash.turbine.test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.openforis.collectone.v2.app.client.auth.FakeAuthClient
import org.openforis.collectone.v2.app.client.pdf.PdfExportClient
import org.openforis.collectone.v2.app.client.pdf.PdfExportResult
import org.openforis.collectone.v2.app.data.datasource.device.AuthDataSource
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.AuthRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.ImpactEventRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.LocalStoreTransactionRunner
import org.openforis.collectone.v2.app.data.repository.LocationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.MutationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SettingsRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.backend.SampleFallbackForms
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.usecase.CompleteFormSubmissionUseCase
import org.openforis.collectone.v2.app.domain.usecase.LaunchFormUseCase
import org.openforis.collectone.v2.app.domain.usecase.RecordImpactEventUseCase
import org.openforis.collectone.v2.app.domain.usecase.ResolveFormDefForLaunchUseCase
import org.openforis.collectone.v2.app.testing.FakeClock
import org.openforis.collectone.v2.app.testing.awaitItemMatching
import org.openforis.collectone.v2.app.ui.text.UiText

/**
 * [PdfExportClient] that records every call instead of touching the platform. [share] answers with
 * [nextShareResult] straight away.
 */
class RecordingPdfExportClient(override val canShareFiles: Boolean = true) : PdfExportClient {
  val saved = mutableListOf<String>()
  /** The bytes of each file in [saved], in the same order. */
  val savedBytes = mutableListOf<ByteArray>()
  val previewed = mutableListOf<String>()
  val shared = mutableListOf<Pair<String, String>>()
  var nextShareResult: PdfExportResult = PdfExportResult.SHARED

  override fun save(fileName: String, bytes: ByteArray) {
    saved += fileName
    savedBytes += bytes
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
 * [RecordImpactEventUseCase] writing to the same store, and a fixed [FakeClock]. Tests collect its
 * events with Turbine. Build it outside `runNow`, like other ViewModel fixtures.
 */
class DataCollectionFixture(
  val store: InMemoryLocalStore = seededStore(),
  val pdfExportClient: RecordingPdfExportClient = RecordingPdfExportClient(),
  val authClient: FakeAuthClient = FakeAuthClient(),
) {
  val surveyRepository = SurveyRepositoryImpl(store)
  val locationRepository = LocationRepositoryImpl()
  val connectivity = ConnectivityRepository()
  val authRepository = AuthRepositoryImpl(AuthDataSource(client = authClient))
  val impactEventRepository = ImpactEventRepositoryImpl(store)
  private var nextImpactEventId = 0
  val recordImpactEvent =
    RecordImpactEventUseCase(
      impactEventRepository = impactEventRepository,
      surveyRepository = surveyRepository,
      surveyEditorRepository = SurveyEditorRepositoryImpl(store),
      authRepository = authRepository,
      connectivityRepository = connectivity,
      clock = FakeClock(Instant.parse("2026-10-01T09:00:00Z")),
      newId = { "impact-${++nextImpactEventId}" },
    )
  private val resolveFormDef = ResolveFormDefForLaunchUseCase(SampleFallbackForms)

  /** The device clock new submissions record as their `clientUpdatedAt`. */
  val submissionClock = FakeClock(Instant.parse("2026-10-01T09:30:00Z"))
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
          answerDisplay = WizardAnswerDisplay,
          clock = submissionClock,
          resolveFormDefForLaunchUseCase = resolveFormDef,
        ),
      launchForm = LaunchFormUseCase(resolveFormDef),
      pdfExportClient = pdfExportClient,
      clock = FakeClock(Instant.fromEpochMilliseconds(1_700_000_000_000L)),
      connectivityRepository = connectivity,
      recordImpactEvent = recordImpactEvent,
      organizationRepository = OrganizationRepositoryImpl(store),
    )
  val uiState
    get() = viewModel.uiState.value
}

/**
 * This ViewModel's state once [predicate] holds. A PDF export loads the report's text (string
 * resources) on other threads, so tests wait for its outcome instead of reading the state right
 * after asking for it. Call from `runTest`.
 */
suspend fun DataCollectionViewModel.awaitUiState(
  predicate: (DataCollectionUiState) -> Boolean
): DataCollectionUiState {
  lateinit var matching: DataCollectionUiState
  uiState.test(timeout = 10.seconds) {
    matching = awaitItemMatching(predicate)
    cancelAndIgnoreRemainingEvents()
  }
  return matching
}

/** The Share PDF sheet, once the PDF asked for with `share…Pdf` is generated. */
suspend fun DataCollectionViewModel.awaitSharedPdfSheet(): SharedPdfSheetState =
  checkNotNull(awaitUiState { it.activeSharedPdfSheet != null }.activeSharedPdfSheet)

/** The PDF export message, once a PDF asked for with `download…Pdf` is saved. */
suspend fun DataCollectionViewModel.awaitPdfExportMessage(): UiText =
  checkNotNull(awaitUiState { it.pdfExportMessage != null }.pdfExportMessage)
