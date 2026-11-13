/**
 * Copyright 2026 The Ground Authors.
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

import { List, Map } from 'immutable';
import { Coordinate } from 'app/models/geometry/coordinate';
import { LinearRing } from 'app/models/geometry/linear-ring';
import { MultiPolygon } from 'app/models/geometry/multi-polygon';
import { Point } from 'app/models/geometry/point';
import { Polygon } from 'app/models/geometry/polygon';
import { LocationOfInterest } from 'app/models/loi.model';
import {
  CLUSTERING_ZOOM_THRESHOLD,
  Extent,
  LoiClusterIndex,
  getExtent,
  isClusterMode,
  mergeExtents,
} from './loi-cluster-index';

const WORLD: Extent = { west: -180, south: -85, east: 180, north: 85 };

function pointLoi(id: string, x: number, y: number, submissionCount = 0) {
  return new LocationOfInterest(
    id,
    'job',
    new Point(new Coordinate(x, y)),
    Map(),
    '',
    true,
    submissionCount
  );
}

function square(x: number, y: number, size: number) {
  return new Polygon(
    new LinearRing(
      List([
        new Coordinate(x, y),
        new Coordinate(x + size, y),
        new Coordinate(x + size, y + size),
        new Coordinate(x, y + size),
        new Coordinate(x, y),
      ])
    ),
    List()
  );
}

describe('LoiClusterIndex', () => {
  let index: LoiClusterIndex;

  beforeEach(() => {
    index = new LoiClusterIndex();
  });

  it('shows a single LOI as a bubble of one', () => {
    index.load([pointLoi('a', 10, 20, 0)]);

    const bubbles = index.getBubbles(WORLD, 5);

    expect(bubbles.length).toBe(1);
    expect(bubbles[0]).toEqual(
      jasmine.objectContaining({ loiId: 'a', total: 1, withData: 0 })
    );
    // Positions go through a projection, so they are only approximately equal.
    expect(bubbles[0].lat).toBeCloseTo(20, 6);
    expect(bubbles[0].lng).toBeCloseTo(10, 6);
  });

  it('counts the LOIs with submissions in a single LOI bubble', () => {
    index.load([pointLoi('a', 10, 20, 3)]);

    expect(index.getBubbles(WORLD, 5)[0].withData).toBe(1);
  });

  it('groups nearby LOIs, counting the ones with submissions', () => {
    index.load([
      pointLoi('a', 10, 20, 0),
      pointLoi('b', 10.0001, 20.0001, 2),
      pointLoi('c', 10.0002, 20.0002, 1),
    ]);

    const bubbles = index.getBubbles(WORLD, 5);

    expect(bubbles.length).toBe(1);
    expect(bubbles[0].total).toBe(3);
    expect(bubbles[0].withData).toBe(2);
    expect(bubbles[0].clusterId).toBeDefined();
    expect(bubbles[0].loiId).toBeUndefined();
  });

  it('keeps distant LOIs apart', () => {
    index.load([pointLoi('a', -100, 40), pointLoi('b', 100, -40)]);

    expect(index.getBubbles(WORLD, 5).map(b => b.loiId)).toEqual(
      jasmine.arrayWithExactContents(['a', 'b'])
    );
  });

  it('splits a group as the zoom level grows', () => {
    // About 1 km apart.
    index.load([pointLoi('a', 10, 20), pointLoi('b', 10.01, 20)]);

    expect(index.getBubbles(WORLD, 5).length).toBe(1);
    expect(index.getBubbles(WORLD, CLUSTERING_ZOOM_THRESHOLD - 1).length).toBe(
      2
    );
  });

  it('returns only the bubbles inside the viewport', () => {
    index.load([pointLoi('in', 10, 20), pointLoi('out', 100, -40)]);

    const bubbles = index.getBubbles(
      { west: 0, south: 0, east: 50, north: 50 },
      5
    );

    expect(bubbles.map(b => b.loiId)).toEqual(['in']);
  });

  it('places polygons at the center of their geometry', () => {
    index.load([new LocationOfInterest('p', 'job', square(10, 20, 2), Map())]);

    const [bubble] = index.getBubbles(WORLD, 5);

    expect(bubble.lng).toBeCloseTo(11);
    expect(bubble.lat).toBeCloseTo(21);
  });

  it('skips LOIs without a drawable geometry', () => {
    const unsupported = new LocationOfInterest(
      'x',
      'job',
      { geometryType: 'LINE_STRING' } as never,
      Map()
    );
    index.load([unsupported, pointLoi('a', 10, 20)]);

    expect(index.getBubbles(WORLD, 5).map(b => b.loiId)).toEqual(['a']);
  });

  it('replaces the previous LOIs when loaded again', () => {
    index.load([pointLoi('a', 10, 20)]);
    index.load([pointLoi('b', 30, 40)]);

    expect(index.getBubbles(WORLD, 5).map(b => b.loiId)).toEqual(['b']);
  });

  it('is empty before anything is loaded', () => {
    expect(index.getBubbles(WORLD, 5)).toEqual([]);
  });

  describe('getBubbleExtent', () => {
    it('covers the geometries of all LOIs in a group', () => {
      index.load([
        new LocationOfInterest('a', 'job', square(10, 20, 1), Map()),
        pointLoi('b', 10.5, 22),
      ]);
      const [bubble] = index.getBubbles(WORLD, 3);
      expect(bubble.clusterId).toBeDefined();

      expect(index.getBubbleExtent(bubble)).toEqual({
        west: 10,
        south: 20,
        east: 11,
        north: 22,
      });
    });

    it('covers the geometry of a single LOI', () => {
      index.load([
        new LocationOfInterest('a', 'job', square(10, 20, 1), Map()),
      ]);
      const [bubble] = index.getBubbles(WORLD, 3);

      expect(index.getBubbleExtent(bubble)).toEqual({
        west: 10,
        south: 20,
        east: 11,
        north: 21,
      });
    });
  });

  it('builds an index of 20,000 LOIs quickly', () => {
    const lois = Array.from({ length: 20000 }, (_, i) =>
      pointLoi(`loi${i}`, (i % 200) * 0.01, Math.floor(i / 200) * 0.01)
    );

    const start = performance.now();
    index.load(lois);
    const elapsed = performance.now() - start;

    expect(index.getBubbles(WORLD, 3).reduce((n, b) => n + b.total, 0)).toBe(
      20000
    );
    expect(elapsed).toBeLessThan(2000);
  });
});

describe('isClusterMode', () => {
  it('is on below the threshold and off from it', () => {
    expect(isClusterMode(CLUSTERING_ZOOM_THRESHOLD - 0.1)).toBeTrue();
    expect(isClusterMode(CLUSTERING_ZOOM_THRESHOLD)).toBeFalse();
  });
});

describe('getExtent', () => {
  it('covers a point', () => {
    expect(getExtent(new Point(new Coordinate(1, 2)))).toEqual({
      west: 1,
      south: 2,
      east: 1,
      north: 2,
    });
  });

  it('covers all polygons of a multi polygon', () => {
    const multi = new MultiPolygon(List([square(0, 0, 1), square(5, 5, 1)]));

    expect(getExtent(multi)).toEqual({ west: 0, south: 0, east: 6, north: 6 });
  });
});

describe('mergeExtents', () => {
  it('returns null when there is nothing to merge', () => {
    expect(mergeExtents([])).toBeNull();
    expect(mergeExtents([null, undefined])).toBeNull();
  });
});
