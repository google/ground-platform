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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.Countries
import org.openforis.collectone.v2.app.domain.model.Country
import org.openforis.collectone.v2.app.ui.common.LocalAppLocale
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_country_with_code

/**
 * The platform's name for the country or territory [code] in the language [languageTag] (the
 * Unicode CLDR names of the JVM or the browser), or `null` when the platform doesn't know it.
 */
internal expect fun platformCountryName(code: String, languageTag: String): String?

/**
 * The platform's collation of text in the language [languageTag] (the Unicode CLDR rules of the
 * JVM's `Collator` or the browser's `Intl.Collator`), so names sort as a reader of that language
 * expects: accents and case don't override the alphabet, and letters such as `Å` or `ñ` take their
 * language's place.
 */
internal expect fun platformCollator(languageTag: String): Comparator<String>

private fun isEnglish(languageTag: String): Boolean =
  languageTag.substringBefore('-').equals("en", ignoreCase = true)

/**
 * This country's name in the language [languageTag]: the English [Country.name] for English, so
 * English text stays as listed in [Countries], otherwise the platform's name, falling back to
 * English when the platform has none.
 */
fun Country.localizedName(languageTag: String): String =
  if (isEnglish(languageTag)) {
    name
  } else {
    platformCountryName(code, languageTag)?.takeIf { it.isNotBlank() && it != code } ?: name
  }

/**
 * Every country in the order pickers list them in the language [languageTag]: by the name they show
 * ([localizedName]), compared with [platformCollator] for that language, the code breaking ties.
 * English keeps the order of [Countries.all].
 */
fun countriesInDisplayOrder(languageTag: String): List<Country> {
  if (isEnglish(languageTag)) return Countries.all
  return Countries.all
    .map { country -> country to country.localizedName(languageTag) }
    .sortedWith(
      compareBy<Pair<Country, String>, String>(platformCollator(languageTag)) { it.second }
        .thenBy { it.first.code }
    )
    .map { it.first }
}

/** This country as shown in pickers and on the organization page, e.g. `Kenya (KE)`. */
@Composable
fun Country.labelText(): String {
  val languageTag = LocalAppLocale.current
  val localizedName = remember(code, languageTag) { localizedName(languageTag) }
  return stringResource(Res.string.org_country_with_code, localizedName, code)
}

/**
 * Countries matching [query], for the searchable country picker in the language [languageTag].
 *
 * English matches and ranks as [Countries.search] does. Other languages rank the exact code first,
 * then names (as shown, [localizedName]) starting with [query], then names with a word starting
 * with it, then names containing it, then countries only their English name or code matches; each
 * rank keeps the order of [countriesInDisplayOrder]. Names match ignoring case and accents
 * ([foldedForSearch]), so `egypte` finds Égypte. A blank [query] returns that whole list.
 */
fun searchCountries(query: String, languageTag: String): List<Country> {
  if (isEnglish(languageTag)) return Countries.search(query)
  val trimmed = query.trim()
  val ordered = countriesInDisplayOrder(languageTag)
  if (trimmed.isEmpty()) return ordered
  val folded = foldedForSearch(trimmed)
  val englishMatches = Countries.search(trimmed).toSet()
  return ordered
    .mapNotNull { country ->
      val name = foldedForSearch(country.localizedName(languageTag))
      val rank =
        when {
          country.code.equals(trimmed, ignoreCase = true) -> 0
          name.startsWith(folded) -> 1
          name.split(' ', '-').any { it.startsWith(folded) } -> 2
          name.contains(folded) -> 3
          country in englishMatches -> 4
          else -> null
        }
      rank?.let { it to country }
    }
    .sortedBy { it.first }
    .map { it.second }
}
