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
import {Component, EventEmitter, Input, Output} from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import {List} from 'immutable';

import {
  Cardinality,
  MultipleChoice,
} from 'app/models/task/multiple-choice.model';
import {Option} from 'app/models/task/option.model';
import {
  TaskCondition,
  TaskConditionExpression,
  TaskConditionExpressionType,
} from 'app/models/task/task-condition.model';
import {Task, TaskType} from 'app/models/task/task.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {DialogService} from 'app/services/dialog/dialog.service';
import {TaskService} from 'app/services/task/task.service';
import {moveItemInFormArray} from 'app/utils/utils';

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
    List([
      TaskType.TEXT,
      TaskType.MULTIPLE_CHOICE,
      TaskType.NUMBER,
      TaskType.DATE,
      TaskType.TIME,
      TaskType.DATE_TIME,
    ]),
  ],
  [TaskGroup.PHOTO, List([TaskType.PHOTO])],
  [TaskGroup.DROP_PIN, List([TaskType.DROP_PIN])],
  [TaskGroup.DRAW_AREA, List([TaskType.DRAW_AREA])],
  [TaskGroup.CAPTURE_LOCATION, List([TaskType.CAPTURE_LOCATION])],
]);

export const taskTypeToGroup = new Map([
  [TaskType.TEXT, TaskGroup.QUESTION],
  [TaskType.MULTIPLE_CHOICE, TaskGroup.QUESTION],
  [TaskType.NUMBER, TaskGroup.QUESTION],
  [TaskType.DATE, TaskGroup.QUESTION],
  [TaskType.TIME, TaskGroup.QUESTION],
  [TaskType.DATE_TIME, TaskGroup.QUESTION],
  [TaskType.PHOTO, TaskGroup.PHOTO],
  [TaskType.DROP_PIN, TaskGroup.DROP_PIN],
  [TaskType.DRAW_AREA, TaskGroup.DRAW_AREA],
  [TaskType.CAPTURE_LOCATION, TaskGroup.CAPTURE_LOCATION],
]);

@Component({
  selector: 'tasks-editor',
  templateUrl: './tasks-editor.component.html',
  styleUrls: ['./tasks-editor.component.scss'],
})
export class TasksEditorComponent {
  formGroup!: FormGroup;

  @Input() tasks?: List<Task>;
  @Output() onValidationChanges: EventEmitter<boolean> =
    new EventEmitter<boolean>();
  @Output() onValueChanges: EventEmitter<boolean> = new EventEmitter<boolean>();

  addableTaskGroups: Array<TaskGroup> = [
    TaskGroup.QUESTION,
    TaskGroup.PHOTO,
    TaskGroup.CAPTURE_LOCATION,
  ];

  constructor(
    private dataStoreService: DataStoreService,
    private dialogService: DialogService,
    private taskService: TaskService,
    private formBuilder: FormBuilder
  ) {}

  multipleChoiceTasks = List<Task>();

  ngOnChanges(): void {
    this.formGroup = this.formBuilder.group({
      tasks: this.formBuilder.array(
        this.tasks?.toArray().map((task: Task) => this.toControl(task)) || [],
        Validators.required
      ),
    }) as FormGroup;

    this.formGroup.statusChanges.subscribe(_ => {
      this.onValidationChanges.emit(this.formGroup?.valid);
    });

    this.formGroup.valueChanges.subscribe(_ => {
      this.multipleChoiceTasks = this.toTasks();

      this.onValueChanges.emit(this.formGroup?.valid);
    });

    this.onValidationChanges.emit(this.formGroup?.valid);

    this.multipleChoiceTasks = this.toTasks();
  }

  get formArray() {
    return this.formGroup.get('tasks') as FormArray;
  }

  onTaskAdd(group: TaskGroup) {
    const types = taskGroupToTypes.get(group);

    const formGroup = this.formBuilder.group({
      id: this.dataStoreService.generateId(),
      type: types?.first(),
      required: false,
      label: ['', Validators.required],
      cardinality: null,
      options: this.formBuilder.array([]),
      hasOtherOption: false,
      addLoiTask: false,
    });

    this.formArray.push(formGroup);
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
          this.formArray.removeAt(index);
        }
      });
  }

  onTaskDuplicate(index: number) {
    this.dialogService
      .openConfirmationDialog(
        'Duplicate task',
        'Are you sure you wish to duplicate this task?'
      )
      .afterClosed()
      .subscribe(dialogResult => {
        if (dialogResult) {
          const task = this.toTask(index);

          const newTask = this.taskService.duplicateTask(task);

          const control = this.toControl(newTask);

          this.formArray.push(control);
        }
      });
  }

  onTaskDrop(event: CdkDragDrop<string[]>): void {
    moveItemInFormArray(
      this.formArray,
      event.previousIndex,
      event.currentIndex
    );
  }

  toControl(task: Task): FormGroup {
    const control = this.formBuilder.group({
      id: task.id,
      type: task.type,
      required: task.required,
      label: [task.label, Validators.required],
      cardinality: task.multipleChoice?.cardinality,
      options: this.formBuilder.array(
        task.multipleChoice?.options.toArray().map(option =>
          this.formBuilder.group({
            id: option.id,
            label: option.label,
            code: option.code,
          })
        ) || []
      ),
      hasOtherOption: task.multipleChoice?.hasOtherOption,
      addLoiTask: task.addLoiTask,
    }) as FormGroup;

    if (task.condition) {
      control.addControl(
        'condition',
        this.formBuilder.group({
          matchType: task.condition.matchType,
          expressions: this.formBuilder.array(
            task.condition?.expressions?.toArray().map(expression =>
              this.formBuilder.group({
                expressionType: expression.expressionType,
                taskId: [expression.taskId, Validators.required],
                optionIds:
                  [expression.optionIds?.toArray(), Validators.required] || [],
              })
            ) || []
          ),
        })
      );
    }

    return control;
  }

  toTask(index: number): Task {
    const task = this.formArray.controls[index];

    const cardinality = task.get('cardinality')?.value as Cardinality;

    const options = List(
      (task.get('options') as FormArray).controls.map(
        (option: AbstractControl, k: number) =>
          ({
            id: option.get('id')?.value as string,
            label: option.get('label')?.value as string,
            code: option.get('code')?.value as string,
            index: k,
          } as Option)
      )
    );

    const condition = task.get('condition') as FormGroup;

    return {
      id: task.get('id')?.value as string,
      type: task.get('type')?.value as TaskType,
      required: task.get('required')?.value as boolean,
      label: task.get('label')?.value as string,
      index,
      multipleChoice: cardinality
        ? ({
            cardinality,
            hasOtherOption: task.get('hasOtherOption')?.value as boolean,
            options,
          } as MultipleChoice)
        : undefined,
      addLoiTask: task.get('addLoiTask')?.value as boolean,
      condition: condition?.value
        ? ({
            matchType: condition.get('matchType')?.value,
            expressions: List(
              (condition.get('expressions') as FormArray).controls.map(
                (expression: AbstractControl) =>
                  ({
                    expressionType: expression.get('expressionType')
                      ?.value as TaskConditionExpressionType,
                    taskId: expression.get('taskId')?.value as string,
                    optionIds: List(
                      expression.get('optionIds')?.value as string[]
                    ),
                  } as TaskConditionExpression)
              )
            ),
          } as TaskCondition)
        : undefined,
    } as Task;
  }

  toTasks(): List<Task> {
    return List(this.formArray.controls.map((_, i: number) => this.toTask(i)));
  }
}
