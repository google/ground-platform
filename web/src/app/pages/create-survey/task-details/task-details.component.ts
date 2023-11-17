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

import {CdkDragDrop} from '@angular/cdk/drag-drop';
import {Component, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {List} from 'immutable';

import {TasksEditorComponent} from 'app/components/tasks-editor/tasks-editor.component';
import {Task, TaskType} from 'app/models/task/task.model';
import {DialogService} from 'app/services/dialog/dialog.service';
import {TaskService} from 'app/services/task/task.service';

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
  @Output() onValidationChange: EventEmitter<boolean> =
    new EventEmitter<boolean>();

  tasks: List<Task> = List([]);

  addableTaskGroups: Array<TaskGroup> = [
    TaskGroup.QUESTION,
    TaskGroup.PHOTO,
    TaskGroup.DROP_PIN,
    TaskGroup.DRAW_AREA,
    TaskGroup.CAPTURE_LOCATION,
  ];

  @ViewChild('tasksEditor')
  tasksEditor?: TasksEditorComponent;

  constructor(
    private taskService: TaskService,
    private dialogService: DialogService
  ) {
    this.taskService.getTasks$().subscribe(tasks => {
      this.tasks = tasks;
    });
    this.tasks = List<Task>();
  }

  getIndex(index: number) {
    return index;
  }

  toTasks(): List<Task> {
    this.tasks = this.tasksEditor?.toTasks() || List([]);

    return this.tasks;
  }

  onTasksChange(valid: boolean): void {
    this.onValidationChange.emit(valid);
  }
}
