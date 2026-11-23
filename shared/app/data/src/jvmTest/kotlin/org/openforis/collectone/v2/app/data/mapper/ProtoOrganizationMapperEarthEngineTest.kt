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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.openforis.collectone.v2.app.domain.model.EarthEnginePresets
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType

class ProtoOrganizationMapperEarthEngineTest {
  @Test
  fun imagerySource_roundTripsEarthEngineTypeAndExpression() {
    val preset = EarthEnginePresets.presets.first()
    val proto = ProtoOrganizationMapper.imagerySourceToProto(preset)

    assertNotNull(proto.earth_engine)
    assertNull(proto.xyz_tiles)
    assertEquals(preset.expression, proto.earth_engine?.expression)
    assertEquals(preset.id, proto.imagery_source_id)
    assertEquals(preset.name, proto.name)

    val mappedBack = ProtoOrganizationMapper.imagerySourceFromProto(proto)
    assertEquals(ImagerySourceType.EARTH_ENGINE, mappedBack.type)
    assertEquals(preset.id, mappedBack.id)
    assertEquals(preset.name, mappedBack.name)
    assertEquals(preset.expression, mappedBack.expression)
    assertEquals("", mappedBack.urlTemplate)
  }

  @Test
  fun imagerySource_roundTripsXyzTilesWithoutEarthEngine() {
    val xyzSource =
      ImagerySource(
        id = "imagery-osm",
        name = "OpenStreetMap",
        urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        type = ImagerySourceType.XYZ_TILES,
        allowOfflineDownload = true,
      )
    val proto = ProtoOrganizationMapper.imagerySourceToProto(xyzSource)

    assertNull(proto.earth_engine)
    assertNotNull(proto.xyz_tiles)
    assertEquals("https://tile.openstreetmap.org/{z}/{x}/{y}.png", proto.xyz_tiles?.url_template)

    val mappedBack = ProtoOrganizationMapper.imagerySourceFromProto(proto)
    assertEquals(ImagerySourceType.XYZ_TILES, mappedBack.type)
    assertEquals(xyzSource.id, mappedBack.id)
    assertEquals(xyzSource.urlTemplate, mappedBack.urlTemplate)
    assertNull(mappedBack.expression)
  }
}
