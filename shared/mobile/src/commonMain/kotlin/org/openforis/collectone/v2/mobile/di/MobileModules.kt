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
package org.openforis.collectone.v2.mobile.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.openforis.collectone.v2.app.di.AppConfig
import org.openforis.collectone.v2.app.di.UserDataSyncController
import org.openforis.collectone.v2.app.di.appModules
import org.openforis.collectone.v2.app.di.platformModule
import org.openforis.collectone.v2.app.domain.repository.AuthRepository
import org.openforis.collectone.v2.core.version.ClientVersionHeaders
import org.openforis.collectone.v2.mobile.MobileLaunchViewModel
import org.openforis.collectone.v2.mobile.ui.navigation.MainSurveyViewModel
import org.openforis.collectone.v2.mobile.ui.offlinemaps.OfflineAreaSelectionViewModel
import org.openforis.collectone.v2.mobile.ui.offlinemaps.OfflineMapsViewModel

/**
 * The mobile app's dependency graph for the user data service at [userDataServiceUrl]: the shared
 * modules, keeping only the surveys the user downloaded, plus [mobileViewModelModule].
 */
fun mobileModules(userDataServiceUrl: String, platform: Module = platformModule()): List<Module> =
  appModules(
    AppConfig(
      userDataServiceUrl = userDataServiceUrl,
      syncDownloadedSurveysOnly = true,
      clientPlatform = ClientVersionHeaders.PLATFORM_MOBILE,
    ),
    platform,
  ) + mobileViewModelModule

/** ViewModels only the mobile app uses. */
val mobileViewModelModule: Module = module {
  viewModel { MainSurveyViewModel(savedState = get()) }
  viewModel {
    OfflineAreaSelectionViewModel(
      offlineMapRepository = get(),
      estimateOfflineArea = get(),
      saveOfflineArea = get(),
    )
  }
  viewModel {
    OfflineMapsViewModel(
      offlineMapRepository = get(),
      mutationRepository = get(),
      surveyRepository = get(),
      estimateDeviceStorage = get(),
    )
  }
  viewModel {
    MobileLaunchViewModel(
      restoredSession = get<AuthRepository>().observeRestoredSession(),
      hasLoadedLocalData = get<UserDataSyncController>().hasLoadedLocalData,
      surveyRepository = get(),
    )
  }
}
