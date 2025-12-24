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

import { DocumentData } from '@angular/fire/firestore';
import { toDocumentData } from '@ground/lib';
import { GroundProtos } from '@ground/proto';
import { Job } from 'app/models/job.model';
import { Role } from 'app/models/role.model';
import {
  DataSharingType,
  Survey,
  SurveyDataVisibility,
  SurveyGeneralAccess,
  SurveyState,
} from 'app/models/survey.model';
import {
  Cardinality,
  MultipleChoice,
} from 'app/models/task/multiple-choice.model';
import { TaskCondition } from 'app/models/task/task-condition.model';
import { Task, TaskType } from 'app/models/task/task.model';
import { List, Map } from 'immutable';

import Pb = GroundProtos.ground.v1beta1;

const PB_ROLES = Map([
  [Role.OWNER, Pb.Role.SURVEY_ORGANIZER],
  [Role.SURVEY_ORGANIZER, Pb.Role.SURVEY_ORGANIZER],
  [Role.DATA_COLLECTOR, Pb.Role.DATA_COLLECTOR],
  [Role.VIEWER, Pb.Role.VIEWER],
]);

const PB_DATA_SHARING_TYPE = Map([
  [DataSharingType.PRIVATE, Pb.Survey.DataSharingTerms.Type.PRIVATE],
  [DataSharingType.PUBLIC, Pb.Survey.DataSharingTerms.Type.PUBLIC_CC0],
  [DataSharingType.CUSTOM, Pb.Survey.DataSharingTerms.Type.CUSTOM],
]);

const PB_STATES = Map([
  [SurveyState.DRAFT, Pb.Survey.State.DRAFT],
  [SurveyState.READY, Pb.Survey.State.READY],
]);

const PB_GENERAL_ACCESS = Map([
  [SurveyGeneralAccess.RESTRICTED, Pb.Survey.GeneralAccess.RESTRICTED],
  [SurveyGeneralAccess.UNLISTED, Pb.Survey.GeneralAccess.UNLISTED],
  [SurveyGeneralAccess.PUBLIC, Pb.Survey.GeneralAccess.PUBLIC],
]);

const PB_DATA_VISIBILITY = Map([
  [
    SurveyDataVisibility.CONTRIBUTOR_AND_ORGANIZERS,
    Pb.Survey.DataVisibility.CONTRIBUTOR_AND_ORGANIZERS,
  ],
  [
    SurveyDataVisibility.ALL_SURVEY_PARTICIPANTS,
    Pb.Survey.DataVisibility.ALL_SURVEY_PARTICIPANTS,
  ],
]);

/**
 * Converts Role instance to its proto message type.
 */
export function roleToProtoRole(role: Role) {
  const pbRole = PB_ROLES.get(role);

  if (!pbRole) {
    throw new Error(`Invalid role encountered: ${role}`);
  }

  return pbRole;
}

export function dataSharingTypeToProto(type: DataSharingType) {
  const pbType = PB_DATA_SHARING_TYPE.get(type);

  if (!pbType) {
    throw new Error(`Invalid data sharing type encountered: ${type}`);
  }

  return pbType;
}

/**
 * Returns the proto representation of a Survey model object.
 */
export function surveyToDocument(
  surveyId: string,
  survey: Partial<Survey>
): DocumentData {
  const {
    title: name,
    description,
    acl,
    ownerId,
    dataSharingTerms,
    state,
    generalAccess,
    dataVisibility,
  } = survey;

  return toDocumentData(
    new Pb.Survey({
      id: surveyId,
      name,
      ...(description && { description }),
      ...(acl && { acl: acl.map(role => roleToProtoRole(role)).toObject() }),
      ownerId,
      ...(dataSharingTerms && {
        dataSharingTerms: toDataSharingTermsMessage(dataSharingTerms),
      }),
      state: PB_STATES.get(state || SurveyState.DRAFT),
      generalAccess: PB_GENERAL_ACCESS.get(
        generalAccess || SurveyGeneralAccess.RESTRICTED
      ),
      dataVisibility: PB_DATA_VISIBILITY.get(
        dataVisibility || SurveyDataVisibility.CONTRIBUTOR_AND_ORGANIZERS
      ),
    })
  );
}

/**
 * Returns the proto representation of a Job model object.
 */
export function jobToDocument(job: Job): DocumentData {
  const { id, index, name, color, tasks } = job;

  return toDocumentData(
    new Pb.Job({
      id,
      index,
      name,
      style: new Pb.Style({ color }),
      tasks: (tasks?.toList() ?? List())
        .map((task: Task) => toTaskMessage(task))
        .toArray(),
    })
  );
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
    case TaskType.INSTRUCTIONS:
      return {
        instructions: new Pb.Task.InstructionsTask({
          text: '',
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
          minAccuracyMeters: 0,
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
 * Returns a Protobuf message representing a Task model.
 */
function toTaskMessage(task: Task): Pb.ITask {
  return new Pb.Task({
    ...toTaskTypeMessage(task.type, task.multipleChoice),
    id: task.id,
    index: task.index,
    prompt: task.label,
    required: task.required,
    level: task.addLoiTask
      ? Pb.Task.DataCollectionLevel.LOI_METADATA
      : Pb.Task.DataCollectionLevel.LOI_DATA,
    conditions: toTaskConditionMessage(task.condition),
  });
}

/**
 * Returns a Protobuf message representing a DataSharingTerms model.
 */
function toDataSharingTermsMessage(dataSharingTerms: {
  type: DataSharingType;
  customText?: string;
}): Pb.Survey.IDataSharingTerms {
  return new Pb.Survey.DataSharingTerms({
    type: dataSharingTypeToProto(dataSharingTerms.type),
    customText: dataSharingTerms.customText,
  });
}
