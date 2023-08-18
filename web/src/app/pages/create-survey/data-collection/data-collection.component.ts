/**
 * Copyright 2023 Google LLC
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

import {Component} from '@angular/core';
import {FormBuilder, FormGroup} from '@angular/forms';

/** Options for radio buttons determining who can set where data is collected */
export enum DataCollectionOption {
  SURVEY_ORGANIZERS = 'SURVEY_ORGANIZERS',
  DATA_COLLECTORS = 'DATA_COLLECTORS',
  ORGANIZERS_AND_COLLECTORS = 'ORGANIZERS_AND_COLLECTORS',
}

interface OptionCardConfig {
  value: DataCollectionOption;
  label: string;
  description: string;
}

@Component({
  selector: 'data-collection',
  templateUrl: './data-collection.component.html',
  styleUrls: ['./data-collection.component.scss'],
})
export class DataCollectionComponent {
  readonly dataCollectionControlKey = 'dataCollectionMethod';
  formGroup: FormGroup;

  dataCollectionOptions: OptionCardConfig[] = [
    {
      value: DataCollectionOption.SURVEY_ORGANIZERS,
      label: 'Survey organizers',
      description:
        'Data collectors collect data exclusively about locations of interest uploaded by you and other survey organizers.',
    },
    {
      value: DataCollectionOption.DATA_COLLECTORS,
      label: 'Data collectors',
      description:
        'Data collectors suggest and collect data about new locations of interest as they discover them.',
    },
    {
      value: DataCollectionOption.ORGANIZERS_AND_COLLECTORS,
      label: 'Both',
      description:
        'Data collectors may collect data about locations of interest uploaded by survey organizers, or suggest new locations as needed.',
    },
  ];

  constructor() {
    this.formGroup = new FormBuilder().group({
      [this.dataCollectionControlKey]: this.dataCollectionOptions[0].value,
    });
  }

  dataCollectionOptionSelected(value: DataCollectionOption) {
    this.formGroup.controls[this.dataCollectionControlKey].setValue(value);
  }
}
