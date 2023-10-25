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

import {Component, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {TasksEditorComponent} from 'app/components/tasks-editor/tasks-editor.component';
import {Task} from 'app/models/task/task.model';
import {List} from 'immutable';

@Component({
  selector: 'task-details',
  templateUrl: './task-details.component.html',
  styleUrls: ['./task-details.component.scss'],
})
export class TaskDetailsComponent {
  tasks = List([]);

  @Input() label?: string;
  @Output() canContinue: EventEmitter<boolean> = new EventEmitter<boolean>();

  constructor() {}

  ngOnInit() {
    this.canContinue.emit(true);
  }

  onChange(valid: boolean): void {
    this.canContinue.emit(valid);
  }

  getTasks(): List<Task> {
    return this.tasksEditor?.toTasks() || List([]);
  }

  @ViewChild('tasksEditor')
  tasksEditor?: TasksEditorComponent;
}
