/**
 * Copyright 2024 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import colors from 'picocolors';
import { diffLines } from 'diff';

/**
 * Decorates a Jasmine matcher with ability to pretty-print differences in
 * objects.
 */
function decorateMatcher(jasmineMatcher) {
  return (util, customEqualityTesters) => {
    const matcher = jasmineMatcher(util, customEqualityTesters);
    return {
      compare: (actual, expected) => {
        const result = matcher.compare(actual, expected);
        if (!result.pass) {
          const formattedDiffs = formatDiffs(actual, expected);
          result.message = `${result.message || ''}\n${formattedDiffs}`;
        }
        return result;
      },
    };
  };
}

/**
 * Formats a diff for display.
 */
function formatDiffs(actual, expected) {
  const diff = diffLines(format(actual), format(expected));
  return diff.map(formatDiff).join('');
}

/**
 * Formats an individual diff for display.
 *
 * @param {*} diff The deltas returned by the diff library.
 * @return {string} Diff line with markers and color codes.
 */
function formatDiff(diff) {
  const { green, red, reset } = colors;
  const { added, removed, value } = diff;
  if (added) {
    return green(`+${value}`);
  } else if (removed) {
    return red(`-${value}`);
  } else {
    return reset(` ${value}`);
  }
}

function format(str) {
  return JSON.stringify(str, null, 2);
}

// Apply decorators before running tests.
beforeAll(() => {
  jasmine.getEnv().addMatchers({
    toBe: decorateMatcher(jasmine.matchers.toBe, 'to be'),
    toEqual: decorateMatcher(jasmine.matchers.toEqual, 'to equal'),
  });
});
