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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.openforis.collectone.v2.app.client.auth.ScriptedAuthClient
import org.openforis.collectone.v2.app.client.auth.ScriptedAuthClient.Companion.MAYA
import org.openforis.collectone.v2.app.data.datasource.local.session.InMemoryActiveSurveyStore
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleSyncDataSource
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.writeSampleData
import org.openforis.collectone.v2.app.domain.repository.SampleDataRepository

/**
 * [UserDataSyncController] reopens the survey the user had open once the initial sync has put the
 * local data in place, and forgets it when the user signs out.
 */
class UserDataSyncActiveSurveyTest {
  /**
   * Downloads the sample data, as the initial sync does, once [pendingDownload] (if any) is done.
   */
  private inner class SampleDownload : SampleDataRepository {
    override suspend fun seedIfNeeded() {
      pendingDownload?.await()
      store.transaction { writeSampleData() }
    }

    override suspend fun resetToSampleData() = Unit
  }

  private val store = InMemoryLocalStore()
  private val activeSurveyStore = InMemoryActiveSurveyStore()
  private val surveyRepository = SurveyRepositoryImpl(store, activeSurveyStore)
  private val authClient = ScriptedAuthClient(serviceSession = MAYA)
  private var pendingDownload: CompletableDeferred<Unit>? = null

  /** A sample survey other than the one the sample data makes active. */
  private val otherSurveyId = runDirect {
    val sample = SurveyRepositoryImpl(seededStore())
    val activeId = sample.getActiveSurveyId()
    sample.getSurveys().first { it.id != activeId }.id
  }

  private fun startController() =
    UserDataSyncController(
        serviceUrl = "http://service.test",
        syncDataSource = sampleSyncDataSource(),
        authClient = authClient,
        localStore = store,
        sampleDataRepository = SampleDownload(),
        surveyRepository = surveyRepository,
        syncEngine = null,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        retryDelay = {},
      )
      .apply { start() }

  @Test
  fun initialSync_reopensTheSurveyTheUserHadOpen() {
    runNow { activeSurveyStore.save(otherSurveyId) }

    val controller = startController()

    assertTrue(controller.hasCompletedInitialSync.value)
    assertEquals(otherSurveyId, surveyRepository.observeOpenSurveyId().value)
    assertEquals(otherSurveyId, runNow { surveyRepository.getActiveSurveyId() })
  }

  @Test
  fun initialSync_withoutASurveyOpenBefore_reopensNone() {
    startController()

    assertNull(surveyRepository.observeOpenSurveyId().value)
  }

  @Test
  fun initialSync_withTheOpenSurveyGone_reopensNone() {
    runNow { activeSurveyStore.save("survey-gone") }

    startController()

    assertNull(surveyRepository.observeOpenSurveyId().value)
  }

  @Test
  fun theSessionRestoredAtLaunch_keepsTheOpenSurvey() {
    runNow { activeSurveyStore.save(otherSurveyId) }

    startController()

    assertEquals(otherSurveyId, runNow { activeSurveyStore.load() })
  }

  @Test
  fun signingOut_forgetsTheOpenSurvey() {
    runNow { activeSurveyStore.save(otherSurveyId) }
    startController()

    runNow { authClient.signOut() }

    assertNull(runNow { activeSurveyStore.load() })
    assertNull(surveyRepository.observeOpenSurveyId().value)
  }

  @Test
  fun anotherUsersInitialSync_runsAgain() {
    val controller = startController()
    pendingDownload = CompletableDeferred()

    authClient.reconnect(MAYA.copy(email = "ima.user@example.org"))

    assertFalse(controller.hasCompletedInitialSync.value)
  }

  @Test
  fun withNothingStoredOnTheDevice_theLocalDataIsLoadedBeforeTheDownloadFinishes() {
    pendingDownload = CompletableDeferred()

    val controller = startController()

    assertTrue(controller.hasLoadedLocalData.value)
    assertFalse(controller.hasCompletedInitialSync.value)
  }

  @Test
  fun signedOut_hasNoLocalDataToWaitFor() {
    runNow { authClient.signOut() }

    val controller = startController()

    assertTrue(controller.hasLoadedLocalData.value)
  }
}
