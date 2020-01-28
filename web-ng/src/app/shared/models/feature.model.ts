/**
 * Copyright 2020 Google LLC
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

import { firestore } from 'firebase/app';

export class Feature {
  constructor(
    readonly id: string,
    readonly layerId: string,
    readonly location: firestore.GeoPoint
  ) {}

  /**
   * Converts the raw object representation deserialized from JSON into an
   * immutable Feature instance.
   *
   * @param id the uuid of the project instance.
   * @param data the source data in a dictionary keyed by string.
   */

  static fromJson(id: string, data: { [key: string]: any }): Feature {
    return new Feature(id, data.layerId, data.location);
  }
}
