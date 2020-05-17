import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FlexLayoutModule } from '@angular/flex-layout';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import {
  MatFormFieldModule,
  MatInputModule,
  MatSelectModule,
  MatIconModule,
  MatSlideToggleModule,
  MatButtonModule,
} from '@angular/material';
import { FormFieldEditorComponent } from './form-field-editor.component';

@NgModule({
  declarations: [FormFieldEditorComponent],
  imports: [
    CommonModule,
    FlexLayoutModule,
    MatFormFieldModule,
    FormsModule,
    ReactiveFormsModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatSlideToggleModule,
    MatButtonModule,
  ],
  exports: [FormFieldEditorComponent],
})
export class FormFieldEditorModule {}
