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
package org.openforis.collectone.v2.app.domain.model

/** Kind of "data was used" signal (`openforis.collectone.v1.data.ImpactEventType`). */
enum class ImpactEventType {
  EXPORT,
  PARTNER_PUSH,
  RECEIPT_GENERATED,
  RECEIPT_SHARED,
  DOI_MINTED,
  SURVEY_CLOSED,
}

/**
 * One "data was used" signal (`openforis.collectone.v1.data.ImpactEvent`; see
 * `docs/technical/model/data/04-impact-events.md`). Append-only and aggregate only: counts and
 * areas, no geometry, and no personal data beyond [actorUserId].
 *
 * Events are recorded on the device and upload with the other pending changes; [isUploaded] is the
 * only field that changes after recording.
 */
data class ImpactEvent(
  val id: String,
  val type: ImpactEventType,
  val surveyId: String,
  /** Organization of the survey, or `null` for a personal survey. */
  val organizationId: String?,
  /** The survey's Purpose Packs when the event happened. */
  val purposeIds: List<String> = emptyList(),
  /** Export profile used, for exports that used one. */
  val exportProfileId: String? = null,
  /** Map features covered, counting plots that share a GeoID once. */
  val featureCount: Int = 0,
  /** Total area of the covered features in hectares, counting plots that share a GeoID once. */
  val areaHa: Double = 0.0,
  /** When it happened (ISO 8601 UTC). */
  val occurredAt: String,
  val actorUserId: String,
  val isUploaded: Boolean = false,
)

/** What an impact event covers: how many map features and how much area. */
data class ImpactCoverage(val featureCount: Int = 0, val areaHa: Double = 0.0) {
  companion object {
    /**
     * Coverage of [entities], counting map features that share a GeoID (the same plot mapped twice)
     * once. Features without a GeoID count individually.
     */
    fun of(entities: List<GeospatialEntityItem>): ImpactCoverage {
      val unique = entities.distinctBy { it.geoId.trim().ifEmpty { "id:${it.id}" } }
      return ImpactCoverage(featureCount = unique.size, areaHa = unique.sumOf { it.areaHectares })
    }
  }
}

/** Lifecycle state of a survey (`openforis.collectone.v1.survey.SurveyState`). */
enum class SurveyLifecycleState {
  /** Under construction; editable by organizers and not yet live for field collection. */
  DRAFT,
  /** Collecting data. */
  PUBLISHED,
  /** Collection ended; the survey and its data are read-only. */
  CLOSED,
  /** Retired and kept for reference. */
  ARCHIVED,
}

/** A use of a survey's data (`SurveyOutcome.Outcome`), in plain language. */
enum class SurveyOutcomeKind {
  SUBMITTED_EUDR_DDS,
  REPORTED_FERM,
  LAND_TITLING,
  SHARED_WITH_BUYERS,
  TRAINED_OR_VALIDATED_MODEL,
  PROTECTED_AREA_MANAGEMENT,
  NOT_YET,
}

/** Time and cost compared with the previous method (`SurveyOutcome.EffortComparison`). */
enum class EffortComparison {
  LESS,
  SAME,
  MORE,
}

/**
 * The organizer's answer to "What happened with this data?" (`SurveyDef.outcome`), asked when a
 * survey is closed or archived.
 */
data class SurveyOutcome(
  val outcomes: Set<SurveyOutcomeKind> = emptySet(),
  val effortComparison: EffortComparison? = null,
  /** When it was answered (ISO 8601 UTC). */
  val answeredAt: String,
  val answeredBy: String,
) {
  /** Whether the data hasn't been used yet (answered "Not yet" or nothing else). */
  val isNotYet: Boolean
    get() = outcomes.isEmpty() || outcomes == setOf(SurveyOutcomeKind.NOT_YET)
}
