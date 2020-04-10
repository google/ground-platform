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

import { Component, Inject, OnDestroy } from '@angular/core';
import { Observable } from 'rxjs';
import { ProjectService } from '../../services/project/project.service';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Project } from '../../shared/models/project.model';
import { Layer } from '../../shared/models/layer.model';
import { Form } from '../../shared/models/form/form.model';
import { Subscription } from 'rxjs';
import { DataStoreService } from '../../services/data-store/data-store.service';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, FormArray, FormControl } from '@angular/forms';
import { FieldType, Field } from '../../shared/models/form/field.model';
import { StringMap } from '../../shared/models/string-map.model';
import { Option } from '../../shared/models/form/option.model';
import { MultipleChoice } from '../../shared/models/form/multiple-choice.model';
import { Cardinality } from '../../shared/models/form/multiple-choice.model';
import { Map } from 'immutable';

const DEFAULT_LAYER_COLOR = '#ff9131';

export interface FormFieldType {
  icon: string;
  label: string;
  type: string;
}

export interface OptionModel {
  label: string;
  code: string;
}

export interface Question {
  label: string;
  fieldType: FormFieldType;
  options: OptionModel[];
}

@Component({
  selector: 'app-layer-dialog',
  templateUrl: './layer-dialog.component.html',
  styleUrls: ['./layer-dialog.component.css'],
})
export class LayerDialogComponent implements OnDestroy {
  lang: string;
  layerId: string;
  layer?: Layer;
  layerName!: string;
  projectId?: string;
  activeProject$: Observable<Project>;
  subscription: Subscription = new Subscription();
  layerForm: FormGroup;
  fieldTypes: FormFieldType[] = [
    {
      icon: 'short_text',
      label: 'Text',
      type: 'text',
    },
    {
      icon: 'library_add_check',
      label: 'Select multiple',
      type: 'multipleChoice',
    },
  ];

  constructor(
    // tslint:disable-next-line:no-any
    @Inject(MAT_DIALOG_DATA) data: any,
    private dialogRef: MatDialogRef<LayerDialogComponent>,
    private projectService: ProjectService,
    private dataStoreService: DataStoreService,
    private router: Router,
    private formBuilder: FormBuilder
  ) {
    this.lang = 'en';
    // Disable closing on clicks outside of dialog.
    dialogRef.disableClose = true;
    this.layerId = data.layerId;
    this.activeProject$ = this.projectService.getActiveProject$();
    this.layerForm = this.formBuilder.group({
      questions: this.formBuilder.array([this.createQuestionGroup()]),
    });
    this.subscription.add(
      this.activeProject$.subscribe(project => {
        this.onProjectLoaded(project);
      })
    );
  }

  getFieldType() {
    return {
      icon: 'short_text',
      label: 'Text',
    };
  }

  createQuestionGroup() {
    return this.formBuilder.group({
      label: [''],
      fieldType: new FormControl(this.fieldTypes[0]),
      options: this.formBuilder.array([this.createOptionGroup()]),
    });
  }

  createOptionGroup() {
    return this.formBuilder.group({
      label: [''],
      code: [''],
    });
  }

  addQuestion() {
    const control = this.layerForm.controls['questions'] as FormArray;
    control.push(this.createQuestionGroup());
  }

  addOption(control: FormArray) {
    control.push(
      this.formBuilder.group({
        label: [''],
        code: [''],
      })
    );
  }

  onProjectLoaded(project: Project) {
    if (this.layerId === ':new') {
      this.layerId = this.dataStoreService.generateId();
      this.layer = {
        id: this.layerId,
      };
    } else {
      this.layer = project.layers.get(this.layerId);
    }
    this.layerName = this.layer?.name?.get(this.lang) || '';

    if (!this.layer) {
      throw Error('No layer exists');
    }
    this.projectId = project.id;
  }

  getForm(formId: string, fields: Map<string, Field>) {
    const form = {
      id: formId,
      fields,
    };
    return form;
  }

  onSave() {
    // TODO: Wait for project to load before showing dialog.
    if (!this.projectId) {
      throw Error('Project not yet loaded');
    }
    let fields = Map<string, Field>();
    this.layerForm.value.questions.forEach((question: Question) => {
      let options = Map<string, Option>();
      const fieldId = this.dataStoreService.generateId();
      let field: Field = {
        id: fieldId,
        type: FieldType['TEXT'],
        required: false,
        label: StringMap({
          en: question.label || '',
        }),
      };
      if (question.fieldType.type === 'multipleChoice') {
        question.options.forEach((option: OptionModel) => {
          const optionId = this.dataStoreService.generateId();
          options = options.set(optionId, {
            id: optionId,
            code: option.code || '',
            label: StringMap({
              en: option.label || '',
            }),
          });
        });
        const multipleChoice: MultipleChoice = {
          cardinality: Cardinality['SELECT_MULTIPLE'],
          options,
        };
        field = {
          ...field,
          type: FieldType['MULTIPLE_CHOICE'],
          multipleChoice: multipleChoice || Map<string, Option>(),
        };
      }
      fields = fields.set(fieldId, field);
    });
    const formId = this.dataStoreService.generateId();
    const layer = new Layer(
      this.layerId,
      this.layer?.color || DEFAULT_LAYER_COLOR,
      // TODO: Make layerName Map
      StringMap({ [this.lang]: this.layerName }),
      this.layerForm.value.questions &&
      this.layerForm.value.questions.length > 0
        ? Map({
            [formId]: this.getForm(formId, fields),
          })
        : Map<string, Form>()
    );

    // TODO: Inform user layer was saved
    this.dataStoreService
      .updateLayer(this.projectId, layer)
      .then(() => this.onClose())
      .catch(err => {
        alert('Layer update failed.');
      });
  }

  onClose() {
    this.dialogRef.close();
    // TODO: refactor this path into a custom router wrapper
    return this.router.navigate([`p/${this.projectId}`]);
  }

  setLayerName(value: string) {
    this.layerName = value;
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
