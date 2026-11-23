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
package org.openforis.collectone.v2.web.domain.model

/** The wording of an [ImpactText.Message]; arguments are listed per entry. */
enum class ImpactMessage {
  /** The standard field's label, lowercase. */
  DETAIL_BY,
  /** The map features (as text) and their area. */
  VALUE_WITH_AREA,
  /** The share as a percentage and the most common answer. */
  SHARE_VALUE,
  /** The map features with an answer (as text). */
  DETAIL_OF_ANSWERED,
  /** The map features (as text). */
  DETAIL_TOTAL_FROM,
  /** The map features (as text). */
  DETAIL_AVERAGE_OF,
  /** The area. */
  DETAIL_COVERING,
  /** The standard field's ID or label. */
  NOTE_LINK_UNKNOWN,
  /** The standard field's ID or label. */
  NOTE_LINK_DEPRECATED,
  /** The standard field's ID or label. */
  NOTE_LINK_NOT_IN_LIBRARY,
  GOAL_DEFORESTATION_FREE_SUPPLY_CHAINS,
  GOAL_ECOSYSTEM_RESTORATION,
  GOAL_FOREST_CARBON_AND_CONSERVATION,
  GOAL_IPLC_RIGHTS_AND_TENURE,
  GOAL_LEGAL_TIMBER,
  GOAL_OPEN_EARTH_DATA,
  GOAL_EFFECTIVE_PROTECTED_AREAS,
  GOAL_SMALLHOLDER_LIVELIHOODS,
  /** The group of standard fields without a goal. */
  OTHER_STANDARD_FIELDS,
  /** The organizer's own purpose. */
  OTHER_PURPOSE_NAMED,
  /** The "Other" purpose, not described yet. */
  OTHER_PURPOSE,
}

/** The count-dependent wording of an [ImpactText.Plural]; arguments are listed per entry. */
enum class ImpactCountedMessage {
  /** The count, formatted. */
  MAP_FEATURES,
  /** The standard field, the count, and examples of the values. */
  NOTE_NOT_A_NUMBER,
  /** The standard field, the count, and examples of the values. */
  NOTE_UNSUPPORTED_UNIT,
}
