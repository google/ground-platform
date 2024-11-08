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
import {DecodedIdToken} from 'firebase-admin/auth';
import HttpStatus from 'http-status-codes';
import {SURVEY_ORGANIZER_ROLE} from './common/auth';
import {resetDatastore} from './common/context';
import {Firestore} from 'firebase-admin/firestore';
import {exportCsvHandler} from './export-csv';
import {registry} from '@ground/lib';
import {GroundProtos} from '@ground/proto';

import Pb = GroundProtos.ground.v1beta1;
const sv = registry.getFieldIds(Pb.Survey);
const j = registry.getFieldIds(Pb.Job);
const t = registry.getFieldIds(Pb.Task);
const l = registry.getFieldIds(Pb.LocationOfInterest);
const pr = registry.getFieldIds(Pb.LocationOfInterest.Property);
const p = registry.getFieldIds(Pb.Point);
const c = registry.getFieldIds(Pb.Coordinates);
const g = registry.getFieldIds(Pb.Geometry);
const s = registry.getFieldIds(Pb.Submission);
const d = registry.getFieldIds(Pb.TaskData);
const mq = registry.getFieldIds(Pb.Task.MultipleChoiceQuestion);
const op = registry.getFieldIds(Pb.Task.MultipleChoiceQuestion.Option);
const cl = registry.getFieldIds(Pb.TaskData.CaptureLocationResult);

