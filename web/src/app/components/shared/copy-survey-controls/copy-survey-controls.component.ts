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

import { Component, ElementRef, Input, OnInit, ViewChild } from '@angular/core';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { NotificationService } from 'app/services/notification/notification.service';

/**
 * Implements the controls for copying the survey link and QR code
 * to the clipboard.
 * This component is typically displayed in the survey create/edit
 * view, enabling users to quickly share the survey.
 */
@Component({
  selector: 'ground-copy-survey-controls',
  templateUrl: './copy-survey-controls.component.html',
  styleUrls: ['./copy-survey-controls.component.scss'],
  standalone: false,
})
export class CopySurveyControlsComponent implements OnInit {
  @Input() surveyId = '';

  @ViewChild('qrCodeElement', { read: ElementRef })
  qrCodeElement!: ElementRef<HTMLCanvasElement>;

  surveyAppLink = '';

  constructor(
    private readonly navigationService: NavigationService,
    private readonly notificationService: NotificationService
  ) {}

  ngOnInit() {
    this.surveyAppLink = this.navigationService.getSurveyAppLink(this.surveyId);
  }

  copyLinkToClipboard() {
    navigator.clipboard.writeText(this.surveyAppLink).then(
      () => {
        this.notificationService.success('Survey link copied to clipboard');
      },
      _ => {
        this.notificationService.error(
          'Impossible to copy Survey link to clipboard'
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

      const data = [new ClipboardItem({ [blob.type]: blob })];
      navigator.clipboard.write(data).then(
        () => {
          this.notificationService.success(
            'Survey QR code copied to clipboard'
          );
        },
        _ => {
          this.notificationService.error(
            'Impossible to copy Survey QR code to clipboard'
          );
        }
      );
    });
  }
}
