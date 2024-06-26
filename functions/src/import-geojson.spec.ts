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

import {
  stubAdminApi,
  mockFirestore,
  TestGeoPoint,
} from '@ground/lib/dist/testing/firestore';
import {
  createPostRequestSpy,
  createResponseSpy,
} from './testing/http-test-helpers';
import {importGeoJsonCallback} from './import-geojson';
import {DecodedIdToken} from 'firebase-admin/auth';
import {Blob, FormData} from 'formdata-node';
import HttpStatus from 'http-status-codes';
import {invokeCallbackAsync} from './handlers';
import {OWNER_ROLE} from './common/auth';

describe('importGeoJson()', () => {
  const surveyId = 'survey001';
  const jobId = 'job123';
  const email = 'somebody@test.it';
  const survey = {
    name: 'Test',
    acl: {
      [email]: OWNER_ROLE,
    },
  };

  afterEach(() => {
    // TODO: Reset db.
  });

  beforeAll(() => {
    stubAdminApi();
  });

  afterAll(() => {});
  [
    {
      desc: 'imports points',
      input: {
        type: 'FeatureCollection',
        features: [
          {
            type: 'Feature',
            geometry: {
              type: 'Point',
              coordinates: [125.6, 10.1],
            },
            properties: {
              name: 'Dinagat Islands',
              area: 3.08,
            },
          },
        ],
      },
      expected: [
        {
          '2': 'job123',
          '3': {'1': {'1': {'1': 10.1, '2': 125.6}}},
          '9': 1,
          '10': {name: 'Dinagat Islands', area: 3.08},
          jobId: 'job123',
          predefined: true,
          geometry: {type: 'Point', coordinates: TestGeoPoint(10.1, 125.6)},
          properties: {name: 'Dinagat Islands', area: 3.08},
        },
      ],
    },
    {
      desc: 'imports polygons',
      input: {
        type: 'FeatureCollection',
        features: [
          {
            type: 'Feature',
            geometry: {
              type: 'Polygon',
              coordinates: [
                [
                  [100.0, 0.0],
                  [101.0, 0.0],
                  [101.0, 1.0],
                  [100.0, 0.0],
                ],
              ],
            },
          },
        ],
      },
      expected: [
        {
          '2': 'job123',
          '3': {
            '2': {
              '1': {
                '1': [
                  // shell
                  {'1': 0, '2': 100},
                  {'1': 0, '2': 101},
                  {'1': 1, '2': 101},
                  {'1': 0, '2': 100},
                ],
              },
            },
          },
          '4': 0, // submission_count
          '9': 1, // source: IMPORTED
          jobId: 'job123',
          predefined: true,
          geometry: {
            type: 'Polygon',
            coordinates: {
              0: {
                0: TestGeoPoint(0, 100),
                1: TestGeoPoint(0, 101),
                2: TestGeoPoint(1, 101),
                3: TestGeoPoint(0, 100),
              },
            },
          },
        },
      ],
    },
    {
      desc: 'imports multi-polygons',
      input: {
        type: 'FeatureCollection',
        features: [
          {
            type: 'Feature',
            geometry: {
              type: 'MultiPolygon',
              coordinates: [
                [
                  [
                    [100.0, 0.0],
                    [101.0, 0.0],
                    [101.0, 1.0],
                    [100.0, 0.0],
                  ],
                ],
                [
                  [
                    [120.0, 1.0],
                    [121.0, 1.0],
                    [121.0, 2.0],
                    [120.0, 1.0],
                  ],
                ],
              ],
            },
          },
        ],
      },
      expected: [
        {
          '2': 'job123', // job_id
          // geometry
          '3': {
            // multi_polygon
            '3': {
              // polygons
              '1': [
                // polygon 1
                {
                  // shell
                  '1': {
                    // coordinates
                    '1': [
                      {'1': 0, '2': 100},
                      {'1': 0, '2': 101},
                      {'1': 1, '2': 101},
                      {'1': 0, '2': 100},
                    ],
                  },
                },
                // polygon 2
                {
                  // shell
                  '1': {
                    // coordinates
                    '1': [
                      {'1': 1, '2': 120},
                      {'1': 1, '2': 121},
                      {'1': 2, '2': 121},
                      {'1': 1, '2': 120},
                    ],
                  },
                },
              ],
            },
          },
          '4': 0, // submission_count
          '9': 1, // source: IMPORTED
          jobId: 'job123',
          predefined: true,
          geometry: {
            type: 'MultiPolygon',
            coordinates: {
              0: {
                0: {
                  0: TestGeoPoint(0, 100),
                  1: TestGeoPoint(0, 101),
                  2: TestGeoPoint(1, 101),
                  3: TestGeoPoint(0, 100),
                },
              },
              1: {
                0: {
                  0: TestGeoPoint(1, 120),
                  1: TestGeoPoint(1, 121),
                  2: TestGeoPoint(2, 121),
                  3: TestGeoPoint(1, 120),
                },
              },
            },
          },
        },
      ],
    },
  ].forEach(({desc, input, expected}) =>
    it(desc, async () => {
      mockFirestore.doc(`surveys/${surveyId}`).set(survey);
      const form = new FormData();
      form.append('survey', surveyId);
      form.append('job', jobId);
      form.append('file', new Blob([JSON.stringify(input)]), 'file.json');

      const req = await createPostRequestSpy({url: '/importGeoJson'}, form);
      const res = createResponseSpy();
      await invokeCallbackAsync(importGeoJsonCallback, req, res, {
        email,
      } as DecodedIdToken);

      expect(res.status).toHaveBeenCalledOnceWith(HttpStatus.OK);

      const lois = await mockFirestore
        .collection(`surveys/${surveyId}/lois`)
        .get();
      expect(lois.docs.map(doc => doc.data())).toEqual(expected);
    })
  );
});
