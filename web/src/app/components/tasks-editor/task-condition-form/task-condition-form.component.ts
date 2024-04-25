/**
 * Copyright 2024 The Ground Authors.
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

import {Component, Input, Pipe, PipeTransform} from '@angular/core';
import {AbstractControl, FormArray, FormGroup} from '@angular/forms';
import {List} from 'immutable';

import {Option} from 'app/models/task/option.model';
import {Task} from 'app/models/task/task.model';

@Pipe({name: 'filterPreviousTasks', pure: false})
export class PreviousTaskPipe implements PipeTransform {
  transform(tasks: List<Task>, filter: number): List<Task> {
    return tasks.filter(task => task.multipleChoice && task.index < filter);
  }
}

@Pipe({name: 'getTaskOptions', pure: false})
export class TaskOptionsPipe implements PipeTransform {
  transform(tasks: List<Task>, filter: string): List<Option> {
    return (
      tasks.find(task => task.id === filter)?.multipleChoice?.options ||
      List([])
    );
  }
}

@Component({
  selector: 'ground-task-condition-form',
  templateUrl: './task-condition-form.component.html',
  styleUrls: ['./task-condition-form.component.scss'],
})
export class TaskConditionFormComponent {
  @Input() formGroup!: FormGroup;
  @Input() index!: number;
  @Input() tasks!: List<Task>;

  get exppressionsControl(): FormArray {
    return this.formGroup.get('expressions') as FormArray;
  }

  get expressionControl(): AbstractControl {
    return this.exppressionsControl.at(0);
  }

  get taskIdControl(): AbstractControl {
    return this.expressionControl.get('taskId')!;
  }
}
