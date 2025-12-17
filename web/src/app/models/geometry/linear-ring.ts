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

import { List } from 'immutable';

import { Coordinate } from './coordinate';
import { GeometryType } from './geometry';
import { LineString } from './line-string';

/**
 * Models an OGC SFS LinearRing. A LinearRing is a LineString which is both
 * closed and simple. In other words, the first and last coordinate in the ring
 * must be equal, and the ring must not self-intersect. Either orientation of
 * the ring is allowed.
 *
 * A ring is assumed to have either 0 or 3 or more points, and the first and
 * last points are assumed to be equal (in 2D).
 *
 * Based on https://locationtech.github.io/jts/javadoc/org/locationtech/jts/geom/LinearRing.html
 */
export class LinearRing extends LineString {
  geometryType = GeometryType.LINEAR_RING;

  constructor(points: List<Coordinate>) {
    super(points);
  }
}
