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

import kotlin.math.max
import org.groundplatform.v2.core.geo.Wgs84

/*
 * Snapshot and scope, and GeoID deduplication (docs/technical/backend/impact-aggregation.md,
 * "Snapshot and Scope" and "GeoID Deduplication").
 */

/** A survey with its resolved concept links and organization, prepared once per run. */
internal class SurveyContext(
  val survey: ImpactSurvey,
  /** The survey's organization, or `null` for personal surveys and unknown organization IDs. */
  val organization: ImpactOrganization?,
  /** Resolved linked concepts by ID. */
  val concepts: Map<String, ImpactConcept>,
) {
  val id: String
    get() = survey.id

  /**
   * Whether the survey counts toward platform-wide outputs (global, country, and cell scopes, and
   * organization-suggested rows): personal surveys always do; organization surveys unless the
   * organization set `exclude_from_platform_aggregates`.
   */
  val isPlatformEligible: Boolean
    get() = organization?.excludeFromPlatformAggregates != true

  /**
   * Organization key for the distinct-organization cell threshold. Personal surveys carry no owner
   * in [ImpactSurvey], so all of them share the key `""` (one "organization").
   */
  val organizationKey: String
    get() = survey.organizationId ?: ""
}

/** One feature of one survey, with everything aggregation needs precomputed. */
internal class FeatureRef(
  val survey: SurveyContext,
  val feature: ImpactFeature,
  /** `g:<GeoID>` for features with a GeoID, `f:<survey ID>/<entity ID>` otherwise. */
  val dedupKey: String,
  /** Geodesic area in hectares (see [featureAreaHa]). */
  val areaHa: Double,
  /** SUM and MEAN values normalized to the concept's unit, by concept ID (linked concepts only). */
  val numbers: Map<String, Double>,
) {
  /**
   * The feature's value for [concept], or `null` when it has none or its survey doesn't link the
   * concept (values for unlinked concepts are ignored).
   */
  fun value(concept: ImpactConcept): ImpactValue? =
    if (concept.id in survey.concepts) feature.values[concept.id] else null
}

/**
 * Resolves [survey]'s links against the survey's library (global concepts plus the survey's
 * organization's), adding a [SkippedLink] to [skipped] for each link that doesn't resolve. Links
 * are a set, so each (survey, concept) pair is reported at most once.
 */
internal fun resolveLinks(
  survey: ImpactSurvey,
  conceptsById: Map<String, ImpactConcept>,
  skipped: MutableList<SkippedLink>,
): Map<String, ImpactConcept> {
  val resolved = LinkedHashMap<String, ImpactConcept>()
  for (id in survey.linkedConceptIds.sorted()) {
    val concept = conceptsById[id]
    val reason =
      when {
        concept == null -> SkippedLinkReason.UNKNOWN
        concept.isDeprecated -> SkippedLinkReason.DEPRECATED
        !concept.isGlobal && concept.ownerOrganizationId != survey.organizationId ->
          SkippedLinkReason.NOT_IN_LIBRARY
        else -> null
      }
    if (reason == null) resolved[id] = concept!! else skipped += SkippedLink(survey.id, id, reason)
  }
  return resolved
}

/**
 * Deduplication key: the trimmed GeoID when present, otherwise a key unique to the feature so
 * features without a GeoID (e.g., tabular rows) are counted individually. The prefixes keep the two
 * key spaces apart.
 */
internal fun dedupKey(surveyId: String, feature: ImpactFeature): String {
  val geoId = feature.geoId?.trim()
  return if (!geoId.isNullOrEmpty()) "g:$geoId" else "f:$surveyId/${feature.entityId}"
}

/**
 * Winner order among features sharing a key: most recently updated first, then the smallest entity
 * ID, then the smallest survey ID, so the winner never depends on input order.
 */
