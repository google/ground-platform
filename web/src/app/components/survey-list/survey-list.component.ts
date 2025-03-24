/**
 * Copyright 2021 The Ground Authors.
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

import {Component, OnDestroy, OnInit} from '@angular/core';
import {List} from 'immutable';
import {Subscription} from 'rxjs';

import {Survey, SurveyState} from 'app/models/survey.model';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';

export enum SurveyListFilter {
  ALL,
  RESTRICTED,
  UNLISTED,
  PUBLIC,
}

@Component({
  selector: 'ground-survey-list',
  templateUrl: './survey-list.component.html',
  styleUrls: ['./survey-list.component.scss'],
})
export class SurveyListComponent implements OnInit, OnDestroy {
  private subscription = new Subscription();
  surveys = List<Survey>();

  constructor(
    private surveyService: SurveyService,
    private navigationService: NavigationService
  ) {}

  ngOnInit(): void {
    const allSurveys = this.surveyService.getAccessibleSurveys$();
    this.subscription.add(
      allSurveys?.subscribe(
        surveys => {
          this.surveys = surveys;
        },
        err => {
          console.error(err);
          this.navigationService.error(err);
        }
      )
    );
  }

  handleSurveySelection(clickedSurvey: Survey): void {
    if (this.isSetupFinished(clickedSurvey)) {
      this.navigationService.selectSurvey(clickedSurvey.id);
    } else {
      this.navigationService.navigateToCreateSurvey(clickedSurvey.id);
    }
  }

  createNewSurvey(): void {
    this.navigationService.navigateToCreateSurvey(null);
  }

  private isSetupFinished(survey: Survey): boolean {
    return survey.state === SurveyState.READY;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
