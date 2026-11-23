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
package org.openforis.collectone.v2.web.ui.surveys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.openforis.collectone.v2.app.domain.model.EffortComparison
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.repository.AuthRepository
import org.openforis.collectone.v2.app.domain.repository.OrganizationRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn
import org.openforis.collectone.v2.web.domain.model.SurveyOutcomePrompt
import org.openforis.collectone.v2.web.domain.usecase.SurveyLifecycleUseCase
import org.openforis.collectone.v2.web.ui.surveyoutcome.SurveyOutcomeActions
import org.openforis.collectone.v2.web.ui.surveyoutcome.SurveyOutcomeCardHolder

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
  dispatcher: CoroutineDispatcher = ViewModelDispatcher,
  private val clock: Clock = Clock.System,
) : ViewModel(viewModelScopeOn(dispatcher)), SurveyOutcomePromptActions {
  private val outcomeCard = SurveyOutcomeCardHolder(surveyLifecycle, viewModelScope)

  private val surveys: StateFlow<List<SurveyPreviewItem>> =
    surveyRepository.observeSurveys().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val uiState: StateFlow<SurveyOutcomePromptUiState> =
    combine(
        surveys,
        organizationRepository.observeOrganizations(),
        authRepository.observeSession(),
        outcomeCard.card,
      ) { surveys, organizations, auth, card ->
        val email = auth.profile.email
        val now = clock.now()
        SurveyOutcomePromptUiState(
          dueSurveyIds =
            surveys
              .filter {
                manages(it, organizations, email) && SurveyOutcomePrompt.isDue(it, now)
              }
              .mapTo(mutableSetOf()) { it.id },
          card = card,
        )
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, SurveyOutcomePromptUiState())

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

  /** Hides the card (used by session resets). */
  fun reset() = outcomeCard.dismiss()
}
