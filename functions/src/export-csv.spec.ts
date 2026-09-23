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
} from '@ground/lib/testing/firestore';
import {
  createGetRequestSpy,
  createResponseSpy,
} from './testing/http-test-helpers';
import { DecodedIdToken } from 'firebase-admin/auth';
import { SURVEY_ORGANIZER_ROLE } from './common/auth';
import { getDatastore, resetDatastore } from './common/context';
import * as context from './common/context';
import { PassThrough } from 'stream';
import { Firestore, QueryDocumentSnapshot } from 'firebase-admin/firestore';
import { exportCsvHandler } from './export-csv';
import { registry } from '@ground/lib';
import { GroundProtos } from '@ground/proto';

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
const a = registry.getFieldIds(Pb.AuditInfo);

/**
 * Fetches LOIs and submissions using simple queries compatible with
 * mock-cloud-firestore (no compound indexes or cursor-based pagination),
 * then joins them in-memory replicating the left-outer-join logic of
 * the production QueryIterator-based implementation.
 */
async function* fetchLoisSubmissionsFromMock(
  db: Firestore,
  surveyId: string,
  jobId: string,
  ownerId: string | null
): AsyncGenerator<[QueryDocumentSnapshot, QueryDocumentSnapshot | undefined]> {
  const [loisSnap, subsSnap] = await Promise.all([
    db.collection(`surveys/${surveyId}/lois`).where(l.jobId, '==', jobId).get(),
    db
      .collection(`surveys/${surveyId}/submissions`)
      .where(s.jobId, '==', jobId)
      .get(),
  ]);

  const loisDocs = [...loisSnap.docs].sort((a, b) =>
    a.id.localeCompare(b.id)
  ) as unknown as QueryDocumentSnapshot[];

  let subsDocs = [...subsSnap.docs] as unknown as QueryDocumentSnapshot[];
  if (ownerId) subsDocs = subsDocs.filter(d => d.get(s.ownerId) === ownerId);
  subsDocs.sort((a, b) => {
    const loiComp = String(a.get(s.loiId)).localeCompare(
      String(b.get(s.loiId))
    );
    return loiComp !== 0 ? loiComp : a.id.localeCompare(b.id);
  });

  let si = 0;
  for (const loiDoc of loisDocs) {
    let found = false;
    while (si < subsDocs.length && subsDocs[si].get(s.loiId) === loiDoc.id) {
      yield [loiDoc, subsDocs[si]];
      si++;
      found = true;
    }
    if (!found) yield [loiDoc, undefined];
  }
}

