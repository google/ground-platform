/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import org.openforis.collectone.v2.core.forms.xpath.EvaluationContext
import org.openforis.collectone.v2.core.forms.xpath.XPathEvaluationException
import org.openforis.collectone.v2.core.forms.xpath.ast.XPathExpr
import org.openforis.collectone.v2.core.forms.xpath.eval.XPathEvaluator
import org.openforis.collectone.v2.core.forms.xpath.model.XPathNode
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

/** Registry and implementation of all standard XPath 1.0 and XForms functions. */
internal object XPathFunctionRegistry {

  /** Eagerly evaluated functions by name, merged from the per-family tables. */
  private val eagerFunctions: Map<String, EagerXPathFunction> =
    mergeFunctionTables(
      StringFunctions.table,
      CryptoFunctions.table,
      LookupFunctions.table,
      BooleanFunctions.table,
      NumericFunctions.table,
      NodeSetFunctions.table,
      DateTimeFunctions.table,
      SelectFunctions.table,
      GeoFunctions.table,
    )

  /** Evaluates a function call expression [name] with raw AST [args] in [context]. */
  fun invoke(
    name: String,
    args: List<XPathExpr>,
    context: EvaluationContext,
    evaluator: XPathEvaluator,
  ): XPathValue {
    // 1. Handle lazy / special control-flow functions first
    when (name) {
      "if" -> {
        checkArgCount(name, args, 3..3)
        val cond = evaluator.evaluate(args[0], context).toBoolean()
        return if (cond) {
          evaluator.evaluate(args[1], context)
        } else {
          evaluator.evaluate(args[2], context)
        }
      }
      "once" -> {
        checkArgCount(name, args, 1..1)
        val currentVal = context.contextNode.extractValue()
        return if (!currentVal.isEmptyValue()) {
          currentVal
        } else {
          evaluator.evaluate(args[0], context)
        }
      }
      "coalesce" -> {
        checkArgCount(name, args, 1..Int.MAX_VALUE)
        for (arg in args) {
          val v = evaluator.evaluate(arg, context)
          if (!v.isEmptyValue()) {
            return v
          }
        }
        return XPathValue.Str("")
      }
      "indexed-repeat" -> {
        return evaluateIndexedRepeat(args, context, evaluator)
      }
    }

    // 2. Evaluate all arguments eagerly for standard functions
    val evaluatedArgs = args.map { evaluator.evaluate(it, context) }
    return invokeEager(name, evaluatedArgs, context)
  }

  private fun invokeEager(
    name: String,
    args: List<XPathValue>,
    context: EvaluationContext,
  ): XPathValue {
    val function =
      eagerFunctions[name] ?: throw XPathEvaluationException("Unknown XPath function '$name()'")
    return EagerCall(name, args, context).function()
  }

  private fun evaluateIndexedRepeat(
    args: List<XPathExpr>,
    context: EvaluationContext,
    evaluator: XPathEvaluator,
  ): XPathValue {
    if (args.size < 3 || args.size % 2 == 0) {
      throw XPathEvaluationException(
        "indexed-repeat requires (target, repeat1, index1, [repeat2, index2, ...])"
      )
    }
    val targetExpr = args[0]
    val repeatFilters = mutableListOf<Pair<String, Int>>()
    var i = 1
    while (i + 1 < args.size) {
      val repeatExpr = args[i]
      val indexVal = evaluator.evaluate(args[i + 1], context).toNumber().toInt()
      val repeatName =
        when (repeatExpr) {
          is XPathExpr.LocationPathExpr ->
            repeatExpr.steps.lastOrNull()?.nodeTest?.let {
              if (it is org.openforis.collectone.v2.core.forms.xpath.ast.NodeTest.NameTest) it.name
              else null
            } ?: ""
          else -> ""
        }
      repeatFilters.add(repeatName to indexVal)
      i += 2
    }

    val candidates = evaluator.evaluate(targetExpr, context)
    if (candidates !is XPathValue.NodeSet) return candidates
    val filtered =
      candidates.nodes.filter { node -> matchesIndexedRepeatAncestors(node, repeatFilters) }
    return XPathValue.NodeSet(filtered)
  }

  private fun matchesIndexedRepeatAncestors(
    node: XPathNode,
    repeatFilters: List<Pair<String, Int>>,
  ): Boolean {
    val ancestorsByName = mutableMapOf<String, Int>()
    var curr: XPathNode? = node
    while (curr != null) {
      if (curr.siblingRepeatCount > 1 || curr.repeatIndex > 1) {
        ancestorsByName[curr.name] = curr.repeatIndex
      } else if (!ancestorsByName.containsKey(curr.name)) {
        ancestorsByName[curr.name] = curr.repeatIndex
      }
      curr = curr.parent
    }
    for ((repName, targetIdx) in repeatFilters) {
      val actualIdx = ancestorsByName[repName]
      if (actualIdx != null && actualIdx != targetIdx) {
        return false
      }
    }
    return true
  }

  private fun checkArgCount(name: String, args: List<XPathExpr>, range: IntRange) {
    if (args.size !in range) {
      throw XPathEvaluationException("Function '$name' expects $range arguments, got ${args.size}")
    }
  }
}
