/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.core.impact

/*
 * Inputs and outputs of the impact aggregation job (docs/technical/backend/impact-aggregation.md).
 *
 * The inputs are plain snapshots so the same pure functions run in the backend job (JVM) and in
 * clients (the prototype computes them on demand), and dashboards match across environments.
 */

/** ID of the `"All users"` organization, which owns the global library. */
const val ALL_USERS_ORGANIZATION_ID = "org-all-users"

/** How a concept's values are aggregated (`groundplatform.v2.library.Concept.Aggregation`). */
enum class ImpactAggregation {
  /** Deduplicated features with a non-empty value. */
  COUNT_DISTINCT_FEATURES,
  /** Deduplicated features per code-list value. */
  COUNT_BY_CODE,
  /** Deduplicated features per code-list value, divided by features with any value. */
  SHARE_BY_CODE,
  /** Sum of numeric values over deduplicated features. */
  SUM,
  /** Mean of numeric values over deduplicated features. */
  MEAN,
  /** Not aggregated (names, identifiers). */
  NONE,
}

/** Privacy class of a concept (`groundplatform.v2.library.Concept.PrivacyClass`). */
enum class ImpactPrivacy {
  AGGREGATE_PUBLIC,
  ORG_ONLY,
  /** Never leaves the organization, not even in aggregates. */
  SENSITIVE,
}

/** Scope of an aggregate row (`groundplatform.v2.data.ImpactAggregate.ScopeType`). */
enum class ImpactScopeType {
  SURVEY,
  ORGANIZATION,
  COUNTRY,
  GLOBAL,
  CELL,
}

/** "Data was used" event types (`groundplatform.v2.data.ImpactEventType`). */
enum class ImpactEventKind {
  EXPORT,
  PARTNER_PUSH,
  RECEIPT_GENERATED,
  RECEIPT_SHARED,
  DOI_MINTED,
  SURVEY_CLOSED,
}

/** Uses of a survey's data (`groundplatform.v2.survey.SurveyOutcome.Outcome`). */
enum class ImpactOutcomeKind {
  SUBMITTED_EUDR_DDS,
  REPORTED_FERM,
  LAND_TITLING,
  SHARED_WITH_BUYERS,
  TRAINED_OR_VALIDATED_MODEL,
  PROTECTED_AREA_MANAGEMENT,
  NOT_YET,
}

/** A library concept, as far as aggregation is concerned. */
data class ImpactConcept(
  /** Concept ID, e.g. `eudr.commodity` or `org.<organization>.shade_tree_count`. */
  val id: String,
  /** Owning organization; [ALL_USERS_ORGANIZATION_ID] for global concepts. */
  val ownerOrganizationId: String,
  val aggregation: ImpactAggregation,
  val privacy: ImpactPrivacy = ImpactPrivacy.ORG_ONLY,
  /** UCUM unit of the concept's values (e.g. `har`, `kg`), or blank for unitless values. */
  val unit: String = "",
  /**
   * Goals the concept contributes to. For organization concepts these are **suggested** goals: they
   * produce separate organization-suggested rows at global scope, never main totals.
   */
  val goals: List<String> = emptyList(),
  val isDeprecated: Boolean = false,
) {
  val isGlobal: Boolean
    get() = ownerOrganizationId == ALL_USERS_ORGANIZATION_ID
}

/** A point on the WGS84 ellipsoid in degrees. */
data class ImpactLatLng(val lat: Double, val lng: Double)

/** Geometry of a map feature, used for area and grid cells. */
sealed interface ImpactGeometry {
  data class Point(val position: ImpactLatLng) : ImpactGeometry

  data class LineString(val positions: List<ImpactLatLng>) : ImpactGeometry

  /** A polygon: the first ring is the exterior, the rest are holes (open or closed rings). */
  data class Polygon(val rings: List<List<ImpactLatLng>>) : ImpactGeometry
}

/**
 * A value of a linked field: text as captured, with an optional unit that overrides the concept's.
 */
data class ImpactValue(
  /**
   * The value as text: a number, a code, or several codes separated by spaces (select multiple).
   */
  val text: String,
  /** UCUM unit of [text], or `null` when it is already in the concept's unit. */
  val unit: String? = null,
)

