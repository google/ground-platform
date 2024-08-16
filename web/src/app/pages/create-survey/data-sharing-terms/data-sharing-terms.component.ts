/**
 * Copyright 2024 The Ground Authors.
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

import {DataSharingType} from 'app/models/survey.model';

type DataSharingTermsOption = {
  value: DataSharingType;
  label: string;
  description: string;
};

@Component({
  selector: 'data-sharing-terms',
  templateUrl: './data-sharing-terms.component.html',
  styleUrls: ['./data-sharing-terms.component.scss'],
})
export class DataSharingTermsComponent implements OnInit {
  @Input() type!: DataSharingType;
  @Input() customText?: string;
  @Output() onValidationChange: EventEmitter<boolean> =
    new EventEmitter<boolean>();

  readonly typeControlKey = 'type';
  readonly customTextControlKey = 'customText';
  formGroup!: FormGroup;

  dataSharingTermsOptions: DataSharingTermsOption[] = [
    {
      value: DataSharingType.PRIVATE,
      label: 'Private',
      description: 'Data will be shared with survey organizers only',
    },
    {
      value: DataSharingType.PUBLIC,
      label: 'Public',
      description:
        'Survey organizers may share and use data publicly with no constraints',
    },
    {
      value: DataSharingType.CUSTOM,
      label: 'Custom agreement',
      description:
        'Survey organizers create terms which must be accepted by data collectors before collecting data',
    },
  ];

  constructor(private formBuilder: FormBuilder) {
    this.formGroup = this.formBuilder.group({
      [this.typeControlKey]: DataSharingType.PRIVATE,
      [this.customTextControlKey]: [
        {value: '', disabled: true},
        Validators.required,
      ],
    });

    this.formGroup.statusChanges.subscribe(_ => {
      this.onValidationChange.emit(this.formGroup?.valid);
    });

    this.typeControl.valueChanges.subscribe(type => {
      if (type === DataSharingType.CUSTOM) {
        this.customTextControl.enable();
      } else {
        this.customTextControl.disable();
      }
    });
  }

  get typeControl() {
    return this.formGroup.controls[this.typeControlKey];
  }

  get customTextControl() {
    return this.formGroup.controls[this.customTextControlKey];
  }

  ngOnInit(): void {
    this.formGroup.controls[this.typeControlKey].setValue(this.type);
    if (this.customText) {
      this.formGroup.controls[this.customTextControlKey].setValue(
        this.customText
      );
    }
  }

  shouldShowCustomizeAgreementSection(type: DataSharingType): boolean {
    return (
      type === DataSharingType.CUSTOM &&
      this.typeControl.value === DataSharingType.CUSTOM
    );
  }
}
