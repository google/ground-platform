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

import firebase from 'firebase/app';
import { Map } from 'immutable';

export interface Feature {
  readonly id: string;
  readonly jobId: string;
  readonly properties?: Map<string, string | number>;
}

export class LocationFeature implements Feature {
  constructor(
    readonly id: string,
    readonly jobId: string,
    // TODO: User custom type instead of exposing types from data job.
    readonly location: firebase.firestore.GeoPoint,
    readonly properties?: Map<string, string | number>
  ) {}
}

export class GeoJsonFeature implements Feature {
  constructor(
    readonly id: string,
    readonly jobId: string,
    readonly geoJson: object,
    readonly properties?: Map<string, string | number>
  ) {}
}

export class PolygonFeature implements Feature {
  constructor(
    readonly id: string,
    readonly jobId: string,
    readonly polygonVertices: firebase.firestore.GeoPoint[],
    readonly properties?: Map<string, string | number>
  ) {}
}
