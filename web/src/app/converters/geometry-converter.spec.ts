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

import { GeoPoint } from 'firebase/firestore';
import { List } from 'immutable';

import { Coordinate } from 'app/models/geometry/coordinate';
import { GeometryType } from 'app/models/geometry/geometry';
import { LinearRing } from 'app/models/geometry/linear-ring';
import { MultiPolygon } from 'app/models/geometry/multi-polygon';
import { Point } from 'app/models/geometry/point';
import { Polygon } from 'app/models/geometry/polygon';
import { deepEqualityTester, formatImmutableCollection } from 'testing/helpers';

import { GEOMETRY_TYPES, toGeometry } from './geometry-converter';

// Pairs of (latitude, longitude).
type Path = Array<[number, number]>;

const x = -42.121;
const y = 28.482;
const path1: Path = [
  [-89.63410225, 41.89729784],
  [-89.63805046, 41.8952534],
  [-89.63659134, 41.8893753],
  [-89.62886658, 41.88956698],
  [-89.62800827, 41.89544507],
  [-89.63410225, 41.89729784],
];
const path2: Path = [
  [-89.63453141, 41.89193106],
  [-89.631184, 41.89090878],
  [-89.63066902, 41.8939756],
  [-89.63358726, 41.89480618],
  [-89.63453141, 41.89193106],
];
const path3: Path = [
  [-89.61006966, 41.89333669],
  [-89.61479034, 41.89832003],
  [-89.6171936, 41.89455062],
  [-89.6152195, 41.89154771],
  [-89.61006966, 41.89333669],
];
const path4: Path = [
  [-89.61393204, 41.89320891],
  [-89.61290207, 41.89429505],
  [-89.61418953, 41.89538118],
  [-89.61513367, 41.89416727],
  [-89.61393204, 41.89320891],
];

function point(x: number, y: number): Point {
  return new Point(new Coordinate(x, y));
}

function linearRing(path: Path): LinearRing {
  return new LinearRing(coordinateList(path));
}

function polygon(shell: Path, ...holes: Array<Path>): Polygon {
  return new Polygon(linearRing(shell), List(holes.map(h => linearRing(h))));
}

function multiPolygon(...polygons: Array<Polygon>): MultiPolygon {
  return new MultiPolygon(List(polygons));
}

function coordinateList(path: Path): List<Coordinate> {
  return List(path.map(it => new Coordinate(it[1], it[0])));
}

function indexedGeoPointMap(path: Path): {} {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const map: { [key: string]: any } = {};
  path.forEach((it, idx) => (map[String(idx)] = new GeoPoint(it[0], it[1])));
  return map;
}

describe('geometry-converter.ts', () => {
  beforeEach(() => {
    jasmine.addCustomEqualityTester(deepEqualityTester);
    jasmine.addCustomObjectFormatter(formatImmutableCollection);
  });

  describe('toGeometry', () => {
    // Valid / passing test cases.
    [
      {
        expectation: 'converts map to Point',
        input: {
          type: GEOMETRY_TYPES.get(GeometryType.POINT),
          coordinates: new GeoPoint(y, x),
        },
        expectedOutput: point(x, y),
      },
      {
        expectation: 'converts map to Polygon',
        input: {
          type: GEOMETRY_TYPES.get(GeometryType.POLYGON),
          coordinates: {
            '0': indexedGeoPointMap(path1),
            '1': indexedGeoPointMap(path2),
          },
        },
        expectedOutput: polygon(path1, path2),
      },
      {
        expectation: 'converts map to MultiPolygon',
        input: {
          type: GEOMETRY_TYPES.get(GeometryType.MULTI_POLYGON),
          coordinates: {
            '0': {
              '0': indexedGeoPointMap(path1),
              '1': indexedGeoPointMap(path2),
            },
            '1': {
              '0': indexedGeoPointMap(path3),
              '1': indexedGeoPointMap(path4),
            },
          },
        },
        expectedOutput: multiPolygon(
          polygon(path1, path2),
          polygon(path3, path4)
        ),
      },
    ].forEach(({ expectation, input, expectedOutput }) =>
      it(expectation, () => expect(toGeometry(input)).toEqual(expectedOutput))
    );

    // Failure states.
    [
      {
        expectation: 'fails on null geometry',
        input: null,
      },
      {
        expectation: 'fails on unknown type',
        input: {
          type: 'Squircle',
          coordinates: new GeoPoint(y, x),
        },
      },
      {
        expectation: 'fails on point with missing coordinates',
        input: {
          type: GEOMETRY_TYPES.get(GeometryType.POINT),
        },
      },
      {
        expectation: 'fails on point with null coordinates',
        input: {
          type: GEOMETRY_TYPES.get(GeometryType.POINT),
          coordinates: null,
        },
      },
      {
        expectation: 'fails on point with wrong coordinates type',
        input: {
          type: GEOMETRY_TYPES.get(GeometryType.POINT),
          coordinates: 'Kapow!',
        },
      },
    ].forEach(({ expectation, input }) =>
      it(expectation, () => expect(toGeometry(input)).toBeInstanceOf(Error))
    );
  });
});
