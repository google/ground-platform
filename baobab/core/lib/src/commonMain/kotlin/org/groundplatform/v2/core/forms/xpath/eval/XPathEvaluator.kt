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
package org.groundplatform.v2.core.forms.xpath.eval

import org.groundplatform.v2.core.forms.xpath.EvaluationContext
import org.groundplatform.v2.core.forms.xpath.XPathEvaluationException
import org.groundplatform.v2.core.forms.xpath.ast.Axis
import org.groundplatform.v2.core.forms.xpath.ast.BinaryOp
import org.groundplatform.v2.core.forms.xpath.ast.LocationStep
import org.groundplatform.v2.core.forms.xpath.ast.NodeTest
import org.groundplatform.v2.core.forms.xpath.ast.XPathExpr
import org.groundplatform.v2.core.forms.xpath.functions.XPathFunctionRegistry
import org.groundplatform.v2.core.forms.xpath.model.XPathNode
import org.groundplatform.v2.core.forms.xpath.model.XPathValue

/** Runtime AST evaluator for compiled XPath expressions. */
internal class XPathEvaluator {

  fun evaluate(expr: XPathExpr, context: EvaluationContext): XPathValue =
    when (expr) {
      is XPathExpr.LiteralNumber -> XPathValue.Number(expr.value)
      is XPathExpr.LiteralString -> XPathValue.Str(expr.value)
      is XPathExpr.VariableReferenceExpr -> evaluateVariable(expr.name, context)
      is XPathExpr.UnaryMinusExpr -> XPathValue.Number(-evaluate(expr.operand, context).toNumber())
      is XPathExpr.BinaryExpr -> evaluateBinary(expr, context)
      is XPathExpr.FunctionCallExpr ->
        XPathFunctionRegistry.invoke(expr.name, expr.args, context, this)
      is XPathExpr.LocationPathExpr -> evaluateLocationPath(expr, context)
      is XPathExpr.FilterPathExpr -> evaluateFilterPath(expr, context)
    }

  private fun evaluateVariable(name: String, context: EvaluationContext): XPathValue {
    context.variables[name]?.let {
      return it
    }

    // Support XLSForm ${field_name} references by searching contextNode then root instance element
    val fromContext = findDescendantsByName(context.contextNode, name)
    if (fromContext.isNotEmpty()) {
      return XPathValue.NodeSet(fromContext)
    }
    val rootElem =
      if (context.rootNode is XPathNode.DocumentRootNode) context.rootNode.instanceElementNode
      else context.rootNode
    val fromRoot = findDescendantsByName(rootElem, name)
    return XPathValue.NodeSet(fromRoot)
  }

  private fun findDescendantsByName(start: XPathNode, targetName: String): List<XPathNode> {
    val result = mutableListOf<XPathNode>()
    fun search(node: XPathNode) {
      val direct = node.children(targetName)
      result.addAll(direct)
      for (child in node.children(null)) {
        if (child !in direct) {
          search(child)
        }
      }
    }
    search(start)
    return result
  }

  private fun evaluateBinary(expr: XPathExpr.BinaryExpr, context: EvaluationContext): XPathValue {
    // Short-circuit logical operators
    if (expr.op == BinaryOp.AND) {
      val leftBool = evaluate(expr.left, context).toBoolean()
      if (!leftBool) return XPathValue.Bool(false)
      return XPathValue.Bool(evaluate(expr.right, context).toBoolean())
    }
    if (expr.op == BinaryOp.OR) {
      val leftBool = evaluate(expr.left, context).toBoolean()
      if (leftBool) return XPathValue.Bool(true)
      return XPathValue.Bool(evaluate(expr.right, context).toBoolean())
    }
    if (expr.op == BinaryOp.UNION) {
      val leftVal = evaluate(expr.left, context)
      val rightVal = evaluate(expr.right, context)
      if (leftVal !is XPathValue.NodeSet || rightVal !is XPathValue.NodeSet) {
        throw XPathEvaluationException("Union operator '|' requires node-set operands")
      }
      val combined = linkedSetOf<XPathNode>()
      combined.addAll(leftVal.nodes)
      combined.addAll(rightVal.nodes)
      return XPathValue.NodeSet(combined.toList())
    }

    val left = evaluate(expr.left, context)
    val right = evaluate(expr.right, context)

    return when (expr.op) {
      BinaryOp.ADD -> XPathValue.Number(left.toNumber() + right.toNumber())
      BinaryOp.SUB -> XPathValue.Number(left.toNumber() - right.toNumber())
      BinaryOp.MUL -> XPathValue.Number(left.toNumber() * right.toNumber())
      BinaryOp.DIV -> XPathValue.Number(left.toNumber() / right.toNumber())
      BinaryOp.MOD -> XPathValue.Number(left.toNumber() % right.toNumber())
      BinaryOp.EQ,
      BinaryOp.NEQ,
      BinaryOp.LT,
      BinaryOp.LTE,
      BinaryOp.GT,
      BinaryOp.GTE -> XPathValue.Bool(evaluateComparison(left, right, expr.op))
      BinaryOp.AND,
      BinaryOp.OR,
      BinaryOp.UNION -> throw XPathEvaluationException("Unhandled binary operator ${expr.op}")
    }
  }

