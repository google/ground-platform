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

import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges,
} from '@angular/core';
import {FormBuilder, FormGroup} from '@angular/forms';

/** Options for radio buttons determining who can set where data is collected */
export enum LoiPermissionsOption {
  SURVEY_ORGANIZERS = 'SURVEY_ORGANIZERS',
  DATA_COLLECTORS = 'DATA_COLLECTORS',
  ORGANIZERS_AND_COLLECTORS = 'ORGANIZERS_AND_COLLECTORS',
}

interface OptionCardConfig {
  value: LoiPermissionsOption;
  label: string;
  description: string;
}

@Component({
  selector: 'loi-permissions',
  templateUrl: './loi-permissions.component.html',
  styleUrls: ['./loi-permissions.component.scss'],
})
export class LoiPermissionsComponent implements OnInit, OnChanges {
  @Input() defaultSelection!: LoiPermissionsOption;
  @Output() select: EventEmitter<LoiPermissionsOption> =
    new EventEmitter<LoiPermissionsOption>();

  readonly loiPermissionsControlKey = 'loiPermissionsOption';
  formGroup: FormGroup;

  loiPermissionsOptions: OptionCardConfig[] = [
    {
      value: LoiPermissionsOption.SURVEY_ORGANIZERS,
      label: 'Survey organizers',
      description:
        'Data collectors collect data exclusively about locations of interest uploaded by you and other survey organizers.',
    },
    {
      value: LoiPermissionsOption.DATA_COLLECTORS,
      label: 'Data collectors',
      description:
        'Data collectors suggest and collect data about new locations of interest as they discover them.',
    },
    {
      value: LoiPermissionsOption.ORGANIZERS_AND_COLLECTORS,
      label: 'Both',
      description:
        'Data collectors may collect data about locations of interest uploaded by survey organizers, or suggest new locations as needed.',
    },
  ];

  constructor() {
    this.formGroup = new FormBuilder().group({
      [this.loiPermissionsControlKey]: this.loiPermissionsOptions[0].value,
    });
  }

  ngOnInit(): void {
    this.formGroup.controls.loiPermissionsOption.valueChanges.subscribe(
      permissionOption => {
        this.select.emit(permissionOption);
      }
    );
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['defaultSelection']) {
      this.formGroup.controls.loiPermissionsOption.setValue(
        this.defaultSelection
      );
    }
  }

  optionCardClicked(value: LoiPermissionsOption) {
    this.formGroup.controls[this.loiPermissionsControlKey].setValue(value);
  }
}
