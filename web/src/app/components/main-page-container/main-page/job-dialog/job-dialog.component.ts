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

import { CdkDragDrop } from '@angular/cdk/drag-drop';
import {
  ChangeDetectorRef,
  Component,
  Inject,
  OnDestroy,
  QueryList,
  ViewChildren,
} from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { List } from 'immutable';
import { Subscription } from 'rxjs';

import { DataCollectionStrategy, Job } from 'app/models/job.model';
import { Task, TaskType } from 'app/models/task/task.model';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { DialogService } from 'app/services/dialog/dialog.service';
import { JobService } from 'app/services/job/job.service';
import { NavigationService } from 'app/services/navigation/navigation.service';

import { MarkerColorEvent } from './edit-style-button/edit-style-button.component';
import { TaskEditorComponent } from './task-editor/task-editor.component';

// To make ESLint happy:
/*global alert*/

@Component({
    selector: 'ground-job-dialog',
    templateUrl: './job-dialog.component.html',
    styleUrls: ['./job-dialog.component.scss'],
    standalone: false
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
  @ViewChildren(TaskEditorComponent)
  taskEditors?: QueryList<TaskEditorComponent>;

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

  addQuestion() {
    const newTask = this.jobService.createTask(
      TaskType.TEXT,
      /* label= */
      '',
      /* required= */
      false,
      /* index= */
      this.tasks.size
    );
    this.tasks = this.tasks.push(newTask);
    this.markTasksTouched();
    this.focusNewQuestion();
  }

  /**
   * Delete the task of a given index.
   *
   * @param index - The index of the task
   * @returns void
   *
   */
  onTaskDelete(index: number) {
    this.dialogService
      .openConfirmationDialog(
        'Warning',
        'Are you sure you wish to delete this question? Any associated data ' +
          'will be lost. This cannot be undone.'
      )
      .afterClosed()
      .subscribe(dialogResult => {
        if (dialogResult) {
          this.tasks = this.tasks.splice(index, 1);
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
      this.addQuestion();
      return;
    }
    if (this.job?.tasks) {
      this.tasks =
        this.job.tasks.toList().sortBy(task => task.index) || List<Task>();
    } else {
      this.addQuestion();
    }
  }

  async onSave() {
    if (!this.surveyId) {
      throw Error('Survey not yet loaded');
    }

    if (!this.taskEditors) {
      return;
    }

    this.markTasksTouched();

    for (const editor of this.taskEditors) {
      if (editor.taskGroup.invalid || !this.isTaskOptionsValid(editor)) {
        return;
      }
    }
    const job = new Job(
      this.job?.id || '',
      /* index */ this.job?.index || -1,
      this.color,
      this.jobName.trim(),
      this.dataStoreService.convertTasksListToMap(this.tasks),
      DataCollectionStrategy.PREDEFINED
    );
    this.addOrUpdateJob(this.surveyId, job);
  }

  private isTaskOptionsValid(taskEditor: TaskEditorComponent): boolean {
    if (!taskEditor.optionEditors) {
      return true;
    }
    for (const editor of taskEditor.optionEditors) {
      if (editor.optionGroup.invalid) {
        return false;
      }
    }
    return true;
  }

  private addOrUpdateJob(surveyId: string, job: Job) {
    // TODO: Inform user job was saved
    this.jobService
      .addOrUpdateJob(surveyId, job)
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

  /**
   * Updates the task at given index from event emitted from task-editor
   *
   * @param index - The index of the task
   * @param event - updated task emitted from task-editor
   * @returns void
   *
   */
  onTaskUpdate(event: Task, index: number) {
    const taskId = this.tasks.get(index)?.id;
    const task = new Task(
      taskId || '',
      event.type,
      event.label,
      event.required,
      index,
      event.multipleChoice
    );
    this.tasks = this.tasks.set(index, task);
  }

  trackByFn(index: number) {
    return index;
  }

  drop(event: CdkDragDrop<string[]>) {
    const taskAtPrevIndex = this.tasks.get(event.previousIndex);
    if (!taskAtPrevIndex) {
      return;
    }
    this.tasks = this.tasks.delete(event.previousIndex);
    this.tasks = this.tasks.insert(event.currentIndex, taskAtPrevIndex);
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  onMarkerColorChange(event: MarkerColorEvent) {
    this.color = event.color;
  }

  private markTasksTouched(): void {
    this.taskEditors?.forEach(editor => {
      this.markOptionsTouched(editor);
      editor.labelControl.markAsTouched();
    });
  }

  private markOptionsTouched(editor: TaskEditorComponent): void {
    editor.optionEditors?.forEach(editor => {
      editor.optionGroup.markAllAsTouched();
    });
  }

  private focusNewQuestion(): void {
    if (this.taskEditors?.length) {
      this.cdr.detectChanges();
      const question = this.taskEditors.last;
      question?.questionInput?.nativeElement.focus();
    }
  }

  private isTaskOptionsDirty(taskEditor: TaskEditorComponent): boolean {
    if (!taskEditor.optionEditors) {
      return true;
    }
    for (const editor of taskEditor.optionEditors) {
      if (editor.optionGroup.dirty) {
        return false;
      }
    }
    return true;
  }

  private hasUnsavedChanges(): boolean {
    if (!this.taskEditors) {
      return false;
    }
    for (const editor of this.taskEditors) {
      if (editor.taskGroup.dirty || !this.isTaskOptionsDirty(editor)) {
        return true;
      }
    }
    return false;
  }

  private close(): void {
    this.dialogRef.close();
    // TODO: Add closeJobDialog() in NavigationService that removes the job fragment.
    return this.navigationService.selectSurvey(this.surveyId!);
  }
}
