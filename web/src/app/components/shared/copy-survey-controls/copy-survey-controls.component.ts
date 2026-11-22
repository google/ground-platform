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

import { Component, Input, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';

import { QrCodeDialogComponent } from 'app/components/shared/qr-code-dialog/qr-code-dialog.component';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { NotificationService } from 'app/services/notification/notification.service';

/**
 * Implements the controls for copying the survey link and showing
 * the QR code.
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

  surveyAppLink = '';

  constructor(
    private readonly navigationService: NavigationService,
    private readonly notificationService: NotificationService,
    private readonly dialog: MatDialog
  ) {}

  ngOnInit() {
    this.surveyAppLink = this.navigationService.getSurveyAppLink(this.surveyId);
  }

  copyLinkToClipboard() {
    navigator.clipboard.writeText(this.surveyAppLink).then(
      () => {
        this.notificationService.success(
          $localize`:@@app.notifications.success.surveyLinkCopied:Survey link copied to clipboard`
        );
      },
      _ => {
        this.notificationService.error(
          $localize`:@@app.notifications.error.surveyLinkCopied:Impossible to copy Survey link to clipboard`
        );
      }
    );
  }

  showQrCode() {
    this.dialog.open(QrCodeDialogComponent, {
      autoFocus: false,
      data: { surveyAppLink: this.surveyAppLink },
    });
  }
}
