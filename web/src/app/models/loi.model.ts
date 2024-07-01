/**
 * Copyright 2020 The Ground Authors.
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

import {List, Map} from 'immutable';

import {Geometry} from './geometry/geometry';

export class LocationOfInterest {
  constructor(
    readonly id: string,
    readonly jobId: string,
    readonly geometry: Geometry,
    readonly properties: Map<string, string | number>,
    readonly customId: string = '',
    readonly predefined: boolean = true
  ) {}

  static getSmallestByArea(lois: List<LocationOfInterest>): LocationOfInterest {
    return lois
      .sort((a, b) =>
        (a.geometry?.getArea() || 0) < (b.geometry?.getArea() || 0) ? -1 : 1
      )
      .first();
  }
}
