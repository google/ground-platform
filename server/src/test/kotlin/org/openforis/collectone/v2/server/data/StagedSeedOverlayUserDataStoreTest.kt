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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.SubmissionMutation
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.server.app.loadSeed
import org.openforis.collectone.v2.server.service.UserDataBackend

class StagedSeedOverlayUserDataStoreTest {
  private val fullSeed: UserDataSeed = loadSeed(null)
  private val earthSurveyId = "00000037-7e4a-5b2c-9d18-3f8a61c4e902"

  @Test
  fun overlaysMissingSeedSurveysAndAcceptsSubmissionsWithoutMutatingPrimary() = runTest {
    val primarySeed =
      fullSeed.copy(surveys = fullSeed.surveys.filterNot { it.survey?.survey_id == earthSurveyId })
    var primaryWriteCalls = 0
    val basePrimary = InMemoryUserDataStore(primarySeed)
    val countingPrimary =
      object : UserDataStore by basePrimary {
        override suspend fun <T> write(block: (UserDataWriter) -> T): Committed<T> {
          primaryWriteCalls++
          return basePrimary.write(block)
        }
      }

    val store = StagedSeedOverlayUserDataStore.create(primary = countingPrimary, seed = fullSeed)
    val backend = UserDataBackend(store)

    // Missing seed survey is visible through the overlay while absent from primary.
    assertNull(basePrimary.read { it.survey(earthSurveyId) })
    val overlaid = assertNotNull(store.read { it.survey(earthSurveyId) })
    assertEquals("Peatland & Canopy Change Interpretation (Google Earth)", overlaid.title)
    assertEquals(5, store.read { it.entities(earthSurveyId, includeDeleted = false).size })

    // Balloon/mobile submission against the staged seed survey commits in memory with zero primary
    // writes.
    val kml =
      backend.exportSurveyKml(
        surveyId = earthSurveyId,
        balloonAppUrl = "https://example.org/balloon/balloon-form.html",
        serviceUrl = "https://api.example.org",
      )
    assertTrue("PLT-GE-04" in kml.content)

    backend.MutateData(
      MutateDataRequest(
        survey_id = earthSurveyId,
        mutations =
          listOf(
            DataMutation(
              client_id = "balloon-test",
              client_seq = 1L,
              submission_mutation =
                SubmissionMutation(
                  submission_id = "00000059-7e4a-5b2c-9d18-3f8a61c4e919",
                  upsert =
                    SubmissionRecord(
                      submission_id = "00000059-7e4a-5b2c-9d18-3f8a61c4e919",
                      survey_id = earthSurveyId,
                      entity_id = "00000044-7e4a-5b2c-9d18-3f8a61c4e904",
                      form_id = "form-google-earth-interpretation",
                    ),
                ),
            ),
            DataMutation(
              client_id = "balloon-test",
              client_seq = 2L,
              entity_mutation =
                EntityMutation(
                  entity_id = "00000044-7e4a-5b2c-9d18-3f8a61c4e904",
                  upsert =
                    EntityRecord(
                      entity_id = "00000044-7e4a-5b2c-9d18-3f8a61c4e904",
                      survey_id = earthSurveyId,
                      dataset_id = "interpretation_plots",
                      layer_id = "layer-interpretation-plots",
                      label = "PLT-GE-04 • West Blackwater Buffer",
                      geo_id = "PLT-GE-04",
                    ),
                ),
            ),
          ),
      )
    )

    assertEquals(0, primaryWriteCalls, "Expected zero writes to primary store on staged revision")
    assertEquals(4, store.read { it.submissions(earthSurveyId, includeDeleted = false).size })
    assertNull(basePrimary.read { it.survey(earthSurveyId) })
  }
}
