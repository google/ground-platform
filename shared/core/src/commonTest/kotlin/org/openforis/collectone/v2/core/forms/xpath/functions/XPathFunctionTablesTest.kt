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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

class XPathFunctionTablesTest {

  @Test
  fun familyTables_defineEveryEagerFunctionExactlyOnce() {
    val merged =
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

    assertEquals(EAGER_FUNCTION_NAMES, merged.keys)
  }

  @Test
  fun define_sameNameTwiceInOneFamily_throws() {
    assertFailsWith<IllegalArgumentException> {
      xpathFunctionTable {
        define("twice") { XPathValue.Bool(true) }
        define("twice") { XPathValue.Bool(false) }
      }
    }
  }

  @Test
  fun mergeFunctionTables_sameNameInTwoFamilies_throws() {
    val first = xpathFunctionTable { define("shared") { XPathValue.Bool(true) } }
    val second = xpathFunctionTable { define("shared") { XPathValue.Bool(false) } }

    assertFailsWith<IllegalArgumentException> { mergeFunctionTables(first, second) }
  }

  private companion object {
    val EAGER_FUNCTION_NAMES =
      setOf(
        "string",
        "concat",
        "join",
        "substr",
        "substring",
        "substring-before",
        "substring-after",
        "translate",
        "string-length",
        "normalize-space",
        "contains",
        "starts-with",
        "ends-with",
        "uuid",
        "digest",
        "base64-decode",
        "pulldata",
        "true",
        "false",
        "boolean",
        "boolean-from-string",
        "not",
        "regex",
        "checklist",
        "weighted-checklist",
        "number",
        "random",
        "int",
        "floor",
        "ceiling",
        "sum",
        "max",
        "min",
        "round",
        "pow",
        "log",
        "log10",
        "abs",
        "sin",
        "cos",
        "tan",
        "asin",
        "acos",
        "atan",
        "atan2",
        "sqrt",
        "exp",
        "exp10",
        "pi",
        "count",
        "count-non-empty",
        "position",
        "last",
        "instance",
        "current",
        "randomize",
        "today",
        "now",
        "date",
        "date-time",
        "decimal-date-time",
        "decimal-time",
        "format-date",
        "format-date-time",
        "selected",
        "selected-at",
        "count-selected",
        "jr:itext",
        "jr:choice-name",
        "area",
        "distance",
        "geofence",
        "intersects",
      )
  }
}