  /**
   * Implements XPath 1.0 Section 3.4 existential comparison semantics with native support for
   * ProtoForms strongly-typed scalars (`DateVal`, `TimestampVal`, `Bool`, `Number`, `Str`).
   */
  private fun evaluateComparison(left: XPathValue, right: XPathValue, op: BinaryOp): Boolean {
    // 1. Both are NodeSets: true if ANY node in left and ANY node in right satisfy op
    if (left is XPathValue.NodeSet && right is XPathValue.NodeSet) {
      for (ln in left.nodes) {
        for (rn in right.nodes) {
          if (compareScalars(ln.extractValue(), rn.extractValue(), op)) {
            return true
          }
        }
      }
      return false
    }

    // 2. Left is NodeSet, Right is Scalar
    if (left is XPathValue.NodeSet) {
      if (left.nodes.isEmpty() && right is XPathValue.Bool) {
        return compareBooleans(false, right.value, op)
      }
      for (ln in left.nodes) {
        if (compareScalars(ln.extractValue(), right, op)) {
          return true
        }
      }
      return false
    }

    // 3. Right is NodeSet, Left is Scalar
    if (right is XPathValue.NodeSet) {
      if (right.nodes.isEmpty() && left is XPathValue.Bool) {
        return compareBooleans(left.value, false, op)
      }
      for (rn in right.nodes) {
        if (compareScalars(left, rn.extractValue(), op)) {
          return true
        }
      }
      return false
    }

    // 4. Both are non-NodeSet values
    return compareScalars(left, right, op)
  }

  private fun compareScalars(a: XPathValue, b: XPathValue, op: BinaryOp): Boolean {
    // Relational operators (<, <=, >, >=)
    if (op == BinaryOp.LT || op == BinaryOp.LTE || op == BinaryOp.GT || op == BinaryOp.GTE) {
      val numA = a.toNumber()
      val numB = b.toNumber()
      if (numA.isNaN() || numB.isNaN()) return false
      return when (op) {
        BinaryOp.LT -> numA < numB
        BinaryOp.LTE -> numA <= numB
        BinaryOp.GT -> numA > numB
        BinaryOp.GTE -> numA >= numB
      }
    }

    // Equality operators (=, !=)
    if (a is XPathValue.Bool || b is XPathValue.Bool) {
      return compareBooleans(a.toBoolean(), b.toBoolean(), op)
    }

    // Temporal comparison if either is DateVal or TimestampVal
    if (
      a is XPathValue.DateVal ||
        b is XPathValue.DateVal ||
        a is XPathValue.TimestampVal ||
        b is XPathValue.TimestampVal
    ) {
      val daysA = a.toNumber()
      val daysB = b.toNumber()
      if (!daysA.isNaN() && !daysB.isNaN()) {
        return if (op == BinaryOp.EQ) daysA == daysB else daysA != daysB
      }
    }

    if (a is XPathValue.Number || b is XPathValue.Number) {
      val numA = a.toNumber()
      val numB = b.toNumber()
      return if (op == BinaryOp.EQ) numA == numB else numA != numB
    }

    val strA = a.toXPathString()
    val strB = b.toXPathString()
    return if (op == BinaryOp.EQ) strA == strB else strA != strB
  }

  private fun compareBooleans(a: Boolean, b: Boolean, op: BinaryOp): Boolean =
    when (op) {
      BinaryOp.EQ -> a == b
      BinaryOp.NEQ -> a != b
      else -> {
        val numA = if (a) 1.0 else 0.0
        val numB = if (b) 1.0 else 0.0
        when (op) {
          BinaryOp.LT -> numA < numB
          BinaryOp.LTE -> numA <= numB
          BinaryOp.GT -> numA > numB
          BinaryOp.GTE -> numA >= numB
          else -> false
        }
      }
    }

  private fun evaluateLocationPath(
    expr: XPathExpr.LocationPathExpr,
    context: EvaluationContext,
  ): XPathValue {
    val startNodes: List<XPathNode> =
      if (expr.isAbsolute) {
        listOf(context.rootNode)
      } else {
        listOf(context.contextNode)
      }
    val nodes = evaluateSteps(startNodes, expr.steps, context)
    return XPathValue.NodeSet(nodes)
  }

