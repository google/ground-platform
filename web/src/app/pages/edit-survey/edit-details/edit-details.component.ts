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

import {Component, OnInit, ViewChild} from '@angular/core';
import {Subscription} from 'rxjs';

import {Survey} from 'app/models/survey.model';
import {SurveyDetailsComponent} from 'app/pages/create-survey/survey-details/survey-details.component';
import {DraftSurveyService} from 'app/services/draft-survey/draft-survey.service';

@Component({
  selector: 'ground-edit-details',
  templateUrl: './edit-details.component.html',
})
export class EditDetailsComponent implements OnInit {
  subscription: Subscription = new Subscription();

  survey?: Survey;

  @ViewChild('surveyDetails')
  surveyDetails?: SurveyDetailsComponent;

  constructor(public draftSurveyService: DraftSurveyService) {}

  ngOnInit() {
    this.subscription.add(
      this.draftSurveyService
        .getSurvey$()
        .subscribe(survey => (this.survey = survey))
    );
  }

  onDetailChanges(valid: boolean): void {
    if (this.surveyDetails) {
      const [title, description] = this.surveyDetails.toTitleAndDescription();

      this.draftSurveyService.updateTitleAndDescription(
        title,
        description,
        valid
      );
    }
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
