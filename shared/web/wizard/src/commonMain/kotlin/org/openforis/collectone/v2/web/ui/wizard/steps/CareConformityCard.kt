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
package org.openforis.collectone.v2.web.ui.wizard.steps

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.ui.common.LocalAppLocale
import org.openforis.collectone.v2.web.domain.model.editor.CareDefaultsReason
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_care_auto_reason_concepts
import org.openforis.collectone.v2.web.resources.wizard_care_auto_reason_more
import org.openforis.collectone.v2.web.resources.wizard_care_auto_reason_organization
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorUiState
import org.openforis.collectone.v2.web.ui.surveyeditor.care.CareCardPosition
import org.openforis.collectone.v2.web.ui.surveyeditor.care.CareConformity
import org.openforis.collectone.v2.web.ui.surveyeditor.care.CareConformityCard
import org.openforis.collectone.v2.web.ui.surveyeditor.care.CareStickyChip
import org.openforis.collectone.v2.web.ui.surveyeditor.care.careCriteria
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardActions
import org.openforis.collectone.v2.web.ui.wizard.CreateSurveyWizardUiState

/**
 * CARE conformity of the survey being created: the editor's sharing, the wizard's consent choice
 * (which it keeps until the survey is saved), and the readiness of its linked fields and purposes.
 */
@Composable
private fun rememberWizardCareConformity(
  uiState: CreateSurveyWizardUiState,
  editorUiState: SurveyEditorUiState,
): CareConformity {
  val readiness = uiState.impactReadiness
  return remember(
    editorUiState.sharing.policy,
    editorUiState.sharing.peerDataVisibility,
    uiState.collectorConsentRequired,
    uiState.collectorConsentText,
    readiness,
    uiState.selectedOrganization,
  ) {
    CareConformity(
      criteria =
        careCriteria(
          policy = editorUiState.sharing.policy,
          peerDataVisibility = editorUiState.sharing.peerDataVisibility,
          consentRequired = uiState.collectorConsentRequired,
          consentText = uiState.collectorConsentText,
          hasCollectiveBenefit =
            readiness.linkedConceptCount > 0 || readiness.selectedPackTitles.isNotEmpty(),
        ),
      isCareApplied = readiness.careSovereigntyApplied,
      excludedOrganizationName =
        uiState.selectedOrganization?.name?.takeIf { readiness.excludeFromPlatformAggregates },
    )
  }
}

/** The wizard's CARE card, with why CARE was applied automatically when it was. */
@Composable
internal fun WizardCareCard(
  uiState: CreateSurveyWizardUiState,
  actions: CreateSurveyWizardActions,
  editorUiState: SurveyEditorUiState,
  position: CareCardPosition,
) {
  CareConformityCard(
    conformity = rememberWizardCareConformity(uiState, editorUiState),
    expanded = uiState.isCareCardExpanded,
    onExpandedChange = actions::setCareCardExpanded,
    onApplyDefaults = actions::applyCareSovereigntyDefaults,
    position = position,
    autoReason = uiState.careDefaultsAutoReason?.let { careDefaultsReasonText(it, uiState) },
  )
}

/** The wizard's sticky CARE chip, shown while the CARE card is scrolled away. */
@Composable
internal fun WizardCareStickyChip(
  uiState: CreateSurveyWizardUiState,
  actions: CreateSurveyWizardActions,
  editorUiState: SurveyEditorUiState,
  position: CareCardPosition,
  scrollState: ScrollState,
  modifier: Modifier = Modifier,
) {
  CareStickyChip(
    conformity = rememberWizardCareConformity(uiState, editorUiState),
    position = position,
    scrollState = scrollState,
    onShowCard = { actions.setCareCardExpanded(true) },
    modifier = modifier,
  )
}

/**
 * Why the wizard applied CARE defaults by itself: the organization's name for an IPLC organization,
 * or the labels (in the app's language) of the first [MAX_REASON_CONCEPTS] linked IPLC concepts.
 */
@Composable
private fun careDefaultsReasonText(
  reason: CareDefaultsReason,
  uiState: CreateSurveyWizardUiState,
): String =
  when (reason) {
    CareDefaultsReason.IplcOrganization ->
      stringResource(
        Res.string.wizard_care_auto_reason_organization,
        uiState.selectedOrganization?.name.orEmpty(),
      )
    is CareDefaultsReason.IplcConcepts -> {
      val language = LocalAppLocale.current.substringBefore('-')
      val labels =
        reason.conceptIds.map { id ->
          uiState.library.concept(id)?.label?.get(language)?.ifBlank { null } ?: id
        }
      val shown = labels.take(MAX_REASON_CONCEPTS).joinToString(", ")
      val fieldList =
        if (labels.size > MAX_REASON_CONCEPTS) {
          stringResource(
            Res.string.wizard_care_auto_reason_more,
            shown,
            labels.size - MAX_REASON_CONCEPTS,
          )
        } else {
          shown
        }
      stringResource(Res.string.wizard_care_auto_reason_concepts, fieldList)
    }
  }

/** Most linked IPLC concepts named in the CARE auto-apply reason before "and N more". */
private const val MAX_REASON_CONCEPTS = 3
