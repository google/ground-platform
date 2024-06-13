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

import {stubAdminApi, testFirestore} from '@ground/lib/dist/testing/firestore';
import {importGeoJsonHandler} from './import-geojson';
import {DecodedIdToken} from 'firebase-admin/auth';
import functions from 'firebase-functions';
import {Blob, FormData} from 'formdata-node';
import {buffer} from 'node:stream/consumers';
import {FormDataEncoder} from 'form-data-encoder';

const test = require('firebase-functions-test')();

describe('importGeoJson()', () => {
  const surveyId = 'survey001';
  const jobId = 'job123';
  // const SUBMISSION = new TestDocumentSnapshot({ loiId });
  // const CONTEXT = new TestEventContext({ surveyId });
  // const SUBMISSIONS_PATH = `${SURVEY_PATH}/submissions`;
  const LOI_COLLECTION = `surveys/${surveyId}/lois`;

  beforeAll(() => {
    stubAdminApi();
  });

  afterAll(() => {
    test.cleanup();
  });

  fit('imports points', async () => {
    spyOn(require('firebase-admin/auth'), 'getAuth').and.returnValue({
      getUser: () => {},
      verifySessionCookie: () => {},
    });
    const add = jasmine.createSpy('add');
    // TODO: rename back to mockFirestore
    testFirestore.collection.withArgs(LOI_COLLECTION).and.returnValue({add});
    const form = new FormData();
    form.append('survey', surveyId);
    form.append('job', jobId);
    form.append(
      'file',
      new Blob([JSON.stringify({
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
      })]),
      'file.json'
    );
    const encoder = new FormDataEncoder(form);

    const req = jasmine.createSpyObj<functions.https.Request>(
      'request',
      {},
      {
        method: 'POST',
        url: '/importGeoJson',
        headers: encoder.headers,
        rawBody: await buffer(encoder),
      }
    );
    const res = jasmine.createSpyObj<functions.Response<any>>('response', [
      'json',
      'send',
      'status',
      'render',
      'header',
      'redirect',
      'end',
      'write',
    ]);
    // const res = new MockExpressResponse();

    importGeoJsonHandler(req, res, {} as DecodedIdToken);

    expect(res.status).toHaveBeenCalledOnceWith(200);
    expect(add).toHaveBeenCalledOnceWith({});
  });
});
