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

import {GeoPoint} from 'firebase/firestore';
import {List, Map} from 'immutable';

import {Geometry, GeometryType} from './geometry/geometry';

export abstract class LocationOfInterest {
  abstract readonly id: string;
  abstract readonly jobId: string;
  abstract readonly name?: string;
  // TODO(#1444): Make non-null once other subtypes are removed.
  abstract readonly geometry?: Geometry;
  // TODO(#1444): Make non-null, init to empty by default.
  abstract readonly properties?: Map<string, string | number>;

  static getSmallestByArea(lois: List<LocationOfInterest>): LocationOfInterest {
    return lois
      .sort((a, b) =>
        (a.geometry?.getArea() || 0) < (b.geometry?.getArea() || 0) ? -1 : 1
      )
      .first();
  }
}

// TODO(#1444): Delete me in favor of single LOI type.
export class PointOfInterest implements LocationOfInterest {
  constructor(
    readonly id: string,
    readonly jobId: string,
    // TODO(#1444): User custom type instead of exposing types from data job.
    readonly location: GeoPoint,
    readonly properties?: Map<string, string | number>
  ) {}
}

// TODO(#1444): Delete me in favor of single LOI type.
export class GeoJsonLocationOfInterest implements LocationOfInterest {
  constructor(
    readonly id: string,
    readonly jobId: string,
    readonly geoJson: object,
    readonly properties?: Map<string, string | number>
  ) {}
}

// TODO(#1444): Delete me in favor of single LOI type.
export class AreaOfInterest implements LocationOfInterest {
  constructor(
    readonly id: string,
    readonly jobId: string,
    readonly polygonVertices: GeoPoint[],
    readonly properties?: Map<string, string | number>
  ) {}
}

// TODO(#1444): Merge into LocationOfInterest and make concrete.
export class GenericLocationOfInterest implements LocationOfInterest {
  constructor(
    readonly id: string,
    readonly jobId: string,
    readonly geometry: Geometry,
    readonly properties: Map<string, string | number>
  ) {}
}
