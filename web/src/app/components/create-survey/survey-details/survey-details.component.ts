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

import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {FormBuilder, FormGroup, Validators} from '@angular/forms';

@Component({
  selector: 'survey-details',
  templateUrl: './survey-details.component.html',
  styleUrls: ['./survey-details.component.scss'],
})
export class SurveyDetailsComponent implements OnInit {
  readonly titleControlKey = 'title';
  readonly descriptionControlKey = 'description';
  formGroup!: FormGroup;

  @Input() title = '';
  @Input() description = '';
  @Output() onValidationChange: EventEmitter<boolean> =
    new EventEmitter<boolean>();
  @Output() onValueChanges: EventEmitter<boolean> = new EventEmitter<boolean>();

  constructor(private formBuilder: FormBuilder) {
    this.formGroup = this.formBuilder.group({
      [this.titleControlKey]: ['', Validators.required],
      [this.descriptionControlKey]: '',
    });

    this.formGroup.statusChanges.subscribe(_ => {
      this.onValidationChange.emit(this.formGroup?.valid);
    });

    this.formGroup.valueChanges.subscribe(_ => {
      this.onValueChanges.emit(this.formGroup?.valid);
    });
  }

  ngOnInit(): void {
    this.formGroup.controls[this.titleControlKey].setValue(this.title);
    this.formGroup.controls[this.descriptionControlKey].setValue(
      this.description
    );
  }

  toTitleAndDescription(): [string, string] {
    return [
      this.formGroup.controls[this.titleControlKey].value,
      this.formGroup.controls[this.descriptionControlKey].value,
    ];
  }
}
