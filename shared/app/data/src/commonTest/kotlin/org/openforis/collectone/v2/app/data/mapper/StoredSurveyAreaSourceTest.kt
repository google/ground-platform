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
import openforis.collectone.v1.survey.SurveyArea as SurveyAreaProto
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAreaSource

/** Survey areas store a typed source; areas stored with English source text still decode. */
class StoredSurveyAreaSourceTest {
  @Test
  fun everySource_roundTrips() {
    listOf(
        SurveyAreaSource.SelectedBoundary,
        SurveyAreaSource.Drawn,
        SurveyAreaSource.Uploaded("lakes.geojson"),
        SurveyAreaSource.PlaceSearch,
        SurveyAreaSource.Coordinates,
        SurveyAreaSource.CollectEarthGrid(25),
      )
      .forEach { source ->
        val proto = SurveyAreaProto(area_source = SurveyAreaSourceMapper.toProto(source))

        assertEquals(source, SurveyAreaSourceMapper.fromProto(proto))
      }
  }

  @Test
  fun areaWithoutSource_isASelectedBoundary() {
    assertEquals(
      SurveyAreaSource.SelectedBoundary,
      SurveyAreaSourceMapper.fromProto(SurveyAreaProto()),
    )
  }

  @Test
  fun legacyArea_decodesTheSourceItNamedInEnglish() {
    assertEquals(SurveyAreaSource.SelectedBoundary, legacySource("Selected boundary"))
    assertEquals(SurveyAreaSource.Drawn, legacySource("Drawn on map"))
    assertEquals(
      SurveyAreaSource.Uploaded("lakes.geojson"),
      legacySource("Uploaded: lakes.geojson"),
    )
    assertEquals(SurveyAreaSource.PlaceSearch, legacySource("Places API"))
    assertEquals(SurveyAreaSource.Coordinates, legacySource("Coordinates"))
    assertEquals(
      SurveyAreaSource.CollectEarthGrid(12),
      legacySource("Collect Earth plot grid (12 plots)"),
    )
  }

  @Test
  fun legacyArea_decodesTheSchemaDocumentedForms() {
    assertEquals(SurveyAreaSource.PlaceSearch, legacySource("search"))
    assertEquals(SurveyAreaSource.Drawn, legacySource("drawn"))
    assertEquals(SurveyAreaSource.Uploaded("plots.kml"), legacySource("uploaded:plots.kml"))
  }

  @Test
  fun legacyAreaWithOtherText_isASelectedBoundary() {
    assertEquals(SurveyAreaSource.SelectedBoundary, legacySource("Somewhere else"))
  }

  /** The source of an area encoded the way clients stored it before `area_source`. */
  private fun legacySource(text: String): SurveyAreaSource {
    val buffer = Buffer().write(SurveyAreaProto(name = "Area").encodeByteString())
    ProtoAdapter.STRING.encodeWithTag(
      ProtoWriter(buffer),
      SurveyAreaSourceMapper.LEGACY_SOURCE_TEXT,
      text,
    )
    return SurveyAreaSourceMapper.fromProto(SurveyAreaProto.ADAPTER.decode(buffer.readByteString()))
  }
}
