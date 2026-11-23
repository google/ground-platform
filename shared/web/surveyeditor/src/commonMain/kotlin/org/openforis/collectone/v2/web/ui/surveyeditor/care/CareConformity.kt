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
package org.openforis.collectone.v2.web.ui.surveyeditor.care

import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_care_authority_to_control
import org.openforis.collectone.v2.web.resources.wizard_care_collective_benefit
import org.openforis.collectone.v2.web.resources.wizard_care_effect_authority_off
import org.openforis.collectone.v2.web.resources.wizard_care_effect_authority_on
import org.openforis.collectone.v2.web.resources.wizard_care_effect_collective_benefit_off
import org.openforis.collectone.v2.web.resources.wizard_care_effect_collective_benefit_on
import org.openforis.collectone.v2.web.resources.wizard_care_effect_ethics_on
import org.openforis.collectone.v2.web.resources.wizard_care_effect_fpic_off
import org.openforis.collectone.v2.web.resources.wizard_care_effect_fpic_on
import org.openforis.collectone.v2.web.resources.wizard_care_effect_responsibility_off
import org.openforis.collectone.v2.web.resources.wizard_care_effect_responsibility_on
import org.openforis.collectone.v2.web.resources.wizard_care_ethics_aggregate_protection
import org.openforis.collectone.v2.web.resources.wizard_care_fpic
import org.openforis.collectone.v2.web.resources.wizard_care_responsibility
import org.openforis.collectone.v2.web.resources.wizard_care_standard_care_fao_indigenous_data_policy
import org.openforis.collectone.v2.web.resources.wizard_care_standard_care_fao_vggt
import org.openforis.collectone.v2.web.resources.wizard_care_standard_care_principles
import org.openforis.collectone.v2.web.resources.wizard_care_standard_fao_fpic_guidelines

/**
 * How a survey's settings measure up against the CARE Principles and FAO FPIC: each criterion (with
 * what it means for the survey in its current state), whether CARE defaults are in effect, and the
 * organization whose data is left out of platform-wide totals, if any.
 */
data class CareConformity(
  val criteria: List<CareGovernanceCriterion>,
  val isCareApplied: Boolean,
  val excludedOrganizationName: String? = null,
) {
  val metCount: Int
    get() = criteria.count { it.isSatisfied }
}

/**
 * The CARE / FPIC criteria for a survey whose sharing is [policy] and [peerDataVisibility], that
 * [consentRequired] collectors accept [consentText], and that [hasCollectiveBenefit] (questions
 * linked to standard fields, or a purpose chosen).
 */
fun careCriteria(
  policy: SharingPolicy,
  peerDataVisibility: PeerDataVisibility,
  consentRequired: Boolean,
  consentText: String,
  hasCollectiveBenefit: Boolean,
): List<CareGovernanceCriterion> {
  val isRestricted = policy == SharingPolicy.RESTRICTED
  val isOwnOnly = peerDataVisibility == PeerDataVisibility.OWN_ONLY
  val hasConsent = consentRequired && consentText.isNotBlank()
  return listOf(
    CareGovernanceCriterion(
      code = "C",
      title = Res.string.wizard_care_collective_benefit,
      standard = Res.string.wizard_care_standard_care_fao_indigenous_data_policy,
      description =
        if (hasCollectiveBenefit) Res.string.wizard_care_effect_collective_benefit_on
        else Res.string.wizard_care_effect_collective_benefit_off,
      isSatisfied = hasCollectiveBenefit,
    ),
    CareGovernanceCriterion(
      code = "A",
      title = Res.string.wizard_care_authority_to_control,
      standard = Res.string.wizard_care_standard_care_fao_vggt,
      description =
        if (isRestricted) Res.string.wizard_care_effect_authority_on
        else Res.string.wizard_care_effect_authority_off,
      isSatisfied = isRestricted,
    ),
    CareGovernanceCriterion(
      code = "R",
      title = Res.string.wizard_care_responsibility,
      standard = Res.string.wizard_care_standard_care_principles,
      description =
        if (isOwnOnly) Res.string.wizard_care_effect_responsibility_on
        else Res.string.wizard_care_effect_responsibility_off,
      isSatisfied = isOwnOnly,
    ),
    CareGovernanceCriterion(
      code = "E",
      title = Res.string.wizard_care_ethics_aggregate_protection,
      standard = Res.string.wizard_care_standard_care_principles,
      description = Res.string.wizard_care_effect_ethics_on,
      isSatisfied = true,
    ),
    CareGovernanceCriterion(
      code = "FPIC",
      title = Res.string.wizard_care_fpic,
      standard = Res.string.wizard_care_standard_fao_fpic_guidelines,
      description =
        if (hasConsent) Res.string.wizard_care_effect_fpic_on
        else Res.string.wizard_care_effect_fpic_off,
      isSatisfied = hasConsent,
    ),
  )
}

/**
 * CARE conformity of a saved survey [draft] owned by [organization], as the Survey editor's Sharing
 * pane shows it. CARE defaults count as in effect when sharing is restricted, collectors see only
 * their own submissions, and either consent is required or the survey has an IPLC context (the same
 * rule the wizard uses).
 */
fun careConformity(draft: SurveyEditorDraft, organization: Organization?): CareConformity {
  val sharing = draft.sharing
  val conceptIds = linkedConceptIds(draft)
  val hasLinkedQuestions =
    draft.forms.any { entry ->
      entry.form.questions.any { it.conceptLink != null }
    }
  val isCareApplied =
    sharing.policy == SharingPolicy.RESTRICTED &&
      sharing.peerDataVisibility == PeerDataVisibility.OWN_ONLY &&
      (sharing.collectorConsentRequired ||
        SurveyWizardRules.usesIplcCareDefaults(organization?.organizationType, conceptIds))
  return CareConformity(
    criteria =
      careCriteria(
        policy = sharing.policy,
        peerDataVisibility = sharing.peerDataVisibility,
        consentRequired = sharing.collectorConsentRequired,
        consentText = sharing.collectorConsentText,
        hasCollectiveBenefit = hasLinkedQuestions || draft.details.purposeIds.isNotEmpty(),
      ),
    isCareApplied = isCareApplied,
    excludedOrganizationName = organization?.takeIf { it.excludeFromPlatformAggregates }?.name,
  )
}

/**
 * [SharingSettings] with CARE defaults applied: restricted access, collectors see only their own
 * submissions, and FPIC consent required (keeping any consent terms already written).
 */
fun SharingSettings.withCareDefaults(): SharingSettings =
  copy(
    policy = SharingPolicy.RESTRICTED,
    peerDataVisibility = PeerDataVisibility.OWN_ONLY,
    collectorConsentRequired = true,
    collectorConsentText =
      collectorConsentText.ifBlank { SurveyWizardRules.DEFAULT_FPIC_CONSENT_TEXT },
  )

/** Dictionary concept IDs linked to [draft]'s questions or dataset properties. */
private fun linkedConceptIds(draft: SurveyEditorDraft): Set<String> = buildSet {
  draft.forms.forEach { entry ->
    entry.form.questions.forEach { question -> question.conceptLink?.conceptId?.let(::add) }
  }
  draft.datasets.forEach { dataset ->
    dataset.properties.forEach { property -> property.conceptLink?.conceptId?.let(::add) }
  }
}
