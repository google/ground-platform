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

@Component({
  selector: 'app-option-editor',
  templateUrl: './option-editor.component.html',
  styleUrls: ['./option-editor.component.css'],
})
export class OptionEditorComponent implements OnInit, OnChanges {
  @Input() code?: string;
  @Input() label?: string;

  optionGroup: FormGroup;

  constructor(private formBuilder: FormBuilder) {
    this.optionGroup = this.formBuilder.group({
      code: [''],
      label: [''],
    });
  }

  ngOnInit(): void {}

  getCode() {
    return this.optionGroup.get('code')?.value;
  }

  getLabel() {
    return this.optionGroup.get('label')?.value;
  }

  ngOnChanges() {
    this.optionGroup.setValue({
      code: this.code,
      label: this.label,
    });
  }
}
