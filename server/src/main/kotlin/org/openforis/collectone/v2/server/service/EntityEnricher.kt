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
package org.openforis.collectone.v2.server.service

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.server.data.UserDataStore
import org.openforis.collectone.v2.server.data.UserDataWriter
import org.openforis.collectone.v2.server.geo.NoopWhispClient
import org.openforis.collectone.v2.server.geo.WhispClient
import org.openforis.collectone.v2.server.messaging.NoopSurveyTopicNotifier
import org.openforis.collectone.v2.server.messaging.SurveyTopicNotifier
import org.slf4j.LoggerFactory

/**
 * Geospatial entity whose geometry was created or changed by a committed `MutateData` request and
 * is eligible for asynchronous external enrichment.
 */
data class EntityEnrichmentCandidate(
  val surveyId: String,
  val entityId: String,
  val geometry: Geometry,
  val dataEpoch: String = "",
)

/**
 * Enriches geospatial entities with external properties (such as Open Foris Whisp indicators) after
 * `MutateData` creates an entity with geometry or modifies an entity's geometry.
 */
interface EntityEnricher : AutoCloseable {
  fun enrichEntities(candidates: List<EntityEnrichmentCandidate>)

  fun reset() {}

  override fun close() {}
}

/** Default no-op [EntityEnricher] used when external enrichment is not configured. */
object NoopEntityEnricher : EntityEnricher {
  override fun enrichEntities(candidates: List<EntityEnrichmentCandidate>) = Unit
}

/**
 * In-process [EntityEnricher] that queries [whispClient] outside the Spanner transaction for each
 * [EntityEnrichmentCandidate], merges the returned properties (prefixed with [prefix]) into the
 * entity's `properties` map in a follow-up transaction (incrementing `revision`), and notifies
 * [topicNotifier] on `survey_<survey_id>_entities` so connected clients pull the enriched record
 * and include the Whisp properties in CSV, GeoJSON, and KML exports.
 */
class WhispEntityEnricher(
  private val store: UserDataStore,
  private val whispClient: WhispClient = NoopWhispClient,
  private val topicNotifier: SurveyTopicNotifier = NoopSurveyTopicNotifier,
  private val prefix: String = DEFAULT_PREFIX,
  private val async: Boolean = true,
  private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
    Thread(r, "whisp-entity-enricher").apply { isDaemon = true }
  },
  private val ownsExecutor: Boolean = true,
) : EntityEnricher {
  private val logger = LoggerFactory.getLogger(WhispEntityEnricher::class.java)

  override fun enrichEntities(candidates: List<EntityEnrichmentCandidate>) {
    if (candidates.isEmpty()) return
    val currentEpoch = store.dataEpoch
    val stamped = candidates.map { candidate ->
      if (candidate.dataEpoch.isEmpty()) candidate.copy(dataEpoch = currentEpoch) else candidate
    }
    dispatch { runBlocking { deliverEnrichments(stamped) } }
  }

  /** Synchronously enriches [candidates], returning the entity IDs whose properties changed. */
  suspend fun deliverEnrichments(candidates: List<EntityEnrichmentCandidate>): List<String> =
    candidates.mapNotNull { candidate ->
      if (enrichCandidate(candidate)) candidate.entityId else null
    }

  override fun close() {
    if (ownsExecutor) {
      executor.shutdownNow()
    }
  }

  private fun dispatch(block: () -> Unit) {
    if (async) {
      executor.execute { runLoggingFailures(block) }
    } else {
      runLoggingFailures(block)
    }
  }

  private fun runLoggingFailures(block: () -> Unit) {
    runCatching(block).onFailure { e ->
      logger.error("Failed to enrich geospatial entities via Whisp", e)
    }
  }

  private suspend fun enrichCandidate(candidate: EntityEnrichmentCandidate): Boolean {
    if (candidate.dataEpoch.isNotEmpty() && store.dataEpoch != candidate.dataEpoch) return false
    val rawProps = whispClient.fetchProperties(candidate.geometry)
    if (rawProps.isEmpty()) return false
    if (candidate.dataEpoch.isNotEmpty() && store.dataEpoch != candidate.dataEpoch) return false
    val committed = store.write { tx -> applyEnrichmentInTx(tx, candidate, rawProps) }
    if (committed.value) {
      topicNotifier.notifySurveyEntitiesChanged(candidate.surveyId, committed.commitTimestamp)
    }
    return committed.value
  }

  private fun applyEnrichmentInTx(
    tx: UserDataWriter,
    candidate: EntityEnrichmentCandidate,
    rawProps: Map<String, String>,
  ): Boolean {
    val survey = tx.survey(candidate.surveyId)
    val surveyActive =
      survey != null && survey.state != SurveyState.DELETED && survey.trash_time == null
    val existing = if (surveyActive) tx.entity(candidate.surveyId, candidate.entityId) else null
    val target =
      existing?.takeIf { !it.is_deleted && GeometryProtos.of(it) == candidate.geometry }
        ?: return false
    val mergedProperties = mergePrefixedProperties(target.properties, prefix, rawProps)
    if (mergedProperties == target.properties) return false
    tx.putEntity(target.copy(revision = target.revision + 1L, properties = mergedProperties))
    return true
  }

  companion object {
    const val DEFAULT_PREFIX = "whisp_"

    /**
     * Replaces all existing keys starting with [prefix] in [existing] with [generated] (prefixing
     * each generated key with [prefix]), preserving all non-[prefix] entity properties.
     */
    internal fun mergePrefixedProperties(
      existing: Map<String, TypedValue>,
      prefix: String,
      generated: Map<String, String>,
    ): Map<String, TypedValue> {
      val retained = existing.filterKeys { !it.startsWith(prefix) }
      val added =
        generated.entries.associate { (k, v) ->
          val key = if (k.startsWith(prefix)) k else "$prefix$k"
          key to TypedValue(string_value = v)
        }
      return retained + added
    }
  }
}
