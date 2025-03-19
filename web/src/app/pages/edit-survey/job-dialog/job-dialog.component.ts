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

import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';

export enum DialogType {
  AddJob,
  RenameJob,
  DeleteJob,
  UndoJobs,
  DeleteLois,
  DeleteOption,
  DeleteSurvey,
  DisableFreeForm,
  InvalidSurvey,
}

export interface DialogConfig {
  title: string;
  content?: string;
  backButtonLabel?: string;
  continueButtonLabel?: string;
}

const dialogConfigs: Record<DialogType, DialogConfig> = {
  [DialogType.AddJob]: {
    title: 'Add new job',
    backButtonLabel: 'Cancel',
    continueButtonLabel: 'Create',
  },
  [DialogType.RenameJob]: {
    title: 'Rename job',
    backButtonLabel: 'Cancel',
    continueButtonLabel: 'Rename',
  },
  [DialogType.UndoJobs]: {
    title: 'Unpublished changes',
    content:
      'If you leave this page, changes you’ve made to this survey won’t be published. Are you sure you want to continue?',
    backButtonLabel: 'Go back',
    continueButtonLabel: 'Continue',
  },
  [DialogType.DeleteJob]: {
    title: 'Delete job',
    content:
      'This job and all of its associated data will be deleted. This operation can’t be undone. Are you sure?',
    backButtonLabel: 'Cancel',
    continueButtonLabel: 'Confirm',
  },
  [DialogType.DeleteLois]: {
    title: 'Delete predefined sites',
    content:
      'All predefined data collection sites and their associated data will be immediately deleted. This action cannot be undone.',
    backButtonLabel: 'Cancel',
    continueButtonLabel: 'Confirm',
  },
  [DialogType.DeleteOption]: {
    title: 'Delete option',
    content:
      'Are you sure you wish to delete this option? All associated data will be lost. This cannot be undone.',
    backButtonLabel: 'Cancel',
    continueButtonLabel: 'Confirm',
  },
  [DialogType.DeleteSurvey]: {
    title: 'Delete survey',
    content:
      'Are you sure you wish to delete this survey? All associated data will be lost. This cannot be undone.',
    backButtonLabel: 'Cancel',
    continueButtonLabel: 'Confirm',
  },
  [DialogType.DisableFreeForm]: {
    title: 'Disable free-form data collection?',
    content:
      'Data collector will no longer be able to add new sites for this job. Data will only be collected for existing sites.',
    backButtonLabel: 'Cancel',
    continueButtonLabel: 'Confirm',
  },
  [DialogType.InvalidSurvey]: {
    title: 'Fix issues with survey',
    content: 'To publish changes, fix any outstanding issues with your survey.',
    backButtonLabel: 'Go back',
  },
};

export interface DialogData {
  dialogType: DialogType;
  jobName: string;
}

@Component({
  selector: 'job-dialog',
  templateUrl: './job-dialog.component.html',
  styleUrls: ['./job-dialog.component.scss'],
})
export class JobDialogComponent {
  public static readonly JOB_NAME_FIELD_ID = 'job-name';

  readonly DialogType = DialogType;

  constructor(
    public dialogRef: MatDialogRef<JobDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: DialogData
  ) {}

  get dialogConfig(): DialogConfig {
    return dialogConfigs[this.data.dialogType];
  }

  get title(): string {
    return this.dialogConfig.title;
  }

  get content(): string | undefined {
    return this.dialogConfig.content;
  }

  get backButtonLabel(): string | undefined {
    return this.dialogConfig.backButtonLabel;
  }

  get continueButtonLabel(): string | undefined {
    return this.dialogConfig.continueButtonLabel;
  }

  get jobNameFieldId() {
    return JobDialogComponent.JOB_NAME_FIELD_ID;
  }
}
