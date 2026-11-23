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

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.domain.model.ImpactEventType
import org.openforis.collectone.v2.web.domain.model.ImpactAttributionLevel
import org.openforis.collectone.v2.web.domain.model.ImpactHint
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_impact_activity_doi_minted
import org.openforis.collectone.v2.web.resources.wizard_impact_activity_downloads
import org.openforis.collectone.v2.web.resources.wizard_impact_activity_receipts_created
import org.openforis.collectone.v2.web.resources.wizard_impact_activity_receipts_shared
import org.openforis.collectone.v2.web.resources.wizard_impact_activity_sent_to_partners
import org.openforis.collectone.v2.web.resources.wizard_impact_activity_surveys_closed
import org.openforis.collectone.v2.web.resources.wizard_impact_attribution_data_collected
import org.openforis.collectone.v2.web.resources.wizard_impact_attribution_data_collected_explanation
import org.openforis.collectone.v2.web.resources.wizard_impact_attribution_exploratory
import org.openforis.collectone.v2.web.resources.wizard_impact_attribution_exploratory_explanation
import org.openforis.collectone.v2.web.resources.wizard_impact_attribution_official_submission
import org.openforis.collectone.v2.web.resources.wizard_impact_attribution_official_submission_explanation
import org.openforis.collectone.v2.web.resources.wizard_impact_attribution_partner_use
import org.openforis.collectone.v2.web.resources.wizard_impact_attribution_partner_use_explanation
import org.openforis.collectone.v2.web.resources.wizard_impact_attribution_purpose_export
import org.openforis.collectone.v2.web.resources.wizard_impact_attribution_purpose_export_explanation
import org.openforis.collectone.v2.web.resources.wizard_impact_hint_collect_data
import org.openforis.collectone.v2.web.resources.wizard_impact_hint_link_fields
import org.openforis.collectone.v2.web.resources.wizard_impact_hint_pick_purpose

/** The level's name in plain language. */
val ImpactAttributionLevel.labelRes: StringResource
  get() =
    when (this) {
      ImpactAttributionLevel.OFFICIAL_SUBMISSION ->
        Res.string.wizard_impact_attribution_official_submission
      ImpactAttributionLevel.PARTNER_USE -> Res.string.wizard_impact_attribution_partner_use
      ImpactAttributionLevel.PURPOSE_EXPORT -> Res.string.wizard_impact_attribution_purpose_export
      ImpactAttributionLevel.DATA_COLLECTED -> Res.string.wizard_impact_attribution_data_collected
      ImpactAttributionLevel.EXPLORATORY -> Res.string.wizard_impact_attribution_exploratory
    }

/** What the level means for how the data was used. */
val ImpactAttributionLevel.explanationRes: StringResource
  get() =
    when (this) {
      ImpactAttributionLevel.OFFICIAL_SUBMISSION ->
        Res.string.wizard_impact_attribution_official_submission_explanation
      ImpactAttributionLevel.PARTNER_USE ->
        Res.string.wizard_impact_attribution_partner_use_explanation
      ImpactAttributionLevel.PURPOSE_EXPORT ->
        Res.string.wizard_impact_attribution_purpose_export_explanation
      ImpactAttributionLevel.DATA_COLLECTED ->
        Res.string.wizard_impact_attribution_data_collected_explanation
      ImpactAttributionLevel.EXPLORATORY ->
        Res.string.wizard_impact_attribution_exploratory_explanation
    }

/** What to do to get numbers. */
val ImpactHint.messageRes: StringResource
  get() =
    when (this) {
      ImpactHint.COLLECT_DATA -> Res.string.wizard_impact_hint_collect_data
      ImpactHint.PICK_PURPOSE -> Res.string.wizard_impact_hint_pick_purpose
      ImpactHint.LINK_FIELDS -> Res.string.wizard_impact_hint_link_fields
    }

/** The name of this kind of "data was used" activity on Impact views, e.g. "Downloads". */
val ImpactEventType.activityLabelRes: StringResource
  get() =
    when (this) {
      ImpactEventType.EXPORT -> Res.string.wizard_impact_activity_downloads
      ImpactEventType.PARTNER_PUSH -> Res.string.wizard_impact_activity_sent_to_partners
      ImpactEventType.RECEIPT_GENERATED -> Res.string.wizard_impact_activity_receipts_created
      ImpactEventType.RECEIPT_SHARED -> Res.string.wizard_impact_activity_receipts_shared
      ImpactEventType.DOI_MINTED -> Res.string.wizard_impact_activity_doi_minted
      ImpactEventType.SURVEY_CLOSED -> Res.string.wizard_impact_activity_surveys_closed
    }
