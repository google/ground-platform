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

import { Injectable } from '@angular/core';
import { DataStoreService } from '../data-store/data-store.service';
import { Layer } from '../../shared/models/layer.model';
import { Field, FieldType } from '../../shared/models/form/field.model';
import { StringMap } from '../../shared/models/string-map.model';
import { Option } from '../../shared/models/form/option.model';
import { MultipleChoice } from '../../shared/models/form/multiple-choice.model';
import { List, Map } from 'immutable';
import { Form } from '../../shared/models/form/form.model';

@Injectable({
  providedIn: 'root',
})
export class LayerService {
  constructor(private dataStoreService: DataStoreService) {}

  createNewLayer(): Layer {
    const layerId = this.dataStoreService.generateId();
    return new Layer(layerId, /* index */ -1);
  }

  createField(
    type: FieldType,
    label: string,
    required: boolean,
    index: number,
    multipleChoice?: MultipleChoice
  ): Field {
    const fieldId = this.dataStoreService.generateId();
    return new Field(
      fieldId,
      type,
      StringMap({
        en: label,
      }),
      required,
      index,
      multipleChoice
    );
  }

  createOption(code: string, label: string, index: number): Option {
    const optionId = this.dataStoreService.generateId();
    const option = new Option(
      optionId || '',
      code,
      StringMap({ en: label }),
      index
    );
    return option;
  }

  updateLayer(projectId: string, layer: Layer): Promise<void> {
    return this.dataStoreService.updateLayer(projectId, layer);
  }

  convertFieldsListToMap(fields: List<Field>): Map<string, Field> {
    let fieldsMap = Map<string, Field>();
    fields.forEach((field: Field, index: number) => {
      const layerFieldId = fields && fields.get(index)?.id;
      const fieldId = layerFieldId
        ? layerFieldId
        : this.dataStoreService.generateId();
      fieldsMap = fieldsMap.set(fieldId, field);
    });
    return fieldsMap;
  }

  createForm(
    id: string | undefined,
    fields: Map<string, Field>
  ): Map<string, Form> {
    const formId = id || this.dataStoreService.generateId();
    return this.setForms(formId, new Form(formId, fields));
  }

  setForms(id: string, form: Form): Map<string, Form> {
    let forms = Map<string, Form>();
    forms = forms.set(id, form);
    return forms;
  }

  getForm(layer?: Layer): Form | undefined {
    const forms = layer?.forms;
    return forms ? forms.valueSeq().first() : undefined;
  }
}
