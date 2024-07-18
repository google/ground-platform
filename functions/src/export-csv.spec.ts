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
  TestGeoPoint,
  createMockFirestore,
  stubAdminApi,
} from '@ground/lib/dist/testing/firestore';
import {
  createGetRequestSpy,
  createResponseSpy,
} from './testing/http-test-helpers';
import {
  $job_id,
  $geometry,
  $submission_count,
  $source,
  $properties,
  $point,
  $coordinates,
  $latitude,
  $longitude,
} from '@ground/lib/dist/testing/proto-field-aliases';
import {DecodedIdToken} from 'firebase-admin/auth';
import HttpStatus from 'http-status-codes';
import {OWNER_ROLE} from './common/auth';
import {resetDatastore} from './common/context';
import {Firestore} from 'firebase-admin/firestore';
import {exportCsvHandler} from './export-csv';

fdescribe('exportCsv()', () => {
  let mockFirestore: Firestore;
  const surveyId = 'survey001';
  const jobId = 'job123';
  const email = 'somebody@test.it';
  const survey = {
    name: 'Test survey',
    acl: {
      [email]: OWNER_ROLE,
    },
    jobs: {
        [jobId]: {
            name: 'Test job'
        }
    }
  };
  const testProperties = {
    name: 'Dinagat Islands',
    area: 3.08,
  };
  const pointLoi = {
    [$job_id]: 'job123',
    [$geometry]: {
      [$point]: {[$coordinates]: {[$latitude]: 10.1, [$longitude]: 125.6}},
    },
    [$submission_count]: 0,
    [$source]: 1, // IMPORTED
    [$properties]: {name: 'Dinagat Islands', area: 3.08},
    jobId: 'job123',
    predefined: true,
    geometry: {type: 'Point', coordinates: TestGeoPoint(10.1, 125.6)},
    properties: testProperties,
  };
//   const polygonLoi = {
//     [$job_id]: 'job123',
//     [$geometry]: {
//       [$polygon]: {
//         [$shell]: {
//           [$coordinates]: [
//             {[$latitude]: 0, [$longitude]: 100},
//             {[$latitude]: 0, [$longitude]: 101},
//             {[$latitude]: 1, [$longitude]: 101},
//             {[$latitude]: 0, [$longitude]: 100},
//           ],
//         },
//       },
//     },
//     [$submission_count]: 0,
//     [$source]: 1, // IMPORTED
//     jobId: 'job123',
//     predefined: true,
//     geometry: {
//       type: 'Polygon',
//       coordinates: {
//         0: {
//           0: TestGeoPoint(0, 100),
//           1: TestGeoPoint(0, 101),
//           2: TestGeoPoint(1, 101),
//           3: TestGeoPoint(0, 100),
//         },
//       },
//     },
//   };
  
  const testCases = [
    {
      desc: 'imports points',
      input: {},
      expected: [pointLoi],
    }
  ];

  beforeEach(() => {
    mockFirestore = createMockFirestore();
    stubAdminApi(mockFirestore);
  });

  afterEach(() => {
    resetDatastore();
  });

  testCases.forEach(({desc, input, expected}) =>
    it(desc, async () => {
      // Add survey.
      mockFirestore.doc(`surveys/${surveyId}`).set(survey);

      // Build mock request and response.
      const req = await createGetRequestSpy({
        url: '/exportCsv',
        query: {
          survey: surveyId,
          job: jobId,
        },
      });
      const res = createResponseSpy();

      // Run export CSV handler.
      await  exportCsvHandler(req, res, {email} as DecodedIdToken);

      // Check post-conditions.
      expect(res.status).toHaveBeenCalledOnceWith(HttpStatus.OK);
      expect(res.type).toHaveBeenCalledOnceWith('text/csv');
      expect(res.setHeader).toHaveBeenCalledOnceWith('Content-Disposition', 'attachment; filename=test-job.csv');
    })
  );
});
