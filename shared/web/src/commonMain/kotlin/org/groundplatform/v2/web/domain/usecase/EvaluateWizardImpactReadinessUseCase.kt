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
package org.groundplatform.v2.web.domain.usecase

import org.groundplatform.v2.app.domain.model.LibraryPrograms
import org.groundplatform.v2.app.domain.model.Organization
import org.groundplatform.v2.app.domain.model.editor.DatasetKind
import org.groundplatform.v2.app.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.app.domain.model.editor.PeerDataVisibility
import org.groundplatform.v2.app.domain.model.editor.SharingPolicy
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.app.domain.usecase.ExportSurveyDataUseCase
import org.groundplatform.v2.app.domain.usecase.ResolvedLibrary
import org.groundplatform.v2.web.domain.model.ImpactGoals
import org.groundplatform.v2.web.domain.model.editor.SurveyWizardRules
import org.groundplatform.v2.web.domain.model.editor.WizardImpactReadiness

/**
 * Evaluates a survey draft's impact, export profile mapping, and CARE / FAO Indigenous data
 * sovereignty readiness for the Survey Creation Wizard.
 */
class EvaluateWizardImpactReadinessUseCase(
  private val exportSurveyDataUseCase: ExportSurveyDataUseCase = ExportSurveyDataUseCase()
) {
  /** Computes [WizardImpactReadiness] for [draft] against [library] and [selectedOrganization]. */
  operator fun invoke(
    draft: SurveyEditorDraft,
    library: ResolvedLibrary,
    selectedOrganization: Organization?,
    language: String,
    isCareSovereigntyApplied: Boolean,
    isCollectorConsentRequired: Boolean,
  ): WizardImpactReadiness {
    val purposeIds = draft.details.purposeIds
    val selectedPacks = purposeIds.mapNotNull(library::purposePack)
    val packTitles = buildList {
      addAll(selectedPacks.map { it.title.get(language).ifBlank { it.id } })
      if (SurveyWizardRules.OTHER_PURPOSE_ID in purposeIds) {
        val otherText = draft.details.otherPurpose.trim()
        add(if (otherText.isNotEmpty()) "Other: $otherText" else "Other")
      }
    }
    val programLabels = draft.details.programIds.map(LibraryPrograms::label)

    val allQuestions = draft.forms.flatMap { it.form.questions }
    val linkedConceptIds = extractLinkedConceptIds(draft)

    val goalIds = buildSet {
      selectedPacks.forEach { addAll(it.goals) }
      linkedConceptIds.forEach { id -> library.concept(id)?.goals?.let(::addAll) }
    }
    val goalTitles = goalIds.map(ImpactGoals::label)

    val primaryDataset = draft.datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER }
    val enabledProfiles = exportSurveyDataUseCase.enabledProfiles(library, purposeIds)
    val exportPlans =
      if (primaryDataset != null) {
        enabledProfiles.map { profile ->
          exportSurveyDataUseCase.plan(
            profile = profile,
            dataset = primaryDataset,
            forms = draft.forms,
            library = library,
            entities = emptyList(),
            language = language,
          )
        }
      } else {
        emptyList()
      }

    val isCareApplied =
      draft.sharing.policy == SharingPolicy.RESTRICTED &&
        draft.sharing.peerDataVisibility == PeerDataVisibility.OWN_ONLY &&
        (isCareSovereigntyApplied ||
          isCollectorConsentRequired ||
          draft.sharing.collectorConsentRequired ||
          SurveyWizardRules.usesIplcCareDefaults(
            selectedOrganization?.organizationType,
            linkedConceptIds,
          ))

    return WizardImpactReadiness(
      selectedPackTitles = packTitles,
      selectedProgramLabels = programLabels,
      linkedConceptCount = allQuestions.count { it.conceptLink != null },
      totalQuestionCount = allQuestions.count { it.type != EditorQuestionType.NOTE },
      goalTitles = goalTitles,
      exportPlans = exportPlans,
      careSovereigntyApplied = isCareApplied,
      excludeFromPlatformAggregates = selectedOrganization?.excludeFromPlatformAggregates == true,
    )
  }

  /** Extracts all dictionary concept IDs linked to questions or dataset properties in [draft]. */
  fun extractLinkedConceptIds(draft: SurveyEditorDraft): Set<String> = buildSet {
    draft.forms.forEach { entry ->
      entry.form.questions.forEach { question -> question.conceptLink?.conceptId?.let(::add) }
    }
    draft.datasets.forEach { dataset ->
      dataset.properties.forEach { property -> property.conceptLink?.conceptId?.let(::add) }
    }
  }
}
