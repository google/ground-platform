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

import {Component} from '@angular/core';
import {List} from 'immutable';
import {Observable} from 'rxjs';
import {map} from 'rxjs/internal/operators/map';

import {Job} from 'app/models/job.model';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';

@Component({
  selector: 'ground-job-list',
  templateUrl: './job-list.component.html',
  styleUrls: ['./job-list.component.scss'],
})
export class JobListComponent {
  readonly jobs$: Observable<List<Job>>;

  constructor(
    readonly surveyService: SurveyService,
    readonly navigationService: NavigationService
  ) {
    this.jobs$ = surveyService
      .getActiveSurvey$()
      .pipe(
        map(survey =>
          List(survey.jobs.valueSeq().toArray()).sortBy(l => l.index)
        )
      );
  }

  isSidePanelExpanded() {
    return this.navigationService.getSidePanelExpanded();
  }
}
