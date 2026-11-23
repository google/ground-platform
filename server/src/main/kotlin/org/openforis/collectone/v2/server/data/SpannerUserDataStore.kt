/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.server.data

import com.google.cloud.Timestamp
import com.google.cloud.spanner.DatabaseClient
import com.google.cloud.spanner.ErrorCode
import com.google.cloud.spanner.KeySet
import com.google.cloud.spanner.Mutation
import com.google.cloud.spanner.SpannerException
import com.google.cloud.spanner.Statement
import com.squareup.wire.Instant
import io.grpc.StatusRuntimeException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.core.impact.ALL_USERS_ORGANIZATION_ID
import org.openforis.collectone.v2.core.library.TemplateVersioning
import org.openforis.collectone.v2.core.version.MobileClientVersionPolicy

/**
 * [UserDataStore] backed by Cloud Spanner (the emulator in development), with the schema in
 * `src/main/resources/spanner/schema.sql` (apply it with [SpannerSchema.apply]).
 *
 * Records are stored as `PROTO` columns encoded with Wire; their `commit_timestamp` comes from the
 * row's `CommitTs` column, written with Spanner's commit timestamp. [write] runs a read-write
 * transaction (Spanner may run the block more than once if it aborts); [read] runs a strong
 * read-only transaction, whose read timestamp is the snapshot timestamp.
 *
 * Record keys are UUIDs: reads of a non-UUID ID find nothing, and writes of one fail with
 * `IllegalArgumentException` (`INVALID_ARGUMENT`).
 *
 * [seed] provides the data [reset] loads and the static places and mutation log examples, which
 * aren't stored in Spanner.
 *
 * The Spanner client blocks, so every call runs on [ioDispatcher] (by default [Dispatchers.IO]),
 * keeping it off the threads that serve RPCs.
 */
