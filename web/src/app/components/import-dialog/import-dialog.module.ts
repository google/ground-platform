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

import {CommonModule} from '@angular/common';
import {FileUploadModule} from '@iplab/ngx-file-upload';
import {ImportDialogComponent} from './import-dialog.component';
import {MatButtonModule} from '@angular/material/button';
import {MatLegacyDialogModule as MatDialogModule} from '@angular/material/legacy-dialog';
import {ReactiveFormsModule} from '@angular/forms';
import {NgModule} from '@angular/core';
import {MatLegacyProgressSpinnerModule as MatProgressSpinnerModule} from '@angular/material/legacy-progress-spinner';

@NgModule({
  declarations: [ImportDialogComponent],
  imports: [
    CommonModule,
    FileUploadModule,
    MatButtonModule,
    MatDialogModule,
    ReactiveFormsModule,
    MatProgressSpinnerModule,
  ],
  exports: [ImportDialogComponent],
})
export class ImportDialogModule {}
