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
import {Map} from 'immutable';

import {AuditInfo} from 'app/models/audit-info.model';
import {Job} from 'app/models/job.model';
import {Role} from 'app/models/role.model';
import {MultipleSelection} from 'app/models/submission/multiple-selection';
import {Result} from 'app/models/submission/result.model';
import {
  Submission,
  SubmissionData,
} from 'app/models/submission/submission.model';
import {Cardinality} from 'app/models/task/multiple-choice.model';
import {Option} from 'app/models/task/option.model';
import {
  TaskCondition,
  TaskConditionMatchType,
} from 'app/models/task/task-condition.model';
import {Task, TaskType} from 'app/models/task/task.model';
import {User} from 'app/models/user.model';

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

export class FirebaseDataConverter {
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

  static tasksToJS(tasks: Map<string, Task> | undefined): {} {
    if (!tasks) {
      return {};
    }
    return tasks?.map(task => this.taskToJS(task)).toJS();
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

  private static taskTypeToString(taskType: TaskType): string {
    const str = TASK_TYPE_ENUMS_BY_STRING.get(taskType);
    if (!str) {
      throw Error(`Unsupported task type ${taskType}`);
    }
    return str;
  }

  private static optionToJS(option: Option): {} {
    const {id, ...optionDoc} = option;
    return optionDoc;
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

  private static resultToJS(result: Result): {} {
    if (typeof result.value === 'string' || typeof result.value === 'number') {
      return result.value;
    }
    if (result.value instanceof MultipleSelection) {
      return result.value.values.toArray();
    }
    if (result.value instanceof Date) {
      return Timestamp.fromDate(result.value);
    }
    throw Error(`Unknown value type of ${result.value}`);
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
