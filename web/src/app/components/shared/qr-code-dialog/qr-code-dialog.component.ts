/**
 * Copyright 2026 The Ground Authors.
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

import { Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { NotificationService } from 'app/services/notification/notification.service';

export interface QrCodeDialogData {
  surveyAppLink: string;
}

@Component({
  selector: 'ground-qr-code-dialog',
  templateUrl: './qr-code-dialog.component.html',
  styleUrls: ['./qr-code-dialog.component.scss'],
  standalone: false,
})
export class QrCodeDialogComponent {
  @ViewChild('qrCodeElement', { read: ElementRef })
  qrCodeElement!: ElementRef<HTMLElement>;

  surveyAppLink: string;

  constructor(
    private dialogRef: MatDialogRef<QrCodeDialogComponent>,
    private readonly notificationService: NotificationService,
    @Inject(MAT_DIALOG_DATA) data: QrCodeDialogData
  ) {
    this.surveyAppLink = data.surveyAppLink;
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

      const data = [new ClipboardItem({ [blob.type]: blob })];
      navigator.clipboard.write(data).then(
        () => {
          this.notificationService.success(
            $localize`:@@app.notifications.success.surveyQrCodeCopied:Survey QR code copied to clipboard`
          );
        },
        _ => {
          this.notificationService.error(
            $localize`:@@app.notifications.error.surveyQrCodeCopied:Impossible to copy Survey QR code to clipboard`
          );
        }
      );
    });
  }

  closeDialog() {
    this.dialogRef.close();
  }
}
