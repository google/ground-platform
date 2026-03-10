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

import { List, hash, is } from 'immutable';

import { Coordinate } from './coordinate';
import { Geometry, GeometryType } from './geometry';

/**
 * Models an OGC-style LineString. A LineString consists of a sequence of two
 * or more vertices, along with all points along the linearly-interpolated
 * curves (line segments) between each pair of consecutive vertices. Consecutive
 * vertices may be equal. The line segments in the line may intersect each other
 * (in other words, the linestring may "curl back" in itself and self-intersect.
 * Linestrings with exactly two identical points are invalid.
 *
 * A linestring is assumed to have either 0 or 2 or more points.
 *
 * Based on https://locationtech.github.io/jts/javadoc/org/locationtech/jts/geom/LineString.html
 */
export class LineString implements Geometry {
  geometryType = GeometryType.LINE_STRING;

  constructor(readonly points: List<Coordinate>) {}

  equals(other: LineString): boolean {
    return is(this.points, other.points);
  }

  hashCode(): number {
    return hash(this);
  }

  extendBounds(bounds: google.maps.LatLngBounds) {
    for (const { y, x } of this.points) {
      bounds.extend(new google.maps.LatLng(y, x));
    }
    return bounds;
  }

  getArea(): number {
    return 0;
  }
}
