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
package org.groundplatform.v2.server.userdata

import groundplatform.v2.data.EntityRecord
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.SurveyDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavior every [UserDataStore] must have (see its KDoc). Subclasses provide an empty store; the
 * Spanner store will reuse these tests against the emulator.
 */
abstract class UserDataStoreContractTest {
  abstract fun newStore(): UserDataStore

  private val store by lazy { newStore() }

  @Test
  fun write_stampsEveryRecordWithOneCommitTimestamp_afterTheWatermark() {
    val before = store.watermark()

    val committed = store.write { tx ->
      tx.putOrganization(Organization(organization_id = ORG, name = "Org"))
      tx.putSurvey(SurveyDef(survey_id = SURVEY, title = "Survey"))
    }

    assertTrue(committed.commitTimestamp > before)
    assertEquals(committed.commitTimestamp, store.watermark())
    store.read { r ->
      assertEquals(committed.commitTimestamp, r.organization(ORG)?.commit_timestamp)
      assertEquals(committed.commitTimestamp, r.survey(SURVEY)?.commit_timestamp)
    }
  }

  @Test
  fun snapshots_includeEverythingCommittedBeforeThem() {
    val committed = store.write { it.putSurvey(SurveyDef(survey_id = SURVEY)) }

    store.read { r ->
      assertTrue(r.snapshotTimestamp >= committed.commitTimestamp)
      assertEquals(committed.commitTimestamp, r.survey(SURVEY)?.commit_timestamp)
    }
    val later = store.write { it.putSurvey(SurveyDef(survey_id = SURVEY, title = "Later")) }
    store.read { r -> assertTrue(later.commitTimestamp <= r.snapshotTimestamp) }
  }

  @Test
  fun laterWrites_commitStrictlyLater() {
    val first = store.write { it.putSurvey(SurveyDef(survey_id = SURVEY)) }
    val second = store.write { it.putSurvey(SurveyDef(survey_id = SURVEY, title = "Again")) }

    assertTrue(second.commitTimestamp > first.commitTimestamp)
  }

  @Test
  fun write_thatThrows_commitsNothing() {
    val before = store.watermark()

    assertFailsWith<IllegalStateException> {
      store.write { tx ->
        tx.putSurvey(SurveyDef(survey_id = SURVEY))
        error("validation failed")
      }
    }

    assertNull(store.read { it.survey(SURVEY) })
    assertEquals(before, store.watermark())
  }

  @Test
  fun readsInsideAWrite_seeDataAsOfTheTransactionStart() {
    store.write { it.putSurvey(SurveyDef(survey_id = SURVEY, title = "Old")) }

    val seen = store.write { tx ->
      tx.putSurvey(SurveyDef(survey_id = SURVEY, title = "New"))
      tx.survey(SURVEY)?.title
    }

    assertEquals("Old", seen.value)
    assertEquals("New", store.read { it.survey(SURVEY)?.title })
  }

  @Test
  fun workingCopiesAndPlotLocks_canBeRemoved() {
    store.write { tx ->
      tx.putWorkingCopy(SURVEY, SurveyDef(survey_id = SURVEY, title = "Draft"))
      tx.putPlotLock(SURVEY, ENTITY, groundplatform.v2.data.PlotLock(entity_id = ENTITY))
    }
    store.write { tx ->
      tx.putWorkingCopy(SURVEY, null)
      tx.putPlotLock(SURVEY, ENTITY, null)
    }

    store.read { r ->
      assertNull(r.workingCopy(SURVEY))
      assertNull(r.plotLock(SURVEY, ENTITY))
    }
  }

  @Test
  fun entities_arePerSurvey() {
    store.write { tx ->
      tx.putEntity(EntityRecord(survey_id = SURVEY, entity_id = ENTITY, label = "A"))
      tx.putEntity(EntityRecord(survey_id = OTHER_SURVEY, entity_id = ENTITY, label = "B"))
    }

    store.read { r ->
      assertEquals("A", r.entity(SURVEY, ENTITY)?.label)
      assertEquals(listOf("B"), r.entities(OTHER_SURVEY).map { it.label })
    }
  }

  @Test
  fun recordApplied_tracksMutationIds_andOnlyRaisesWatermarks() {
    store.write { it.recordApplied(SCOPE, listOf("m-1"), CLIENT, clientSeq = 5L) }
    store.write { it.recordApplied(SCOPE, listOf("m-2"), CLIENT, clientSeq = 3L) }

    store.read { r ->
      assertTrue(r.isMutationApplied("m-1") && r.isMutationApplied("m-2"))
      val watermark = r.appliedWatermarks(CLIENT).single()
      assertEquals(SCOPE, watermark.scope)
      assertEquals(5L, watermark.client_seq)
      assertTrue(r.appliedWatermarks("someone-else").isEmpty())
    }
  }

  @Test
  fun reset_startsANewDataEpoch() {
    val epoch = store.dataEpoch
    store.write { it.putSurvey(SurveyDef(survey_id = SURVEY)) }

    store.reset()

    assertNotEquals(epoch, store.dataEpoch)
    assertNull(store.read { it.survey(SURVEY) })
  }

  companion object {
    const val ORG = "00000000-0000-4000-8000-0000000000a1"
    const val SURVEY = "00000000-0000-4000-8000-0000000000b1"
    const val OTHER_SURVEY = "00000000-0000-4000-8000-0000000000b2"
    const val ENTITY = "00000000-0000-4000-8000-0000000000c1"
    const val CLIENT = "00000000-0000-4000-8000-0000000000d1"
    const val SCOPE = "surveys/$SURVEY/data"
  }
}

class InMemoryUserDataStoreTest : UserDataStoreContractTest() {
  override fun newStore(): UserDataStore = InMemoryUserDataStore()
}
