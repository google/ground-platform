/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.data.repository

import openforis.collectone.v1.survey.GetOverlayTileUrlRequest
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.data.error.domainResultOf
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.repository.OverlayTileUrl
import org.openforis.collectone.v2.app.domain.repository.OverlayTileUrlRepository

/** [OverlayTileUrlRepository] backed by the server's `GeoDataService.GetOverlayTileUrl`. */
class GrpcOverlayTileUrlRepository(private val client: CollectOneGrpcClient) :
  OverlayTileUrlRepository {
  override suspend fun resolve(
    surveyId: String,
    imagerySourceId: String,
  ): DomainResult<OverlayTileUrl> = domainResultOf {
    val response =
      client.getOverlayTileUrl(
        GetOverlayTileUrlRequest(survey_id = surveyId, imagery_source_id = imagerySourceId)
      )
    OverlayTileUrl(
      urlTemplate = response.url_template,
      ttlSeconds = response.ttl?.getSeconds() ?: DEFAULT_TTL_SECONDS,
    )
  }

  companion object {
    /** How long a tile URL is assumed to stay valid when the server doesn't say: two hours. */
    const val DEFAULT_TTL_SECONDS: Long = 2 * 60 * 60
  }
}
