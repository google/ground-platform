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
package org.openforis.collectone.v2.app.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.ui.text.UiText

/** The English counts and nouns of map features, as the map wrote them before. */
class MapFeatureNounTextTest {
  private fun english(text: UiText): String = runBlocking { text.resolveString() }

  @Test
  fun genericFeatures_areCountedAsMapFeatures() {
    assertEquals("1 map feature", english(MapFeatureNoun.Generic.countText(1)))
    assertEquals("5 map features", english(MapFeatureNoun.Generic.countText(5)))
    assertEquals("map features", english(MapFeatureNoun.Generic.pluralText))
  }

  @Test
  fun layerFeatures_areCountedWithTheLayerNoun() {
    val parcels = MapFeatureNoun.OfLayer("coffee parcel", "coffee parcels")
    assertEquals("1 coffee parcel", english(parcels.countText(1)))
    assertEquals("5 coffee parcels", english(parcels.countText(5)))
    assertEquals("coffee parcels", english(parcels.pluralText))
  }
}
