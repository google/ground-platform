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

import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.TypedValue
import org.groundplatform.v2.core.forms.xpath.ast.DependencyAnalyzer
import org.groundplatform.v2.core.forms.xpath.ast.XPathDependency
import org.groundplatform.v2.core.forms.xpath.ast.XPathExpr
import org.groundplatform.v2.core.forms.xpath.eval.XPathEvaluator
import org.groundplatform.v2.core.forms.xpath.model.XPathValue

/**
 * A compiled, immutable XPath expression ready for repeated evaluation across form instances and
 * reactive recalculation cycles.
 */
class CompiledXPathExpression internal constructor(val source: String, val ast: XPathExpr) {
  private val evaluator = XPathEvaluator()

  /**
   * Static set of field, secondary instance, and repeat context dependencies referenced by this
   * expression. Used by reactive form DAGs to schedule recalculations on field edits.
   */
  val dependencies: Set<XPathDependency> by lazy { DependencyAnalyzer.extractDependencies(ast) }

  /** Evaluates this expression against [context] and returns the raw [XPathValue] ADT result. */
  fun evaluate(context: EvaluationContext): XPathValue = evaluator.evaluate(ast, context)

  /**
   * Evaluates this expression and coerces the result to a boolean (`relevant`, `constraint`,
   * `required`).
   */
  fun evaluateBoolean(context: EvaluationContext): Boolean = evaluate(context).toBoolean()

  /**
   * Evaluates this expression and coerces the result to a double-precision number (`repeat_count`).
   */
  fun evaluateNumber(context: EvaluationContext): Double = evaluate(context).toNumber()

  /** Evaluates this expression and formats the result as a canonical XPath string (`output`). */
  fun evaluateString(context: EvaluationContext): String = evaluate(context).toXPathString()

  /**
   * Evaluates this expression and converts the result into a strongly-typed protobuf [TypedValue]
   * suitable for writing to a field binding (`calculate_expression`).
   */
  fun evaluateTypedValue(context: EvaluationContext, targetType: DataType? = null): TypedValue? =
    evaluate(context).toTypedValue(targetType)

  /**
   * Evaluates this expression and converts the result into a protobuf [FieldValue] (supporting both
   * scalar values and multi-select lists).
   */
  fun evaluateFieldValue(context: EvaluationContext, targetType: DataType? = null): FieldValue? =
    evaluate(context).toFieldValue(targetType)

  override fun toString(): String = "CompiledXPathExpression($source)"
}
