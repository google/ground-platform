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
import { Job } from '../../shared/models/job.model';
import { Field, FieldType } from '../../shared/models/form/field.model';
import { StringMap } from '../../shared/models/string-map.model';
import { Option } from '../../shared/models/form/option.model';
import { MultipleChoice } from '../../shared/models/form/multiple-choice.model';
import { List, Map } from 'immutable';
import { Form } from '../../shared/models/form/form.model';
import { ProjectService } from '../project/project.service';
import { take } from 'rxjs/operators';

@Injectable({
  providedIn: 'root',
})
export class JobService {
  constructor(
    private dataStoreService: DataStoreService,
    private projectService: ProjectService
  ) {}

  /**
   * Creates and returns a new job with a generated unique identifier.
   */
  createNewJob(): Job {
    const jobId = this.dataStoreService.generateId();
    return new Job(jobId, /* index */ -1);
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
   * Adds/Updates the job of a project with a given job value.
   */
  async addOrUpdateJob(projectId: string, job: Job): Promise<void> {
    if (job.index === -1) {
      const index = await this.getJobCount();
      job = job.withIndex(index);
    }
    return this.dataStoreService.addOrUpdateJob(projectId, job);
  }

  /**
   * Converts list of fields to map.
   */
  convertFieldsListToMap(fields: List<Field>): Map<string, Field> {
    let fieldsMap = Map<string, Field>();
    fields.forEach((field: Field, index: number) => {
      const jobFieldId = fields && fields.get(index)?.id;
      const fieldId = jobFieldId
        ? jobFieldId
        : this.dataStoreService.generateId();
      fieldsMap = fieldsMap.set(fieldId, field);
    });
    return fieldsMap;
  }

  /**
   * Creates and returns a new form map with a with a generated unique identifier and fields value.
   *
   * @param id the id of the new form.
   * @param fields the fields of the new form.
   */
  createForm(
    id: string | undefined,
    fields: Map<string, Field>
  ): Map<string, Form> | undefined {
    if (JobService.isFormEmpty(fields)) {
      return undefined;
    }
    const formId = id || this.dataStoreService.generateId();
    return JobService.createFormMap(formId, new Form(formId, fields));
  }

  /**
   * Creates and returns a form map with a given id and form value.
   */
  private static createFormMap(id: string, form: Form): Map<string, Form> {
    let forms = Map<string, Form>();
    forms = forms.set(id, form);
    return forms;
  }

  /**
   * Checks if there are no fields or first field in the form is empty.
   */
  private static isFormEmpty(fields: Map<string, Field>): boolean {
    return (
      fields.isEmpty() ||
      (fields.size === 1 && !JobService.getFieldLabel(fields.first()))
    );
  }

  private static getFieldLabel(field: Field): string {
    return field.label.get('en')?.trim() || '';
  }

  /**
   * Returns the form value from a job passed.
   */
  getForm(job?: Job): Form | undefined {
    const forms = job?.forms;
    return forms ? forms.valueSeq().first() : undefined;
  }

  private async getJobCount(): Promise<number> {
    const project = await this.projectService
      .getActiveProject$()
      .pipe(take(1))
      .toPromise();
    return project.jobs?.size;
  }
}
