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
  Input,
  OnInit,
  Output,
} from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';

import { GroundPinService } from 'app/services/ground-pin/ground-pin.service';

import { ColorPickerComponent } from './color-picker/color-picker.component';

@Component({
  selector: 'ground-edit-style-button',
  templateUrl: './edit-style-button.component.html',
  standalone: false,
})
export class EditStyleButtonComponent implements OnInit {
  @Input() markerColor = 'black';
  @Output() markerColorChange = new EventEmitter<MarkerColorEvent>();
  markerPinUrl: SafeUrl;

  constructor(
    private dialog: MatDialog,
    private sanitizer: DomSanitizer,
    private groundPinService: GroundPinService
  ) {
    this.markerPinUrl = sanitizer.bypassSecurityTrustUrl(
      groundPinService.getPinImageSource(this.markerColor)
    );
  }

  ngOnInit() {
    this.markerPinUrl = this.sanitizer.bypassSecurityTrustUrl(
      this.groundPinService.getPinImageSource(this.markerColor)
    );
  }

  openColorPickerDialog(evt: MouseEvent): void {
    const target = new ElementRef(evt.currentTarget);
    this.dialog
      .open(ColorPickerComponent, {
        data: { trigger: target },
      })
      .componentInstance.onColorPicked.subscribe((color: string) => {
        this.markerColor = color;
        this.markerColorChange.emit(new MarkerColorEvent(this.markerColor));
        this.markerPinUrl = this.sanitizer.bypassSecurityTrustUrl(
          this.groundPinService.getPinImageSource(this.markerColor)
        );
      });
  }
}

export class MarkerColorEvent {
  color: string;

  constructor(newColor: string) {
    this.color = newColor;
  }
}
