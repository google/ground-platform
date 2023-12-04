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
import {Task, TaskType} from 'app/models/task/task.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {DialogService} from 'app/services/dialog/dialog.service';
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
  selector: 'tasks-editor',
  templateUrl: './tasks-editor.component.html',
  styleUrls: ['./tasks-editor.component.scss'],
})
export class TasksEditorComponent {
  formGroup!: FormGroup;

  @Input() label?: string;
  @Input() tasks?: List<Task>;
  @Output() onValidationChanges: EventEmitter<boolean> =
    new EventEmitter<boolean>();
  @Output() onValueChanges: EventEmitter<boolean> = new EventEmitter<boolean>();

  addableTaskGroups: Array<TaskGroup> = [
    TaskGroup.QUESTION,
    TaskGroup.PHOTO,
    TaskGroup.DROP_PIN,
    TaskGroup.DRAW_AREA,
    TaskGroup.CAPTURE_LOCATION,
  ];

  constructor(
    private dataStoreService: DataStoreService,
    private dialogService: DialogService,
    private formBuilder: FormBuilder
  ) {}

  private initForm() {
    this.formGroup = this.formBuilder.group({
      tasks: this.formBuilder.array(
        this.tasks?.toArray().map(task =>
          this.formBuilder.group({
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
          })
        ) || []
      ),
    });

    this.formGroup.statusChanges.subscribe(_ => {
      this.onValidationChanges.emit(this.formGroup?.valid);
    });

    this.formGroup.valueChanges.subscribe(_ => {
      this.onValueChanges.emit(this.formGroup?.valid);
    });
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
          const formGroupToDuplicate = this.formArray.controls[index];

          const formGroup = this.formBuilder.group({
            id: this.dataStoreService.generateId(),
            type: formGroupToDuplicate.get('type')?.value,
            required: formGroupToDuplicate.get('required')?.value,
            label: [
              formGroupToDuplicate.get('label')?.value,
              Validators.required,
            ],
            cardinality: formGroupToDuplicate.get('cardinality')?.value,
            options: (formGroupToDuplicate.get('options') as FormArray)
              .controls,
          });

          this.formArray.push(formGroup);
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

  toTasks(): List<Task> {
    return List(
      this.formArray.controls.map((task: AbstractControl, i: number) => {
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

        const hasOtherOption = !!task.get('hasOtherOption')?.value;

        return {
          id: task.get('id')?.value as string,
          type: task.get('type')?.value as TaskType,
          required: task.get('required')?.value as boolean,
          label: task.get('label')?.value as string,
          index: i,
          multipleChoice: cardinality
            ? ({
                cardinality,
                options,
                hasOtherOption,
              } as MultipleChoice)
            : undefined,
        } as Task;
      })
    );
  }
}
