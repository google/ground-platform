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

import io.grpc.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import openforis.collectone.v1.survey.GeoDataServiceServer
import openforis.collectone.v1.survey.GetOverlayTileUrlRequest
import openforis.collectone.v1.survey.GetOverlayTileUrlResponse
import openforis.collectone.v1.survey.GetPlotStatisticsRequest
import openforis.collectone.v1.survey.GetPlotStatisticsResponse
import openforis.collectone.v1.survey.GetPlotTimeSeriesRequest
import openforis.collectone.v1.survey.GetPlotTimeSeriesResponse
import openforis.collectone.v1.survey.GetSceneTileUrlRequest
import openforis.collectone.v1.survey.GetSceneTileUrlResponse
import openforis.collectone.v1.survey.PlotRef
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyState
import openforis.collectone.v1.survey.TimeSeriesSource
import org.openforis.collectone.v2.core.access.SurveyRole
import org.openforis.collectone.v2.core.impact.ALL_USERS_ORGANIZATION_ID
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.auth.currentCaller
import org.openforis.collectone.v2.server.data.UserDataReader
import org.openforis.collectone.v2.server.data.UserDataStore
import org.openforis.collectone.v2.server.geo.EarthEngineTileClient
import org.openforis.collectone.v2.server.geo.ImageryAnalytics
import org.openforis.collectone.v2.server.geo.PlotGeometry
import org.openforis.collectone.v2.server.geo.SceneKind

/** `GeoDataService`: imagery tile URLs and Earth Engine plot analytics for a survey's viewers. */
internal class GeoDataServiceImpl(
  private val store: UserDataStore,
  private val earthEngineTileClient: EarthEngineTileClient,
  private val imageryAnalytics: ImageryAnalytics,
) : GeoDataServiceServer {

  override suspend fun GetOverlayTileUrl(
    request: GetOverlayTileUrlRequest
  ): GetOverlayTileUrlResponse {
    if (request.survey_id.isBlank() || request.imagery_source_id.isBlank()) {
      throw Status.INVALID_ARGUMENT.withDescription(
          "survey_id and imagery_source_id must not be blank"
        )
        .asRuntimeException()
    }
    val caller = currentCaller()
    // Resolve the imagery source inside the read snapshot, but call Earth Engine outside it: the
    // store may re-run read blocks, and the snapshot shouldn't stay open across a network call.
    val source = store.read { r ->
      val survey = requireSurveyViewer(r, request.survey_id, caller)
      val allUsersOrg = r.organization(ALL_USERS_ORGANIZATION_ID)
      val surveyOrg =
        if (survey.organization_id.isNotBlank()) r.organization(survey.organization_id) else null

      allUsersOrg?.imagery_sources?.firstOrNull {
        it.imagery_source_id == request.imagery_source_id
      }
        ?: surveyOrg?.imagery_sources?.firstOrNull {
          it.imagery_source_id == request.imagery_source_id
        }
        ?: throw Status.NOT_FOUND.withDescription(
            "Unknown imagery_source_id: ${request.imagery_source_id}"
          )
          .asRuntimeException()
    }

    val eeExpr = source.earth_engine?.expression
    val xyz = source.xyz_tiles
    val (urlTemplate, ttlSeconds) =
      when {
        eeExpr != null -> {
          // `resolve` may block on the Earth Engine REST API on a cache miss.
          val resolved = withContext(Dispatchers.IO) { earthEngineTileClient.resolve(eeExpr) }
          resolved.urlTemplate to resolved.ttlSeconds
        }
        xyz != null -> xyz.url_template to EarthEngineTileClient.DEFAULT_TTL_SECONDS
        else ->
          throw Status.INVALID_ARGUMENT.withDescription(
              "Imagery source ${request.imagery_source_id} has no valid tile configuration"
            )
            .asRuntimeException()
      }
    return GetOverlayTileUrlResponse(
      url_template = urlTemplate,
      ttl = com.squareup.wire.durationOfSeconds(ttlSeconds, 0L),
    )
  }

  override suspend fun GetPlotStatistics(
    request: GetPlotStatisticsRequest
  ): GetPlotStatisticsResponse {
    val (callerKey, plot) = resolvePlot(request.survey_id, request.plot)
    return imageryAnalytics.statistics(callerKey, plot)
  }

  override suspend fun GetPlotTimeSeries(
    request: GetPlotTimeSeriesRequest
  ): GetPlotTimeSeriesResponse {
    if (request.source == TimeSeriesSource.TIME_SERIES_SOURCE_UNSPECIFIED) {
      throw Status.INVALID_ARGUMENT.withDescription("source must be set").asRuntimeException()
    }
    val (callerKey, plot) = resolvePlot(request.survey_id, request.plot)
    return imageryAnalytics.timeSeries(callerKey, plot, request.source, request.window)
  }

  override suspend fun GetSceneTileUrl(request: GetSceneTileUrlRequest): GetSceneTileUrlResponse {
    requireSurveyId(request.survey_id)
    SceneKind.require(request.scene_id)
    val caller = currentCaller()
    store.read { r -> requireSurveyViewer(r, request.survey_id, caller) }
    return imageryAnalytics.sceneTileUrl(
      analyticsCallerKey(caller),
      request.scene_id,
      request.stretch,
    )
  }

  /**
   * Checks that [caller] may view [surveyId] (at least `VIEWER`) and resolves [plot] to the
   * geometry to analyze: the stored point or polygon of the map feature it names, else the geometry
   * the client drew.
   */
  private suspend fun resolvePlot(surveyId: String, plot: PlotRef?): Pair<String, PlotGeometry> {
    requireSurveyId(surveyId)
    val caller = currentCaller()
    val geometry = store.read { r ->
      requireSurveyViewer(r, surveyId, caller)
      val entity =
        plot
          ?.entity_id
          ?.takeIf { it.isNotBlank() }
          ?.let { r.entity(surveyId, it) }
          ?.takeUnless { it.is_deleted }
      PlotGeometry.of(plot, entity)
    }
    return analyticsCallerKey(caller) to geometry
  }

  private fun requireSurveyId(surveyId: String) {
    if (surveyId.isBlank()) {
      throw Status.INVALID_ARGUMENT.withDescription("survey_id must not be blank")
        .asRuntimeException()
    }
  }

  /** Rate-limiting key of [caller]; signed-out viewers of public surveys share one budget. */
  private fun analyticsCallerKey(caller: Caller?): String = caller?.userId ?: "anonymous"

  /** The live survey [surveyId], if [caller] has at least `VIEWER` access to it. */
  private fun requireSurveyViewer(r: UserDataReader, surveyId: String, caller: Caller?): SurveyDef {
    val survey = r.survey(surveyId)
    if (survey == null || survey.trash_time != null || survey.state == SurveyState.DELETED) {
      throw Status.NOT_FOUND.withDescription("Survey not found: $surveyId").asRuntimeException()
    }
    val access = Access(r, caller)
    val role = access.surveyRole(survey, r.acl(surveyId))
    if (!role.atLeast(SurveyRole.VIEWER)) {
      throw Access.denied("survey $surveyId")
    }
    return survey
  }
}
