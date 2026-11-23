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
package org.openforis.collectone.v2.app.domain.repository

import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem

/**
 * Schema metadata for a server-backed entity dataset table: its row count and the union of all
 * property keys (including server-computed enrichment columns such as `whisp_*`) discovered across
 * its entities without loading every row's values into memory.
 */
data class EntityTableSchema(
  val totalCount: Int,
  val propertyKeys: List<String>,
)

/**
 * On-demand remote reads for entity tables, entity details, and full dataset exports when the web
 * console uses lightweight sync instead of holding every row's full property map in memory.
 */
interface EntityTableRepository {
  /** Whether the active host uses lazy on-scroll property loading instead of full sync. */
  val isLazyLoadingEnabled: Boolean

  /** Discovers the total row count and full set of property keys for one dataset. */
  suspend fun fetchSchema(surveyId: String, datasetId: String): EntityTableSchema

  /**
   * Fetches only the requested [propertyKeys] (or all properties when empty) for the given
   * [entityIds] of [datasetId] in [surveyId]. Returns a map of `entityId -> (propertyKey ->
   * value)`.
   */
  suspend fun fetchEntityProperties(
    surveyId: String,
    datasetId: String,
    entityIds: List<String>,
    propertyKeys: List<String>,
  ): Map<String, Map<String, String>>

  /** Fetches the full property map of a single entity (for details inspection). */
  suspend fun fetchEntityProperties(
    surveyId: String,
    datasetId: String,
    entityId: String,
  ): Map<String, String>?

  /**
   * Fetches every live entity of [datasetId] in [surveyId] with full properties (for CSV, GeoJSON,
   * and KML exports).
   */
  suspend fun fetchAllEntitiesForExport(
    surveyId: String,
    datasetId: String,
  ): List<GeospatialEntityItem>
}
