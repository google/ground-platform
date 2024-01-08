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

import {Component} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';

import {
  DialogData,
  DialogType,
  JobDialogComponent,
} from 'app/pages/edit-survey/job-dialog/job-dialog.component';
import {DraftSurveyService} from 'app/services/draft-survey/draft-survey.service';
import {NavigationService} from 'app/services/navigation/navigation.service';

export enum HeaderState {
  DEFAULT = 1,
  MAP_VIEW = 2,
  EDIT_SURVEY = 3,
}

@Component({
  selector: 'ground-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
})
export class HeaderComponent {
  surveyId = '';
  state = HeaderState.DEFAULT;

  constructor(
    public dialog: MatDialog,
    public draftSurveyService: DraftSurveyService,
    public navigationService: NavigationService
  ) {}

  async ngOnInit(): Promise<void> {
    this.navigationService.getSurveyId$()?.subscribe(surveyId => {
      if (surveyId) this.surveyId = surveyId;
    });
    if (this.navigationService.isSurveyPage(this.surveyId)) {
      this.state = HeaderState.MAP_VIEW;
    } else if (this.navigationService.isEditSurveyPage(this.surveyId)) {
      this.state = HeaderState.EDIT_SURVEY;
    }
  }

  onSurveysButtonClick(): void {
    this.navigationService.navigateToSurveyList();
  }

  onEditSurveyClick() {
    this.navigationService.navigateToEditSurvey(this.surveyId);
  }

  onCancelEditSurveyClick() {
    if (!this.draftSurveyService.dirty) {
      this.navigationService.selectSurvey(this.surveyId);
      return;
    }

    const dialogRef = this.dialog.open(JobDialogComponent, {
      data: {dialogType: DialogType.UndoJobs},
    });

    dialogRef.afterClosed().subscribe(async (result: DialogData) => {
      if (result?.dialogType === DialogType.UndoJobs)
        this.navigationService.selectSurvey(this.surveyId);
    });
  }

  async onFinishEditSurveyClick() {
    await this.draftSurveyService.updateSurvey();
  }

  isDraftSurveyDirty() {
    return this.draftSurveyService.dirty;
  }
}
