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

/**
 * [text] in Unicode canonical decomposition (NFD), so an accented letter such as `É` becomes the
 * base letter `E` followed by its combining accent (`java.text.Normalizer` on the JVM,
 * `String.prototype.normalize` in the browser).
 */
internal expect fun canonicalDecomposition(text: String): String

/** The combining diacritical marks (U+0300 to U+036F) that Latin-script accents decompose into. */
private val COMBINING_DIACRITICS = '\u0300'..'\u036F'

/**
 * [text] folded for search: lower-cased, with the accents of Latin-script letters removed, so
 * `égypte` and `Egypte` both fold to `egypte`, and Vietnamese `Đức` folds to `duc`.
 *
 * Only Latin diacritics are removed; marks that are part of other scripts (the vowel and tone signs
 * of Thai, Lao, or Khmer) are kept, since removing them would change the word.
 */
internal fun foldedForSearch(text: String): String =
  canonicalDecomposition(text)
    .lowercase()
    .filterNot { it in COMBINING_DIACRITICS }
    // Đ/đ (Vietnamese, among others) is a letter of its own, not D with a combining mark.
    .replace('đ', 'd')
