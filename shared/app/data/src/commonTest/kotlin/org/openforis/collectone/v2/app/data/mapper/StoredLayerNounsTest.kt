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

import kotlin.test.Test
import kotlin.test.assertEquals
import openforis.collectone.v1.survey.LayerDef
import openforis.collectone.v1.survey.MapConfig
import openforis.collectone.v1.survey.SurveyDef

/** Layers keep the author's nouns as typed; the English nouns old clients filled in read blank. */
class StoredLayerNounsTest {
  private fun decode(layer: LayerDef) =
    ProtoSurveyMapper.mapLayersFromProto(SurveyDef(map_config = MapConfig(layers = listOf(layer))))
      .single()

  @Test
  fun authorNouns_decodeAsTyped() {
    val layer =
      decode(
        LayerDef(
          id = "l1",
          singular_item_label = "coffee parcel",
          plural_item_label = "coffee parcels",
        )
      )

    assertEquals("coffee parcel", layer.singularNoun)
    assertEquals("coffee parcels", layer.pluralNoun)
  }

  @Test
  fun missingNouns_decodeBlank() {
    val layer = decode(LayerDef(id = "l1"))

    assertEquals("", layer.singularNoun)
    assertEquals("", layer.pluralNoun)
  }

  @Test
  fun englishFallbackNouns_decodeBlank() {
    val layer =
      decode(LayerDef(id = "l1", singular_item_label = "location", plural_item_label = "locations"))

    assertEquals("", layer.singularNoun)
    assertEquals("", layer.pluralNoun)
  }
}
