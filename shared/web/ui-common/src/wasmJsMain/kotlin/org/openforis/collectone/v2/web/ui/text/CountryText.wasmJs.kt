/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openforis.collectone.v2.web.ui.text

internal actual fun platformCountryName(code: String, languageTag: String): String? =
  intlRegionName(code, languageTag)

/** The browser's `Intl.DisplayNames` region name, or `null` when it has none. */
@Suppress("UnusedParameter") // Both are read by the JavaScript body.
private fun intlRegionName(code: String, languageTag: String): String? =
  js(
    """{
    try {
      return new Intl.DisplayNames([languageTag], { type: 'region' }).of(code) || null;
    } catch (e) {
      return null;
    }
  }"""
  )

internal actual fun platformCollator(languageTag: String): Comparator<String> {
  val collator = intlCollator(languageTag)
  return Comparator { a, b -> intlCompare(collator, a, b) }
}

/** The browser's `Intl.Collator` for [languageTag], or its default one for an unknown tag. */
@Suppress("UnusedParameter") // Read by the JavaScript body.
private fun intlCollator(languageTag: String): JsAny =
  js(
    """{
    try {
      return new Intl.Collator([languageTag]);
    } catch (e) {
      return new Intl.Collator();
    }
  }"""
  )

@Suppress("UnusedParameter") // All three are read by the JavaScript body.
private fun intlCompare(collator: JsAny, a: String, b: String): Int = js("collator.compare(a, b)")
