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

import {Component, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {Observable, Subscription} from 'rxjs';

import {Survey} from 'app/models/survey.model';
import {AuthService} from 'app/services/auth/auth.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {environment} from 'environments/environment';

@Component({
  selector: 'ground-survey-dashboard',
  templateUrl: './survey-dashboard.component.html',
  styleUrls: ['./survey-dashboard.component.scss'],
})
export class SurveyDashboardComponent implements OnInit, OnDestroy {
  activeSurvey$: Observable<Survey>;
  private subscription = new Subscription();

  constructor(
    private authService: AuthService,
    private navigationService: NavigationService,
    private route: ActivatedRoute,
    private surveyService: SurveyService
  ) {
    this.activeSurvey$ = surveyService.getActiveSurvey$();
    navigationService.init(this.route);
  }

  ngOnInit() {
    // Activate new survey on route changes.
    this.subscription.add(
      this.navigationService.getSurveyId$().subscribe(surveyId => {
        if (!surveyId) return;
        if (this.surveyService.getActiveSurvey()?.id === surveyId) return;
        this.surveyService.activateSurvey(surveyId);
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
}
