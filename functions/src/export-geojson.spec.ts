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
import {exportGeojsonHandler} from './export-geojson';
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
const mq = registry.getFieldIds(Pb.Task.MultipleChoiceQuestion);
const op = registry.getFieldIds(Pb.Task.MultipleChoiceQuestion.Option);

describe('export()', () => {
  let mockFirestore: Firestore;
  const email = 'somebody@test.it';
  const survey = {
    [sv.name]: 'Test survey',
    [sv.acl]: {
      [email]: SURVEY_ORGANIZER_ROLE,
    },
  };
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
      [g.point]: {[p.coordinates]: {[c.latitude]: 10.1, [c.longitude]: 125.6}},
    },
    [l.submissionCount]: 0,
    [l.source]: Pb.LocationOfInterest.Source.IMPORTED,
    [l.properties]: {
      name: {[pr.stringValue]: 'Dinagat Islands'},
      area: {[pr.numericValue]: 3.08},
    },
  };
  const testCases = [
    {
      desc: '[GEOJSON] - export point',
      jobId: job1.id,
      survey: survey,
      jobs: [job1],
      lois: [pointLoi1],
      expectedFilename: 'test-job.geojson',
      expectedGeojson: {
        type: 'FeatureCollection',
        features: [
          {
            type: 'Feature',
            properties: {name: 'Dinagat Islands', area: 3.08},
            geometry: {type: 'Point', coordinates: [125.6, 10.1]},
          },
          {
            type: 'Feature',
            properties: null,
            geometry: {type: 'Point', coordinates: [8.3, 47.05]},
          },
        ],
      },
    },
  ];

  beforeEach(() => {
    mockFirestore = createMockFirestore();
    stubAdminApi(mockFirestore);
  });

  afterEach(() => {
    resetDatastore();
  });

  testCases.forEach(({desc, jobId, survey, jobs, lois, expectedFilename}) =>
    it(desc, async () => {
      // Populate database.
      mockFirestore.doc(`surveys/${survey.id}`).set(survey);
      jobs?.forEach(({id, ...job}) =>
        mockFirestore.doc(`surveys/${survey.id}/jobs/${id}`).set(job)
      );
      lois?.forEach(({id, ...loi}) =>
        mockFirestore.doc(`surveys/${survey.id}/lois/${id}`).set(loi)
      );

      // Build mock request and response.
      const req = await createGetRequestSpy({
        url: '/exportGeojson',
        query: {
          survey: survey.id,
          job: jobId,
        },
      });
      const chunks: string[] = [];
      const res = createResponseSpy(chunks);

      // Run export handler.
      await exportGeojsonHandler(req, res, {email} as DecodedIdToken);

      // Check post-conditions.
      expect(res.status).toHaveBeenCalledOnceWith(HttpStatus.OK);
      expect(res.type).toHaveBeenCalledOnceWith('application/json');
      expect(res.setHeader).toHaveBeenCalledOnceWith(
        'Content-Disposition',
        `attachment; filename=${expectedFilename}`
      );
    })
  );
});
