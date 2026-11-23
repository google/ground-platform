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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.domain.error.getOrElse
import org.openforis.collectone.v2.app.domain.error.map
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAccess
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyDirectoryRepository

/**
 * The Download survey screen's look-up of surveys the user's survey list doesn't include: the
 * survey a pasted share link (or survey ID) points to, or public surveys matching the query.
 *
 * Each [search] replaces the previous one and waits [delayMillis] for typing to pause before asking
 * the user data service, so only the last query of a burst is looked up. Lookups need the network:
 * while offline (or without a [directory], when not connected to a user data service) nothing is
 * looked up, and a failed lookup shows no extra results rather than an error, since the survey list
 * still shows.
 *
 * Runs its lookups in the owning ViewModel's [scope], so they stop with it.
 */
internal class SurveyDirectorySearch(
  private val scope: CoroutineScope,
  private val directory: SurveyDirectoryRepository?,
  private val connectivityRepository: ConnectivityRepository,
  private val delayMillis: Long,
) {
  /** The latest lookup's [results], and whether one is in flight. */
  data class State(
    val results: List<SurveyPreviewItem> = emptyList(),
    val isSearching: Boolean = false,
  )

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()

  private var lookup: Job? = null

  /**
   * Looks up [query] once typing pauses, cancelling any lookup still waiting or in flight. Queries
   * shorter than [MIN_QUERY_LENGTH] (after trimming) clear the results instead.
   */
  fun search(query: String) {
    lookup?.cancel()
    val directory = directory
    val trimmed = query.trim()
    if (directory == null || trimmed.length < MIN_QUERY_LENGTH) {
      _state.value = State()
      return
    }
    lookup = scope.launch {
      delay(delayMillis)
      if (!connectivityRepository.isOnline()) return@launch
      _state.update { it.copy(isSearching = true) }
      val linkedId = SurveyAccess.surveyIdFromLink(trimmed)
      val found =
        if (linkedId != null) directory.lookUpSurvey(linkedId).map { listOfNotNull(it) }
        else directory.searchPublicSurveys(trimmed)
      _state.value = State(results = found.getOrElse { emptyList() })
    }
  }

  /** The survey with [surveyId] among the latest results, if any. */
  fun result(surveyId: String): SurveyPreviewItem? =
    _state.value.results.firstOrNull { it.id == surveyId }

  /**
   * Forgets the results (the workbench's Reset). A lookup still in flight isn't cancelled, so its
   * results can still arrive, as when the results lived in the ViewModel's session state.
   */
  fun clearResults() {
    _state.value = State()
  }

  companion object {
    /** Shortest query that looks up public surveys. */
    const val MIN_QUERY_LENGTH = 2
  }
}
