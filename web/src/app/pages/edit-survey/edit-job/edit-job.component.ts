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

import {Component, EventEmitter, Input, Output} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {Task, TaskType} from 'app/models/task/task.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {DialogService} from 'app/services/dialog/dialog.service';
import {JobService} from 'app/services/job/job.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {TaskService} from 'app/services/task/task.service';
import {List} from 'immutable';
import {Observable, filter, firstValueFrom, map} from 'rxjs';

export enum TaskGroup {
  QUESTION = 1,
  PHOTO = 2,
  DROP_PIN = 3,
  DRAW_AREA = 4,
  CAPTURE_LOCATION = 5,
  SUGGEST_LOI = 6,
}

export const taskGroupToTypes = new Map([
  [TaskGroup.QUESTION, List([TaskType.TEXT, TaskType.DATE])],
  [TaskGroup.PHOTO, List([TaskType.PHOTO])],
  [TaskGroup.DROP_PIN, List([TaskType.DROP_PIN])],
  [TaskGroup.DRAW_AREA, List([TaskType.DRAW_AREA])],
  [TaskGroup.SUGGEST_LOI, List([TaskType.DROP_PIN])],
]);

export const taskTypeToGroup = new Map([
  [TaskType.TEXT, TaskGroup.QUESTION],
  [TaskType.DATE, TaskGroup.QUESTION],
  [TaskType.PHOTO, TaskGroup.PHOTO],
  [TaskType.DRAW_AREA, TaskGroup.DRAW_AREA],
  [TaskType.DROP_PIN, TaskGroup.DROP_PIN],
]);

@Component({
  selector: 'edit-job',
  templateUrl: './edit-job.component.html',
  styleUrls: ['./edit-job.component.scss'],
})
export class EditJobComponent {
  surveyId?: string;
  jobId?: string;
  tasks?: List<Task>;
  TaskGroup = TaskGroup;

  constructor(
    route: ActivatedRoute,
    private navigationService: NavigationService,
    private dialogService: DialogService,
    private surveyService: SurveyService,
    private taskService: TaskService
  ) {
    this.navigationService.getSurveyId$().subscribe(surveyId => {
      if (surveyId) {
        route.params.subscribe(params => {
          this.surveyId = surveyId;
          this.jobId = params['id'];
        });
      }
    });
  }

  async ngOnInit(): Promise<void> {
    this.tasks = await firstValueFrom(
      this.surveyService
        .getActiveSurvey$()
        .pipe(filter(survey => survey.id === this.surveyId))
        .pipe(
          map(survey =>
            survey
              .getJob(this.jobId!)
              ?.tasks?.toList()
          )
        )
    );

    this.tasks = this.tasks!.sort((t1, t2) => t1.index-t2.index)
  }

  getIndex(index: number) {
    return index;
  }

  onTaskAdd(group: TaskGroup) {
    const types = taskGroupToTypes.get(group);

    const type = types?.first();

    if (type && this.tasks) {
      const task = this.taskService.createTask(
        type,
        '',
        false,
        this.tasks.size
      );

      this.tasks = this.tasks.push(task);
    }

    // No need to call addOrUpdateTasks because ngOnChange emits an update once
    // the task-input component is created
  }

  onTaskUpdate(event: Task, index: number) {
    if (!this.tasks) {
      throw Error('tasks list is is empty');
    }

    this.tasks = this.tasks.set(index, event);
    this.taskService.addOrUpdateTasks(this.surveyId!, this.jobId!, this.tasks);
  }

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
          this.tasks = this.tasks!.splice(index, 1);
        }
      });
  }
}
