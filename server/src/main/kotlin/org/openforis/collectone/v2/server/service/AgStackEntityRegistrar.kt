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
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.geo.geoid.AgStackGeoId
import org.openforis.collectone.v2.server.data.UserDataStore
import org.openforis.collectone.v2.server.data.UserDataWriter
import org.openforis.collectone.v2.server.geo.AgStackRegistrationResult
import org.openforis.collectone.v2.server.geo.AgStackRegistryClient
import org.openforis.collectone.v2.server.geo.NoopAgStackRegistryClient
import org.openforis.collectone.v2.server.messaging.NoopSurveyTopicNotifier
import org.openforis.collectone.v2.server.messaging.SurveyTopicNotifier
import org.slf4j.LoggerFactory

/**
 * Geospatial entity whose geometry was created or changed by a committed `MutateData` request and
 * should be registered with the AgStack Asset Registry.
 */
data class GeoIdRegistrationCandidate(
  val surveyId: String,
  val datasetId: String,
  val entityId: String,
  val geometry: Geometry,
  val provisionalGeoId: String = "",
  val dataEpoch: String = "",
)

/**
 * Registers newly created or geometry-modified geospatial entities with the AgStack Asset Registry
 * outside the `MutateData` write transaction and reconciles the stored `EntityRecord.geo_id` when
 * the registry promotes a polygon to its level-20 fallback or matches an existing boundary.
 */
interface GeoIdRegistrar : AutoCloseable {
  fun registerEntities(candidates: List<GeoIdRegistrationCandidate>)

  fun reset() {}

  override fun close() {}
}

/** Default no-op [GeoIdRegistrar] used when no external AgStack Asset Registry is configured. */
object NoopGeoIdRegistrar : GeoIdRegistrar {
  override fun registerEntities(candidates: List<GeoIdRegistrationCandidate>) = Unit
}

/**
 * In-process [GeoIdRegistrar] that registers each [GeoIdRegistrationCandidate] with
 * [registryClient] outside the Spanner transaction, validates the returned GeoID, and updates
 * `EntityRecord.geo_id` (incrementing `revision`) and notifies [topicNotifier] whenever the
 * authoritative registered GeoID differs from the provisional candidate.
 */
class AgStackEntityRegistrar(
  private val store: UserDataStore,
  private val registryClient: AgStackRegistryClient = NoopAgStackRegistryClient,
  private val topicNotifier: SurveyTopicNotifier = NoopSurveyTopicNotifier,
  private val async: Boolean = true,
  private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
    Thread(r, "agstack-entity-registrar").apply { isDaemon = true }
  },
  private val ownsExecutor: Boolean = true,
) : GeoIdRegistrar {
  private val logger = LoggerFactory.getLogger(AgStackEntityRegistrar::class.java)

  override fun registerEntities(candidates: List<GeoIdRegistrationCandidate>) {
    if (candidates.isEmpty()) return
    val currentEpoch = store.dataEpoch
    val stamped = candidates.map { candidate ->
      if (candidate.dataEpoch.isEmpty()) candidate.copy(dataEpoch = currentEpoch) else candidate
    }
    dispatch { runBlocking { deliverRegistrations(stamped) } }
  }

  /**
   * Synchronously registers [candidates] with [registryClient], returning the entity IDs whose
   * stored `geo_id` was updated in Spanner.
   */
  suspend fun deliverRegistrations(candidates: List<GeoIdRegistrationCandidate>): List<String> =
    candidates.mapNotNull { candidate ->
      if (registerCandidate(candidate)) candidate.entityId else null
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
      logger.error("Failed to register geospatial entities with AgStack Asset Registry", e)
    }
  }

  private suspend fun registerCandidate(candidate: GeoIdRegistrationCandidate): Boolean {
    if (candidate.dataEpoch.isNotEmpty() && store.dataEpoch != candidate.dataEpoch) return false
    val registration =
      registryClient.registerGeometry(candidate.geometry, candidate.entityId) ?: return false
    if (!isValidRegistrationResult(registration, candidate.geometry)) {
      logger.warn(
        "Ignoring invalid AgStack registration GeoID '{}' for entity {}",
        registration.geoId,
        candidate.entityId,
      )
      return false
    }
    if (candidate.dataEpoch.isNotEmpty() && store.dataEpoch != candidate.dataEpoch) return false
    val committed = store.write { tx -> applyRegistrationInTx(tx, candidate, registration) }
    if (committed.value) {
      topicNotifier.notifySurveyEntitiesChanged(candidate.surveyId, committed.commitTimestamp)
    }
    return committed.value
  }

  private fun applyRegistrationInTx(
    tx: UserDataWriter,
    candidate: GeoIdRegistrationCandidate,
    registration: AgStackRegistrationResult,
  ): Boolean {
    val survey = tx.survey(candidate.surveyId)
    val surveyActive =
      survey != null && survey.state != SurveyState.DELETED && survey.trash_time == null
    val existing = if (surveyActive) tx.entity(candidate.surveyId, candidate.entityId) else null
    val target =
      existing?.takeIf { !it.is_deleted && GeometryProtos.of(it) == candidate.geometry }
        ?: return false
    if (target.geo_id == registration.geoId) return false
    tx.putEntity(target.copy(revision = target.revision + 1L, geo_id = registration.geoId))
    return true
  }

  companion object {
    internal fun isValidRegistrationResult(
      registration: AgStackRegistrationResult,
      geometry: Geometry,
    ): Boolean {
      if (!AgStackGeoId.isValidFormat(registration.geoId)) return false
      return registration.isDuplicate || AgStackGeoId.matchesGeometry(registration.geoId, geometry)
    }
  }
}
