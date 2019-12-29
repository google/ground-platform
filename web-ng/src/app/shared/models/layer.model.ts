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

import { StringMap } from './string-map.model';

type Data = { [key: string]: any };

export class Layer {
  constructor(readonly id: string, readonly name: StringMap) {}

  /**
   * Converts the raw object representation deserialized from JSON into an
   * immutable Layer instance.
   *
   * @param data the source data in a dictionary keyed by string.
   */
  static fromJson(id: string, data: Data): Layer {
    return new Layer(id, StringMap(data.name));
  }

  /**
   * Converts a map of id to raw objectfrom JSON into an array of immutable
   * Layer instances.
   *
   * @param layers a map of raw layer objects keyed by id.
   */
  static fromJsonMap(layers: Data) {
    return Object.keys(layers).map((id: string) =>
      Layer.fromJson(id, layers[id])
    );
  }
}
