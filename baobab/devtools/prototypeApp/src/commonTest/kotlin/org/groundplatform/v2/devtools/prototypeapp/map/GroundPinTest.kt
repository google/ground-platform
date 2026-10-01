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
package org.groundplatform.v2.devtools.prototypeapp.map

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GroundPinTest {
  @Test
  fun svg_fillsWithMarkerColor() {
    val svg = GroundPin.svg("#1565C0", "A", isPending = false)

    assertTrue(svg.startsWith("<svg"))
    assertContains(svg, """fill="#1565C0"""")
    assertFalse(svg.contains("stroke-dasharray"))
  }

  @Test
  fun svg_pendingPinHasDashedEdge() {
    assertContains(GroundPin.svg("#1565C0", "A", isPending = true), "stroke-dasharray")
  }

  @Test
  fun svg_showsSymbolOrSquare() {
    val withSymbol = GroundPin.svg("#1565C0", "A", isPending = false)
    val withoutSymbol = GroundPin.svg("#1565C0", "", isPending = false)

    assertContains(withSymbol, ">A</text>")
    assertContains(withSymbol, """font-size="9.5"""")
    assertContains(withoutSymbol, "<rect")
    assertFalse(withoutSymbol.contains("<text"))
  }

  @Test
  fun svg_usesSmallerFontForMultiCharacterSymbols() {
    assertContains(GroundPin.svg("#1565C0", "AB", isPending = false), """font-size="6.5"""")
  }

  @Test
  fun svg_escapesXml() {
    val svg = GroundPin.svg("\"red\"", "<", isPending = false)

    assertContains(svg, "&quot;red&quot;")
    assertContains(svg, ">&lt;</text>")
  }

  @Test
  fun iconId_distinguishesEveryInput() {
    val base = GroundPin.iconId("#1565C0", "A", isPending = false)

    assertNotEquals(base, GroundPin.iconId("#2E7D32", "A", isPending = false))
    assertNotEquals(base, GroundPin.iconId("#1565C0", "B", isPending = false))
    assertNotEquals(base, GroundPin.iconId("#1565C0", "A", isPending = true))
  }
}
