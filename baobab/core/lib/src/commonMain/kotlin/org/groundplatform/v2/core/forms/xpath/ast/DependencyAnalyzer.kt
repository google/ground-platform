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

/** Represents a static dependency discovered in an XPath expression for reactive DAG scheduling. */
sealed interface XPathDependency {
  /**
   * Dependency on a field path within the primary instance record.
   *
   * @property path Canonical slash-separated path (e.g., `/household/member/age` or `../age`).
   * @property isAbsolute True if rooted at the document top-level (`/data/...`), false if relative.
   */
  data class FieldDependency(val path: String, val isAbsolute: Boolean) : XPathDependency

  /** Dependency on a secondary lookup dataset (`instance('id')` or `pulldata('id', ...)`). */
  data class SecondaryInstanceDependency(val instanceId: String) : XPathDependency

  /**
   * Dependency on repeat context position (`position()`, `last()`, `current()`, `indexed-repeat`).
   */
  data object RepeatContextDependency : XPathDependency
}

/**
 * Statically analyzes an [XPathExpr] AST to extract all field, secondary instance, and repeat
 * context dependencies. Used by reactive form engines to construct recalculation DAGs.
 */
object DependencyAnalyzer {

  fun extractDependencies(expr: XPathExpr): Set<XPathDependency> {
    val dependencies = mutableSetOf<XPathDependency>()
    visit(expr, dependencies)
    return dependencies
  }

  private fun visit(expr: XPathExpr, out: MutableSet<XPathDependency>) {
    when (expr) {
      is XPathExpr.LiteralNumber,
      is XPathExpr.LiteralString -> {}
      is XPathExpr.VariableReferenceExpr -> {
        out.add(XPathDependency.FieldDependency(expr.name, isAbsolute = false))
      }
      is XPathExpr.UnaryMinusExpr -> visit(expr.operand, out)
      is XPathExpr.BinaryExpr -> {
        visit(expr.left, out)
        visit(expr.right, out)
      }
      is XPathExpr.FunctionCallExpr -> {
        when (expr.name) {
          "instance" -> {
            val firstArg = expr.args.firstOrNull()
            if (firstArg is XPathExpr.LiteralString) {
              out.add(XPathDependency.SecondaryInstanceDependency(firstArg.value))
            }
          }
          "pulldata" -> {
            val firstArg = expr.args.firstOrNull()
            if (firstArg is XPathExpr.LiteralString) {
              out.add(XPathDependency.SecondaryInstanceDependency(firstArg.value))
            }
          }
          "position",
          "last",
          "current",
          "indexed-repeat" -> {
            out.add(XPathDependency.RepeatContextDependency)
          }
        }
        for (arg in expr.args) {
          visit(arg, out)
        }
      }
      is XPathExpr.LocationPathExpr -> {
        val pathString = formatSteps(expr.isAbsolute, expr.steps)
        if (pathString.isNotEmpty()) {
          out.add(XPathDependency.FieldDependency(pathString, expr.isAbsolute))
        }
        for (step in expr.steps) {
          if (step.axis == Axis.PARENT) {
            out.add(XPathDependency.RepeatContextDependency)
          }
          for (pred in step.predicates) {
            visit(pred, out)
          }
        }
      }
      is XPathExpr.FilterPathExpr -> {
        visit(expr.base, out)
        for (pred in expr.predicates) {
          visit(pred, out)
        }
        for (step in expr.trailingSteps) {
          for (pred in step.predicates) {
            visit(pred, out)
          }
        }
      }
    }
  }

  private fun formatSteps(isAbsolute: Boolean, steps: List<LocationStep>): String {
    if (steps.isEmpty()) return if (isAbsolute) "/" else "."
    val parts = steps.map { step ->
      when (step.axis) {
        Axis.SELF -> "."
        Axis.PARENT -> ".."
        else ->
          when (val nt = step.nodeTest) {
            is NodeTest.NameTest -> nt.name
            is NodeTest.WildcardTest -> "*"
            is NodeTest.AnyNodeTest -> "node()"
            is NodeTest.TextNodeTest -> "text()"
          }
      }
    }
    return (if (isAbsolute) "/" else "") + parts.joinToString("/")
  }
}
