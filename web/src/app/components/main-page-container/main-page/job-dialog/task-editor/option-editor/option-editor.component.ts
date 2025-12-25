/**
 * Copyright 2020 The Ground Authors.
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
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  Output,
  ViewChild,
} from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';

@Component({
  selector: 'ground-option-editor',
  templateUrl: './option-editor.component.html',
  styleUrls: ['./option-editor.component.scss'],
  standalone: false,
})
export class OptionEditorComponent implements OnInit, OnChanges {
  @Input() code?: string;
  @Input() label?: string;
  @Input() index?: number;
  @Output() update = new EventEmitter();
  @Output() delete = new EventEmitter();
  @ViewChild('optionInput', { static: true }) optionInput?: ElementRef;

  optionGroup: FormGroup;

  constructor(private formBuilder: FormBuilder) {
    this.optionGroup = this.formBuilder.group({
      code: [''],
      label: ['', Validators.required],
    });
  }

  ngOnInit(): void {
    // As the options fields value change we are emitting the updated value to the form-field-editor.
    this.optionGroup.valueChanges.subscribe(value => {
      this.update.emit({
        code: value.code,
        label: value.label,
      });
    });
  }

  ngOnChanges(): void {
    this.optionGroup.setValue({
      code: this.code,
      label: this.label,
    });
  }

  onDeleteOption(index: number): void {
    this.delete.emit(index);
  }

  get labelControl(): AbstractControl {
    return this.optionGroup.get('label')!;
  }

  get codeControl(): AbstractControl {
    return this.optionGroup.get('code')!;
  }

  onLabelBlur(): void {
    this.labelControl.setValue(this.labelControl.value.trim());
  }

  onCodeBlur(): void {
    this.codeControl.setValue(this.codeControl.value.trim());
  }
}
