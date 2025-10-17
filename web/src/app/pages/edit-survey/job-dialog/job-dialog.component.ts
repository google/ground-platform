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
  SurveyCreationDenied,
}

export interface DialogConfig {
  title: string;
  content?: string;
  backButtonLabel?: string;
  continueButtonLabel?: string;
}

export const dialogConfigs: Record<DialogType, DialogConfig> = {
  [DialogType.AddJob]: {
    title: $localize`:@@app.dialogs.addJob.title:Add new job`,
    backButtonLabel: $localize`:@@app.labels.cancel:Cancel`,
    continueButtonLabel: $localize`:@@app.labels.create:Create`,
  },
  [DialogType.RenameJob]: {
    title: $localize`:@@app.dialogs.renameJob.title:Rename job`,
    backButtonLabel: $localize`:@@app.labels.cancel:Cancel`,
    continueButtonLabel: $localize`:@@app.labels.rename:Rename`,
  },
  [DialogType.UndoJobs]: {
    title: $localize`:@@app.dialogs.unpublishedChanges.title:Unpublished changes`,
    content: $localize`:@@app.dialogs.unpublishedChanges.content:If you leave this page, changes you’ve made to this survey won’t be published. Are you sure you want to continue?`,
    backButtonLabel: $localize`:@@app.labels.goBack:Go back`,
    continueButtonLabel: $localize`:@@app.labels.continue:Continue`,
  },
  [DialogType.DeleteJob]: {
    title: $localize`:@@app.dialogs.deleteJob.title:Delete job`,
    content: $localize`:@@app.dialogs.deleteJob.content:This job and all of its associated data will be deleted. This operation can’t be undone. Are you sure?`,
    backButtonLabel: $localize`:@@app.labels.cancel:Cancel`,
    continueButtonLabel: $localize`:@@app.labels.confirm:Confirm`,
  },
  [DialogType.DeleteLois]: {
    title: $localize`:@@app.dialogs.deleteLois.title:Delete predefined sites`,
    content: $localize`:@@app.dialogs.deleteLois.content:All predefined data collection sites and their associated data will be immediately deleted. This action cannot be undone.`,
    backButtonLabel: $localize`:@@app.labels.cancel:Cancel`,
    continueButtonLabel: $localize`:@@app.labels.confirm:Confirm`,
  },
  [DialogType.DeleteOption]: {
    title: $localize`:@@app.dialogs.deleteOption.title:Delete option`,
    content: $localize`:@@app.dialogs.deleteOption.content:Are you sure you wish to delete this option? All associated data will be lost. This cannot be undone.`,
    backButtonLabel: $localize`:@@app.labels.cancel:Cancel`,
    continueButtonLabel: $localize`:@@app.labels.confirm:Confirm`,
  },
  [DialogType.DeleteSurvey]: {
    title: $localize`:@@app.dialogs.deleteSurvey.title:Delete survey`,
    content: $localize`:@@app.dialogs.deleteSurvey.content:Are you sure you wish to delete this survey? All associated data will be lost. This cannot be undone.`,
    backButtonLabel: $localize`:@@app.labels.cancel:Cancel`,
    continueButtonLabel: $localize`:@@app.labels.confirm:Confirm`,
  },
  [DialogType.DisableFreeForm]: {
    title: $localize`:@@app.dialogs.disableFreeForm.title:Disable free-form data collection?`,
    content: $localize`:@@app.dialogs.disableFreeForm.content:Data collector will no longer be able to add new sites for this job. Data will only be collected for existing sites.`,
    backButtonLabel: $localize`:@@app.labels.cancel:Cancel`,
    continueButtonLabel: $localize`:@@app.labels.confirm:Confirm`,
  },
  [DialogType.InvalidSurvey]: {
    title: $localize`:@@app.dialogs.invalidSurvey.title:Fix issues with survey`,
    content: $localize`:@@app.dialogs.invalidSurvey.content:To publish changes, fix any outstanding issues with your survey.`,
    backButtonLabel: $localize`:@@app.labels.goBack:Go back`,
  },
  [DialogType.SurveyCreationDenied]: {
    title: $localize`:@@app.dialogs.surveyCreationDenied.title:Registration Required`,
    content: $localize`:@@app.dialogs.surveyCreationDenied.content:You must register for an account to create a new survey. Click "Continue" to be redirected to the registration form.`,
    backButtonLabel: $localize`:@@app.labels.goBack:Go back`,
    continueButtonLabel: $localize`:@@app.labels.continue:Continue`,
  },
};

export interface DialogData {
  dialogType: DialogType;
  jobName?: string;
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
