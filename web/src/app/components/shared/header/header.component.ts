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

import { Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import {
  DialogData,
  DialogType,
  JobDialogComponent,
} from 'app/components/edit-survey/job-dialog/job-dialog.component';
import { AuthService } from 'app/services/auth/auth.service';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SurveyService } from 'app/services/survey/survey.service';

export enum HeaderState {
  DEFAULT = 1,
  MAP_VIEW = 2,
  EDIT_SURVEY = 3,
}

@Component({
  selector: 'ground-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
  standalone: false,
})
export class HeaderComponent implements OnInit {
  surveyId = '';
  state = HeaderState.DEFAULT;
  readonly HeaderState = HeaderState;
  isPublishingChanges = false;
  canManage = false;

  constructor(
    public dialog: MatDialog,
    public authService: AuthService,
    public draftSurveyService: DraftSurveyService,
    public navigationService: NavigationService,
    public surveyService: SurveyService
  ) {}

  async ngOnInit(): Promise<void> {
    this.surveyService.getActiveSurvey$().subscribe(survey => {
      if (!survey) {
        this.surveyId = '';
        this.state = HeaderState.DEFAULT;
        return;
      }
      const { id: surveyId } = survey;

      this.surveyId = surveyId;

      this.canManage = this.surveyService.canManageSurvey();

      if (this.navigationService.isEditSurveyPage(this.surveyId)) {
        this.state = HeaderState.EDIT_SURVEY;
      } else if (this.navigationService.isSurveyPage(this.surveyId)) {
        this.state = HeaderState.MAP_VIEW;
      }
    });
  }

  onSurveysButtonClick(): void {
    this.navigationService.navigateToSurveyList();
  }

  onEditSurveyClick() {
    this.navigationService.navigateToEditSurvey(this.surveyId);
  }

  onAboutClick() {
    this.navigationService.navigateToAboutPage();
  }

  onTermsOfServiceClick() {
    this.navigationService.navigateToTermsOfService();
  }

  onCancelEditSurveyClick() {
    if (!this.draftSurveyService.dirty) {
      this.navigationService.selectSurvey(this.surveyId);
      return;
    }

    const dialogRef = this.dialog.open(JobDialogComponent, {
      data: { dialogType: DialogType.UndoJobs },
      panelClass: 'small-width-dialog',
    });

    dialogRef.afterClosed().subscribe(async (result: DialogData) => {
      if (result?.dialogType === DialogType.UndoJobs)
        this.navigationService.selectSurvey(this.surveyId);
    });
  }

  async onFinishEditSurveyClick() {
    if (this.isDraftSurveyValid()) {
      this.isPublishingChanges = true;
      await this.draftSurveyService.updateSurvey();
      this.isPublishingChanges = false;
      this.navigationService.selectSurvey(this.surveyId);
      return;
    }

    this.dialog.open(JobDialogComponent, {
      data: { dialogType: DialogType.InvalidSurvey },
      panelClass: 'small-width-dialog',
    });
  }

  isDraftSurveyValid(): boolean {
    return this.draftSurveyService.valid.reduce(
      (accumulator, currentValue) => accumulator && currentValue,
      true
    );
  }

  isDraftSurveyDirtyAndValid(): boolean {
    return this.draftSurveyService.dirty && this.isDraftSurveyValid();
  }
}
