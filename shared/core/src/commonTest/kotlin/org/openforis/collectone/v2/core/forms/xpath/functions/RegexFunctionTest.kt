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
package org.openforis.collectone.v2.core.forms.xpath.functions

import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.core.forms.xpath.EvaluationContext
import org.openforis.collectone.v2.core.forms.xpath.XPathEngine
import org.openforis.collectone.v2.core.forms.xpath.buildRecordInstance
import org.openforis.collectone.v2.core.forms.xpath.buildTestFormDef
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

class RegexFunctionTest {

  private val context =
    EvaluationContext.fromRecordInstance(
      recordInstance = buildRecordInstance { string("code", "GH-2026") },
      formDef = buildTestFormDef(),
    )

  private fun evaluate(expression: String): XPathValue = XPathEngine.evaluate(expression, context)

  @Test
  fun regex_invalidPattern_returnsFalse() {
    assertEquals(XPathValue.Bool(false), evaluate("regex('abc', '[unclosed')"))
  }

  @Test
  fun regex_invalidPatternEvaluatedAgain_stillReturnsFalse() {
    evaluate("regex('abc', '(unbalanced')")

    assertEquals(XPathValue.Bool(false), evaluate("regex('abc', '(unbalanced')"))
  }

  @Test
  fun regex_samePatternAgainstAnotherValue_matchesThatValue() {
    assertEquals(XPathValue.Bool(true), evaluate("regex('A-1', '^[A-Z]-[0-9]$')"))

    assertEquals(XPathValue.Bool(false), evaluate("regex('a-1', '^[A-Z]-[0-9]$')"))
  }

  @Test
  fun regex_afterMoreDistinctPatternsThanTheCacheHolds_stillMatches() {
    for (i in 0 until 200) {
      assertEquals(XPathValue.Bool(true), evaluate("regex('value-$i', 'value-$i$')"))
    }
  }

  @Test
  fun regex_fieldValue_isMatchedAgainstPattern() {
    assertEquals(XPathValue.Bool(true), evaluate("regex(/household/code, '^GH-[0-9]{4}$')"))
  }
}
