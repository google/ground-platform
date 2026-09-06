/**
 * Copyright 2024 The Ground Authors.
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

import { GroundProtos } from '@ground/proto';
import { List } from 'immutable';

import { Coordinate } from 'app/models/geometry/coordinate';
import { Geometry } from 'app/models/geometry/geometry';
import { LinearRing } from 'app/models/geometry/linear-ring';
import { MultiPolygon } from 'app/models/geometry/multi-polygon';
import { Point } from 'app/models/geometry/point';
import { Polygon } from 'app/models/geometry/polygon';

import Pb = GroundProtos.ground.v1beta1;

// ── Model → Protobuf ──────────────────────────────────────────────────────────

export function geometryModelToPb(geometry: Geometry): Pb.IGeometry {
  if (geometry instanceof Point) {
    return new Pb.Geometry({point: pointModelToPb(geometry)});
  } else if (geometry instanceof Polygon) {
    return new Pb.Geometry({polygon: polygonModelToPb(geometry)});
  } else if (geometry instanceof MultiPolygon) {
    return new Pb.Geometry({multiPolygon: multiPolygonModelToPb(geometry)});
  } else {
    throw new Error('Unsupported geometry type');
  }
}

function pointModelToPb(point: Point): Pb.IPoint {
  return new Pb.Point({
    coordinates: coordinateModelToPb(point.coord),
  });
}

function polygonModelToPb(polygon: Polygon): Pb.IPolygon {
  return new Pb.Polygon({
    shell: linearRingModelToPb(polygon.shell),
    holes: polygon.holes.map(h => linearRingModelToPb(h)).toArray(),
  });
}

function multiPolygonModelToPb(mp: MultiPolygon): Pb.IMultiPolygon {
  return new Pb.MultiPolygon({
    polygons: mp.polygons.map(p => polygonModelToPb(p)).toArray(),
  });
}

function linearRingModelToPb(ring: LinearRing): Pb.ILinearRing {
  return new Pb.LinearRing({
    coordinates: ring.points.map(c => coordinateModelToPb(c)).toArray(),
  });
}

function coordinateModelToPb(coord: Coordinate): Pb.ICoordinates {
  return new Pb.Coordinates({
    longitude: coord.x,
    latitude: coord.y,
  });
}

// ── Protobuf → Model ──────────────────────────────────────────────────────────

export function geometryPbToModel(pb: Pb.IGeometry): Geometry {
  if (pb.point) {
    return pointPbToModel(pb.point);
  } else if (pb.polygon) {
    return polygonPbToModel(pb.polygon);
  } else if (pb.multiPolygon) {
    return multiPolygonPbToModel(pb.multiPolygon);
  } else {
    throw new Error('Unsupported or missing geometry type');
  }
}

function pointPbToModel(pb: Pb.IPoint): Point {
  if (!pb.coordinates) throw new Error('Point has no coordinates');
  const coordinates = coordinatesPbToModel(pb.coordinates);
  return new Point(coordinates);
}

function polygonPbToModel(pb: Pb.IPolygon): Polygon {
  const shell = linearRingPbToModel(pb.shell || {});
  const holes = (pb.holes || []).map(h => linearRingPbToModel(h));
  return new Polygon(shell, List(holes));
}

function multiPolygonPbToModel(pb: Pb.IMultiPolygon): MultiPolygon {
  if (!pb.polygons) throw new Error('Missing polygons in MultiPolygon');
  const polygons = pb.polygons.map(p => polygonPbToModel(p));
  return new MultiPolygon(List(polygons));
}

function linearRingPbToModel(pb: Pb.ILinearRing): LinearRing {
  if (!pb.coordinates) throw new Error('Missing coordinates in LinearRing');
  const coordinates = pb.coordinates.map(c => coordinatesPbToModel(c));
  return new LinearRing(List(coordinates));
}

export function coordinatesPbToModel(pb: Pb.ICoordinates): Coordinate {
  if (!pb.longitude || !pb.latitude) throw new Error('Incomplete coordinate');
  return new Coordinate(pb.longitude, pb.latitude);
}
