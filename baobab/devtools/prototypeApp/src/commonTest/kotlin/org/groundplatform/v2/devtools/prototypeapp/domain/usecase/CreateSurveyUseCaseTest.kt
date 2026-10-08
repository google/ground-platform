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
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LocalStoreTransactionRunner
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapThumbnailTheme

class CreateSurveyUseCaseTest {
  @Test
  fun uniqueSurveyId_slugifiesTheTitle_andAddsANumericSuffixWhenTaken() {
    assertEquals(
      "survey-mangrove-nursery-audit",
      CreateSurveyUseCase.uniqueSurveyId("Mangrove Nursery Audit", emptySet()),
    )
    assertEquals("survey-r-d-2026", CreateSurveyUseCase.uniqueSurveyId("  R&D (2026) ", emptySet()))
    assertEquals("survey-survey", CreateSurveyUseCase.uniqueSurveyId("!!!", emptySet()))
    assertEquals(
      "survey-plots-3",
      CreateSurveyUseCase.uniqueSurveyId("Plots", setOf("survey-plots", "survey-plots-2")),
    )
  }

  @Test
  fun newSurvey_isEmptyPersonalOrOrganizational_andCyclesThumbnailThemes() {
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
      .first { it.id == PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE }

    val inOrg = useCase.newSurvey("Mangrove Nursery Audit", existing, kfs, "maya@example.org")
    assertEquals("survey-mangrove-nursery-audit", inOrg.id)
    assertEquals(kfs.id, inOrg.organizationId)
    assertEquals(kfs.name, inOrg.location)
    assertEquals("maya@example.org", inOrg.ownerEmail)
    assertEquals(0, inOrg.entityCount)
    assertFalse(inOrg.isDownloaded)
    assertEquals(
      MapThumbnailTheme.entries[existing.size % MapThumbnailTheme.entries.size],
      inOrg.thumbnailTheme,
    )

    val personal = useCase.newSurvey("   ", existing, null, "maya@example.org")
    assertEquals(CreateSurveyUseCase.DEFAULT_TITLE, personal.title)
    assertNull(personal.organizationId)
    assertEquals("No survey area yet", personal.location)
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
      .first { it.id == PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE }
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
}
