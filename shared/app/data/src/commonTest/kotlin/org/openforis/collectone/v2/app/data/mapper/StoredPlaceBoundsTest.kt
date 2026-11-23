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
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * Places stored with their bounding box as four separate numbers (fields 11 to 14) still decode.
 */
class StoredPlaceBoundsTest {
  @Test
  fun legacyPlace_decodesItsStoredBoundingBox() {
    val legacy = legacyPlace(west = 36.922, south = -0.442, east = 36.978, north = -0.396)

    assertEquals(
      LngLatBounds(west = 36.922, south = -0.442, east = 36.978, north = -0.396),
      ProtoRecordMapper.placeFromProto(legacy).bounds,
    )
  }

  @Test
  fun legacyPlaceWithSwappedEdges_decodesNormalizedBounds() {
    val legacy = legacyPlace(west = 36.978, south = -0.396, east = 36.922, north = -0.442)

    assertEquals(
      LngLatBounds(west = 36.922, south = -0.442, east = 36.978, north = -0.396),
      ProtoRecordMapper.placeFromProto(legacy).bounds,
    )
  }

  @Test
  fun legacyPlaceMissingAnEdge_hasNoBounds() {
    val legacy = legacyPlace(west = 36.922, south = -0.442, east = 36.978, north = null)

    assertNull(ProtoRecordMapper.placeFromProto(legacy).bounds)
  }

  /** A place encoded the way clients stored it before `PlaceRecord.bounds`. */
  private fun legacyPlace(west: Double, south: Double, east: Double, north: Double?): PlaceRecord {
    val buffer = Buffer().write(PlaceRecord(id = "p1").encodeByteString())
    val writer = ProtoWriter(buffer)
    ProtoAdapter.DOUBLE.encodeWithTag(writer, LEGACY_BBOX_MIN_LNG, west)
    ProtoAdapter.DOUBLE.encodeWithTag(writer, LEGACY_BBOX_MIN_LAT, south)
    ProtoAdapter.DOUBLE.encodeWithTag(writer, LEGACY_BBOX_MAX_LNG, east)
    if (north != null) ProtoAdapter.DOUBLE.encodeWithTag(writer, LEGACY_BBOX_MAX_LAT, north)
    return PlaceRecord.ADAPTER.decode(buffer.readByteString())
  }

  private companion object {
    const val LEGACY_BBOX_MIN_LNG = 11
    const val LEGACY_BBOX_MIN_LAT = 12
    const val LEGACY_BBOX_MAX_LNG = 13
    const val LEGACY_BBOX_MAX_LAT = 14
  }
}
