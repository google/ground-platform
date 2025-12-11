/**
 * Copyright 2022 The Ground Authors.
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
import {toMessage} from '@ground/lib';
import {GroundProtos} from '@ground/proto';
import {List, Map, OrderedMap} from 'immutable';

import {DataCollectionStrategy, Job} from 'app/models/job.model';
import {Role} from 'app/models/role.model';
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
import {Option} from 'app/models/task/option.model';
import {
  TaskCondition,
  TaskConditionExpression,
  TaskConditionExpressionType,
  TaskConditionMatchType,
} from 'app/models/task/task-condition.model';
import {Task, TaskType} from 'app/models/task/task.model';

import Pb = GroundProtos.ground.v1beta1;

const DateTimeQuestionType = Pb.Task.DateTimeQuestion.Type;
const DrawGeometryMethod = Pb.Task.DrawGeometry.Method;
const MultipleChoiceQuestionType = Pb.Task.MultipleChoiceQuestion.Type;
const DataCollectionLevel = Pb.Task.DataCollectionLevel;

export const MODEL_ROLES = Map([
  [Pb.Role.SURVEY_ORGANIZER, Role.SURVEY_ORGANIZER],
  [Pb.Role.DATA_COLLECTOR, Role.DATA_COLLECTOR],
  [Pb.Role.VIEWER, Role.VIEWER],
]);

const MODEL_DATA_SHARING_TERMS_TYPES = Map([
  [Pb.Survey.DataSharingTerms.Type.PRIVATE, DataSharingType.PRIVATE],
  [Pb.Survey.DataSharingTerms.Type.PUBLIC_CC0, DataSharingType.PUBLIC],
  [Pb.Survey.DataSharingTerms.Type.CUSTOM, DataSharingType.CUSTOM],
]);

const MODEL_STATE_VALUES = Map([
  [Pb.Survey.State.DRAFT, SurveyState.DRAFT],
  [Pb.Survey.State.READY, SurveyState.READY],
]);

const MODEL_GENERAL_ACCESS_VALUES = Map([
  [Pb.Survey.GeneralAccess.RESTRICTED, SurveyGeneralAccess.RESTRICTED],
  [Pb.Survey.GeneralAccess.UNLISTED, SurveyGeneralAccess.UNLISTED],
  [Pb.Survey.GeneralAccess.PUBLIC, SurveyGeneralAccess.PUBLIC],
]);

const MODEL_DATA_VISIBILITY_VALUES = Map([
  [
    Pb.Survey.DataVisibility.ALL_SURVEY_PARTICIPANTS,
    SurveyDataVisibility.ALL_SURVEY_PARTICIPANTS,
  ],
  [
    Pb.Survey.DataVisibility.CONTRIBUTOR_AND_ORGANIZERS,
    SurveyDataVisibility.CONTRIBUTOR_AND_ORGANIZERS,
  ],
]);

function dataSharingTypeFromProto(
  protoType?: Pb.Survey.DataSharingTerms.Type | null
) {
  if (!protoType) {
    return DataSharingType.PRIVATE;
  }

  const dataSharingType = MODEL_DATA_SHARING_TERMS_TYPES.get(protoType);

  if (!dataSharingType) {
    return DataSharingType.PRIVATE;
  }

  return dataSharingType;
}

/**
 * Helper to return either the keys of a dictionary, or if missing, an
 * empty array.
 */
function keys(dict?: {}): string[] {
  return Object.keys(dict || {});
}

export function jobDocToModel(data: DocumentData): Job {
  return jobPbToModel(toMessage(data, Pb.Job) as Pb.Job);
}

export function jobDocsToModel(data: DocumentData[]): List<Job> {
  return List<Job>(data.map(jobDocToModel));
}

function jobPbToModel(pb: Pb.IJob): Job {
  return new Job(
    pb.id!,
    pb.index!,
    pb.style?.color || undefined,
    pb.name!,
    OrderedMap<string, Task>(
      pb.tasks!.map(taskPb => [taskPb.id!, taskPbToModel(taskPb)])
    ),
    pb.tasks!.find(task => task.level === DataCollectionLevel.LOI_METADATA)
      ? DataCollectionStrategy.MIXED
      : DataCollectionStrategy.PREDEFINED
  );
}

