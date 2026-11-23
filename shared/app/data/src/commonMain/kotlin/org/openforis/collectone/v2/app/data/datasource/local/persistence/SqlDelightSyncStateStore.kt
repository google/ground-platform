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

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.survey.Organization as OrganizationProto
import openforis.collectone.v1.userdata.ClientSyncState
import openforis.collectone.v1.userdata.PendingMutationRecord
import openforis.collectone.v1.userdata.SurveyBaseRecord
import openforis.collectone.v1.userdata.SyncTokensRecord
import org.openforis.collectone.v2.app.client.storage.SqlDriverFactory
import org.openforis.collectone.v2.app.data.datasource.local.db.LocalDatabase

/**
 * [SyncStateStore] in the local SQLite database (`LocalDatabase`, schema in
 * `src/commonMain/sqldelight/`). One row per outbox mutation and per server base, so a change
 * writes only the rows it touches. The database is opened, and its schema created or migrated, on
 * first use of [connection].
 */
class SqlDelightSyncStateStore(private val connection: LocalDatabaseConnection) : SyncStateStore {
  /** A store with its own connection to the database [driverFactory] opens. */
  constructor(driverFactory: SqlDriverFactory) : this(LocalDatabaseConnection(driverFactory))

  override suspend fun load(): ClientSyncState = withDatabase { db ->
    val state = db.syncStateQueries.select().awaitAsOneOrNull()
    ClientSyncState(
      outbox =
        db.outboxQueries.selectAll().awaitAsList().map(PendingMutationRecord.ADAPTER::decode),
      survey_bases =
        db.serverBaseQueries
          .selectSurveyBases()
          .awaitAsList()
          .map(SurveyBaseRecord.ADAPTER::decode),
      entity_bases =
        db.serverBaseQueries.selectEntityBases().awaitAsList().map(EntityRecord.ADAPTER::decode),
      submission_bases =
        db.serverBaseQueries
          .selectSubmissionBases()
          .awaitAsList()
          .map(SubmissionRecord.ADAPTER::decode),
      organization_bases =
        db.serverBaseQueries
          .selectOrganizationBases()
          .awaitAsList()
          .map(OrganizationProto.ADAPTER::decode),
      library_bases =
        db.serverBaseQueries
          .selectLibraryBases()
          .awaitAsList()
          .map(OrganizationLibraryRecord.ADAPTER::decode),
      client_id = state?.client_id.orEmpty(),
      next_client_seq = state?.next_client_seq ?: 0L,
      sync_tokens = state?.sync_tokens?.let(SyncTokensRecord.ADAPTER::decode),
      downloaded_survey_ids = db.syncStateQueries.selectDownloadedSurveyIds().awaitAsList(),
    )
  }

  override suspend fun account(): String = withDatabase { db ->
    db.syncStateQueries.selectAccount().awaitAsOneOrNull().orEmpty()
  }

  override suspend fun replace(state: ClientSyncState, account: String) = withDatabase { db ->
    db.transaction {
      deleteAll(db)
      db.syncStateQueries.putAccount(account)
      state.outbox.forEach { putMutation(db, it) }
      state.survey_bases.forEach {
        db.serverBaseQueries.upsertSurveyBase(it.survey_id, SurveyBaseRecord.ADAPTER.encode(it))
      }
      state.entity_bases.forEach {
        putEntityBase(db, SurveyRecordKey(it.survey_id, it.entity_id), it)
      }
      state.submission_bases.forEach {
        putSubmissionBase(db, SurveyRecordKey(it.survey_id, it.submission_id), it)
      }
      state.organization_bases.forEach { putOrganizationBase(db, it.organization_id, it) }
      state.library_bases.forEach { putLibraryBase(db, it.organization_id, it) }
      putSyncState(
        db,
        clientId = state.client_id,
        nextClientSeq = state.next_client_seq,
        syncTokens = state.sync_tokens ?: SyncTokensRecord(),
        downloadedSurveyIds = state.downloaded_survey_ids,
      )
    }
  }

