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

import { Component, EventEmitter, Input, Output, input } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Map } from 'immutable';

import { ShareDialogComponent } from 'app/components/shared/share-dialog/share-dialog.component';
import { Role } from 'app/models/role.model';
import { Survey } from 'app/models/survey.model';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SurveyService } from 'app/services/survey/survey.service';

@Component({
  selector: 'ground-survey-header',
  templateUrl: './survey-header.component.html',
  styleUrls: ['./survey-header.component.scss'],
  standalone: false,
})
export class SurveyHeaderComponent {
  activeSurvey = input<Survey>();
  @Input() isDirty = false;
  @Input() isValid = true;
  @Output() onAclChange = new EventEmitter<Map<string, Role>>();
  @Output() onPublish = new EventEmitter<void>();
  @Output() onCancel = new EventEmitter<void>();

  constructor(
    public navigationService: NavigationService,
    public surveyService: SurveyService,
    private dialog: MatDialog
  ) {}

  /**
   * Updates the survey title with input element value.
   *
   * @param evt the event emitted from the input element on blur.
   */
  updateSurveyTitle(value: string): Promise<void> {
    const survey = this.activeSurvey();
    if (!survey || value === survey.title) return Promise.resolve();
    return this.surveyService.updateTitle(survey.id, value);
  }

  onSurveysButtonClick(): void {
    this.navigationService.navigateToSurveyList();
  }

  openShareDialog(): void {
    const survey = this.activeSurvey();
    if (!survey) return;

    this.dialog
      .open(ShareDialogComponent, {
        width: '580px',
        autoFocus: false,
        data: { survey },
      })
      .afterClosed()
      .subscribe(result => {
        if (result && result.acl) {
          if (this.isEditSurveyPage()) {
            this.onAclChange.emit(result.acl);
          } else {
            this.surveyService.updateAcl(survey, result.acl);
          }
        }
      });
  }

  onClickSidePanelButtonEvent() {
    this.navigationService.onClickSidePanelButton();
  }

  isEditSurveyPage() {
    if (!this.activeSurvey()) {
      console.error('No active survey');
      return;
    }
    return this.navigationService.isEditSurveyPage(this.activeSurvey()!.id);
  }
}
