/**
 * Copyright 2019 The Ground Authors.
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
  ChangeDetectorRef,
  Component,
  Inject,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { List } from 'immutable';
import { Subscription, firstValueFrom } from 'rxjs';

import { TaskEditorComponent } from 'app/components/shared/task-editor/task-editor.component';
import { DataCollectionStrategy, Job } from 'app/models/job.model';
import { Task, TaskType } from 'app/models/task/task.model';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { DialogService } from 'app/services/dialog/dialog.service';
import { JobService } from 'app/services/job/job.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SurveyService } from 'app/services/survey/survey.service';

import { MarkerColorEvent } from './edit-style-button/edit-style-button.component';

// To make ESLint happy:
/*global alert*/

@Component({
  selector: 'ground-job-dialog',
  templateUrl: './job-dialog.component.html',
  styleUrls: ['./job-dialog.component.scss'],
  standalone: false,
})
export class JobDialogComponent implements OnDestroy {
  job?: Job;
  jobName!: string;
  surveyId?: string;
  subscription: Subscription = new Subscription();
  taskTypes = TaskType;
  tasks: List<Task>;
  color!: string;
  defaultJobColor: string;
  @ViewChild('tasksEditor')
  tasksEditor?: TaskEditorComponent;

  constructor(
    @Inject(MAT_DIALOG_DATA)
    data: {
      surveyId: string;
      job?: Job;
      createJob: boolean;
    },
    private dialogRef: MatDialogRef<JobDialogComponent>,
    private dialogService: DialogService,
    private jobService: JobService,
    private dataStoreService: DataStoreService,
    private navigationService: NavigationService,
    private surveyService: SurveyService,
    private readonly cdr: ChangeDetectorRef
  ) {
    this.defaultJobColor = '#ff9131';
    // Disable closing on clicks outside of dialog.
    dialogRef.disableClose = true;
    this.tasks = List<Task>();
    this.init(data.surveyId, data.createJob, data.job);
    this.dialogRef.keydownEvents().subscribe(event => {
      if (event.key === 'Escape') {
        this.close();
      }
    });
  }

  init(surveyId: string, createJob: boolean, job?: Job) {
    if (!createJob && !job) {
      console.warn('User passed an invalid job id');
    }
    this.surveyId = surveyId;
    this.job = job;
    this.jobName = this.job?.name || '';
    this.color = this.job?.color || this.defaultJobColor;
    if (!job) {
      this.job = this.jobService.createNewJob();
    }
    if (this.job?.tasks) {
      this.tasks = this.job.tasks.toList().sortBy(task => task.index);
    }
  }

  async onSave() {
    if (!this.surveyId) {
      throw Error('Survey not yet loaded');
    }

    if (!this.tasksEditor) {
      return;
    }

    if (this.tasksEditor.formGroup.invalid) {
      this.tasksEditor.formGroup.markAllAsTouched();
      return;
    }
    // We get tasks from the editor to ensure we have the latest edits
    const tasks = this.tasksEditor.toTasks();

    const job = new Job(
      this.job?.id || '',
      /* index */ this.job?.index || -1,
      this.color,
      this.jobName.trim(),
      this.dataStoreService.convertTasksListToMap(tasks),
      DataCollectionStrategy.PREDEFINED
    );
    this.addOrUpdateJob(this.surveyId, job);
  }

  private async addOrUpdateJob(surveyId: string, job: Job) {
    const survey = await firstValueFrom(
      this.surveyService.loadSurvey$(surveyId)
    );
    // TODO: Inform user job was saved
    this.jobService
      .addOrUpdateJob(survey, job)
      .then(() => this.close())
      .catch(err => {
        console.error(err);
        alert('Job update failed.');
      });
  }

  onCancel(): void {
    if (!this.hasUnsavedChanges()) {
      this.close();
      return;
    }
    this.dialogService
      .openConfirmationDialog(
        'Discard changes',
        'Unsaved changes to this job will be lost. Are you sure?'
      )
      .afterClosed()
      .subscribe(async dialogResult => {
        if (dialogResult) {
          this.close();
        }
      });
  }

  setJobName(value: string) {
    this.jobName = value;
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  onMarkerColorChange(event: MarkerColorEvent) {
    this.color = event.color;
  }

  private hasUnsavedChanges(): boolean {
    if (!this.tasksEditor) {
      return false;
    }
    return this.tasksEditor.formGroup.dirty;
  }

  private close(): void {
    this.dialogRef.close();
    // TODO: Add closeJobDialog() in NavigationService that removes the job fragment.
    return this.navigationService.selectSurvey(this.surveyId!);
  }
}
