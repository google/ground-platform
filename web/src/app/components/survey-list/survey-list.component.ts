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
import {List, Map} from 'immutable';
import {Subscription} from 'rxjs';

import {
  Survey,
  SurveyGeneralAccess,
  SurveyState,
} from 'app/models/survey.model';
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
  allSurveys = List<Survey>();
  surveys = List<Survey>();
  currentFilter = SurveyListFilter.ALL;
  filterCounters = Map([[SurveyListFilter.ALL, 0]]);

  SurveyListFilter = SurveyListFilter;

  SurveyGeneralAccess = SurveyGeneralAccess;

  constructor(
    private navigationService: NavigationService,
    private surveyService: SurveyService
  ) {}

  ngOnInit(): void {
    this.subscription.add(
      this.surveyService.getAccessibleSurveys$().subscribe({
        next: surveys => {
          this.allSurveys = surveys;
          this.applyFilterCounters();
          this.applyFilter();
        },
        error: err => {
          console.error(err);
          this.navigationService.error(err);
        },
      })
    );
  }

  applyFilterCounters(): void {
    let restrictedCount = 0;
    let unlistedCount = 0;
    let publicCount = 0;

    this.allSurveys.forEach(survey => {
      if (survey.generalAccess === SurveyGeneralAccess.RESTRICTED) {
        restrictedCount++;
      } else if (survey.generalAccess === SurveyGeneralAccess.UNLISTED) {
        unlistedCount++;
      } else if (survey.generalAccess === SurveyGeneralAccess.PUBLIC) {
        publicCount++;
      }
    });

    this.filterCounters = Map([
      [SurveyListFilter.ALL, this.allSurveys.size],
      [SurveyListFilter.RESTRICTED, restrictedCount],
      [SurveyListFilter.UNLISTED, unlistedCount],
      [SurveyListFilter.PUBLIC, publicCount],
    ]);
  }

  applyFilter(): void {
    this.surveys = this.allSurveys.filter(this.filterSurveys.bind(this));
  }

  handleFilterSelection(newFilter: SurveyListFilter): void {
    this.currentFilter = newFilter;
    this.applyFilter();
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

  private filterSurveys(survey: Survey): boolean {
    switch (this.currentFilter) {
      case SurveyListFilter.RESTRICTED:
        return survey.generalAccess === SurveyGeneralAccess.RESTRICTED;
      case SurveyListFilter.UNLISTED:
        return survey.generalAccess === SurveyGeneralAccess.UNLISTED;
      case SurveyListFilter.PUBLIC:
        return survey.generalAccess === SurveyGeneralAccess.PUBLIC;
      default:
        return true;
    }
  }

  private isSetupFinished(survey: Survey): boolean {
    return survey.state === SurveyState.READY;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
