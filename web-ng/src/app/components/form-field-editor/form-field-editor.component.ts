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
  SimpleChanges,
} from '@angular/core';
import { FormGroup, FormBuilder } from '@angular/forms';
import { FieldType } from '../../shared/models/form/field.model';
import { StringMap } from '../../shared/models/string-map.model';
import { Option } from '../../shared/models/form/option.model';
import { List } from 'immutable';
import {
  MultipleChoice,
  Cardinality,
} from '../../shared/models/form/multiple-choice.model';
import { DataStoreService } from '../../services/data-store/data-store.service';
import { ViewChildren, QueryList } from '@angular/core';
import { OptionEditorComponent } from '../option-editor/option-editor.component';

export interface FieldTypeSelectOption {
  icon: string;
  label: string;
  type: FieldType;
}

@Component({
  selector: 'app-form-field-editor',
  templateUrl: './form-field-editor.component.html',
  styleUrls: ['./form-field-editor.component.scss'],
})
export class FormFieldEditorComponent implements OnInit, OnChanges {
  @ViewChildren('optionEditor') optionsEditor?: QueryList<
    OptionEditorComponent
  >;
  @Input() label?: string;
  @Input() required?: boolean;
  @Input() type?: string;
  @Input() multipleChoice?: MultipleChoice;
  @Output() update = new EventEmitter();
  @Output() delete = new EventEmitter();
  formOptions: MultipleChoice | undefined;
  fieldTypes: FieldTypeSelectOption[] = [
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

  constructor(
    private formBuilder: FormBuilder,
    private dataStoreService: DataStoreService
  ) {
    this.formFieldGroup = this.formBuilder.group({
      label: [''],
      required: [false],
      type: this.fieldTypes[0],
    });
  }

  ngOnInit(): void {
    // As the form fields value change we are emitting the updated value to the layer-dialog.
    this.formFieldGroup.valueChanges.subscribe(value => {
      this.update.emit({
        label: StringMap({ en: value.label }),
        required: value.required,
        type: value.type.type,
        multipleChoice: this.formOptions,
      });
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    const type = this.fieldTypes.find(
      fieldType => fieldType.type === Number(this.type)
    );
    if (changes.multipleChoice) {
      this.formOptions = this.multipleChoice;
    }
    this.formFieldGroup.setValue({
      label: this.label,
      required: this.required,
      type,
    });
  }

  /**
   * Emits the delete field event to the layer dialog component.
   *
   * @returns void
   *
   */
  onFieldDelete() {
    this.delete.emit();
  }

  getFieldType() {
    return this.formFieldGroup.get('type')?.value;
  }

  /**
   * Updates the type in the formFieldGroup on the select change event.
   *
   * @param event: FieldTypeSelectOption
   * @returns void
   *
   */
  onFieldTypeSelect(event: FieldTypeSelectOption) {
    this.type = event.type.toString();
    this.formFieldGroup.patchValue({ type: event });
    if (event.type === FieldType.MULTIPLE_CHOICE) {
      this.onAddOption();
    } else {
      this.formOptions = undefined;
    }
  }

  trackByFn(index: number) {
    return index;
  }

  getOptions() {
    if (this.formFieldGroup.get('type')?.value.type === FieldType.TEXT) {
      return;
    }
    let options = List<Option>();
    const cardinality =
      this.formOptions?.cardinality || Cardinality.SELECT_MULTIPLE;
    this.optionsEditor?.toArray().forEach((optionEditor, index) => {
      const code = optionEditor.getCode();
      const label = optionEditor.getLabel();
      const option = this.createOption(code, label);
      options = options.set(index, option);
    });
    return new MultipleChoice(cardinality, options);
  }

  onAddOption() {
    const option = this.createOption('', '');
    const index = this.formOptions?.options.size || 0;
    this.setFormOptions(index, option);
  }

  createOption(code: string, label: string) {
    const optionId = this.dataStoreService.generateId();
    const option = new Option(optionId || '', code, StringMap({ en: label }));
    return option;
  }

  setFormOptions(index: number, option: Option) {
    const cardinality =
      this.formOptions?.cardinality || Cardinality.SELECT_MULTIPLE;
    let options = this.formOptions?.options || List<Option>();
    options = options?.set(index, option);
    this.formOptions = new MultipleChoice(cardinality, options);
  }
}
