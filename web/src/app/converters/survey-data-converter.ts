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
import {registry, toMessage} from '@ground/lib';
import {GroundProtos} from '@ground/proto';
import {List, Map} from 'immutable';

import {DataCollectionStrategy, Job} from 'app/models/job.model';
import {Role} from 'app/models/role.model';
import {Survey} from 'app/models/survey.model';
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
const s = registry.getFieldIds(Pb.Survey);

const DateTimeQuestionType = Pb.Task.DateTimeQuestion.Type;
const DrawGeometryMethod = Pb.Task.DrawGeometry.Method;
const MultipleChoiceQuestionType = Pb.Task.MultipleChoiceQuestion.Type;
const DataCollectionLevel = Pb.Task.DataCollectionLevel;

const TASK_TYPE_ENUMS_BY_STRING = Map([
  [TaskType.TEXT, 'text_field'],
  [TaskType.DATE, 'date'],
  [TaskType.MULTIPLE_CHOICE, 'multiple_choice'],
  [TaskType.DATE_TIME, 'date_time'],
  [TaskType.NUMBER, 'number'],
  [TaskType.PHOTO, 'photo'],
  [TaskType.DROP_PIN, 'drop_pin'],
  [TaskType.DRAW_AREA, 'draw_area'],
  [TaskType.CAPTURE_LOCATION, 'capture_location'],
]);

const TASK_TYPE_STRINGS_BY_ENUM = Map(
  Array.from(
    TASK_TYPE_ENUMS_BY_STRING.toArray(),
    el => el.reverse() as [string, TaskType]
  )
);

const MODEL_ROLES = Map([
  [Pb.Role.SURVEY_ORGANIZER, Role.SURVEY_ORGANIZER],
  [Pb.Role.DATA_COLLECTOR, Role.DATA_COLLECTOR],
  [Pb.Role.VIEWER, Role.VIEWER],
]);

/**
 * Helper to return either the keys of a dictionary, or if missing, an
 * empty array.
 */
function keys(dict?: {}): string[] {
  return Object.keys(dict || {});
}

export function jobDocsToModel(data: DocumentData[]): List<Job> {
  return List<Job>(
    data.map(job => jobPbToModel(toMessage(job, Pb.Job) as Pb.Job))
  );
}

function jobPbToModel(pb: Pb.IJob): Job {
  return new Job(
    pb.id!,
    pb.index!,
    pb.style?.color || undefined,
    pb.name!,
    Map<string, Task>(
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
  // Use old converter if document doesn't include `name` using the new
  // proto-based format.
  if (!data[s.name]) {
    return LegacySurveyDataConverter.toSurvey(id, data);
  }
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
    )
  );
}

