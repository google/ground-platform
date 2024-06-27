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

export function deleteEmpty(
  dict: Record<any, any | undefined | null>
): Record<any, any> {
  return Object.keys(dict).reduce((acc, key) => {
    if (!isEmpty(dict[key])) {
      acc[key] = dict[key];
    }
    return acc;
  }, {} as Record<string, any>);
}

export function isEmpty(obj: any | null | undefined): boolean {
  if (obj === null || obj === undefined) return true;
  switch (typeof obj) {
    case 'string':
      return obj === '';
    case 'object':
      return Object.keys(obj).length === 0;
    default:
      return false;
  }
}
