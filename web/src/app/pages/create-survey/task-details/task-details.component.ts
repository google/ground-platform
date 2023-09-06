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

import { Component } from '@angular/core';
import { Task, TaskType } from 'app/models/task/task.model';
import { DialogService } from 'app/services/dialog/dialog.service';
import { TaskService, TaskUpdate } from 'app/services/task/task.service';
import { List } from 'immutable';

export enum TaskGroup {
  QUESTION = 1,
  PHOTO = 2,
  DROP_PIN = 3,
  DRAW_AREA = 4,
  CAPTURE_LOCATION = 5,
}

@Component({
  selector: 'task-details',
  templateUrl: './task-details.component.html',
  styleUrls: ['./task-details.component.scss'],
})
export class TaskDetailsComponent {
  tasks: List<Task>;

  constructor(
    private taskService: TaskService,
    private dialogService: DialogService,
  ) {
    this.taskService.getTasks$().subscribe(tasks => {
      this.tasks = tasks;
    });
    this.tasks = List<Task>();
  }

  onQuestionAdd() {
    let task = this.taskService.createTask(
      TaskType.TEXT,
      /* label= */
      '',
      /* required= */
      false,
      /* index= */
      this.tasks.size
    )
    this.tasks = this.tasks.push(task);
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

  taskGroupQuestion(): TaskGroup {
    return TaskGroup.QUESTION;
  }

  trackByFn(index: number) {
    return index;
  }

  toTasks(): List<Task> {
    return this.tasks;
  }
}
