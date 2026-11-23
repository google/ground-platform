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
package org.openforis.collectone.v2.app.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import org.openforis.collectone.v2.app.data.datasource.local.session.InMemoryActiveSurveyStore
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore

/**
 * [SurveyRepositoryImpl] keeps the survey the user opened, remembered so a relaunch reopens it, and
 * forgets it once that survey leaves the device or the user signs out.
 */
class SurveyRepositoryActiveSurveyTest {
  private val activeSurveyStore = InMemoryActiveSurveyStore()
  private val store = seededStore()
  private val repository = SurveyRepositoryImpl(store, activeSurveyStore)

  /** A sample survey other than the one the sample data makes active. */
  private val otherSurveyId = runDirect {
    val activeId = repository.getActiveSurveyId()
    repository.getSurveys().first { it.id != activeId }.id
  }

  /** The repository of the next launch: a fresh local store over the same remembered survey. */
  private fun relaunchedRepository() = SurveyRepositoryImpl(seededStore(), activeSurveyStore)

  @Test
  fun beforeTheUserOpensASurvey_noSurveyIsOpen() {
    assertNull(repository.observeOpenSurveyId().value)
  }

  @Test
  fun activatingASurvey_opensAndRemembersIt() {
    runDirect { repository.setActiveSurveyId(otherSurveyId) }

    assertEquals(otherSurveyId, repository.observeOpenSurveyId().value)
    assertEquals(otherSurveyId, runDirect { activeSurveyStore.load() })
  }

  @Test
  fun activatingNoSurvey_forgetsTheRememberedOne() {
    runDirect {
      repository.setActiveSurveyId(otherSurveyId)
      repository.setActiveSurveyId("")
    }

    assertNull(repository.observeOpenSurveyId().value)
    assertNull(runDirect { activeSurveyStore.load() })
  }

  @Test
  fun restoring_reopensTheRememberedSurvey() {
    runDirect { repository.setActiveSurveyId(otherSurveyId) }
    val relaunched = relaunchedRepository()
    assertNotEquals(otherSurveyId, runDirect { relaunched.getActiveSurveyId() })

    val restored = runDirect { relaunched.restoreActiveSurvey() }

    assertEquals(otherSurveyId, restored)
    assertEquals(otherSurveyId, runDirect { relaunched.getActiveSurveyId() })
    assertEquals(otherSurveyId, relaunched.observeOpenSurveyId().value)
  }

  @Test
  fun restoring_withNothingRemembered_changesNothing() {
    val activeBefore = runDirect { repository.getActiveSurveyId() }

    assertNull(runDirect { repository.restoreActiveSurvey() })
    assertEquals(activeBefore, runDirect { repository.getActiveSurveyId() })
  }

  @Test
  fun restoring_aSurveyNoLongerStored_changesNothing() {
    runDirect { activeSurveyStore.save("survey-gone") }
    val activeBefore = runDirect { repository.getActiveSurveyId() }

    assertNull(runDirect { repository.restoreActiveSurvey() })
    assertEquals(activeBefore, runDirect { repository.getActiveSurveyId() })
    assertNull(repository.observeOpenSurveyId().value)
  }

  @Test
  fun removingTheDownload_closesAndForgetsTheSurvey() {
    runDirect {
      repository.setActiveSurveyId(otherSurveyId)
      repository.setSurveyDownloaded(otherSurveyId, downloaded = false)
    }

    assertNull(repository.observeOpenSurveyId().value)
    assertNull(runDirect { activeSurveyStore.load() })
  }

  @Test
  fun removingAnotherSurveysDownload_keepsTheOpenSurvey() {
    val activeId = runDirect { repository.getActiveSurveyId() }
    runDirect {
      repository.setActiveSurveyId(otherSurveyId)
      repository.setSurveyDownloaded(activeId, downloaded = false)
    }

    assertEquals(otherSurveyId, repository.observeOpenSurveyId().value)
    assertEquals(otherSurveyId, runDirect { activeSurveyStore.load() })
  }

  @Test
  fun deletingTheSurvey_closesAndForgetsIt() {
    runDirect {
      repository.setActiveSurveyId(otherSurveyId)
      repository.deleteSurvey(otherSurveyId)
    }

    assertNull(repository.observeOpenSurveyId().value)
    assertNull(runDirect { activeSurveyStore.load() })
  }

  @Test
  fun forgetting_closesAndForgetsTheSurvey() {
    runDirect {
      repository.setActiveSurveyId(otherSurveyId)
      repository.forgetActiveSurvey()
    }

    assertNull(repository.observeOpenSurveyId().value)
    assertNull(runDirect { activeSurveyStore.load() })
  }
}
