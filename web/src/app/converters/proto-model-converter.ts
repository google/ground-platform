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

import {toDocumentData} from '@ground/lib';
import {GroundProtos} from '@ground/proto';
import {Role} from 'app/models/role.model';
import {Map} from 'immutable';

const Pb = GroundProtos.google.ground.v1beta1;

const PB_ROLES = Map([
  [Role.OWNER, Pb.Role.SURVEY_ORGANIZER],
  [Role.SURVEY_ORGANIZER, Pb.Role.SURVEY_ORGANIZER],
  [Role.DATA_COLLECTOR, Pb.Role.DATA_COLLECTOR],
  [Role.VIEWER, Pb.Role.VIEWER],
]);

/**
 * Converts Role instance to its proto message type.
 */
export function roleToProto(role: Role) {
  return PB_ROLES.get(role) || Pb.Role.VIEWER;
}

/**
 * Creates a proto rapresentation of a Survey.
 */
export function newSurveyToProto(
  name: string,
  description: string,
  acl: Map<string, Role>,
  ownerId: string
): {} {
  return toDocumentData(
    new Pb.Survey({
      name,
      description,
      acl: acl.map(role => roleToProto(role)).toObject(),
      ownerId,
    })
  );
}

/**
 * Creates a proto rapresentation of a Survey.
 */
export function partialSurveyToProto(name: string, description?: string): {} {
  return toDocumentData(
    new Pb.Survey({
      name,
      ...(description && {description}),
    })
  );
}
