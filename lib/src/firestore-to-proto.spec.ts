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
import {toMessage} from './firestore-to-proto';
import {Constructor} from 'protobufjs';

const {Coordinates, Job, LinearRing, Role, Style, Survey, Task, LocationOfInterest} =
  GroundProtos.google.ground.v1beta1;

describe('toMessage()', () => {
  [
    {
      desc: 'converts string fields',
      input: {
        '2': 'Survey name',
        '3': 'Survey desc',
      },
      expected: new Survey({
        name: 'Survey name',
        description: 'Survey desc',
      }),
    },
    {
      desc: 'converts nested message',
      input: {
        '4': {'1': '#112233'},
      },
      expected: new Job({
        style: new Style({color: '#112233'}),
      }),
    },
    {
      desc: 'converts repeated message',
      input: {
        '1': [
          {'1': 5, '2': 7},
          {'1': 12, '2': 23},
          {'1': 9, '2': 2},
        ],
      },
      expected: new LinearRing({
        coordinates: [
          new Coordinates({latitude: 5, longitude: 7}),
          new Coordinates({latitude: 12, longitude: 23}),
          new Coordinates({latitude: 9, longitude: 2}),
        ],
      }),
    },
    {
      desc: 'converts map<string, enum>',
      input: {
        '4': {
          email1: 2,
          email2: 3,
        },
      },
      expected: new Survey({
        acl: {
          email1: Role.DATA_COLLECTOR,
          email2: Role.SURVEY_ORGANIZER,
        },
      }),
    },
    {
      desc: 'converts map<string, Message>',
      input: {
        '10': {
          'stringProperty': {'1': 'aaa'},
          'numberProperty': {'2': 123.4},
        },
      },
      expected: new LocationOfInterest({
        properties: {
          'stringProperty': new LocationOfInterest.Property({stringValue: 'aaa'}),
          'numberProperty': new LocationOfInterest.Property({numericValue: 123.4}),
        },
      }),
    },
    {
      desc: 'converts enum value',
      input: {
        '1': 3,
      },
      expected: new Task.DateTimeQuestion({
        type: Task.DateTimeQuestion.Type.BOTH_DATE_AND_TIME,
      }),
    },
    {
      desc: 'skips unset (0) enum value',
      input: {},
      expected: new Task.DateTimeQuestion(),
    },
    {
      desc: 'converts repeated message',
      input: {
        '1': [
          {'1': 5, '2': 7},
          {'1': 12, '2': 23},
          {'1': 9, '2': 2},
        ],
      },
      expected: new LinearRing({
        coordinates: [
          new Coordinates({latitude: 5, longitude: 7}),
          new Coordinates({latitude: 12, longitude: 23}),
          new Coordinates({latitude: 9, longitude: 2}),
        ],
      }),
    },
  ].forEach(({desc, input, expected}) =>
    it(desc, () => {
      const output = toMessage(input, expected.constructor as Constructor<any>);
      expect(output).toEqual(expected);
    })
  );
});
