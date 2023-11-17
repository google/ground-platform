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
import {
  Component,
  EventEmitter,
  HostListener,
  Input,
  Output,
} from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormGroup,
} from '@angular/forms';
import {firstValueFrom} from 'rxjs';

import {Cardinality} from 'app/models/task/multiple-choice.model';
import {TaskType} from 'app/models/task/task.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {DialogService} from 'app/services/dialog/dialog.service';
import {moveItemInFormArray} from 'app/utils/utils';

import {TaskGroup, taskTypeToGroup} from '../tasks-editor.component';

export interface TaskTypeOption {
  icon: string;
  label: string;
  type: TaskType;
  cardinality?: Cardinality;
}

export const TaskTypeOptions: Array<TaskTypeOption> = [
  {
    icon: 'notes',
    label: 'Text',
    type: TaskType.TEXT,
  },
  {
    icon: 'access_time',
    label: 'Date/Time',
    type: TaskType.DATE_TIME,
  },
  {
    icon: 'radio_button_checked',
    label: 'Select One',
    type: TaskType.MULTIPLE_CHOICE,
    cardinality: Cardinality.SELECT_ONE,
  },
  {
    icon: 'library_add_check',
    label: 'Select multiple',
    type: TaskType.MULTIPLE_CHOICE,
    cardinality: Cardinality.SELECT_MULTIPLE,
  },
];

export const Tasks: {
  [key in TaskGroup]: {
    icon: string;
    label: string;
    placeholder: string;
    requiredMessage: string;
  };
} = {
  [TaskGroup.QUESTION]: {
    icon: 'forum',
    label: 'Answer a question',
    placeholder: 'Question',
    requiredMessage: 'Question is required',
  },
  [TaskGroup.PHOTO]: {
    icon: 'photo_camera',
    label: 'Take a photo',
    placeholder: 'Instructions',
    requiredMessage: 'Instructions are required',
  },
  [TaskGroup.DROP_PIN]: {
    icon: 'pin_drop',
    label: 'Drop a pin',
    placeholder: 'Instructions',
    requiredMessage: 'Instructions are required',
  },
  [TaskGroup.DRAW_AREA]: {
    icon: 'draw',
    label: 'Draw an area',
    placeholder: 'Instructions',
    requiredMessage: 'Instructions are required',
  },
  [TaskGroup.CAPTURE_LOCATION]: {
    icon: 'share_location',
    label: 'Capture location',
    placeholder: 'Instructions',
    requiredMessage: 'Instructions are required',
  },
};

@Component({
  selector: 'task-form',
  templateUrl: './task-form.component.html',
  styleUrls: ['./task-form.component.scss'],
})
export class TaskFormComponent {
  @Input() formGroup!: FormGroup;
  @Input() index!: number;

  @Output() delete = new EventEmitter();
  @Output() duplicate = new EventEmitter();

  /** When expanded, options and actions below the fold are visible to the user. */
  expanded: boolean;

  /** Set to true when question gets focus, false when it loses focus. */
  selected: boolean;

  taskGroup!: TaskGroup;

  TaskGroup = TaskGroup;

  TaskType = TaskType;

  TaskTypeOptions = TaskTypeOptions;

  Tasks = Tasks;

  constructor(
    private dataStoreService: DataStoreService,
    private dialogService: DialogService
  ) {
    this.expanded = false;
    this.selected = false;
  }

  @HostListener('click')
  onTaskFocus() {
    this.expanded = true;
    this.selected = true;
  }

  @HostListener('document:click')
  onTaskBlur() {
    if (!this.selected) this.expanded = false;
    this.selected = false;
  }

  ngOnInit(): void {
    this.taskGroup =
      taskTypeToGroup.get(this.typeControl.value) ?? TaskGroup.QUESTION;
  }

  ngOnChanges(): void {
    this.taskGroup =
      taskTypeToGroup.get(this.typeControl.value) ?? TaskGroup.QUESTION;
  }

  get typeControl(): AbstractControl {
    return this.formGroup.get('type')!;
  }

  get labelControl(): AbstractControl {
    return this.formGroup.get('label')!;
  }

  get cardinalityControl(): AbstractControl {
    return this.formGroup.get('cardinality')!;
  }

  get optionsControl(): FormArray {
    return this.formGroup.get('options')! as FormArray;
  }

  onTaskDelete(): void {
    this.delete.emit(this.index);
  }

  onTaskDuplicate(): void {
    this.duplicate.emit(this.index);
  }

  getTaskTypeOption(type: TaskType): TaskTypeOption | undefined {
    return TaskTypeOptions.find(option => option.type === type);
  }

  onTaskTypeSelect(type: TaskType): void {
    const taskTypeOption = this.getTaskTypeOption(type)!;

    this.typeControl.setValue(taskTypeOption.type);
    this.cardinalityControl.setValue(taskTypeOption.cardinality);

    while (this.optionsControl.length !== 0) this.optionsControl.removeAt(0);

    if (this.cardinalityControl.value && this.optionsControl.length === 0) {
      const formGroup = new FormBuilder().group({
        id: this.dataStoreService.generateId(),
        label: '',
        code: '',
      });

      this.optionsControl.push(formGroup);
    }
  }

  onAddOption(): void {
    const formGroup = new FormBuilder().group({
      id: this.dataStoreService.generateId(),
      label: '',
      code: '',
    });

    this.optionsControl.push(formGroup);
  }

  onDeleteOption(index: number) {
    firstValueFrom(
      this.dialogService
        .openConfirmationDialog(
          'Warning',
          'Are you sure you wish to delete this option? ' +
            'Any associated data will be lost. This cannot be undone.'
        )
        .afterClosed()
    ).then(dialogResult => {
      if (dialogResult) {
        this.optionsControl.removeAt(index);
      }
    });
  }

  drop(event: CdkDragDrop<string[]>): void {
    moveItemInFormArray(
      this.optionsControl,
      event.previousIndex,
      event.currentIndex
    );
  }

  onLabelBlur(): void {
    this.labelControl.setValue(this.labelControl.value.trim());
  }
}
