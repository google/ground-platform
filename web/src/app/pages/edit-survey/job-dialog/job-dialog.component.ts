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
}

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

  public get title() {
    switch (this.data.dialogType) {
      case DialogType.AddJob:
        return 'Add new job';
      case DialogType.RenameJob:
        return 'Rename job';
      case DialogType.DeleteJob:
        return 'Delete job';
      case DialogType.UndoJobs:
        return 'Unpublished changes';
      case DialogType.DeleteLois:
        return 'Delete predefined sites';
      case DialogType.DeleteOption:
        return 'Delete option';
      case DialogType.DeleteSurvey:
        return 'Delete survey';
      default:
        return '';
    }
  }

  public get buttonLabel() {
    switch (this.data.dialogType) {
      case DialogType.AddJob:
        return 'Create';
      case DialogType.RenameJob:
        return 'Rename';
      case DialogType.DeleteJob:
        return 'Confirm';
      case DialogType.UndoJobs:
        return 'Continue';
      case DialogType.DeleteLois:
        return 'Confirm';
      case DialogType.DeleteOption:
        return 'Confirm';
      case DialogType.DeleteSurvey:
        return 'Confirm';
      default:
        return '';
    }
  }

  get jobNameFieldId() {
    return JobDialogComponent.JOB_NAME_FIELD_ID;
  }

  onCancelClick(): void {
    this.dialogRef.close();
  }
}
