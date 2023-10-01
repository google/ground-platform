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

import {Component, Input, OnDestroy} from '@angular/core';
import {MatLegacyDialog as MatDialog} from '@angular/material/legacy-dialog';
import {SurveyService} from 'app/services/survey/survey.service';
import {Subscription} from 'rxjs';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {ShareDialogComponent} from 'app/components/share-dialog/share-dialog.component';
import {Survey} from 'app/models/survey.model';

@Component({
  selector: 'ground-survey-header',
  templateUrl: './survey-header.component.html',
  styleUrls: ['./survey-header.component.scss'],
})
export class SurveyHeaderComponent implements OnDestroy {
  @Input() survey: Survey = Survey.UNSAVED_NEW;

  title: string;
  surveyId!: string;

  subscription: Subscription = new Subscription();
  constructor(
    public navigationService: NavigationService,
    public surveyService: SurveyService,
    private dialog: MatDialog
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

  openShareDialog(): void {
    this.dialog.open(ShareDialogComponent, {
      width: '580px',
      autoFocus: false,
    });
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
