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

import { GroundProtos } from '@ground/proto';
import { toMessage } from './firestore-to-proto';
import { Constructor } from 'protobufjs';

const {
  AuditInfo,
  Coordinates,
  Job,
  LinearRing,
  Role,
  Style,
  Survey,
  Task,
  LocationOfInterest,
} = GroundProtos.ground.v1beta1;
const { Timestamp } = GroundProtos.google.protobuf;

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
        '4': { '1': '#112233' },
      },
      expected: new Job({
        style: new Style({ color: '#112233' }),
      }),
    },
    {
      desc: 'converts repeated message',
      input: {
        '1': [
          { '1': 5, '2': 7 },
          { '1': 12, '2': 23 },
          { '1': 9, '2': 2 },
        ],
      },
      expected: new LinearRing({
        coordinates: [
          new Coordinates({ latitude: 5, longitude: 7 }),
          new Coordinates({ latitude: 12, longitude: 23 }),
          new Coordinates({ latitude: 9, longitude: 2 }),
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
          stringProperty: { '1': 'non-empty string' },
          emptyStringProperty: { '1': '' },
          numberProperty: { '2': 123.4 },
          valueZeroProperty: { '2': 0 },
        },
      },
      expected: new LocationOfInterest({
        properties: {
          stringProperty: new LocationOfInterest.Property({
            stringValue: 'non-empty string',
          }),
          emptyStringProperty: new LocationOfInterest.Property({
            stringValue: '',
          }),
          numberProperty: new LocationOfInterest.Property({
            numericValue: 123.4,
          }),
          valueZeroProperty: new LocationOfInterest.Property({
            numericValue: 0,
          }),
        },
      }),
    },
    {
      desc: 'skips null nested message',
      input: {
        '4': null,
      },
      expected: new Job(),
    },
    {
      desc: 'skips null map field',
      input: {
        '4': null,
      },
      expected: new Survey(),
    },
    {
      desc: 'skips non-object map field',
      input: {
        '4': 'not an object',
      },
      expected: new Survey(),
    },
    {
      desc: 'skips array map field',
      input: {
        '4': [1, 2, 3],
      },
      expected: new Survey(),
    },
    {
      desc: 'skips repeated field when value is not an array',
      input: {
        '1': 'not an array',
      },
      expected: new LinearRing(),
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
      desc: 'skips non-numeric enum value',
      input: {
        '1': 'not-a-number',
      },
      expected: new Task.DateTimeQuestion(),
    },
    {
      desc: 'converts google.protobuf.Timestamp field',
      input: {
        '1': 'user-123',
        '2': { '1': 1700000000, '2': 500 },
      },
      expected: new AuditInfo({
        userId: 'user-123',
        clientTimestamp: new Timestamp({ seconds: 1700000000, nanos: 500 }),
      }),
    },
    {
      desc: 'skips non-numeric keys',
      input: {
        '2': 'Survey name',
        notANumber: 'should be ignored',
        foo: 42,
      },
      expected: new Survey({
        name: 'Survey name',
      }),
    },
    {
      desc: 'skips unrecognized field numbers',
      input: {
        '2': 'Survey name',
        '999': 'unknown field should be ignored',
      },
      expected: new Survey({
        name: 'Survey name',
      }),
    },
    {
      desc: 'converts repeated message',
      input: {
        '1': [
          { '1': 5, '2': 7 },
          { '1': 12, '2': 23 },
          { '1': 9, '2': 2 },
        ],
      },
      expected: new LinearRing({
        coordinates: [
          new Coordinates({ latitude: 5, longitude: 7 }),
          new Coordinates({ latitude: 12, longitude: 23 }),
          new Coordinates({ latitude: 9, longitude: 2 }),
        ],
      }),
    },
  ].forEach(({ desc, input, expected }) =>
    it(desc, () => {
      const output = toMessage(
        input,
        expected.constructor as Constructor<unknown>
      );
      expect(output).toEqual(expected);
    })
  );

  it('logs and skips fields whose conversion returns an Error', () => {
    const debugSpy = spyOn(console, 'debug');
    const output = toMessage(
      {
        '2': 'Survey name',
        '4': 'not an object', // acl map expects object → Error
      },
      Survey
    );
    expect(output).toEqual(new Survey({ name: 'Survey name' }));
    expect(debugSpy).toHaveBeenCalledWith(jasmine.any(Error));
  });

  it('returns Error when constructor is not a protojs message', () => {
    class NotAProtoMessage {}
    const output = toMessage({}, NotAProtoMessage as Constructor<unknown>);
    expect(output).toEqual(jasmine.any(Error));
    expect((output as Error).message).toContain('is not a protojs message');
  });

  it('returns Error when message type not found in registry', () => {
    class UnknownProtoMessage {
      static getTypeUrl(): string {
        return '/ground.v1beta1.DoesNotExist';
      }
    }
    const output = toMessage({}, UnknownProtoMessage as Constructor<unknown>);
    expect(output).toEqual(jasmine.any(Error));
    expect((output as Error).message).toContain('not found in registry');
  });
});
