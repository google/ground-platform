/**
 * Copyright 2020 Google LLC
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
import firebase from 'firebase/app';
import { FirebaseDataConverter } from '../../shared/converters/firebase-data-converter';
import { Task } from '../models/task/task.model';
import { Map, List } from 'immutable';
import { Option } from '../models/task/option.model';
import { Step, StepType } from '../models/task/step.model';
import {
  MultipleChoice,
  Cardinality,
} from '../models/task/multiple-choice.model';

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
    taskId: 'task001',
    results: {
      step001: 'text result',
      step002: ['option001', 'option002'],
      step003: 123,
      step004: new firebase.firestore.Timestamp(1641533340, 0),
      step005: new firebase.firestore.Timestamp(1641534444, 0),
    },
  };
}

class MockModel {
  static step001: Step = new Step(
    'step001',
    StepType.TEXT,
    'Text Field',
    /*required=*/ true,
    0
  );

  static step002: Step = new Step(
    'step002',
    StepType.MULTIPLE_CHOICE,
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

  static step003: Step = new Step(
    'step003',
    StepType.NUMBER,
    'How many sloths are there?',
    /*required=*/ true,
    2
  );

  static step004: Step = new Step(
    'step004',
    StepType.DATE,
    'What is the current date?',
    /*required=*/ true,
    2
  );

  static step005: Step = new Step(
    'step005',
    StepType.TIME,
    'What time is it?',
    /*required=*/ true,
    2
  );

  static task001: Task = new Task(
    'task001',
    Map({
      step001: MockModel.step001,
      step002: MockModel.step002,
      step003: MockModel.step003,
      step004: MockModel.step004,
      step005: MockModel.step005,
    })
  );
}

describe('FirebaseDataConverter', () => {
  it('Submission converts back and forth without loosing data.', () => {
    expect(
      FirebaseDataConverter.submissionToJS(
        FirebaseDataConverter.toSubmission(
          MockModel.task001,
          'submission001',
          MockFirebaseData.submission001
        )
      )
    ).toEqual(MockFirebaseData.submission001);
  });
});
