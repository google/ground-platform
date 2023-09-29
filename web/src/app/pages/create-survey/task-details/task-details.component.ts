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
}

export const taskGroupToTypes = new Map([
  [
    TaskGroup.QUESTION,
    List([TaskType.TEXT, TaskType.DATE, TaskType.MULTIPLE_CHOICE]),
  ],
  [TaskGroup.PHOTO, List([TaskType.PHOTO])],
  [TaskGroup.DROP_PIN, List([TaskType.DROP_PIN])],
  [TaskGroup.DRAW_AREA, List([TaskType.DRAW_AREA])],
  [TaskGroup.CAPTURE_LOCATION, List([TaskType.CAPTURE_LOCATION])],
]);

export const taskTypeToGroup = new Map([
  [TaskType.TEXT, TaskGroup.QUESTION],
  [TaskType.DATE, TaskGroup.QUESTION],
  [TaskType.MULTIPLE_CHOICE, TaskGroup.QUESTION],
  [TaskType.PHOTO, TaskGroup.PHOTO],
  [TaskType.DROP_PIN, TaskGroup.DROP_PIN],
  [TaskType.DRAW_AREA, TaskGroup.DRAW_AREA],
  [TaskType.CAPTURE_LOCATION, TaskGroup.CAPTURE_LOCATION],
]);

@Component({
  selector: 'task-details',
  templateUrl: './task-details.component.html',
  styleUrls: ['./task-details.component.scss'],
})
export class TaskDetailsComponent {
  @Input() label?: string;

  tasks: List<Task>;

  addableTaskGroups: Array<TaskGroup> = [
    TaskGroup.QUESTION,
    TaskGroup.PHOTO,
    TaskGroup.DROP_PIN,
    TaskGroup.DRAW_AREA,
    TaskGroup.CAPTURE_LOCATION,
  ];

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

  onDuplicateTask(index: number) {
    this.dialogService
      .openConfirmationDialog(
        'Duplicate task',
        'Are you sure you wish to duplicate this task?'
      )
      .afterClosed()
      .subscribe(dialogResult => {
        if (dialogResult) {
          const taskToDuplicate = this.tasks.get(index);
          if (taskToDuplicate) {
            const task = this.taskService.createTask(
              taskToDuplicate?.type,
              taskToDuplicate?.label,
              taskToDuplicate?.required,
              this.tasks.size
            );
            this.tasks = this.tasks.push(task);
          }
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
