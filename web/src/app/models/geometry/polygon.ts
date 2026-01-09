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
import { LinearRing } from './linear-ring';

/**
 * Represents a polygon with linear edges, which may include holes. The outer
 * boundary (shell) and inner boundaries (holes) of the polygon are represented
 * by LinearRings. The boundary rings of the polygon may have any orientation.
 * Polygons are closed, simple geometries by definition.
 *
 * The polygon model is assumed to conform to the assertions specified in the
 * OpenGIS Simple Features Specification for SQL.
 *
 * Based on https://locationtech.github.io/jts/javadoc/org/locationtech/jts/geom/Polygon.html
 */
export class Polygon implements Geometry {
  geometryType = GeometryType.POLYGON;

  constructor(
    readonly shell: LinearRing,
    readonly holes: List<LinearRing>
  ) {}

  equals(other: Polygon): boolean {
    return is(this.shell, other.shell) && is(this.holes, other.holes);
  }

  hashCode(): number {
    return hash(this);
  }

  extendBounds(bounds: google.maps.LatLngBounds) {
    this.shell.extendBounds(bounds);
    return bounds;
  }

  getArea(): number {
    return google.maps.geometry.spherical.computeArea(
      this.shell.points
        .map(({ x, y }) => new google.maps.LatLng(y, x))
        .toArray()
    );
  }
}
