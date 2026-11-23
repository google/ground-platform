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

import kotlin.math.round
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

/** XPath 1.0 string functions (section 4.2) and the ODK string extensions. */
internal object StringFunctions {

  val table: Map<String, EagerXPathFunction> = xpathFunctionTable {
    define("string") {
      checkEagerArgCount(name, args, 0..1)
      val target = args.firstOrNull() ?: XPathValue.NodeSet(listOf(context.contextNode))
      XPathValue.Str(target.toXPathString())
    }
    define("concat") {
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
    define("join") {
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
    define("substr") {
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
    define("substring") {
      // Standard XPath 1.0 1-based substring(str, start, length?)
      checkEagerArgCount(name, args, 2..3)
      val str = args[0].toXPathString()
      val startNum = round(args[1].toNumber())
      val lenNum = if (args.size >= 3) round(args[2].toNumber()) else Double.POSITIVE_INFINITY
      if (startNum.isNaN() || lenNum.isNaN() || lenNum <= 0.0) {
        XPathValue.Str("")
      } else {
        // Clamp in Double/Long space before narrowing. `(-Infinity).toInt()` is Int.MIN_VALUE and
        // subtracting 1 from that wraps around to Int.MAX_VALUE, which would make an unbounded
        // start behave like a start past the end of the string and yield "".
        val startIdxLong = clampToLong(startNum) - 1L
        val startIdx = startIdxLong.coerceIn(0L, str.length.toLong()).toInt()
        val endIdx =
          if (lenNum.isInfinite()) {
            str.length
          } else {
            (startIdxLong + clampToLong(lenNum)).coerceIn(0L, str.length.toLong()).toInt()
          }
        if (startIdx >= str.length || startIdx >= endIdx) XPathValue.Str("")
        else XPathValue.Str(str.substring(startIdx, endIdx))
      }
    }
    define("substring-before") {
      checkEagerArgCount(name, args, 2..2)
      val target = args[0].toXPathString()
      val prefix = args[1].toXPathString()
      if (prefix.isEmpty() || !target.contains(prefix)) XPathValue.Str("")
      else XPathValue.Str(target.substringBefore(prefix))
    }
    define("substring-after") {
      checkEagerArgCount(name, args, 2..2)
      val target = args[0].toXPathString()
      val suffix = args[1].toXPathString()
      if (suffix.isEmpty()) XPathValue.Str(target)
      else if (!target.contains(suffix)) XPathValue.Str("")
      else XPathValue.Str(target.substringAfter(suffix))
    }
    define("translate") {
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
    define("string-length") {
      checkEagerArgCount(name, args, 0..1)
      val target = args.firstOrNull() ?: XPathValue.NodeSet(listOf(context.contextNode))
      XPathValue.Number(target.toXPathString().length.toDouble())
    }
    define("normalize-space") {
      checkEagerArgCount(name, args, 0..1)
      val target = args.firstOrNull() ?: XPathValue.NodeSet(listOf(context.contextNode))
      val normalized = target.toXPathString().trim().replace(Regex("\\s+"), " ")
      XPathValue.Str(normalized)
    }
    define("contains") {
      checkEagerArgCount(name, args, 2..2)
      XPathValue.Bool(args[0].toXPathString().contains(args[1].toXPathString()))
    }
    define("starts-with") {
      checkEagerArgCount(name, args, 2..2)
      XPathValue.Bool(args[0].toXPathString().startsWith(args[1].toXPathString()))
    }
    define("ends-with") {
      checkEagerArgCount(name, args, 2..2)
      XPathValue.Bool(args[0].toXPathString().endsWith(args[1].toXPathString()))
    }
  }

  /**
   * Narrows an XPath number to a [Long] for string-index arithmetic, saturating rather than
   * wrapping. `Double.toInt()`/`toLong()` map infinities and out-of-range values onto the numeric
   * extremes, where a subsequent `- 1` or `+ len` silently overflows. Bounds are kept within [Int]
   * range so two clamped values can be added without overflowing a [Long].
   */
  private fun clampToLong(value: Double): Long =
    when {
      value.isNaN() -> 0L
      value >= Int.MAX_VALUE.toDouble() -> Int.MAX_VALUE.toLong()
      value <= Int.MIN_VALUE.toDouble() -> Int.MIN_VALUE.toLong()
      else -> value.toLong()
    }
}
