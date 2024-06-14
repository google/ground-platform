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
import HttpStatus from 'http-status-codes';
import {invokeCallbackAsync} from './handlers';

describe('importGeoJson()', () => {
  const surveyId = 'survey001';
  const jobId = 'job123';
  // const SUBMISSION = new TestDocumentSnapshot({ loiId });
  // const CONTEXT = new TestEventContext({ surveyId });
  // const SUBMISSIONS_PATH = `${SURVEY_PATH}/submissions`;
  // const LOI_COLLECTION = `surveys/${surveyId}/lois`;

  beforeAll(() => {
    stubAdminApi();
  });

  afterAll(() => {});

  fit('imports points', async () => {
    spyOn(require('firebase-admin/auth'), 'getAuth').and.returnValue({
      getUser: () => {},
      verifySessionCookie: () => {},
    });
    // const add = jasmine.createSpy('add');
    // TODO: rename back to mockFirestore
    testFirestore.doc(`surveys/${surveyId}`).set({
      name: 'Test',
    });
    // testFirestore.collection.withArgs(LOI_COLLECTION).and.returnValue({add});
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
    const encoder = new FormDataEncoder(form);

    const req = jasmine.createSpyObj<functions.https.Request>(
      'request',
      ['unpipe'],
      {
        method: 'POST',
        url: '/importGeoJson',
        headers: encoder.headers,
        rawBody: await buffer(encoder),
      }
    );
    const res = jasmine.createSpyObj<functions.Response<any>>('response', [
      'send',
      'status',
      'end',
    ]);
    res.status.and.returnValue(res);
    res.end.and.returnValue(res);

    await invokeCallbackAsync(
      importGeoJsonHandler,
      req,
      res,
      {} as DecodedIdToken
    );

    expect(res.status).toHaveBeenCalledOnceWith(HttpStatus.OK);

    const loiCollection = await testFirestore
      .collection(`surveys/${surveyId}/lois`)
      .get();
    const loiData = loiCollection.docs.map(doc => doc.data());
    console.log(loiData);
    expect(loiData).toEqual([
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

/**
 * Returns a new Object with the specified coordinates. Use in place of GeoPoint
 * in tests to work around lack of support in MockFirebase lib.
 */
function TestGeoPoint(_latitude: number, _longitude: number) {
  return {
    _latitude,
    _longitude,
  };
}
