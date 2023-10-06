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

import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {Component} from '@angular/core';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {LocationOfInterest} from 'app/models/loi.model';
import {GeometryType} from 'app/models/geometry/geometry';
import {SurveyService} from 'app/services/survey/survey.service';
import {SubmissionService} from 'app/services/submission/submission.service';
import {Observable, switchMap} from 'rxjs';
import {Submission} from 'app/models/submission/submission.model';
import {List} from 'immutable';

@Component({
  selector: 'ground-loi-panel',
  templateUrl: './loi-panel.component.html',
  styleUrls: ['./loi-panel.component.scss'],
})
export class LocationOfInterestPanelComponent {
  loi!: LocationOfInterest;
  name!: string | null;
  icon!: string;
  submissions!: List<Submission>;

  constructor(
    private loiService: LocationOfInterestService,
    private surveyService: SurveyService,
    private submissionService: SubmissionService,
    private navigationService: NavigationService
  ) {
    this.loiService.getSelectedLocationOfInterest$().subscribe(loi => {
      this.loi = loi;
      this.name =
        LocationOfInterestService.getLoiNameFromProperties(loi) ??
        LocationOfInterestService.getAnonymousDisplayName({
          loi,
          index: 0,
        });
      this.icon = this.getLoiIcon(loi);
    });

    this.surveyService
      .getActiveSurvey$()
      .pipe(
        switchMap(survey =>
          loiService
            .getSelectedLocationOfInterest$()
            .pipe(
              switchMap(loi => this.submissionService.submissions$(survey, loi))
            )
        )
      )
      .subscribe(submissions => (this.submissions = submissions));
  }

  getLoiIcon(loi: LocationOfInterest): string {
    let icon = 'point';
    const type = loi.geometry?.geometryType;
    if (type === GeometryType.POLYGON || type === GeometryType.MULTI_POLYGON) {
      icon = 'polygon';
    }
    return icon;
  }

  onSelectSubmission(submissionId: string) {
    this.navigationService.showSubmissionDetail(submissionId);
  }
}
