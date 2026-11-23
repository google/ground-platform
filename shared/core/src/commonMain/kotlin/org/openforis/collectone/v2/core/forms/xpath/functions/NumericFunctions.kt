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
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random
import org.openforis.collectone.v2.core.forms.xpath.XPathEvaluationException
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

/** XPath 1.0 number functions (section 4.4) and the ODK math extensions. */
internal object NumericFunctions {

  val table: Map<String, EagerXPathFunction> = xpathFunctionTable {
    define("number") {
      checkEagerArgCount(name, args, 0..1)
      val target = args.firstOrNull() ?: XPathValue.NodeSet(listOf(context.contextNode))
      XPathValue.Number(target.toNumber())
    }
    define("random") {
      checkEagerArgCount(name, args, 0..0)
      val rnd = if (context.randomSeed != null) Random(context.randomSeed) else Random.Default
      XPathValue.Number(rnd.nextDouble())
    }
    define("int") {
      checkEagerArgCount(name, args, 1..1)
      val d = args[0].toNumber()
      XPathValue.Number(if (d.isNaN()) Double.NaN else d.toLong().toDouble())
    }
    define("floor") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Number(floor(args[0].toNumber()))
    }
    define("ceiling") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Number(ceil(args[0].toNumber()))
    }
    define("sum") {
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
    define("max") {
      if (args.isEmpty()) return@define XPathValue.Number(Double.NaN)
      val nums = collectNumbers(args)
      if (nums.isEmpty()) XPathValue.Number(Double.NaN)
      else if (nums.any { it.isNaN() }) XPathValue.Number(Double.NaN)
      else XPathValue.Number(nums.maxOrNull() ?: Double.NaN)
    }
    define("min") {
      if (args.isEmpty()) return@define XPathValue.Number(Double.NaN)
      val nums = collectNumbers(args)
      if (nums.isEmpty()) XPathValue.Number(Double.NaN)
      else if (nums.any { it.isNaN() }) XPathValue.Number(Double.NaN)
      else XPathValue.Number(nums.minOrNull() ?: Double.NaN)
    }
    define("round") {
      checkEagerArgCount(name, args, 1..2)
      val num = args[0].toNumber()
      val places = args.getOrNull(1)?.toNumber()?.toInt() ?: 0
      if (num.isNaN() || num.isInfinite()) {
        XPathValue.Number(num)
      } else {
        val factor = 10.0.pow(places)
        val scaled = num * factor
        // XPath 1.0 section 4.4: ties round to the value closest to POSITIVE infinity, so
        // round(-1.5) is -1. That is floor(x + 0.5) for negative and positive values alike --
        // ceil(x - 0.5) would incorrectly round -1.5 away from zero to -2.
        val rounded = floor(scaled + 0.5)
        XPathValue.Number(rounded / factor)
      }
    }
    define("pow") {
      checkEagerArgCount(name, args, 2..2)
      XPathValue.Number(args[0].toNumber().pow(args[1].toNumber()))
    }
    define("log") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Number(ln(args[0].toNumber()))
    }
    define("log10") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Number(log10(args[0].toNumber()))
    }
    define("abs") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Number(abs(args[0].toNumber()))
    }
    define("sin") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Number(sin(args[0].toNumber()))
    }
    define("cos") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Number(cos(args[0].toNumber()))
    }
    define("tan") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Number(tan(args[0].toNumber()))
    }
    define("asin") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Number(asin(args[0].toNumber()))
    }
    define("acos") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Number(acos(args[0].toNumber()))
    }
    define("atan") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Number(atan(args[0].toNumber()))
    }
    define("atan2") {
      checkEagerArgCount(name, args, 2..2)
      XPathValue.Number(atan2(args[0].toNumber(), args[1].toNumber()))
    }
    define("sqrt") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Number(sqrt(args[0].toNumber()))
    }
    define("exp") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Number(exp(args[0].toNumber()))
    }
    define("exp10") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Number(10.0.pow(args[0].toNumber()))
    }
    define("pi") {
      checkEagerArgCount(name, args, 0..0)
      XPathValue.Number(PI)
    }
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
}
