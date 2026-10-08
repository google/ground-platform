/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.ui.map

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GroundPinTest {
  @Test
  fun svg_fillsWithLayerColor() {
    val svg = GroundPin.svg("#1565C0", "park", isPending = false)

    assertTrue(svg.startsWith("<svg"))
    assertContains(svg, """fill="#1565C0"""")
    assertFalse(svg.contains("stroke-dasharray"))
  }

  @Test
  fun svg_pendingPinHasDashedEdge() {
    assertContains(GroundPin.svg("#1565C0", "park", isPending = true), "stroke-dasharray")
  }

  @Test
  fun svg_drawsLayerIconOrSquare_neverAStatusSymbol() {
    val withIcon = GroundPin.svg("#1565C0", "park", isPending = false)
    val withoutIcon = GroundPin.svg("#1565C0", null, isPending = false)
    val unknownIcon = GroundPin.svg("#1565C0", "not-an-icon", isPending = false)

    assertContains(withIcon, "<g transform=")
    assertContains(withIcon, """<path d="M""")
    assertFalse(withIcon.contains("<text"))
    assertContains(withoutIcon, "<rect")
    assertFalse(withoutIcon.contains("<text"))
    assertContains(unknownIcon, "<rect")
  }

  @Test
  fun svg_iconContrastsWithLayerColor() {
    // White icons on dark layer colors, near-black icons on light ones.
    val onDark = GroundPin.svg("#1565C0", "park", isPending = false)
    val onLight = GroundPin.svg("#FFEB3B", "park", isPending = false)

    assertContains(onDark, """fill="#FFFFFF" fill-rule""")
    assertContains(onLight, """fill="#1F1F1F" fill-rule""")
  }

  @Test
  fun svg_escapesXml() {
    val svg = GroundPin.svg("\"red\"", null, isPending = false)

    assertContains(svg, "&quot;red&quot;")
  }

  @Test
  fun iconId_distinguishesEveryInput() {
    val base = GroundPin.iconId("#1565C0", "park", isPending = false)

    assertNotEquals(base, GroundPin.iconId("#2E7D32", "park", isPending = false))
    assertNotEquals(base, GroundPin.iconId("#1565C0", "flag", isPending = false))
    assertNotEquals(base, GroundPin.iconId("#1565C0", null, isPending = false))
    assertNotEquals(base, GroundPin.iconId("#1565C0", "park", isPending = true))
  }
}
