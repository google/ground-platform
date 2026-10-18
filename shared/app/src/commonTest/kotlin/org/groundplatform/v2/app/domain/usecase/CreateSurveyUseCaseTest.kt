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
package org.groundplatform.v2.app.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.app.data.datasource.local.store.runNow
import org.groundplatform.v2.app.data.datasource.local.store.seededStore
import org.groundplatform.v2.app.data.repository.LocalStoreTransactionRunner
import org.groundplatform.v2.app.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.app.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.app.data.seed.SampleOrganizationsData
import org.groundplatform.v2.app.domain.model.MapThumbnailTheme
import org.groundplatform.v2.app.domain.model.SurveyLifecycleState

class CreateSurveyUseCaseTest {
  @Test
  fun newSurvey_getsANewRandomUuid_whateverTheTitle() {
    val store = seededStore()
    val useCase =
      CreateSurveyUseCase(
        SurveyRepositoryImpl(store),
        SurveyEditorRepositoryImpl(store),
        LocalStoreTransactionRunner(store),
      )

    val first = useCase.newSurvey("Plots", emptyList(), null, "maya@example.org")
    val second = useCase.newSurvey("Plots", listOf(first), null, "maya@example.org")

    assertTrue(UUID.matches(first.id), first.id)
    assertTrue(UUID.matches(second.id), second.id)
    assertTrue(first.id != second.id)
  }

  @Test
  fun newSurvey_isEmptyPersonalOrOrganizational_startsInDraft_andCyclesThumbnailThemes() {
    val store = seededStore()
    val surveyRepository = SurveyRepositoryImpl(store)
    val useCase =
      CreateSurveyUseCase(
        surveyRepository,
        SurveyEditorRepositoryImpl(store),
        LocalStoreTransactionRunner(store),
      )
    val existing = runNow { surveyRepository.getSurveys() }
    val kfs = runNow {
      OrganizationRepositoryImpl(store).getOrganizations()
    }
      .first { it.id == SampleOrganizationsData.KENYA_FOREST_SERVICE }

    val inOrg = useCase.newSurvey("Mangrove Nursery Audit", existing, kfs, "maya@example.org")
    assertTrue(UUID.matches(inOrg.id), inOrg.id)
    assertEquals(kfs.id, inOrg.organizationId)
    assertEquals(kfs.name, inOrg.location)
    assertEquals("maya@example.org", inOrg.ownerEmail)
    assertEquals(0, inOrg.entityCount)
    assertFalse(inOrg.isDownloaded)
    assertEquals(SurveyLifecycleState.DRAFT, inOrg.state)
    assertTrue(inOrg.isDraft)
    assertFalse(inOrg.isClosed)
    assertEquals(
      MapThumbnailTheme.entries[existing.size % MapThumbnailTheme.entries.size],
      inOrg.thumbnailTheme,
    )

    val personal = useCase.newSurvey("   ", existing, null, "maya@example.org")
    assertEquals(CreateSurveyUseCase.DEFAULT_TITLE, personal.title)
    assertNull(personal.organizationId)
    assertEquals("No survey area yet", personal.location)
    assertEquals(SurveyLifecycleState.DRAFT, personal.state)
  }

  @Test
  fun invoke_storesTheSurveyWithABlankOwnedDraft_andActivatesIt() {
    val store = seededStore()
    val surveyRepository = SurveyRepositoryImpl(store)
    val editorRepository = SurveyEditorRepositoryImpl(store)
    val useCase =
      CreateSurveyUseCase(surveyRepository, editorRepository, LocalStoreTransactionRunner(store))
    val existing = runNow { surveyRepository.getSurveys() }
    val kfs = runNow {
      OrganizationRepositoryImpl(store).getOrganizations()
    }
      .first { it.id == SampleOrganizationsData.KENYA_FOREST_SERVICE }
    val survey = useCase.newSurvey("Mangrove Nursery Audit", existing, kfs, "maya@example.org")

    runNow { useCase(survey, ownerName = "Maya Lin") }

    val stored = runNow { surveyRepository.getSurveys() }
    assertEquals(existing.size + 1, stored.size)
    assertEquals(survey, stored.last())
    assertEquals(survey.id, runNow { surveyRepository.getActiveSurveyId() })
    val draft = runNow { editorRepository.getDraft(survey.id) }
    assertEquals("Mangrove Nursery Audit", draft.details.title)
    assertEquals(kfs.id, draft.details.organizationId)
    assertEquals("maya@example.org", draft.sharing.ownerEmail)
    assertEquals("Maya Lin", draft.sharing.ownerProfile?.displayName)
  }

  private companion object {
    /** A version 4 UUID in canonical lowercase form. */
    val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
  }
}
