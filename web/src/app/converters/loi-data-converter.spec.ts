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
import {DocumentData} from '@angular/fire/firestore';
import {GeoPoint} from 'firebase/firestore';
import {Map} from 'immutable';

import {GEOMETRY_TYPES, toGeometry} from 'app/converters/geometry-converter';
import {Geometry, GeometryType} from 'app/models/geometry/geometry';
import {Point} from 'app/models/geometry/point';
import {
  AreaOfInterest,
  GenericLocationOfInterest,
  GeoJsonLocationOfInterest,
  LocationOfInterest,
  PointOfInterest,
} from 'app/models/loi.model';

import {LoiDataConverter} from './loi-data-converter';

const x = -42.121;
const y = 28.482;
const geoPointData = {
  type: GEOMETRY_TYPES.get(GeometryType.POINT),
  coordinates: new GeoPoint(x, y),
};

describe('toLocationOfInterest', () => {
  const geoPointDataWithError = toGeometry(geoPointData);
  if (geoPointData instanceof Error) {
    throw new Error(
      `got unexpected error in geometry conversion ${geoPointDataWithError}`
    );
  }
  const geoPoint = geoPointDataWithError as Geometry;

  const testData: {
    expectation: string;
    inputId: string;
    inputData: DocumentData;
    want: LocationOfInterest;
  }[] = [
    {
      expectation: 'converts geometry with no properties',
      inputId: 'id0',
      inputData: {
        jobId: 'jobId0',
        geometry: geoPointData,
        properties: {},
      },
      want: new GenericLocationOfInterest(
        'id0',
        'jobId0',
        geoPoint,
        Map<string, string | number>()
      ),
    },
    {
      expectation: 'converts geometry with properties',
      inputId: 'id0',
      inputData: {
        jobId: 'jobId0',
        geometry: geoPointData,
        properties: {
          prop0: 'value0',
          prop1: 1,
          prop2: '',
        },
      },
      want: new GenericLocationOfInterest(
        'id0',
        'jobId0',
        geoPoint,
        Map<string, string | number>([
          ['prop0', 'value0'],
          ['prop1', 1],
          ['prop2', ''],
        ])
      ),
    },
  ];

  for (const t of testData) {
    const got = LoiDataConverter.toLocationOfInterest(t.inputId, t.inputData);
    if (got instanceof Error) {
      throw new Error(`got unexpected error: ${got}`);
    }

    it(t.expectation, () => expect(got as LocationOfInterest).toEqual(t.want));
  }
});

describe('toLocationOfInterest_Error', () => {
  const testData: {
    expectation: string;
    inputId: string;
    inputData: DocumentData;
    wantErrorMessage: string;
  }[] = [
    {
      expectation: 'Missing job ID in UI data',
      inputId: 'id0',
      inputData: {
        jobId: '',
        geometry: geoPointData,
        properties: {},
      },
      wantErrorMessage: 'missing job id',
    },
  ];

  for (const t of testData) {
    const got = LoiDataConverter.toLocationOfInterest(t.inputId, t.inputData);
    if (!(got instanceof Error)) {
      throw new Error(`expected error but instead got ${got}`);
    }

    it(t.expectation, () =>
      expect((got as Error).message).toContain(t.wantErrorMessage)
    );
  }
});

describe('loiToJS', () => {
  const geoPointWithError = toGeometry(geoPointData);
  if (geoPointData instanceof Error) {
    throw new Error(
      `got unexpected error in geometry conversion ${geoPointWithError}`
    );
  }
  const geoPoint = geoPointWithError as unknown as GeoPoint;
  const point = geoPointWithError as unknown as Point;

  const testData: {
    expectation: string;
    loi: LocationOfInterest;
    want: {};
  }[] = [
    {
      expectation: 'converts GenericLocationOfInterest with Point geometry',
      loi: new GenericLocationOfInterest('id0', 'jobId0', point, Map()),
      want: {
        jobId: 'jobId0',
        geometry: {
          coordinates: new GeoPoint(point.coord.x, point.coord.y),
          type: GEOMETRY_TYPES.get(GeometryType.POINT),
        },
      },
    },
    {
      expectation: 'converts GeoJsonLocationOfInterest',
      loi: new GeoJsonLocationOfInterest(
        'id0',
        'jobId0',
        geoPoint,
        Map<string, string | number>()
      ),
      want: {
        jobId: 'jobId0',
        geoJson: point,
      },
    },
    {
      expectation: 'converts AreaOfInterest',
      loi: new AreaOfInterest(
        'id0',
        'jobId0',
        [geoPoint],
        Map<string, string | number>()
      ),
      want: {
        jobId: 'jobId0',
        polygonVertices: [point],
      },
    },
    {
      expectation: 'converts empty AreaOfInterest',
      loi: new AreaOfInterest(
        'id0',
        'jobId0',
        [],
        Map<string, string | number>()
      ),
      want: {
        jobId: 'jobId0',
        polygonVertices: [],
      },
    },
  ];

  for (const t of testData) {
    const got = LoiDataConverter.loiToJS(t.loi);
    if (got instanceof Error) {
      throw new Error(`got unexpected error ${got}`);
    }

    it(t.expectation, () => expect(got as {}).toEqual(t.want));
  }
});

describe('loiToJS_Error', () => {
  const geoPointWithError = toGeometry(geoPointData);
  if (geoPointData instanceof Error) {
    throw new Error(
      `got unexpected error in geometry conversion ${geoPointWithError}`
    );
  }
  const geoPoint = geoPointWithError as unknown as GeoPoint;

  const testData: {
    expectation: string;
    loi: LocationOfInterest;
    wantErrorMessage: string;
  }[] = [
    {
      expectation: 'Cannot convert PointOfInterest',
      loi: new PointOfInterest(
        'id0',
        'jobId0',
        geoPoint,
        Map<string, string | number>()
      ),
      wantErrorMessage: 'Cannot convert unexpected loi class',
    },
  ];

  for (const t of testData) {
    const got = LoiDataConverter.loiToJS(t.loi);
    if (!(got instanceof Error)) {
      throw new Error(`expected error but instead got ${got}`);
    }

    it(t.expectation, () =>
      expect((got as Error).message).toContain(t.wantErrorMessage)
    );
  }
});
