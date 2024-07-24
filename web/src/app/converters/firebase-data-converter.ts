/**
 * Copyright 2019 The Ground Authors.
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

import {DocumentData, Timestamp} from '@angular/fire/firestore';
import {List, Map} from 'immutable';

import {AuditInfo} from 'app/models/audit-info.model';
import {MultiPolygon} from 'app/models/geometry/multi-polygon';
import {DataCollectionStrategy, Job} from 'app/models/job.model';
import {Role} from 'app/models/role.model';
import {Result} from 'app/models/submission/result.model';
import {
  Submission,
  SubmissionData,
} from 'app/models/submission/submission.model';
import {Survey} from 'app/models/survey.model';
import {
  Cardinality,
  MultipleChoice,
} from 'app/models/task/multiple-choice.model';
import {Option} from 'app/models/task/option.model';
import {
  TaskCondition,
  TaskConditionMatchType,
} from 'app/models/task/task-condition.model';
import {Task, TaskType} from 'app/models/task/task.model';
import {User} from 'app/models/user.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';

import {toGeometry} from './geometry-converter';
import {Point} from '../models/geometry/point';
import {Polygon} from '../models/geometry/polygon';

const TASK_TYPE_ENUMS_BY_STRING = Map([
  [TaskType.TEXT, 'text_field'],
  [TaskType.DATE, 'date'],
  [TaskType.MULTIPLE_CHOICE, 'multiple_choice'],
  [TaskType.TIME, 'time'],
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

/**
 * Helper to return either the keys of a dictionary, or if missing, returns an
 * empty array.
 */
function keys(dict?: {}): string[] {
  return Object.keys(dict || {});
}

