/**
 * Copyright 2020 Google LLC
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
  OnInit,
  Input,
  Output,
  EventEmitter,
  OnChanges,
  SimpleChanges,
  OnDestroy,
  ViewChildren,
  QueryList,
  HostListener,
  ElementRef,
  ViewChild,
  ChangeDetectorRef,
} from '@angular/core';
import {
  FormGroup,
  FormBuilder,
  Validators,
  FormControl,
  AbstractControl,
  ValidationErrors,
} from '@angular/forms';
import { DialogService } from '../../services/dialog/dialog.service';
import { StepType } from '../../shared/models/task/step.model';
import { Option } from '../../shared/models/task/option.model';
import { List } from 'immutable';
import {
  MultipleChoice,
  Cardinality,
} from '../../shared/models/task/multiple-choice.model';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { JobService } from '../../services/job/job.service';
import { Subscription } from 'rxjs';
import { OptionEditorComponent } from '../option-editor/option-editor.component';

export interface StepTypeSelectOption {
  icon: string;
  label: string;
  type: StepType;
  cardinality?: Cardinality;
}

@Component({
  selector: 'ground-task-step-editor',
  templateUrl: './task-step-editor.component.html',
  styleUrls: ['./task-step-editor.component.scss'],
})
export class TaskStepEditorComponent implements OnInit, OnChanges, OnDestroy {
  @Input() label?: string;
  @Input() required?: boolean;
  @Input() stepType?: StepType;
  @Input() multipleChoice?: MultipleChoice;
  @Input() cardinality?: Cardinality;
  @Input() stepCount?: Number;
  @Output() update = new EventEmitter();
  @Output() delete = new EventEmitter();
  taskOptions: MultipleChoice | undefined;
  selectStepOptions: StepTypeSelectOption[];

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
    this.selectStepOptions = [
      {
        icon: 'short_text',
        label: 'Text',
        type: StepType.TEXT,
      },
      {
        icon: 'radio_button_checked',
        label: 'Select One',
        type: StepType.MULTIPLE_CHOICE,
        cardinality: Cardinality.SELECT_ONE,
      },
      {
        icon: 'library_add_check',
        label: 'Select multiple',
        type: StepType.MULTIPLE_CHOICE,
        cardinality: Cardinality.SELECT_MULTIPLE,
      },
      {
        icon: 'photo',
        label: 'Photo',
        type: StepType.PHOTO,
      },
      {
        icon: 'tag',
        label: 'Number',
        type: StepType.NUMBER,
      },
      {
        icon: 'calendar_today',
        label: 'Date',
        type: StepType.DATE,
      },
      {
        icon: 'access_time',
        label: 'Time',
        type: StepType.TIME,
      },
    ];
    this.taskGroup = this.taskBuilder.group({
      label: ['', this.validateLabel.bind(this)],
      required: [false],
      // By default we set the select step to be of text type.
      selectStepOption: this.selectStepOptions[StepType.TEXT],
    });
  }

  private validateLabel(control: FormControl): ValidationErrors | null {
    return this.isTaskEmpty() ? null : Validators.required(control);
  }

  private isTaskEmpty(): boolean {
    return (
      this.label?.trim().length === 0 &&
      this.stepCount === 1 &&
      this.stepType === StepType.TEXT
    );
  }

  ngOnInit(): void {
    // As the task steps value change we are emitting the updated value to the job-dialog.
    this.subscription.add(
      this.taskGroup.valueChanges.subscribe(value => {
        this.update.emit({
          label: value.label,
          required: value.required,
          type: value.selectStepOption.type,
          multipleChoice: this.taskOptions,
        });
      })
    );
  }

  /*
   * This method is used to get the updated values of step from the job-dialog.
   */
  ngOnChanges(changes: SimpleChanges): void {
    if (changes.multipleChoice) {
      this.taskOptions = this.multipleChoice;
    }
    this.taskGroup.setValue({
      label: this.label,
      required: this.required,
      selectStepOption: this.getSelectedStepTypeOption(),
    });
    this.markOptionEditorsTouched();
  }

  getSelectedStepTypeOption(): StepTypeSelectOption {
    const selectedOption = this.selectStepOptions.find(
      option =>
        option.type === this.stepType &&
        (option.type !== StepType.MULTIPLE_CHOICE ||
          option.cardinality === this.cardinality)
    );
    if (!selectedOption) {
      throw new Error(`Unsupported step type${this.stepType}`);
    }
    return selectedOption;
  }

  /**
   * Emits the delete step event to the job dialog component.
   *
   * @returns void
   */
  onStepDelete(): void {
    this.delete.emit();
  }

  getSelectStepType(): StepTypeSelectOption {
    return this.taskGroup.get('selectStepOption')?.value;
  }

  /**
   * Updates the type in the taskGroup on the select change event.
   *
   * @param event: StepTypeSelectOption
   * @returns void
   */
  onStepTypeSelect(event: StepTypeSelectOption): void {
    this.stepType = event.type;
    this.cardinality = event.cardinality;
    if (this.cardinality && this.taskOptions?.options) {
      this.taskOptions = new MultipleChoice(
        this.cardinality,
        this.taskOptions.options
      );
    }
    this.taskGroup.patchValue({ selectStepOption: event });
    if (event.type === StepType.MULTIPLE_CHOICE) {
      if (!this.taskOptions?.options?.size) {
        this.onAddOption();
      }
    } else {
      this.taskOptions = undefined;
    }
  }

  /*
   * This is used to track input added or removed in case of step options.
   */
  trackByFn(index: number): number {
    return index;
  }

  /**
   * Emits event with updated Options. Called when label or code gets updated.
   *
   * @param event: label and code value of the option step.
   * @param index: index of the option to be updated.
   * @returns void
   */
  onOptionUpdate(event: { label: string; code: string }, index: number): void {
    const option = this.jobService.createOption(event.code, event.label, index);
    const options = this.setTaskOptions(index, option);
    this.emitTaskOptions(options);
  }

  onOptionDelete(index: number) {
    this.dialogService
      .openConfirmationDialog(
        'Warning',
        'Are you sure you wish to delete this option? ' +
          'Any associated data will be lost. This cannot be undone.'
      )
      .afterClosed()
      .toPromise()
      .then(dialogResult => {
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
      type: this.stepType,
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
