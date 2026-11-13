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

import Supercluster, { PointFeature } from 'supercluster';
import { Geometry } from 'app/models/geometry/geometry';
import { MultiPolygon } from 'app/models/geometry/multi-polygon';
import { Point } from 'app/models/geometry/point';
import { Polygon } from 'app/models/geometry/polygon';
import { LocationOfInterest } from 'app/models/loi.model';

/**
 * Below this zoom level LOIs are shown as bubbles, from it up as their own
 * shapes. Same as `CLUSTERING_ZOOM_THRESHOLD` in Ground Android.
 */
export const CLUSTERING_ZOOM_THRESHOLD = 14;

/** Distance in pixels within which LOIs are grouped into one bubble. */
const CLUSTER_RADIUS_PX = 100;

/** Returns true if LOIs are to be shown as bubbles at `zoom`. */
export function isClusterMode(zoom: number): boolean {
  return zoom < CLUSTERING_ZOOM_THRESHOLD;
}

/** A rectangle in degrees. */
export interface Extent {
  west: number;
  south: number;
  east: number;
  north: number;
}

/**
 * What is drawn in place of the LOIs it stands for: a group of LOIs, or a
 * single one. Like Android, a single LOI is a bubble too (reading "0/1").
 */
export interface LoiBubble {
  /** Stable while the bubble stands for the same LOIs; usable as a key. */
  key: string;
  lat: number;
  lng: number;
  /** Number of LOIs the bubble stands for. */
  total: number;
  /** How many of them have at least one submission. */
  withData: number;
  /** Set if the bubble stands for a single LOI. */
  loiId?: string;
  /** Set if the bubble stands for a group of LOIs. */
  clusterId?: number;
}

/** What is kept of an LOI in the index. */
interface IndexedLoi {
  id: string;
  withData: number;
}

/**
 * Groups LOIs into bubbles for the zoom levels below
 * `CLUSTERING_ZOOM_THRESHOLD`, and answers which bubbles are in a viewport.
 *
 * It is a plain class with no dependency on Google Maps, so it can be tested
 * and reasoned about on its own. LOIs are placed at the center of their
 * geometry, as in Ground Android.
 */
export class LoiClusterIndex {
  private index = this.createIndex();
  private extents = new Map<string, Extent>();

  constructor() {
    // Supercluster can't be queried before it has been loaded.
    this.index.load([]);
  }

  /** Replaces the indexed LOIs. LOIs without a drawable geometry are skipped. */
  load(lois: Iterable<LocationOfInterest>): void {
    const extents = new Map<string, Extent>();
    const features: PointFeature<IndexedLoi>[] = [];
    for (const { id, geometry, submissionCount } of lois) {
      const extent = getExtent(geometry);
      if (!extent) continue;
      extents.set(id, extent);
      features.push({
        type: 'Feature',
        properties: { id, withData: submissionCount > 0 ? 1 : 0 },
        geometry: {
          type: 'Point',
          coordinates: [
            (extent.west + extent.east) / 2,
            (extent.south + extent.north) / 2,
          ],
        },
      });
    }
    this.extents = extents;
    this.index = this.createIndex();
    this.index.load(features);
  }

  /** Returns the bubbles for the LOIs inside `viewport` at `zoom`. */
  getBubbles(viewport: Extent, zoom: number): LoiBubble[] {
    const { west, south, east, north } = viewport;
    return this.index.getClusters([west, south, east, north], zoom).map(f => {
      const [lng, lat] = f.geometry.coordinates;
      const props = f.properties;
      if ('cluster' in props && props.cluster) {
        return {
          key: `cluster:${props.cluster_id}`,
          lat,
          lng,
          total: props.point_count,
          withData: props.withData,
          clusterId: props.cluster_id,
        };
      }
      const { id, withData } = props as IndexedLoi;
      return { key: `loi:${id}`, lat, lng, total: 1, withData, loiId: id };
    });
  }

  /** Returns the area covered by the geometries of the LOIs in `bubble`. */
  getBubbleExtent(bubble: LoiBubble): Extent | null {
    const ids =
      bubble.loiId !== undefined
        ? [bubble.loiId]
        : this.index
            .getLeaves(bubble.clusterId!, Infinity)
            .map(f => (f.properties as IndexedLoi).id);
    return mergeExtents(ids.map(id => this.extents.get(id)));
  }

  private createIndex(): Supercluster<IndexedLoi, { withData: number }> {
    return new Supercluster<IndexedLoi, { withData: number }>({
      radius: CLUSTER_RADIUS_PX,
      // Beyond this zoom level, no LOIs are grouped.
      maxZoom: CLUSTERING_ZOOM_THRESHOLD - 1,
      map: ({ withData }) => ({ withData }),
      reduce: (total, props) => {
        total.withData += props.withData;
      },
    });
  }
}

/** Returns the area covered by `geometry`, or null for unsupported ones. */
export function getExtent(geometry: Geometry): Extent | null {
  if (geometry instanceof Point) {
    const { x, y } = geometry.coord;
    return { west: x, south: y, east: x, north: y };
  }
  if (geometry instanceof Polygon) {
    return mergeExtents(
      geometry.shell.points.map(({ x, y }) => ({
        west: x,
        south: y,
        east: x,
        north: y,
      }))
    );
  }
  if (geometry instanceof MultiPolygon) {
    return mergeExtents(geometry.polygons.map(p => getExtent(p)));
  }
  return null;
}

/** Returns the smallest area covering all of `extents`, or null if none. */
export function mergeExtents(
  extents: Iterable<Extent | null | undefined>
): Extent | null {
  let merged: Extent | null = null;
  for (const extent of extents) {
    if (!extent) continue;
    merged = merged
      ? {
          west: Math.min(merged.west, extent.west),
          south: Math.min(merged.south, extent.south),
          east: Math.max(merged.east, extent.east),
          north: Math.max(merged.north, extent.north),
        }
      : extent;
  }
  return merged;
}
