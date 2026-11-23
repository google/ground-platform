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
package org.openforis.collectone.v2.app.ui

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.ui.dashboard.DashboardViewModel
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionViewModel
import org.openforis.collectone.v2.app.ui.listsearch.ListSearchViewModel
import org.openforis.collectone.v2.app.ui.map.SurveyMapViewModel
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingViewModel
import org.openforis.collectone.v2.app.ui.settings.SettingsViewModel

/**
 * The feature ViewModels of one client session, which both clients use and a
 * [org.openforis.collectone.v2.app.ui.session.SessionCoordinator] wires together.
 */
data class AppViewModels(
  val settings: SettingsViewModel,
  val onboarding: OnboardingViewModel,
  val surveyMap: SurveyMapViewModel,
  val dashboard: DashboardViewModel,
  val dataCollection: DataCollectionViewModel,
  val listSearch: ListSearchViewModel,
) {
  companion object {
    /**
     * New ViewModels from [dataHolder]'s graph that no `ViewModelStoreOwner` keeps, for sessions
     * built outside composition (tests and the workbench).
     */
    fun create(dataHolder: AppDataHolder, isAlwaysOnline: Boolean = false): AppViewModels =
      AppViewModels(
        settings = dataHolder.newViewModel(),
        onboarding = dataHolder.newViewModel(),
        surveyMap = dataHolder.newViewModel(),
        dashboard = dataHolder.newViewModel(),
        dataCollection = dataHolder.newViewModel(),
        listSearch = dataHolder.newViewModel(isAlwaysOnline),
      )
  }
}

/**
 * The [AppViewModels] of the current `ViewModelStoreOwner` (the Android activity, or the browser
 * window), from the Koin graph: they outlive configuration changes and restore their saved state
 * after Android process death.
 */
@Composable
fun koinAppViewModels(isAlwaysOnline: Boolean = false): AppViewModels =
  AppViewModels(
    settings = koinViewModel(),
    onboarding = koinViewModel(),
    surveyMap = koinViewModel(),
    dashboard = koinViewModel(),
    dataCollection = koinViewModel(),
    listSearch = koinViewModel { parametersOf(isAlwaysOnline) },
  )
