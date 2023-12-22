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

import {Coordinate} from 'app/models/geometry/coordinate';
import {LinearRing} from 'app/models/geometry/linear-ring';
import {Polygon} from 'app/models/geometry/polygon';
import {Collection, List, is, isValueObject} from 'immutable';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function deepEqualityTester(a: any, b: any): boolean | undefined {
  // `is()` doesn't do deep equals on arrays or dictionaries, so we let Jasmine
  // handle these instead. Jasmine will still pass indiviudal elements back to
  // this tester.
  if (
    Array.isArray(a) ||
    Array.isArray(b) ||
    !isValueObject(a) ||
    !isValueObject(b)
  ) {
    return;
  }
  return is(a, b);
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function formatImmutableCollection(val: any): string | undefined {
  if (val instanceof Collection) {
    return JSON.stringify((val as Collection<never, never>).toJS());
  }
  return;
}

/** Converts an array of coordinates to a Polygon.  */
export function polygonShellCoordsToPolygon(coordinates: number[][]): Polygon {
  const coordinateInstances = coordinates.map(
    ([longitude, latitude]) => new Coordinate(longitude, latitude)
  );
  return new Polygon(new LinearRing(List(coordinateInstances)), List());
}
