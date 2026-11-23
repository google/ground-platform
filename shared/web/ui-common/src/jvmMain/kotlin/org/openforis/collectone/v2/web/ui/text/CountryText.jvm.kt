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

import java.text.Collator
import java.util.Locale

internal actual fun platformCountryName(code: String, languageTag: String): String? = runCatching {
  Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.forLanguageTag(languageTag))
}
  .getOrNull()

internal actual fun platformCollator(languageTag: String): Comparator<String> {
  val collator = Collator.getInstance(Locale.forLanguageTag(languageTag))
  return Comparator { a, b -> collator.compare(a, b) }
}
