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
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EffortComparison
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyOutcomeKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyOutcomeSelection
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SurveyLifecycleUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyOutcomeCardState

/** User intents of the "What happened with this data?" card shown for a closed survey. */
interface SurveyOutcomeActions {
  /** Turns outcome [kind] on or off; "Not yet" can't be combined with other outcomes. */
  fun toggleOutcome(kind: SurveyOutcomeKind)

  /** Sets (or clears, with `null`) how the time and cost compared with the previous method. */
  fun setEffortComparison(comparison: EffortComparison?)

  /** Saves the answer as the survey's outcome and hides the card. */
  fun saveOutcome()

  /** Hides the card without answering. The survey stays closed either way. */
  fun skipOutcome()
}

/**
 * The open "What happened with this data?" card of a ViewModel (at most one at a time). Saving goes
 * through [SurveyLifecycleUseCase.saveOutcome] in [scope]; the card hides right away.
 */
internal class SurveyOutcomeCardHolder(
  private val surveyLifecycle: SurveyLifecycleUseCase?,
  private val scope: CoroutineScope,
) : SurveyOutcomeActions {
  private val _card = MutableStateFlow<SurveyOutcomeCardState?>(null)

  /** The open card, or `null`. */
  val card: StateFlow<SurveyOutcomeCardState?> = _card.asStateFlow()

  /** Opens the card for [survey], starting from its previous answer. */
  fun open(survey: SurveyPreviewItem) {
    _card.value = SurveyOutcomeCardState.of(survey)
  }

  /** Hides the card if it belongs to survey [surveyId] (or any card when `null`). */
  fun dismiss(surveyId: String? = null) {
    _card.update { if (surveyId == null || it?.surveyId == surveyId) null else it }
  }

  override fun toggleOutcome(kind: SurveyOutcomeKind) {
    _card.update { it?.copy(outcomes = SurveyOutcomeSelection.toggle(it.outcomes, kind)) }
  }

  override fun setEffortComparison(comparison: EffortComparison?) {
    _card.update { it?.copy(effortComparison = comparison) }
  }

  override fun saveOutcome() {
    val current = _card.value ?: return
    if (!current.canSave) return
    _card.value = null
    val lifecycle = surveyLifecycle ?: return
    scope.launch {
      lifecycle.saveOutcome(current.surveyId, current.outcomes, current.effortComparison)
    }
  }

  override fun skipOutcome() = dismiss()
}