/** One map feature (or table row) of a survey. */
data class ImpactFeature(
  val entityId: String,
  /** AgStack-style GeoID, or `null`/blank when the feature has none (counted individually). */
  val geoId: String? = null,
  /** Last update as epoch milliseconds; the most recently updated duplicate wins. */
  val updatedAtMillis: Long = 0,
  /** Geometry for geodesic area and grid cells, if known. */
  val geometry: ImpactGeometry? = null,
  /** Precomputed area in hectares, used when [geometry] is `null` or has no area. */
  val areaHa: Double? = null,
  /** ISO 3166-1 alpha-2 country of the feature, or `null` to use the organization's country. */
  val countryCode: String? = null,
  /** Values of linked fields, keyed by concept ID. */
  val values: Map<String, ImpactValue> = emptyMap(),
)

/** A Purpose Pack selected by a survey. */
data class ImpactPurpose(
  val id: String,
  /** Whether the pack is in the global library (only those count platform-wide). */
  val isGlobal: Boolean,
  /** Export profiles the pack enables. */
  val exportProfileIds: Set<String> = emptySet(),
)

/** One recorded impact event (already GeoID-deduplicated when recorded). */
data class ImpactEventRecord(
  val kind: ImpactEventKind,
  val featureCount: Long = 0,
  val areaHa: Double = 0.0,
  val exportProfileId: String? = null,
)

/** One survey's snapshot. */
data class ImpactSurvey(
  val id: String,
  /** Organization of the survey, or `null` for a personal survey. */
  val organizationId: String?,
  val purposes: List<ImpactPurpose> = emptyList(),
  /** Concept IDs linked by the survey's fields (survey-level links are authoritative). */
  val linkedConceptIds: Set<String> = emptySet(),
  val features: List<ImpactFeature> = emptyList(),
  /** Submissions not attached to a map feature, which still count as collected data. */
  val submissionCount: Int = 0,
  val events: List<ImpactEventRecord> = emptyList(),
  /** Whether the survey is closed or archived (only those are expected to have an outcome). */
  val isClosed: Boolean = false,
  /** The organizer's answer, or `null` when unanswered; empty or `{NOT_YET}` means "Not yet". */
  val outcome: Set<ImpactOutcomeKind>? = null,
)

/** An organization's settings that affect aggregation. */
data class ImpactOrganization(
  val id: String,
  /** ISO 3166-1 alpha-2 country code, or `null` when not specified. */
  val countryCode: String? = null,
  /** `Organization.exclude_from_platform_aggregates`: kept out of every global output. */
  val excludeFromPlatformAggregates: Boolean = false,
)

/** Everything one aggregation run reads. */
data class ImpactInput(
  val surveys: List<ImpactSurvey>,
  /** Every concept of every library the surveys may resolve against. */
  val concepts: List<ImpactConcept>,
  val organizations: List<ImpactOrganization> = emptyList(),
)

/** Thresholds and parameters of a run. */
data class ImpactConfig(
  /** Minimum features for a cell to be published. */
  val cellMinFeatures: Int = 10,
  /** Minimum distinct organizations for a cell to be published. */
  val cellMinOrganizations: Int = 2,
  /** S2 level of fine cells (about 80 km²). */
  val fineCellLevel: Int = 10,
  /** S2 level of coarse cells (about 5,000 km²) that unpublishable fine cells merge into. */
  val coarseCellLevel: Int = 7,
  /** Minimum features for a country aggregate to be published. */
  val countryMinFeatures: Int = 10,
  /** Surveys with no purposes and fewer features than this are test or exploratory surveys. */
  val exploratoryMaxFeatures: Int = 5,
  /**
   * Concepts counted as distinct values (e.g. producers registered), regardless of their
   * aggregation rule. The first one a survey links is used.
   */
  val distinctValueConceptIds: List<String> = listOf("core.producer_id", "core.producer_name"),
  val runId: String = "",
  val pipelineVersion: Int = 1,
  val computedAtMillis: Long = 0,
)

/** What a row measures. */
enum class ImpactMetric {
  /** All deduplicated features of the scope ([ImpactAggregateRow.conceptId] is empty). */
  FEATURES,
  /** A linked concept, per its aggregation rule. */
  CONCEPT,
  /** Distinct values of a [ImpactConfig.distinctValueConceptIds] concept, in `value`. */
  DISTINCT_VALUES,
  /** Impact events of one type: `value` is the event count. */
  EVENT,
  /** Surveys per outcome kind: `value` is the survey count. */
  OUTCOME,
  /** Surveys per attribution score: `value` is the survey count. */
  ATTRIBUTION,
}

