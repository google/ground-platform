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
package org.openforis.collectone.v2.app.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.model.MapClusterFeatureItem
import org.openforis.collectone.v2.app.domain.model.MapFeatureKind
import org.openforis.collectone.v2.app.domain.model.groupClusterFeaturesByMarkerSymbol

/** The English status chips of map cluster groups, as the domain wrote them before. */
class MapClusterTextTest {
  private fun feature(id: String, symbol: String) =
    MapClusterFeatureItem(
      id = id,
      kind = MapFeatureKind.ENTITY,
      label = id,
      markerSymbol = symbol,
      colorHex = 0xFF2E7D32L,
      colorCss = "#2E7D32",
      normalizedX = 0.5f,
      normalizedY = 0.5f,
    )

  @Test
  fun statusText_namesEachGroupInEnglish() {
    val groups =
      groupClusterFeaturesByMarkerSymbol(
        listOf(
          feature("a", "✓"),
          feature("b", "◐"),
          feature("c", "○"),
          feature("d", "★"),
          feature("e", ""),
        )
      )

    assertEquals(
      listOf("Completed", "In progress", "Pending", "Symbol ★", "No marker symbol"),
      groups.map { runBlocking { it.statusText.resolveString() } },
    )
  }
}
