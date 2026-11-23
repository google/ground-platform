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
package org.openforis.collectone.v2.app.data.mapper

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okio.Buffer
import openforis.collectone.v1.data.PlaceRecord
import openforis.collectone.v1.data.PlaceSource as PlaceSourceProto
import org.openforis.collectone.v2.app.domain.model.PlaceSource

/** Places store a typed source; places stored with the English `source_label` still decode. */
class StoredPlaceSourceTest {
  @Test
  fun place_decodesItsSource() {
    val proto = PlaceRecord(id = "p1", source = PlaceSourceProto.PLACE_SOURCE_PLACES_API)

    assertEquals(PlaceSource.PLACES_API, ProtoRecordMapper.placeFromProto(proto).source)
  }

  @Test
  fun placeWithoutSource_hasNoSource() {
    assertNull(ProtoRecordMapper.placeFromProto(PlaceRecord(id = "p1")).source)
  }

  @Test
  fun legacyPlaceLabelledPlacesApi_decodesThePlacesApiSource() {
    val legacy = legacyPlace(sourceLabel = "Places API")

    assertEquals(PlaceSource.PLACES_API, ProtoRecordMapper.placeFromProto(legacy).source)
  }

  @Test
  fun legacyPlaceWithAnotherLabel_hasNoSource() {
    val legacy = legacyPlace(sourceLabel = "Mapbox")

    assertNull(ProtoRecordMapper.placeFromProto(legacy).source)
  }

  @Test
  fun placeWithoutSource_encodesUnspecified() {
    val place = ProtoRecordMapper.placeFromProto(PlaceRecord(id = "p1"))

    assertEquals(
      PlaceSourceProto.PLACE_SOURCE_UNSPECIFIED,
      ProtoRecordMapper.placeToProto(place).source,
    )
  }

  /** A place encoded the way clients stored it before `PlaceRecord.source`. */
  private fun legacyPlace(sourceLabel: String): PlaceRecord {
    val buffer = Buffer().write(PlaceRecord(id = "p1").encodeByteString())
    ProtoAdapter.STRING.encodeWithTag(
      ProtoWriter(buffer),
      ProtoRecordMapper.PLACE_LEGACY_SOURCE_LABEL,
      sourceLabel,
    )
    return PlaceRecord.ADAPTER.decode(buffer.readByteString())
  }
}
