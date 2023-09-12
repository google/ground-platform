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
import {NavigationService} from 'app/services/navigation/navigation.service';
import {Survey} from 'app/models/survey.model';
import {Job} from 'app/models/job.model';
import {filter, first} from 'rxjs';

@Component({
  selector: 'edit-survey',
  templateUrl: './edit-survey.component.html',
  styleUrls: ['./edit-survey.component.scss'],
})
export class EditSurveyComponent implements OnInit {
  currentSurveyId?: string;
  currentSurvey?: Survey;

  constructor(
    private surveyService: SurveyService,
    private navigationService: NavigationService,
    route: ActivatedRoute
  ) {
    navigationService.init(route);
  }

  ngOnInit(): void {
    this.navigationService.getSurveyId$().subscribe(surveyId => {
      if (surveyId) {
        this.currentSurveyId = surveyId;
        this.surveyService.activateSurvey(surveyId);
      }
    });
    this.surveyService
      .getActiveSurvey$()
      .pipe(
        filter(survey => survey.id === this.currentSurveyId),
        first()
      )
      .subscribe(survey => (this.currentSurvey = survey));
  }

  jobs(): Job[] {
    return Array.from(this.currentSurvey?.jobs.values() ?? []);
  }

  jobRouterLink(jobId: string): string[] {
    return [`./job/${jobId}`];
  }

  onMenu(e: Event): void {
    e.preventDefault();
    e.stopImmediatePropagation();
  }
}
