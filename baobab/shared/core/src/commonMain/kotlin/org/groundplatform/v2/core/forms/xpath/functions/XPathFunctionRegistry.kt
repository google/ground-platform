/*
 * Copyright 2026 The Ground Authors.
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

/**
 * 2026 The Ground Authors.
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
package org.groundplatform.v2.core.forms.xpath.functions

import groundplatform.v2.forms.ControlDef
import groundplatform.v2.forms.ViewComponent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random
import org.groundplatform.v2.core.forms.xpath.EvaluationContext
import org.groundplatform.v2.core.forms.xpath.XPathEvaluationException
import org.groundplatform.v2.core.forms.xpath.ast.XPathExpr
import org.groundplatform.v2.core.forms.xpath.eval.XPathEvaluator
import org.groundplatform.v2.core.forms.xpath.model.TemporalUtils
import org.groundplatform.v2.core.forms.xpath.model.XPathNode
import org.groundplatform.v2.core.forms.xpath.model.XPathValue

/** Registry and implementation of all standard XPath 1.0 and ODK XForms functions. */
internal object XPathFunctionRegistry {

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
  ): XPathValue =
    when (name) {
      // --- String Functions ---
      "string" -> {
        checkEagerArgCount(name, args, 0..1)
        val target = args.firstOrNull() ?: XPathValue.NodeSet(listOf(context.contextNode))
        XPathValue.Str(target.toXPathString())
      }
      "concat" -> {
        val sb = StringBuilder()
        for (arg in args) {
          if (arg is XPathValue.NodeSet) {
            for (n in arg.nodes) {
              sb.append(n.extractValue().toXPathString())
            }
          } else {
            sb.append(arg.toXPathString())
          }
        }
        XPathValue.Str(sb.toString())
      }
      "join" -> {
        checkEagerArgCount(name, args, 1..Int.MAX_VALUE)
        val sep = args[0].toXPathString()
        val tokens = mutableListOf<String>()
        for (i in 1 until args.size) {
          when (val arg = args[i]) {
            is XPathValue.NodeSet ->
              arg.nodes.forEach { tokens.add(it.extractValue().toXPathString()) }
            is XPathValue.ValueList -> arg.values.forEach { tokens.add(it.toXPathString()) }
            else -> tokens.add(arg.toXPathString())
          }
        }
        XPathValue.Str(tokens.joinToString(sep))
      }
      "substr" -> {
        checkEagerArgCount(name, args, 2..3)
        val str = args[0].toXPathString()
        val len = str.length
        val rawStart = args[1].toNumber().toInt()
        val start = (if (rawStart < 0) len + rawStart else rawStart).coerceIn(0, len)
        val end =
          if (args.size >= 3) {
            val rawEnd = args[2].toNumber().toInt()
            (if (rawEnd < 0) len + rawEnd else rawEnd).coerceIn(start, len)
          } else {
            len
          }
        XPathValue.Str(str.substring(start, end))
      }
      "substring" -> {
        // Standard XPath 1.0 1-based substring(str, start, length?)
        checkEagerArgCount(name, args, 2..3)
        val str = args[0].toXPathString()
        val startNum = round(args[1].toNumber())
        val lenNum = if (args.size >= 3) round(args[2].toNumber()) else Double.POSITIVE_INFINITY
        if (startNum.isNaN() || lenNum.isNaN() || lenNum <= 0.0) {
          XPathValue.Str("")
        } else {
          val startIdx = (startNum.toInt() - 1).coerceAtLeast(0)
          val endIdx =
            if (lenNum.isInfinite()) str.length
            else (startNum.toInt() - 1 + lenNum.toInt()).coerceAtMost(str.length)
          if (startIdx >= str.length || startIdx >= endIdx) XPathValue.Str("")
          else XPathValue.Str(str.substring(startIdx, endIdx))
        }
      }
      "substring-before" -> {
        checkEagerArgCount(name, args, 2..2)
        val target = args[0].toXPathString()
        val prefix = args[1].toXPathString()
        if (prefix.isEmpty() || !target.contains(prefix)) XPathValue.Str("")
        else XPathValue.Str(target.substringBefore(prefix))
      }
      "substring-after" -> {
        checkEagerArgCount(name, args, 2..2)
        val target = args[0].toXPathString()
        val suffix = args[1].toXPathString()
        if (suffix.isEmpty()) XPathValue.Str(target)
        else if (!target.contains(suffix)) XPathValue.Str("")
        else XPathValue.Str(target.substringAfter(suffix))
      }
      "translate" -> {
        checkEagerArgCount(name, args, 3..3)
        val src = args[0].toXPathString()
        val fromChars = args[1].toXPathString()
        val toChars = args[2].toXPathString()
        val sb = StringBuilder()
        for (ch in src) {
          val idx = fromChars.indexOf(ch)
          if (idx < 0) {
            sb.append(ch)
          } else if (idx < toChars.length) {
            sb.append(toChars[idx])
          }
        }
        XPathValue.Str(sb.toString())
      }
      "string-length" -> {
        checkEagerArgCount(name, args, 0..1)
        val target = args.firstOrNull() ?: XPathValue.NodeSet(listOf(context.contextNode))
        XPathValue.Number(target.toXPathString().length.toDouble())
      }
      "normalize-space" -> {
        checkEagerArgCount(name, args, 0..1)
        val target = args.firstOrNull() ?: XPathValue.NodeSet(listOf(context.contextNode))
        val normalized = target.toXPathString().trim().replace(Regex("\\s+"), " ")
        XPathValue.Str(normalized)
      }
      "contains" -> {
        checkEagerArgCount(name, args, 2..2)
        XPathValue.Bool(args[0].toXPathString().contains(args[1].toXPathString()))
      }
      "starts-with" -> {
        checkEagerArgCount(name, args, 2..2)
        XPathValue.Bool(args[0].toXPathString().startsWith(args[1].toXPathString()))
      }
      "ends-with" -> {
        checkEagerArgCount(name, args, 2..2)
        XPathValue.Bool(args[0].toXPathString().endsWith(args[1].toXPathString()))
      }
      "uuid" -> {
        checkEagerArgCount(name, args, 0..1)
        val len = args.firstOrNull()?.toNumber()?.toInt()
        XPathValue.Str(CryptoAndPrngUtils.generateUuidOrToken(len, context.randomSeed))
      }
      "digest" -> {
        checkEagerArgCount(name, args, 2..3)
        val payload = args[0].toXPathString()
        val algo = args[1].toXPathString()
        val encoding = args.getOrNull(2)?.toXPathString() ?: "hex"
        XPathValue.Str(CryptoAndPrngUtils.digest(payload, algo, encoding))
      }
      "base64-decode" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Str(CryptoAndPrngUtils.base64Decode(args[0].toXPathString()))
      }
      "pulldata" -> {
        checkEagerArgCount(name, args, 4..4)
        val instanceId = args[0].toXPathString()
        val returnField = args[1].toXPathString()
        val keyField = args[2].toXPathString()
        val keyVal = args[3].toXPathString()
        evaluatePullData(instanceId, returnField, keyField, keyVal, context)
      }

      // --- Boolean Functions ---
      "true" -> {
        checkEagerArgCount(name, args, 0..0)
        XPathValue.Bool(true)
      }
      "false" -> {
        checkEagerArgCount(name, args, 0..0)
        XPathValue.Bool(false)
      }
      "boolean" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Bool(args[0].toBoolean())
      }
      "boolean-from-string" -> {
        checkEagerArgCount(name, args, 1..1)
        val str = args[0].toXPathString().trim()
        XPathValue.Bool(str == "true" || str == "1")
      }
      "not" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Bool(!args[0].toBoolean())
      }
      "regex" -> {
        checkEagerArgCount(name, args, 2..2)
        val target = args[0].toXPathString()
        val pattern = args[1].toXPathString()
        val matches =
          try {
            Regex(pattern).matches(target)
          } catch (e: Exception) {
            false
          }
        XPathValue.Bool(matches)
      }
      "checklist" -> {
        checkEagerArgCount(name, args, 2..Int.MAX_VALUE)
        val min = args[0].toNumber().toInt()
        val max = args[1].toNumber().toInt()
        var truthyCount = 0
        for (i in 2 until args.size) {
          val arg = args[i]
          if (arg is XPathValue.NodeSet) {
            truthyCount += arg.nodes.count { it.extractValue().toBoolean() }
          } else if (arg.toBoolean()) {
            truthyCount++
          }
        }
        val minOk = min < 0 || truthyCount >= min
        val maxOk = max < 0 || truthyCount <= max
        XPathValue.Bool(minOk && maxOk)
      }
      "weighted-checklist" -> {
        checkEagerArgCount(name, args, 2..Int.MAX_VALUE)
        val min = args[0].toNumber()
        val max = args[1].toNumber()
        var totalWeight = 0.0
        var i = 2
        while (i + 1 < args.size) {
          val valArg = args[i]
          val weightArg = args[i + 1]
          if (valArg is XPathValue.NodeSet && weightArg is XPathValue.NodeSet) {
            val count = minOf(valArg.nodes.size, weightArg.nodes.size)
            for (k in 0 until count) {
              if (valArg.nodes[k].extractValue().toBoolean()) {
                totalWeight += weightArg.nodes[k].extractValue().toNumber()
              }
            }
          } else if (valArg.toBoolean()) {
            totalWeight += weightArg.toNumber()
          }
          i += 2
        }
        val minOk = min < 0.0 || totalWeight >= min
        val maxOk = max < 0.0 || totalWeight <= max
        XPathValue.Bool(minOk && maxOk)
      }

      // --- Number Functions ---
      "number" -> {
        checkEagerArgCount(name, args, 0..1)
        val target = args.firstOrNull() ?: XPathValue.NodeSet(listOf(context.contextNode))
        XPathValue.Number(target.toNumber())
      }
      "random" -> {
        checkEagerArgCount(name, args, 0..0)
        val rnd = if (context.randomSeed != null) Random(context.randomSeed) else Random.Default
        XPathValue.Number(rnd.nextDouble())
      }
      "int" -> {
        checkEagerArgCount(name, args, 1..1)
        val d = args[0].toNumber()
        XPathValue.Number(if (d.isNaN()) Double.NaN else d.toLong().toDouble())
      }
      "floor" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Number(floor(args[0].toNumber()))
      }
      "ceiling" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Number(ceil(args[0].toNumber()))
      }
      "sum" -> {
        checkEagerArgCount(name, args, 1..1)
        val arg = args[0]
        if (arg !is XPathValue.NodeSet) {
          throw XPathEvaluationException("Function 'sum' requires a node-set argument")
        }
        var sum = 0.0
        for (node in arg.nodes) {
          val v = node.extractValue()
          if (!v.isEmptyValue()) {
            sum += v.toNumber()
          }
        }
        XPathValue.Number(sum)
      }
      "max" -> {
        if (args.isEmpty()) return XPathValue.Number(Double.NaN)
        val nums = collectNumbers(args)
        if (nums.isEmpty()) XPathValue.Number(Double.NaN)
        else if (nums.any { it.isNaN() }) XPathValue.Number(Double.NaN)
        else XPathValue.Number(nums.maxOrNull() ?: Double.NaN)
      }
      "min" -> {
        if (args.isEmpty()) return XPathValue.Number(Double.NaN)
        val nums = collectNumbers(args)
        if (nums.isEmpty()) XPathValue.Number(Double.NaN)
        else if (nums.any { it.isNaN() }) XPathValue.Number(Double.NaN)
        else XPathValue.Number(nums.minOrNull() ?: Double.NaN)
      }
      "round" -> {
        checkEagerArgCount(name, args, 1..2)
        val num = args[0].toNumber()
        val places = args.getOrNull(1)?.toNumber()?.toInt() ?: 0
        if (num.isNaN() || num.isInfinite()) {
          XPathValue.Number(num)
        } else {
          val factor = 10.0.pow(places)
          val scaled = num * factor
          // Half-up rounding per XPath / ODK specification
          val rounded = if (scaled >= 0.0) floor(scaled + 0.5) else ceil(scaled - 0.5)
          XPathValue.Number(rounded / factor)
        }
      }
      "pow" -> {
        checkEagerArgCount(name, args, 2..2)
        XPathValue.Number(args[0].toNumber().pow(args[1].toNumber()))
      }
      "log" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Number(ln(args[0].toNumber()))
      }
      "log10" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Number(log10(args[0].toNumber()))
      }
      "abs" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Number(abs(args[0].toNumber()))
      }
      "sin" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Number(sin(args[0].toNumber()))
      }
      "cos" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Number(cos(args[0].toNumber()))
      }
      "tan" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Number(tan(args[0].toNumber()))
      }
      "asin" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Number(asin(args[0].toNumber()))
      }
      "acos" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Number(acos(args[0].toNumber()))
      }
      "atan" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Number(atan(args[0].toNumber()))
      }
      "atan2" -> {
        checkEagerArgCount(name, args, 2..2)
        XPathValue.Number(atan2(args[0].toNumber(), args[1].toNumber()))
      }
      "sqrt" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Number(sqrt(args[0].toNumber()))
      }
      "exp" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Number(exp(args[0].toNumber()))
      }
      "exp10" -> {
        checkEagerArgCount(name, args, 1..1)
        XPathValue.Number(10.0.pow(args[0].toNumber()))
      }
      "pi" -> {
        checkEagerArgCount(name, args, 0..0)
        XPathValue.Number(PI)
      }

      // --- Node-set Functions ---
      "count" -> {
        checkEagerArgCount(name, args, 1..1)
        val arg = args[0]
        if (arg !is XPathValue.NodeSet) {
          throw XPathEvaluationException("Function 'count' requires a node-set argument")
        }
        XPathValue.Number(arg.nodes.size.toDouble())
      }
      "count-non-empty" -> {
        checkEagerArgCount(name, args, 1..1)
        val arg = args[0]
        if (arg !is XPathValue.NodeSet) {
          throw XPathEvaluationException("Function 'count-non-empty' requires a node-set argument")
        }
        val count = arg.nodes.count { !it.extractValue().isEmptyValue() }
        XPathValue.Number(count.toDouble())
      }
      "position" -> {
        checkEagerArgCount(name, args, 0..1)
        if (args.isEmpty()) {
          XPathValue.Number(context.contextPosition.toDouble())
        } else {
          val arg = args[0]
          if (arg is XPathValue.NodeSet && arg.nodes.isNotEmpty()) {
            val target = arg.nodes.first()
            val pos =
              if (
                target is XPathNode.InstanceRootElementNode &&
                  context.contextNode.parent === target &&
                  (context.contextNode.siblingRepeatCount > 1 || context.contextPosition > 1)
              ) {
                context.contextPosition
              } else {
                target.repeatIndex
              }
            XPathValue.Number(pos.toDouble())
          } else {
            XPathValue.Number(context.contextPosition.toDouble())
          }
        }
      }
      "last" -> {
        checkEagerArgCount(name, args, 0..0)
        XPathValue.Number(context.contextSize.toDouble())
      }
      "instance" -> {
        checkEagerArgCount(name, args, 1..1)
        val instanceId = args[0].toXPathString()
        val root =
          context.secondaryInstanceProvider.resolveRoot(instanceId)
            ?: throw XPathEvaluationException("Secondary instance '$instanceId' not found")
        XPathValue.NodeSet(listOf(root))
      }
      "current" -> {
        checkEagerArgCount(name, args, 0..0)
        XPathValue.NodeSet(listOf(context.currentQuestionNode))
      }
      "randomize" -> {
        checkEagerArgCount(name, args, 1..2)
        val arg = args[0]
        if (arg !is XPathValue.NodeSet) {
          throw XPathEvaluationException(
            "Function 'randomize' requires a node-set as its first argument"
          )
        }
        val seedStr = args.getOrNull(1)?.toXPathString()
        val shuffled = CryptoAndPrngUtils.randomizeList(arg.nodes, seedStr, context.randomSeed)
        XPathValue.NodeSet(shuffled)
      }

      // --- Date and Time Functions ---
      "today" -> {
        checkEagerArgCount(name, args, 0..0)
        val epochDays = floor(context.clockEpochMillis() / 86400000.0).toLong()
        val date = TemporalUtils.epochDaysToDate(epochDays)
        XPathValue.DateVal(date)
      }
      "now" -> {
        checkEagerArgCount(name, args, 0..0)
        val epochDays = context.clockEpochMillis() / 86400000.0
        val instant = TemporalUtils.epochDaysToInstant(epochDays)
        XPathValue.TimestampVal(instant)
      }
      "date" -> {
        checkEagerArgCount(name, args, 1..1)
        val arg = args[0].unwrapNodeSet()
        when (arg) {
          is XPathValue.DateVal -> arg
          else -> {
            val str = arg.toXPathString()
            TemporalUtils.tryParseDate(str)?.let {
              return XPathValue.DateVal(it)
            }
            val num = arg.toNumber()
            if (!num.isNaN()) {
              XPathValue.DateVal(TemporalUtils.epochDaysToDate(floor(num).toLong()))
            } else {
              XPathValue.Str("")
            }
          }
        }
      }
      "date-time" -> {
        checkEagerArgCount(name, args, 1..1)
        val arg = args[0].unwrapNodeSet()
        when (arg) {
          is XPathValue.TimestampVal -> arg
          is XPathValue.DateVal ->
            XPathValue.TimestampVal(TemporalUtils.epochDaysToInstant(arg.toNumber()))
          else -> {
            val epochDays = TemporalUtils.tryParseToEpochDays(arg.toXPathString()) ?: arg.toNumber()
            if (!epochDays.isNaN()) {
              XPathValue.TimestampVal(TemporalUtils.epochDaysToInstant(epochDays))
            } else {
              XPathValue.Str("")
            }
          }
        }
      }
      "decimal-date-time" -> {
        checkEagerArgCount(name, args, 1..1)
        val arg = args[0].unwrapNodeSet()
        XPathValue.Number(arg.toNumber())
      }
      "decimal-time" -> {
        checkEagerArgCount(name, args, 1..1)
        val arg = args[0].unwrapNodeSet()
        when (arg) {
          is XPathValue.TimeVal -> XPathValue.Number(TemporalUtils.timeOfDayToDecimal(arg.value))
          else -> {
            val time = TemporalUtils.tryParseTime(arg.toXPathString())
            if (time != null) XPathValue.Number(TemporalUtils.timeOfDayToDecimal(time))
            else XPathValue.Number(Double.NaN)
          }
        }
      }
      "format-date",
      "format-date-time" -> {
        checkEagerArgCount(name, args, 2..2)
        val epochDays = args[0].toNumber()
        val pattern = args[1].toXPathString()
        XPathValue.Str(TemporalUtils.formatPattern(epochDays, pattern))
      }

      // --- Select, Translation, Repeat & Geographic Functions ---
      "selected" -> {
        checkEagerArgCount(name, args, 2..2)
        val listArg = args[0].unwrapNodeSet()
        val choice = args[1].toXPathString().trim()
        val selected =
          when (listArg) {
            is XPathValue.ValueList -> listArg.values.any { it.toXPathString() == choice }
            else -> listArg.toXPathString().trim().split(Regex("\\s+")).contains(choice)
          }
        XPathValue.Bool(selected)
      }
      "selected-at" -> {
        checkEagerArgCount(name, args, 2..2)
        val listArg = args[0].unwrapNodeSet()
        val idx = args[1].toNumber().toInt()
        val tokens =
          when (listArg) {
            is XPathValue.ValueList -> listArg.values.map { it.toXPathString() }
            else -> listArg.toXPathString().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
          }
        val item = if (idx in tokens.indices) tokens[idx] else ""
        XPathValue.Str(item)
      }
      "count-selected" -> {
        checkEagerArgCount(name, args, 1..1)
        val listArg = args[0].unwrapNodeSet()
        val count =
          when (listArg) {
            is XPathValue.ValueList -> listArg.values.size
            else -> {
              val str = listArg.toXPathString().trim()
              if (str.isEmpty()) 0 else str.split(Regex("\\s+")).size
            }
          }
        XPathValue.Number(count.toDouble())
      }
      "jr:itext" -> {
        checkEagerArgCount(name, args, 1..1)
        val textId = args[0].toXPathString()
        XPathValue.Str(resolveItext(textId, context))
      }
      "jr:choice-name" -> {
        checkEagerArgCount(name, args, 2..2)
        evaluateChoiceName(args[0], args[1], context)
      }
      "area" -> {
        checkEagerArgCount(name, args, 1..1)
        val points = GeoUtils.extractPoints(args[0])
        XPathValue.Number(GeoUtils.calculateAreaSquareMeters(points))
      }
      "distance" -> {
        checkEagerArgCount(name, args, 1..2)
        if (args.size == 2) {
          val p1 = GeoUtils.extractPoints(args[0]).firstOrNull()
          val p2 = GeoUtils.extractPoints(args[1]).firstOrNull()
          if (p1 != null && p2 != null) {
            XPathValue.Number(GeoUtils.haversineDistanceMeters(p1, p2))
          } else {
            XPathValue.Number(0.0)
          }
        } else {
          val points = GeoUtils.extractPoints(args[0])
          XPathValue.Number(GeoUtils.totalDistanceMeters(points))
        }
      }
      "geofence" -> {
        checkEagerArgCount(name, args, 2..2)
        val pt = GeoUtils.extractPoints(args[0]).firstOrNull()
        val polygon = GeoUtils.extractPoints(args[1])
        val inside =
          if (pt != null && polygon.size >= 3) GeoUtils.isPointInPolygon(pt, polygon) else false
        XPathValue.Bool(inside)
      }
      "intersects" -> {
        checkEagerArgCount(name, args, 1..2)
        val points = GeoUtils.extractPoints(args[0])
        XPathValue.Bool(GeoUtils.hasSelfIntersection(points))
      }
      else -> throw XPathEvaluationException("Unknown XPath function '$name()'")
    }

  private fun evaluatePullData(
    instanceId: String,
    returnField: String,
    keyField: String,
    keyVal: String,
    context: EvaluationContext,
  ): XPathValue {
    // 1. Try O(1) indexed equality pushdown on SecondaryInstanceProvider
    val pushdownRows =
      context.secondaryInstanceProvider.lookupByEquality(instanceId, keyField, keyVal)
    if (pushdownRows != null) {
      val firstRow = pushdownRows.firstOrNull() ?: return XPathValue.Str("")
      val cell = firstRow[returnField] ?: return XPathValue.Str("")
      return XPathValue.fromTypedValue(cell)
    }

    // 2. Fallback: traverse virtual XPathNode tree
    val root =
      context.secondaryInstanceProvider.resolveRoot(instanceId) ?: return XPathValue.Str("")
    val itemNodes =
      root.children("root").flatMap { it.children("item") }.ifEmpty { root.children("item") }
    for (item in itemNodes) {
      val keyChild = item.children(keyField).firstOrNull()
      if (keyChild != null && keyChild.extractValue().toXPathString() == keyVal) {
        val retChild = item.children(returnField).firstOrNull()
        return retChild?.extractValue() ?: XPathValue.Str("")
      }
    }
    return XPathValue.Str("")
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
              if (it is org.groundplatform.v2.core.forms.xpath.ast.NodeTest.NameTest) it.name
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

  private fun resolveItext(textId: String, context: EvaluationContext): String {
    val catalog = context.translations ?: return textId
    val languages = catalog.languages
    if (languages.isEmpty()) return textId

    val activeLang =
      languages.find { it.language.equals(context.activeLanguage, ignoreCase = true) }
        ?: languages.find { it.is_default }
        ?: languages.first()

    return activeLang.strings[textId]?.value_ ?: ""
  }

  private fun evaluateChoiceName(
    arg0: XPathValue,
    arg1: XPathValue,
    context: EvaluationContext,
  ): XPathValue {
    // Supports both ODK signature: jr:choice-name(choice_val, 'field_path')
    // and ProtoForms doc signature: jr:choice-name(node_target, choice_val)
    val choiceVal: String
    val fieldPath: String
    if (arg0 is XPathValue.NodeSet && arg0.nodes.isNotEmpty()) {
      fieldPath = arg0.nodes.first().name
      choiceVal = arg1.toXPathString()
    } else {
      val s0 = arg0.toXPathString()
      val s1 = arg1.toXPathString()
      if (s1.startsWith("/") || s1.contains("/")) {
        choiceVal = s0
        fieldPath = s1
      } else {
        fieldPath = s0
        choiceVal = s1
      }
    }

    val controls = mutableListOf<ControlDef>()
    fun collectControls(components: List<ViewComponent>) {
      for (comp in components) {
        comp.control?.let { controls.add(it) }
        comp.group?.let { collectControls(it.components) }
        comp.repeat?.let { collectControls(it.components) }
      }
    }
    context.formDef?.view?.components?.let { collectControls(it) }

    val targetFieldName = fieldPath.substringAfterLast('/')
    val matchingControl = controls.find {
      it.field_ref == fieldPath || it.field_ref.substringAfterLast('/') == targetFieldName
    }
    val choiceItem = matchingControl?.choices?.find { it.value_ == choiceVal }
    if (choiceItem != null) {
      val label = choiceItem.label
      if (label != null) {
        if (label.text_id.isNotEmpty()) {
          val itextVal = resolveItext(label.text_id, context)
          if (itextVal.isNotEmpty()) return XPathValue.Str(itextVal)
        }
        if (label.text.isNotEmpty()) return XPathValue.Str(label.text)
      }
    }
    return XPathValue.Str(choiceVal)
  }

  private fun collectNumbers(args: List<XPathValue>): List<Double> {
    val nums = mutableListOf<Double>()
    for (arg in args) {
      when (arg) {
        is XPathValue.NodeSet -> {
          for (n in arg.nodes) {
            val v = n.extractValue()
            if (!v.isEmptyValue()) {
              nums.add(v.toNumber())
            }
          }
        }
        is XPathValue.ValueList -> arg.values.forEach { nums.add(it.toNumber()) }
        else -> if (!arg.isEmptyValue()) nums.add(arg.toNumber())
      }
    }
    return nums
  }

  private fun checkArgCount(name: String, args: List<XPathExpr>, range: IntRange) {
    if (args.size !in range) {
      throw XPathEvaluationException("Function '$name' expects $range arguments, got ${args.size}")
    }
  }

  private fun checkEagerArgCount(name: String, args: List<XPathValue>, range: IntRange) {
    if (args.size !in range) {
      throw XPathEvaluationException("Function '$name' expects $range arguments, got ${args.size}")
    }
  }
}
