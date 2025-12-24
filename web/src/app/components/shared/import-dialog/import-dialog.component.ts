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

import { HttpErrorResponse } from '@angular/common/http';
import { Component, Inject, NgZone } from '@angular/core';
import { FormBuilder, FormControl, FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { DataImportService } from 'app/services/data-import/data-import.service';
import { NotificationService } from 'app/services/notification/notification.service';

@Component({
  selector: 'ground-import-dialog',
  templateUrl: './import-dialog.component.html',
  styleUrls: ['./import-dialog.component.scss'],
  standalone: false,
})
export class ImportDialogComponent {
  private surveyId: string;
  private jobId: string;
  public readonly acceptedExtensions = '.geojson';
  uploadForm: FormGroup;
  public files: Array<File> = [];
  isImporting = false;

  constructor(
    @Inject(MAT_DIALOG_DATA)
    public data: { surveyId: string; jobId: string },
    private formBuilder: FormBuilder,
    private dataImportService: DataImportService,
    private readonly dialogRef: MatDialogRef<ImportDialogComponent>,
    private readonly notificationService: NotificationService,
    private ngZone: NgZone
  ) {
    this.surveyId = data.surveyId;
    this.jobId = data.jobId;
    this.uploadForm = this.formBuilder.group({
      file: new FormControl(),
    });
  }

  async onImportLocationsOfInterest(): Promise<void> {
    const files = this.uploadForm.get('file')?.value;
    if (!files || files.length === 0) {
      console.error('File missing');
      return;
    }
    if (files.length > 1) {
      console.error(
        'Invalid request. Only one file may be imported at the time'
      );
      return;
    }
    try {
      this.isImporting = true;
      const response = await this.dataImportService.importLocationsOfInterest(
        this.surveyId,
        this.jobId,
        files[0] as File
      );
      this.notificationService.success(`${response.count} sites imported`);
    } catch (err) {
      console.error(err);
      this.notificationService.error(
        `Importing data collection sites failed due to the following error: ${
          (err as HttpErrorResponse).error
        }`
      );
    }
    this.isImporting = false;
    this.ngZone.run(() => {
      this.dialogRef.close();
    });
  }
}
