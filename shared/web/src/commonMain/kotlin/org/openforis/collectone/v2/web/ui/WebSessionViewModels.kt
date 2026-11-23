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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.parameter.parametersOf
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.ui.map.SurveyMapViewModel
import org.openforis.collectone.v2.web.di.webDomainModule
import org.openforis.collectone.v2.web.di.webViewModelModule
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorViewModel
import org.openforis.collectone.v2.web.ui.timeseries.TimeSeriesViewModel
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardViewModel

// The web console keeps the ViewModels that several of its pages share in the session's
// ViewModelStoreOwner (WebAppState.sessionViewModels), and the ViewModels of one page in that
// page's back-stack entry (the surveys page's SurveyOutcomePromptViewModel). Session-scoped:
// - OrganizationViewModel: organization pages, the header, the route guard, and invitations.
// - SurveyEditorViewModel: the Survey editor, the dashboard's survey toolbar, the header's save
//   status, and the Survey Creation Wizard, which edits through it.
// - CreateSurveyWizardViewModel: remembers its working survey between visits to the wizard, so
//   opening it again doesn't leave an untouched blank survey behind.
// - ImpactViewModel: the Impact page, and its messages on the dashboard.
// - TimeSeriesViewModel: pins scenes on the session's survey map and keeps results for the session.

/**
 * The [ViewModelStoreOwner] of the ViewModels that several web console pages share (see
 * [WebAppState.sessionViewModels]). [ProvideWebSession] provides it.
 */
val LocalWebSessionViewModelStoreOwner =
  staticCompositionLocalOf<ViewModelStoreOwner> {
    error("No web console session here: wrap the content in ProvideWebSession")
  }

/**
 * Provides [state]'s Koin graph and its session-scoped ViewModels (see [sessionViewModel]) to
 * [content].
 */
@Composable
fun ProvideWebSession(state: WebAppState, content: @Composable () -> Unit) {
  KoinContext(state.dataHolder.koin) {
    CompositionLocalProvider(
      LocalWebSessionViewModelStoreOwner provides state.sessionViewModels,
      content = content,
    )
  }
}

/**
 * The session-scoped ViewModel of type [T], from the Koin graph: created with [parameters] the
 * first time a page asks for it, and kept until the session ends.
 */
@Composable
inline fun <reified T : ViewModel> sessionViewModel(
  noinline parameters: ParametersDefinition? = null
): T =
  koinViewModel(
    viewModelStoreOwner = LocalWebSessionViewModelStoreOwner.current,
    parameters = parameters,
  )

/** The session's Survey Creation Wizard, which edits through the session's Survey editor. */
@Composable
fun sessionCreateSurveyWizardViewModel(): CreateSurveyWizardViewModel {
  val surveyEditor = sessionViewModel<SurveyEditorViewModel>()
  return sessionViewModel { parametersOf(surveyEditor) }
}

/** The session's Time series panel, which shows its scenes on [surveyMap]. */
@Composable
fun sessionTimeSeriesViewModel(surveyMap: SurveyMapViewModel): TimeSeriesViewModel =
  sessionViewModel {
    parametersOf(surveyMap::showPinnedScene)
  }

/**
 * A new session store for a session built outside composition (tests), over [dataHolder]'s graph
 * with the web modules added to it. In the browser the session uses the window's store instead.
 */
fun standaloneSessionViewModels(dataHolder: AppDataHolder): ViewModelStoreOwner {
  dataHolder.koin.loadModules(listOf(webDomainModule, webViewModelModule))
  return object : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
  }
}
