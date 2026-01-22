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

import { Component, ViewChild, effect } from '@angular/core';
import { ActivatedRoute, Params } from '@angular/router';
import { List } from 'immutable';
import { Subscription } from 'rxjs';

import { EditSurveyComponent } from 'app/components/edit-survey/edit-survey.component';
import { LoiEditorComponent } from 'app/components/shared/loi-editor/loi-editor.component';
import { TasksEditorComponent } from 'app/components/shared/tasks-editor/tasks-editor.component';
import { DataCollectionStrategy, Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { Survey } from 'app/models/survey.model';
import { Task } from 'app/models/task/task.model';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SurveyService } from 'app/services/survey/survey.service';
import { TaskService } from 'app/services/task/task.service';

enum EditJobSection {
  TASKS,
  LOIS,
}

@Component({
  selector: 'edit-job',
  templateUrl: './edit-job.component.html',
  styleUrls: ['./edit-job.component.scss'],
  standalone: false,
})
export class EditJobComponent {
  subscription: Subscription = new Subscription();
  loisSubscription: Subscription = new Subscription();

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
    private editSurveyComponent: EditSurveyComponent
  ) {
    this.subscription.add(
      this.navigationService
        .getSurveyId$()
        .subscribe(surveyId => this.onSurveyIdChange(surveyId))
    );

    effect(() => {
      const survey = this.editSurveyComponent.survey();
      if (this.jobId && survey) {
        this.updateJobState(survey, this.jobId);
      }
    });
  }

  async ngOnInit(): Promise<void> {
    this.subscription.add(
      this.route.params.subscribe(async params => {
        this.jobId = params['id'];
        const survey = this.editSurveyComponent.survey();
        if (survey) {
          this.updateJobState(survey, this.jobId!);
        }
      })
    );
  }

  private onSurveyIdChange(surveyId: string | null) {
    if (surveyId) {
      this.surveyId = surveyId;
    }
  }

  private updateJobState(survey: Survey, jobId: string) {
    const job = survey.getJob(jobId);
    if (!job) return;

    // Check if job actually changed to avoid unnecessary updates/resets
    if (this.job && this.job === job) return;

    this.job = job;

    this.loisSubscription.unsubscribe();
    this.loisSubscription = this.loiService
      .getPredefinedLoisByJobId$(survey, job.id)
      .subscribe((lois: List<LocationOfInterest>) => (this.lois = lois));

    // Only set tasks if they haven't been edited locally.
    // EditJobComponent keeps tasks in `this.tasks`.
    // If the survey updates (e.g. from renaming job), tasks might not change.
    // If we re-assign `this.tasks`, the editor might reset cursor/focus.
    // However, if we added a task via `addOrUpdateTasks` (which calls `draftSurveyService`),
    // the survey update will reflect that.
    const tasks = this.job?.tasks?.toList().sortBy(task => task.index);
    if (this.tasks?.equals(tasks)) return;
    this.tasks = tasks;
  }

  onSectionChange(section: EditJobSection) {
    if (this.tasksEditor && section === EditJobSection.LOIS) {
      this.tasks = this.tasksEditor.toTasks();
    }
    this.section = section;
  }

  onTasksChange(valid: boolean): void {
    if (this.jobId && this.tasksEditor) {
      this.editSurveyComponent.addOrUpdateTasks(
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

    if (this.job) {
      this.editSurveyComponent.addOrUpdateJob(
        this.job.copyWith({ tasks, strategy })
      );
    }
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
    this.loisSubscription.unsubscribe();
  }
}
