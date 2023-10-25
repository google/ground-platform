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

import {Task} from 'app/models/task/task.model';
import {DialogService} from 'app/services/dialog/dialog.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {TaskService} from 'app/services/task/task.service';
import {List} from 'immutable';
import {Subscription, firstValueFrom, map} from 'rxjs';
import {Component, ViewChild} from '@angular/core';
import {ActivatedRoute, Params} from '@angular/router';
import {LoiSelectionComponent} from 'app/pages/create-survey/loi-selection/loi-selection.component';

@Component({
  selector: 'edit-job',
  templateUrl: './edit-job.component.html',
  styleUrls: ['./edit-job.component.scss'],
})
export class EditJobComponent {
  subscription: Subscription = new Subscription();

  surveyId?: string;
  jobId?: string;
  section: 'tasks' | 'lois' = 'tasks';
  tasks?: List<Task>;

  @ViewChild('loiSelection')
  loiSelection?: LoiSelectionComponent;

  constructor(
    private route: ActivatedRoute,
    private navigationService: NavigationService,
    private dialogService: DialogService,
    private surveyService: SurveyService,
    private taskService: TaskService
  ) {
    this.subscription.add(
      this.navigationService
        .getSurveyId$()
        .subscribe(surveyId => this.onSurveyIdChange(surveyId))
    );
  }

  async ngOnInit(): Promise<void> {
    this.subscription.add(
      this.route.params.subscribe(async params => {
        await this.onJobIdChange(params);
      })
    );
  }

  private onSurveyIdChange(surveyId: string | null) {
    if (surveyId) {
      this.surveyId = surveyId;
    }
  }

  private async onJobIdChange(params: Params) {
    this.jobId = params['id'];

    this.tasks = await firstValueFrom(
      this.surveyService.getActiveSurvey$().pipe(
        map(survey =>
          survey
            .getJob(this.jobId!)
            ?.tasks?.toList()
            .sortBy(task => task.index)
        )
      )
    );
  }

  getIndex(index: number) {
    return index;
  }

  onChangeSection(section: 'tasks' | 'lois') {
    this.section = section;
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
