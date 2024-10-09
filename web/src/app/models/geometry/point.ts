/**
 * Copyright 2022 The Ground Authors.
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

import {hash, is} from 'immutable';

import {Coordinate} from './coordinate';
import {Geometry, GeometryType} from './geometry';

/**
 * Represents a single point.
 *
 * Based on https://locationtech.github.io/jts/javadoc/org/locationtech/jts/geom/Point.html
 */
export class Point implements Geometry {
  geometryType = GeometryType.POINT;

  constructor(readonly coord: Coordinate, readonly accuracy?: number, readonly altitude?: number) {}

  equals(other: Point): boolean {
    return is(this.coord, other.coord);
  }

  hashCode(): number {
    return hash(this);
  }

  extendBounds(bounds: google.maps.LatLngBounds) {
    const {y, x} = this.coord;
    bounds.extend(new google.maps.LatLng(y, x));
    return bounds;
  }

  getArea(): number {
    return 0;
  }
}
