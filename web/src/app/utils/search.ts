/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/** The combining diacritical marks (U+0300 to U+036F) Latin accents decompose into. */
const COMBINING_DIACRITICS = /[̀-ͯ]/g;

/**
 * `text` folded for search: lower-cased, with the accents of Latin-script
 * letters removed, so `Égypte` and `egypte` both fold to `egypte`, and
 * Vietnamese `Đức` folds to `duc`.
 *
 * Only Latin diacritics are removed: the vowel and tone marks of scripts such
 * as Thai or Lao are kept, since removing them would change the word.
 */
export function foldForSearch(text: string): string {
  return (
    text
      .normalize('NFD')
      .toLowerCase()
      .replace(COMBINING_DIACRITICS, '')
      // Đ/đ is a letter of its own, not D with a combining mark.
      .replace(/đ/g, 'd')
  );
}

/**
 * Whether `query` matches any of `fields`: every word of the query has to
 * appear in at least one field, in any order, ignoring case and accents. A
 * blank query matches everything.
 */
export function matchesSearch(
  query: string,
  ...fields: (string | undefined)[]
): boolean {
  const words = foldForSearch(query).split(/\s+/).filter(Boolean);
  if (words.length === 0) return true;
  const haystack = fields.map(f => foldForSearch(f ?? ''));
  return words.every(word => haystack.some(field => field.includes(word)));
}
