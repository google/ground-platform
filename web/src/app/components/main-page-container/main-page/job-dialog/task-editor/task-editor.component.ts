/**
 * Copyright 2020 The Ground Authors.
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

import { CdkDragDrop } from '@angular/cdk/drag-drop';
import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  QueryList,
  SimpleChanges,
  ViewChild,
  ViewChildren,
} from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  FormControl,
  FormGroup,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import {
  Cardinality,
  MultipleChoice,
} from 'app/models/task/multiple-choice.model';
import { Option } from 'app/models/task/option.model';
import { TaskType } from 'app/models/task/task.model';
import { DialogService } from 'app/services/dialog/dialog.service';
import { JobService } from 'app/services/job/job.service';
import { List } from 'immutable';
import { Subscription, firstValueFrom } from 'rxjs';

import { OptionEditorComponent } from './option-editor/option-editor.component';

export interface TaskTypeSelectOption {
  icon: string;
  label: string;
  type: TaskType;
  cardinality?: Cardinality;
}

@Component({
  selector: 'ground-task-editor',
  templateUrl: './task-editor.component.html',
  styleUrls: ['./task-editor.component.scss'],
  standalone: false,
})
export class TaskEditorComponent implements OnInit, OnChanges, OnDestroy {
  @Input() label?: string;
  @Input() required?: boolean;
  @Input() taskType?: TaskType;
  @Input() multipleChoice?: MultipleChoice;
  @Input() cardinality?: Cardinality;
  @Input() taskCount?: Number;
  @Output() update = new EventEmitter();
  @Output() delete = new EventEmitter();
  taskOptions: MultipleChoice | undefined;
  selectTaskOptions: TaskTypeSelectOption[];

  /** When expanded, options and actions below the fold are visible to the user. */
  expanded: boolean;

  /** Set to true when question gets focus, false when it loses focus. */
  selected: boolean;

  subscription: Subscription = new Subscription();

  taskGroup: FormGroup;
  @ViewChild('questionInput', { static: true }) questionInput?: ElementRef;

  @HostListener('click')
  onTaskFocus() {
    this.expanded = true;
    this.selected = true;
  }

  @HostListener('document:click')
  onTaskBlur() {
    if (!this.selected) {
      this.expanded = false;
    }
    this.selected = false;
  }

  @ViewChildren(OptionEditorComponent)
  optionEditors?: QueryList<OptionEditorComponent>;

  constructor(
    private taskBuilder: FormBuilder,
    private dialogService: DialogService,
    private jobService: JobService,
    private readonly cdr: ChangeDetectorRef
  ) {
    this.expanded = false;
    this.selected = false;
    this.selectTaskOptions = [
      {
        icon: 'short_text',
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
        icon: 'photo',
        label: 'Photo',
        type: TaskType.PHOTO,
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
    this.taskGroup = this.taskBuilder.group({
      label: ['', this.validateLabel.bind(this)],
      required: [false],
      // By default we set the select task to be of text type.
      selectTaskOption: this.selectTaskOptions[TaskType.TEXT],
    });
  }

  private validateLabel(control: FormControl): ValidationErrors | null {
    return this.isTaskEmpty() ? null : Validators.required(control);
  }

  private isTaskEmpty(): boolean {
    return (
      this.label?.trim().length === 0 &&
      this.taskCount === 1 &&
      this.taskType === TaskType.TEXT
    );
  }

  ngOnInit(): void {
    // As the task tasks value change we are emitting the updated value to the job-dialog.
    this.subscription.add(
      this.taskGroup.valueChanges.subscribe(value => {
        this.update.emit({
          label: value.label,
          required: value.required,
          type: value.selectTaskOption.type,
          multipleChoice: this.taskOptions,
        });
      })
    );
  }

  /*
   * This method is used to get the updated values of task from the job-dialog.
   */
  ngOnChanges(changes: SimpleChanges): void {
    if (changes.multipleChoice) {
      this.taskOptions = this.multipleChoice;
    }
    this.taskGroup.setValue({
      label: this.label,
      required: this.required,
      selectTaskOption: this.getSelectedTaskTypeOption(),
    });
    this.markOptionEditorsTouched();
  }

  getSelectedTaskTypeOption(): TaskTypeSelectOption {
    const selectedOption = this.selectTaskOptions.find(
      option =>
        option.type === this.taskType &&
        (option.type !== TaskType.MULTIPLE_CHOICE ||
          option.cardinality === this.cardinality)
    );
    if (!selectedOption) {
      throw new Error(`Unsupported task type${this.taskType}`);
    }
    return selectedOption;
  }

  /**
   * Emits the delete task event to the job dialog component.
   *
   * @returns void
   */
  onTaskDelete(): void {
    this.delete.emit();
  }

  getSelectTaskType(): TaskTypeSelectOption {
    return this.taskGroup.get('selectTaskOption')?.value;
  }

  /**
   * Updates the type in the taskGroup on the select change event.
   *
   * @param event: TaskTypeSelectOption
   * @returns void
   */
  onTaskTypeSelect(event: TaskTypeSelectOption): void {
    this.taskType = event.type;
    this.cardinality = event.cardinality;
    if (this.cardinality && this.taskOptions?.options) {
      this.taskOptions = new MultipleChoice(
        this.cardinality,
        this.taskOptions.options
      );
    }
    this.taskGroup.patchValue({ selectTaskOption: event });
    if (event.type === TaskType.MULTIPLE_CHOICE) {
      if (!this.taskOptions?.options?.size) {
        this.onAddOption();
      }
    } else {
      this.taskOptions = undefined;
    }
  }

  /*
   * This is used to track input added or removed in case of task options.
   */
  trackByFn(index: number): number {
    return index;
  }

  /**
   * Emits event with updated Options. Called when label or code gets updated.
   *
   * @param event: label and code value of the option task.
   * @param index: index of the option to be updated.
   * @returns void
   */
  onOptionUpdate(event: { label: string; code: string }, index: number): void {
    const option = this.jobService.createOption(event.code, event.label, index);
    const options = this.setTaskOptions(index, option);
    this.emitTaskOptions(options);
  }

  onOptionDelete(index: number) {
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
        let options = this.taskOptions?.options;
        if (!options) return;
        options = options.delete(index);
        this.emitTaskOptions(options);
      }
    });
  }

  onAddOption(): void {
    const index = this.taskOptions?.options.size || 0;
    const option = this.jobService.createOption(
      /* code= */ '',
      /* label= */ '',
      index
    );
    const options = this.setTaskOptions(index, option);
    this.emitTaskOptions(options);
    this.focusNewOption();
  }

  setTaskOptions(index: number, option: Option): List<Option> {
    let options = this.taskOptions?.options || List<Option>();
    options = options?.set(index, option);
    return options;
  }

  emitTaskOptions(options: List<Option>): void {
    const cardinality =
      this.taskOptions?.cardinality ||
      this.cardinality ||
      Cardinality.SELECT_MULTIPLE;
    this.taskOptions = new MultipleChoice(cardinality, options);
    this.update.emit({
      label: this.label,
      required: this.required,
      type: this.taskType,
      multipleChoice: this.taskOptions,
    });
  }

  drop(event: CdkDragDrop<string[]>): void {
    if (!this.taskOptions) return;
    let options = this.taskOptions.options;
    const optionAtPrevIndex = options.get(event.previousIndex);
    if (!optionAtPrevIndex) return;

    options = options.delete(event.previousIndex);
    options = options.insert(event.currentIndex, optionAtPrevIndex);
    // Update indexes.
    options = options.map((option: Option, index: number) =>
      option.withIndex(index)
    );

    this.emitTaskOptions(options);
  }

  get labelControl(): AbstractControl {
    return this.taskGroup.get('label')!;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  onLabelBlur(): void {
    this.labelControl.setValue(this.labelControl.value.trim());
  }

  private markOptionEditorsTouched(): void {
    this.optionEditors?.forEach(editor => {
      editor.optionGroup.markAllAsTouched();
    });
  }

  private focusNewOption(): void {
    this.cdr.detectChanges();
    if (this.optionEditors?.length) {
      const option = this.optionEditors.last;
      option?.optionInput?.nativeElement.focus();
    }
  }
}
