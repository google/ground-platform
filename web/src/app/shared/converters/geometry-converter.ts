/**
 * Copyright 2022 Google LLC
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

import firebase from 'firebase/app';
import { List, Map } from 'immutable';
import { LinearRing } from '../models/geometry/linear-ring';
import { Point } from '../models/geometry/point';
import { Polygon } from '../models/geometry/polygon';
import { Coordinate } from './../models/geometry/coordinate';
import { Geometry, GeometryType } from './../models/geometry/geometry';
import { MultiPolygon } from './../models/geometry/multi-polygon';
import { indexedMapToList } from './indexed-map';

import GeoPoint = firebase.firestore.GeoPoint;

const GEOJSON_GEOMETRY_TYPES = Map([
  [GeometryType.POINT, 'Point'],
  [GeometryType.POLYGON, 'Polygon'],
  [GeometryType.MULTI_POLYGON, 'MultiPolygon'],
]);

export function toGeometry(geometry?: any): Geometry {
  if (!geometry) {
    throw new Error('Missing geometry');
  }
  switch (geometry.type) {
    case GEOJSON_GEOMETRY_TYPES.get(GeometryType.POINT):
      return toPoint(geometry.coordinates);
    case GEOJSON_GEOMETRY_TYPES.get(GeometryType.POLYGON):
      return toPolygon(geometry.coordinates);
    case GEOJSON_GEOMETRY_TYPES.get(GeometryType.MULTI_POLYGON):
      return toMultiPolygon(geometry.coordinates);
    default:
      throw new Error(`Unsupported geometry type ${geometry.type}`);
  }
}

function toPoint(coordinates?: any): Point {
  return new Point(toCoordinate(coordinates));
}

function isGeoPointList(list: List<any>): boolean {
  return (
    list instanceof List &&
    !list.isEmpty() &&
    list.every(v => v instanceof GeoPoint)
  );
}

function toPolygon(coordinatesMap?: any): Polygon {
  const rings = indexedMapToList(coordinatesMap) as List<List<GeoPoint>>;
  if (rings.isEmpty() || !rings.every(l => isGeoPointList(l))) {
    throw new Error('Missing or invalid coordinates');
  }
  const shell = new LinearRing(rings.shift()!.map(c => toCoordinate(c)));
  const holes = rings.map(h => new LinearRing(h.map(c => toCoordinate(c))));
  return new Polygon(shell, holes);
}

function toMultiPolygon(coordinatesMap?: any): MultiPolygon {
  const polygons = indexedMapToList(coordinatesMap);
  if (polygons.isEmpty()) {
    throw new Error('Empty multi-polygon in db ${coordinatesMap}');
  }
  return new MultiPolygon(polygons.map(p => toPolygon(p)));
}

function toCoordinate(coordinates?: any): Coordinate {
  if (!(coordinates && coordinates instanceof GeoPoint)) {
    throw new Error(`Expected GeoPoint, got ${coordinates}`);
  }
  return new Coordinate(coordinates.latitude, coordinates.longitude);
}
