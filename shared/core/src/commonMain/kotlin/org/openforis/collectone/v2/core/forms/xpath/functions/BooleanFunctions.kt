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

import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

/** XPath 1.0 boolean functions (section 4.3) and the ODK boolean extensions. */
internal object BooleanFunctions {

  val table: Map<String, EagerXPathFunction> = xpathFunctionTable {
    define("true") {
      checkEagerArgCount(name, args, 0..0)
      XPathValue.Bool(true)
    }
    define("false") {
      checkEagerArgCount(name, args, 0..0)
      XPathValue.Bool(false)
    }
    define("boolean") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Bool(args[0].toBoolean())
    }
    define("boolean-from-string") {
      checkEagerArgCount(name, args, 1..1)
      val str = args[0].toXPathString().trim()
      XPathValue.Bool(str == "true" || str == "1")
    }
    define("not") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Bool(!args[0].toBoolean())
    }
    define("regex") {
      checkEagerArgCount(name, args, 2..2)
      val target = args[0].toXPathString()
      val pattern = args[1].toXPathString()
      // ODK XForms spec, regex(): the expression string becomes a regular expression that is
      // tested against the value. JavaRosa uses Matcher.find() and Enketo uses RegExp.test(),
      // both substring (unanchored) matches, so use containsMatchIn rather than a full-string
      // match. Authors anchor with ^...$ when they need the whole value to match.
      XPathValue.Bool(compiledRegex(pattern)?.containsMatchIn(target) ?: false)
    }
    define("checklist") {
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
    define("weighted-checklist") {
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
  }

  /** Upper bound on [regexCache]; a form uses a handful of distinct patterns. */
  private const val MAX_CACHED_REGEX_PATTERNS = 64

  /**
   * Compiled `regex()` patterns, with `null` for a pattern that does not compile. The map is
   * replaced rather than mutated, so concurrent evaluations can at worst compile a pattern twice,
   * and it starts over once it holds [MAX_CACHED_REGEX_PATTERNS] entries.
   */
  private var regexCache: Map<String, Regex?> = emptyMap()

  /**
   * Returns [pattern] compiled, or `null` if it is not a valid regular expression.
   *
   * An invalid pattern makes `regex()` return false instead of failing the evaluation. This keeps
   * the long-standing behavior, but it also hides the authoring mistake from the form designer.
   */
  private fun compiledRegex(pattern: String): Regex? {
    val cache = regexCache
    if (pattern in cache) return cache[pattern]
    val compiled =
      try {
        Regex(pattern)
      } catch (e: Exception) {
        null
      }
    val base = if (cache.size >= MAX_CACHED_REGEX_PATTERNS) emptyMap() else cache
    regexCache = base + (pattern to compiled)
    return compiled
  }
}
