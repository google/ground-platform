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
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { FieldType } from '../../shared/models/form/field.model';
import { StringMap } from '../../shared/models/string-map.model';
import { Option } from '../../shared/models/form/option.model';
import { List } from 'immutable';
import {
  MultipleChoice,
  Cardinality,
} from '../../shared/models/form/multiple-choice.model';
import { DataStoreService } from '../../services/data-store/data-store.service';
import { MatDialog } from '@angular/material/dialog';
import { ConfirmationDialogComponent } from '../confirmation-dialog/confirmation-dialog.component';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { LayerService } from '../../services/layer/layer.service';

export interface FieldTypeSelectOption {
  icon: string;
  label: string;
  type: FieldType;
  cardinality?: Cardinality;
}

@Component({
  selector: 'app-form-field-editor',
  templateUrl: './form-field-editor.component.html',
  styleUrls: ['./form-field-editor.component.scss'],
})
export class FormFieldEditorComponent implements OnInit, OnChanges {
  @Input() label?: string;
  @Input() required?: boolean;
  @Input() type?: FieldType;
  @Input() multipleChoice?: MultipleChoice;
  @Input() cardinality?: Cardinality;
  @Output() update = new EventEmitter();
  @Output() delete = new EventEmitter();
  formOptions: MultipleChoice | undefined;
  fieldTypes: FieldTypeSelectOption[] = [
    {
      icon: 'radio_button_checked',
      label: 'Select One',
      type: FieldType.MULTIPLE_CHOICE,
      cardinality: Cardinality.SELECT_ONE,
    },
    {
      icon: 'short_text',
      label: 'Text',
      type: FieldType.TEXT,
    },
    {
      icon: 'library_add_check',
      label: 'Select multiple',
      type: FieldType.MULTIPLE_CHOICE,
      cardinality: Cardinality.SELECT_MULTIPLE,
    },
  ];

  formFieldGroup: FormGroup;

  constructor(
    private formBuilder: FormBuilder,
    private dataStoreService: DataStoreService,
    private confirmationDialog: MatDialog,
    private layerService: LayerService
  ) {
    this.formFieldGroup = this.formBuilder.group({
      label: ['', Validators.required],
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

  /*
    Input params change from the parent are handled by this method.
  */
  ngOnChanges(changes: SimpleChanges) {
    const type =
      this.type == FieldType.TEXT
        ? this.fieldTypes[1]
        : this.fieldTypes.find(
            fieldType =>
              fieldType.type === this.type &&
              fieldType.cardinality === this.cardinality
          );
    if (changes.multipleChoice) {
      this.formOptions = this.multipleChoice;
      const options = this.formOptions?.options;
      options?.sortBy(option => option.index);
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
    this.type = event.type;
    this.cardinality = event.cardinality;
    if (this.cardinality && this.formOptions?.options) {
      this.formOptions = new MultipleChoice(
        this.cardinality,
        this.formOptions.options
      );
    }
    this.formFieldGroup.patchValue({ type: event });
    if (event.type === FieldType.MULTIPLE_CHOICE) {
      if (!this.formOptions?.options?.size) {
        this.onAddOption();
      }
    } else {
      this.formOptions = undefined;
    }
  }

  trackByFn(index: number) {
    return index;
  }

  /**
   * Emits event to layer dialog whenvever label or code gets updated in options-editor.
   *
   * @param event: label and code value of the option field.
   * @param index: index of the option to be updated.
   * @returns void
   *
   */

  onOptionUpdate(event: { label: string; code: string }, index: number) {
    const option = this.layerService.createOption(
      event.code,
      event.label,
      index
    );
    const options = this.setFormOptions(index, option);
    this.emitFormOptions(options);
  }

  onOptionDelete(index: number) {
    const dialogRef = this.openConfirmationDialog();
    dialogRef.afterClosed().subscribe(dialogResult => {
      if (dialogResult) {
        let options = this.formOptions?.options;
        if (!options) return;
        options = options.delete(index);
        this.emitFormOptions(options);
      }
    });
  }

  openConfirmationDialog() {
    return this.confirmationDialog.open(ConfirmationDialogComponent, {
      maxWidth: '500px',
      data: {
        title: 'Warning',
        message:
          'Are you sure you wish to delete this option? Any associated data will be lost. This cannot be undone.',
      },
    });
  }

  onAddOption() {
    const index = this.formOptions?.options.size || 0;
    const option = this.layerService.createOption('', '', index);
    const options = this.setFormOptions(index, option);
    this.emitFormOptions(options);
  }

  setFormOptions(index: number, option: Option) {
    let options = this.formOptions?.options || List<Option>();
    options = options?.set(index, option);
    return options;
  }

  emitFormOptions(options: List<Option>) {
    const cardinality =
      this.formOptions?.cardinality ||
      this.cardinality ||
      Cardinality.SELECT_MULTIPLE;
    this.formOptions = new MultipleChoice(cardinality, options);
    this.update.emit({
      label: StringMap({ en: this.label }),
      required: this.required,
      type: this.type,
      multipleChoice: this.formOptions,
    });
  }

  drop(event: CdkDragDrop<string[]>) {
    if (!this.formOptions) return;
    let options = this.formOptions.options;
    const optionAtPrevIndex = options.get(event.previousIndex);
    const optionAtCurrentIndex = options.get(event.currentIndex);
    if (optionAtPrevIndex && optionAtCurrentIndex) {
      options = options.set(event.previousIndex, optionAtCurrentIndex);
      options = options.set(event.currentIndex, optionAtPrevIndex);
    }
    this.emitFormOptions(options);
  }

  get labelControl() {
    return this.formFieldGroup.get('label')!;
  }
}
