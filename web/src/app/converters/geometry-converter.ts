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

/* eslint-disable @typescript-eslint/no-explicit-any */

import { List, Map } from 'immutable';

import { Coordinate } from 'app/models/geometry/coordinate';
import { Geometry, GeometryType } from 'app/models/geometry/geometry';
import { LinearRing } from 'app/models/geometry/linear-ring';
import { MultiPolygon } from 'app/models/geometry/multi-polygon';
import { Point } from 'app/models/geometry/point';
import { Polygon } from 'app/models/geometry/polygon';

/** Pretty-print objects. */
const stringify = (o: Object) => JSON.stringify(o);

/**
 * Maps geometry type enum values to their GeoJson equivalent string
 * representations used in the remote db.
 */
export const GEOMETRY_TYPES = Map([
  [GeometryType.POINT, 'Point'],
  [GeometryType.POLYGON, 'Polygon'],
  [GeometryType.MULTI_POLYGON, 'MultiPolygon'],
  [GeometryType.LINE_STRING, 'LineString'],
  [GeometryType.LINEAR_RING, 'LinearRing'],
]);

/**
 * Converts remote db representation of a geometry to a geometry object using a
 * modified GeoJSON representation:
 *
 * * The GeoJSON map hierarchy is converted to a Firestore nested map.
 * * Since Firestore does not allow nested arrays, arrays are replaced with
 *   nested maps, keyed by integer array index.
 * * Coordinates (two-element double arrays) are represented as a Firestore
 *   GeoPoint.
 *
 * Only `Point`, `Polygon`, and `MultiPolygon` are supported; behavior for other
 * geometry types is undefined.
 */

export function toGeometry(geometry?: any): Geometry | Error {
  if (!geometry) {
    return new Error('Error converting to Geometry: missing geometry');
  }
  try {
    switch (geometry.type) {
      case GEOMETRY_TYPES.get(GeometryType.POINT):
        return toPoint(geometry.coordinates);
      case GEOMETRY_TYPES.get(GeometryType.POLYGON):
        return toPolygon(geometry.coordinates);
      case GEOMETRY_TYPES.get(GeometryType.MULTI_POLYGON):
        return toMultiPolygon(geometry.coordinates);
      default:
        return Error(
          `Error converting to Geometry: unsupported geometry type ${geometry.type}`
        );
    }
  } catch (e) {
    return e instanceof Error
      ? e
      : new Error(`Error converting to Geometry: ${JSON.stringify(e)}`);
  }
}

function toPoint(coordinates?: any): Point {
  return new Point(toCoordinate(coordinates));
}

/**
 * Returns a polygon constructed from the db representation of polygon
 * coordinates. The provided map must be an indexed map of rings (one shell and
 * zero or more holes), each comprised of an indexed map of coordinates.
 */
function toPolygon(coordinatesMap?: any): Polygon {
  const rings: List<List<any>> =
    indexedMapToList(coordinatesMap).map(indexedMapToList);
  if (rings.isEmpty()) {
    throw new Error('Error converting to Geometry: empty coordinates map');
  }
  // First ring is the polygon's outer shell.
  const shell = toLinearRing(rings.first());
  // Any remaining rings represent inner rings / holes.
  const holes = rings.shift().map(h => toLinearRing(h));
  return new Polygon(shell, holes);
}

/**
 * Returns a polygon constructed from the db representation of multi-polygon
 * coordinates. The provided map must be an indexed map of polygons. See
 * `toPolygon` for polygon representation.
 */
function toMultiPolygon(coordinatesMap?: any): MultiPolygon {
  const polygons = indexedMapToList(coordinatesMap);
  if (polygons.isEmpty()) {
    throw new Error(
      `Error converting to Geometry: empty multi-polygon ${stringify(
        coordinatesMap
      )}`
    );
  }
  return new MultiPolygon(polygons.map(toPolygon));
}

function toCoordinate(coordinates?: any): Coordinate {
  if (!(coordinates && coordinates.longitude && coordinates.latitude)) {
    throw new Error(
      `Error converting to Geometry: expected GeoPoint, got ${stringify(
        coordinates
      )}`
    );
  }
  return new Coordinate(coordinates.longitude, coordinates.latitude);
}

function toLinearRing(coordinateList: List<any>): LinearRing {
  return new LinearRing(coordinateList.map(toCoordinate));
}

/**
 * Returns the list representation of a dictionary whose keys represent indices
 * within the final list. This is done for the outer map only; it is not
 * done recursively.
 *
 * For example, the following map representation:
 * {0: {0: 'alpha', 1: 'beta'}, 1: {0: 'gamma', 1: 'delta'}}
 *
 * Would be converted the the following list:
 * [{0: 'alpha', 1: 'beta'}, {0: 'gamma', 1: 'delta'}]
 *
 * `null` values are not allowed.
 */
function indexedMapToList(map?: any): List<any> {
  if (
    !map ||
    typeof map !== 'object' ||
    Object.values(map).some(v => v === null)
  ) {
    throw new Error(
      `Error converting to Geometry: invalid indexed map ${stringify(map)}`
    );
  }

  // Convert keys to numbers and sort by index.
  const entries = Object.entries(map)
    .map(([key, val]) => [Number(key), val] as [number, unknown])
    .sort((a, b) => a[0] - b[0]);

  // Verify keys correspond 1:1 to array indices (i.e., 0 to array.length-1).
  if (entries.map(e => e[0]).some((key, idx) => key !== idx)) {
    throw new Error(
      `Error converting to Geometry: non-sequential indices in ${stringify(
        entries
      )}`
    );
  }

  // Entries are already sorted by sequential indices, so we can now extract
  // values as an array.
  return List(entries.map(e => e[1]));
}
