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

import { Layer } from './layer.model';
import { List } from 'immutable';
import { StringMap } from './string-map.model';

export class Project {
  constructor(
    readonly id: string,
    readonly title: StringMap,
    readonly description: StringMap,
    readonly layers: List<Layer>
  ) { }

  /**
   * Converts the raw object representation deserialized from JSON into an
   * immutable Project instance.
   *
   * @param id the uuid of the project instance.
   * @param data the source data in a dictionary keyed by string.
   */

  static fromJson(id: string, data: { [key: string]: any }): Project {
    return new Project(
      id,
      StringMap(data.title),
      StringMap(data.description),
      List(Layer.fromJsonMap(data.layers || {}))
    );
  }
}