  private fun evaluateFilterPath(
    expr: XPathExpr.FilterPathExpr,
    context: EvaluationContext,
  ): XPathValue {
    var currentVal = evaluate(expr.base, context)
    if (expr.predicates.isNotEmpty()) {
      if (currentVal !is XPathValue.NodeSet) {
        throw XPathEvaluationException("Predicates can only be applied to node-sets")
      }
      var filtered = currentVal.nodes
      for (pred in expr.predicates) {
        filtered = applyPredicate(filtered, pred, context)
      }
      currentVal = XPathValue.NodeSet(filtered)
    }

    if (expr.trailingSteps.isEmpty()) {
      return currentVal
    }
    if (currentVal !is XPathValue.NodeSet) {
      throw XPathEvaluationException("Path steps can only be applied to node-sets")
    }
    val finalNodes = evaluateSteps(currentVal.nodes, expr.trailingSteps, context)
    return XPathValue.NodeSet(finalNodes)
  }

  private fun evaluateSteps(
    initialNodes: List<XPathNode>,
    steps: List<LocationStep>,
    context: EvaluationContext,
  ): List<XPathNode> {
    var currentNodes = initialNodes
    for (step in steps) {
      val stepCandidates = mutableListOf<XPathNode>()
      for (node in currentNodes) {
        val expanded = expandAxisAndNodeTest(node, step.axis, step.nodeTest)
        var filtered = expanded
        for (pred in step.predicates) {
          filtered = applyPredicate(filtered, pred, context)
        }
        stepCandidates.addAll(filtered)
      }
      currentNodes = stepCandidates
    }
    return currentNodes
  }

  private fun expandAxisAndNodeTest(
    node: XPathNode,
    axis: Axis,
    nodeTest: NodeTest,
  ): List<XPathNode> {
    val axisNodes: List<XPathNode> =
      when (axis) {
        Axis.SELF -> listOf(node)
        Axis.PARENT -> listOfNotNull(node.parent)
        Axis.CHILD -> {
          when (nodeTest) {
            is NodeTest.NameTest -> node.children(nodeTest.name)
            else -> node.children(null)
          }
        }
        Axis.DESCENDANT -> collectDescendants(node, includeSelf = false)
        Axis.DESCENDANT_OR_SELF -> collectDescendants(node, includeSelf = true)
        Axis.ANCESTOR -> collectAncestors(node, includeSelf = false)
        Axis.ANCESTOR_OR_SELF -> collectAncestors(node, includeSelf = true)
        Axis.FOLLOWING_SIBLING -> collectSiblings(node, following = true)
        Axis.PRECEDING_SIBLING -> collectSiblings(node, following = false)
      }

    return axisNodes.filter { matchesNodeTest(it, nodeTest) }
  }

  private fun matchesNodeTest(node: XPathNode, nodeTest: NodeTest): Boolean =
    when (nodeTest) {
      is NodeTest.AnyNodeTest,
      is NodeTest.TextNodeTest -> true
      is NodeTest.WildcardTest -> node !is XPathNode.DocumentRootNode
      is NodeTest.NameTest -> {
        if (node is XPathNode.InstanceRootElementNode) {
          node.matchesName(nodeTest.name)
        } else {
          node.name == nodeTest.name
        }
      }
    }

  private fun collectDescendants(node: XPathNode, includeSelf: Boolean): List<XPathNode> {
    val out = mutableListOf<XPathNode>()
    if (includeSelf) out.add(node)
    for (child in node.children(null)) {
      out.addAll(collectDescendants(child, includeSelf = true))
    }
    return out
  }

  private fun collectAncestors(node: XPathNode, includeSelf: Boolean): List<XPathNode> {
    val out = mutableListOf<XPathNode>()
    var curr: XPathNode? = if (includeSelf) node else node.parent
    while (curr != null) {
      out.add(curr)
      curr = curr.parent
    }
    return out
  }

  private fun collectSiblings(node: XPathNode, following: Boolean): List<XPathNode> {
    val parent = node.parent ?: return emptyList()
    val allSiblings = parent.children(null)
    val idx = allSiblings.indexOf(node)
    if (idx < 0) return emptyList()
    return if (following) {
      allSiblings.subList(idx + 1, allSiblings.size)
    } else {
      allSiblings.subList(0, idx)
    }
  }

  private fun applyPredicate(
    candidates: List<XPathNode>,
    predicate: XPathExpr,
    outerContext: EvaluationContext,
  ): List<XPathNode> {
    if (candidates.isEmpty()) return emptyList()

    val size = candidates.size
    val matched = mutableListOf<XPathNode>()
    for ((idx, candidate) in candidates.withIndex()) {
      val position1Based = idx + 1
      val predContext =
        outerContext.withContextNode(newNode = candidate, position = position1Based, size = size)
      val predResult = evaluate(predicate, predContext)
      val include =
        when (predResult) {
          is XPathValue.Number -> {
            // Standard XPath 1.0 rule: A numeric predicate result [N] tests position() == N
            val targetPos = predResult.value
            !targetPos.isNaN() && targetPos == position1Based.toDouble()
          }
          else -> predResult.toBoolean()
        }
      if (include) {
        matched.add(candidate)
      }
    }
    return matched
  }
}
