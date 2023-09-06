/**
 * Copyright 2019 Google LLC
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

import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {JobDialogComponent} from './job-dialog.component';
import {MatLegacyDialogModule as MatDialogModule} from '@angular/material/legacy-dialog';
import {MatLegacyFormFieldModule as MatFormFieldModule} from '@angular/material/legacy-form-field';
import {MatLegacyInputModule as MatInputModule} from '@angular/material/legacy-input';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {InlineEditorModule} from 'app/components/inline-editor/inline-editor.module';
import {EditStyleButtonModule} from './edit-style-button/edit-style-button.module';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {ConfirmationDialogComponent} from 'app/components/confirmation-dialog/confirmation-dialog.component';
import {TaskEditorModule} from './task-editor/task-editor.module';
import {DragDropModule} from '@angular/cdk/drag-drop';
import {MatCheckboxModule} from '@angular/material/checkbox';

@NgModule({
  declarations: [JobDialogComponent, ConfirmationDialogComponent],
  imports: [
    CommonModule,
    MatDialogModule,
    MatFormFieldModule,
    FormsModule,
    ReactiveFormsModule,
    InlineEditorModule,
    EditStyleButtonModule,
    MatInputModule,
    MatButtonModule,
    TaskEditorModule,
    DragDropModule,
    MatIconModule,
    MatCheckboxModule,
  ],
})
export class JobDialogModule {}