export class LegacySurveyDataConverter {
  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Survey instance.
   *
   * @param id the uuid of the survey instance.
   * @param data the source data in a dictionary keyed by string.
   */
  static toSurvey(id: string, data: DocumentData): Survey {
    return new Survey(
      id,
      data.title,
      data.description,
      Map<string, Job>(
        keys(data.jobs).map((id: string) => [
          id as string,
          LegacySurveyDataConverter.toJob(id, data.jobs[id]),
        ])
      ),
      Map<string, Role>(
        keys(data.acl).map((id: string) => [
          id as string,
          LegacySurveyDataConverter.toRole(data.acl[id]),
        ])
      )
    );
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Job instance.
   *
   * @param id the uuid of the job instance.
   * @param data the source data in a dictionary keyed by string.
   */
  private static toJob(id: string, data: DocumentData): Job {
    return new Job(
      id,
      // Fall back to constant so old dev databases do not break.
      data.index || -1,
      data.defaultStyle?.color || data.color,
      data.name,
      LegacySurveyDataConverter.toTasks(data),
      LegacySurveyDataConverter.toStrategy(data.strategy)
    );
  }

  static toStrategy(strategy: string): DataCollectionStrategy {
    if (!strategy) return DataCollectionStrategy.PREDEFINED;
    if (strategy === 'AD_HOC') return DataCollectionStrategy.MIXED;
    return strategy as DataCollectionStrategy;
  }

  static toRole(roleString: string): Role {
    // Compatibility for older versions of Ground db.
    roleString = roleString.replace(/-/g, '_').toUpperCase();
    let role: Role | undefined = Role[roleString as keyof typeof Role];
    if (!role) {
      console.log('User has unsupported role: ', roleString);
      role = Role.VIEWER;
    }
    return role;
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Map of id to Task.
   *
   * @param data the source tasks in a dictionary keyed by string.
   */
  private static toTasks(data: DocumentData): Map<string, Task> {
    return Map<string, Task>(
      keys(data.tasks)
        .map(id => LegacySurveyDataConverter.toTask(id, data.tasks[id]))
        .filter(task => task !== null)
        .map(task => [task!.id, task!])
    );
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Task instance.
   *
   * @param id the uuid of the survey instance.
   * @param data the source data in a dictionary keyed by string.
   * <pre><code>
   * {
   *   index: 0,
   *   label: 'Question 1',
   *   required: true,
   *   type: 'text_field'
   * }
   * </code></pre>
   * or
   * <pre><code>
   * {
   *   index: 1,
   *   label: 'Question 2',
   *   required: false,
   *   type: 'multiple_choice',
   *   cardinality: 'select_one',
   *   options: {
   *     option001: {
   *       index: 0,
   *       code: 'A',
   *       label: 'Option A'
   *     },
   *     // ...
   *   }
   * </code></pre>
   */
  private static toTask(id: string, data: DocumentData): Task | null {
    try {
      return new Task(
        id,
        // TODO: Move private static functions out to top-level of file so that
        // they don't need to be qualified with full class name.
        LegacySurveyDataConverter.stringToTaskType(data.type),
        data.label,
        data.required,
        // Fall back to constant so old dev databases do not break.
        data.index || -1,
        data.options &&
          new MultipleChoice(
            LegacySurveyDataConverter.stringToCardinality(data.cardinality),
            List(
              keys(data.options).map((id: string) =>
                LegacySurveyDataConverter.toOption(id, data.options[id])
              )
            ),
            data.hasOtherOption || false
          ),
        LegacySurveyDataConverter.toCondition(data.condition),
        data.addLoiTask
      );
    } catch (e) {
      console.error(e);
      return null;
    }
  }

  private static toCondition(map?: DocumentData): TaskCondition | undefined {
    if (!map) return undefined;
    return new TaskCondition(
      map?.matchType,
      List(
        map?.expressions?.map((expression: any) => ({
          expressionType: expression.expressionType,
          taskId: expression.taskId,
          optionIds: List(expression.optionIds),
        }))
      ) || []
    );
  }

  private static stringToCardinality(cardinality: string): Cardinality {
    switch (cardinality) {
      case 'select_one':
        return Cardinality.SELECT_ONE;
      case 'select_multiple':
        return Cardinality.SELECT_MULTIPLE;
      default:
        throw Error(`Unsupported cardinality ${cardinality}`);
    }
  }

  private static stringToTaskType(taskType: string): TaskType {
    const type = TASK_TYPE_STRINGS_BY_ENUM.get(taskType);
    if (!type) {
      throw new Error(`Ignoring unsupported task of type: ${taskType}`);
    }
    return type;
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Task instance.
   *
   * @param id the uuid of the survey instance.
   * @param data the source data in a dictionary keyed by string.
   * <pre><code>
   * {
   *    index: 0,
   *    code: 'A',
   *    label: 'Option A'
   *  }
   * </code></pre>
   */
  private static toOption(id: string, data: DocumentData): Option {
    return new Option(
      id,
      data.code,
      data.label,
      // Fall back to constant so old dev databases do not break.
      data.index || -1
    );
  }
}
