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

/**
 * Minimal representation of a planar, linear vector geometry.
 *
 * Based on https://locationtech.github.io/jts/javadoc/org/locationtech/jts/geom/Geometry.html
 */
export interface Geometry {
  geometryType: GeometryType;

  extendBounds: (bounds: google.maps.LatLngBounds) => google.maps.LatLngBounds;
}

/**
 * An enum representing the type of the geometry instance.
 */
export enum GeometryType {
  POINT = 'POINT',
  LINE_STRING = 'LINE_STRING',
  LINEAR_RING = 'LINEAR_RING',
  POLYGON = 'POLYGON',
  MULTI_POLYGON = 'MULTI_POLYGON',
}

/** A label for a given geometry type. Defaults to 'Polygon'. */
export function geometryTypeLabel(geometryType?: GeometryType): string {
  if (geometryType === GeometryType.POINT) {
    return 'Point';
  }
  if (geometryType === GeometryType.MULTI_POLYGON) {
    return 'Multipolygon';
  }
  return 'Polygon';
}
