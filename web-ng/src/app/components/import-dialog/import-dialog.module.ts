import { MatButtonModule } from '@angular/material/button';
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

import { CommonModule } from '@angular/common';
import { FileUploadModule } from '@iplab/ngx-file-upload';
import { HttpClientModule } from '@angular/common/http';
import { ImportDialogComponent } from '../import-dialog/import-dialog.component';
import { MatDialogModule } from '@angular/material/dialog';
import { ReactiveFormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';

@NgModule({
  declarations: [ImportDialogComponent],
  imports: [
    CommonModule,
    FileUploadModule,
    HttpClientModule,
    MatButtonModule,
    MatDialogModule,
    ReactiveFormsModule,
  ],
  exports: [ImportDialogComponent],
})
export class ImportDialogModule {}
