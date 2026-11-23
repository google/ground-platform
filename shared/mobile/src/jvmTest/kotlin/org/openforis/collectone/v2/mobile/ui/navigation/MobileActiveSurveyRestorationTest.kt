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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import org.openforis.collectone.v2.app.client.auth.FakeAuthClient
import org.openforis.collectone.v2.app.data.datasource.local.session.ActiveSurveyStore
import org.openforis.collectone.v2.app.data.datasource.local.session.InMemoryActiveSurveyStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.di.AppConfig
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.di.createAppDataHolder
import org.openforis.collectone.v2.app.di.platformModule
import org.openforis.collectone.v2.mobile.MobileApp
import org.openforis.collectone.v2.mobile.MobileAppSession
import org.openforis.collectone.v2.mobile.di.mobileViewModelModule
import org.openforis.collectone.v2.mobile.ui.MobileAppState

/**
 * After Android process death, the Main Survey the `NavHost` restores shows the survey the user had
 * open, which the app reopens from the device's local database before it starts; when that survey
 * is gone, the app starts over at the surveys on the device instead of showing another survey. A
 * relaunch without saved navigation state opens the survey the user had open, too.
 */
@OptIn(ExperimentalTestApi::class)
class MobileActiveSurveyRestorationTest {
  /** The survey kept in the device's local database, which outlives the app's processes. */
  private val storedActiveSurvey = InMemoryActiveSurveyStore()

  /**
   * A process of the app: its own dependency graph (signed in, with the terms accepted, as the
   * stored account restores it) and the UI state it restored from its last process ([saved]).
   */
  private inner class AppProcess(saved: Map<String, List<Any?>>?) {
    val registry = SaveableStateRegistry(saved) { true }
    val dataHolder: AppDataHolder =
      createAppDataHolder(
          config = AppConfig(userDataClient = { _ -> SeedUserDataService().client() }),
          platform = platformModule(authClient = ::FakeAuthClient),
          overrides =
            module {
              single<LocalStore> { seededStore() }
              single<ActiveSurveyStore> { storedActiveSurvey }
            },
        )
        .also { holder ->
          holder.koin.loadModules(listOf(mobileViewModelModule))
          runBlocking {
            holder.authRepository.signInWithGoogle()
            holder.authRepository.acceptTerms()
          }
        }
    val sessions = mutableListOf<MobileAppState>()
  }

  private val dispatcherOwner =
    object : NavigationEventDispatcherOwner {
      override val navigationEventDispatcher = NavigationEventDispatcher()
    }

  /** A sample survey other than the one a new process makes active before reopening any. */
  private val surveyId = runBlocking {
    val sample = SurveyRepositoryImpl(seededStore())
    val defaultId = sample.getActiveSurveyId()
    sample.getSurveys().first { it.id != defaultId }.id
  }

  @Test
  fun processDeath_atTheMainSurvey_reopensTheSurveyTheUserHadOpen() = runComposeUiTest {
    var process by mutableStateOf<AppProcess?>(AppProcess(saved = null))
    setContent { process?.let { ComposeApp(it) } }
    val first = checkNotNull(process)
    runOnIdle { first.sessions.last().viewModels.onboarding.openSurvey(surveyId) }
    waitUntil { first.sessions.last().currentRoute == MobileRoute.MainSurvey }

    val restarted = restart(first) { process = it }

    val session = restarted.sessions.last()
    assertEquals(MobileRoute.MainSurvey, session.currentRoute)
    assertEquals(surveyId, runOnIdle { session.activeSurveyAccess.activeSurvey.id })
  }

  @Test
  fun processDeath_withTheOpenSurveyGone_startsOverAtTheSurveysOnTheDevice() = runComposeUiTest {
    var process by mutableStateOf<AppProcess?>(AppProcess(saved = null))
    setContent { process?.let { ComposeApp(it) } }
    val first = checkNotNull(process)
    runOnIdle { first.sessions.last().viewModels.onboarding.openSurvey(surveyId) }
    waitUntil { first.sessions.last().currentRoute == MobileRoute.MainSurvey }
    // For example removed from the device by a sync after the user lost access.
    runBlocking { storedActiveSurvey.save("survey-gone") }

    val restarted = restart(first) { process = it }

    val session = restarted.sessions.last()
    assertEquals(MobileRoute.DownloadedSurveys, session.currentRoute)
    assertEquals(listOf(MobileRoute.DownloadedSurveys), session.navigator.backStack)
  }

  @Test
  fun relaunch_withTheSurveyLastOpenedOnTheDevice_opensIt() = runComposeUiTest {
    var process by mutableStateOf<AppProcess?>(AppProcess(saved = null))
    setContent { process?.let { ComposeApp(it) } }
    val first = checkNotNull(process)
    runOnIdle { first.sessions.last().viewModels.onboarding.openSurvey(surveyId) }
    waitUntil { first.sessions.last().currentRoute == MobileRoute.MainSurvey }

    val relaunched = relaunch { process = it }

    val session = relaunched.sessions.last()
    assertEquals(listOf<MobileRoute>(MobileRoute.MainSurvey), session.navigator.backStack)
    assertEquals(surveyId, runOnIdle { session.activeSurveyAccess.activeSurvey.id })
  }

  @Composable
  private fun ComposeApp(process: AppProcess) {
    CompositionLocalProvider(
      LocalSaveableStateRegistry provides process.registry,
      LocalNavigationEventDispatcherOwner provides dispatcherOwner,
    ) {
      MobileAppSession(process.dataHolder) { state ->
        if (process.sessions.lastOrNull() !== state) process.sessions.add(state)
        MobileApp(state)
      }
    }
  }

  /** Closes the app and launches it again through [show], without any saved UI state. */
  private fun ComposeUiTest.relaunch(show: (AppProcess?) -> Unit): AppProcess {
    runOnIdle { show(null) }
    waitForIdle()
    val next = AppProcess(saved = null)
    runOnIdle { show(next) }
    waitForIdle()
    return next
  }

  /** Kills [process], keeping the UI state it saved, and starts the next one through [show]. */
  private fun ComposeUiTest.restart(
    process: AppProcess,
    show: (AppProcess?) -> Unit,
  ): AppProcess {
    val saved = runOnIdle { process.registry.performSave() }
    runOnIdle { show(null) }
    waitForIdle()
    val next = AppProcess(saved)
    runOnIdle { show(next) }
    waitForIdle()
    return next
  }
}
