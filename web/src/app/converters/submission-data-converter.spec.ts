/**
 * Copyright 2024 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {Timestamp} from '@angular/fire/firestore';
import {Map} from 'immutable';

import {Job} from 'app/models/job.model';
import {Submission} from 'app/models/submission/submission.model';

import {FirebaseDataConverter} from './firebase-data-converter';
import {LegacySubmissionDataConverter} from './submission-data-converter';

const job001: Job = new Job(
  /* id= */ 'job001',
  /* index= */ 0,
  '#ffffff',
  'Test job',
  Map({})
);

const submission001 = {
  created: {
    clientTimestamp: undefined,
    serverTimestamp: undefined,
    user: {
      displayName: 'Creator',
      email: 'creator@test.com',
      id: 'creator001',
    },
  },
  lastModified: {
    clientTimestamp: undefined,
    serverTimestamp: undefined,
    user: {
      displayName: 'Modifier',
      email: 'modifier@test.com',
      id: 'modifier001',
    },
  },
  loiId: 'loi001',
  jobId: 'job001',
  data: {
    task001: 'text result',
    task002: ['option001', 'option002'],
    task003: 123,
    task004: new Timestamp(1641533340, 0),
    task005: new Timestamp(1641534444, 0),
  },
};

describe('toSubmission', () => {
  it('Submission converts back and forth without losing data', () => {
    expect(
      FirebaseDataConverter.submissionToJS(
        LegacySubmissionDataConverter.toSubmission(
          job001,
          'submission001',
          submission001
        ) as Submission
      )
    ).toEqual(submission001);
  });
});
