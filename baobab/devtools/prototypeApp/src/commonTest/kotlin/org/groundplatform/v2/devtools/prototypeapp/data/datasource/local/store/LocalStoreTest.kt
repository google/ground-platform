/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeEntitiesData
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeSubmissionsData
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeSurveysData
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SampleDataSeeder
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SurveyEditorSamples
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus
import org.groundplatform.v2.devtools.prototypeapp.surveyeditor.SurveyEditorDraft

/** Runs [block] to completion on an immediate dispatcher, as the app does for store writes. */
internal fun <T> runNow(block: suspend () -> T): T {
  var result: Result<T>? = null
  CoroutineScope(Dispatchers.Unconfined).launch { result = runCatching { block() } }
  return checkNotNull(result) { "Block suspended; expected it to complete immediately" }
    .getOrThrow()
}

/** Returns an [InMemoryLocalStore] filled with the sample data. */
internal fun seededStore(): InMemoryLocalStore =
  InMemoryLocalStore().also { store -> runDirect { SampleDataSeeder(store).seedIfNeeded() } }

/**
 * Runs [block] synchronously without a dispatcher, so it also works when called from inside
 * [runNow]. The block must not suspend.
 */
internal fun <T> runDirect(block: suspend () -> T): T {
  var result: Result<T>? = null
  block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
  return checkNotNull(result) { "Block suspended; expected it to complete immediately" }
    .getOrThrow()
}

/**
 * Behavior every [LocalStore] backend must have. Each backend gets a subclass that implements
 * [createStore]; the in-memory store is [LocalStoreTest], and a persistent backend added with the
 * sync engine must pass the same tests.
 *
 * Tests drive the store with [runNow], so a backend that really suspends (for example on disk I/O)
 * needs a blocking runner in its subclass's platform test source set.
 */
abstract class LocalStoreContractTest {
  /** Returns a new, empty store. */
  abstract fun createStore(): LocalStore

  /** Returns a new store filled with the sample data. */
  private fun seededStore(): LocalStore =
    createStore().also { store -> runDirect { SampleDataSeeder(store).seedIfNeeded() } }

  @Test
  fun seeder_writesEverySurveysSampleDataExactly() {
    val store = seededStore()
    val surveys = PrototypeFakeSurveysData.defaultSampleSurveys()
    runNow {
      store.transaction {
        assertEquals(surveys, surveys())
        assertEquals(PrototypeFakeOrganizationsData.defaultOrganizations(), organizations())
        for (survey in surveys) {
          assertEquals(PrototypeFakeEntitiesData.entitiesForSurvey(survey.id), entities(survey.id))
          assertEquals(PrototypeFakeSurveysData.formsForSurvey(survey.id), forms(survey.id))
          assertEquals(
            PrototypeFakeSubmissionsData.standaloneSubmissionsForSurvey(survey.id),
            standaloneSubmissions(survey.id),
          )
          assertTrue(surveyConfig(survey.id)!!.primaryFormXml.isNotBlank())
        }
        assertEquals(SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID, preferences().activeSurveyId)
        assertEquals(SampleDataSeeder.SEED_VERSION, preferences().seedVersion)
        assertEquals(
          SurveyEditorSamples.draft(),
          surveyEditorDraft(SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID),
        )
      }
    }
  }

  @Test
  fun deleteOrganization_makesItsSurveysPersonal() {
    val store = seededStore()
    val orgId = PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE
    runNow {
      store.transaction {
        assertTrue(surveys().any { it.organizationId == orgId })
        deleteOrganization(orgId)
        assertNull(organization(orgId))
        assertTrue(surveys().none { it.organizationId == orgId })
        // Other organizations and their surveys are untouched.
        assertNotNull(organization(PrototypeFakeOrganizationsData.MEKONG_MANGROVE_ALLIANCE))
        assertTrue(
          surveys().any {
            it.organizationId == PrototypeFakeOrganizationsData.MEKONG_MANGROVE_ALLIANCE
          }
        )
      }
    }
  }

  @Test
  fun upsertOrganization_appendsNewAndReplacesExisting() {
    val store = seededStore()
    runNow {
      store.transaction {
        val before = organizations()
        val renamed = before.first().copy(name = "Renamed")
        upsertOrganization(renamed)
        assertEquals(renamed, organizations().first())
        assertEquals(before.size, organizations().size)
        val added = renamed.copy(id = "org-new", name = "New org")
        upsertOrganization(added)
        assertEquals(added, organizations().last())
        assertEquals(before.size + 1, organizations().size)
      }
    }
  }

  @Test
  fun seedIfNeeded_doesNotOverwriteExistingData() {
    val store = seededStore()
    runNow { store.transaction { putMutations(emptyList()) } }
    runNow { SampleDataSeeder(store).seedIfNeeded() }
    assertTrue(runNow { store.transaction { mutations() } }.isEmpty())
  }

  @Test
  fun reset_restoresSampleData() {
    val store = seededStore()
    runNow { store.transaction { putMutations(emptyList()) } }
    runNow { SampleDataSeeder(store).reset() }
    assertTrue(runNow { store.transaction { mutations() } }.isNotEmpty())
  }

