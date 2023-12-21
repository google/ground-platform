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

import {Component} from '@angular/core';
import {List, Map} from 'immutable';
import {Observable} from 'rxjs';

import {DataCollectionStrategy, Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {Survey} from 'app/models/survey.model';
import {Task} from 'app/models/task/task.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {TaskService} from 'app/services/task/task.service';

@Component({
  selector: 'survey-loi',
  templateUrl: './survey-loi.component.html',
  styleUrls: ['./survey-loi.component.scss'],
})
export class SurveyLoiComponent {
  lois$!: Observable<List<LocationOfInterest>>;

  job?: Job;

  constructor(
    readonly dataStoreService: DataStoreService,
    readonly taskService: TaskService,
    readonly loiService: LocationOfInterestService,
    readonly surveyService: SurveyService
  ) {}

  ngOnInit() {
    this.lois$ = this.loiService.getLoisWithLabels$();

    this.job = this.surveyService.getActiveSurvey().jobs.first();

    this.onStrategyChange(this.job?.strategy);
  }

  onStrategyChange(strategy?: DataCollectionStrategy) {
    if (this.job) {
      const tasks =
        strategy === DataCollectionStrategy.AD_HOC
          ? this.taskService.addLoiTask(this.job?.tasks || Map<string, Task>())
          : this.taskService.removeLoiTask(
              this.job?.tasks || Map<string, Task>()
            );

      this.dataStoreService.addOrUpdateJob(
        this.surveyService.getActiveSurvey().id,
        this.job.copyWith({tasks, strategy})
      );
    }
  }
}
