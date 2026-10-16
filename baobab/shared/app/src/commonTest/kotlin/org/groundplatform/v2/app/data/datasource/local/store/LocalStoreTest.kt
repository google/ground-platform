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
package org.groundplatform.v2.app.data.datasource.local.store

import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.survey.Organization as OrganizationProto
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyFieldPatch
import groundplatform.v2.survey.SurveyMutation
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
import org.groundplatform.v2.app.data.seed.SampleDataSeeder
import org.groundplatform.v2.app.data.seed.SampleEntitiesData
import org.groundplatform.v2.app.data.seed.SampleOrganizationsData
import org.groundplatform.v2.app.data.seed.SampleSubmissionsData
import org.groundplatform.v2.app.data.seed.SampleSurveyEditorData
import org.groundplatform.v2.app.data.seed.SampleSurveysData
import org.groundplatform.v2.app.domain.model.OrganizationLibrary
import org.groundplatform.v2.app.domain.model.SyncStatus
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDraft

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
    createStore().also { store ->
      runDirect { SampleDataSeeder(store, sampleSyncDataSource()).seedIfNeeded() }
    }

  @Test
  fun seeder_writesEverySurveysSampleDataExactly() {
    val store = seededStore()
    val surveys = SampleSurveysData.defaultSampleSurveys()
    runNow {
      store.transaction {
        assertEquals(surveys, surveys())
        assertEquals(SampleOrganizationsData.defaultOrganizations(), organizations())
        for (survey in surveys) {
          assertEquals(SampleEntitiesData.entitiesForSurvey(survey.id), entities(survey.id))
          assertEquals(SampleSurveysData.formsForSurvey(survey.id), forms(survey.id))
          assertEquals(
            SampleSubmissionsData.standaloneSubmissionsForSurvey(survey.id),
            standaloneSubmissions(survey.id),
          )
          assertNotNull(surveyConfig(survey.id)!!.primaryForm)
        }
        assertEquals(SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID, preferences().activeSurveyId)
        assertEquals(SampleDataSeeder.SEED_VERSION, preferences().seedVersion)
        val kenyaDraft = surveyEditorDraft(SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID)!!
        assertEquals(
          forms(SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID).map { it.id },
          kenyaDraft.forms.map { it.form.formId },
        )
        assertEquals(SampleSurveyEditorData.kenyaSharing(), kenyaDraft.sharing)
      }
    }
  }

  @Test
  fun deleteOrganization_makesItsSurveysPersonal() {
    val store = seededStore()
    val orgId = SampleOrganizationsData.KENYA_FOREST_SERVICE
    runNow {
      store.transaction {
        assertTrue(surveys().any { it.organizationId == orgId })
        deleteOrganization(orgId)
        assertNull(organization(orgId))
        assertTrue(surveys().none { it.organizationId == orgId })
        // Other organizations and their surveys are untouched.
        assertNotNull(organization(SampleOrganizationsData.MEKONG_MANGROVE_ALLIANCE))
        assertTrue(
          surveys().any {
            it.organizationId == SampleOrganizationsData.MEKONG_MANGROVE_ALLIANCE
          }
        )
      }
    }
  }

  @Test
  fun deleteLibrary_removesOnlyThatLibrary() {
    val store = createStore()
    runNow {
      store.transaction {
        putLibrary(OrganizationLibrary("o1"))
        putLibrary(OrganizationLibrary("o2"))
        deleteLibrary("o1")
        deleteLibrary("missing")
      }
    }
    assertEquals(setOf("o2"), runNow { store.transaction { libraries().keys } })
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
    runNow { SampleDataSeeder(store, sampleSyncDataSource()).seedIfNeeded() }
    assertTrue(runNow { store.transaction { mutations() } }.isEmpty())
  }

  @Test
  fun reset_restoresSampleData() {
    val store = seededStore()
    runNow { store.transaction { putMutations(emptyList()) } }
    runNow { SampleDataSeeder(store, sampleSyncDataSource()).reset() }
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
    assertEquals(SampleEntitiesData.entitiesForSurvey(kenya).size - 1, count)
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
    runNow { SampleDataSeeder(store, sampleSyncDataSource()).seedIfNeeded() }
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
    val kenyaDraft =
      runNow {
        store.transaction { surveyEditorDraft(SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID) }
      }!!
    assertTrue(
      kenyaDraft.datasets.any { it.id == SampleSurveyEditorData.farmers().id },
      "seeded Kenya draft keeps its Data tables",
    )
  }

  @Test
  fun observeSurveyConfigs_includesEverySeededSurvey() {
    val store = seededStore()
    val configs = runNow { store.observeSurveyConfigs().first() }
    assertEquals(
      SampleSurveysData.defaultSampleSurveys().map { it.id }.toSet(),
      configs.keys,
    )
  }

  @Test
  fun outbox_isStoredInClientSeqOrder_andObservable() {
    val store = createStore()
    val first = pendingTitle(seq = 1, title = "A")
    val second = pendingTitle(seq = 2, title = "B")
    runNow { store.transaction { putOutbox(listOf(second, first)) } }

    assertEquals(listOf(first, second), runNow { store.observeOutbox().first() })
    assertEquals(listOf(first, second), runNow { store.transaction { outbox() } })
  }

  @Test
  fun failedTransaction_leavesOutboxAndSyncStateUnchanged() {
    val store = createStore()
    assertFailsWith<IllegalStateException> {
      runNow {
        store.transaction {
          putOutbox(listOf(pendingTitle(seq = 1, title = "A")))
          putSyncState(SyncState(clientId = "c", nextClientSeq = 2))
          error("abort")
        }
      }
    }
    assertEquals(emptyList(), runNow { store.transaction { outbox() } })
    assertEquals(SyncState(), runNow { store.transaction { syncState() } })
  }

  @Test
  fun bases_areStoredPerRecord_andForgottenWithNull() {
    val store = createStore()
    val survey = "s1"
    val entity = EntityRecord(entity_id = "e1", survey_id = survey, revision = 3)
    val submission = SubmissionRecord(submission_id = "sub1", survey_id = survey, revision = 2)
    val org = OrganizationProto(organization_id = "o1", version = 4)
    val library = OrganizationLibraryRecord(organization_id = "o1", version = 5)
    val surveyBase = SurveyBase(saved = SurveyDef(survey_id = survey, title = "Saved"))
    runNow {
      store.transaction {
        putSurveyBase(survey, surveyBase)
        putEntityBase(survey, "e1", entity)
        putSubmissionBase(survey, "sub1", submission)
        putOrganizationBase("o1", org)
        putLibraryBase("o1", library)
      }
    }
    runNow {
      store.transaction {
        assertEquals(surveyBase, surveyBase(survey))
        assertEquals(entity, entityBase(survey, "e1"))
        assertEquals(mapOf("sub1" to submission), submissionBases(survey))
        assertEquals(org, organizationBase("o1"))
        assertEquals(library, libraryBase("o1"))
        putEntityBase(survey, "e1", null)
        putOrganizationBase("o1", null)
      }
    }
    runNow {
      store.transaction {
        assertNull(entityBase(survey, "e1"))
        assertNull(organizationBase("o1"))
        assertEquals(library, libraryBase("o1"))
      }
    }
  }

  @Test
  fun deleteSurvey_forgetsItsBases_butKeepsTheOutbox() {
    val store = createStore()
    val pending = pendingTitle(seq = 1, title = "A")
    runNow {
      store.transaction {
        putSurveyBase("s1", SurveyBase(saved = SurveyDef(survey_id = "s1")))
        putEntityBase("s1", "e1", EntityRecord(entity_id = "e1"))
        putOutbox(listOf(pending))
        deleteSurvey("s1")
      }
    }
    runNow {
      store.transaction {
        assertNull(surveyBase("s1"))
        assertEquals(emptyMap(), entityBases("s1"))
        assertEquals(listOf(pending), outbox())
      }
    }
  }

  @Test
  fun syncState_isStored_andClearedWithTheStore() {
    val store = createStore()
    val syncTokens = SyncTokens(surveys = "surveys-token", entitiesBySurvey = mapOf("s1" to "e"))
    runNow {
      store.transaction {
        updateSyncState { it.copy(clientId = "c1", nextClientSeq = 7, syncTokens = syncTokens) }
      }
    }
    assertEquals(
      SyncState(clientId = "c1", nextClientSeq = 7, syncTokens = syncTokens),
      runNow { store.transaction { syncState() } },
    )
    runNow { store.clear() }
    assertEquals(SyncState(), runNow { store.transaction { syncState() } })
  }

  private fun pendingTitle(seq: Long, title: String) =
    PendingMutation(
      mutationId = "m$seq",
      clientSeq = seq,
      target = MutationTarget.Survey("s1", workingCopy = true),
      recordKey = "survey/s1",
      payload =
        MutationPayload.Survey(
          SurveyMutation(update_field = SurveyFieldPatch(key = "title", string_value = title))
        ),
    )
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
      assertEquals(SampleEntitiesData.entitiesForSurvey(kenya).size, activeEntityCount.value)

      runNow { store.transaction { deleteEntity(kenya, entities(kenya).first().id) } }
      assertEquals(
        SampleEntitiesData.entitiesForSurvey(kenya).size - 1,
        activeEntityCount.value,
      )

      val forest = "survey-sample-plots-forest"
      runNow { store.transaction { updatePreferences { it.copy(activeSurveyId = forest) } } }
      assertEquals(
        SampleEntitiesData.entitiesForSurvey(forest).size,
        activeEntityCount.value,
      )
    } finally {
      scope.cancel()
    }
  }
}
