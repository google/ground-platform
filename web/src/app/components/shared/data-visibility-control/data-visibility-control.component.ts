/**
 * Copyright 2025 The Ground Authors.
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
import { MatSlideToggleChange } from '@angular/material/slide-toggle';

import { Survey, SurveyDataVisibility } from 'app/models/survey.model';

@Component({
  selector: 'ground-data-visibility-control',
  templateUrl: './data-visibility-control.component.html',
  standalone: false,
})
export class DataVisibilityControlComponent {
  @Input() survey?: Survey;
  @Output() onDataVisibilityChange = new EventEmitter<SurveyDataVisibility>();

  readonly SurveyDataVisibility = SurveyDataVisibility;

  get selectedDataVisibility(): SurveyDataVisibility {
    return (
      this.survey?.dataVisibility ||
      SurveyDataVisibility.CONTRIBUTOR_AND_ORGANIZERS
    );
  }

  changeDataVisibility(event: MatSlideToggleChange) {
    const dataVisibility = event.checked
      ? SurveyDataVisibility.ALL_SURVEY_PARTICIPANTS
      : SurveyDataVisibility.CONTRIBUTOR_AND_ORGANIZERS;

    this.onDataVisibilityChange.emit(dataVisibility);
  }
}
