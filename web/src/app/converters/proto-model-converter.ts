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

import {DocumentData} from '@angular/fire/firestore';
import {toDocumentData} from '@ground/lib';
import {GroundProtos} from '@ground/proto';
import {Map} from 'immutable';

import {Job} from 'app/models/job.model';
import {Role} from 'app/models/role.model';
import {Cardinality} from 'app/models/task/multiple-choice.model';
import {TaskType} from 'app/models/task/task.model';

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
  const pbRole = PB_ROLES.get(role);

  if (!pbRole) throw new Error(`Invalid role encountered: ${role}`);

  return pbRole;
}

/**
 * Creates a proto rapresentation of a Survey.
 */
export function newSurveyToProto(
  name: string,
  description: string,
  acl: Map<string, Role>,
  ownerId: string
): DocumentData | Error {
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
export function partialSurveyToProto(
  name: string,
  description?: string
): DocumentData | Error {
  return toDocumentData(
    new Pb.Survey({
      name,
      ...(description && {description}),
    })
  );
}

/**
 * Creates a proto rapresentation of a Job.
 */
export function jobToProto(job: Job): DocumentData {
  return toDocumentData(
    new Pb.Job({
      id: job.id,
      index: job.index,
      name: job.name,
      style: new Pb.Style({color: job.color}),
      tasks: job.tasks
        ?.map(task => {
          let pbTask = {};

          switch (task.type) {
            case TaskType.NUMBER:
              pbTask = {
                numberQuestion: new Pb.Task.NumberQuestion(),
              };
              break;
            case TaskType.CAPTURE_LOCATION:
              pbTask = {
                captureLocation: new Pb.Task.CaptureLocation(),
              };
              break;
            case TaskType.MULTIPLE_CHOICE:
              pbTask = {
                multipleChoiceQuestion: new Pb.Task.MultipleChoiceQuestion({
                  type:
                    task.multipleChoice!.cardinality === Cardinality.SELECT_ONE
                      ? Pb.Task.MultipleChoiceQuestion.Type.SELECT_ONE
                      : Pb.Task.MultipleChoiceQuestion.Type.SELECT_MULTIPLE,
                  hasOtherOption: task.multipleChoice!.hasOtherOption,
                  options: task
                    .multipleChoice!.options.map(
                      option =>
                        new Pb.Task.MultipleChoiceQuestion.Option({
                          id: option.id,
                          index: option.index,
                          label: option.label,
                        })
                    )
                    .toArray(),
                }),
              };
              break;
          }

          return new Pb.Task({
            ...pbTask,
            id: task.id,
            index: task.index,
            prompt: task.label,
            required: task.required,
            level: task.addLoiTask
              ? Pb.Task.DataCollectionLevel.LOI_DATA
              : Pb.Task.DataCollectionLevel.LOI_METADATA,
            conditions: [],
          });
        })
        .toList()
        .toArray(),
    })
  );
}
