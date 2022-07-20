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
import { StringMap } from '../models/string-map.model';
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
    responses: {
      element001: 'text response',
      element002: ['option001', 'option002'],
      element003: 123,
      element004: new firebase.firestore.Timestamp(1641533340, 0),
      element005: new firebase.firestore.Timestamp(1641534444, 0),
    },
  };
}

class MockModel {
  static element001: Step = new Step(
    'element001',
    StepType.TEXT,
    StringMap({ en: 'Text Field' }),
    /*required=*/ true,
    0
  );

  static element002: Step = new Step(
    'element002',
    StepType.MULTIPLE_CHOICE,
    StringMap({ en: 'Multiple Select' }),
    /*required=*/ true,
    1,
    new MultipleChoice(
      Cardinality.SELECT_MULTIPLE,
      List([
        new Option(
          'option001',
          'code001',
          StringMap({ en: 'option 1' }),
          /* index= */
          0
        ),
        new Option(
          'option002',
          'code002',
          StringMap({ en: 'option 2' }),
          /* index= */
          0
        ),
      ])
    )
  );

  static element003: Step = new Step(
    'element003',
    StepType.NUMBER,
    StringMap({ en: 'How many sloths are there?' }),
    /*required=*/ true,
    2
  );

  static element004: Step = new Step(
    'element004',
    StepType.DATE,
    StringMap({ en: 'What is the current date?' }),
    /*required=*/ true,
    2
  );

  static element005: Step = new Step(
    'element005',
    StepType.TIME,
    StringMap({ en: 'What time is it?' }),
    /*required=*/ true,
    2
  );

  static task001: Task = new Task(
    'task001',
    Map({
      element001: MockModel.element001,
      element002: MockModel.element002,
      element003: MockModel.element003,
      element004: MockModel.element004,
      element005: MockModel.element005,
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