  override suspend fun apply(change: SyncStateChange) {
    withDatabase { db ->
      db.transaction {
        if (change.clearAll) deleteAll(db)
        change.deletedSurveyIds.forEach { surveyId ->
          db.serverBaseQueries.deleteSurveyBase(surveyId)
          db.serverBaseQueries.deleteEntityBasesOfSurvey(surveyId)
          db.serverBaseQueries.deleteSubmissionBasesOfSurvey(surveyId)
        }
        change.deletedMutationIds.forEach { db.outboxQueries.delete(it) }
        change.upsertedMutations.forEach { putMutation(db, it) }
        change.surveyBases.forEach { (surveyId, record) ->
          if (record == null) {
            db.serverBaseQueries.deleteSurveyBase(surveyId)
          } else {
            db.serverBaseQueries.upsertSurveyBase(surveyId, SurveyBaseRecord.ADAPTER.encode(record))
          }
        }
        change.entityBases.forEach { (key, record) -> putEntityBase(db, key, record) }
        change.submissionBases.forEach { (key, record) -> putSubmissionBase(db, key, record) }
        change.organizationBases.forEach { (id, record) -> putOrganizationBase(db, id, record) }
        change.libraryBases.forEach { (id, record) -> putLibraryBase(db, id, record) }
        change.syncState?.let { state ->
          putSyncState(
            db,
            clientId = state.clientId,
            nextClientSeq = state.nextClientSeq,
            syncTokens = ClientSyncStateCodec.toRecord(state.syncTokens),
            downloadedSurveyIds = state.downloadedSurveyIds,
          )
        }
      }
    }
  }

  /** Closes the database; the next call opens it again. */
  suspend fun close() = connection.close()

  private suspend fun <R> withDatabase(block: suspend (LocalDatabase) -> R): R =
    connection.withDatabase(block)

  private suspend fun deleteAll(db: LocalDatabase) {
    db.outboxQueries.deleteAll()
    // Grouped statements return a lazy result; await runs them.
    db.serverBaseQueries.deleteAll().await()
    db.syncStateQueries.deleteAll().await()
  }

  private suspend fun putMutation(db: LocalDatabase, record: PendingMutationRecord) {
    db.outboxQueries.upsert(
      record.mutation_id,
      record.client_seq,
      PendingMutationRecord.ADAPTER.encode(record),
    )
  }

  private suspend fun putEntityBase(
    db: LocalDatabase,
    key: SurveyRecordKey,
    record: EntityRecord?,
  ) {
    if (record == null) {
      db.serverBaseQueries.deleteEntityBase(key.surveyId, key.recordId)
    } else {
      db.serverBaseQueries.upsertEntityBase(
        key.surveyId,
        key.recordId,
        EntityRecord.ADAPTER.encode(record),
      )
    }
  }

  private suspend fun putSubmissionBase(
    db: LocalDatabase,
    key: SurveyRecordKey,
    record: SubmissionRecord?,
  ) {
    if (record == null) {
      db.serverBaseQueries.deleteSubmissionBase(key.surveyId, key.recordId)
    } else {
      db.serverBaseQueries.upsertSubmissionBase(
        key.surveyId,
        key.recordId,
        SubmissionRecord.ADAPTER.encode(record),
      )
    }
  }

  private suspend fun putOrganizationBase(
    db: LocalDatabase,
    organizationId: String,
    record: OrganizationProto?,
  ) {
    if (record == null) {
      db.serverBaseQueries.deleteOrganizationBase(organizationId)
    } else {
      db.serverBaseQueries.upsertOrganizationBase(
        organizationId,
        OrganizationProto.ADAPTER.encode(record),
      )
    }
  }

  private suspend fun putLibraryBase(
    db: LocalDatabase,
    organizationId: String,
    record: OrganizationLibraryRecord?,
  ) {
    if (record == null) {
      db.serverBaseQueries.deleteLibraryBase(organizationId)
    } else {
      db.serverBaseQueries.upsertLibraryBase(
        organizationId,
        OrganizationLibraryRecord.ADAPTER.encode(record),
      )
    }
  }

  private suspend fun putSyncState(
    db: LocalDatabase,
    clientId: String,
    nextClientSeq: Long,
    syncTokens: SyncTokensRecord,
    downloadedSurveyIds: Collection<String>,
  ) {
    db.syncStateQueries.put(clientId, nextClientSeq, SyncTokensRecord.ADAPTER.encode(syncTokens))
    db.syncStateQueries.deleteDownloadedSurveys()
    downloadedSurveyIds.forEach { db.syncStateQueries.insertDownloadedSurvey(it) }
  }
}
