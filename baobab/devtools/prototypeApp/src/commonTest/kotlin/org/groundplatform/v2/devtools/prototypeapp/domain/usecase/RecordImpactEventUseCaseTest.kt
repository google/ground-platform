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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.InMemoryLocalStore
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.AuthRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.ImpactEventRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactCoverage
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEventType
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.ConnectivityRepository

class RecordImpactEventUseCaseTest {
  private class Fixture(val store: InMemoryLocalStore = seededStore()) {
    val connectivity = ConnectivityRepository()
    val auth = AuthRepositoryImpl()
    val surveys = SurveyRepositoryImpl(store)
    val drafts = SurveyEditorRepositoryImpl(store)
    val events = ImpactEventRepositoryImpl(store)
    private var nextId = 0
    val record =
      RecordImpactEventUseCase(
        impactEventRepository = events,
        surveyRepository = surveys,
        surveyEditorRepository = drafts,
        authRepository = auth,
        connectivityRepository = connectivity,
        now = { "2026-10-01T09:00:00Z" },
        newId = { "impact-${++nextId}" },
      )
  }

  @Test
  fun fillsTheEventFromTheSurveyItsPurposesAndTheSignedInUser() {
    val f = Fixture()
    val survey = runNow { f.surveys.getSurveys() }.first { it.organizationId != null }
    val purposeIds = runNow { f.drafts.getDraft(survey.id) }.details.purposeIds

    val event = runNow {
      f.record(
        ImpactEventType.EXPORT,
        survey.id,
        coverage = ImpactCoverage(featureCount = 3, areaHa = 4.5),
        exportProfileId = "profile-eudr",
      )
    }

    assertEquals("impact-1", event.id)
    assertEquals(ImpactEventType.EXPORT, event.type)
    assertEquals(survey.id, event.surveyId)
    assertEquals(survey.organizationId, event.organizationId)
    assertEquals(purposeIds, event.purposeIds)
    assertEquals("profile-eudr", event.exportProfileId)
    assertEquals(3, event.featureCount)
    assertEquals(4.5, event.areaHa)
    assertEquals("2026-10-01T09:00:00Z", event.occurredAt)
    assertEquals(runNow { f.auth.getSession() }.profile.email, event.actorUserId)
    assertEquals(listOf(event), runNow { f.events.getEvents() })
  }

  @Test
  fun kenyaCoffeeReceipt_carriesItsOrganizationAndPurposePack() {
    val f = Fixture()
    val event = runNow { f.record(ImpactEventType.RECEIPT_GENERATED, "survey-kenya-coffee") }
    assertTrue(event.organizationId != null)
    assertEquals(listOf("eudr_due_diligence"), event.purposeIds)
    assertEquals(0, event.featureCount)
    assertEquals(0.0, event.areaHa)
    assertNull(event.exportProfileId)
  }

  @Test
  fun personalOrUnknownSurvey_hasNoOrganization() {
    val f = Fixture()
    val event = runNow { f.record(ImpactEventType.RECEIPT_GENERATED, "survey-unknown") }
    assertNull(event.organizationId)
    assertEquals("survey-unknown", event.surveyId)
  }

  @Test
  fun online_marksTheEventUploaded_offline_queuesIt() {
    val f = Fixture()
    val online = runNow { f.record(ImpactEventType.RECEIPT_SHARED, "survey-kenya-coffee") }
    assertTrue(online.isUploaded)

    f.connectivity.setOnline(false)
    val offline = runNow { f.record(ImpactEventType.RECEIPT_SHARED, "survey-kenya-coffee") }
    assertFalse(offline.isUploaded)
    assertEquals(
      listOf(true, false),
      runNow { f.events.getEvents() }.map { it.isUploaded },
    )
  }
}
