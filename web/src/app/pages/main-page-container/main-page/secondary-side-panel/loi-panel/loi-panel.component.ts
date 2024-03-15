/**
 * Copyright 2023 The Ground Authors.
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
import {List} from 'immutable';
import {Subscription, switchMap} from 'rxjs';

import {LocationOfInterest} from 'app/models/loi.model';
import {Submission} from 'app/models/submission/submission.model';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SubmissionService} from 'app/services/submission/submission.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {getLoiIcon} from 'app/utils/utils';

@Component({
  selector: 'ground-loi-panel',
  templateUrl: './loi-panel.component.html',
  styleUrls: ['./loi-panel.component.scss'],
})
export class LocationOfInterestPanelComponent implements OnInit, OnDestroy {
  subscription: Subscription = new Subscription();

  loi!: LocationOfInterest;
  name!: string | null;
  icon!: string;
  iconColor!: string;
  submissions!: List<Submission>;

  constructor(
    private loiService: LocationOfInterestService,
    private surveyService: SurveyService,
    private submissionService: SubmissionService,
    private navigationService: NavigationService
  ) {}

  ngOnInit() {
    this.subscription.add(
      this.surveyService
        .getActiveSurvey$()
        .pipe(
          switchMap(survey =>
            this.loiService.getSelectedLocationOfInterest$().pipe(
              switchMap(loi => {
                this.iconColor = survey.getJob(loi.jobId)!.color!;
                this.loi = loi;
                this.name = LocationOfInterestService.getDisplayName(loi);
                this.icon = getLoiIcon(loi);

                return this.submissionService.submissions$(survey, loi);
              })
            )
          )
        )
        .subscribe(submissions => (this.submissions = submissions))
    );
  }

  onSelectSubmission(submissionId: string) {
    this.navigationService.showSubmissionDetail(this.loi.id, submissionId);
  }

  onClosePanel() {
    this.navigationService.clearLocationOfInterestId();
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
