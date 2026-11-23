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
package org.openforis.collectone.v2.web.ui.surveyeditor.care

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_care_authority_to_control
import org.openforis.collectone.v2.web.resources.wizard_care_authority_to_control_description
import org.openforis.collectone.v2.web.resources.wizard_care_collective_benefit
import org.openforis.collectone.v2.web.resources.wizard_care_collective_benefit_description
import org.openforis.collectone.v2.web.resources.wizard_care_ethics
import org.openforis.collectone.v2.web.resources.wizard_care_ethics_description
import org.openforis.collectone.v2.web.resources.wizard_care_fpic
import org.openforis.collectone.v2.web.resources.wizard_care_fpic_description
import org.openforis.collectone.v2.web.resources.wizard_care_responsibility
import org.openforis.collectone.v2.web.resources.wizard_care_responsibility_description
import org.openforis.collectone.v2.web.resources.wizard_care_standard_care_fao_indigenous_data_policy
import org.openforis.collectone.v2.web.resources.wizard_care_standard_care_fao_vggt
import org.openforis.collectone.v2.web.resources.wizard_care_standard_care_principles
import org.openforis.collectone.v2.web.resources.wizard_care_standard_fao_fpic_manual

/**
 * One CARE Principle or FAO Indigenous Peoples' data governance & FPIC criterion displayed in the
 * wizard's Sharing & data sovereignty step.
 */
data class CareGovernanceCriterion(
  /** Short code (the CARE letter, or `FPIC`), shown as is. */
  val code: String,
  val title: StringResource,
  val standard: StringResource,
  val description: StringResource,
  val isSatisfied: Boolean,
)

/** CARE and FAO FPIC governance criteria of the wizard's Sharing step. */
object CareGovernanceRubric {
  /** The default 5-criterion CARE & FAO FPIC governance rubric shown in Step 6 (`Sharing`). */
  val DEFAULT_CRITERIA: List<CareGovernanceCriterion> =
    listOf(
      CareGovernanceCriterion(
        code = "C",
        title = Res.string.wizard_care_collective_benefit,
        standard = Res.string.wizard_care_standard_care_fao_indigenous_data_policy,
        description = Res.string.wizard_care_collective_benefit_description,
        isSatisfied = true,
      ),
      CareGovernanceCriterion(
        code = "A",
        title = Res.string.wizard_care_authority_to_control,
        standard = Res.string.wizard_care_standard_care_fao_vggt,
        description = Res.string.wizard_care_authority_to_control_description,
        isSatisfied = true,
      ),
      CareGovernanceCriterion(
        code = "R",
        title = Res.string.wizard_care_responsibility,
        standard = Res.string.wizard_care_standard_care_principles,
        description = Res.string.wizard_care_responsibility_description,
        isSatisfied = true,
      ),
      CareGovernanceCriterion(
        code = "E",
        title = Res.string.wizard_care_ethics,
        standard = Res.string.wizard_care_standard_care_principles,
        description = Res.string.wizard_care_ethics_description,
        isSatisfied = true,
      ),
      CareGovernanceCriterion(
        code = "FPIC",
        title = Res.string.wizard_care_fpic,
        standard = Res.string.wizard_care_standard_fao_fpic_manual,
        description = Res.string.wizard_care_fpic_description,
        isSatisfied = true,
      ),
    )
}
