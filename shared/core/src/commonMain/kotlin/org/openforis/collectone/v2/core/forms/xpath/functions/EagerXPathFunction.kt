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

import org.openforis.collectone.v2.core.forms.xpath.EvaluationContext
import org.openforis.collectone.v2.core.forms.xpath.XPathEvaluationException
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

/** A call to an eagerly evaluated XPath function: its [name], evaluated [args] and [context]. */
internal class EagerCall(
  val name: String,
  val args: List<XPathValue>,
  val context: EvaluationContext,
)

/** Implementation of an XPath function whose arguments are all evaluated before the call. */
internal typealias EagerXPathFunction = EagerCall.() -> XPathValue

/** Collects the functions of one family, rejecting names defined twice. */
internal class XPathFunctionTableBuilder {
  private val functions = LinkedHashMap<String, EagerXPathFunction>()

  /** Registers [body] under every name in [names]. */
  fun define(vararg names: String, body: EagerXPathFunction) {
    for (name in names) {
      require(name !in functions) { "XPath function '$name()' is defined twice" }
      functions[name] = body
    }
  }

  fun build(): Map<String, EagerXPathFunction> = functions.toMap()
}

/** Builds a name-to-implementation table for one family of XPath functions. */
internal fun xpathFunctionTable(
  build: XPathFunctionTableBuilder.() -> Unit
): Map<String, EagerXPathFunction> = XPathFunctionTableBuilder().apply(build).build()

/** Merges per-family tables, rejecting a name that two families both define. */
internal fun mergeFunctionTables(
  vararg tables: Map<String, EagerXPathFunction>
): Map<String, EagerXPathFunction> {
  val merged = LinkedHashMap<String, EagerXPathFunction>()
  for (table in tables) {
    for ((name, function) in table) {
      require(name !in merged) { "XPath function '$name()' is defined by two families" }
      merged[name] = function
    }
  }
  return merged
}

/**
 * Throws [XPathEvaluationException] unless [name] was called with a number of [args] in [range].
 */
internal fun checkEagerArgCount(name: String, args: List<XPathValue>, range: IntRange) {
  if (args.size !in range) {
    throw XPathEvaluationException("Function '$name' expects $range arguments, got ${args.size}")
  }
}
