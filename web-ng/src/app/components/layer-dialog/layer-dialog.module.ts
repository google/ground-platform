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

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LayerDialogComponent } from './layer-dialog.component';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { InlineEditorModule } from '../inline-editor/inline-editor.module';
import { ColorPickerModule } from '../color-picker/color-picker.module';
import { EditStyleButtonModule } from '../edit-style-button/edit-style-button.module';
import { FlexLayoutModule } from '@angular/flex-layout';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ConfirmationDialogComponent } from '../confirmation-dialog/confirmation-dialog.component';
import { FormFieldEditorModule } from '../form-field-editor/form-field-editor.module';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { MatCheckboxModule } from '@angular/material/checkbox';

@NgModule({
  declarations: [LayerDialogComponent, ConfirmationDialogComponent],
  imports: [
    CommonModule,
    MatDialogModule,
    MatFormFieldModule,
    FormsModule,
    ReactiveFormsModule,
    InlineEditorModule,
    ColorPickerModule,
    EditStyleButtonModule,
    MatInputModule,
    FlexLayoutModule,
    MatButtonModule,
    FormFieldEditorModule,
    DragDropModule,
    MatIconModule,
    MatCheckboxModule,
  ],
})
export class LayerDialogModule {}
