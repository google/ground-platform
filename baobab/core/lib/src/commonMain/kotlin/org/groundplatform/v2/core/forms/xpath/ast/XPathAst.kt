/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.xpath.ast

/** Abstract Syntax Tree (AST) root node for compiled XPath expressions. */
sealed interface XPathExpr {
  /** Numeric literal constant (e.g., `42`, `3.14`). */
  data class LiteralNumber(val value: Double) : XPathExpr

  /** String literal constant (e.g., `'hello'`, `"world"`). */
  data class LiteralString(val value: String) : XPathExpr

  /**
   * Binary operator expression (`+`, `-`, `*`, `div`, `mod`, `=`, `!=`, `<`, `<=`, `>`, `>=`,
   * `and`, `or`, `|`).
   */
  data class BinaryExpr(val op: BinaryOp, val left: XPathExpr, val right: XPathExpr) : XPathExpr

  /** Unary negation expression (`-expr`). */
  data class UnaryMinusExpr(val operand: XPathExpr) : XPathExpr

  /** Function invocation (`name(arg1, arg2, ...)`). */
  data class FunctionCallExpr(val name: String, val args: List<XPathExpr>) : XPathExpr

  /** Variable or XLSForm parameter reference (`${field}` or `$var`). */
  data class VariableReferenceExpr(val name: String) : XPathExpr

  /**
   * Location path expression starting from root (`isAbsolute = true`) or current context node
   * (`isAbsolute = false`), consisting of zero or more [LocationStep]s.
   */
  data class LocationPathExpr(val isAbsolute: Boolean, val steps: List<LocationStep>) : XPathExpr

  /**
   * Path expression rooted at a primary expression (e.g., `instance('cities')/root/item[code='a']`
   * or `(expr)[predicate]/step`).
   */
  data class FilterPathExpr(
    val base: XPathExpr,
    val predicates: List<XPathExpr>,
    val trailingSteps: List<LocationStep>,
  ) : XPathExpr
}

/** Supported XPath 1.0 binary operators. */
enum class BinaryOp {
  OR,
  AND,
  EQ,
  NEQ,
  LT,
  LTE,
  GT,
  GTE,
  ADD,
  SUB,
  MUL,
  DIV,
  MOD,
  UNION,
}

/** A single step in an XPath location path (`axis::nodeTest[predicate]*`). */
data class LocationStep(
  val axis: Axis,
  val nodeTest: NodeTest,
  val predicates: List<XPathExpr> = emptyList(),
)

/** Navigation axis for a [LocationStep]. */
enum class Axis {
  SELF, // . or self::
  PARENT, // .. or parent::
  CHILD, // child:: (default)
  DESCENDANT, // descendant::
  DESCENDANT_OR_SELF, // // or descendant-or-self::
  ANCESTOR, // ancestor::
  ANCESTOR_OR_SELF, // ancestor-or-self::
  FOLLOWING_SIBLING, // following-sibling::
  PRECEDING_SIBLING, // preceding-sibling::
}

/** Node test matching candidate nodes along an [Axis]. */
sealed interface NodeTest {
  /** Matches nodes with a specific field/element name. */
  data class NameTest(val name: String) : NodeTest

  /** Matches any element/field node (`*`). */
  data object WildcardTest : NodeTest

  /** Matches any node (`node()`). */
  data object AnyNodeTest : NodeTest

  /** Matches text content (`text()`). */
  data object TextNodeTest : NodeTest
}
