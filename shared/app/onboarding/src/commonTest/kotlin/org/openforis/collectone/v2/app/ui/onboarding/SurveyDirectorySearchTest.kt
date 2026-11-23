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
package org.openforis.collectone.v2.app.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAccess
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyDirectoryRepository

class SurveyDirectorySearchTest {
  /** Records every lookup; [gate], when set, holds lookups until completed. */
  private class RecordingDirectory(private val failure: DomainError? = null) :
    SurveyDirectoryRepository {
    val searches = mutableListOf<String>()
    val lookUps = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun searchPublicSurveys(query: String): DomainResult<List<SurveyPreviewItem>> {
      searches += query
      gate?.await()
      return failure?.let { DomainResult.Failure(it) }
        ?: DomainResult.Success(listOf(preview("public-$query")))
    }

    override suspend fun lookUpSurvey(surveyId: String): DomainResult<SurveyPreviewItem?> {
      lookUps += surveyId
      return DomainResult.Success(if (surveyId == LINKED_ID) preview(surveyId) else null)
    }

    override suspend fun downloadSurvey(survey: SurveyPreviewItem) = Unit
  }

  private val connectivity = ConnectivityRepository()

  private fun CoroutineScope.search(directory: SurveyDirectoryRepository?) =
    SurveyDirectorySearch(this, directory, connectivity, delayMillis = DELAY)

  @Test
  fun onlyTheLastQueryOfABurstIsLookedUp_afterTypingPauses() = runTest {
    val directory = RecordingDirectory()
    val search = backgroundScope.search(directory)

    search.search("ma")
    advanceTimeBy(DELAY / 2)
    search.search("man")
    advanceTimeBy(DELAY / 2)
    search.search("mang ")
    runCurrent()
    assertTrue(directory.searches.isEmpty(), "nothing is looked up while typing")

    advanceTimeBy(DELAY)
    runCurrent()
    assertEquals(listOf("mang"), directory.searches, "trimmed, once")
    assertEquals(listOf("public-mang"), search.state.value.results.map { it.id })
    assertFalse(search.state.value.isSearching)
  }

  @Test
  fun isSearching_whileTheLookupIsInFlight() = runTest {
    val directory = RecordingDirectory().apply { gate = CompletableDeferred() }
    val search = backgroundScope.search(directory)

    search.search("mangroves")
    advanceTimeBy(DELAY + 1)
    assertTrue(search.state.value.isSearching)

    directory.gate?.complete(Unit)
    runCurrent()
    assertFalse(search.state.value.isSearching)
    assertEquals(1, search.state.value.results.size)
  }

  @Test
  fun aShareLink_looksUpItsSurvey_insteadOfSearching() = runTest {
    val directory = RecordingDirectory()
    val search = backgroundScope.search(directory)

    search.search(SurveyAccess.surveyUrl(LINKED_ID))
    advanceTimeBy(DELAY + 1)

    assertEquals(listOf(LINKED_ID), directory.lookUps)
    assertTrue(directory.searches.isEmpty())
    assertEquals(LINKED_ID, search.result(LINKED_ID)?.id)
    assertNull(search.result("other"))
  }

  @Test
  fun shortQueries_clearTheResults_withoutALookup() = runTest {
    val directory = RecordingDirectory()
    val search = backgroundScope.search(directory)
    search.search("mangroves")
    advanceTimeBy(DELAY + 1)
    assertEquals(1, search.state.value.results.size)

    search.search(" m ")
    advanceTimeBy(DELAY + 1)

    assertEquals(SurveyDirectorySearch.State(), search.state.value)
    assertEquals(listOf("mangroves"), directory.searches)
  }

  @Test
  fun aNewQuery_cancelsALookupStillInFlight() = runTest {
    val directory = RecordingDirectory().apply { gate = CompletableDeferred() }
    val search = backgroundScope.search(directory)
    search.search("first")
    advanceTimeBy(DELAY + 1)

    directory.gate = null
    search.search("second")
    advanceTimeBy(DELAY + 1)

    assertEquals(listOf("first", "second"), directory.searches)
    assertEquals(listOf("public-second"), search.state.value.results.map { it.id })
  }

  @Test
  fun offline_nothingIsLookedUp_andEarlierResultsStay() = runTest {
    val directory = RecordingDirectory()
    val search = backgroundScope.search(directory)
    search.search("mangroves")
    advanceTimeBy(DELAY + 1)

    connectivity.setOnline(false)
    search.search("plots")
    advanceTimeBy(DELAY + 1)

    assertEquals(listOf("mangroves"), directory.searches)
    assertEquals(listOf("public-mangroves"), search.state.value.results.map { it.id })
  }

  @Test
  fun failedLookups_andNoDirectory_showNoResults() = runTest {
    val failing = backgroundScope.search(RecordingDirectory(failure = DomainError.Network))
    failing.search("mangroves")
    advanceTimeBy(DELAY + 1)
    assertEquals(SurveyDirectorySearch.State(), failing.state.value)

    val disconnected = backgroundScope.search(directory = null)
    disconnected.search("mangroves")
    advanceTimeBy(DELAY + 1)
    assertEquals(SurveyDirectorySearch.State(), disconnected.state.value)
  }

  private companion object {
    const val DELAY = 400L
    const val LINKED_ID = "22222222-2222-4222-8222-222222222222"

    fun preview(id: String) =
      SurveyPreviewItem(
        id = id,
        title = id,
        description = "",
        location = "",
        isDownloaded = false,
        entityCount = 0,
      )
  }
}
