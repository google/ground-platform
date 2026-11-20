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

import { MultipleChoice } from './multiple-choice.model';
import { Option } from './option.model';
import { TaskCondition } from './task-condition.model';
import { Copiable } from '../copiable';

/**
 *
 * It converts in JavaScript to:
 * {
 *   1: "TEXT",
 *   2: "MULTIPLE_CHOICE",
 *   3: "PHOTO",
 *   4: "NUMBER",
 *   5: "DATE",
 *   6: "TIME",
 *   TEXT: 1,
 *   MULTIPLE_CHOICE: 2,
 *   PHOTO: 3,
 *   NUMBER: 4,
 *   DATE: 5,
 *   TIME: 6,
 * }
 *
 * So to convert string to enum use:
 *  var value = TaskType["TEXT"]
 * Because then:
 *  value == TaskType.TEXT
 */
export enum TaskType {
  TEXT = 1,
  MULTIPLE_CHOICE = 2,
  PHOTO = 3,
  NUMBER = 4,
  DATE = 5,
  TIME = 6,
  DATE_TIME = 7,
  DRAW_AREA = 8,
  DROP_PIN = 9,
  CAPTURE_LOCATION = 10,
  INSTRUCTIONS = 11,
}

// TODO: add a subclass of Task for each task type.
export class Task extends Copiable {
  constructor(
    readonly id: string,
    readonly type: TaskType,
    readonly label: string,
    readonly required: boolean,
    readonly index: number,
    readonly multipleChoice?: MultipleChoice,
    /**
     * When collecting data, this task will be shown only when the condition is
     * `null` or when it evaluates to `true`.
     */
    readonly condition?: TaskCondition,
    readonly addLoiTask?: boolean
  ) {
    super();
  }

  /**
   * For MULTIPLE_CHOICE tasks, returns the option with the specified id.
   * Throws an Error if called for other task types.
   */
  getMultipleChoiceOption(optionId: string): Option {
    if (this.type !== TaskType.MULTIPLE_CHOICE) {
      throw Error(
        `Task ${this.id} of type ${TaskType[this.type]} has no options.`
      );
    }
    if (this.multipleChoice === undefined) {
      throw Error(`Task ${this.id} does not have choices defined.`);
    }
    const option = this.multipleChoice.options.find(o => o.id === optionId);
    if (!option) {
      throw Error(`Option ${optionId} not found in task ${this.id}.`);
    }
    return option;
  }
}
