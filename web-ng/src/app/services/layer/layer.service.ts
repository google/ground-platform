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

  /**
   * Creates a new layer with a generated uuid.
   */
  createNewLayer(): Layer {
    const layerId = this.dataStoreService.generateId();
    return new Layer(layerId, /* index */ -1);
  }

  /**
   * Creates a new field with a given type, label, required, index and multipleChoice values.
   *
   * @param type the type of the new field.
   * @param label the label of the new field.
   * @param required the required value of the new field.
   * @param index the index of the new field.
   * @param multipleChoice the multipleChoice value of the new field.
   */
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

  /**
   * Creates a new option with a given code, label and index.
   *
   * @param code the code of the new option.
   * @param label the label of the new option.
   * @param index the index of the new option.
   */
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

  /**
   * Updates layer of a project with a given layer value.
   *
   * @param projectId the id of the project
   * @param layer the layer of the project
   */
  updateLayer(projectId: string, layer: Layer): Promise<void> {
    return this.dataStoreService.updateLayer(projectId, layer);
  }

  /**
   * Converts list of fields to map.
   *
   * @param fields the fields that are going to be converted to map type.
   */
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

  /**
   * Creates a new form with a given id and fields value.
   *
   * @param id the id of the new form.
   * @param fields the fields of the new form.
   */
  createForm(
    id: string | undefined,
    fields: Map<string, Field>
  ): Map<string, Form> {
    const formId = id || this.dataStoreService.generateId();
    return LayerService.createFormMap(formId, new Form(formId, fields));
  }

  /**
   * Creates a form map with a given id and form value.
   *
   * @param id the id of the forms that need to be set.
   * @param form the value of the form that will be set at a given index.
   */
  private static createFormMap(id: string, form: Form): Map<string, Form> {
    let forms = Map<string, Form>();
    forms = forms.set(id, form);
    return forms;
  }

  /**
   * Returns the form value from a layer passed.
   *
   * @param layer the layer from which form values are returned.
   */
  getForm(layer?: Layer): Form | undefined {
    const forms = layer?.forms;
    return forms ? forms.valueSeq().first() : undefined;
  }
}
