/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.web.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.openforis.collectone.v2.app.data.seed.SurveyEditorSamples
import org.openforis.collectone.v2.app.domain.usecase.ResolvedLibrary
import org.openforis.collectone.v2.web.domain.model.ImpactText
import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.domain.model.editor.SurveyCollectionMode
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.domain.model.editor.WizardMapFeatureSource
import org.openforis.collectone.v2.web.domain.model.editor.WizardStartMethod
import org.openforis.collectone.v2.web.domain.usecase.EvaluateWizardImpactReadinessUseCase
import org.openforis.collectone.v2.web.ui.surveyeditor.care.CareGovernanceRubric

/** The English labels of the Survey Creation Wizard's choices and readiness summary. */
class WizardTextTest {

  private fun english(resources: List<StringResource>) = runBlocking {
    resources.map { getString(it) }
  }

  private fun english(text: ImpactText) = runBlocking { text.load() }

  @Test
  fun stepLabels() {
    assertEquals(
      listOf("Overview", "Purpose", "Survey area", "Map features", "Forms", "Sharing"),
      english(CreateSurveyWizardStep.entries.map { it.labelRes }),
    )
    assertEquals(
      listOf(
        "Title, organization, languages, and start method",
        "Survey goal, purpose pack, and reporting programs",
        "Study boundary and offline/web basemap imagery",
        "Primary map layer, sample plots, or imported features",
        "Questionnaires, library concepts, and export mapping",
        "Access, CARE data sovereignty, and impact readiness",
      ),
      english(CreateSurveyWizardStep.entries.map { it.subtitleRes }),
    )
  }

  @Test
  fun startMethodLabels() {
    assertEquals(
      listOf("Build a new survey", "Copy an existing survey", "Import from file"),
      english(WizardStartMethod.entries.map { it.labelRes }),
    )
    assertEquals(
      listOf(
        "Configure map layers and forms step by step or from purpose packs.",
        "Clone forms, map layer schemas, survey area, and purpose packs without data.",
        "Import a form definition from ODK XForms (.xml) or Collect Earth (.cep).",
      ),
      english(WizardStartMethod.entries.map { it.descriptionRes }),
    )
  }

  @Test
  fun collectionModeLabels() {
    assertEquals(
      listOf("Field survey", "Visual classification", "Both"),
      english(SurveyCollectionMode.entries.map { it.labelRes }),
    )
    assertEquals(
      listOf(
        "Mobile offline/online field data collection by data collectors on the ground.",
        "Desk-based visual classification of sample plots over satellite and basemap imagery.",
        "Combined desk-based visual classification and mobile field data collection.",
      ),
      english(SurveyCollectionMode.entries.map { it.summaryRes }),
    )
  }

  @Test
  fun mapFeatureSourceLabels() {
    assertEquals(
      listOf(
        "Data collectors add map features in the field",
        "Generate sample plots",
        "Import map features (GeoJSON / KML)",
      ),
      english(WizardMapFeatureSource.entries.map { it.labelRes }),
    )
    assertEquals(
      listOf(
        "Collectors capture new points or polygons on site during field visits.",
        "Create systematic or random sample plots (and sub-plot grids) inside the survey area.",
        "Upload pre-defined plot boundaries, parcels, or points from a file.",
      ),
      english(WizardMapFeatureSource.entries.map { it.descriptionRes }),
    )
  }

  @Test
  fun careGovernanceCriteria() {
    val criteria = CareGovernanceRubric.DEFAULT_CRITERIA
    assertEquals(listOf("C", "A", "R", "E", "FPIC"), criteria.map { it.code })
    assertEquals(
      listOf(
        "Collective Benefit",
        "Authority to Control",
        "Responsibility",
        "Ethics",
        "Free, Prior and Informed Consent",
      ),
      english(criteria.map { it.title }),
    )
    assertEquals(
      listOf(
        "CARE & FAO Indigenous Data Policy",
        "CARE & FAO VGGT Tenure Governance",
        "CARE Principles",
        "CARE Principles",
        "FAO FPIC Manual",
      ),
      english(criteria.map { it.standard }),
    )
    assertEquals(
      listOf(
        "Ecosystem and community indicators use shared library concepts for community-owned " +
          "evidence.",
        "Access is Restricted so community stewards control who can view or collect survey " +
          "records.",
        "Peer visibility is limited to each contributor's own submissions plus survey organizers.",
        "Sensitive community data is protected from unapproved external aggregation.",
        "Explicit collector/participant consent is required before recording field data.",
      ),
      english(criteria.map { it.description }),
    )
  }

  @Test
  fun readinessOtherPurposeTitles() {
    val draft = SurveyEditorSamples.draft()
    fun packTitles(otherPurpose: String) =
      EvaluateWizardImpactReadinessUseCase()(
          draft.copy(
            details =
              draft.details.copy(
                purposeIds = listOf(SurveyWizardRules.OTHER_PURPOSE_ID),
                otherPurpose = otherPurpose,
              )
          ),
          ResolvedLibrary(organizationId = null),
          selectedOrganization = null,
          language = "en",
          isCareSovereigntyApplied = false,
          isCollectorConsentRequired = false,
        )
        .selectedPackTitles
        .map(::english)

    assertEquals(listOf("Other: Amphibian habitats"), packTitles(" Amphibian habitats "))
    assertEquals(listOf("Other"), packTitles(""))
  }
}
