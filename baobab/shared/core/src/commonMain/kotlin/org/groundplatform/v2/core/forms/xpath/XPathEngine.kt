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
package org.groundplatform.v2.core.forms.xpath

import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.TypedValue
import org.groundplatform.v2.core.forms.xpath.ast.XPathParser
import org.groundplatform.v2.core.forms.xpath.model.XPathValue

/**
 * Primary facade for compiling and evaluating ODK XForms-compliant XPath expressions against
 * ProtoForms Protocol Buffer records (`groundplatform.v2.forms.RecordInstance` and
 * `groundplatform.v2.forms.FormDef`).
 */
object XPathEngine {

  /**
   * Compiles an XPath expression string into an immutable [CompiledXPathExpression] AST with static
   * dependency metadata.
   *
   * @throws XPathSyntaxException if [expression] contains syntax errors.
   */
  fun compile(expression: String): CompiledXPathExpression {
    val ast = XPathParser.parse(expression)
    return CompiledXPathExpression(expression, ast)
  }

  /** Evaluates [expression] against [context] and returns the rich [XPathValue] result. */
  fun evaluate(expression: String, context: EvaluationContext): XPathValue =
    compile(expression).evaluate(context)

  /** Evaluates [expression] against [context] and coerces the result to a boolean. */
  fun evaluateBoolean(expression: String, context: EvaluationContext): Boolean =
    compile(expression).evaluateBoolean(context)

  /** Evaluates [expression] against [context] and coerces the result to a number. */
  fun evaluateNumber(expression: String, context: EvaluationContext): Double =
    compile(expression).evaluateNumber(context)

  /** Evaluates [expression] against [context] and formats the result as a string. */
  fun evaluateString(expression: String, context: EvaluationContext): String =
    compile(expression).evaluateString(context)

  /**
   * Evaluates [expression] against [context] and converts the result into a protobuf [TypedValue]
   * matching [targetType].
   */
  fun evaluateTypedValue(
    expression: String,
    context: EvaluationContext,
    targetType: DataType? = null,
  ): TypedValue? = compile(expression).evaluateTypedValue(context, targetType)

  /**
   * Evaluates [expression] against [context] and converts the result into a protobuf [FieldValue]
   * matching [targetType].
   */
  fun evaluateFieldValue(
    expression: String,
    context: EvaluationContext,
    targetType: DataType? = null,
  ): FieldValue? = compile(expression).evaluateFieldValue(context, targetType)
}
