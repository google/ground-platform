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

import { Component, Input, OnChanges } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Survey } from 'app/models/survey.model';

import { AuthService } from 'app/services/auth/auth.service';
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
export class HeaderComponent implements OnChanges {
  @Input() survey: Survey | null = null;
  surveyId = '';
  state = HeaderState.DEFAULT;
  readonly HeaderState = HeaderState;
  canManage = false;

  constructor(
    public dialog: MatDialog,
    public authService: AuthService,
    public navigationService: NavigationService,
    public surveyService: SurveyService
  ) {}

  ngOnChanges(): void {
    if (!this.survey) {
      this.surveyId = '';
      this.state = HeaderState.DEFAULT;
      return;
    }
    const { id: surveyId } = this.survey;

    this.surveyId = surveyId;

    this.canManage = this.surveyService.canManageSurvey(this.survey);

    if (this.navigationService.isEditSurveyPage(this.surveyId)) {
      this.state = HeaderState.EDIT_SURVEY;
    } else if (this.navigationService.isSurveyPage(this.surveyId)) {
      this.state = HeaderState.MAP_VIEW;
    }
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
}
