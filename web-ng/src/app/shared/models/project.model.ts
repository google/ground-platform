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

import { Layer } from './layer.model';
import { Form } from './form/form.model';
import { StringMap } from './string-map.model';
import { Map } from 'immutable';

export class Project {
  constructor(
    readonly id: string,
    readonly title: StringMap,
    readonly description: StringMap,
    readonly layers: Map<string, Layer>
  ) {}

  getLayerForm(layerId: string, formId: string): Form {
    const layer = this.layers.get(layerId);
    if (!layer) {
      throw Error(`Layer ${layerId} not found in the project ${this.id}.`);
    }
    const form = layer.forms?.get(formId);
    if (!form) {
      throw Error(`Form ${formId} not found in the layer ${layerId}.`);
    }
    return form;
  }

  getForm(formId: string): Form {
    for (const [_, layer] of this.layers) {
      const form = layer.forms?.get(formId);
      if (form) {
        return form;
      }
    }
    throw Error(`Form ${formId} not found in the project ${this.id}.`);
  }
}
