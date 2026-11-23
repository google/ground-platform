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
package org.openforis.collectone.v2.web.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.ui.datacollection.SearchableListUiState
import org.openforis.collectone.v2.app.ui.session.SurveyRecordsUiStates
import org.openforis.collectone.v2.app.ui.session.searchableListUiState
import org.openforis.collectone.v2.app.ui.session.surveyRecordsUiStatesOf
import org.openforis.collectone.v2.web.ui.impact.ImpactViewModel
import org.openforis.collectone.v2.web.ui.organization.OrganizationViewModel
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorViewModel
import org.openforis.collectone.v2.web.ui.timeseries.TimeSeriesViewModel
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardViewModel

// The ViewModels a test session's pages show, for tests that drive them or check their state. They
// come from the same stores as the pages' `koinViewModel()` calls, so a test and the page it shows
// share one instance whichever asks first.

/**
 * The ViewModel of type [T] that [owner] keeps, created from [dataHolder]'s graph with [parameters]
 * if it has none yet (as `koinViewModel()` would).
 */
inline fun <reified T : ViewModel> storedViewModel(
  owner: ViewModelStoreOwner,
  dataHolder: AppDataHolder,
  vararg parameters: Any,
): T =
  ViewModelProvider.create(
      owner.viewModelStore,
      viewModelFactory { initializer { dataHolder.newViewModel<T>(*parameters) } },
    )
    .get(T::class)

/** The session-scoped ViewModel of type [T] (see [sessionViewModel]). */
inline fun <reified T : ViewModel> WebAppState.sessionViewModelForTest(vararg parameters: Any): T =
  storedViewModel(sessionViewModels, dataHolder, *parameters)

/** The session's organizations directory and organization pages. */
val WebAppState.organization: OrganizationViewModel
  get() = sessionViewModelForTest()

/** The session's Survey editor. */
val WebAppState.surveyEditor: SurveyEditorViewModel
  get() = sessionViewModelForTest()

/** The session's Survey Creation Wizard. */
val WebAppState.createSurveyWizard: CreateSurveyWizardViewModel
  get() = sessionViewModelForTest(surveyEditor)

/** The session's Impact views. */
val WebAppState.impact: ImpactViewModel
  get() = sessionViewModelForTest()

/** The session's Time series panel. */
val WebAppState.timeSeries: TimeSeriesViewModel
  get() = sessionViewModelForTest(viewModels.surveyMap::showPinnedScene)

/** The ID of the organization the organization page shows, or `null`. */
val WebAppState.openOrganizationId: String?
  get() = organization.uiState.value.openOrganizationId

/** Whether the signed-in user may open the Survey editor for the active survey. */
val WebAppState.canManageActiveSurvey: Boolean
  get() = activeSurveyAccess.canManageSurvey(organization.uiState.value.isPlatformAdmin)

/** The session's record surfaces as the dashboard would build them now. */
val WebAppState.surveyRecords: SurveyRecordsUiStates
  get() =
    surveyRecordsUiStatesOf(
      viewModels = viewModels,
      data = dataHolder.appData.value,
      notice = notices.notice.value,
      isAlwaysOnline = true,
    )

/** The dashboard's searchable list as it would show now. */
val WebAppState.searchableList: SearchableListUiState
  get() = surveyRecords.searchableListUiState(viewModels.surveyMap)
