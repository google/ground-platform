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
import { TextFieldComponent } from './text-field.component';

@NgModule({
  declarations: [TextFieldComponent],
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
  exports: [TextFieldComponent],
})
export class TextFieldModule {}
