/**
 * Copyright 2023 The Ground Authors.
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

import {List} from 'immutable';
import {Copiable} from '../copiable';

export abstract class TaskCondition extends Copiable {}

/**
 * Tasks with this condition are shown only when the data collector is adding a
 * new, unplanned location of interest.
 */
export class NewUnplannedLocationOfInterestCondition extends TaskCondition {
  constructor() {
    super();
  }
}

/**
 * Tasks with this condition are shown only when the user reponds to the
 * previous multiple choice question task with `taskId` with one or more of the
 * options in this condition's `responseIds`.
 */
export class MultipleChoiceResponseCondition extends TaskCondition {
  constructor(readonly taskId: string, readonly responseIds: List<String>) {
    super();
  }
}
