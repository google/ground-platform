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

import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.survey.MapConfig
import openforis.collectone.v1.survey.SurveyArea
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyState
import openforis.collectone.v1.userdata.SurveySeed
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.server.data.UserDataStore
import org.openforis.collectone.v2.server.data.UserDataWriter
import org.openforis.collectone.v2.server.messaging.SurveyTopicNotifier

/**
 * Gives the sample surveys of an already loaded database the survey areas (and relocated sample
 * features) the seed gained later.
 *
 * Clients derive a survey's coordinates from its area, so sample surveys stored before their area
 * was added to the seed would show none. For every survey in the seed with a
 * `map_config.survey_area`, [run] sets that area on the stored survey only when neither its saved
 * revision nor its working copy has one, and relocates any pre-relocation sample entities or
 * submissions whose coordinates still lie outside that seed area: it never replaces an area someone
 * chose, ignores surveys that aren't stored or were purged, and does nothing on later runs. Each
 * backfilled survey gets the next shared `record_version` (saved revision, working copy, and ACL)
 * so downloaded copies refresh, as after any other survey write.
 */
internal class SeedSurveyAreaBackfill(
  private val store: UserDataStore,
  private val topicNotifier: SurveyTopicNotifier,
) {
  /** Backfills the survey areas of [seed]'s surveys; returns the IDs of the surveys it changed. */
  suspend fun run(seed: UserDataSeed): List<String> {
    val candidateSeeds = seed.surveys.filter { it.survey?.map_config?.survey_area != null }
    if (candidateSeeds.isEmpty()) return emptyList()
    val backfilled =
      store
        .write { tx ->
          candidateSeeds.mapNotNull { surveySeed -> tx.backfill(surveySeed) }
        }
        .value
    for ((surveyId, recordVersion) in backfilled) {
      topicNotifier.notifySurveyDefChanged(surveyId, recordVersion)
    }
    return backfilled.map { it.first }
  }

  /**
   * Sets `survey_area` (and relocates pre-relocation seed features) on [surveySeed]'s survey if
   * needed; returns its new record version if it changed anything.
   */
  private fun UserDataWriter.backfill(surveySeed: SurveySeed): Pair<String, Long>? {
    val seedSurvey = surveySeed.survey ?: return null
    val area = seedSurvey.map_config?.survey_area ?: return null
    val surveyId = seedSurvey.survey_id
    val saved = survey(surveyId)?.takeIf { it.state != SurveyState.DELETED } ?: return null
    val workingCopy = workingCopy(surveyId)
    val missingArea = !saved.hasSurveyArea && workingCopy?.hasSurveyArea != true
    val matchesSeedArea = missingArea || saved.map_config?.survey_area == area
    if (!matchesSeedArea) return null

    var relocatedData = false
    for (seedEntity in surveySeed.entities) {
      val storedEntity = entity(surveyId, seedEntity.entity_id) ?: continue
      if (storedEntity.is_deleted) continue
      if (
        storedEntity.geometry != seedEntity.geometry &&
          !isInsideArea(storedEntity, area) &&
          isInsideArea(seedEntity, area)
      ) {
        putEntity(seedEntity.copy(revision = storedEntity.revision + 1L))
        relocatedData = true
      }
    }
    for (seedSub in surveySeed.submissions) {
      val storedSub = submission(surveyId, seedSub.submission_id) ?: continue
      if (storedSub.is_deleted) continue
      if (seedSub.location != null && storedSub.location != seedSub.location) {
        putSubmission(seedSub.copy(revision = storedSub.revision + 1L))
        relocatedData = true
      }
    }

    if (!missingArea && !relocatedData) return null

    val recordVersion = nextSurveyRecordVersion(surveyId)
    val updatedSaved =
      (if (missingArea) saved.withSurveyArea(area) else saved).copy(record_version = recordVersion)
    putSurvey(updatedSaved)
    workingCopy?.let {
      putWorkingCopy(
        surveyId,
        (if (missingArea) it.withSurveyArea(area) else it).copy(record_version = recordVersion),
      )
    }
    acl(surveyId)?.let { putAcl(it.copy(record_version = recordVersion)) }
    return surveyId to recordVersion
  }

  private fun isInsideArea(entity: EntityRecord, area: SurveyArea): Boolean {
    val pos = GeometryProtos.of(entity)?.let(GeometryProtos::representativePosition) ?: return false
    val rings =
      area.boundary
        ?.let {
          it.polygon?.rings.orEmpty() +
            it.multi_polygon?.polygons.orEmpty().flatMap { poly -> poly.rings }
        }
        .orEmpty()
    val positions = rings.flatMap { it.positions }
    if (positions.isEmpty()) return false
    val minLat = positions.minOf { it.latitude }
    val maxLat = positions.maxOf { it.latitude }
    val minLng = positions.minOf { it.longitude }
    val maxLng = positions.maxOf { it.longitude }
    return pos.latitude in minLat..maxLat && pos.longitude in minLng..maxLng
  }

  private val SurveyDef.hasSurveyArea: Boolean
    get() =
      map_config?.survey_area?.boundary?.let(GeometryProtos::vertexCount)?.let { it > 0 } == true

  private fun SurveyDef.withSurveyArea(area: SurveyArea): SurveyDef =
    copy(map_config = (map_config ?: MapConfig()).copy(survey_area = area))
}
