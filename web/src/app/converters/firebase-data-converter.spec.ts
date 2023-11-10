/**
 * Copyright 2020 The Ground Authors.
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

import {Timestamp} from 'firebase/firestore';
import {List, Map} from 'immutable';

import {Job} from 'app/models/job.model';
import {Role} from 'app/models/role.model';
import {
  Cardinality,
  MultipleChoice,
} from 'app/models/task/multiple-choice.model';
import {Option} from 'app/models/task/option.model';
import {Task, TaskType} from 'app/models/task/task.model';

import {FirebaseDataConverter} from './firebase-data-converter';

class MockFirebaseData {
  static submission001 = {
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
}

class MockModel {
  static task001: Task = new Task(
    'task001',
    TaskType.TEXT,
    'Text Field',
    /*required=*/ true,
    0
  );

  static task002: Task = new Task(
    'task002',
    TaskType.MULTIPLE_CHOICE,
    'Multiple Select',
    /*required=*/ true,
    1,
    new MultipleChoice(
      Cardinality.SELECT_MULTIPLE,
      List([
        new Option(
          'option001',
          'code001',
          'option 1',
          /* index= */
          0
        ),
        new Option(
          'option002',
          'code002',
          'option 2',
          /* index= */
          0
        ),
      ])
    )
  );

  static task003: Task = new Task(
    'task003',
    TaskType.NUMBER,
    'How many sloths are there?',
    /*required=*/ true,
    2
  );

  static task004: Task = new Task(
    'task004',
    TaskType.DATE,
    'What is the current date?',
    /*required=*/ true,
    2
  );

  static task005: Task = new Task(
    'task005',
    TaskType.TIME,
    'What time is it?',
    /*required=*/ true,
    2
  );

  static job001: Job = new Job(
    /* id= */ 'job001',
    /* index= */ 0,
    '#ffffff',
    'Test job',
    Map({
      task001: MockModel.task001,
      task002: MockModel.task002,
      task003: MockModel.task003,
      task004: MockModel.task004,
    })
  );
}

describe('FirebaseDataConverter', () => {
  it('Submission converts back and forth without loosing data.', () => {
    expect(
      FirebaseDataConverter.submissionToJS(
        FirebaseDataConverter.toSubmission(
          MockModel.job001,
          'submission001',
          MockFirebaseData.submission001
        )
      )
    ).toEqual(MockFirebaseData.submission001);
  });

  describe('toRole()', () => {
    it('converts enums to strings', () => {
      expect(FirebaseDataConverter.toRoleId(Role.OWNER)).toEqual('OWNER');
      expect(FirebaseDataConverter.toRoleId(Role.SURVEY_ORGANIZER)).toEqual(
        'SURVEY_ORGANIZER'
      );
      expect(FirebaseDataConverter.toRoleId(Role.DATA_COLLECTOR)).toEqual(
        'DATA_COLLECTOR'
      );
      expect(FirebaseDataConverter.toRoleId(Role.VIEWER)).toEqual('VIEWER');
    });

    it('converts strings to enums', () => {
      expect(FirebaseDataConverter.toRole('OWNER')).toEqual(Role.OWNER);
      expect(FirebaseDataConverter.toRole('SURVEY_ORGANIZER')).toEqual(
        Role.SURVEY_ORGANIZER
      );
      expect(FirebaseDataConverter.toRole('DATA_COLLECTOR')).toEqual(
        Role.DATA_COLLECTOR
      );
      expect(FirebaseDataConverter.toRole('OWNVIEWERER')).toEqual(Role.VIEWER);
    });

    it('returns VIEWER on unrecognized role', () => {
      expect(
        FirebaseDataConverter.toRole('some_rule_i_dont_recognize')
      ).toEqual(Role.VIEWER);
    });
  });
});
