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
import org.koin.dsl.module
import org.openforis.collectone.v2.app.di.AppConfig
import org.openforis.collectone.v2.app.di.appModules
import org.openforis.collectone.v2.app.di.platformModule
import org.openforis.collectone.v2.web.domain.usecase.ComputeImpactUseCase
import org.openforis.collectone.v2.web.domain.usecase.CreateOrganizationUseCase
import org.openforis.collectone.v2.web.domain.usecase.GenerateSamplePlotsUseCase
import org.openforis.collectone.v2.web.domain.usecase.InviteCollaboratorUseCase
import org.openforis.collectone.v2.web.domain.usecase.InviteOrganizationMemberUseCase
import org.openforis.collectone.v2.web.domain.usecase.ManageImagerySourcesUseCase
import org.openforis.collectone.v2.web.domain.usecase.ManageLibraryUseCase
import org.openforis.collectone.v2.web.domain.usecase.SearchConceptsUseCase
import org.openforis.collectone.v2.web.domain.usecase.SurveyLifecycleUseCase
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorViewModel

/**
 * Domain Use Cases only the web console uses (organizations, library, sampling, survey lifecycle,
 * and impact). The use cases both clients use are in the shared `domainModule`.
 */
val webDomainModule: Module = module {
  factory { CreateOrganizationUseCase(get()) }
  factory { InviteOrganizationMemberUseCase(get()) }
  factory { ManageImagerySourcesUseCase(get()) }
  factory { ManageLibraryUseCase(get()) }
  factory { ComputeImpactUseCase(resolveLibrary = get()) }
  factory { SearchConceptsUseCase() }
  factory { GenerateSamplePlotsUseCase(clock = get()) }
  factory { InviteCollaboratorUseCase() }
  factory {
    SurveyLifecycleUseCase(
      surveyRepository = get(),
      authRepository = get(),
      recordImpactEvent = get(),
      clock = get(),
    )
  }
}

/**
 * ViewModels only the web console uses: each feature's module. Two take parameters from the
 * session: the Survey Creation Wizard edits through the session's [SurveyEditorViewModel], and the
 * Time series panel shows scenes on the session's survey map through a `(ImagerySource?) -> Unit`.
 */
val webViewModelModule: Module = module {
  includes(
    organizationModule,
    surveyEditorModule,
    wizardModule,
    surveysModule,
    impactModule,
    timeSeriesModule,
  )
}

/** The web console's dependency graph: the shared [appModules] plus the web-only modules. */
fun webModules(config: AppConfig, platform: Module = platformModule()): List<Module> =
  appModules(config, platform) + webDomainModule + webViewModelModule
