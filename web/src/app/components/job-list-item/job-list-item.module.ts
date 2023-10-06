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

import {BrowserModule} from '@angular/platform-browser';
import {ImportDialogModule} from 'app/components/import-dialog/import-dialog.module';
import {NgModule} from '@angular/core';
import {JobListItemComponent} from './job-list-item.component';
import {MatLegacyListModule as MatListModule} from '@angular/material/legacy-list';
import {MatLegacyMenuModule as MatMenuModule} from '@angular/material/legacy-menu';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatLegacyDialogModule as MatDialogModule} from '@angular/material/legacy-dialog';
import {MatLegacySnackBarModule as MatSnackBarModule} from '@angular/material/legacy-snack-bar';
import {MatTreeModule} from '@angular/material/tree';
import {CdkTreeModule} from '@angular/cdk/tree';

@NgModule({
  imports: [
    BrowserModule,
    ImportDialogModule,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatMenuModule,
    MatDialogModule,
    MatSnackBarModule,
    MatTreeModule,
    CdkTreeModule,
  ],
  exports: [JobListItemComponent],
  declarations: [JobListItemComponent],
})
export class JobListItemModule {}
