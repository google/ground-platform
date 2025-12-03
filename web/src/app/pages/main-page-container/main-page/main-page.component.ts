import {Component, OnInit, effect, Injector} from '@angular/core';
import {toObservable} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';
import {Observable, Subscription, map} from 'rxjs';

import {Survey} from 'app/models/survey.model';
import {AuthService} from 'app/services/auth/auth.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
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
  private urlParamsSignal = this.navigationService.getUrlParams();
  private surveyId$: Observable<string | null>;

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
    this.surveyId$ = toObservable(this.urlParamsSignal).pipe(
      map(params => params.surveyId)
    );

    effect(() => {
      const {loiId, submissionId} = this.urlParamsSignal();
      if (loiId) this.loiService.selectLocationOfInterest(loiId);
      if (submissionId) this.submissionService.selectSubmission(submissionId);
    });
  }

  ngOnInit() {
    // Show title dialog to assign title on a new survey.
    this.subscription.add(
      this.surveyId$.subscribe(
        id => id === NavigationService.JOB_ID_NEW && this.showTitleDialog()
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
