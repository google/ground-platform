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
import {List} from 'immutable';
import {firstValueFrom} from 'rxjs';

import {Cardinality} from 'app/models/task/multiple-choice.model';
import {TaskType} from 'app/models/task/task.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {DialogService} from 'app/services/dialog/dialog.service';
import {moveItemInFormArray} from 'app/utils/utils';

import {
  TaskGroup,
  taskGroupToTypes,
  taskTypeToGroup,
} from '../tasks-editor.component';

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
  {
    icon: 'tag',
    label: 'Number',
    type: TaskType.NUMBER,
  },
  {
    icon: 'calendar_today',
    label: 'Date',
    type: TaskType.DATE,
  },
  {
    icon: 'access_time',
    label: 'Time',
    type: TaskType.DATE_TIME,
  },
];

export const Tasks: {
  [key in TaskGroup]: {
    icon: string;
    label: string;
    placeholder: string;
    requiredMessage: string;
    isGeometry?: boolean;
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
    isGeometry: true,
  },
  [TaskGroup.DRAW_AREA]: {
    icon: 'draw',
    label: 'Draw an area',
    placeholder: 'Instructions',
    requiredMessage: 'Instructions are required',
    isGeometry: true,
  },
  [TaskGroup.CAPTURE_LOCATION]: {
    icon: 'share_location',
    label: 'Capture location',
    placeholder: 'Instructions',
    requiredMessage: 'Instructions are required',
    isGeometry: true,
  },
};

export const GeometryTasks = List([
  TaskGroup.DROP_PIN,
  TaskGroup.DRAW_AREA,
  TaskGroup.CAPTURE_LOCATION,
]);

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

  addLoiTask?: boolean;

  otherOption?: FormGroup;

  taskGroup!: TaskGroup;

  taskTypeOption?: TaskTypeOption;

  TaskGroup = TaskGroup;

  TaskType = TaskType;

  TaskTypeOptions = TaskTypeOptions;

  Tasks = Tasks;

  GeometryTasks = GeometryTasks;

  constructor(
    private dataStoreService: DataStoreService,
    private dialogService: DialogService,
    private formBuilder: FormBuilder
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
    const type = this.typeControl.value;
    const cardinality = this.cardinalityControl.value;

    this.taskGroup = taskTypeToGroup.get(type) ?? TaskGroup.QUESTION;
    this.taskTypeOption = this.getTaskTypeOption(type, cardinality);
    this.addLoiTask = this.addLoiTaskControl.value;

    if (this.addLoiTask) this.formGroup.get('required')?.disable();

    if (this.hasOtherOptionControl?.value) this.onAddOtherOption();
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

  get hasOtherOptionControl(): AbstractControl {
    return this.formGroup.get('hasOtherOption')!;
  }

  get addLoiTaskControl(): AbstractControl {
    return this.formGroup.get('addLoiTask')!;
  }

  onTaskDelete(): void {
    this.delete.emit(this.index);
  }

  onTaskDuplicate(): void {
    this.duplicate.emit(this.index);
  }

  getTaskTypeOption(
    type: TaskType,
    cardinality?: Cardinality
  ): TaskTypeOption | undefined {
    return TaskTypeOptions.find(
      taskTypeOption =>
        taskTypeOption.type === type &&
        (!cardinality || taskTypeOption.cardinality === cardinality)
    );
  }

  onTaskTypeSelect(taskTypeOption: TaskTypeOption): void {
    this.taskTypeOption = taskTypeOption;

    const {type, cardinality} = this.taskTypeOption;

    this.typeControl.setValue(type);
    this.cardinalityControl.setValue(cardinality);

    if (!cardinality) this.optionsControl.clear({emitEvent: false});

    if (cardinality && this.optionsControl.length === 0) this.onAddOption();
  }

  onTaskGroupSelect(taskGroup: TaskGroup): void {
    const taskType = taskGroupToTypes.get(taskGroup)?.first();

    this.typeControl.setValue(taskType);
  }

  onAddOption(): void {
    const formGroup = this.formBuilder.group({
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

  onAddOtherOption(): void {
    this.otherOption = this.formBuilder.group({
      label: {value: 'Other...', disabled: true},
    });

    this.hasOtherOptionControl.setValue(true);
  }

  onDeleteOtherOption(): void {
    this.hasOtherOptionControl.setValue(false);
  }

  drop(event: CdkDragDrop<string[]>): void {
    moveItemInFormArray(
      this.optionsControl,
      event.previousIndex,
      event.currentIndex
    );
  }
}