function taskPbToModelTaskType(pb: Pb.ITask): TaskType {
  const {
    textQuestion,
    numberQuestion,
    dateTimeQuestion,
    multipleChoiceQuestion,
    drawGeometry,
    captureLocation,
    takePhoto,
    instructions,
  } = pb;

  if (textQuestion) return TaskType.TEXT;
  else if (numberQuestion) return TaskType.NUMBER;
  else if (dateTimeQuestion) {
    if (dateTimeQuestion.type === DateTimeQuestionType.DATE_ONLY)
      return TaskType.DATE;
    else if (dateTimeQuestion.type === DateTimeQuestionType.TIME_ONLY)
      return TaskType.TIME;
    else if (dateTimeQuestion.type === DateTimeQuestionType.BOTH_DATE_AND_TIME)
      return TaskType.DATE_TIME;
    else throw new Error('Error converting to Task: invalid task data');
  } else if (multipleChoiceQuestion) return TaskType.MULTIPLE_CHOICE;
  else if (drawGeometry) {
    if (drawGeometry.allowedMethods!.includes(DrawGeometryMethod.DRAW_AREA))
      return TaskType.DRAW_AREA;
    else if (drawGeometry.allowedMethods!.includes(DrawGeometryMethod.DROP_PIN))
      return TaskType.DROP_PIN;
    else throw new Error('Error converting to Task: invalid task data');
  } else if (captureLocation) return TaskType.CAPTURE_LOCATION;
  else if (takePhoto) return TaskType.PHOTO;
  else if (instructions) return TaskType.INSTRUCTIONS;
  else throw new Error('Error converting to Task: invalid task data');
}

function taskConditionPbToModel(pb: Pb.ITask): TaskCondition | undefined {
  const {conditions} = pb;

  if (Array.isArray(conditions) && conditions.length > 0) {
    const {multipleChoice} = conditions[0];

    return new TaskCondition(
      TaskConditionMatchType.MATCH_ALL,
      List([
        new TaskConditionExpression(
          TaskConditionExpressionType.ONE_OF_SELECTED,
          multipleChoice!.taskId!,
          List(multipleChoice!.optionIds!)
        ),
      ])
    );
  } else return undefined;
}

function taskMultipleChoicePbToModel(pb: Pb.ITask): MultipleChoice | undefined {
  const {multipleChoiceQuestion} = pb;

  if (multipleChoiceQuestion) {
    return new MultipleChoice(
      multipleChoiceQuestion.type! ===
      MultipleChoiceQuestionType.SELECT_MULTIPLE
        ? Cardinality.SELECT_MULTIPLE
        : Cardinality.SELECT_ONE,
      List(
        multipleChoiceQuestion.options!.map(
          option =>
            new Option(option.id!, option.id!, option.label!, option.index!)
        )
      ),
      multipleChoiceQuestion.hasOtherOption!
    );
  } else return undefined;
}

function taskPbToModel(pb: Pb.ITask): Task {
  return new Task(
    pb.id!,
    taskPbToModelTaskType(pb),
    pb.prompt!,
    pb.required!,
    pb.index!,
    taskMultipleChoicePbToModel(pb),
    taskConditionPbToModel(pb),
    pb.level! === DataCollectionLevel.LOI_METADATA
  );
}

export function surveyDocToModel(
  id: string,
  data: DocumentData,
  jobs?: List<Job>
): Survey | Error {
  const pb = toMessage(data, Pb.Survey) as Pb.Survey;
  return new Survey(
    id,
    pb.name,
    pb.description,
    jobs
      ? Map<string, Job>(jobs.map((job: Job) => [job.id, job]))
      : Map<string, Job>(),
    Map<string, Role>(
      keys(pb.acl).map((id: string) => [
        id as string,
        MODEL_ROLES.get(pb.acl[id])!,
      ])
    ),
    pb.ownerId,
    {
      type: dataSharingTypeFromProto(pb.dataSharingTerms?.type),
      customText: pb.dataSharingTerms?.customText ?? undefined,
    },
    MODEL_STATE_VALUES.get(pb.state),
    MODEL_GENERAL_ACCESS_VALUES.get(pb.generalAccess) ||
      SurveyGeneralAccess.RESTRICTED,
    MODEL_DATA_VISIBILITY_VALUES.get(pb.dataVisibility) ||
      SurveyDataVisibility.CONTRIBUTOR_AND_ORGANIZERS
  );
}
