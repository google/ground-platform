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

import {Component, ViewChild} from '@angular/core';
import {ActivatedRoute, Params} from '@angular/router';
import {List} from 'immutable';
import {Subscription, firstValueFrom} from 'rxjs';

import {LoiSelectionComponent} from 'app/components/loi-selection/loi-selection.component';
import {TasksEditorComponent} from 'app/components/tasks-editor/tasks-editor.component';
import {LocationOfInterest} from 'app/models/loi.model';
import {Task} from 'app/models/task/task.model';
import {DraftSurveyService} from 'app/services/draft-survey/draft-survey.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';

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
  lois!: List<LocationOfInterest>;
  isLoading = true;

  tasks?: List<Task>;

  @ViewChild('tasksEditor')
  tasksEditor?: TasksEditorComponent;

  @ViewChild('loiSelection')
  loiSelection?: LoiSelectionComponent;

  constructor(
    private route: ActivatedRoute,
    private navigationService: NavigationService,
    private loiService: LocationOfInterestService,
    public surveyService: SurveyService,
    public draftSurveyService: DraftSurveyService
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
    this.isLoading = true;

    this.jobId = params['id'];

    this.tasks = this.draftSurveyService
      .getSurvey()
      .getJob(this.jobId!)
      ?.tasks?.toList()
      .sortBy(task => task.index);

    this.lois = await firstValueFrom(
      this.loiService.getLoisByJobId$(this.jobId!)
    );

    this.isLoading = false;
  }

  getIndex(index: number) {
    return index;
  }

  onChangeSection(section: 'tasks' | 'lois') {
    this.section = section;
  }

  onTasksChange(valid: boolean): void {
    if (this.jobId && valid) {
      this.tasks = this.tasksEditor?.toTasks() || List([]);

      this.draftSurveyService.addOrUpdateTasks(this.jobId, this.tasks);
    }
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
