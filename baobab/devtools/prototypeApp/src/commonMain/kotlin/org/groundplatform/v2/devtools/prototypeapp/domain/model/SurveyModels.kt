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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

import groundplatform.v2.survey.FormConceptLinks
import org.groundplatform.v2.core.forms.model.FormDefinition

/** Visual color/feature theme for a survey's placeholder map thumbnail. */
enum class MapThumbnailTheme(
  val primaryTerrainHex: Long,
  val secondaryWaterHex: Long,
  val accentPolygonHex: Long,
  val badgeLabel: String,
) {
  RAINFOREST(
    primaryTerrainHex = 0xFF1B5E20,
    secondaryWaterHex = 0xFF0277BD,
    accentPolygonHex = 0xFFA5D6A7,
    badgeLabel = "CANOPY",
  ),
  SAVANNA(
    primaryTerrainHex = 0xFF6D4C41,
    secondaryWaterHex = 0xFF00838F,
    accentPolygonHex = 0xFFFFE082,
    badgeLabel = "CORRIDOR",
  ),
  HIGHLAND_AGRI(
    primaryTerrainHex = 0xFF2E7D32,
    secondaryWaterHex = 0xFF1565C0,
    accentPolygonHex = 0xFFC5E1A5,
    badgeLabel = "PARCELS",
  ),
  COASTAL_DELTA(
    primaryTerrainHex = 0xFF00695C,
    secondaryWaterHex = 0xFF0288D1,
    accentPolygonHex = 0xFF80CBC4,
    badgeLabel = "ESTUARY",
  ),
  WATERSHED(
    primaryTerrainHex = 0xFF33691E,
    secondaryWaterHex = 0xFF039BE5,
    accentPolygonHex = 0xFFE6EE9C,
    badgeLabel = "BASIN",
  ),
  PEATLAND(
    primaryTerrainHex = 0xFF3E2723,
    secondaryWaterHex = 0xFF006064,
    accentPolygonHex = 0xFF80DEEA,
    badgeLabel = "WETLAND",
  ),
}

/** Represents a survey shared with the current user on the "Download survey" screen. */
data class SurveyPreviewItem(
  val id: String,
  val title: String,
  val description: String,
  val location: String,
  val coordinatesLabel: String,
  val offlineSizeLabel: String,
  val isDownloaded: Boolean,
  val thumbnailTheme: MapThumbnailTheme,
  val entityCount: Int,
  /** Email of the survey's owner (`SurveyDef.owner_id`), or empty if unknown. */
  val ownerEmail: String = "",
  /** Organization the survey belongs to (`SurveyDef.organization_id`), or `null` if personal. */
  val organizationId: String? = null,
  /** Lifecycle state (`SurveyDef.state`); closed and archived surveys are read-only. */
  val state: SurveyLifecycleState = SurveyLifecycleState.PUBLISHED,
  /** When the survey stopped collecting data (ISO 8601 UTC), or `null` while it's published. */
  val closedAt: String? = null,
  /** Answer to "What happened with this data?" (`SurveyDef.outcome`), or `null` if not answered. */
  val outcome: SurveyOutcome? = null,
) {
  /** Whether the survey is closed or archived, so it can't be edited or collect data. */
  val isClosed: Boolean
    get() = state != SurveyLifecycleState.PUBLISHED
}

/** Survey-level form definitions stored alongside a survey's data. */
data class SurveyConfig(
  /** Primary [FormDefinition] of the survey, used by the form runner and workbench. */
  val primaryForm: FormDefinition? = null,
  /** [FormDefinition] for each form in the survey, keyed by form ID. */
  val formsById: Map<String, FormDefinition> = emptyMap(),
  /**
   * Dictionary concepts linked to each form's questions (`SurveyDef.form_concept_links`).
   * Authoritative over the `ground:concept` attributes in [formsById].
   */
  val formConceptLinks: List<FormConceptLinks> = emptyList(),
  /** Purpose Packs selected for the survey (`SurveyDef.purpose_ids`). */
  val purposeIds: List<String> = emptyList(),
  /** Programs the survey reports to (`SurveyDef.program_ids`). */
  val programIds: List<String> = emptyList(),
)

/** Number of map features and submissions stored for a survey. */
data class SurveyStats(val entityCount: Int, val submissionCount: Int)
