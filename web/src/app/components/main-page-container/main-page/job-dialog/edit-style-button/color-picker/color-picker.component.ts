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

import {
  Component,
  ElementRef,
  EventEmitter,
  Inject,
  OnInit,
  Output,
} from '@angular/core';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogConfig,
  MatDialogRef,
} from '@angular/material/dialog';
import { Job } from 'app/models/job.model';
import { ColorEvent } from 'ngx-color';

@Component({
  selector: 'ground-color-picker',
  templateUrl: './color-picker.component.html',
  styleUrls: ['./color-picker.component.scss'],
  standalone: false,
})
export class ColorPickerComponent implements OnInit {
  job?: Job;
  surveyId?: string;
  color!: string;
  private readonly matDialogRef: MatDialogRef<ColorPickerComponent>;
  private readonly triggerElementRef: ElementRef;
  @Output() onColorPicked: EventEmitter<{}> = new EventEmitter();

  constructor(
    private dialog: MatDialog,
    matDialogRef: MatDialogRef<ColorPickerComponent>,
    @Inject(MAT_DIALOG_DATA) data: { trigger: ElementRef }
  ) {
    this.matDialogRef = matDialogRef;
    this.triggerElementRef = data.trigger;
  }

  ngOnInit() {
    if (!this.triggerElementRef) {
      return;
    }
    const matDialogConfig: MatDialogConfig = new MatDialogConfig();
    const rect = this.triggerElementRef.nativeElement.getBoundingClientRect();
    matDialogConfig.position = {
      left: `${rect.left}px`,
      top: `${rect.bottom + 10}px`,
    };
    matDialogConfig.width = '300px';
    matDialogConfig.height = '300px';
    this.matDialogRef.updateSize(matDialogConfig.width, matDialogConfig.height);
    this.matDialogRef.updatePosition(matDialogConfig.position);
  }

  openColorPickerDialog() {
    this.dialog.open(ColorPickerComponent);
  }

  close() {
    this.matDialogRef.close();
  }

  handleColorChange($event: ColorEvent) {
    this.color = $event.color.hex;
  }

  // Test comment. Will remove later.
  onSave() {
    this.onColorPicked.emit(this.color);
    this.close();
  }
}
