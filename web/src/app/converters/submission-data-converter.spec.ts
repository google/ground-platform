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

import {registry} from '@ground/lib';
import {GroundProtos} from '@ground/proto';
import {Map} from 'immutable';

import {Job} from 'app/models/job.model';
import {Submission} from 'app/models/submission/submission.model';
import {Task, TaskType} from 'app/models/task/task.model';

import {FirebaseDataConverter} from './firebase-data-converter';
import {submissionDocToModel} from './submission-data-converter';

import Pb = GroundProtos.ground.v1beta1;

const sb = registry.getFieldIds(Pb.Submission);
const ai = registry.getFieldIds(Pb.AuditInfo);
const td = registry.getFieldIds(Pb.TaskData);
const td_tr = registry.getFieldIds(Pb.TaskData.TextResponse);

const job001: Job = new Job(
  /* id= */ 'job001',
  /* index= */ 0,
  '#ffffff',
  'Test job',
  Map([['task001', new Task('task001', TaskType.TEXT, 'text', true, 1)]])
);

const submissionDoc001 = {
  [sb.created]: {
    [ai.userId]: 'creator001',
    [ai.clientTimestamp]: undefined,
    [ai.serverTimestamp]: undefined,
    [ai.displayName]: 'Creator',
    [ai.emailAddress]: 'creator@test.com',
  },
  [sb.lastModified]: {
    [ai.userId]: 'modifier001',
    [ai.clientTimestamp]: undefined,
    [ai.serverTimestamp]: undefined,
    [ai.displayName]: 'Modifier',
    [ai.emailAddress]: 'modifier@test.com',
  },
  [sb.loiId]: 'loi001',
  [sb.jobId]: 'job001',
  [sb.taskData]: [
    {[td.taskId]: 'task001', [td.textResponse]: {[td_tr.text]: 'text result'}},
    // {[td.taskId]: 'task002', [td.numberResponse]: 123},
    // {[td.taskId]: 'task003', 1: ['option001', 'option002']},
    // {[td.taskId]: 'task004', 1: new Timestamp(1641533340, 0)},
    // {[td.taskId]: 'task005', 1: new Timestamp(1641534444, 0)},
  ],
};

const submission001 = {
  created: {
    clientTimestamp: new Date(0),
    serverTimestamp: new Date(0),
    user: {
      displayName: 'Creator',
      email: 'creator@test.com',
      id: 'creator001',
    },
  },
  lastModified: {
    clientTimestamp: new Date(0),
    serverTimestamp: new Date(0),
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
    // task002: 123,
    // task003: ['option001', 'option002'],
    // task004: new Timestamp(1641533340, 0),
    // task005: new Timestamp(1641534444, 0),
  },
};

describe('toSubmission', () => {
  it('Submission converts back and forth without losing data', () => {
    const submission001JS = FirebaseDataConverter.submissionToJS(
      submissionDocToModel(
        job001,
        'submission001',
        submissionDoc001
      ) as Submission
    );

    expect(submission001JS).toEqual(submission001);
  });
});
