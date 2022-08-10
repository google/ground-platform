/**
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { List } from 'immutable';

/**
 * Utilities to convert between recursively nested dictionaries
 * with 0-based sequential integer keys, and their equivalent representation
 * as a nested list.
 *
 * For example, the following list:
 * [['alpha', 'beta'], ['gamma', 'delta']]
 *
 * Would have an equivalent dictionary representation:
 * {0: {0: 'alpha', 1: 'beta'}, 1: {0: 'gamma', 1: 'delta'}}
 */

/**
 * Recursively convert a nested indexed map to a nested array.
 *
 * Null values are not allowed.
 *
 * @param map
 * @returns
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function indexedMapToList(map?: any): List<any> {
  if (
    !map ||
    typeof map !== 'object' ||
    Object.values(map).some(v => v === null)
  ) {
    throw new Error(`Invalid indexed map ${map}`);
  }

  // Convert keys to numbers and sort by index.
  const entries = Object.entries(map)
    .map(([key, val]) => [Number(key), val] as [number, unknown])
    .sort((a, b) => a[0] - b[0]);

  // Verify keys correspond 1:1 to array indices (i.e., 0 to array.length-1).
  // Non-numeric indices will appear as NaN, which will cause this check to
  // fail.
  if (!entries.map(e => e[0]).every((key, idx) => key === idx)) {
    throw new Error(`Non-sequential indices in ${map}`);
  }

  // Entries are already sorted by sequential indices, so we can now extract
  // values as an array, recursively unpacking nested maps to arrays.
  return List(
    entries
      .map(e => e[1])
      .map(v => (typeof v === 'object' ? indexedMapToList(v) : v))
  );
}
