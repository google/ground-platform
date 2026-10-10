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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EffortComparison
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyOutcomeKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyOutcomePrompt
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.AuthRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.OrganizationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SurveyLifecycleUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.common.platformEpochMillis
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyOutcomePromptUiState

/**
 * User intents of the "Was this data used?" prompt. Implemented by [SurveyOutcomePromptViewModel].
 */
interface SurveyOutcomePromptActions : SurveyOutcomeActions {
  /** Opens the "What happened with this data?" card for survey [surveyId]. */
  fun openOutcomeCard(surveyId: String)
}

/**
 * ViewModel of the gentle "Was this data used?" badge on the web surveys page.
 *
 * A closed or archived survey shows the badge to people who manage it (its owner and its
 * organization's Managers) once its outcome question is due again ([SurveyOutcomePrompt.isDue],
 * evaluated with [now]). Selecting the badge opens the card; saving stores the answer through
 * [SurveyLifecycleUseCase.saveOutcome], which hides the badge unless the answer is "Not yet" (that
 * restarts the wait).
 */
class SurveyOutcomePromptViewModel(
  surveyRepository: SurveyRepository,
  organizationRepository: OrganizationRepository,
  authRepository: AuthRepository,
  surveyLifecycle: SurveyLifecycleUseCase?,
  scope: CoroutineScope,
  /** Current time in epoch milliseconds. */
  private val now: () -> Long = { platformEpochMillis() },
) : SurveyOutcomePromptActions {
  private val outcomeCard = SurveyOutcomeCardHolder(surveyLifecycle, scope)

  private val surveys: StateFlow<List<SurveyPreviewItem>> =
    surveyRepository.observeSurveys().stateIn(scope, SharingStarted.Eagerly, emptyList())

  val uiState: StateFlow<SurveyOutcomePromptUiState> =
    combine(
        surveys,
        organizationRepository.observeOrganizations(),
        authRepository.observeSession(),
        outcomeCard.card,
      ) { surveys, organizations, auth, card ->
        val email = auth.profile.email
        val nowMillis = now()
        SurveyOutcomePromptUiState(
          dueSurveyIds =
            surveys
              .filter {
                manages(it, organizations, email) && SurveyOutcomePrompt.isDue(it, nowMillis)
              }
              .mapTo(mutableSetOf()) { it.id },
          card = card,
        )
      }
      .stateIn(scope, SharingStarted.Eagerly, SurveyOutcomePromptUiState())

  /** Whether [email] owns [survey] or manages its organization. */
  private fun manages(
    survey: SurveyPreviewItem,
    organizations: List<Organization>,
    email: String,
  ): Boolean {
    if (email.isBlank()) return false
    if (survey.ownerEmail.equals(email, ignoreCase = true)) return true
    return organizations.firstOrNull { it.id == survey.organizationId }?.isManager(email) == true
  }

  override fun openOutcomeCard(surveyId: String) {
    surveys.value.firstOrNull { it.id == surveyId }?.let(outcomeCard::open)
  }

  override fun toggleOutcome(kind: SurveyOutcomeKind) = outcomeCard.toggleOutcome(kind)

  override fun setEffortComparison(comparison: EffortComparison?) =
    outcomeCard.setEffortComparison(comparison)

  override fun saveOutcome() = outcomeCard.saveOutcome()

  override fun skipOutcome() = outcomeCard.skipOutcome()

  /** Hides the card (used by the prototype's Reset). */
  fun reset() = outcomeCard.dismiss()
}
