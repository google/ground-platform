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

import { DocumentData } from '@angular/fire/firestore';
import { registry } from '@ground/lib';
import { GroundProtos } from '@ground/proto';
import { Map } from 'immutable';

import { Geometry } from 'app/models/geometry/geometry';
import { LocationOfInterest } from 'app/models/loi.model';

import { geometryPbToModel } from './geometry-data-converter';
import { loiDocToModel } from './loi-data-converter';

import Pb = GroundProtos.ground.v1beta1;

const lo = registry.getFieldIds(Pb.LocationOfInterest);
const ge = registry.getFieldIds(Pb.Geometry);
const po = registry.getFieldIds(Pb.Point);
const co = registry.getFieldIds(Pb.Coordinates);
const pr = registry.getFieldIds(Pb.LocationOfInterest.Property);

const x = -42.121;
const y = 28.482;

const geoPointData = {
  point: {
    coordinates: { latitude: x, longitude: y },
  },
  [ge.point]: {
    [po.coordinates]: { [co.latitude]: x, [co.longitude]: y },
  },
};

describe('loiDocToModel', () => {
  const geoPointDataWithError = geometryPbToModel(geoPointData);

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
        [lo.jobId]: 'jobId0',
        [lo.geometry]: geoPointData,
        [lo.properties]: {},
        [lo.source]: Pb.LocationOfInterest.Source.IMPORTED,
      },
      want: new LocationOfInterest(
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
        [lo.jobId]: 'jobId0',
        [lo.geometry]: geoPointData,
        [lo.properties]: {
          prop0: { [pr.stringValue]: 'value0' },
          prop1: { [pr.numericValue]: 1 },
          prop2: { [pr.stringValue]: '' },
        },
        [lo.source]: Pb.LocationOfInterest.Source.IMPORTED,
      },
      want: new LocationOfInterest(
        'id0',
        'jobId0',
        geoPoint,
        Map<string, string | number>([
          ['prop0', 'value0'],
          ['prop1', 1],
        ])
      ),
    },
  ];

  for (const t of testData) {
    const got = loiDocToModel(t.inputId, t.inputData);
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
        [lo.jobId]: '',
        [lo.geometry]: geoPointData,
        [lo.properties]: {},
      },
      wantErrorMessage: 'Missing job_id in loi id0',
    },
  ];

  for (const t of testData) {
    const got = loiDocToModel(t.inputId, t.inputData);
    if (!(got instanceof Error)) {
      throw new Error(`expected error but instead got ${got}`);
    }

    it(t.expectation, () =>
      expect((got as Error).message).toContain(t.wantErrorMessage)
    );
  }
});
