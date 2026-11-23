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

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.openforis.collectone.v2.web.domain.model.ImpactCountedMessage
import org.openforis.collectone.v2.web.domain.model.ImpactMessage
import org.openforis.collectone.v2.web.domain.model.ImpactText
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_goal_deforestation_free_supply_chains
import org.openforis.collectone.v2.web.resources.wizard_goal_ecosystem_restoration
import org.openforis.collectone.v2.web.resources.wizard_goal_effective_protected_areas
import org.openforis.collectone.v2.web.resources.wizard_goal_forest_carbon_and_conservation
import org.openforis.collectone.v2.web.resources.wizard_goal_iplc_rights_and_tenure
import org.openforis.collectone.v2.web.resources.wizard_goal_legal_timber
import org.openforis.collectone.v2.web.resources.wizard_goal_open_earth_data
import org.openforis.collectone.v2.web.resources.wizard_goal_other_standard_fields
import org.openforis.collectone.v2.web.resources.wizard_goal_smallholder_livelihoods
import org.openforis.collectone.v2.web.resources.wizard_impact_detail_average_of
import org.openforis.collectone.v2.web.resources.wizard_impact_detail_by
import org.openforis.collectone.v2.web.resources.wizard_impact_detail_covering
import org.openforis.collectone.v2.web.resources.wizard_impact_detail_of_answered
import org.openforis.collectone.v2.web.resources.wizard_impact_detail_total_from
import org.openforis.collectone.v2.web.resources.wizard_impact_map_features
import org.openforis.collectone.v2.web.resources.wizard_impact_note_link_deprecated
import org.openforis.collectone.v2.web.resources.wizard_impact_note_link_not_in_library
import org.openforis.collectone.v2.web.resources.wizard_impact_note_link_unknown
import org.openforis.collectone.v2.web.resources.wizard_impact_note_not_a_number
import org.openforis.collectone.v2.web.resources.wizard_impact_note_unsupported_unit
import org.openforis.collectone.v2.web.resources.wizard_impact_share_value
import org.openforis.collectone.v2.web.resources.wizard_impact_value_with_area
import org.openforis.collectone.v2.web.resources.wizard_purpose_other
import org.openforis.collectone.v2.web.resources.wizard_readiness_other_purpose

/** [this] resolved in the current locale, for suspend code such as PDF summaries. */
suspend fun ImpactText.load(): String =
  when (this) {
    is ImpactText.Plain -> value
    is ImpactText.Message -> getString(key.resource, *args.loadArgs())
    is ImpactText.Plural ->
      getPluralString(key.resource, quantity.toPluralQuantity(), *args.loadArgs())
    is ImpactText.Measured -> impactMeasurementText(measurement).resolveString()
  }

private suspend fun List<Any>.loadArgs(): Array<Any> = map {
  if (it is ImpactText) it.load() else it
}
  .toTypedArray()

/** A plural count clamped to the range plural resources select on. */
fun Long.toPluralQuantity(): Int = coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

/** The string resource of an [ImpactText.Message] key. */
val ImpactMessage.resource: StringResource
  get() =
    when (this) {
      ImpactMessage.DETAIL_BY -> Res.string.wizard_impact_detail_by
      ImpactMessage.VALUE_WITH_AREA -> Res.string.wizard_impact_value_with_area
      ImpactMessage.SHARE_VALUE -> Res.string.wizard_impact_share_value
      ImpactMessage.DETAIL_OF_ANSWERED -> Res.string.wizard_impact_detail_of_answered
      ImpactMessage.DETAIL_TOTAL_FROM -> Res.string.wizard_impact_detail_total_from
      ImpactMessage.DETAIL_AVERAGE_OF -> Res.string.wizard_impact_detail_average_of
      ImpactMessage.DETAIL_COVERING -> Res.string.wizard_impact_detail_covering
      ImpactMessage.NOTE_LINK_UNKNOWN -> Res.string.wizard_impact_note_link_unknown
      ImpactMessage.NOTE_LINK_DEPRECATED -> Res.string.wizard_impact_note_link_deprecated
      ImpactMessage.NOTE_LINK_NOT_IN_LIBRARY -> Res.string.wizard_impact_note_link_not_in_library
      ImpactMessage.GOAL_DEFORESTATION_FREE_SUPPLY_CHAINS ->
        Res.string.wizard_goal_deforestation_free_supply_chains
      ImpactMessage.GOAL_ECOSYSTEM_RESTORATION -> Res.string.wizard_goal_ecosystem_restoration
      ImpactMessage.GOAL_FOREST_CARBON_AND_CONSERVATION ->
        Res.string.wizard_goal_forest_carbon_and_conservation
      ImpactMessage.GOAL_IPLC_RIGHTS_AND_TENURE -> Res.string.wizard_goal_iplc_rights_and_tenure
      ImpactMessage.GOAL_LEGAL_TIMBER -> Res.string.wizard_goal_legal_timber
      ImpactMessage.GOAL_OPEN_EARTH_DATA -> Res.string.wizard_goal_open_earth_data
      ImpactMessage.GOAL_EFFECTIVE_PROTECTED_AREAS ->
        Res.string.wizard_goal_effective_protected_areas
      ImpactMessage.GOAL_SMALLHOLDER_LIVELIHOODS -> Res.string.wizard_goal_smallholder_livelihoods
      ImpactMessage.OTHER_STANDARD_FIELDS -> Res.string.wizard_goal_other_standard_fields
      ImpactMessage.OTHER_PURPOSE_NAMED -> Res.string.wizard_readiness_other_purpose
      ImpactMessage.OTHER_PURPOSE -> Res.string.wizard_purpose_other
    }

/** The plural resource of an [ImpactText.Plural] key. */
val ImpactCountedMessage.resource: PluralStringResource
  get() =
    when (this) {
      ImpactCountedMessage.MAP_FEATURES -> Res.plurals.wizard_impact_map_features
      ImpactCountedMessage.NOTE_NOT_A_NUMBER -> Res.plurals.wizard_impact_note_not_a_number
      ImpactCountedMessage.NOTE_UNSUPPORTED_UNIT -> Res.plurals.wizard_impact_note_unsupported_unit
    }
