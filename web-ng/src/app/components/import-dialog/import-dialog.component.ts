import { DataImportService } from './../../services/data-import/data-import.service';
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

import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

@Component({
  selector: 'app-import-dialog',
  templateUrl: './import-dialog.component.html',
  styleUrls: ['./import-dialog.component.css'],
})
export class ImportDialogComponent {
  private projectId: string;
  private layerId: string;
  uploadForm: FormGroup;

  constructor(
    @Inject(MAT_DIALOG_DATA)
    public data: { projectId: string; layerId: string },
    private formBuilder: FormBuilder,
    private dataImportService: DataImportService,
    dialogRef: MatDialogRef<ImportDialogComponent>
  ) {
    this.projectId = data.projectId;
    this.layerId = data.layerId;
    this.uploadForm = this.formBuilder.group({
      file: [''],
    });
    dialogRef.afterClosed().subscribe(submit => {
      if (submit) {
        this.upload();
      }
    });
  }

  onFileSelect(event: Event) {
    const target = event.target as HTMLInputElement;
    if (target && target.files && target.files.length > 0) {
      const file = target.files[0];
      this.uploadForm.get('file')?.setValue(file);
    }
  }

  upload() {
    const file = this.uploadForm.get('file');
    if (!file) {
      console.error('File missing');
      return;
    }

    this.dataImportService
      .importCsv(this.projectId, this.layerId, file.value as File)
      .subscribe(
        // TODO(#528): Show upload progress and success/error message to user.
        res => console.log(res),
        err => console.log(err)
      );
  }
}
