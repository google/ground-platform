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
  createMockFirestore,
  stubAdminApi,
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
import {SURVEY_ORGANIZER_ROLE} from './common/auth';
import {resetDatastore} from './common/context';
import {Firestore} from 'firebase-admin/firestore';
import {registry} from '@ground/lib';
import {GroundProtos} from '@ground/proto';

import Pb = GroundProtos.ground.v1beta1;
const sv = registry.getFieldIds(Pb.Survey);
const l = registry.getFieldIds(Pb.LocationOfInterest);
const pr = registry.getFieldIds(Pb.LocationOfInterest.Property);
const g = registry.getFieldIds(Pb.Geometry);
const p = registry.getFieldIds(Pb.Point);
const c = registry.getFieldIds(Pb.Coordinates);
const pg = registry.getFieldIds(Pb.Polygon);
const lr = registry.getFieldIds(Pb.LinearRing);
const mp = registry.getFieldIds(Pb.MultiPolygon);

describe('importGeoJson()', () => {
  let mockFirestore: Firestore;
  const surveyId = 'survey001';
  const jobId = 'job123';
  const email = 'somebody@test.it';
  const survey = {
    [sv.name]: 'Test',
    [sv.acl]: {
      [email]: SURVEY_ORGANIZER_ROLE,
    },
  };
  const testProperties = {
    name: 'Dinagat Islands',
    area: 3.08,
  };
  const geoJsonWithPoint = {
    type: 'FeatureCollection',
    features: [
      {
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [125.6, 10.1],
        },
        properties: testProperties,
      },
    ],
  };
  const pointLoi = {
    [l.jobId]: 'job123',
    [l.geometry]: {
      [g.point]: {[p.coordinates]: {[c.latitude]: 10.1, [c.longitude]: 125.6}},
    },
    [l.submissionCount]: 0,
    [l.source]: 1, // IMPORTED
    [l.properties]: {
      name: {[pr.stringValue]: 'Dinagat Islands'},
      area: {[pr.numericValue]: 3.08},
    },
  };
  const geoJsonWithPolygon = {
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
  };
  const polygonLoi = {
    [l.jobId]: 'job123',
    [l.geometry]: {
      [g.polygon]: {
        [pg.shell]: {
          [lr.coordinates]: [
            {[c.latitude]: 0, [c.longitude]: 100},
            {[c.latitude]: 0, [c.longitude]: 101},
            {[c.latitude]: 1, [c.longitude]: 101},
            {[c.latitude]: 0, [c.longitude]: 100},
          ],
        },
      },
    },
    [l.submissionCount]: 0,
    [l.source]: 1, // IMPORTED
  };
  const geoJsonWithMultiPolygon = {
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
  };
  const multiPolygonLoi = {
    [l.jobId]: 'job123',
    [l.geometry]: {
      [g.multiPolygon]: {
        [mp.polygons]: [
          // polygons[0]
          {
            [pg.shell]: {
              [lr.coordinates]: [
                {[c.latitude]: 0, [c.longitude]: 100},
                {[c.latitude]: 0, [c.longitude]: 101},
                {[c.latitude]: 1, [c.longitude]: 101},
                {[c.latitude]: 0, [c.longitude]: 100},
              ],
            },
          },
          // polygons[1]
          {
            [pg.shell]: {
              [lr.coordinates]: [
                {[c.latitude]: 1, [c.longitude]: 120},
                {[c.latitude]: 1, [c.longitude]: 121},
                {[c.latitude]: 2, [c.longitude]: 121},
                {[c.latitude]: 1, [c.longitude]: 120},
              ],
            },
          },
        ],
      },
    },
    [l.submissionCount]: 0,
    [l.source]: 1, // IMPORTED
  };
  const geoJsonWithNonCRS84Point = {
    type: 'FeatureCollection',
    features: [
      {
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [300, 10.1],
        },
        properties: testProperties,
      },
    ],
  };

  const testCases = [
    {
      desc: 'imports points',
      input: geoJsonWithPoint,
      expectedStatus: HttpStatus.OK,
      expected: [pointLoi],
    },
    {
      desc: 'imports polygons',
      input: geoJsonWithPolygon,
      expectedStatus: HttpStatus.OK,
      expected: [polygonLoi],
    },
    {
      desc: 'imports multi-polygons',
      input: geoJsonWithMultiPolygon,
      expectedStatus: HttpStatus.OK,
      expected: [multiPolygonLoi],
    },
    {
      desc: 'imports unsupported feature type',
      input: {...geoJsonWithPoint, type: 'UnsupportedFeature'},
      expectedStatus: HttpStatus.BAD_REQUEST,
      expected: [],
    },
    {
      desc: 'imports non CRS84 point',
      input: geoJsonWithNonCRS84Point,
      expectedStatus: HttpStatus.OK,
      expected: [],
    },
  ];

  beforeEach(() => {
    mockFirestore = createMockFirestore();
    stubAdminApi(mockFirestore);
  });

  afterEach(() => {
    resetDatastore();
  });

  async function loiData(surveyId: string) {
    const lois = await mockFirestore
      .collection(`surveys/${surveyId}/lois`)
      .get();
    return lois.docs.map(doc => doc.data());
  }

  function createPostData(surveyId: string, jobId: string, geoJson: object) {
    const form = new FormData();
    form.append('survey', surveyId);
    form.append('job', jobId);
    form.append('file', new Blob([JSON.stringify(geoJson)]), 'file.json');
    return form;
  }

  testCases.forEach(({desc, input, expectedStatus, expected}) =>
    it(desc, async () => {
      // Add survey.
      mockFirestore.doc(`surveys/${surveyId}`).set(survey);

      // Build mock request and response.
      const req = await createPostRequestSpy(
        {url: '/importGeoJson'},
        createPostData(surveyId, jobId, input)
      );
      const res = createResponseSpy();

      try {
        // Run import GeoJSON function.
        // Ideally we would call `importGeoJson` directly rather than via `invokeCallbackAsync`,
        // but that would require mocking all middleware which may be overkill.
        await invokeCallbackAsync(importGeoJsonCallback, req, res, {
          email,
        } as DecodedIdToken);
      } catch (error) {
        console.log(error);
      }

      // Check post-conditions.
      expect(res.status).toHaveBeenCalledOnceWith(expectedStatus);
      expect(await loiData(surveyId)).toEqual(expected);
    })
  );
});
