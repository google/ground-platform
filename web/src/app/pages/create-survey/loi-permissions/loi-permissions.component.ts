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
  image_selected: string;
  image_unselected: string;
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
      label: 'Predefined',
      description:
        'Data collectors gather data exclusively about preloaded data collection sites.',
      image_selected: 'survey-org-color.png',
      image_unselected: 'survey-org-bw.png',
    },
    {
      value: LoiPermissionsOption.DATA_COLLECTORS,
      label: 'Ad hoc',
      description:
        'Data collectors map and collect data about new sites as they go.',
      image_selected: 'data-collectors-color.png',
      image_unselected: 'data-collectors-bw.png',
    },
    {
      value: LoiPermissionsOption.ORGANIZERS_AND_COLLECTORS,
      label: 'Mixed',
      description:
        'Data collectors gather data about preloaded sites, but can also add new sites as needed.',
      image_selected: 'both-color.png',
      image_unselected: 'both-bw.png',
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
