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

import { Component, OnInit, ElementRef, OnDestroy } from '@angular/core';
import { AuthService } from './../../services/auth/auth.service';
import { UserProfilePopupComponent } from '../../components/user-profile-popup/user-profile-popup.component';
import { MatDialog } from '@angular/material/dialog';
import { SurveyService } from '../../services/survey/survey.service';
import { Subscription } from 'rxjs';
import { NavigationService } from '../../services/navigation/navigation.service';
import { ShareDialogComponent } from '../share-dialog/share-dialog.component';

@Component({
  selector: 'ground-survey-header',
  templateUrl: './survey-header.component.html',
  styleUrls: ['./survey-header.component.scss'],
})
export class SurveyHeaderComponent implements OnInit, OnDestroy {
  title: string;
  surveyId!: string;

  subscription: Subscription = new Subscription();
  constructor(
    public auth: AuthService,
    public navigationService: NavigationService,
    private dialog: MatDialog,
    private surveyService: SurveyService
  ) {
    this.title = '';
    const activeSurvey$ = this.surveyService.getActiveSurvey$();
    this.subscription.add(
      activeSurvey$.subscribe(survey => {
        this.title = survey.title || '';
        this.surveyId = survey.id;
      })
    );
  }

  ngOnInit() {}

  openProfileDialog(evt: MouseEvent): void {
    const target = new ElementRef(evt.currentTarget);
    this.dialog.open(UserProfilePopupComponent, {
      data: { trigger: target },
    });
  }

  /**
   * Updates the survey title with input element value.
   *
   * @param evt the event emitted from the input element on blur.
   */
  updateSurveyTitle(value: string): Promise<void> {
    if (value === this.title) return Promise.resolve();
    return this.surveyService.updateTitle(this.surveyId, value);
  }

  onSurveysButtonClick(): void {
    this.navigationService.navigateToSurveyList();
  }

  private openShareDialog(): void {
    this.dialog.open(ShareDialogComponent, {
      width: '580px',
      autoFocus: false,
    });
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
