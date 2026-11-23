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

import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_map_features_count
import org.openforis.collectone.v2.app.resources.app_map_features_lowercase
import org.openforis.collectone.v2.app.ui.text.UiText

/**
 * What map counts and banners call the survey's map features: generic map features, or the noun of
 * the one visible map layer.
 */
sealed interface MapFeatureNoun {
  /** No single map layer names the features: they are "map features", in the user's language. */
  data object Generic : MapFeatureNoun

  /**
   * The visible layer's nouns as the survey author wrote them, e.g. `"coffee parcel"` and `"coffee
   * parcels"`.
   */
  data class OfLayer(val singularNoun: String, val pluralNoun: String) : MapFeatureNoun
}

/** The plural noun, e.g. `"map features"` or `"coffee parcels"`. */
val MapFeatureNoun.pluralText: UiText
  get() =
    when (this) {
      MapFeatureNoun.Generic -> UiText.Resource(Res.string.app_map_features_lowercase)
      is MapFeatureNoun.OfLayer -> UiText.Plain(pluralNoun)
    }

/**
 * [count] features, e.g. `"5 map features"`. A layer's nouns are the survey's own text, so the
 * count goes before the author's singular or plural noun (`"1 coffee parcel"`, `"5 coffee
 * parcels"`).
 */
fun MapFeatureNoun.countText(count: Int): UiText =
  when (this) {
    MapFeatureNoun.Generic ->
      UiText.Plural(Res.plurals.app_map_features_count, count, listOf(count))
    is MapFeatureNoun.OfLayer ->
      UiText.Plain("$count ${if (count == 1) singularNoun else pluralNoun}")
  }
