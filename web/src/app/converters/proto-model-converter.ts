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
import {Task, TaskType} from 'app/models/task/task.model';

import Pb = GroundProtos.google.ground.v1beta1;

const PB_ROLES = Map([
  [Role.OWNER, Pb.Role.SURVEY_ORGANIZER],
  [Role.SURVEY_ORGANIZER, Pb.Role.SURVEY_ORGANIZER],
  [Role.DATA_COLLECTOR, Pb.Role.DATA_COLLECTOR],
  [Role.VIEWER, Pb.Role.VIEWER],
]);

/**
 * Converts Role instance to its proto message type.
 */
export function roleToProtoRole(role: Role) {
  const pbRole = PB_ROLES.get(role);

  if (!pbRole) throw new Error(`Invalid role encountered: ${role}`);

  return pbRole;
}

/**
 * Creates a proto rapresentation of a Survey.
 */
export function newSurveyToDocument(
  name: string,
  description: string,
  acl: Map<string, Role>,
  ownerId: string
): DocumentData | Error {
  return toDocumentData(
    new Pb.Survey({
      name,
      description,
      acl: acl.map(role => roleToProtoRole(role)).toObject(),
      ownerId,
    })
  );
}

/**
 * Creates a proto rapresentation of a Survey.
 */
export function partialSurveyToDocument(
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
 * Creates a proto rapresentation of a survey access control list.
 */
export function aclToDocument(acl: Map<string, Role>): DocumentData | Error {
  return toDocumentData(
    new Pb.Survey({
      acl: acl.map(role => roleToProtoRole(role)).toObject(),
    })
  );
}

/**
 * Creates a proto rapresentation of a Job.
 */
export function jobToDocument(job: Job): DocumentData {
  return toDocumentData(
    new Pb.Job({
      id: job.id,
      index: job.index,
      name: job.name,
      style: new Pb.Style({color: job.color}),
      tasks: job.tasks
        ?.map(task => {
          return new Pb.Task({
            ...taskTypeToPartialMessage(task),
            id: task.id,
            index: task.index,
            prompt: task.label,
            required: task.required,
            level: task.addLoiTask
              ? Pb.Task.DataCollectionLevel.LOI_DATA
              : Pb.Task.DataCollectionLevel.LOI_METADATA,
            conditions: taskConditionToPartialMessage(task),
          });
        })
        .toList()
        .toArray(),
    })
  );
}

/**
 * Creates a partial rapresentation of a Task.
 */
function taskTypeToPartialMessage(task: Task): Pb.ITask {
  const {type: taskType, multipleChoice: taskMultipleChoice} = task;

  switch (taskType) {
    case TaskType.TEXT:
      return {
        textQuestion: new Pb.Task.TextQuestion({
          type: Pb.Task.TextQuestion.Type.SHORT_TEXT,
        }),
      };
    case TaskType.MULTIPLE_CHOICE:
      return {
        multipleChoiceQuestion: new Pb.Task.MultipleChoiceQuestion({
          type:
            taskMultipleChoice!.cardinality === Cardinality.SELECT_ONE
              ? Pb.Task.MultipleChoiceQuestion.Type.SELECT_ONE
              : Pb.Task.MultipleChoiceQuestion.Type.SELECT_MULTIPLE,
          hasOtherOption: task.multipleChoice!.hasOtherOption,
          options: taskMultipleChoice!.options
            .map(
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
    case TaskType.PHOTO:
      return {
        takePhoto: new Pb.Task.TakePhoto({
          minHeadingDegrees: 0,
          maxHeadingDegrees: 360,
        }),
      };
    case TaskType.NUMBER:
      return {
        numberQuestion: new Pb.Task.NumberQuestion({
          type: Pb.Task.NumberQuestion.Type.FLOAT,
        }),
      };
    case TaskType.DATE:
      return {
        dateTimeQuestion: new Pb.Task.DateTimeQuestion({
          type: Pb.Task.DateTimeQuestion.Type.DATE_ONLY,
        }),
      };
    case TaskType.TIME:
      return {
        dateTimeQuestion: new Pb.Task.DateTimeQuestion({
          type: Pb.Task.DateTimeQuestion.Type.TIME_ONLY,
        }),
      };
    case TaskType.DATE_TIME:
      return {
        dateTimeQuestion: new Pb.Task.DateTimeQuestion({
          type: Pb.Task.DateTimeQuestion.Type.BOTH_DATE_AND_TIME,
        }),
      };
    case TaskType.DRAW_AREA:
      return {
        drawGeometry: new Pb.Task.DrawGeometry({
          allowedMethods: [Pb.Task.DrawGeometry.Method.DRAW_AREA],
        }),
      };
    case TaskType.DROP_PIN:
      return {
        drawGeometry: new Pb.Task.DrawGeometry({
          allowedMethods: [Pb.Task.DrawGeometry.Method.DROP_PIN],
        }),
      };
    case TaskType.CAPTURE_LOCATION:
      return {
        captureLocation: new Pb.Task.CaptureLocation({
          minAccuracyMeters: null,
        }),
      };
    default:
      throw new Error(`Invalid role encountered: ${taskType}`);
  }
}

/**
 * Creates a partial rapresentation of a Task.
 */
function taskConditionToPartialMessage(task: Task): Pb.Task.ICondition[] {
  const {condition: taskCondition} = task;

  return (
    taskCondition?.expressions
      .map(
        expression =>
          new Pb.Task.Condition({
            multipleChoice: new Pb.Task.MultipleChoiceSelection({
              optionIds: expression.optionIds.toArray(),
            }),
          })
      )
      .toArray() || []
  );
}