describe('exportCsv()', () => {
  let mockFirestore: Firestore;
  const email = 'somebody@test.it';
  const userId = 'user5000';
  const survey = {
    [sv.name]: 'Test survey',
    [sv.acl]: {
      [email]: SURVEY_ORGANIZER_ROLE,
    },
  };
  const emptyJob = {id: 'job123'};
  const job1 = {
    id: 'job123',
    [j.name]: 'Test job',
    [j.tasks]: [
      {
        [t.id]: 'task001',
        [t.prompt]: 'What is the meaning of life?',
        [t.textQuestion]: {
          ['1' /* type */]: Pb.Task.TextQuestion.Type.SHORT_TEXT,
        },
      },
      {
        [t.id]: 'task002',
        [t.prompt]: 'How much?',
        [t.numberQuestion]: {
          ['1' /* type */]: Pb.Task.NumberQuestion.Type.FLOAT,
        },
      },
      {
        [t.id]: 'task003',
        [t.prompt]: 'When?',
        [t.dateTimeQuestion]: {
          ['1' /* type */]: Pb.Task.DateTimeQuestion.Type.BOTH_DATE_AND_TIME,
        },
      },
      {
        [t.id]: 'task004',
        [t.prompt]: 'Which ones?',
        [t.multipleChoiceQuestion]: {
          [mq.type]: Pb.Task.MultipleChoiceQuestion.Type.SELECT_MULTIPLE,
          [mq.options]: [
            {
              [op.id]: 'aaa',
              [op.index]: 1,
              [op.label]: 'AAA',
            },
            {
              [op.id]: 'bbb',
              [op.index]: 2,
              [op.label]: 'BBB',
            },
          ],
          [mq.hasOtherOption]: true,
        },
      },
      {
        [t.id]: 'task005',
        [t.prompt]: 'Where are you now?',
        [t.captureLocation]: {
          ['1' /* min_accuracy_meters */]: 999999,
        },
      },
      {
        [t.id]: 'task006',
        [t.prompt]: 'Take a photo',
        [t.takePhoto]: {
          ['1' /* min_heading_degrees */]: 0,
          ['2' /* max_heading_degrees */]: 360,
        },
      },
    ],
  };
  const pointLoi1 = {
    id: 'loi100',
    [l.jobId]: job1.id,
    [l.customTag]: 'POINT_001',
    [l.geometry]: {
      [g.point]: {[p.coordinates]: {[c.latitude]: 10.1, [c.longitude]: 125.6}},
    },
    [l.submission_count]: 0,
    [l.source]: Pb.LocationOfInterest.Source.IMPORTED,
    [l.properties]: {
      name: {[pr.stringValue]: 'Dinagat Islands'},
      area: {[pr.numericValue]: 3.08},
    },
  };
  const pointLoi2 = {
    id: 'loi200',
    [l.jobId]: job1.id,
    [l.customTag]: 'POINT_002',
    [l.geometry]: {
      [g.point]: {[p.coordinates]: {[c.latitude]: 47.05, [c.longitude]: 8.3}},
    },
    [l.submissionCount]: 0,
    [l.source]: Pb.LocationOfInterest.Source.FIELD_DATA,
    [l.properties]: {
      name: {[pr.stringValue]: 'Luzern'},
    },
  };
  const submission1a = {
    id: '001a',
    [s.loiId]: pointLoi1.id,
    [s.index]: 1,
    [s.jobId]: job1.id,
    [s.ownerId]: userId,
    [s.taskData]: [
      {
        [d.id]: 'data001a',
        [d.taskId]: 'task001',
        [d.textResponse]: {
          '1': 'Submission 1',
        },
      },
      {
        [d.id]: 'data002a',
        [d.taskId]: 'task002',
        [d.numberResponse]: {
          '1': 42,
        },
      },
    ],
  };
  const submission1b = {
    id: '001b',
    [s.loiId]: pointLoi1.id,
    [s.index]: 2,
    [s.jobId]: job1.id,
    [s.ownerId]: userId,
    [s.taskData]: [
      {
        [d.id]: 'data001b',
        [d.taskId]: 'task001',
        [d.textResponse]: {
          '1': 'Submission 2',
        },
      },
      {
        [d.id]: 'data003a',
        [d.taskId]: 'task003',
        [d.dateTimeResponse]: {
          '1': {
            '1': 1331209044, // seconds
          },
        },
      },
    ],
  };
  const submission2a = {
    id: '002a',
    [s.loiId]: pointLoi2.id,
    [s.index]: 1,
    [s.jobId]: job1.id,
    [s.ownerId]: userId,
    [s.taskData]: [
      {
        [d.id]: 'data004',
        [d.taskId]: 'task004',
        [d.multipleChoiceResponses]: {
          '1': ['aaa', 'bbb'],
          '2': 'Other',
        },
      },
      {
        [d.id]: 'data005a',
        [d.taskId]: 'task005',
        [d.captureLocationResult]: {
          [cl.coordinates]: {
            [c.latitude]: -123,
            [c.longitude]: 45,
          },
        },
      },
      {
        [d.id]: 'data006b',
        [d.taskId]: 'task006',
        [d.takePhotoResult]: {
          '1': 'http://photo/url',
        },
      },
    ],
  };
  const testCases = [
    {
      desc: 'export points w/o submissions',
      jobId: emptyJob.id,
      survey: survey,
      jobs: [emptyJob],
      lois: [pointLoi1, pointLoi2],
      submissions: [],
      expectedFilename: 'ground-export.csv',
      expectedCsv: [
        '"system:index","geometry","name","area","data:contributor_name","data:contributor_email"',
        '"POINT_001","POINT (125.6 10.1)","Dinagat Islands",3.08,,',
        '"POINT_002","POINT (8.3 47.05)","Luzern",,,',
      ],
    },
    {
      desc: 'export points w/submissions',
      jobId: job1.id,
      survey: survey,
      jobs: [job1],
      lois: [pointLoi1, pointLoi2],
      submissions: [submission1a, submission1b, submission2a],
      expectedFilename: 'test-job.csv',
      expectedCsv: [
        '"system:index","geometry","name","area","data:What is the meaning of life?","data:How much?","data:When?","data:Which ones?","data:Where are you now?","data:Take a photo","data:contributor_name","data:contributor_email"',
        '"POINT_001","POINT (125.6 10.1)","Dinagat Islands",3.08,"Submission 1",42,,,,,,',
        '"POINT_001","POINT (125.6 10.1)","Dinagat Islands",3.08,"Submission 2",,"2012-03-08T12:17:24.000Z",,,,,',
        '"POINT_002","POINT (8.3 47.05)","Luzern",,,,,"AAA,BBB,Other","POINT (45 -123)","http://photo/url",,',
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

  testCases.forEach(
    ({
      desc,
      jobId,
      survey,
      jobs,
      lois,
      submissions,
      expectedFilename,
      expectedCsv,
    }) =>
      it(desc, async () => {
        // Populate database.
        mockFirestore.doc(`surveys/${survey.id}`).set(survey);
        jobs?.forEach(({id, ...job}) =>
          mockFirestore.doc(`surveys/${survey.id}/jobs/${id}`).set(job)
        );
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
