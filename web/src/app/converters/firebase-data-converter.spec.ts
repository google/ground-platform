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

import {Role} from 'app/models/role.model';

import {FirebaseDataConverter} from './firebase-data-converter';

describe('FirebaseDataConverter', () => {
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