export class FirebaseDataConverter {
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
          FirebaseDataConverter.toJob(id, data.jobs[id]),
        ])
      ),
      Map<string, Role>(
        keys(data.acl).map((id: string) => [
          id as string,
          FirebaseDataConverter.toRole(data.acl[id]),
        ])
      )
    );
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

  static newSurveyToJS(
    title: string,
    description: string,
    acl: Map<string, Role>
  ) {
    return {
      title,
      description,
      acl: FirebaseDataConverter.aclToJs(acl),
    };
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
      this.toTasks(data),
      this.toStrategy(data.strategy)
    );
  }

  static toStrategy(strategy: string): DataCollectionStrategy {
    if (!strategy) return DataCollectionStrategy.PREDEFINED;
    if (strategy === 'AD_HOC') return DataCollectionStrategy.MIXED;
    return strategy as DataCollectionStrategy;
  }

  static jobToJS(job: Job): {} {
    const {name, tasks, color, strategy, ...jobDoc} = job;
    return {
      strategy,
      name,
      tasks: this.tasksToJS(tasks),
      defaultStyle: {color},
      ...jobDoc,
    };
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
        .map(id => FirebaseDataConverter.toTask(id, data.tasks[id]))
        .filter(task => task !== null)
        .map(task => [task!.id, task!])
    );
  }

  static tasksToJS(tasks: Map<string, Task> | undefined): {} {
    if (!tasks) {
      return {};
    }

    return tasks?.map(task => this.taskToJS(task)).toJS();
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
        FirebaseDataConverter.stringToTaskType(data.type),
        data.label,
        data.required,
        // Fall back to constant so old dev databases do not break.
        data.index || -1,
        data.options &&
          new MultipleChoice(
            FirebaseDataConverter.stringToCardinality(data.cardinality),
            List(
              keys(data.options).map((id: string) =>
                FirebaseDataConverter.toOption(id, data.options[id])
              )
            ),
            data.hasOtherOption || false
          ),
        FirebaseDataConverter.toCondition(data.condition),
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

  private static taskToJS(task: Task): {} {
    const {type, multipleChoice, condition, id, ...taskDoc} = task;

    if (multipleChoice === undefined) {
      return {
        type: FirebaseDataConverter.taskTypeToString(type),
        condition: FirebaseDataConverter.taskConditionToJS(condition),
        ...taskDoc,
      };
    } else {
      return {
        type: FirebaseDataConverter.taskTypeToString(type),
        condition: FirebaseDataConverter.taskConditionToJS(condition),
        cardinality: FirebaseDataConverter.cardinalityToString(
          multipleChoice.cardinality
        ),
        hasOtherOption: multipleChoice.hasOtherOption,
        // convert list of options to map of optionId: option.
        options:
          multipleChoice?.options?.reduce(
            (map, option: Option) => ({
              ...map,
              [option.id]: FirebaseDataConverter.optionToJS(option),
            }),
            {}
          ) || {},
        ...taskDoc,
      };
    }
  }
  private static taskConditionToJS(condition?: TaskCondition): {} | undefined {
    if (!condition) return undefined;
    if (condition.matchType === TaskConditionMatchType.MATCH_ALL) {
      return {
        matchType: condition.matchType,
        expressions: condition.expressions,
      };
    } else {
      throw new Error(`Unimplemented task condition type $condition`);
    }
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

  private static cardinalityToString(cardinality: Cardinality): string {
    switch (cardinality) {
      case Cardinality.SELECT_ONE:
        return 'select_one';
      case Cardinality.SELECT_MULTIPLE:
        return 'select_multiple';
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

  private static taskTypeToString(taskType: TaskType): string {
    const str = TASK_TYPE_ENUMS_BY_STRING.get(taskType);
    if (!str) {
      throw Error(`Unsupported task type ${taskType}`);
    }
    return str;
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

  private static optionToJS(option: Option): {} {
    const {id, ...optionDoc} = option;
    return optionDoc;
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Submission instance.
   *
   * @param data the source data in a dictionary keyed by string.
   * <pre><code>
   * {
   *   loiId: 'loi123'
   *   taskId: 'task001',
   *   data: {
   *     'task001': 'Result text',    // For 'text_field' tasks.
   *     'task002': ['A', 'B'],       // For 'multiple_choice' tasks.
   *      // ...
   *   }
   *   created: <AUDIT_INFO>,
   *   lastModified: <AUDIT_INFO>
   * }
   * </code></pre>
   */
  static toSubmission(
    job: Job,
    id: string,
    data: DocumentData
  ): Submission | Error {
    if (!job.tasks) {
      return Error(
        'Error converting to submission: job must contain at least once task'
      );
    }
    if (!data) {
      return Error(
        `Error converting to submission: submission ${id} does not have document data.`
      );
    }
    return new Submission(
      id,
      data.loiId,
      job,
      FirebaseDataConverter.toAuditInfo(data.created),
      FirebaseDataConverter.toAuditInfo(data.lastModified),
      FirebaseDataConverter.toResults(job, data)
    );
  }

  static submissionToJS(submission: Submission): {} {
    return {
      loiId: submission.loiId,
      jobId: submission.job?.id,
      created: FirebaseDataConverter.auditInfoToJs(submission.created),
      lastModified: FirebaseDataConverter.auditInfoToJs(
        submission.lastModified
      ),
      data: FirebaseDataConverter.dataToJS(submission.data),
    };
  }

  static toUser(data: DocumentData, uid: string): User | undefined {
    if (!data) {
      return;
    }
    return new User(
      uid,
      data.email,
      data.isAuthenticated || true,
      data.displayName,
      data.photoURL
    );
  }

  private static dataToJS(data: SubmissionData): {} {
    return data.entrySeq().reduce(
      (obj: {}, [taskId, result]) => ({
        ...obj,
        [taskId]: FirebaseDataConverter.resultToJS(result),
      }),
      {}
    );
  }

  /**
   * Extracts and converts from the raw Firebase object to a map of {@link Result}s keyed by task id.
   * In case of error when converting from raw data to {@link Result}, logs the error and then ignores
   * that one {@link Result}.
   *
   * @param job the job related to this submission data, {@link job.tasks} must not be null or undefined.
   * @param data the source data in a dictionary keyed by string.
   */
  private static toResults(job: Job, data: DocumentData): Map<string, Result> {
    // TODO(#1288): Clean up remaining references to old responses field
    // Support submissions that have results or responses fields instead of data
    // before model change.
    const submissionData = data.data ?? data.results ?? data.responses;
    return Map<string, Result>(
      keys(submissionData)
        .map((taskId: string) => {
          return [
            taskId as string,
            FirebaseDataConverter.toResult(
              submissionData[taskId],
              job.tasks!.get(taskId)
            ),
          ];
        })
        .filter(([_, resultOrError]) =>
          DataStoreService.filterAndLogError<Result>(
            resultOrError as Result | Error
          )
        )
        .map(([k, v]) => [k, v] as [string, Result])
    );
  }

  private static toResult(
    resultValue: number | string | List<string>,
    task?: Task
  ): Result | Error {
    try {
      if (typeof resultValue === 'string') {
        return new Result(resultValue as string);
      }
      if (typeof resultValue === 'number') {
        return new Result(resultValue as number);
      }
      if (resultValue instanceof Array) {
        return new Result(
          List(
            resultValue
              .filter(optionId => !optionId.startsWith('['))
              .map(
                optionId =>
                  task?.getMultipleChoiceOption(optionId) ||
                  new Option(optionId, optionId, optionId, -1)
              )
          )
        );
      }
      if (resultValue instanceof Timestamp) {
        return new Result(resultValue.toDate());
      }
      const geometry = toGeometry(resultValue);
      if (
        geometry instanceof Point ||
        geometry instanceof Polygon ||
        geometry instanceof MultiPolygon
      ) {
        return new Result(geometry);
      }
      return Error(
        `Error converting to Result: unknown value type ${typeof resultValue}`
      );
    } catch (err: any) {
      return err instanceof Error ? err : new Error(err);
    }
  }

  private static resultToJS(result: Result): {} {
    if (typeof result.value === 'string') {
      return result.value;
    }
    if (typeof result.value === 'number') {
      return result.value;
    }
    if (result.value instanceof List) {
      return (result.value as List<Option>).map(option => option.id).toArray();
    }
    if (result.value instanceof Date) {
      return Timestamp.fromDate(result.value);
    }
    throw Error(`Unknown value type of ${result.value}`);
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable AuditInfo instance.
   *
   * @param data the source data in a dictionary keyed by string.
   * <pre><code>
   * {
   *   user: {
   *     id: ...,
   *     displayName: ...,
   *     email: ...
   *   },
   *   clientTimestamp: ...,
   *   serverTimestamp: ...
   * }
   * </code></pre>
   */
  private static toAuditInfo(data: DocumentData): AuditInfo {
    return new AuditInfo(
      data.user,
      data.clientTimestamp?.toDate(),
      data.serverTimestamp?.toDate()
    );
  }

  private static auditInfoToJs(auditInfo: AuditInfo): {} {
    return {
      user: FirebaseDataConverter.userToJs(auditInfo.user),
      clientTimestamp: auditInfo.clientTime,
      serverTimestamp: auditInfo.serverTime,
    };
  }

  private static userToJs(user: User): {} {
    return {
      id: user.id,
      email: user.email,
      displayName: user.displayName,
    };
  }

  static aclToJs(acl: Map<string, Role>): {} {
    return acl.map(FirebaseDataConverter.toRoleId).toJS();
  }

  /**
   * Returns the string used to represent the specified role in the database.
   * @param role the Role to be converted.
   */
  static toRoleId(role: Role): string {
    return Role[role].toUpperCase();
  }
}
