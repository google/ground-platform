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
import {List, Map} from 'immutable';

import {Job} from 'app/models/job.model';
import {Role} from 'app/models/role.model';
import {
  Cardinality,
  MultipleChoice,
} from 'app/models/task/multiple-choice.model';
import {TaskCondition} from 'app/models/task/task-condition.model';
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
 * Returns the proto representation of a Survey model object.
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
 * Returns the proto representation of a partial Survey model object.
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
 * Returns the proto representation of a Survey ACL model object.
 */
export function aclToDocument(acl: Map<string, Role>): DocumentData | Error {
  return toDocumentData(
    new Pb.Survey({
      acl: acl.map(role => roleToProtoRole(role)).toObject(),
    })
  );
}

/**
 * Returns the proto representation of a Job model object.
 */
export function jobToDocument(job: Job): DocumentData {
  return toDocumentData(
    new Pb.Job({
      id: job.id,
      index: job.index,
      name: job.name,
      style: new Pb.Style({color: job.color}),
      tasks: job.tasks?.toList().map(toTaskMessage).toArray(),
    })
  );
}

export function tasksToDocument(tasks: List<Task>): DocumentData {
  return toDocumentData(tasks.toList().map(toTaskMessage).toArray());
}

/**
 * Returns a Protobuf message representing a partial Task model.
 */
function toTaskTypeMessage(
  taskType: TaskType,
  taskMultipleChoice?: MultipleChoice
): Pb.ITask {
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
          hasOtherOption: taskMultipleChoice!.hasOtherOption,
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
 * Returns a Protobuf message representing a TaskCondition model.
 */
function toTaskConditionMessage(
  taskCondition?: TaskCondition
): Pb.Task.ICondition[] {
  return (
    taskCondition?.expressions
      .map(
        expression =>
          new Pb.Task.Condition({
            multipleChoice: new Pb.Task.MultipleChoiceSelection({
              optionIds: expression.optionIds.toArray(),
              taskId: expression.taskId,
            }),
          })
      )
      .toArray() || []
  );
}

/**
 * Returns a Protobuf messager epresenting a Task model.
 */
function toTaskMessage(task: Task): Pb.ITask {
  return new Pb.Task({
    ...toTaskTypeMessage(task.type, task.multipleChoice),
    id: task.id,
    index: task.index,
    prompt: task.label,
    required: task.required,
    level: task.addLoiTask
      ? Pb.Task.DataCollectionLevel.LOI_DATA
      : Pb.Task.DataCollectionLevel.LOI_METADATA,
    conditions: toTaskConditionMessage(task.condition),
  });
}
