/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.openforis.collectone.v2.app.domain.usecase.ClusterMapFeaturesUseCase
import org.openforis.collectone.v2.app.domain.usecase.CompleteFormSubmissionUseCase
import org.openforis.collectone.v2.app.domain.usecase.ComputeWayfindingNavigationUseCase
import org.openforis.collectone.v2.app.domain.usecase.CreateSurveyUseCase
import org.openforis.collectone.v2.app.domain.usecase.EstimateDeviceStorageUseCase
import org.openforis.collectone.v2.app.domain.usecase.EstimateOfflineAreaUseCase
import org.openforis.collectone.v2.app.domain.usecase.ExportSurveyDataUseCase
import org.openforis.collectone.v2.app.domain.usecase.LaunchFormUseCase
import org.openforis.collectone.v2.app.domain.usecase.RecordImpactEventUseCase
import org.openforis.collectone.v2.app.domain.usecase.ResolveFormDefForLaunchUseCase
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.app.domain.usecase.SaveOfflineAreaUseCase
import org.openforis.collectone.v2.app.domain.usecase.SearchPlacesUseCase
import org.openforis.collectone.v2.app.domain.usecase.SyncMutationsUseCase
import org.openforis.collectone.v2.app.ui.datacollection.WizardAnswerDisplay

/**
 * Domain layer of the app's dependency graph: the Domain Use Cases both the mobile app and the web
 * console use. Use cases hold no state, so each consumer gets its own instance.
 */
val domainModule: Module = module {
  factory {
    RecordImpactEventUseCase(
      impactEventRepository = get(),
      surveyRepository = get(),
      surveyEditorRepository = get(),
      authRepository = get(),
      connectivityRepository = get(),
      clock = get(),
    )
  }
  factory { ExportSurveyDataUseCase(get()) }
  factory { ResolveFormDefForLaunchUseCase() }
  factory { LaunchFormUseCase(get()) }
  factory {
    CompleteFormSubmissionUseCase(
      surveyRepository = get(),
      mutationRepository = get(),
      transactionRunner = get(),
      answerDisplay = WizardAnswerDisplay,
      clock = get(),
      resolveFormDefForLaunchUseCase = get(),
    )
  }
  factory {
    SyncMutationsUseCase(
      mutationRepository = get(),
      surveyRepository = get(),
      transactionRunner = get(),
      connectivityRepository = get(),
      impactEventRepository = get(),
      outboxSyncRepository = getOrNull(),
      clock = get(),
    )
  }
  factory {
    CreateSurveyUseCase(
      surveyRepository = get(),
      surveyEditorRepository = get(),
      transactionRunner = get(),
      libraryRepository = get(),
    )
  }
  factory { ResolveLibraryUseCase() }
  factory { ComputeWayfindingNavigationUseCase() }
  factory { ClusterMapFeaturesUseCase() }
  factory { SearchPlacesUseCase() }
  factory { EstimateDeviceStorageUseCase(deviceStorageRepository = get()) }
  factory {
    EstimateOfflineAreaUseCase(
      offlineMapRepository = get(),
      deviceStorageRepository = get(),
      connectivityRepository = get(),
    )
  }
  factory { SaveOfflineAreaUseCase(offlineMapRepository = get(), connectivityRepository = get()) }
}
