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
package org.openforis.collectone.v2.core.forms.xpath.ast

import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.core.forms.xpath.XPathEngine
import org.openforis.collectone.v2.core.forms.xpath.ast.XPathDependency.FieldDependency
import org.openforis.collectone.v2.core.forms.xpath.ast.XPathDependency.RepeatContextDependency
import org.openforis.collectone.v2.core.forms.xpath.ast.XPathDependency.SecondaryInstanceDependency

class DependencyAnalyzerTest {

  private fun dependenciesOf(expression: String): Set<XPathDependency> =
    DependencyAnalyzer.extractDependencies(XPathEngine.compile(expression).ast)

  @Test
  fun literal_hasNoDependencies() {
    assertEquals(emptySet(), dependenciesOf("'hello'"))
  }

  @Test
  fun absolutePath_isAbsoluteFieldDependency() {
    assertEquals(
      setOf(FieldDependency("/household/size", isAbsolute = true)),
      dependenciesOf("/household/size"),
    )
  }

  @Test
  fun relativePath_isRelativeFieldDependency() {
    assertEquals(setOf(FieldDependency("age", isAbsolute = false)), dependenciesOf("age"))
  }

  @Test
  fun binaryExpression_collectsBothOperands() {
    assertEquals(
      setOf(
        FieldDependency("/data/qty", isAbsolute = true),
        FieldDependency("/data/price", isAbsolute = true),
      ),
      dependenciesOf("/data/qty * /data/price"),
    )
  }

  @Test
  fun parentStep_addsRepeatContextDependency() {
    assertEquals(
      setOf(FieldDependency("../species", isAbsolute = false), RepeatContextDependency),
      dependenciesOf("../species"),
    )
  }

  @Test
  fun instanceCall_addsSecondaryInstanceDependency() {
    assertEquals(
      setOf(SecondaryInstanceDependency("trees")),
      dependenciesOf("instance('trees')"),
    )
  }

  @Test
  fun pulldataCall_addsSecondaryInstanceDependency() {
    assertEquals(
      setOf(SecondaryInstanceDependency("prices")),
      dependenciesOf("pulldata('prices', 'price', 'item', 'rice')"),
    )
  }

  @Test
  fun positionCall_addsRepeatContextDependency() {
    assertEquals(setOf(RepeatContextDependency), dependenciesOf("position()"))
  }

  @Test
  fun functionArguments_areVisited() {
    assertEquals(
      setOf(FieldDependency("/data/name", isAbsolute = true)),
      dependenciesOf("concat('Hi ', /data/name)"),
    )
  }

  @Test
  fun predicate_isVisited() {
    assertEquals(
      setOf(
        FieldDependency("/data/person", isAbsolute = true),
        FieldDependency("age", isAbsolute = false),
      ),
      dependenciesOf("/data/person[age > 18]"),
    )
  }
}
