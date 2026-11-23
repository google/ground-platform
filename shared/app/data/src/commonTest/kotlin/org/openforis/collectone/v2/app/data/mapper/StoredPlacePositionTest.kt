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
package org.openforis.collectone.v2.app.data.mapper

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import okio.Buffer
import openforis.collectone.v1.data.PlaceRecord
import openforis.collectone.v1.data.Position
import org.openforis.collectone.v2.map.LatLng

/** Places store a canonical position; places stored with separate numbers still decode. */
class StoredPlacePositionTest {
  @Test
  fun place_roundTripsItsPosition() {
    val proto = PlaceRecord(id = "p1", position = Position(latitude = 9.82, longitude = 106.34))

    assertEquals(LatLng(9.82, 106.34), ProtoRecordMapper.placeFromProto(proto).position)
  }

  @Test
  fun storedPlaceWithAnOutOfRangePosition_stillLoads() {
    val place =
      ProtoRecordMapper.placeFromProto(
        PlaceRecord(id = "p1", position = Position(latitude = 120.0, longitude = 10.0))
      )

    assertEquals(LatLng(90.0, 10.0), place.position)
  }

  @Test
  fun legacyPlace_decodesItsStoredLongitudeAndLatitude() {
    val buffer = Buffer().write(PlaceRecord(id = "p1").encodeByteString())
    val writer = ProtoWriter(buffer)
    ProtoAdapter.DOUBLE.encodeWithTag(writer, ProtoRecordMapper.PLACE_LEGACY_LONGITUDE, 36.947)
    ProtoAdapter.DOUBLE.encodeWithTag(writer, ProtoRecordMapper.PLACE_LEGACY_LATITUDE, -0.416)
    val legacy = PlaceRecord.ADAPTER.decode(buffer.readByteString())

    assertEquals(LatLng(-0.416, 36.947), ProtoRecordMapper.placeFromProto(legacy).position)
  }
}
