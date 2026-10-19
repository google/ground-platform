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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/** Runs the [UserDataStoreContractTest] against [SpannerUserDataStore] on the Spanner emulator. */
@Tag(SPANNER_TAG)
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
@Tag(SPANNER_TAG)
class SpannerUserDataBackendTest : UserDataBackendTest() {
  override fun newBackend() = SpannerTestDatabase.sampleBackend()
}

@Tag(SPANNER_TAG)
class SpannerMutationIdempotencyTest : MutationIdempotencyTest() {
  override fun newBackend() = SpannerTestDatabase.sampleBackend()
}

@Tag(SPANNER_TAG)
class SpannerRecordVersionTest : RecordVersionTest() {
  override fun newBackend() = SpannerTestDatabase.sampleBackend()
}

@Tag(SPANNER_TAG)
class SpannerAuthorizationTest : AuthorizationTest() {
  override fun newBackend() = SpannerTestDatabase.sampleBackend()
}

@Tag(SPANNER_TAG)
class SpannerAccessEpochTest : AccessEpochTest() {
  override fun newBackend() = SpannerTestDatabase.sampleBackend()
}

@Tag(SPANNER_TAG)
class SpannerSurveyTrashAndSharingTest : SurveyTrashAndSharingTest() {
  override fun newBackend() = SpannerTestDatabase.sampleBackend()
}
