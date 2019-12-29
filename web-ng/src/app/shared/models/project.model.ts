/**
 * Copyright 2019 Google LLC
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

import { StringMap } from "./string-map.model";

export class Project {
  constructor(
    readonly title: StringMap,
    readonly description: StringMap
  ) {}

  /**
   * Converts the raw object representation deserialized from JSON into an
   * immutable Project instance.
   *
   * @param d the source data in a dictionary keyed by string.
   */
  static fromJson(d: { [key: string]: any } ): Project {
    return new Project(StringMap(d.title), StringMap(d.description));
  }
}
