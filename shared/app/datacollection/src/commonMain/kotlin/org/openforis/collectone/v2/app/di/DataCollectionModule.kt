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
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionViewModel

/**
 * The data collection feature's part of the app's dependency graph: its ViewModel, which views get
 * with `koinViewModel()` and which keeps the selected map feature and submission across Android
 * process death through the `SavedStateHandle` it takes (an open form is session-only).
 * `appModules` (in `:shared:app`) includes it.
 */
val dataCollectionModule: Module = module {
  viewModel {
    DataCollectionViewModel(
      surveyRepository = get(),
      settingsRepository = get(),
      authRepository = get(),
      locationRepository = get(),
      completeFormSubmission = get(),
      launchForm = get(),
      pdfExportClient = get(),
      clock = get(),
      connectivityRepository = get(),
      recordImpactEvent = get(),
      organizationRepository = get(),
      savedState = get(),
    )
  }
}
