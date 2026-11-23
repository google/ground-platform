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
package org.openforis.collectone.v2.app.ui.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.repository.OverlayTileUrlRepository

/**
 * Keeps [SurveyMapSession.resolvedOverlayTileUrls] filled for the enabled Earth Engine imagery
 * sources of the active survey's organizations.
 *
 * Earth Engine tile URLs are computed by the server and expire, so each enabled source gets its own
 * refresh loop in [scope]: it resolves the URL, then re-resolves when the returned TTL runs out (at
 * least every [MIN_REFRESH_SECONDS]); a failure retries after [RETRY_SECONDS] and leaves the layer
 * hidden meanwhile. Disabling a source cancels its loop and drops its URL; switching surveys
 * cancels every loop and also drops the pinned scene, which belongs to the previous survey.
 *
 * Owned by `SurveyMapViewModel`; [jobs] and [lastActiveSurveyId] are only touched from the single
 * collector started by [start], so they need no synchronization.
 */
internal class OverlayTileUrlRefresher(
  private val scope: CoroutineScope,
  private val overlayTileUrls: OverlayTileUrlRepository,
  private val session: MutableStateFlow<SurveyMapSession>,
) {
  private val jobs = mutableMapOf<String, Job>()
  private var lastActiveSurveyId: String = ""

  /** Starts following [data] and the session's enabled sources until [scope] is cancelled. */
  fun start(data: StateFlow<SurveyMapData>) {
    scope.launch {
      combine(
          data.map { it.activeSurveyId to it.organizations },
          session.map { it.enabledImagerySourceIds },
        ) { (activeSurveyId, orgs), enabledIds ->
          Triple(activeSurveyId, orgs, enabledIds)
        }
        .collect { (activeSurveyId, orgs, enabledIds) ->
          onChange(activeSurveyId, orgs, enabledIds, data.value)
        }
    }
  }

  private fun onChange(
    activeSurveyId: String,
    orgs: List<Organization>,
    enabledIds: Set<String>,
    data: SurveyMapData,
  ) {
    if (activeSurveyId != lastActiveSurveyId) {
      lastActiveSurveyId = activeSurveyId
      jobs.values.forEach { it.cancel() }
      jobs.clear()
      session.update { it.copy(resolvedOverlayTileUrls = emptyMap(), pinnedSceneOverlay = null) }
    }
    if (activeSurveyId.isBlank()) return

    val activeEeSources =
      imagerySourcesOf(orgs, data.activeSurvey?.organizationId).filter {
        it.type == ImagerySourceType.EARTH_ENGINE && it.id in enabledIds
      }
    val activeEeIds = activeEeSources.map { it.id }.toSet()

    (jobs.keys - activeEeIds).forEach { id ->
      jobs.remove(id)?.cancel()
      session.update { s -> s.copy(resolvedOverlayTileUrls = s.resolvedOverlayTileUrls - id) }
    }
    for (source in activeEeSources) {
      if (source.id !in jobs) jobs[source.id] = launchRefreshLoop(activeSurveyId, source.id)
    }
  }

  private fun launchRefreshLoop(surveyId: String, sourceId: String): Job = scope.launch {
    while (isActive) {
      when (val resolved = overlayTileUrls.resolve(surveyId, sourceId)) {
        is DomainResult.Success -> {
          val (urlTemplate, ttlSeconds) = resolved.value
          session.update { s ->
            s.copy(resolvedOverlayTileUrls = s.resolvedOverlayTileUrls + (sourceId to urlTemplate))
          }
          delay(ttlSeconds.coerceAtLeast(MIN_REFRESH_SECONDS) * MILLIS_PER_SECOND)
        }
        is DomainResult.Failure -> delay(RETRY_SECONDS * MILLIS_PER_SECOND)
      }
    }
  }

  private companion object {
    const val MIN_REFRESH_SECONDS = 60L
    const val RETRY_SECONDS = 30L
    const val MILLIS_PER_SECOND = 1000L
  }
}

/**
 * The imagery sources whose tile URLs may need resolving: those of the first organization that is
 * "All users" or synthetic, then those of the survey's organization [surveyOrganizationId].
 */
private fun imagerySourcesOf(
  orgs: List<Organization>,
  surveyOrganizationId: String?,
): List<ImagerySource> {
  val allUsersOrg = orgs.firstOrNull { it.isAllUsers }
  val surveyOrg = surveyOrganizationId?.let { id -> orgs.firstOrNull { it.id == id } }
  return allUsersOrg?.imagerySources.orEmpty() + surveyOrg?.imagerySources.orEmpty()
}
