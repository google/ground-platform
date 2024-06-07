/**
 * Copyright 2024 The Ground Authors.
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

import {GeoPoint} from '@google-cloud/firestore';

function isObject(item: any) {
  return item && typeof item === 'object' && !Array.isArray(item);
}

/**
 * Merge two objects by key-value pairs, recursively.
 */
export function deepMerge(target: any, source: any): any {
  // TODO(#1758): Delete once this is no longer used.
  if (isObject(target) && isObject(source)) {
    for (const key in source) {
      if (isObject(source[key]) && !(source[key] instanceof GeoPoint)) {
        if (!target[key]) Object.assign(target, {[key]: {}});
        deepMerge(target[key], source[key]);
      } else {
        Object.assign(target, {[key]: source[key]});
      }
    }
  }
  return target;
}
