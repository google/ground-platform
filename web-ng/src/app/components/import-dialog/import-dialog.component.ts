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

import { environment } from '../../../environments/environment';
import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';

@Component({
  selector: 'app-import-dialog',
  templateUrl: './import-dialog.component.html',
  styleUrls: ['./import-dialog.component.css'],
})
export class ImportDialogComponent {
  // TODO: Access via AngularFireFunctionsModule instead?
  readonly SERVER_URL = `${environment.cloudFunctionsUrl}/importCsv`;
  private projectId: string;
  private layerId: string;
  uploadForm: FormGroup;

  constructor(
    @Inject(MAT_DIALOG_DATA)
    public data: { projectId: string; layerId: string },
    private formBuilder: FormBuilder,
    private httpClient: HttpClient
  ) {
    this.projectId = data.projectId;
    this.layerId = data.layerId;
    this.uploadForm = this.formBuilder.group({
      file: [''],
    });
  }

  onFileSelect(event: Event) {
    const target = event.target as HTMLInputElement;
    if (target && target.files && target.files.length > 0) {
      const file = target.files[0];
      this.uploadForm.get('file')?.setValue(file);
    }
  }

  onSubmit() {
    const file = this.uploadForm.get('file');
    if (!file) {
      console.error('File missing');
      return;
    }
    const formData = new FormData();
    formData.set('project', this.projectId);
    formData.set('layer', this.layerId);
    formData.append('file', file.value);
    this.httpClient.post<any>(this.SERVER_URL, formData).subscribe(
      res => console.log(res),
      err => console.log(err)
    );
  }
}
