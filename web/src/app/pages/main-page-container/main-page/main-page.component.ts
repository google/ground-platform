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

import {Component, OnInit} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {Observable, Subscription, combineLatest} from 'rxjs';
import {take} from 'rxjs/operators';

import {Survey, SurveyState} from 'app/models/survey.model';
import {AuthService} from 'app/services/auth/auth.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SubmissionService} from 'app/services/submission/submission.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {environment} from 'environments/environment';

import {JobDialogComponent} from './job-dialog/job-dialog.component';
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
  activeSurvey$: Observable<Survey>;
  subscription: Subscription = new Subscription();
  shouldEnableDrawingTools = false;
  showSubmissionPanel: Boolean = false;
  constructor(
    private navigationService: NavigationService,
    private surveyService: SurveyService,
    private loiService: LocationOfInterestService,
    private submissionService: SubmissionService,
    private authService: AuthService,
    private dialog: MatDialog
  ) {
    this.activeSurvey$ = this.surveyService.getActiveSurvey$();
  }

  ngOnInit() {
    // Show title dialog to assign title on a new survey.
    this.subscription.add(
      this.navigationService
        .getSurveyId$()
        .subscribe(
          id => id === NavigationService.JOB_ID_NEW && this.showTitleDialog()
        )
    );
    // Show job dialog when non-null job id set in URL.
    this.subscription.add(
      this.navigationService
        .getJobId$()
        .subscribe(id => id && this.showEditJobDialog(id))
    );
    // Show loi details when non-null LOI id set in URL.
    // Show submission details when submission id set in URL.
    this.subscription.add(
      combineLatest([
        this.navigationService.getLocationOfInterestId$(),
        this.navigationService.getSubmissionId$(),
      ]).subscribe(([loiId, submissionId]) => {
        if (loiId) this.loadLocationOfInterestDetails(loiId);
        if (submissionId) this.loadSubmissionDetails(submissionId);
      })
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

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  private showTitleDialog() {
    this.dialog.open(TitleDialogComponent, {
      width: '500px',
      disableClose: true,
    });
  }

  private showEditJobDialog(jobId: string) {
    this.activeSurvey$.pipe(take(1)).subscribe(survey =>
      this.dialog.open(JobDialogComponent, {
        autoFocus: jobId === NavigationService.JOB_ID_NEW,
        data: {
          surveyId:
            survey.state === SurveyState.UNSAVED
              ? NavigationService.SURVEY_ID_NEW
              : survey.id,
          createJob: jobId === NavigationService.SURVEY_ID_NEW,
          job: survey.jobs?.get(jobId),
        },
        panelClass: 'job-dialog-container',
      })
    );
  }

  private loadLocationOfInterestDetails(loiId: string) {
    this.loiService.selectLocationOfInterest(loiId);
  }

  private loadSubmissionDetails(submissionId: string) {
    this.submissionService.selectSubmission(submissionId);
  }
}
