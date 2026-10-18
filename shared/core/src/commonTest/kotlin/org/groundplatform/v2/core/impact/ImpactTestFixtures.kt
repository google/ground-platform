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

import kotlin.math.abs
import kotlin.math.round
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Builders and assertions shared by the impact aggregation tests. */
internal object Fx {
  fun concept(
    id: String,
    aggregation: ImpactAggregation,
    owner: String = ALL_USERS_ORGANIZATION_ID,
    privacy: ImpactPrivacy = ImpactPrivacy.AGGREGATE_PUBLIC,
    unit: String = "",
    goals: List<String> = emptyList(),
    deprecated: Boolean = false,
  ) = ImpactConcept(id, owner, aggregation, privacy, unit, goals, deprecated)

  fun point(lat: Double, lng: Double) = ImpactGeometry.Point(ImpactLatLng(lat, lng))

  /** A closed, counter-clockwise lat/lng square with its south-west corner at ([lat], [lng]). */
  fun square(lat: Double, lng: Double, size: Double = 0.001) =
    ImpactGeometry.Polygon(listOf(ring(lat, lng, size)))

  fun ring(lat: Double, lng: Double, size: Double): List<ImpactLatLng> =
    listOf(
      ImpactLatLng(lat, lng),
      ImpactLatLng(lat, lng + size),
      ImpactLatLng(lat + size, lng + size),
      ImpactLatLng(lat + size, lng),
      ImpactLatLng(lat, lng),
    )

  fun feature(
    entityId: String,
    geoId: String? = null,
    updatedAt: Long = 0,
    geometry: ImpactGeometry? = null,
    areaHa: Double? = null,
    country: String? = null,
    values: Map<String, String> = emptyMap(),
  ) =
    ImpactFeature(
      entityId = entityId,
      geoId = geoId,
      updatedAtMillis = updatedAt,
      geometry = geometry,
      areaHa = areaHa,
      countryCode = country,
      values = values.mapValues { ImpactValue(it.value) },
    )

  /** A point feature with a precomputed area and text values. */
  fun plot(
    entityId: String,
    geoId: String? = null,
    areaHa: Double = 1.0,
    updatedAt: Long = 0,
    values: Map<String, String> = emptyMap(),
  ) = feature(entityId, geoId, updatedAt, areaHa = areaHa, values = values)

  fun survey(
    id: String,
    organizationId: String?,
    links: Set<String> = emptySet(),
    features: List<ImpactFeature> = emptyList(),
    purposes: List<ImpactPurpose> = emptyList(),
    events: List<ImpactEventRecord> = emptyList(),
    isClosed: Boolean = false,
    outcome: Set<ImpactOutcomeKind>? = null,
    submissionCount: Int = 0,
  ) =
    ImpactSurvey(
      id = id,
      organizationId = organizationId,
      purposes = purposes,
      linkedConceptIds = links,
      features = features,
      submissionCount = submissionCount,
      events = events,
      isClosed = isClosed,
      outcome = outcome,
    )

  fun run(
    surveys: List<ImpactSurvey>,
    concepts: List<ImpactConcept> = emptyList(),
    organizations: List<ImpactOrganization> = emptyList(),
    config: ImpactConfig = ImpactConfig(),
  ) = ImpactAggregator.run(ImpactInput(surveys, concepts, organizations), config)
}

/** The single row matching the filters, failing if there are none or several. */
internal fun ImpactRunResult.row(
  scopeType: ImpactScopeType,
  scopeId: String,
  metric: ImpactMetric,
  conceptId: String = "",
  code: String = "",
  goal: String = "",
): ImpactAggregateRow {
  val matches = rows.filter {
    it.scopeType == scopeType &&
      it.scopeId == scopeId &&
      it.metric == metric &&
      it.conceptId == conceptId &&
      it.code == code &&
      it.goal == goal
  }
  if (matches.size != 1)
    fail("Expected 1 row for $scopeType/$scopeId/$metric/$conceptId/$code/$goal: $matches")
  return matches.single()
}

internal fun ImpactRunResult.hasRow(
  scopeType: ImpactScopeType,
  scopeId: String,
  metric: ImpactMetric,
  conceptId: String = "",
): Boolean = rows.any {
  it.scopeType == scopeType &&
    it.scopeId == scopeId &&
    it.metric == metric &&
    it.conceptId == conceptId
}

internal fun assertNear(
  expected: Double,
  actual: Double,
  tolerance: Double = 1e-9,
  message: String = "",
) {
  assertTrue(
    abs(expected - actual) <= tolerance,
    "$message expected $expected ± $tolerance but was $actual",
  )
}

internal fun assertFeatures(row: ImpactAggregateRow, featureCount: Long, areaHa: Double) {
  assertEquals(featureCount, row.featureCount, "featureCount of $row")
  assertNear(areaHa, row.areaHa, 1e-9, "areaHa of $row")
}

/** Fixed-point rendering with [decimals] digits, identical on every platform. */
internal fun fixed(value: Double, decimals: Int = 4): String {
  var scale = 1L
  repeat(decimals) { scale *= 10 }
  val scaled = round(value * scale).toLong()
  val sign = if (scaled < 0) "-" else ""
  val magnitude = abs(scaled)
  val fraction = (magnitude % scale).toString().padStart(decimals, '0')
  return "$sign${magnitude / scale}.$fraction"
}

/** One line per row, for golden comparisons. */
internal fun render(rows: List<ImpactAggregateRow>): String =
  rows.joinToString("\n") { row ->
    buildList {
        add("${row.scopeType} '${row.scopeId}' ${row.metric}")
        if (row.conceptId.isNotEmpty()) add("concept=${row.conceptId}")
        if (row.code.isNotEmpty()) add("code=${row.code}")
        row.eventKind?.let { add("event=$it") }
        if (row.outcomeKind.isNotEmpty()) add("outcome=${row.outcomeKind}")
        if (row.attributionScore != 0) add("score=${row.attributionScore}")
        if (row.organizationSuggested) add("suggested org=${row.organizationId} goal=${row.goal}")
        add("n=${row.featureCount}")
        add("ha=${fixed(row.areaHa, 2)}")
        add("v=${fixed(row.value)}")
        if (row.suppressed) add("SUPPRESSED")
      }
      .joinToString(" ")
  }
