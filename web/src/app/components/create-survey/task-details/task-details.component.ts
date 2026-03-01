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
  Output,
  ViewChild,
} from '@angular/core';
import { List } from 'immutable';

import { TaskEditorComponent } from 'app/components/shared/task-editor/task-editor.component';
import { Job } from 'app/models/job.model';
import { Task } from 'app/models/task/task.model';

@Component({
  selector: 'task-details',
  templateUrl: './task-details.component.html',
  standalone: false,
})
export class TaskDetailsComponent {
  @Output() onValidationChange: EventEmitter<boolean> =
    new EventEmitter<boolean>();

  @Input() job?: Job;

  tasks: List<Task> = List([]);

  @ViewChild('tasksEditor')
  tasksEditor?: TaskEditorComponent;

  constructor() {}

  ngOnChanges(): void {
    this.tasks = this.job?.getTasksSorted() || List([]);
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
