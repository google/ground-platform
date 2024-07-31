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
  const jobId = 'job123';
  const email = 'somebody@test.it';
    // TODO(#1758): Use new proto-based survey and job representation.
  const survey1 = {
    id: 'survey001',
    name: 'Test survey 1',
    acl: {
      [email]: OWNER_ROLE,
    }
  };
  const survey2 = {
    id: 'survey002',
    name: 'Test survey 2',
    acl: {
      [email]: OWNER_ROLE,
    },
    jobs: {
      [jobId]: {
        name: 'Test job',
        tasks: {
          task001: {
            type: 'text_field',
            label: 'What is the meaning of life?',
          },
          task002: {
            type: 'capture_location',
            label: 'Where are you now?',
          },
          task003: {
            type: 'draw_area',
            label: 'Delimit plot boundaries',
          },
        },
      },
    },
  };
  const pointLoi1 = {
    id: 'loi100',
    [$job_id]: jobId,
    [$custom_tag]: 'POINT_001',
    [$geometry]: {
      [$point]: {[$coordinates]: {[$latitude]: 10.1, [$longitude]: 125.6}},
    },
    [$submission_count]: 0,
    [$source]: 1, // IMPORTED
    [$properties]: {
      name: {[$string_value]: 'Dinagat Islands'},
      area: {[$numeric_value]: 3.08},
    },
  };
  const pointLoi2 = {
    id: 'loi200',
    [$job_id]: jobId,
    [$custom_tag]: 'POINT_002',
    [$geometry]: {
      [$point]: {[$coordinates]: {[$latitude]: 47.05, [$longitude]: 8.3}},
    },
    [$submission_count]: 0,
    [$source]: 2, // FIELD_DATA
    [$properties]: {
      name: {[$string_value]: 'Luzern'},
    },
  };
  const submission1a = {
    id: '001a',
    // TODO
  };
  const submission1b = {
    id: '001b',
    // TODO
  };
  const submission2a = {
    id: '002a',
    // TODO
  };
  const testCases = [
    {
      desc: 'export points w/o submissions',
      survey: survey1,
      lois: [pointLoi1, pointLoi2],
      submissions: [],
      expectedFilename: 'ground-export.csv',
      expectedCsv: [
        `"system:index","geometry","name","area","data:contributor_name","data:contributor_email"`,
        `"POINT_001","POINT (125.6 10.1)","Dinagat Islands",3.08,,`,
        `"POINT_002","POINT (8.3 47.05)","Luzern",,,`,
      ],
    },
    {
      desc: 'export points w/submissions',
      survey: survey2,
      lois: [pointLoi1, pointLoi2],
      submissions: [submission1a, submission1b, submission2a],
      expectedFilename: 'test-job.csv',
      expectedCsv: [
        `"system:index","geometry","name","area","data:What is the meaning of life?","data:Where are you now?","data:Delimit plot boundaries","data:contributor_name","data:contributor_email"`,
        `"POINT_001","POINT (125.6 10.1)","Dinagat Islands",3.08,,,,,`,
        `"POINT_002","POINT (8.3 47.05)","Luzern",,,,,,`,
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

  testCases.forEach(({desc, survey, lois, submissions, expectedFilename, expectedCsv}) =>
    it(desc, async () => {
      // Populate database.
      mockFirestore.doc(`surveys/${survey.id}`).set(survey);
      lois?.forEach(({id, ...loi}) =>
        mockFirestore.doc(`surveys/${survey.id}/lois/${id}`).set(loi)
      );
      submissions?.forEach(({id, ...submission}) =>
        mockFirestore
          .doc(`surveys/${survey.id}/submissions/${id}`)
          .set(submission)
      );

      // Build mock request and response.
      const req = await createGetRequestSpy({
        url: '/exportCsv',
        query: {
          survey: survey.id,
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
        `attachment; filename=${expectedFilename}`
      );
      const output = chunks.join('').trim();
      const lines = output.split('\n');
      expect(lines).toEqual(expectedCsv);
    })
  );
});
