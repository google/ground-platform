/**
 * Copyright 2024 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {GroundProtos} from '@ground/proto';
import {Geometry, MultiPolygon, Point, Polygon, Position} from 'geojson';

import Pb = GroundProtos.ground.v1beta1;

/**
 * Returns the equivalent GeoJSON Geometry for the provided Geometry proto.
 */
export function toGeoJsonGeometry(pb: Pb.IGeometry): Geometry {
  if (pb.point) {
    return toGeoJsonPoint(pb.point);
  } else if (pb.polygon) {
    return toGeoJsonPolygon(pb.polygon);
  } else if (pb.multiPolygon) {
    return toGeoJsonMultiPolygon(pb.multiPolygon);
  } else {
    throw new Error('Unsupported or missing geometry');
  }
}

function toGeoJsonPoint(pb: Pb.IPoint): Point {
  if (!pb.coordinates) throw new Error('Invalid Point: coordinates missing');
  return {
    type: 'Point',
    coordinates: toGeoJsonPosition(pb.coordinates),
  };
}

function toGeoJsonPosition(coordinates: Pb.ICoordinates): Position {
  if (!coordinates.longitude || !coordinates.latitude)
    throw new Error('Invalid Point: coordinates missing');
  return [coordinates.longitude, coordinates.latitude];
}

function toGeoJsonPolygon(pb: Pb.IPolygon): Polygon {
  return {
    type: 'Polygon',
    coordinates: toGeoJsonPolygonCoordinates(pb),
  };
}

function toGeoJsonPolygonCoordinates(pb: Pb.IPolygon): Position[][] {
  if (!pb.shell) throw new Error('Invalid Polygon: shell coordinates missing');
  return [
    toGeoJsonPositionArray(pb.shell),
    ...(pb.holes || []).map(h => toGeoJsonPositionArray(h)),
  ];
}

function toGeoJsonPositionArray(ring: Pb.ILinearRing): Position[] {
  if (!ring.coordinates)
    throw new Error('Invalid LinearRing: coordinates missing');
  return ring.coordinates.map(c => toGeoJsonPosition(c));
}

function toGeoJsonMultiPolygon(multiPolygon: Pb.IMultiPolygon): MultiPolygon {
  if (!multiPolygon.polygons)
    throw new Error('Invalid multi-polygon: coordinates missing');
  return {
    type: 'MultiPolygon',
    coordinates: multiPolygon.polygons.map(p => toGeoJsonPolygonCoordinates(p)),
  };
}
