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

import {GroundProtos} from '@ground/proto';
import {toDocumentData} from './proto-to-firestore';

const {Job, Role, Style, Survey, Task, LinearRing, Coordinates} =
  GroundProtos.google.ground.v1beta1;

describe('toDocumentData()', () => {
  [
    {
      desc: 'converts string fields',
      input: new Survey({
        name: 'Survey name',
        description: 'Survey desc',
      }),
      expected: {
        '2': 'Survey name',
        '3': 'Survey desc',
      },
    },
    {
      desc: 'converts repeated message',
      input: new LinearRing({
        coordinates: [
          new Coordinates({latitude: 5, longitude: 7}),
          new Coordinates({latitude: 12, longitude: 23}),
          new Coordinates({latitude: 9, longitude: 2}),
        ],
      }),
      expected: {
        '1': [
          {'1': 5, '2': 7},
          {'1': 12, '2': 23},
          {'1': 9, '2': 2},
        ],
      },
    },
    {
      desc: 'converts nested message',
      input: new Job({
        style: new Style({color: '#112233'}),
      }),
      expected: {
        '4': {'1': '#112233'},
      },
    },
    {
      desc: 'converts map<string, enum>',
      input: new Survey({
        acl: {
          email1: Role.DATA_COLLECTOR,
          email2: Role.SURVEY_ORGANIZER,
        },
      }),
      expected: {
        '4': {
          email1: 2,
          email2: 3,
        },
      },
    },
    {
      desc: 'converts enum value',
      input: new Task.DateTimeQuestion({
        type: Task.DateTimeQuestion.Type.BOTH_DATE_AND_TIME,
      }),
      expected: {
        '1': 3,
      },
    },
    {
      desc: 'skips unset (0) enum value',
      input: new Task.DateTimeQuestion({
        type: Task.DateTimeQuestion.Type.TYPE_UNSPECIFIED,
      }),
      expected: {},
    },
    {
      desc: 'converts repeated message',
      input: new LinearRing({
        coordinates: [
          new Coordinates({latitude: 5, longitude: 7}),
          new Coordinates({latitude: 12, longitude: 23}),
          new Coordinates({latitude: 9, longitude: 2}),
        ],
      }),
      expected: {
        "1": [
          {"1": 5, "2": 7},
          {"1": 12, "2": 23},
          {"1": 9, "2": 2}
        ],
      },
    },
  ].forEach(({desc, input, expected}) =>
    it(desc, () => {
      const output = toDocumentData(input);
      expect(output).toEqual(expected);
    })
  );
});
