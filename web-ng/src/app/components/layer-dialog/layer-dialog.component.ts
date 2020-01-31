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

import { Component, Inject, OnInit, ElementRef } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatDialog } from '@angular/material';
import { ColorPickerComponent } from '../color-picker/color-picker.component';
import { ColorEvent } from 'ngx-color';

@Component({
  selector: 'app-layer-dialog',
  templateUrl: './layer-dialog.component.html',
  styleUrls: ['./layer-dialog.component.css'],
})
export class LayerDialogComponent {
  layerId: string;
  bgColor: string;

  constructor(
    // tslint:disable-next-line:no-any
    @Inject(MAT_DIALOG_DATA) data: any,
    private dialogRef: MatDialogRef<LayerDialogComponent>,
    private dialog: MatDialog
  ) {
    this.bgColor = 'black';
    this.layerId = data.layerId!;
    // Disable closing on clicks outside of dialog.
    dialogRef.disableClose = true;
  }

  openColorPickerDialog(evt: MouseEvent): void {
    const target = new ElementRef(evt.currentTarget);
    this.dialog
      .open(ColorPickerComponent, {
        data: { trigger: target },
      })
      .componentInstance.onColorPicked.subscribe((evt: ColorEvent) => {
        this.bgColor = evt.color.hex;
      });
  }
}
