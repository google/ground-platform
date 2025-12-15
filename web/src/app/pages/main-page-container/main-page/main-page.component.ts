/**
 * Copyright 2019 The Ground Authors.
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

import {Component, OnInit, effect, input} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {Subscription} from 'rxjs';

import {Survey} from 'app/models/survey.model';
import {AuthService} from 'app/services/auth/auth.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {JOB_ID_NEW} from 'app/services/navigation/navigation.constants';
import {SubmissionService} from 'app/services/submission/submission.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {environment} from 'environments/environment';

import {TitleDialogComponent} from './title-dialog/title-dialog.component';

/**
 * Root component for main application page showing map, jobs list, and
 * survey header. Responsible for coordinating page-level URL states with
 * various services.
 */
@Component({
  selector: 'ground-main-page',
  templateUrl: './main-page.component.html',
  styleUrls: ['./main-page.component.scss'],
})
export class MainPageComponent implements OnInit {
  activeSurvey = input.required<Survey>();
  private urlParamsSignal = this.navigationService.getUrlParams();

  subscription: Subscription = new Subscription();
  showSubmissionPanel: Boolean = false;

  constructor(
    private navigationService: NavigationService,
    private surveyService: SurveyService,
    private loiService: LocationOfInterestService,
    private submissionService: SubmissionService,
    private authService: AuthService,
    private dialog: MatDialog
  ) {
    effect(() => {
      const {loiId, submissionId} = this.urlParamsSignal();
      if (loiId) this.loiService.selectLocationOfInterest(loiId);
      if (submissionId) this.submissionService.selectSubmission(submissionId);
    });
  }

  ngOnInit() {
    // Show title dialog to assign title on a new survey.
    this.subscription.add(
      this.navigationService
        .getSurveyId$()
        .subscribe(
          id => id === JOB_ID_NEW && this.showTitleDialog()
        )
    );
    // Redirect to sign in page if user is not authenticated.
    this.subscription.add(
      this.authService.isAuthenticated$().subscribe(isAuthenticated => {
        if (!isAuthenticated && !environment.useEmulators) {
          this.navigationService.signIn();
        }
      })
    );
  }

  private showTitleDialog() {
    this.dialog.open(TitleDialogComponent, {
      width: '500px',
      disableClose: true,
    });
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
