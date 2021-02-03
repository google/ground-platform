/**
 * Copyright 2019 Google LLC
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

import { Form } from './form/form.model';
import { StringMap } from './string-map.model';
import { Map } from 'immutable';

export class Layer {
  constructor(
    readonly id: string,
    readonly index: number,
    readonly color?: string,
    readonly name?: StringMap,
    readonly forms?: Map<string, Form>,
    readonly contributorsCanAdd?: string[]
  ) {}

  getForm(formId: string): Form | undefined {
    return this.forms?.get(formId);
  }

  withIndex(index: number): Layer {
    return { ...this, index };
  }
}
