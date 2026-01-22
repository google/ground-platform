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

import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { List } from 'immutable';
import { Subscription } from 'rxjs';

import { LoiEditorComponent } from 'app/components/shared/loi-editor/loi-editor.component';
import { TasksEditorComponent } from 'app/components/shared/tasks-editor/tasks-editor.component';
import { DataCollectionStrategy, Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { Survey } from 'app/models/survey.model';
import { Task } from 'app/models/task/task.model';
import { LocationOfInterestService } from 'app/services/loi/loi.service';

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
  private _survey?: Survey;
  @Input() set survey(value: Survey | undefined) {
    this._survey = value;
    this.tryUpdateJobState();
  }
  get survey(): Survey | undefined {
    return this._survey;
  }

  private _jobId?: string;
  @Input('id') set jobId(value: string | undefined) {
    this._jobId = value;
    this.tryUpdateJobState();
  }
  get jobId(): string | undefined {
    return this._jobId;
  }

  @Output() updateJob = new EventEmitter<Job>();
  @Output() updateTasks = new EventEmitter<{
    jobId: string;
    tasks: List<Task>;
    valid: boolean;
  }>();

  subscription: Subscription = new Subscription();
  loisSubscription: Subscription = new Subscription();

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
    private loiService: LocationOfInterestService,
    private taskService: TaskService,
    public surveyService: SurveyService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['survey'] || changes['jobId']) && this.survey && this.jobId) {
      this.updateJobState(this.survey, this.jobId);
    }
  }

  private tryUpdateJobState() {
    if (this.survey && this.jobId) {
      this.updateJobState(this.survey, this.jobId);
    }
  }

  // Track last fetching key to avoid redundant re-fetches
  private lastLoiFetchKey: string | null = null;

  private updateJobState(survey: Survey, jobId: string) {
    const job = survey.getJob(jobId);
    if (!job) return;

    // Separate job update from LOI fetching to avoid re-fetching LOIs on every keystroke
    this.job = job;

    const fetchKey = `${survey.id}-${jobId}`;
    if (this.lastLoiFetchKey !== fetchKey) {
      this.lastLoiFetchKey = fetchKey;
      this.loisSubscription.unsubscribe();
      this.loisSubscription = this.loiService
        .getPredefinedLoisByJobId$(survey, job.id)
        .subscribe((lois: List<LocationOfInterest>) => (this.lois = lois));
    }

    // Only set tasks if they have changed conceptually
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
      this.updateTasks.emit({
        jobId: this.jobId,
        tasks: this.tasksEditor.toTasks(),
        valid,
      });
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
      this.updateJob.emit(this.job.copyWith({ tasks, strategy }));
    }
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
    this.loisSubscription.unsubscribe();
  }
}
