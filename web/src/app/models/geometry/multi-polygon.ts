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

import { Geometry, GeometryType } from './geometry';
import { Polygon } from './polygon';

/**
 * Models a collection of Polygons.
 *
 * As per the OGC SFS specification, the Polygons in a MultiPolygon may not
 * overlap, and may only touch at single points. This allows the topological
 * point-set semantics to be well-defined.
 *
 * Based on https://locationtech.github.io/jts/javadoc/org/locationtech/jts/geom/MultiPolygon.html
 */
export class MultiPolygon implements Geometry {
  geometryType = GeometryType.MULTI_POLYGON;

  constructor(readonly polygons: List<Polygon>) {}

  equals(other: MultiPolygon): boolean {
    return is(this.polygons, other.polygons);
  }

  hashCode(): number {
    return hash(this);
  }

  extendBounds(bounds: google.maps.LatLngBounds) {
    for (const polygon of this.polygons) {
      polygon.extendBounds(bounds);
    }
    return bounds;
  }

  getArea(): number {
    return this.polygons
      .map(polygon => polygon.getArea())
      .reduce((accumulator, currentValue) => {
        return accumulator + currentValue;
      }, 0);
  }
}
