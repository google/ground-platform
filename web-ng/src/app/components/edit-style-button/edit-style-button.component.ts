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

import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ColorPickerComponent } from '../color-picker/color-picker.component';
import { ColorEvent } from 'ngx-color';

@Component({
  selector: 'app-edit-style-button',
  templateUrl: './edit-style-button.component.html',
  styleUrls: ['./edit-style-button.component.css'],
})
export class EditStyleButtonComponent {
  @Input() markerColor = 'black';
  @Output() markerColorChange = new EventEmitter<MarkerColorEvent>();

  constructor(
    // tslint:disable-next-line:no-any
    private dialog: MatDialog
  ) {}

  openColorPickerDialog(evt: MouseEvent): void {
    const target = new ElementRef(evt.currentTarget);
    this.dialog
      .open(ColorPickerComponent, {
        data: { trigger: target },
      })
      .componentInstance.onColorPicked.subscribe((evt: ColorEvent) => {
        this.markerColor = evt.color.hex;
        this.markerColorChange.emit(new MarkerColorEvent(this.markerColor));
      });
  }
}

export class MarkerColorEvent {
  color: string;

  constructor(newColor: string) {
    this.color = newColor;
  }
}
