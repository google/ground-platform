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
package org.openforis.collectone.v2.app.data.datasource.remote

import openforis.collectone.v1.survey.SurveyOverview
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.mapper.ProtoSurveyMapper
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem

// Store helpers shared by the sync steps of GrpcSyncDataSource. Each runs inside the caller's
// transaction, so its reads and writes commit together with the step's other changes.

/**
 * Drops the entity and submission sync tokens of a survey that no longer exists locally, so a
 * survey that comes back later starts its data with a full sync instead of a stale delta.
 */
internal fun LocalStoreTransaction.forgetDataTokens(surveyId: String) {
  updateSyncState {
    it.copy(
      syncTokens =
        it.syncTokens.copy(
          entitiesBySurvey = it.syncTokens.entitiesBySurvey - surveyId,
          submissionsBySurvey = it.syncTokens.submissionsBySurvey - surveyId,
        )
    )
  }
}

/** Stores [token] for [key], or removes the entry when [token] is empty (no token: full sync). */
internal fun Map<String, String>.withToken(key: String, token: String): Map<String, String> =
  if (token.isEmpty()) this - key else this + (key to token)

/**
 * IDs of the surveys whose definition or data has mutations in the outbox (in any state). Their
 * local copies are kept until those uploads drain, even when the survey leaves the downloaded set.
 */
internal fun LocalStoreTransaction.surveysWithPendingMutations(): Set<String> =
  outbox()
    .mapNotNull {
      when (val target = it.target) {
        is MutationTarget.Survey -> target.surveyId
        is MutationTarget.Data -> target.surveyId
        else -> null
      }
    }
    .toSet()

/** The survey list entry of a survey that isn't downloaded, from its overview alone. */
internal fun LocalStoreTransaction.catalogPreview(overview: SurveyOverview): SurveyPreviewItem =
  ProtoSurveyMapper.surveyPreviewFromOverviewProto(
      overview = overview,
      mapLayers = mapLayers(overview.survey_id),
    )
    .copy(isDownloaded = false)

internal fun SurveyOverview.isTrashedOrDeleted(): Boolean =
  trash_time != null || state == SurveyState.DELETED
