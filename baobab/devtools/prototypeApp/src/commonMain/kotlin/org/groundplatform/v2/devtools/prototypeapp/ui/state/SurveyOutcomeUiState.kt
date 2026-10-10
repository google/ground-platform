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
package org.groundplatform.v2.devtools.prototypeapp.ui.state

import org.groundplatform.v2.devtools.prototypeapp.domain.model.EffortComparison
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyOutcomeKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem

/**
 * The "What happened with this data?" card for a closed survey, with the answer being chosen.
 * Starts from the survey's previous answer, if any.
 */
data class SurveyOutcomeCardState(
  val surveyId: String,
  val surveyTitle: String,
  val outcomes: Set<SurveyOutcomeKind> = emptySet(),
  val effortComparison: EffortComparison? = null,
) {
  /** Whether there's something to save. */
  val canSave: Boolean
    get() = outcomes.isNotEmpty() || effortComparison != null

  companion object {
    /** A card for [survey], starting from its previous answer ([SurveyPreviewItem.outcome]). */
    fun of(survey: SurveyPreviewItem): SurveyOutcomeCardState =
      SurveyOutcomeCardState(
        surveyId = survey.id,
        surveyTitle = survey.title,
        outcomes = survey.outcome?.outcomes.orEmpty(),
        effortComparison = survey.outcome?.effortComparison,
      )
  }
}

/**
 * Screen state of the "Was this data used?" prompt on the web surveys page: which closed surveys
 * show the badge, and the card opened from one.
 */
data class SurveyOutcomePromptUiState(
  /** Closed surveys the signed-in user manages whose outcome question is due again. */
  val dueSurveyIds: Set<String> = emptySet(),
  /** The card opened from a badge, or `null`. */
  val card: SurveyOutcomeCardState? = null,
)