class SpannerUserDataStore(
  private val client: DatabaseClient,
  private val seed: UserDataSeed = UserDataSeed(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UserDataStore {

  override val dataEpoch: String
    get() =
      client.singleUse().executeQuery(Statement.of("SELECT DataEpoch FROM ServiceMetadata")).use {
        if (it.next()) it.getString(0) else NO_EPOCH
      }

  // A read-only transaction gets its read timestamp from its first read, hence the `SELECT 1`.
  override suspend fun watermark(): Instant =
    withContext(ioDispatcher) {
      client.singleUseReadOnlyTransaction().use { tx ->
        tx.executeQuery(Statement.of("SELECT 1")).use { it.next() }
        tx.readTimestamp.toInstant()
      }
    }

  override suspend fun <T> read(block: (UserDataReader) -> T): T =
    withContext(ioDispatcher) {
      client.readOnlyTransaction().use { tx ->
        tx.executeQuery(Statement.of("SELECT 1")).use { it.next() }
        block(SpannerUserDataReader(tx, tx.readTimestamp.toInstant(), seed))
      }
    }

  override suspend fun <T> write(block: (UserDataWriter) -> T): Committed<T> =
    withContext(ioDispatcher) { writeBlocking(block) }

  private fun <T> writeBlocking(block: (UserDataWriter) -> T): Committed<T> {
    val runner = client.readWriteTransaction()
    val value =
      try {
        runner.run { tx ->
          val writer = SpannerUserDataWriter(tx, Timestamp.now().toInstant(), seed)
          block(writer).also { writer.flush() }
        }
      } catch (e: SpannerException) {
        // The client wraps exceptions thrown by the block; rethrow them unwrapped.
        val cause = e.cause
        throw if (
          e.errorCode == ErrorCode.UNKNOWN && cause is Exception && cause !is StatusRuntimeException
        )
          cause
        else e
      }
    @Suppress("UNCHECKED_CAST")
    return Committed(value as T, runner.commitTimestamp.toInstant())
  }

  /**
   * Deletes all data and loads [seed], keeping the seed's commit timestamps (Spanner accepts past
   * timestamps in commit timestamp columns) so delta sync keeps the seed's order.
   */
  override suspend fun reset(): Unit = withContext(ioDispatcher) { resetBlocking() }

  override suspend fun clientVersionPolicy(): MobileClientVersionPolicy? =
    withContext(ioDispatcher) {
      client
        .singleUse()
        .executeQuery(
          Statement.of(
            "SELECT LatestMobileVersion, MinRequiredMobileVersion, MobileUpdateUrl " +
              "FROM ServiceMetadata WHERE Singleton = TRUE"
          )
        )
        .use { rs ->
          if (!rs.next()) return@withContext null
          val hasLatest = !rs.isNull(0)
          val hasMin = !rs.isNull(1)
          val hasUrl = !rs.isNull(2)
          if (!hasLatest && !hasMin && !hasUrl) return@withContext null
          MobileClientVersionPolicy(
            latestVersion = if (hasLatest) rs.getString(0) else "",
            minRequiredVersion = if (hasMin) rs.getString(1) else "",
            updateUrl = if (hasUrl) rs.getString(2) else "",
          )
        }
    }

  override suspend fun saveClientVersionPolicy(policy: MobileClientVersionPolicy): Unit =
    withContext(ioDispatcher) {
      val norm = policy.normalized()
      client.readWriteTransaction().run { tx ->
        val existingEpoch =
          tx
            .executeQuery(
              Statement.of("SELECT DataEpoch FROM ServiceMetadata WHERE Singleton = TRUE")
            )
            .use {
              if (it.next()) it.getString(0) else null
            }
        val builder =
          if (existingEpoch != null) {
            Mutation.newUpdateBuilder("ServiceMetadata").set("Singleton").to(true)
          } else {
            Mutation.newInsertOrUpdateBuilder("ServiceMetadata")
              .set("Singleton")
              .to(true)
              .set("DataEpoch")
              .to(UUID.randomUUID().toString())
          }
        tx.buffer(
          builder
            .set("LatestMobileVersion")
            .to(norm.latestVersion)
            .set("MinRequiredMobileVersion")
            .to(norm.minRequiredVersion)
            .set("MobileUpdateUrl")
            .to(norm.updateUrl)
            .build()
        )
      }
    }

  private fun resetBlocking() {
    client.write(
      SpannerSchema.tables.reversed().map { Mutation.delete(it, KeySet.all()) } +
        Mutation.newInsertOrUpdateBuilder("ServiceMetadata")
          .set("Singleton")
          .to(true)
          .set("DataEpoch")
          .to(UUID.randomUUID().toString())
          .build()
    )
    val mutations = mutableListOf<Mutation>()
    for (org in seed.organizations) {
      mutations += organizationRow(org, org.commit_timestamp)
      org.members.forEach { mutations += memberRow(org.organization_id, it, org.commit_timestamp) }
    }
    for (library in seed.libraries) {
      mutations += libraryRow(library, library.commit_timestamp)
      mutations += conceptRows(library)
    }
    for (surveySeed in seed.surveys) {
      val survey = surveySeed.survey ?: continue
      mutations += surveyRow(survey, survey.commit_timestamp, surveySeed.acl)
      if (survey.saved_revision_number > 0L) {
        mutations += revisionRow(survey, survey.commit_timestamp)
      }
      surveySeed.acl?.let { acl ->
        acl.entries.forEach { mutations += aclEntryRow(survey.survey_id, it, acl.commit_timestamp) }
      }
      surveySeed.working_copy?.let {
        mutations += workingCopyRow(survey.survey_id, it, it.commit_timestamp)
      }
      surveySeed.entities.forEach { mutations += entityRow(it, it.commit_timestamp) }
      surveySeed.submissions.forEach { mutations += submissionRow(it, it.commit_timestamp) }
    }
    mutations.chunked(SEED_BATCH_SIZE).forEach { client.write(it) }
  }

  /**
   * Ensures the global `"All users"` library in Spanner reflects the bundled [seed]'s global
   * library without resetting user organizations or surveys.
   */
  suspend fun syncGlobalLibraryFromSeed() {
    val seedGlobal =
      seed.libraries.firstOrNull {
        it.organization_id == ALL_USERS_ORGANIZATION_ID
      } ?: return
    val existing = read { it.library(ALL_USERS_ORGANIZATION_ID) }
    // Templates whose content changed in the seed get the next version.
    val bundle = seedGlobal.bundle?.let { TemplateVersioning.withVersions(existing?.bundle, it) }
    if (existing?.bundle != bundle) {
      write { writer ->
        writer.putLibrary(
          seedGlobal.copy(
            bundle = bundle,
            settings = existing?.settings ?: seedGlobal.settings,
            version = (existing?.version ?: 0L) + 1L,
          )
        )
      }
    }
  }

  /**
   * Inserts any sample surveys from [seed] that have never been stored in Spanner (and updates
   * `form_launch_configs` on unedited `record_version == 1` sample surveys when seed availability
   * flags change) without touching user-edited or trashed surveys.
   */
  suspend fun syncMissingSurveysFromSeed() {
    val missingSeeds = read { reader ->
      seed.surveys.filter { surveySeed ->
        val surveyId = surveySeed.survey?.survey_id
        !surveyId.isNullOrBlank() && reader.survey(surveyId) == null
      }
    }
    for (surveySeed in missingSeeds) {
      val survey = surveySeed.survey ?: continue
      val surveyId = survey.survey_id
      write { writer ->
        writer.putSurvey(survey)
        surveySeed.acl?.let(writer::putAcl)
      }
      if (
        surveySeed.working_copy != null ||
          surveySeed.entities.isNotEmpty() ||
          surveySeed.submissions.isNotEmpty()
      ) {
        write { writer ->
          surveySeed.working_copy?.let { writer.putWorkingCopy(surveyId, it) }
          surveySeed.entities.forEach(writer::putEntity)
          surveySeed.submissions.forEach(writer::putSubmission)
        }
      }
    }

    // Sync launch config availability flags (e.g. open_in_google_earth) on unedited v1 seed rows.
    val staleLaunchSurveyIds = read { reader ->
      seed.surveys.mapNotNull { surveySeed ->
        val seedSurvey = surveySeed.survey ?: return@mapNotNull null
        val surveyId = seedSurvey.survey_id
        val saved = reader.survey(surveyId) ?: return@mapNotNull null
        val working = reader.workingCopy(surveyId)
        if (
          saved.record_version == 1L &&
            saved.trash_time == null &&
            working == null &&
            saved.form_launch_configs != seedSurvey.form_launch_configs
        ) {
          surveyId to seedSurvey.form_launch_configs
        } else {
          null
        }
      }
    }
    for ((surveyId, launchConfigs) in staleLaunchSurveyIds) {
      write { writer ->
        val saved = writer.survey(surveyId) ?: return@write
        val working = writer.workingCopy(surveyId)
        if (
          saved.record_version == 1L &&
            saved.trash_time == null &&
            working == null &&
            saved.form_launch_configs != launchConfigs
        ) {
          val nextVer = saved.record_version + 1L
          writer.putSurvey(
            saved.copy(form_launch_configs = launchConfigs, record_version = nextVer)
          )
          writer.acl(surveyId)?.let { writer.putAcl(it.copy(record_version = nextVer)) }
        }
      }
    }
  }

  companion object {
    /** [dataEpoch] before [reset] first wrote one. */
    const val NO_EPOCH = "unset"

    private const val SEED_BATCH_SIZE = 200
  }
}
