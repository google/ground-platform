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
package org.openforis.collectone.v2.web.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.AuthRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.domain.model.EffortComparison
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.web.domain.usecase.SurveyLifecycleUseCase
import org.openforis.collectone.v2.web.ui.surveyoutcome.SurveyOutcomeCardHolder

class IsoLanguagesAndOutcomeCardTest {

  @Test
  fun isoLanguages_findByCodeAndSearchSupportPart1Part3AndNameQueries() {
    val english = assertNotNull(IsoLanguages.findByCode("en"))
    assertEquals("eng", english.id)
    assertEquals("en", english.part1)
    assertEquals("en", english.code)
    assertEquals("English (en / eng)", english.label)
    assertEquals(english, IsoLanguages.findByCode("ENG"))
    assertNull(IsoLanguages.findByCode("zzz_nonexistent"))

    val acholi = assertNotNull(IsoLanguages.findByCode("ach"))
    assertEquals("ach", acholi.code)
    assertEquals("Acoli (ach)", acholi.label)

    val defaultList = IsoLanguages.search("", limit = 10)
    assertEquals(10, defaultList.size)

    val swahiliMatches = IsoLanguages.search("sw", limit = 15)
    assertTrue(swahiliMatches.isNotEmpty())
    assertEquals("sw", swahiliMatches.first().part1)

    val nameMatches = IsoLanguages.search("kikuyu", limit = 10)
    assertTrue(nameMatches.any { it.id == "kik" })
  }

  @Test
  fun surveyOutcomeCardHolder_managesCardSelectionDismissalAndPersistence() {
    val store = seededStore()
    val surveys = SurveyRepositoryImpl(store)
    val auth = AuthRepositoryImpl()
    runDirect { auth.signInWithGoogle() }
    val lifecycle =
      SurveyLifecycleUseCase(
        surveyRepository = surveys,
        authRepository = auth,
        recordImpactEvent = null,
        clock = Clock.System,
      )
    val holder = SurveyOutcomeCardHolder(lifecycle, CoroutineScope(Dispatchers.Unconfined))

    val survey = runDirect { surveys.getSurveys().first() }
    assertNull(holder.card.value)

    holder.open(survey)
    val initialCard = assertNotNull(holder.card.value)
    assertEquals(survey.id, initialCard.surveyId)
    assertFalse(initialCard.canSave)

    // Saving when nothing is selected is a no-op
    holder.saveOutcome()
    assertNotNull(holder.card.value)

    holder.toggleOutcome(SurveyOutcomeKind.SHARED_WITH_BUYERS)
    holder.setEffortComparison(EffortComparison.LESS)
    assertTrue(assertNotNull(holder.card.value).canSave)

    // Dismissing with a different survey ID keeps the open card
    holder.dismiss("other-survey-id")
    assertNotNull(holder.card.value)

    holder.saveOutcome()
    assertNull(holder.card.value)
    val persisted = runDirect { surveys.getSurveys().first { it.id == survey.id } }.outcome
    assertNotNull(persisted)
    assertEquals(setOf(SurveyOutcomeKind.SHARED_WITH_BUYERS), persisted.outcomes)
    assertEquals(EffortComparison.LESS, persisted.effortComparison)

    // Re-opening pre-populates previous answer; skipOutcome dismisses without overwriting
    holder.open(runDirect { surveys.getSurveys().first { it.id == survey.id } })
    assertEquals(
      setOf(SurveyOutcomeKind.SHARED_WITH_BUYERS),
      assertNotNull(holder.card.value).outcomes,
    )
    holder.skipOutcome()
    assertNull(holder.card.value)
  }
}
