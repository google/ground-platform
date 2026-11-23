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

import com.google.cloud.spanner.SpannerException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.userdata.UserDataSeed
import org.junit.jupiter.api.Tag
import org.openforis.collectone.v2.server.app.loadSeed
import org.openforis.collectone.v2.server.service.AccessEpochTest
import org.openforis.collectone.v2.server.service.AuthorizationTest
import org.openforis.collectone.v2.server.service.ConcurrentMutationTest
import org.openforis.collectone.v2.server.service.DataServiceImplTest
import org.openforis.collectone.v2.server.service.LibraryServiceImplTest
import org.openforis.collectone.v2.server.service.MutationIdempotencyTest
import org.openforis.collectone.v2.server.service.OrganizationServiceImplTest
import org.openforis.collectone.v2.server.service.RecordVersionTest
import org.openforis.collectone.v2.server.service.SPANNER_TAG
import org.openforis.collectone.v2.server.service.ServerOwnedFieldsTest
import org.openforis.collectone.v2.server.service.SurveyLifecycleTest
import org.openforis.collectone.v2.server.service.SurveyServiceImplTest
import org.openforis.collectone.v2.server.service.SurveyTrashAndSharingTest
import org.openforis.collectone.v2.server.service.UserDataBackendTest
import org.openforis.collectone.v2.server.service.sampleBackend

/** Runs the [UserDataStoreContractTest] against [SpannerUserDataStore] on the Spanner emulator. */
@Tag(SPANNER_TAG)
class SpannerUserDataStoreTest : UserDataStoreContractTest() {
  override fun newStore(): UserDataStore = SpannerTestDatabase.store()

  @Test
  fun nonUuidRecordIds_areNotFound_andCannotBeWritten() = runTest {
    val store = SpannerTestDatabase.store()

    assertNull(store.read { it.survey("not-a-uuid") })
    assertFailsWith<IllegalArgumentException> {
      store.write { it.putSurvey(SurveyDef(survey_id = "not-a-uuid")) }
    }
  }

  @Test
  fun applyingTheSchemaAgain_keepsTheData() = runTest {
    val store = SpannerTestDatabase.store()
    store.write { it.putSurvey(SurveyDef(survey_id = SURVEY, title = "Kept")) }

    SpannerTestDatabase.applySchema()

    assertEquals("Kept", store.read { it.survey(SURVEY)?.title })
  }

  @Test
  fun applyingTheSchema_addsMissingColumnsAndIndexes() = runTest {
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
  fun recordsWithoutTheirSurvey_cannotBeWritten() = runTest {
    val store = SpannerTestDatabase.store()

    assertFailsWith<SpannerException> {
      store.write { it.putEntity(EntityRecord(survey_id = SURVEY, entity_id = ENTITY)) }
    }
    assertNull(store.read { it.entity(SURVEY, ENTITY) })
  }
}

@Tag(SPANNER_TAG)
class SpannerUserDataBackendTest : UserDataBackendTest() {
  override fun newBackend(seed: UserDataSeed) = SpannerTestDatabase.backend(seed)
}

@Tag(SPANNER_TAG)
class SpannerMutationIdempotencyTest : MutationIdempotencyTest() {
  override fun newBackend() = SpannerTestDatabase.sampleBackend()
}

@Tag(SPANNER_TAG)
class SpannerConcurrentMutationTest : ConcurrentMutationTest() {
  override fun newStore() = SpannerTestDatabase.store(loadSeed(null))
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
class SpannerServerOwnedFieldsTest : ServerOwnedFieldsTest() {
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

@Tag(SPANNER_TAG)
class SpannerSurveyServiceImplTest : SurveyServiceImplTest() {
  override fun newBackend(seed: UserDataSeed) = SpannerTestDatabase.backend(seed)
}

@Tag(SPANNER_TAG)
class SpannerSurveyLifecycleTest : SurveyLifecycleTest() {
  override fun newBackend(seed: UserDataSeed) = SpannerTestDatabase.backend(seed)
}

@Tag(SPANNER_TAG)
class SpannerOrganizationServiceImplTest : OrganizationServiceImplTest() {
  override fun newBackend(seed: UserDataSeed) = SpannerTestDatabase.backend(seed)
}

@Tag(SPANNER_TAG)
class SpannerLibraryServiceImplTest : LibraryServiceImplTest() {
  override fun newBackend(seed: UserDataSeed) = SpannerTestDatabase.backend(seed)
}

@Tag(SPANNER_TAG)
class SpannerDataServiceImplTest : DataServiceImplTest() {
  override fun newBackend(seed: UserDataSeed) = SpannerTestDatabase.backend(seed)
}
