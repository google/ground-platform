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

import {
  Component,
  OnInit,
  Input,
  Output,
  EventEmitter,
  OnChanges,
} from '@angular/core';
import { FormGroup, FormBuilder } from '@angular/forms';
import { FieldType } from '../../shared/models/form/field.model';

export interface FieldTypeOptionModel {
  icon: string;
  label: string;
  type: FieldType;
}

@Component({
  selector: 'app-form-field-editor',
  templateUrl: './form-field-editor.component.html',
  styleUrls: ['./form-field-editor.component.css'],
})
export class FormFieldEditorComponent implements OnInit, OnChanges {
  @Input() label?: string;
  @Input() required?: boolean;
  @Input() type?: string;
  @Output() update = new EventEmitter();
  @Output() delete = new EventEmitter();
  fieldTypeOptions: FieldTypeOptionModel[] = [
    {
      icon: 'short_text',
      label: 'Text',
      type: FieldType.TEXT,
    },
    {
      icon: 'library_add_check',
      label: 'Select multiple',
      type: FieldType.MULTIPLE_CHOICE,
    },
  ];

  formFieldGroup: FormGroup;

  constructor(private formBuilder: FormBuilder) {
    this.formFieldGroup = this.formBuilder.group({
      label: [''],
      required: [false],
      type: this.fieldTypeOptions[0],
    });
  }

  ngOnInit(): void {
    this.formFieldGroup.valueChanges.subscribe(value => {
      this.update.emit({
        label: value.label,
        required: value.required,
        type: value.type.type,
      });
    });
  }

  ngOnChanges() {
    const type = this.fieldTypeOptions.find(
      fieldTypeOption => fieldTypeOption.type === Number(this.type)
    );
    this.formFieldGroup.setValue({
      label: this.label,
      required: this.required,
      type,
    });
  }

  onFieldDelete() {
    this.delete.emit();
  }

  getFieldType() {
    return this.formFieldGroup.get('type')?.value;
  }

  onFieldTypeSelect(event: FieldTypeOptionModel) {
    this.formFieldGroup.patchValue({ type: event });
  }
}
