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

import { hash } from 'immutable';

/**
 * A lightweight class used to store coordinates on the 2-dimensional Cartesian
 * plane.
 *
 * It is distinct from Point, which is a subclass of Geometry.
 *
 * Based on https://locationtech.github.io/jts/javadoc/org/locationtech/jts/geom/Coordinate.html
 */
export class Coordinate {
  constructor(
    readonly x: number,
    readonly y: number
  ) {}

  equals(other: Coordinate): boolean {
    return this.x === other.x && this.y === other.y;
  }

  hashCode(): number {
    return hash(this);
  }
}
