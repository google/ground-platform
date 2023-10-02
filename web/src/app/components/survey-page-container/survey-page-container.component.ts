/**
 * Copyright 2020 The Ground Authors.
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

import {NavigationService} from 'app/services/navigation/navigation.service';
import {Observable, Subscription} from 'rxjs';
import {Survey} from 'app/models/survey.model';
import {SurveyService} from 'app/services/survey/survey.service';
import {Component, OnInit, OnDestroy} from '@angular/core';

@Component({
  selector: 'ground-survey-page-container',
  templateUrl: './survey-page-container.component.html',
  styleUrls: ['./survey-page-container.component.css'],
})
export class SurveyPageContainerComponent implements OnInit, OnDestroy {
  activeSurvey$: Observable<Survey | null>;
  private subscription = new Subscription();

  constructor(
    private navigationService: NavigationService,
    private surveyService: SurveyService
  ) {
    this.activeSurvey$ = surveyService.getActiveSurvey$();
  }
}
