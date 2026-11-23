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
package org.openforis.collectone.v2.core.forms.xpath.functions

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.openforis.collectone.v2.core.forms.xpath.EvaluationContext
import org.openforis.collectone.v2.core.forms.xpath.XPathEngine
import org.openforis.collectone.v2.core.forms.xpath.buildRecordInstance
import org.openforis.collectone.v2.core.forms.xpath.buildTestFormDef

/** XPath 1.0 section 4.4 number functions and the ODK math extensions. */
class NumericFunctionsTest {

  private fun context(randomSeed: Long? = null) =
    EvaluationContext.fromRecordInstance(
      recordInstance =
        buildRecordInstance {
          string("a", "2")
          string("b", "3.5")
          emptyField("blank")
          string("word", "abc")
        },
      formDef = buildTestFormDef(),
      randomSeed = randomSeed,
    )

  private fun number(expression: String, randomSeed: Long? = null): Double =
    XPathEngine.evaluateNumber(expression, context(randomSeed))

  @Test
  fun number_convertsStringsAndIsNaNForText() {
    assertEquals(12.5, number("number(' 12.5 ')"))
    assertEquals(2.0, number("number(/household/a)"))
    assertTrue(number("number(/household/word)").isNaN())
  }

  @Test
  fun int_truncatesTowardZero() {
    // ODK XForms spec: int() discards the fraction, unlike floor() for negatives.
    assertEquals(-2.0, number("int(-2.7)"))
    assertEquals(2.0, number("int(2.7)"))
    assertTrue(number("int('x')").isNaN())
  }

  @Test
  fun floorAndCeiling_roundTowardTheirInfinities() {
    assertEquals(-3.0, number("floor(-2.1)"))
    assertEquals(-2.0, number("ceiling(-2.9)"))
  }

  @Test
  fun round_supportsNegativePlacesAndKeepsNonFiniteValues() {
    assertEquals(1200.0, number("round(1234.5, -2)"))
    assertEquals(1.24, number("round(1.235, 2)"))
    assertTrue(number("round('x')").isNaN())
    assertEquals(Double.POSITIVE_INFINITY, number("round(1 div 0)"))
  }

  @Test
  fun sum_skipsEmptyAnswersAndRejectsNonNodeSets() {
    assertEquals(5.5, number("sum(/household/a | /household/b | /household/blank)"))
    assertFailsWith<Exception> { number("sum(3)") }
  }

  @Test
  fun maxAndMin_skipEmptyAnswersButPropagateNaN() {
    assertEquals(3.5, number("max(/household/a, /household/b, /household/blank)"))
    assertEquals(2.0, number("min(/household/a, /household/b)"))
    assertTrue(number("max(/household/blank)").isNaN())
    assertTrue(number("min(/household/a, /household/word)").isNaN())
    assertTrue(number("max()").isNaN())
  }

  @Test
  fun mathExtensions() {
    assertEquals(8.0, number("pow(2, 3)"))
    assertEquals(1000.0, number("exp10(3)"))
    assertEquals(2.0, number("log10(100)"))
    assertEquals(PI, number("pi()"))
    assertEquals(3.0, number("sqrt(9)"))
    assertEquals(1.0, number("exp(0)"))
    assertEquals(0.0, number("log(1)"))
    assertEquals(PI / 2, number("atan2(1, 0)"))
    assertEquals(2.5, number("abs(-2.5)"))
  }

  @Test
  fun random_isRepeatableWithASeedAndInTheUnitInterval() {
    val first = number("random()", randomSeed = 42L)

    assertEquals(first, number("random()", randomSeed = 42L))
    assertTrue(first >= 0.0 && first < 1.0)
  }

  @Test
  fun wrongArgumentCounts_fail() {
    assertFailsWith<Exception> { number("floor()") }
    assertFailsWith<Exception> { number("pow(2)") }
    assertFailsWith<Exception> { number("random(1)") }
  }
}
