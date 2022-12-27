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

import {Map} from 'immutable';
import {GeoPoint} from 'firebase/firestore';
import {Geometry} from 'app/models/geometry/geometry';

export interface LocationOfInterest {
  readonly id: string;
  readonly jobId: string;
  // TODO: Make non-null once other subtypes are removed.
  readonly geometry?: Geometry;
  // TODO: Make non-null, init to empty by default.
  readonly properties?: Map<string, string | number>;
}

// TODO: Delete me in favor of single LOI type.
export class PointOfInterest implements LocationOfInterest {
  constructor(
    readonly id: string,
    readonly jobId: string,
    // TODO: User custom type instead of exposing types from data job.
    readonly location: GeoPoint,
    readonly properties?: Map<string, string | number>
  ) {}
}

// TODO: Delete me in favor of single LOI type.
export class GeoJsonLocationOfInterest implements LocationOfInterest {
  constructor(
    readonly id: string,
    readonly jobId: string,
    readonly geoJson: object,
    readonly properties?: Map<string, string | number>
  ) {}
}

// TODO: Delete me in favor of single LOI type.
export class AreaOfInterest implements LocationOfInterest {
  constructor(
    readonly id: string,
    readonly jobId: string,
    readonly polygonVertices: GeoPoint[],
    readonly properties?: Map<string, string | number>
  ) {}
}

// TODO: Merge into LocationOfInterest and make concrete.
export class GenericLocationOfInterest implements LocationOfInterest {
  constructor(
    readonly id: string,
    readonly jobId: string,
    readonly geometry: Geometry,
    readonly properties: Map<string, string | number>
  ) {}
}
