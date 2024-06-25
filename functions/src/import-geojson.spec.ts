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

  beforeAll(() => {
    stubAdminApi();
  });

  afterAll(() => {});

  it('imports points', async () => {
    mockFirestore.doc(`surveys/${surveyId}`).set(survey);
    const form = new FormData();
    form.append('survey', surveyId);
    form.append('job', jobId);
    form.append(
      'file',
      new Blob([
        JSON.stringify({
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
              },
            },
          ],
        }),
      ]),
      'file.json'
    );

    const req = await createPostRequestSpy({url: '/importGeoJson'}, form);
    const res = createResponseSpy();
    await invokeCallbackAsync(importGeoJsonCallback, req, res, {
      email,
    } as DecodedIdToken);

    expect(res.status).toHaveBeenCalledOnceWith(HttpStatus.OK);

    const lois = await mockFirestore
      .collection(`surveys/${surveyId}/lois`)
      .get();
    expect(lois.docs.map(doc => doc.data())).toEqual([
      {
        '2': 'job123',
        '3': {'1': {'1': {'1': 10.1, '2': 125.6}}},
        '9': 1,
        '10': {name: 'Dinagat Islands'},
        jobId: 'job123',
        predefined: true,
        geometry: {type: 'Point', coordinates: TestGeoPoint(10.1, 125.6)},
        properties: {name: 'Dinagat Islands'},
      },
    ]);
  });
});
