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
package org.openforis.collectone.v2.web.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorViewModel
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardViewModel

/**
 * The Survey Creation Wizard's ViewModel, which edits through the [SurveyEditorViewModel] it is
 * created with.
 */
val wizardModule: Module = module {
  viewModel { parameters ->
    CreateSurveyWizardViewModel(
      surveyRepository = get(),
      surveyEditorRepository = get(),
      organizationRepository = get(),
      authRepository = get(),
      libraryRepository = get(),
      createSurveyUseCase = get(),
      surveyEditor = parameters.get(),
      resolveLibraryUseCase = get(),
      exportSurveyDataUseCase = get(),
      outboxSyncRepository = getOrNull(),
    )
  }
}
