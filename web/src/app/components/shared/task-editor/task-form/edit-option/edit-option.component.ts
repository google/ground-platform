/**
 * Copyright 2023 The Ground Authors.
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

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AbstractControl, FormGroup } from '@angular/forms';

import { Cardinality } from 'app/models/task/multiple-choice.model';

@Component({
  selector: 'ground-edit-option',
  templateUrl: './edit-option.component.html',
  styleUrls: ['./edit-option.component.scss'],
  standalone: false,
})
export class EditOptionComponent {
  @Input() formGroup!: FormGroup;
  @Input() index!: number;
  @Input() type: Cardinality = Cardinality.SELECT_ONE;

  @Output() update = new EventEmitter();
  @Output() delete = new EventEmitter();

  Cardinality = Cardinality;

  onDeleteOption(index: number): void {
    this.delete.emit(index);
  }

  get labelControl(): AbstractControl {
    return this.formGroup.get('label')!;
  }

  onLabelBlur(): void {
    this.labelControl.setValue(this.labelControl.value.trim());
  }
}
