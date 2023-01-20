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

import {ActivatedRoute} from '@angular/router';
import {Component, OnInit} from '@angular/core';
import {SurveyService} from 'app/services/survey/survey.service';
import {FormGroup, FormBuilder} from '@angular/forms';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {Survey} from 'app/models/survey.model';

@Component({
  selector: 'create-survey',
  templateUrl: './create-survey.component.html',
  styleUrls: ['./create-survey.component.scss'],
})
export class CreateSurveyComponent implements OnInit {
  readonly titleControlKey = 'title';
  readonly descriptionControlKey = 'description';
  private currentSurveyId: string | null = null;
  formGroup: FormGroup;

  constructor(
    private surveyService: SurveyService,
    private navigationService: NavigationService,
    route: ActivatedRoute
  ) {
    this.formGroup = new FormBuilder().group({
      [this.titleControlKey]: '',
      [this.descriptionControlKey]: '',
    });
    navigationService.init(route);
  }

  ngOnInit(): void {
    this.navigationService.getSurveyId$().subscribe(surveyId => {
      if (surveyId) {
        this.surveyService.activateSurvey(surveyId);
      }
      this.currentSurveyId = surveyId;
    });
    this.surveyService
      .getActiveSurvey$()
      .subscribe(survey => this.loadExistingSurvey(survey));
  }

  loadExistingSurvey(survey: Survey): void {
    this.formGroup.controls[this.titleControlKey].setValue(survey.title);
    this.formGroup.controls[this.descriptionControlKey].setValue(
      survey.description
    );
  }

  back(): void {
    this.navigationService.navigateToSurveyList();
  }

  async continue(): Promise<void> {
    const title = this.formGroup.controls[this.titleControlKey].value;
    const description =
      this.formGroup.controls[this.descriptionControlKey].value;
    if (this.currentSurveyId) {
      await this.surveyService.updateTitleAndDescription(
        this.currentSurveyId,
        title,
        description
      );
      this.navigationService.navigateToCreateJob(this.currentSurveyId);
    } else {
      const createdSurveyId = await this.surveyService.createSurvey(
        title,
        description
      );
      this.navigationService.navigateToCreateJob(createdSurveyId);
    }
  }
}
