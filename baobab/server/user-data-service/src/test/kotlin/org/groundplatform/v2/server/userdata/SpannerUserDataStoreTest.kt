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

import com.google.cloud.spanner.SpannerException
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.userdata.UserDataSeed
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Runs the [UserDataStoreContractTest] against [SpannerUserDataStore] on the Spanner emulator.
 * Skipped unless `SPANNER_EMULATOR_HOST` is set (for example by running
 * `scripts/spanner-emulator.sh`).
 */
@EnabledIfEnvironmentVariable(named = "SPANNER_EMULATOR_HOST", matches = ".+")
class SpannerUserDataStoreTest : UserDataStoreContractTest() {
  override fun newStore(): UserDataStore = SpannerTestDatabase.store()

  @Test
  fun nonUuidRecordIds_areNotFound_andCannotBeWritten() {
    val store = SpannerTestDatabase.store()

    assertNull(store.read { it.survey("not-a-uuid") })
    assertFailsWith<IllegalArgumentException> {
      store.write { it.putSurvey(SurveyDef(survey_id = "not-a-uuid")) }
    }
  }

  @Test
  fun applyingTheSchemaAgain_keepsTheData() {
    val store = SpannerTestDatabase.store()
    store.write { it.putSurvey(SurveyDef(survey_id = SURVEY, title = "Kept")) }

    SpannerTestDatabase.applySchema()

    assertEquals("Kept", store.read { it.survey(SURVEY)?.title })
  }

  @Test
  fun applyingTheSchema_addsMissingColumnsAndIndexes() {
    SpannerTestDatabase.store()
    SpannerTestDatabase.updateDdl(
      "DROP SEARCH INDEX SurveysSearch",
      "ALTER TABLE Surveys DROP COLUMN SearchTokens",
    )

    SpannerTestDatabase.applySchema()

    val ddl = SpannerTestDatabase.ddl()
    assertTrue(ddl.any { it.startsWith("CREATE SEARCH INDEX SurveysSearch") })
    assertTrue(ddl.single { it.startsWith("CREATE TABLE Surveys ") }.contains("SearchTokens"))
  }

  @Test
  fun recordsWithoutTheirSurvey_cannotBeWritten() {
    val store = SpannerTestDatabase.store()

    assertFailsWith<SpannerException> {
      store.write { it.putEntity(EntityRecord(survey_id = SURVEY, entity_id = ENTITY)) }
    }
    assertNull(store.read { it.entity(SURVEY, ENTITY) })
  }
}

/** The backend tests, on a Spanner store loaded with the bundled sample seed. */
@EnabledIfEnvironmentVariable(named = "SPANNER_EMULATOR_HOST", matches = ".+")
class SpannerUserDataBackendTest : UserDataBackendTest() {
  override fun newBackend() = UserDataBackend(SpannerTestDatabase.store(loadSeed(null)))
}

@EnabledIfEnvironmentVariable(named = "SPANNER_EMULATOR_HOST", matches = ".+")
class SpannerMutationIdempotencyTest : MutationIdempotencyTest() {
  override fun newBackend() = UserDataBackend(SpannerTestDatabase.store(loadSeed(null)))
}

@EnabledIfEnvironmentVariable(named = "SPANNER_EMULATOR_HOST", matches = ".+")
class SpannerRecordVersionTest : RecordVersionTest() {
  override fun newBackend() = UserDataBackend(SpannerTestDatabase.store(loadSeed(null)))
}

@EnabledIfEnvironmentVariable(named = "SPANNER_EMULATOR_HOST", matches = ".+")
class SpannerAccessEpochTest : AccessEpochTest() {
  override fun newBackend() = UserDataBackend(SpannerTestDatabase.store(loadSeed(null)))
}

@EnabledIfEnvironmentVariable(named = "SPANNER_EMULATOR_HOST", matches = ".+")
class SpannerSurveyTrashAndSharingTest : SurveyTrashAndSharingTest() {
  override fun newBackend() = UserDataBackend(SpannerTestDatabase.store(loadSeed(null)))
}

/**
 * One emulator database with the schema applied, shared by the Spanner tests (which reset it) and
 * dropped when the test JVM exits.
 */
object SpannerTestDatabase {
  private val emulatorHost: String? = System.getenv("SPANNER_EMULATOR_HOST")

  private val connection: SpannerConnection by lazy {
    val settings =
      SpannerSettings(
        projectId = "test-project",
        instanceId = "test-instance",
        databaseId = "store-" + UUID.randomUUID().toString().take(8),
        emulatorHost = emulatorHost,
      )
    val connection = SpannerConnection.open(settings, SpannerOperationRecorder())
    SpannerSchema.apply(connection.databaseAdmin, settings)
    Runtime.getRuntime()
      .addShutdownHook(
        Thread {
          connection.databaseAdmin.dropDatabase(settings.instanceId, settings.databaseId)
          connection.close()
        }
      )
    connection
  }

  fun applySchema() = SpannerSchema.apply(connection.databaseAdmin, connection.settings)

  fun updateDdl(vararg statements: String) {
    val settings = connection.settings
    connection.databaseAdmin
      .updateDatabaseDdl(settings.instanceId, settings.databaseId, statements.toList(), null)
      .get()
  }

  fun ddl(): List<String> =
    connection.databaseAdmin.getDatabaseDdl(
      connection.settings.instanceId,
      connection.settings.databaseId,
    )

  /** A store reset to [seed]. Skips the calling test unless the emulator is configured. */
  fun store(seed: UserDataSeed = UserDataSeed()): SpannerUserDataStore {
    assumeTrue(!emulatorHost.isNullOrBlank(), "Set SPANNER_EMULATOR_HOST to run")
    return SpannerUserDataStore(connection.databaseClient, seed).also { it.reset() }
  }
}
