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

import { Component } from '@angular/core';
import { MatSlideToggleChange } from '@angular/material/slide-toggle';
import { Survey, SurveyDataVisibility } from 'app/models/survey.model';
import { AuthService } from 'app/services/auth/auth.service';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'ground-data-visibility-control',
  templateUrl: './data-visibility-control.component.html',
  standalone: false,
})
export class DataVisibilityControlComponent {
  private subscription = new Subscription();

  selectedDataVisibility!: SurveyDataVisibility;

  SurveyDataVisibility = SurveyDataVisibility;

  constructor(
    readonly authService: AuthService,
    readonly draftSurveyService: DraftSurveyService
  ) {
    this.subscription.add(
      this.draftSurveyService
        .getSurvey$()
        .subscribe(survey => this.onSurveyLoaded(survey))
    );
  }

  private async onSurveyLoaded(survey: Survey): Promise<void> {
    this.selectedDataVisibility =
      survey.dataVisibility || SurveyDataVisibility.CONTRIBUTOR_AND_ORGANIZERS;
  }

  changeDataVisibility(event: MatSlideToggleChange) {
    const dataVisibility = event.checked
      ? SurveyDataVisibility.ALL_SURVEY_PARTICIPANTS
      : SurveyDataVisibility.CONTRIBUTOR_AND_ORGANIZERS;

    this.selectedDataVisibility = dataVisibility;

    this.draftSurveyService.updateDataVisibility(dataVisibility);
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
