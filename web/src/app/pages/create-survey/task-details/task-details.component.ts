/**
 * Copyright 2023 Google LLC
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
import {Task, TaskType} from 'app/models/task/task.model';
import {DialogService} from 'app/services/dialog/dialog.service';
import {TaskService} from 'app/services/task/task.service';
import {List} from 'immutable';

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
  selector: 'task-details',
  templateUrl: './task-details.component.html',
  styleUrls: ['./task-details.component.scss'],
})
export class TaskDetailsComponent {
  tasks: List<Task>;

  TaskGroup = TaskGroup;

  constructor(
    private taskService: TaskService,
    private dialogService: DialogService
  ) {
    this.taskService.getTasks$().subscribe(tasks => {
      this.tasks = tasks;
    });
    this.tasks = List<Task>();
  }

  onTaskAdd(group: TaskGroup) {
    const types = taskGroupToTypes.get(group);

    const type = types?.first();

    if (type) {
      const task = this.taskService.createTask(
        type,
        '',
        false,
        this.tasks.size
      );

      this.tasks = this.tasks.push(task);
    }
  }

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

  getIndex(index: number) {
    return index;
  }

  toTasks(): List<Task> {
    return this.tasks;
  }
}
