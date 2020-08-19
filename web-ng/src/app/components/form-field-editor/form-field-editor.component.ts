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
}

@Component({
  selector: 'app-form-field-editor',
  templateUrl: './form-field-editor.component.html',
  styleUrls: ['./form-field-editor.component.scss'],
})
export class FormFieldEditorComponent implements OnInit, OnChanges {
  @Input() label?: string;
  @Input() required?: boolean;
  // Could this be an enum instead of a string so that it's more self-
  // documenting and also compile checked?
  @Input() type?: string;
  @Input() multipleChoice?: MultipleChoice;
  @Output() update = new EventEmitter();
  @Output() delete = new EventEmitter();
  formOptions: MultipleChoice | undefined;
  // Once would expect a variable called `fieldTypes` to be of type `FieldType`,
  // whereas here it reflects form select field options.
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
    // Unused, can be removed.
    private dataStoreService: DataStoreService,
    private confirmationDialog: MatDialog,
    private layerService: LayerService
  ) {
    // For consistency, can this be defined right after fieldTypes rather
    // than in the constructor (or vice versa)?
    // Re naming - we're already in the FormFieldEditor - since there's only
    // one of these, 'formGroup' would also be sufficient.
    this.formFieldGroup = this.formBuilder.group({
      label: ['', Validators.required],
      required: [false],
      // What's special about value "0" - add a comment explaining that we
      // define to the 0th item (i.e. "text" is the default option).
      // Also - `type` is overloaded here to be both the field type
      // (FieldType.TEXT) and the selected field type option. Careful
      // naming can disambiguate here.
      type: this.fieldTypes[0],
    });
  }

  ngOnInit(): void {
    // As the form fields value change we are emitting the updated value to the layer-dialog.
    // We need to store the subscription here returned as we do in other
    // components and dispose of it in ngOnDestroy().
    this.formFieldGroup.valueChanges.subscribe(value => {
      this.update.emit({
        label: StringMap({ en: value.label }),
        required: value.required,
        // This is hard to grok, it's a signal that the names could be chosen
        // to more carefully reflect what's what.
        type: value.type.type,
        // Same here. What's a "multipleChoice" and why is it the same thing
        // as formOptions? As-is it's not possible to understand what's
        // happening here without tracing back through the code.
        multipleChoice: this.formOptions,
      });
    });
  }

  // A comment explaining when is this method called would be helpful.
  ngOnChanges(changes: SimpleChanges) {
    const type = this.fieldTypes.find(
      fieldType => fieldType.type === Number(this.type)
    );
    if (changes.multipleChoice) {
      this.formOptions = this.multipleChoice;
      const options = this.formOptions?.options;
      // Does this sort the actual options in the form or a copy of them?
      // If it's the actual options, may be worth inlining the var `options`
      // here to make it more obvious.
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
    this.type = event.type.toString();
    this.formFieldGroup.patchValue({ type: event });
    if (event.type === FieldType.MULTIPLE_CHOICE) {
      this.onAddOption();
    } else {
      this.formOptions = undefined;
    }
  }

  // This method has an obscure name and I don't see a reference to it in this
  // class; if the name has special meaning a comment would help clarify.
  trackByFn(index: number) {
    return index;
  }

  // Original wording implied that the method is run many times, or once, and
  // sits around waiting until the label or code is updated. Instead, it
  // actually only called when that happens, and the method itself doesn't
  // impose those conditions at all. Also, the method itself doesn't only emit
  // to the LayerDialog; that's just the only subscriber to the component's
  // output. A more precise ("technical correct") description will prevent
  // confusion.
  /**
   * Emits event with updated Options. Called when label or code gets updated.
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
    // Let's use toPromise().then() here since this only emits once. It also
    // avoids us needing to manage the subscription.
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
          // Split up long lines.
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
      this.formOptions?.cardinality || Cardinality.SELECT_MULTIPLE;
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
