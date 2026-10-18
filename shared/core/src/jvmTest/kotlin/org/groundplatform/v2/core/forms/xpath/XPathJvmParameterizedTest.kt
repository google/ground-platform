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
package org.groundplatform.v2.core.forms.xpath

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * JUnit 5 `@ParameterizedTest` runner executing each case in [XPathParameterizedTest.ALL_CASES] as
 * an individually reported test node on the JVM target.
 */
class XPathJvmParameterizedTest {

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideXPathCases")
  fun evaluateXPathExpressionCase(case: XPathParameterizedTest.ParameterizedCase) {
    val context =
      XPathParameterizedTest.createEvaluationContext(
        contextPath = case.contextPath,
        activeLanguage = case.activeLanguage,
      )
    val compiled = XPathEngine.compile(case.expression)
    val result = compiled.evaluate(context)

    case.expectedBoolean?.let { expected ->
      assertEquals(
        expected = expected,
        actual = result.toBoolean(),
        message = "Boolean mismatch in case: ${case.name}",
      )
    }

    case.expectedNumber?.let { expected ->
      val actual = result.toNumber()
      assertTrue(
        abs(expected - actual) <= case.numberTolerance,
        "Number mismatch in case: ${case.name} -> expected $expected, got $actual",
      )
    }

    case.expectedString?.let { expected ->
      assertEquals(
        expected = expected,
        actual = result.toXPathString(),
        message = "String mismatch in case: ${case.name}",
      )
    }

    case.customAssertion?.invoke(result, context)
  }

  companion object {
    @JvmStatic
    fun provideXPathCases(): List<XPathParameterizedTest.ParameterizedCase> =
      XPathParameterizedTest.ALL_CASES
  }
}
