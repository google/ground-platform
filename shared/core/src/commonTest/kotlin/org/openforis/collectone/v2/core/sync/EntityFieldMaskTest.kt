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
package org.openforis.collectone.v2.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.core.geo.GeometryProtos

class EntityFieldMaskTest {
  private fun sampleEntity(): EntityRecord =
    EntityRecord(
      entity_id = "e1",
      dataset_id = "sample_plots",
      survey_id = "s1",
      revision = 4L,
      label = "P0001",
      geo_id = "geo-123",
      layer_id = "layer-1",
      geometry = GeometryProtos.point(-1.2, 36.8),
      properties =
        mapOf(
          "plot_id" to TypedValue(string_value = "P0001"),
          "status" to TypedValue(string_value = "PENDING"),
          "commodity" to TypedValue(string_value = "coffee"),
          "whisp_Country" to TypedValue(string_value = "KEN"),
          "whisp_TMF_def_2020" to TypedValue(string_value = "0.0"),
        ),
    )

  @Test
  fun emptyMask_returnsOriginalRecordUnchanged() {
    val entity = sampleEntity()
    assertEquals(entity, EntityFieldMask.apply(entity, emptyList()))
  }

  @Test
  fun lightweightSyncMask_keepsMetadataCoreAndExtraConceptKeys_omitsBulkProperties() {
    val entity = sampleEntity()
    val mask = EntityFieldMask.forLightweightSync(extraPropertyKeys = listOf("commodity"))

    val masked = EntityFieldMask.apply(entity, mask)

    assertEquals("e1", masked.entity_id)
    assertEquals("P0001", masked.label)
    assertEquals(entity.geometry, masked.geometry)
    assertEquals(
      setOf("plot_id", "status", "commodity"),
      masked.properties.keys,
    )
  }

  @Test
  fun tableTileMask_returnsOnlyRequestedColumnSliceWithoutGeometry() {
    val entity = sampleEntity()
    val mask = EntityFieldMask.forTableTile(listOf("whisp_Country", "whisp_TMF_def_2020"))

    val masked = EntityFieldMask.apply(entity, mask)

    assertEquals("e1", masked.entity_id)
    assertEquals("P0001", masked.label)
    assertNull(masked.geometry)
    assertEquals(
      mapOf(
        "whisp_Country" to TypedValue(string_value = "KEN"),
        "whisp_TMF_def_2020" to TypedValue(string_value = "0.0"),
      ),
      masked.properties,
    )
  }
}