  @Test
  fun entities_storeSubmissionsSeparatelyAndJoinOnRead() {
    val store = seededStore()
    val surveyId = SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID
    runNow {
      store.transaction {
        val entity = entities(surveyId).first { it.submissions.isNotEmpty() }
        val updated =
          entity.copy(
            submissions = entity.submissions.map { it.copy(syncStatus = SyncStatus.FAILED) }
          )
        upsertEntity(surveyId, updated)
        assertEquals(updated, entity(surveyId, entity.id))
        assertEquals(entities(surveyId).size, entityCount(surveyId))
      }
    }
  }

  @Test
  fun transaction_isAtomicWhenBlockThrows() {
    val store = seededStore()
    val before = runNow { store.transaction { mutations() } }
    assertFailsWith<IllegalStateException> {
      runNow {
        store.transaction {
          putMutations(emptyList())
          error("boom")
        }
      }
    }
    assertEquals(before, runNow { store.transaction { mutations() } })
  }

  @Test
  fun nestedTransaction_joinsOuterTransaction() {
    val store = seededStore()
    runNow {
      store.transaction {
        putMutations(emptyList())
        // A nested transaction sees the outer transaction's uncommitted write.
        assertTrue(store.transaction { mutations() }.isEmpty())
      }
    }
  }

  @Test
  fun surveyEdits_surviveSwitchingActiveSurvey() {
    val store = seededStore()
    val kenya = SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID
    runNow {
      store.transaction {
        deleteEntity(kenya, entities(kenya).first().id)
        updatePreferences { it.copy(activeSurveyId = "survey-sample-plots-forest") }
        updatePreferences { it.copy(activeSurveyId = kenya) }
      }
    }
    val count = runNow { store.transaction { entityCount(kenya) } }
    assertEquals(PrototypeFakeEntitiesData.entitiesForSurvey(kenya).size - 1, count)
  }

  @Test
  fun clear_removesEverything() {
    val store = seededStore()
    runNow { store.clear() }
    runNow {
      store.transaction {
        assertTrue(surveys().isEmpty())
        assertNull(surveyConfig(SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID))
      }
    }
  }

  @Test
  fun seedIfNeeded_reseedsWhenSeedVersionChanges() {
    val store = seededStore()
    runNow {
      store.transaction {
        putMutations(emptyList())
        updatePreferences { it.copy(seedVersion = SampleDataSeeder.SEED_VERSION - 1) }
      }
    }
    runNow { SampleDataSeeder(store).seedIfNeeded() }
    assertTrue(runNow { store.transaction { mutations() } }.isNotEmpty())
  }

  @Test
  fun surveyEditorDraft_isStoredPerSurveyAndObservable() {
    val store = seededStore()
    val forest = "survey-sample-plots-forest"
    val draft = SurveyEditorDraft.blank(forest, title = "Forest plots")
    assertNull(runNow { store.transaction { surveyEditorDraft(forest) } })
    runNow { store.transaction { putSurveyEditorDraft(forest, draft) } }
    assertEquals(draft, runNow { store.observeSurveyEditorDraft(forest).first() })
    assertEquals(
      SurveyEditorSamples.draft(),
      runNow { store.transaction { surveyEditorDraft(SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID) } },
    )
  }

  @Test
  fun observeSurveyConfigs_includesEverySeededSurvey() {
    val store = seededStore()
    val configs = runNow { store.observeSurveyConfigs().first() }
    assertEquals(
      PrototypeFakeSurveysData.defaultSampleSurveys().map { it.id }.toSet(),
      configs.keys,
    )
  }
}

/** [LocalStoreContractTest] for [InMemoryLocalStore], plus its in-memory-only guarantees. */
class LocalStoreTest : LocalStoreContractTest() {
  override fun createStore(): LocalStore = InMemoryLocalStore()

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun observers_onImmediateDispatcher_seeWritesSynchronously() {
    val store = seededStore()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
      val activeEntityCount =
        store
          .observePreferences()
          .map { it.activeSurveyId }
          .flatMapLatest { id ->
            combine(store.observeEntities(id), store.observeForms(id)) { e, _ -> e.size }
          }
          .stateIn(scope, SharingStarted.Eagerly, -1)
      val kenya = SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID
      assertEquals(PrototypeFakeEntitiesData.entitiesForSurvey(kenya).size, activeEntityCount.value)

      runNow { store.transaction { deleteEntity(kenya, entities(kenya).first().id) } }
      assertEquals(
        PrototypeFakeEntitiesData.entitiesForSurvey(kenya).size - 1,
        activeEntityCount.value,
      )

      val forest = "survey-sample-plots-forest"
      runNow { store.transaction { updatePreferences { it.copy(activeSurveyId = forest) } } }
      assertEquals(
        PrototypeFakeEntitiesData.entitiesForSurvey(forest).size,
        activeEntityCount.value,
      )
    } finally {
      scope.cancel()
    }
  }
}
