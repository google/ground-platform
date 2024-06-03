/**
 * Copyright 2019 The Ground Authors.
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

import {DragDropModule} from '@angular/cdk/drag-drop';
import {CommonModule} from '@angular/common';
import {NgModule} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatDialogModule} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';

import {InlineEditorModule} from 'app/components/inline-editor/inline-editor.module';

import {EditStyleButtonModule} from './edit-style-button/edit-style-button.module';
import {JobDialogComponent} from './job-dialog.component';
import {TaskEditorModule} from './task-editor/task-editor.module';

@NgModule({
  declarations: [JobDialogComponent],
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
