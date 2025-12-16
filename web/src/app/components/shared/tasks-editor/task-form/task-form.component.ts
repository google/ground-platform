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

import '@angular/localize/init';

import { CdkDragDrop } from '@angular/cdk/drag-drop';
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
  Validators,
} from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { List } from 'immutable';

import {
  DialogData,
  DialogType,
  JobDialogComponent,
} from 'app/components/edit-survey/job-dialog/job-dialog.component';
import { Cardinality } from 'app/models/task/multiple-choice.model';
import {
  TaskConditionExpressionType,
  TaskConditionMatchType,
} from 'app/models/task/task-condition.model';
import { TaskType } from 'app/models/task/task.model';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { moveItemInFormArray } from 'app/utils/utils';

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
    label: 'Select one',
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
    type: TaskType.TIME,
  },
];

export const Tasks: {
  [key in TaskGroup]: {
    icon: string;
    label: string;
    placeholder: string;
    requiredMessage?: string;
    isGeometry?: boolean;
  };
} = {
  [TaskGroup.QUESTION]: {
    icon: 'forum',
    label: $localize`:@@app.taskEditor.question.label:Answer a question`,
    placeholder: $localize`:@@app.taskEditor.question.placeholder:Question`,
    requiredMessage: $localize`:@@app.taskEditor.question.requiredMessage:Question is required`,
  },
  [TaskGroup.PHOTO]: {
    icon: 'photo_camera',
    label: $localize`:@@app.taskEditor.takeAPhoto.label:Take a photo`,
    placeholder: $localize`:@@app.taskEditor.takeAPhoto.placeholder:Instructions`,
    requiredMessage: $localize`:@@app.taskEditor.takeAPhoto.requiredMessage:Instructions are required`,
  },
  [TaskGroup.DROP_PIN]: {
    icon: 'pin_drop',
    label: $localize`:@@app.taskEditor.pinDrop.label:Drop a pin`,
    placeholder: $localize`:@@app.taskEditor.pinDrop.placeholder:Instructions`,
    requiredMessage: $localize`:@@app.taskEditor.pinDrop.requiredMessage:Instructions are required`,
    isGeometry: true,
  },
  [TaskGroup.DRAW_AREA]: {
    icon: 'draw',
    label: $localize`:@@app.taskEditor.drawArea.label:Draw or walk perimeter`,
    placeholder: $localize`:@@app.taskEditor.drawArea.placeholder:Instructions`,
    requiredMessage: $localize`:@@app.taskEditor.drawArea.requiredMessage:Instructions are required`,
    isGeometry: true,
  },
  [TaskGroup.CAPTURE_LOCATION]: {
    icon: 'share_location',
    label: $localize`:@@app.taskEditor.captureLocation.label:Capture location`,
    placeholder: $localize`:@@app.taskEditor.captureLocation.placeholder:Instructions`,
    requiredMessage: $localize`:@@app.taskEditor.captureLocation.requiredMessage:Instructions are required`,
    isGeometry: true,
  },
  [TaskGroup.INSTRUCTIONS]: {
    icon: 'list_alt_check',
    label: $localize`:@@app.taskEditor.instructions.label:Instructions`,
    placeholder: $localize`:@@app.taskEditor.instructions.placeholder:Instructions`,
    requiredMessage: $localize`:@@app.taskEditor.instructions.requiredMessage:Instructions are required`,
  },
};

const GeometryTasks = List([TaskGroup.DROP_PIN, TaskGroup.DRAW_AREA]);

const AddLoiTaskGroups = List([TaskGroup.DROP_PIN, TaskGroup.DRAW_AREA]);

@Component({
  selector: 'task-form',
  templateUrl: './task-form.component.html',
  styleUrls: ['./task-form.component.scss'],
})
export class TaskFormComponent {
  @Input() formGroup!: FormGroup;
  @Input() formGroupIndex!: number;

  @Output() delete = new EventEmitter();
  @Output() duplicate = new EventEmitter();
  @Output() toggleCondition = new EventEmitter();

  /** When expanded, options and actions below the fold are visible to the user. */
  expanded: boolean;

  /** Set to true when question gets focus, false when it loses focus. */
  selected: boolean;

  addLoiTask?: boolean;

  hasCondition?: boolean;

  otherOption?: FormGroup;

  Cardinality = Cardinality;

  taskGroup!: TaskGroup;

  taskTypeOption?: TaskTypeOption;

  TaskGroup = TaskGroup;

  TaskType = TaskType;

  TaskTypeOptions = TaskTypeOptions;

  Tasks = Tasks;

  GeometryTasks = GeometryTasks;

  AddLoiTaskGroups = AddLoiTaskGroups;

  constructor(
    public dialog: MatDialog,
    private dataStoreService: DataStoreService,
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
    this.hasCondition = this.conditionControl?.value;

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

  get conditionControl(): AbstractControl {
    return this.formGroup.get('condition')!;
  }

  onTaskDelete(): void {
    this.delete.emit(this.formGroupIndex);
  }

  onTaskDuplicate(): void {
    this.duplicate.emit(this.formGroupIndex);
  }

  onTaskConditionToggle(): void {
    if (this.formGroup.get('condition')) {
      this.formGroup.removeControl('condition');
    } else {
      this.formGroup.addControl(
        'condition',
        this.formBuilder.group({
          matchType: TaskConditionMatchType.MATCH_ALL,
          expressions: this.formBuilder.array([
            this.formBuilder.group({
              expressionType: TaskConditionExpressionType.ONE_OF_SELECTED,
              taskId: [null, Validators.required],
              optionIds: [[], Validators.required],
            }),
          ]),
        })
      );
    }
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

    const { type, cardinality } = this.taskTypeOption;

    this.typeControl.setValue(type);
    this.cardinalityControl.setValue(cardinality);

    if (!cardinality) this.optionsControl.clear({ emitEvent: false });

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

  onAddOtherOption(): void {
    this.otherOption = this.formBuilder.group({
      label: { value: 'Other...', disabled: true },
    });

    this.hasOtherOptionControl.setValue(true);
  }

  openDeleteOptionDialog(index?: number) {
    this.dialog
      .open(JobDialogComponent, {
        data: { dialogType: DialogType.DeleteOption },
        panelClass: 'small-width-dialog',
      })
      .afterClosed()
      .subscribe(async (result: DialogData) => {
        if (result?.dialogType === DialogType.DeleteOption) {
          if (index !== undefined) this.optionsControl.removeAt(index);
          else this.hasOtherOptionControl.setValue(false);
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
}
