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

import { Timestamp } from '@angular/fire/firestore';
import { registry } from '@ground/lib';
import { GroundProtos } from '@ground/proto';
import { List, Map } from 'immutable';

import { Job } from 'app/models/job.model';
import { Submission } from 'app/models/submission/submission.model';
import {
  Cardinality,
  MultipleChoice,
} from 'app/models/task/multiple-choice.model';
import { Option } from 'app/models/task/option.model';
import { Task, TaskType } from 'app/models/task/task.model';

import { FirebaseDataConverter } from './firebase-data-converter';
import { submissionDocToModel } from './submission-data-converter';

import Pb = GroundProtos.ground.v1beta1;

const sb = registry.getFieldIds(Pb.Submission);
const ai = registry.getFieldIds(Pb.AuditInfo);
const td = registry.getFieldIds(Pb.TaskData);
const td_tr = registry.getFieldIds(Pb.TaskData.TextResponse);
const td_nr = registry.getFieldIds(Pb.TaskData.NumberResponse);
const td_dr = registry.getFieldIds(Pb.TaskData.DateTimeResponse);
const td_mcr = registry.getFieldIds(Pb.TaskData.MultipleChoiceResponses);

const job001: Job = new Job(
  /* id= */ 'job001',
  /* index= */ 0,
  '#ffffff',
  'Test job',
  Map([
    ['task001', new Task('task001', TaskType.TEXT, '', true, 1)],
    ['task002', new Task('task002', TaskType.NUMBER, '', true, 2)],
    ['task003', new Task('task003', TaskType.DATE, '', true, 3)],
    ['task004', new Task('task004', TaskType.DATE, '', true, 4)],
    [
      'task005',
      new Task(
        'task005',
        TaskType.MULTIPLE_CHOICE,
        '',
        false,
        5,
        new MultipleChoice(
          Cardinality.SELECT_MULTIPLE,
          List([
            new Option('optionId001', '', '', 1),
            new Option('optionId002', '', '', 2),
          ]),
          true
        )
      ),
    ],
  ])
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
    {
      [td.taskId]: 'task001',
      [td.textResponse]: { [td_tr.text]: 'text result' },
    },
    { [td.taskId]: 'task002', [td.numberResponse]: { [td_nr.number]: 123 } },
    {
      [td.taskId]: 'task003',
      [td.dateTimeResponse]: {
        [td_dr.dateTime]: { 1: 1641533340 },
      },
    },
    {
      [td.taskId]: 'task004',
      [td.dateTimeResponse]: {
        [td_dr.dateTime]: { 1: 1641534444 },
      },
    },
    {
      [td.taskId]: 'task005',
      [td.multipleChoiceResponses]: {
        [td_mcr.selectedOptionIds]: ['optionId001', 'optionId002'],
      },
    },
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
    task002: 123,
    task003: new Timestamp(1641533340, 0),
    task004: new Timestamp(1641534444, 0),
    task005: ['optionId001', 'optionId002'],
  },
};

describe('submissionDocToModel', () => {
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
