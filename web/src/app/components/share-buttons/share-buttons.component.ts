/**
 * Copyright 2025 The Ground Authors.
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

import {Component, ElementRef, Input, ViewChild} from '@angular/core';

import {NotificationService} from 'app/services/notification/notification.service';

@Component({
  selector: 'ground-share-buttons',
  templateUrl: './share-buttons.component.html',
  styleUrls: ['./share-buttons.component.scss'],
})
export class ShareButtonsComponent {
  @Input() surveyId = '';

  @ViewChild('qrCodeElement', {read: ElementRef})
  qrCodeElement!: ElementRef<HTMLCanvasElement>;

  constructor(private readonly notificationService: NotificationService) {}

  copyLinkToClipboard() {
    const data = this.surveyId;
    navigator.clipboard.writeText(data).then(
      () => {
        this.notificationService.success(
          'Survey link copied into the clipboard'
        );
      },
      _ => {
        this.notificationService.error(
          'Impossible to copy Survey link into the clipboard'
        );
      }
    );
  }

  copyQrCodeToClipboard() {
    const canvas = this.qrCodeElement!.nativeElement.querySelector('canvas');

    if (!canvas) {
      console.error('Canvas element not found');
      return;
    }

    canvas.toBlob(blob => {
      if (!blob) {
        console.error('Failed to create blob from canvas');
        return;
      }

      const data = [new ClipboardItem({[blob.type]: blob})];
      navigator.clipboard.write(data).then(
        () => {
          this.notificationService.success(
            'Survey QR code copied into the clipboard'
          );
        },
        _ => {
          this.notificationService.error(
            'Impossible to copy Survey QR code into the clipboard'
          );
        }
      );
    });
  }
}
