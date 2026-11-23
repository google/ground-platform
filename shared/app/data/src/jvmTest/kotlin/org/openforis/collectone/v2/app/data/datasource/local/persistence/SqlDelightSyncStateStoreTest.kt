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
package org.openforis.collectone.v2.app.data.datasource.local.persistence

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.survey.Organization as OrganizationProto
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.userdata.ClientSyncState
import openforis.collectone.v1.userdata.PendingMutationRecord
import openforis.collectone.v1.userdata.SurveyBaseRecord
import openforis.collectone.v1.userdata.SyncTokensRecord
import org.openforis.collectone.v2.app.client.storage.JdbcSqlDriverFactory
import org.openforis.collectone.v2.app.data.datasource.local.store.SyncState
import org.openforis.collectone.v2.app.data.datasource.local.store.SyncTokens

/** The sync state store over a real SQLite database (JDBC). */
class SqlDelightSyncStateStoreTest {
  private val databaseFile = Files.createTempFile("collect-one-sync-state", ".db").toFile()
  private val store = SqlDelightSyncStateStore(JdbcSqlDriverFactory(databaseFile))

  @AfterTest
  fun deleteDatabase() {
    runBlocking { store.close() }
    databaseFile.delete()
  }

  @Test
  fun newDatabase_loadsAnEmptyState() {
    assertEquals(ClientSyncState(), runBlocking { store.load() })
  }

  @Test
  fun replacedState_isLoadedBack() {
    runBlocking { store.replace(fullState) }

    assertEquals(fullState, runBlocking { store.load() })
  }

  @Test
  fun storedState_survivesClosingAndReopeningTheDatabase() {
    runBlocking {
      store.replace(fullState)
      store.close()
    }

    val reopened = SqlDelightSyncStateStore(JdbcSqlDriverFactory(databaseFile))

    assertEquals(fullState, runBlocking { reopened.load().also { reopened.close() } })
  }

  @Test
  fun replacedState_keepsItsAccount_untilTheStoreIsCleared() {
    runBlocking { store.replace(fullState, account = "collector@example.org") }
    assertEquals("collector@example.org", runBlocking { store.account() })

    runBlocking { store.apply(SyncStateChange(clearAll = true)) }

    assertEquals("", runBlocking { store.account() })
  }

  @Test
  fun change_upsertsAndForgetsOnlyTheRowsItNames() {
    runBlocking { store.replace(fullState) }

    runBlocking {
      store.apply(
        SyncStateChange(
          upsertedMutations = listOf(mutation("m3", seq = 3)),
          deletedMutationIds = setOf("m1"),
          entityBases =
            mapOf(
              SurveyRecordKey("s1", "e1") to entity("s1", "e1", label = "Updated"),
              SurveyRecordKey("s1", "e2") to null,
            ),
          organizationBases = mapOf("o1" to null),
        )
      )
    }

    assertEquals(
      fullState.copy(
        outbox = listOf(mutation("m2", seq = 2), mutation("m3", seq = 3)),
        entity_bases = listOf(entity("s1", "e1", label = "Updated")),
        organization_bases = emptyList(),
      ),
      runBlocking { store.load() },
    )
  }

  @Test
  fun change_deletedSurvey_forgetsAllItsBases() {
    runBlocking { store.replace(fullState) }

    runBlocking { store.apply(SyncStateChange(deletedSurveyIds = setOf("s1"))) }

    assertEquals(
      fullState.copy(
        survey_bases = emptyList(),
        entity_bases = emptyList(),
        submission_bases = emptyList(),
      ),
      runBlocking { store.load() },
    )
  }

  @Test
  fun change_clearAll_forgetsEverythingBeforeApplyingTheRest() {
    runBlocking { store.replace(fullState) }

    runBlocking {
      store.apply(
        SyncStateChange(clearAll = true, upsertedMutations = listOf(mutation("m9", seq = 9)))
      )
    }

    assertEquals(
      ClientSyncState(outbox = listOf(mutation("m9", seq = 9))),
      runBlocking { store.load() },
    )
  }

  @Test
  fun change_syncState_replacesIdentityTokensAndDownloads() {
    runBlocking { store.replace(fullState) }

    runBlocking {
      store.apply(
        SyncStateChange(
          syncState =
            SyncState(
              clientId = "client-2",
              nextClientSeq = 42,
              syncTokens = SyncTokens(surveys = "surveys-2"),
              downloadedSurveyIds = setOf("s2"),
            )
        )
      )
    }

    val loaded = runBlocking { store.load() }
    assertEquals("client-2", loaded.client_id)
    assertEquals(42L, loaded.next_client_seq)
    assertEquals(SyncTokensRecord(surveys = "surveys-2"), loaded.sync_tokens)
    assertEquals(listOf("s2"), loaded.downloaded_survey_ids)
  }

  private val fullState =
    ClientSyncState(
      outbox = listOf(mutation("m1", seq = 1), mutation("m2", seq = 2)),
      survey_bases =
        listOf(
          SurveyBaseRecord(survey_id = "s1", saved = SurveyDef(survey_id = "s1", title = "Saved"))
        ),
      entity_bases =
        listOf(entity("s1", "e1", label = "Server"), entity("s1", "e2", label = "Other")),
      submission_bases = listOf(SubmissionRecord(survey_id = "s1", submission_id = "sub1")),
      organization_bases = listOf(OrganizationProto(organization_id = "o1", name = "Org")),
      library_bases = listOf(OrganizationLibraryRecord(organization_id = "o1")),
      client_id = "client-1",
      next_client_seq = 3,
      sync_tokens =
        SyncTokensRecord(surveys = "surveys-token", entities_by_survey = mapOf("s1" to "e-token")),
      downloaded_survey_ids = listOf("s1"),
    )

  private fun mutation(id: String, seq: Long) =
    PendingMutationRecord(
      mutation_id = id,
      client_seq = seq,
      record_key = "survey/s1",
      scope_id = "s1",
      survey_mutation = SurveyMutation(),
    )

  private fun entity(surveyId: String, entityId: String, label: String) =
    EntityRecord(survey_id = surveyId, entity_id = entityId, label = label)
}
