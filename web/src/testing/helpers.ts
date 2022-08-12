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

import { Collection } from 'immutable';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function immutableCollectionTester(a: any, b: any): boolean | undefined {
  if (a instanceof Collection && b instanceof Collection) {
    return (a as Collection<never, never>).equals(
      b as Collection<never, never>
    );
  }
  return;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function formatImmutableCollection(val: any): string | undefined {
  if (val instanceof Collection) {
    return JSON.stringify((val as Collection<never, never>).toJS());
  }
  return;
}

beforeEach(() => {
  jasmine.addCustomEqualityTester(immutableCollectionTester);
  jasmine.addCustomObjectFormatter(formatImmutableCollection);
});
