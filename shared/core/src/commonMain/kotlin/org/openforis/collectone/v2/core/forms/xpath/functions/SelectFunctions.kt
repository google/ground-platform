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

/** ODK functions over space-separated select-multiple values. */
internal object SelectFunctions {

  val table: Map<String, EagerXPathFunction> = xpathFunctionTable {
    define("selected") {
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
    define("selected-at") {
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
    define("count-selected") {
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
  }
}
