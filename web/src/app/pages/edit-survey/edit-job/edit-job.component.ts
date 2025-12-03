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
import {toObservable} from '@angular/core/rxjs-interop';
import {ActivatedRoute, Params} from '@angular/router';
import {List} from 'immutable';
import {Subscription, map, Observable} from 'rxjs';

import {LoiEditorComponent} from 'app/components/loi-editor/loi-editor.component';
import {TasksEditorComponent} from 'app/components/tasks-editor/tasks-editor.component';
import {DataCollectionStrategy, Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {Task} from 'app/models/task/task.model';
import {DraftSurveyService} from 'app/services/draft-survey/draft-survey.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {TaskService} from 'app/services/task/task.service';

enum EditJobSection {
  TASKS,
  LOIS,
}

@Component({
  selector: 'edit-job',
  templateUrl: './edit-job.component.html',
  styleUrls: ['./edit-job.component.scss'],
})
export class EditJobComponent {
  subscription: Subscription = new Subscription();
  loisSubscription: Subscription = new Subscription();

  surveyId$: Observable<string | null>;
  surveyId?: string;
  jobId?: string;

  section: EditJobSection = EditJobSection.TASKS;

  job?: Job;
  tasks?: List<Task>;
  addLoiTaskId?: string;
  lois!: List<LocationOfInterest>;

  EditJobSection = EditJobSection;

  @ViewChild('tasksEditor')
  tasksEditor?: TasksEditorComponent;

  @ViewChild('loiEditor')
  loiEditor?: LoiEditorComponent;

  constructor(
    private route: ActivatedRoute,
    private navigationService: NavigationService,
    private loiService: LocationOfInterestService,
    private taskService: TaskService,
    public surveyService: SurveyService,
    public draftSurveyService: DraftSurveyService
  ) {
    this.surveyId$ = toObservable(this.navigationService.getUrlParams()).pipe(
      map(params => params.surveyId)
    );
  }

  async ngOnInit(): Promise<void> {
    this.subscription.add(
      this.surveyId$.subscribe(surveyId => this.onSurveyIdChange(surveyId))
    );
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

  private onJobIdChange(params: Params) {
    this.jobId = params['id'];

    this.job = this.draftSurveyService.getSurvey().getJob(this.jobId!);

    this.loisSubscription.add(
      this.loiService
        .getPredefinedLoisByJobId$(this.job!.id)
        .subscribe((lois: List<LocationOfInterest>) => (this.lois = lois))
    );

    this.tasks = this.job?.tasks?.toList().sortBy(task => task.index);
  }

  onSectionChange(section: EditJobSection) {
    if (this.tasksEditor && section === EditJobSection.LOIS) {
      this.tasks = this.tasksEditor.toTasks();
    }
    this.section = section;
  }

  onTasksChange(valid: boolean): void {
    if (this.jobId && this.tasksEditor) {
      this.draftSurveyService.addOrUpdateTasks(
        this.jobId,
        this.tasksEditor.toTasks(),
        valid
      );
    }
  }

  onStrategyChange(strategy: DataCollectionStrategy) {
    const addLoiTask = this.job?.tasks?.find(task => !!task.addLoiTask);

    if (addLoiTask) this.addLoiTaskId = addLoiTask.id;

    const tasks = this.taskService.updateLoiTasks(
      this.job?.tasks,
      strategy,
      this.addLoiTaskId
    );

    this.draftSurveyService.addOrUpdateJob(
      this.job!.copyWith({tasks, strategy})
    );

    this.job = this.draftSurveyService.getSurvey().getJob(this.jobId!);

    this.tasks = tasks?.toList().sortBy(task => task.index);
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();

    this.loisSubscription.unsubscribe();
  }
}
