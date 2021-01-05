/**
 * Copyright 2020 Google LLC
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

import { Option } from './option.model';
import { List } from 'immutable';

/**
 *
 * It converts in JavaScript to:
 * {
 *   1: "SELECT_ONE",
 *   3: "SELECT_MULTIPLE",
 *   SELECT_ONE: 1,
 *   SELECT_MULTIPLE: 2,
 * }
 *
 * So to convert string to enum use:
 *  var value = Cardinality["SELECT_ONE"]
 * Because then:
 *  value == Cardinality.SELECT_ONE
 */
export enum Cardinality {
  SELECT_ONE = 1,
  SELECT_MULTIPLE = 2,
}

export class MultipleChoice {
  constructor(
    readonly cardinality: Cardinality,
    readonly options: List<Option>
  ) {
    this.options = this.options.sortBy(option => option.index);
  }
}
