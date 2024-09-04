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

import {Role} from 'app/models/role.model';

import {MODEL_ROLES} from './survey-data-converter';

import Pb = GroundProtos.ground.v1beta1;

describe('surveyDocToModel', () => {
  describe('MODEL_ROLES', () => {
    it('converts strings to enums', () => {
      expect(MODEL_ROLES.get(Pb.Role.SURVEY_ORGANIZER)).toEqual(
        Role.SURVEY_ORGANIZER
      );
      expect(MODEL_ROLES.get(Pb.Role.DATA_COLLECTOR)).toEqual(
        Role.DATA_COLLECTOR
      );
      expect(MODEL_ROLES.get(Pb.Role.VIEWER)).toEqual(Role.VIEWER);
    });
  });
});