describe('exportCsv()', () => {
  let mockFirestore: Firestore;
  let storageChunks: string[];
  let mockFile: jasmine.SpyObj<any>;
  const FIREBASE_DOWNLOAD_URL_PREFIX =
    'https://firebasestorage.googleapis.com/v0/b/test-bucket/o/';
  const email = 'somebody@test.it';
  const userId = 'user5000';
  const surveyId = 'survey001';
  const host = 'ground.example.com';
  const survey = {
    [sv.name]: 'Test survey',
    [sv.acl]: {
      [email]: SURVEY_ORGANIZER_ROLE,
    },
  };
  const auditInfo = {
    [a.userId]: userId,
    [a.displayName]: 'display_name',
    [a.emailAddress]: 'address@email.com',
    [a.clientTimestamp]: { 1: 1, 2: 0 },
    [a.serverTimestamp]: { 1: 1, 2: 0 },
  };

  const emptyJob = { id: 'job123' };
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
    [l.id]: 'loi100',
    [l.jobId]: job1.id,
    [l.customTag]: 'POINT_001',
    [l.geometry]: {
      [g.point]: {
        [p.coordinates]: { [c.latitude]: 10.1, [c.longitude]: 125.6 },
      },
    },
    [l.submissionCount]: 0,
    [l.source]: Pb.LocationOfInterest.Source.IMPORTED,
    [l.properties]: {
      name: { [pr.stringValue]: 'Dinagat Islands' },
      area: { [pr.numericValue]: 3.08 },
    },
  };
  const pointLoi2 = {
    id: 'loi200',
    [l.id]: 'loi200',
    [l.jobId]: job1.id,
    [l.customTag]: 'POINT_002',
    [l.geometry]: {
      [g.point]: {
        [p.coordinates]: { [c.latitude]: 47.05, [c.longitude]: 8.3 },
      },
    },
    [l.submissionCount]: 0,
    [l.source]: Pb.LocationOfInterest.Source.FIELD_DATA,
    [l.properties]: {
      name: { [pr.stringValue]: 'Luzern' },
    },
  };
  const submission1a = {
    id: '001a',
    [s.id]: '001a',
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
    [s.id]: '001b',
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
    [s.created]: auditInfo,
  };
  const submission2a = {
    id: '002a',
    [s.id]: '002a',
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
          '2': 'other',
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
          '1': `user-media/surveys/${surveyId}/submissions/task006-4f8b1c2d-9a3e-4d71-b0c5-2e6a7f9d1834.jpg`,
        },
      },
    ],
  };
  // A job with two photo tasks, used to check that each photo cell in a row
  // gets a link of its own. Photo links are keyed by task, so a submission
  // answering several photo tasks must yield several distinct URLs.
  const photoJob = {
    id: 'job456',
    [j.name]: 'Photo job',
    [j.tasks]: [
      {
        [t.id]: 'task006',
        [t.prompt]: 'Take a photo',
        [t.takePhoto]: {
          ['1' /* min_heading_degrees */]: 0,
          ['2' /* max_heading_degrees */]: 360,
        },
      },
      {
        [t.id]: 'task007',
        [t.prompt]: 'Take another photo',
        [t.takePhoto]: {
          ['1' /* min_heading_degrees */]: 0,
          ['2' /* max_heading_degrees */]: 360,
        },
      },
    ],
  };
  const photoLoi = {
    id: 'loi300',
    [l.id]: 'loi300',
    [l.jobId]: photoJob.id,
    [l.customTag]: 'POINT_003',
    [l.geometry]: {
      [g.point]: {
        [p.coordinates]: { [c.latitude]: 47.05, [c.longitude]: 8.3 },
      },
    },
    [l.submissionCount]: 0,
    [l.source]: Pb.LocationOfInterest.Source.FIELD_DATA,
    [l.properties]: {
      name: { [pr.stringValue]: 'Luzern' },
    },
  };
  const photoSubmission = {
    id: '003a',
    [s.id]: '003a',
    [s.loiId]: photoLoi.id,
    [s.index]: 1,
    [s.jobId]: photoJob.id,
    [s.ownerId]: userId,
    [s.taskData]: [
      {
        [d.id]: 'data006c',
        [d.taskId]: 'task006',
        [d.takePhotoResult]: {
          '1': `user-media/surveys/${surveyId}/submissions/task006-4f8b1c2d-9a3e-4d71-b0c5-2e6a7f9d1834.jpg`,
        },
      },
      {
        [d.id]: 'data007a',
        [d.taskId]: 'task007',
        [d.takePhotoResult]: {
          '1': `user-media/surveys/${surveyId}/submissions/task007-7c2e9b41-5d6a-4f83-a1b0-3e8d5c7f2941.jpg`,
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
        '"system:index","geometry","name","area","data:contributor_name","data:contributor_email","data:created_client_timestamp","data:created_server_timestamp"',
        '"POINT_001","POINT (125.6 10.1)","Dinagat Islands",3.08,,,,',
        '"POINT_002","POINT (8.3 47.05)","Luzern",,,,,',
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
        '"system:index","geometry","name","area","data:What is the meaning of life?","data:How much?","data:When?","data:Which ones?","data:Where are you now?","data:Take a photo","data:contributor_name","data:contributor_email","data:created_client_timestamp","data:created_server_timestamp"',
        '"POINT_001","POINT (125.6 10.1)","Dinagat Islands",3.08,"Submission 1",42,,,,,,,"1970-01-01T00:00:00.000Z","1970-01-01T00:00:00.000Z"',
        '"POINT_001","POINT (125.6 10.1)","Dinagat Islands",3.08,"Submission 2",,"2012-03-08T12:17:24.000Z",,,,"display_name","address@email.com","1970-01-01T00:00:01.000Z","1970-01-01T00:00:01.000Z"',
        '"POINT_002","POINT (8.3 47.05)","Luzern",,,,,"AAA,BBB,Other: other","POINT (45 -123)","https://ground.example.com/exportMedia?survey=survey001&submission=002a&task=task006",,,"1970-01-01T00:00:00.000Z","1970-01-01T00:00:00.000Z"',
      ],
    },
    {
      desc: 'export points w and w/o submissions',
      jobId: job1.id,
      survey: survey,
      jobs: [job1],
      lois: [pointLoi1, pointLoi2],
      submissions: [submission1a, submission1b],
      expectedFilename: 'test-job.csv',
      expectedCsv: [
        '"system:index","geometry","name","area","data:What is the meaning of life?","data:How much?","data:When?","data:Which ones?","data:Where are you now?","data:Take a photo","data:contributor_name","data:contributor_email","data:created_client_timestamp","data:created_server_timestamp"',
        '"POINT_001","POINT (125.6 10.1)","Dinagat Islands",3.08,"Submission 1",42,,,,,,,"1970-01-01T00:00:00.000Z","1970-01-01T00:00:00.000Z"',
        '"POINT_001","POINT (125.6 10.1)","Dinagat Islands",3.08,"Submission 2",,"2012-03-08T12:17:24.000Z",,,,"display_name","address@email.com","1970-01-01T00:00:01.000Z","1970-01-01T00:00:01.000Z"',
        '"POINT_002","POINT (8.3 47.05)","Luzern",,,,,,,,,,,',
      ],
    },
    {
      desc: 'links each photo task in a row separately',
      jobId: photoJob.id,
      survey: survey,
      jobs: [photoJob],
      lois: [photoLoi],
      submissions: [photoSubmission],
      expectedFilename: 'photo-job.csv',
      expectedCsv: [
        '"system:index","geometry","name","data:Take a photo","data:Take another photo","data:contributor_name","data:contributor_email","data:created_client_timestamp","data:created_server_timestamp"',
        '"POINT_003","POINT (8.3 47.05)","Luzern","https://ground.example.com/exportMedia?survey=survey001&submission=003a&task=task006","https://ground.example.com/exportMedia?survey=survey001&submission=003a&task=task007",,,"1970-01-01T00:00:00.000Z","1970-01-01T00:00:00.000Z"',
      ],
    },
  ];

  beforeEach(() => {
    mockFirestore = createMockFirestore();
    stubAdminApi(mockFirestore);
    storageChunks = [];
    const writeStream = new PassThrough();
    writeStream.on('data', (chunk: Buffer) =>
      storageChunks.push(chunk.toString())
    );
    mockFile = jasmine.createSpyObj('file', [
      'createWriteStream',
      'setMetadata',
    ]);
    mockFile.createWriteStream.and.returnValue(writeStream);
    mockFile.setMetadata.and.resolveTo([{}]);
    Object.defineProperty(mockFile, 'name', {
      value: 'temp/user5000/job.csv',
    });
    Object.defineProperty(mockFile, 'bucket', {
      value: { name: 'test-bucket' },
    });
    const mockBucket = jasmine.createSpyObj('bucket', ['file']);
    mockBucket.file.and.returnValue(mockFile);
    spyOn(context, 'getStorageBucket').and.returnValue(mockBucket);
    spyOn(getDatastore(), 'fetchPartialLocationsOfInterest').and.callFake(
      (surveyId: string, jobId: string) => {
        const emptyQuery: any = {
          get: async () => ({ empty: true, docs: [] }),
          startAfter: () => emptyQuery,
        };
        return {
          get: () =>
            mockFirestore
              .collection(`surveys/${surveyId}/lois`)
              .where(l.jobId, '==', jobId)
              .get(),
          startAfter: () => emptyQuery,
        } as any;
      }
    );
    spyOn(getDatastore(), 'fetchLoisSubmissions').and.callFake(
      async (surveyId: string, jobId: string, ownerId: string | null) =>
        fetchLoisSubmissionsFromMock(mockFirestore, surveyId, jobId, ownerId)
    );
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
        mockFirestore.doc(`surveys/${surveyId}`).set(survey);
        jobs?.forEach(({ id, ...job }) =>
          mockFirestore.doc(`surveys/${surveyId}/jobs/${id}`).set(job)
        );
        lois?.forEach(({ id, ...loi }) =>
          mockFirestore.doc(`surveys/${surveyId}/lois/${id}`).set(loi)
        );
        submissions?.forEach(({ id, ...submission }) =>
          mockFirestore
            .doc(`surveys/${surveyId}/submissions/${id}`)
            .set(submission)
        );

        // Build mock request and response.
        const req = await createGetRequestSpy({
          url: '/exportCsv',
          headers: { host, 'x-forwarded-proto': 'https' },
          query: {
            survey: surveyId,
            job: jobId,
          },
        });
        const res = createResponseSpy();

        // Run export CSV handler.
        await exportCsvHandler(req, res, { email } as DecodedIdToken);

        // Check post-conditions.
        expect(res.redirect as jasmine.Spy).toHaveBeenCalledTimes(1);
        const redirectUrl: string = (
          res.redirect as jasmine.Spy
        ).calls.mostRecent().args[0];
        expect(redirectUrl).toContain(FIREBASE_DOWNLOAD_URL_PREFIX);
        expect(mockFile.createWriteStream).toHaveBeenCalledWith(
          jasmine.objectContaining({
            metadata: jasmine.objectContaining({
              contentDisposition: `attachment; filename=${expectedFilename}`,
            }),
          })
        );
        const output = storageChunks.join('').trim();
        const lines = output.split('\n');
        expect(lines).toEqual(expectedCsv);
      })
  );
});
