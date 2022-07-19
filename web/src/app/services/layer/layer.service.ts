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
import { Field, FieldType } from '../../shared/models/task/field.model';
import { StringMap } from '../../shared/models/string-map.model';
import { Option } from '../../shared/models/task/option.model';
import { MultipleChoice } from '../../shared/models/task/multiple-choice.model';
import { List, Map } from 'immutable';
import { Task } from '../../shared/models/task/task.model';
import { SurveyService } from '../survey/survey.service';
import { take } from 'rxjs/operators';

@Injectable({
  providedIn: 'root',
})
export class LayerService {
  constructor(
    private dataStoreService: DataStoreService,
    private surveyService: SurveyService
  ) {}

  /**
   * Creates and returns a new layer with a generated unique identifier.
   */
  createNewLayer(): Layer {
    const layerId = this.dataStoreService.generateId();
    return new Layer(layerId, /* index */ -1);
  }

  /**
   * Creates and returns a new field with a generated unique identifier and a single English label.
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
   * Creates and returns a new option with a generated unique identifier, a single English label and code.
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
   * Adds/Updates the layer of a survey with a given layer value.
   */
  async addOrUpdateLayer(surveyId: string, layer: Layer): Promise<void> {
    if (layer.index === -1) {
      const index = await this.getLayerCount();
      layer = layer.withIndex(index);
    }
    return this.dataStoreService.addOrUpdateLayer(surveyId, layer);
  }

  /**
   * Converts list of fields to map.
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
   * Creates and returns a new task map with a generated unique identifier and fields value.
   *
   * @param id the id of the new task.
   * @param fields the fields of the new task.
   */
  createTask(
    id: string | undefined,
    fields: Map<string, Field>
  ): Map<string, Task> | undefined {
    if (LayerService.isTaskEmpty(fields)) {
      return undefined;
    }
    const taskId = id || this.dataStoreService.generateId();
    return LayerService.createTaskMap(taskId, new Task(taskId, fields));
  }

  /**
   * Creates and returns a task map with a given id and task value.
   */
  private static createTaskMap(id: string, task: Task): Map<string, Task> {
    let tasks = Map<string, Task>();
    tasks = tasks.set(id, task);
    return tasks;
  }

  /**
   * Checks if there are no fields or first field in the task is empty.
   */
  private static isTaskEmpty(fields: Map<string, Field>): boolean {
    return (
      fields.isEmpty() ||
      (fields.size === 1 && !LayerService.getFieldLabel(fields.first()))
    );
  }

  private static getFieldLabel(field: Field): string {
    return field.label.get('en')?.trim() || '';
  }

  /**
   * Returns the task value from a layer passed.
   */
  getTask(layer?: Layer): Task | undefined {
    const tasks = layer?.tasks;
    return tasks ? tasks.valueSeq().first() : undefined;
  }

  private async getLayerCount(): Promise<number> {
    const survey = await this.surveyService
      .getActiveSurvey$()
      .pipe(take(1))
      .toPromise();
    return survey.layers?.size;
  }
}
