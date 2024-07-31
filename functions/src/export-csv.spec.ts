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
  createGetRequestSpy,
  createResponseSpy,
} from './testing/http-test-helpers';
import {
  $job_id,
  $geometry,
  $submission_count,
  $source,
  $properties,
  $custom_tag,
  $point,
  $coordinates,
  $latitude,
  $longitude,
  $string_value,
  $numeric_value,
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
        name: 'Test job',
      },
    },
  };
  const pointLoi1 = {
    id: 'loi100',
    [$job_id]: 'job123',
    [$custom_tag]: 'POINT_001',
    [$geometry]: {
      [$point]: {[$coordinates]: {[$latitude]: 10.1, [$longitude]: 125.6}},
    },
    [$submission_count]: 0,
    [$source]: 1, // IMPORTED
    [$properties]: {
      name: {[$string_value]: 'Dinagat Islands'},
      area: {[$numeric_value]: 3.08},
    }
  };
  const pointLoi2 = {
    id: 'loi200',
    [$job_id]: 'job123',
    [$custom_tag]: 'POINT_002',
    [$geometry]: {
      [$point]: {[$coordinates]: {[$latitude]: 47.05, [$longitude]: 8.30}},
    },
    [$submission_count]: 0,
    [$source]: 2, // FIELD_DATA
    [$properties]: {
      name: {[$string_value]: 'Luzern'},
    }
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
      desc: 'export points w/o submissions',
      lois: [pointLoi1, pointLoi2],
      submissions: [],
      expected: [
        `"system:index","geometry","name","area","data:contributor_name","data:contributor_email"`,
        `"POINT_001","POINT (125.6 10.1)","Dinagat Islands",3.08,,`,
        `"POINT_002","POINT (8.3 47.05)","Luzern",,,`,
      ],
    },
  ];

  beforeEach(() => {
    mockFirestore = createMockFirestore();
    stubAdminApi(mockFirestore);
  });

  afterEach(() => {
    resetDatastore();
  });

  testCases.forEach(({desc, lois, submissions, expected}) =>
    it(desc, async () => {
      // Populate database.
      mockFirestore.doc(`surveys/${surveyId}`).set(survey);
      lois?.forEach(loi =>
        mockFirestore.doc(`surveys/${surveyId}/lois/${loi.id}`).set(loi)
      );
      // submissions?.forEach(submission =>
      //   mockFirestore
      //     .doc(`surveys/${surveyId}/submissions/${submissions.id}`)
      //     .set(submission)
      // );

      // Build mock request and response.
      const req = await createGetRequestSpy({
        url: '/exportCsv',
        query: {
          survey: surveyId,
          job: jobId,
        },
      });
      const chunks: string[] = [];
      const res = createResponseSpy(chunks);

      // Run export CSV handler.
      await exportCsvHandler(req, res, {email} as DecodedIdToken);

      // Check post-conditions.
      expect(res.status).toHaveBeenCalledOnceWith(HttpStatus.OK);
      expect(res.type).toHaveBeenCalledOnceWith('text/csv');
      expect(res.setHeader).toHaveBeenCalledOnceWith(
        'Content-Disposition',
        'attachment; filename=test-job.csv'
      );
      const output = chunks.join('').trim();
      const lines = output.split('\n'); 
      expect(lines).toEqual(expected);
    })
  );
});
