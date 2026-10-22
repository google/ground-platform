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
package org.openforis.collectone.v2.app.data.repository

import openforis.collectone.v1.survey.BatchGetSurveyOverviewsRequest
import openforis.collectone.v1.survey.SearchPublicSurveysRequest
import openforis.collectone.v1.survey.SurveyOverview
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.remote.GrpcProtoMapper
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.repository.SurveyDirectoryRepository

/**
 * [SurveyDirectoryRepository] backed by the user data service ([grpcClient]). Downloads are written
 * to [store] directly, not through the outbox: the downloaded set is device-local, and the survey
 * itself isn't a local edit.
 */
class SurveyDirectoryRepositoryImpl(
  private val store: LocalStore,
  private val grpcClient: CollectOneGrpcClient,
) : SurveyDirectoryRepository {
  override suspend fun searchPublicSurveys(query: String): List<SurveyPreviewItem> =
    grpcClient
      .searchPublicSurveys(SearchPublicSurveysRequest(query = query, page_size = PAGE_SIZE))
      .overviews
      .filter { it.isListable() }
      .map(::preview)

  override suspend fun lookUpSurvey(surveyId: String): SurveyPreviewItem? =
    grpcClient
      .batchGetSurveyOverviews(BatchGetSurveyOverviewsRequest(survey_ids = listOf(surveyId)))
      .results
      .firstOrNull()
      ?.overview
      ?.takeIf { it.isListable() }
      ?.let(::preview)

  override suspend fun downloadSurvey(survey: SurveyPreviewItem) {
    store.transaction {
      updateSyncState { it.copy(downloadedSurveyIds = it.downloadedSurveyIds + survey.id) }
      if (survey(survey.id) == null) upsertSurvey(survey.copy(isDownloaded = true))
      else updateSurvey(survey.id) { it.copy(isDownloaded = true) }
    }
  }

  private fun preview(overview: SurveyOverview): SurveyPreviewItem =
    GrpcProtoMapper.surveyPreviewFromOverviewProto(overview).copy(isDownloaded = false)

  private fun SurveyOverview.isListable(): Boolean =
    state != SurveyState.DELETED && trash_time == null

  private companion object {
    /** Public search results shown at once. */
    const val PAGE_SIZE = 20
  }
}
