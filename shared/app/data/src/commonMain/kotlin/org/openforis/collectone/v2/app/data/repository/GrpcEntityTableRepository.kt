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

import openforis.collectone.v1.data.GetEntityRequest
import openforis.collectone.v1.data.ListEntitiesRequest
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.mapper.ProtoRecordMapper
import org.openforis.collectone.v2.app.data.mapper.ProtoSurveyMapper
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.repository.EntityTableRepository
import org.openforis.collectone.v2.app.domain.repository.EntityTableSchema
import org.openforis.collectone.v2.core.sync.EntityFieldMask

/**
 * [EntityTableRepository] backed by [CollectOneGrpcClient] (`DataService.ListEntities` and
 * `DataService.GetEntity`). Used by the web console's virtualized tables, details inspector, and
 * exports to lazy-load entity properties on demand instead of holding full tables in memory.
 */
class GrpcEntityTableRepository(
  private val grpcClient: CollectOneGrpcClient,
  private val store: LocalStore,
  override val isLazyLoadingEnabled: Boolean = true,
) : EntityTableRepository {
  override suspend fun fetchSchema(surveyId: String, datasetId: String): EntityTableSchema {
    val response =
      grpcClient.listEntities(
        ListEntitiesRequest(
          survey_id = surveyId,
          dataset_id = datasetId,
          page_size = 1,
          field_mask = EntityFieldMask.SCHEMA_ONLY_MASK,
          include_property_keys = true,
        )
      )
    return EntityTableSchema(
      totalCount = response.total_size,
      propertyKeys = response.property_keys,
    )
  }

  override suspend fun fetchEntityProperties(
    surveyId: String,
    datasetId: String,
    entityIds: List<String>,
    propertyKeys: List<String>,
  ): Map<String, Map<String, String>> {
    if (entityIds.isEmpty()) return emptyMap()
    val response =
      grpcClient.listEntities(
        ListEntitiesRequest(
          survey_id = surveyId,
          dataset_id = datasetId,
          entity_ids = entityIds,
          field_mask = EntityFieldMask.forTableTile(propertyKeys),
        )
      )
    return response.entities.associate { entity ->
      entity.entity_id to entity.properties.mapValues { (_, v) -> v.string_value.orEmpty() }
    }
  }

  override suspend fun fetchEntityProperties(
    surveyId: String,
    datasetId: String,
    entityId: String,
  ): Map<String, String>? {
    if (entityId.isBlank()) return null
    val response =
      grpcClient.getEntity(
        GetEntityRequest(survey_id = surveyId, dataset_id = datasetId, entity_id = entityId)
      )
    val entity = response.entity ?: return null
    if (entity.entity_id.isBlank()) return null
    return entity.properties.mapValues { (_, v) -> v.string_value.orEmpty() }
  }

  override suspend fun fetchAllEntitiesForExport(
    surveyId: String,
    datasetId: String,
  ): List<GeospatialEntityItem> {
    val (submissionsByEntity, generatedDatasetIds) =
      store.transaction {
        val subs = entities(surveyId).associate { it.id to it.submissions }
        val gen = ProtoSurveyMapper.generatedDatasetIds(surveyBase(surveyId)?.saved)
        subs to gen
      }
    val all = mutableListOf<GeospatialEntityItem>()
    var pageToken = ""
    while (true) {
      val response =
        grpcClient.listEntities(
          ListEntitiesRequest(
            survey_id = surveyId,
            dataset_id = datasetId,
            page_size = EXPORT_PAGE_SIZE,
            page_token = pageToken,
          )
        )
      response.entities.forEach { proto ->
        all +=
          ProtoRecordMapper.entityFromProto(
            proto = proto,
            submissions = submissionsByEntity[proto.entity_id].orEmpty(),
            isGenerated = proto.dataset_id in generatedDatasetIds,
          )
      }
      if (response.next_page_token.isEmpty()) break
      pageToken = response.next_page_token
    }
    return all
  }

  private companion object {
    const val EXPORT_PAGE_SIZE = 500
  }
}