/** Outcome kind reported for closed surveys nobody answered (distinct from "Not yet"). */
const val OUTCOME_UNANSWERED = "UNANSWERED"

/** One aggregate (`groundplatform.v2.data.ImpactAggregate`). */
data class ImpactAggregateRow(
  val scopeType: ImpactScopeType,
  /** Survey ID, organization ID, ISO country code, S2 token, or empty for global. */
  val scopeId: String,
  val metric: ImpactMetric,
  /** Concept ID for concept rows; empty otherwise. */
  val conceptId: String = "",
  /** Code-list value for `*_BY_CODE` rows. */
  val code: String = "",
  val eventKind: ImpactEventKind? = null,
  /** [ImpactOutcomeKind] name or [OUTCOME_UNANSWERED] for outcome rows. */
  val outcomeKind: String = "",
  /** Attribution score (1–5) for attribution rows. */
  val attributionScore: Int = 0,
  val featureCount: Long = 0,
  val areaHa: Double = 0.0,
  /** Sum, mean, share (0–1), distinct-value count, event count, or survey count. */
  val value: Double = 0.0,
  /** Below the threshold: counts are zeroed and shown as "fewer than 10". */
  val suppressed: Boolean = false,
  /** An organization concept's row under its suggested goal (global scope only). */
  val organizationSuggested: Boolean = false,
  /** Owning organization of [conceptId] for organization-suggested rows. */
  val organizationId: String = "",
  /** Suggested goal for organization-suggested rows. */
  val goal: String = "",
)

/** Why a linked concept was skipped. */
enum class SkippedLinkReason {
  /** No concept with that ID. */
  UNKNOWN,
  DEPRECATED,
  /** Owned by an organization other than the survey's (not in its resolved library). */
  NOT_IN_LIBRARY,
}

data class SkippedLink(val surveyId: String, val conceptId: String, val reason: SkippedLinkReason)

/** Why a value couldn't be aggregated. */
enum class UnnormalizableReason {
  /** Not a number, for SUM and MEAN. */
  NOT_A_NUMBER,
  /** A unit that isn't supported or can't be converted to the concept's unit. */
  UNSUPPORTED_UNIT,
}

data class UnnormalizableValue(
  val surveyId: String,
  val conceptId: String,
  val entityId: String,
  val value: String,
  val unit: String?,
  val reason: UnnormalizableReason,
)

/** What a run couldn't count, for discreet "why?" explanations. */
data class ImpactDiagnostics(
  val skippedLinks: List<SkippedLink> = emptyList(),
  val unnormalizableValues: List<UnnormalizableValue> = emptyList(),
  /** Cells below the threshold even after merging into their parent. */
  val suppressedCellCount: Int = 0,
  /** Features in suppressed cells. */
  val suppressedCellFeatureCount: Long = 0,
  /** Countries below the threshold. */
  val suppressedCountryCount: Int = 0,
) {
  fun forSurvey(surveyId: String): ImpactDiagnostics =
    ImpactDiagnostics(
      skippedLinks = skippedLinks.filter { it.surveyId == surveyId },
      unnormalizableValues = unnormalizableValues.filter { it.surveyId == surveyId },
    )
}

/** Which condition set a survey's attribution score. */
enum class AttributionBasis(val score: Int) {
  /** Official or regulatory submission (EUDR DDS, FERM report, protected area management). */
  OFFICIAL_SUBMISSION(5),
  /** Sent to a partner, or used by buyers, for land titling, or to check a map or model. */
  PARTNER_USE(4),
  /** Exported with an export profile one of the survey's purposes enables. */
  PURPOSE_EXPORT(3),
  /** Data collected, use unknown. */
  DATA_COLLECTED(2),
  /** Test or exploratory survey, or no data yet. */
  EXPLORATORY(1),
}

data class ImpactAttribution(val surveyId: String, val basis: AttributionBasis) {
  val score: Int
    get() = basis.score
}

/** The result of one run. */
data class ImpactRunResult(
  val rows: List<ImpactAggregateRow>,
  val diagnostics: ImpactDiagnostics,
  /** Attribution per survey ID. */
  val attributions: Map<String, ImpactAttribution>,
) {
  fun rows(scopeType: ImpactScopeType, scopeId: String = ""): List<ImpactAggregateRow> =
    rows.filter {
      it.scopeType == scopeType && it.scopeId == scopeId
    }
}