private val WINNER_ORDER: Comparator<FeatureRef> =
  compareByDescending<FeatureRef> { it.feature.updatedAtMillis }
    .thenBy { it.feature.entityId }
    .thenBy { it.survey.id }

/**
 * Deduplicates [refs] within one scope: one winner per [FeatureRef.dedupKey], ordered by key. The
 * winner alone provides area and values; values are never merged across duplicates, even when the
 * winner lacks a value another duplicate has.
 */
internal fun dedup(refs: Iterable<FeatureRef>): List<FeatureRef> {
  val winners = HashMap<String, FeatureRef>()
  for (ref in refs) {
    val current = winners[ref.dedupKey]
    if (current == null || WINNER_ORDER.compare(ref, current) < 0) winners[ref.dedupKey] = ref
  }
  return winners.values.sortedBy { it.dedupKey }
}

/**
 * Geodesic area of [geometry] on the WGS 84 ellipsoid in hectares: a polygon's exterior ring minus
 * its holes (each ring's absolute area, so orientation doesn't matter); points and lines have none.
 */
internal fun geodesicAreaHa(geometry: ImpactGeometry?): Double =
  when (geometry) {
    null,
    is ImpactGeometry.Point,
    is ImpactGeometry.LineString -> 0.0
    is ImpactGeometry.Polygon -> {
      val rings = geometry.rings
      if (rings.isEmpty()) 0.0
      else max(0.0, ringAreaHa(rings[0]) - rings.drop(1).sumOf { ringAreaHa(it) })
    }
  }

private fun ringAreaHa(ring: List<ImpactLatLng>): Double =
  Wgs84.ringAreaHa(DoubleArray(ring.size) { ring[it].lat }, DoubleArray(ring.size) { ring[it].lng })

/**
 * Area of [feature] in hectares: the geodesic area of its geometry, or [ImpactFeature.areaHa] when
 * it has no geometry or the geometry has no area (points, lines). Negative or non-finite
 * precomputed areas are ignored.
 */
internal fun featureAreaHa(feature: ImpactFeature): Double {
  val geodesic = geodesicAreaHa(feature.geometry)
  if (geodesic > 0.0) return geodesic
  return feature.areaHa?.takeIf { it.isFinite() && it >= 0.0 } ?: geodesic
}

/**
 * Representative point of [geometry] for grid cells: a point's position, the mean of a line's
 * vertices, or the mean of a polygon's exterior ring vertices (without a repeated closing vertex).
 * A vertex mean is not an area centroid, but it is cheap, deterministic, and always near small
 * plots. Rings crossing the antimeridian are not supported. Returns `null` for empty geometries.
 */
internal fun representativePoint(geometry: ImpactGeometry?): ImpactLatLng? {
  val point =
    when (geometry) {
      null -> null
      is ImpactGeometry.Point -> geometry.position
      is ImpactGeometry.LineString -> vertexMean(geometry.positions)
      is ImpactGeometry.Polygon -> {
        val ring = geometry.rings.firstOrNull().orEmpty()
        vertexMean(if (ring.size > 1 && ring.first() == ring.last()) ring.dropLast(1) else ring)
      }
    }
  return point?.takeIf { it.lat.isFinite() && it.lng.isFinite() }
}

private fun vertexMean(positions: List<ImpactLatLng>): ImpactLatLng? =
  if (positions.isEmpty()) null
  else
    ImpactLatLng(
      positions.sumOf { it.lat } / positions.size,
      positions.sumOf { it.lng } / positions.size,
    )

/**
 * Country of a deduplicated feature: its own ISO 3166-1 alpha-2 code, else its survey's
 * organization's, in upper case; `null` when neither is known.
 */
internal fun countryOf(ref: FeatureRef): String? =
  (ref.feature.countryCode?.trim()?.takeIf { it.isNotEmpty() }
      ?: ref.survey.organization?.countryCode?.trim()?.takeIf { it.isNotEmpty() })
    ?.uppercase()
