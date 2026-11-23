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
package org.openforis.collectone.v2.web.domain.model.editor

import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.core.geo.io.GeoFileReader
import org.openforis.collectone.v2.core.geo.io.GeoReadResult

/** A file read for import as a Data table, with its plan (`null` if nothing can be imported). */
data class DataTableImportPreview(
  val fileName: String,
  val text: String,
  val plan: DataTableImportPlan?,
  val notes: List<DomainMessage>,
) {
  /** Re-reads the file with ambiguous numeric dates in [order]. */
  fun withDateOrder(order: DateOrder): DataTableImportPreview = of(fileName, text, order)

  companion object {
    fun of(
      fileName: String,
      text: String,
      dateOrder: DateOrder = DateOrder.DAY_FIRST,
    ): DataTableImportPreview {
      val result = DataTableImporter.plan(fileName, text, dateOrder)
      return DataTableImportPreview(fileName, text, result.plan, result.notes)
    }
  }
}

/** A file read for import as a Map layer, with its plan (`null` if nothing can be imported). */
data class MapLayerImportPreview(
  val fileName: String,
  val result: GeoReadResult,
  val plan: MapLayerImportPlan?,
) {
  companion object {
    fun of(fileName: String, text: String): MapLayerImportPreview {
      val result = GeoFileReader.read(fileName, text)
      return MapLayerImportPreview(fileName, result, MapLayerImporter.plan(fileName, result))
    }
  }
}
