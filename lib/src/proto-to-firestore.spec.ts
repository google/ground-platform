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

import {registry} from './message-registry';
import {GroundProtos} from '@ground/proto';
import {toDocumentData} from './proto-to-firestore';

import Pb = GroundProtos.ground.v1beta1;
const s = registry.getFieldIds(Pb.Survey);
const j = registry.getFieldIds(Pb.Job);
const c = registry.getFieldIds(Pb.Coordinates);
const lr = registry.getFieldIds(Pb.LinearRing);
const t = registry.getFieldIds(Pb.Task);
const st = registry.getFieldIds(Pb.Style);
const dtq = registry.getFieldIds(Pb.Task.DateTimeQuestion);

const {Job, Role, Style, Survey, Task, LinearRing, Coordinates} =
  GroundProtos.ground.v1beta1;

describe('toDocumentData()', () => {
  [
    {
      desc: 'converts string fields',
      input: new Survey({
        name: 'Survey name',
        description: 'Survey desc',
      }),
      expected: {
        [s.name]: 'Survey name',
        [s.description]: 'Survey desc',
        [s.state]: Survey.State.STATE_UNSPECIFIED,
        [s.generalAccess]: Survey.GeneralAccess.GENERAL_ACCESS_UNSPECIFIED,
        [s.dataVisibility]: Survey.DataVisibility.DATA_VISIBILITY_UNSPECIFIED
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
        [lr.coordinates]: [
          {[c.latitude]: 5, [c.longitude]: 7},
          {[c.latitude]: 12, [c.longitude]: 23},
          {[c.latitude]: 9, [c.longitude]: 2},
        ],
      },
    },
    {
      desc: 'converts nested message',
      input: new Job({
        style: new Style({color: '#112233'}),
      }),
      expected: {
        [j.index]: 0,
        [j.style]: {[st.color]: '#112233'},
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
        [s.acl]: {
          email1: 2, // DATA_COLLECTOR
          email2: 3, // SURVEY_ORGANIZER
        },
        [s.state]: Survey.State.STATE_UNSPECIFIED,
        [s.generalAccess]: Survey.GeneralAccess.GENERAL_ACCESS_UNSPECIFIED,
        [s.dataVisibility]: Survey.DataVisibility.DATA_VISIBILITY_UNSPECIFIED
      },
    },
    {
      desc: 'converts enum value',
      input: new Task.DateTimeQuestion({
        type: Task.DateTimeQuestion.Type.BOTH_DATE_AND_TIME,
      }),
      expected: {
        [dtq.type]: 3,
      },
    },
    {
      desc: 'sets default enum value',
      input: new Task.DateTimeQuestion({
        type: Task.DateTimeQuestion.Type.TYPE_UNSPECIFIED,
      }),
      expected: {
        [dtq.type]: 0, // UNSPECIFIED
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
        [lr.coordinates]: [
          {[c.latitude]: 5, [c.longitude]: 7},
          {[c.latitude]: 12, [c.longitude]: 23},
          {[c.latitude]: 9, [c.longitude]: 2},
        ],
      },
    },
    {
      desc: 'converts oneof',
      input: new Task({
        textQuestion: new Task.TextQuestion({
          type: Task.TextQuestion.Type.SHORT_TEXT,
        }),
      }),
      expected: {
        [t.index]: 0,
        [t.required]: false,
        [t.level]: 0,
        [t.textQuestion]: {'1': 1},
      },
    },
  ].forEach(({desc, input, expected}) =>
    it(desc, () => {
      const output = toDocumentData(input);
      expect(output).toEqual(expected);
    })
  );
});
