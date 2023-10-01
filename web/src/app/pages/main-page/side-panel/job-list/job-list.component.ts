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
import {MatIconRegistry} from '@angular/material/icon';
import {DomSanitizer} from '@angular/platform-browser';
import {SurveyService} from 'app/services/survey/survey.service';
import {Observable} from 'rxjs';
import {map} from 'rxjs/internal/operators/map';
import {Job} from 'app/models/job.model';
import {List} from 'immutable';
import {NavigationService} from 'app/services/navigation/navigation.service';

// TODO: Make custom svgs match the color of the job icon
const POINT_ICON = `
  <svg width="36" height="36" viewBox="0 0 36 36" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path fill-rule="evenodd" clip-rule="evenodd" d="M10 17.4244V18.5756C10 20.2568 10.2051 22.0009 10.6154 22.9235C11.0256 23.8462 11.8462 24.7697 12.8718 25.2825C13.8974 25.7953 15.7436 26 17.4244 26H18.5756C20.2564 26 22.1026 25.7953 23.1282 25.2825C24.1538 24.7697 24.9744 23.8462 25.3846 22.9235C25.7949 22.0009 26 20.2568 26 18.5756V17.4244C26 15.7432 25.7949 13.9991 25.3846 13.0765C24.9744 12.1538 24.1538 11.2303 23.1282 10.7175C22.1026 10.2047 20.2564 10 18.5756 10H17.4244C15.7436 10 13.8974 10.2047 12.8718 10.7175C11.8462 11.2303 11.0256 12.1538 10.6154 13.0765C10.2051 13.9991 10 15.7432 10 17.4244ZM16.0383 15.2383C15.5965 15.2383 15.2383 15.5965 15.2383 16.0383V19.9621C15.2383 20.4039 15.5965 20.7621 16.0383 20.7621H19.9621C20.4039 20.7621 20.7621 20.4039 20.7621 19.9621V16.0383C20.7621 15.5965 20.4039 15.2383 19.9621 15.2383H16.0383Z" fill="currentColor"/>
  </svg>
`;

const POLYGON_ICON = `
  <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
    <ellipse cx="3.99381" cy="16.2562" rx="2.07712" ry="2.07712" fill="currentColor"/>
    <ellipse cx="16.2553" cy="16.2562" rx="2.07712" ry="2.07712" fill="currentColor"/>
    <ellipse cx="16.1882" cy="10.0259" rx="2.07712" ry="2.07712" fill="currentColor"/>
    <circle cx="3.99381" cy="3.74375" r="2.07712" fill="currentColor"/>
    <rect x="6.60785" y="15.2679" width="7.03542" height="1.87611" fill="currentColor"/>
    <rect x="7.04443" y="4.01172" width="8.20297" height="2.07712" transform="rotate(25.8625 7.04443 4.01172)" fill="currentColor"/>
    <rect x="15.2168" y="13.66" width="1.03856" height="2.07712" transform="rotate(-90 15.2168 13.66)" fill="currentColor"/>
    <rect x="2.85406" y="13.392" width="7.03542" height="2.34514" transform="rotate(-90 2.85406 13.392)" fill="currentColor"/>
  </svg>
`;

@Component({
  selector: 'ground-job-list',
  templateUrl: './job-list.component.html',
  styleUrls: ['./job-list.component.scss'],
})
export class JobListComponent {
  readonly jobs$: Observable<List<Job>>;

  constructor(
    private iconRegistry: MatIconRegistry,
    private sanitizer: DomSanitizer,
    readonly surveyService: SurveyService,
    private navigationService: NavigationService
  ) {
    // Add custom loi icons for job list items
    iconRegistry.addSvgIconLiteral(
      'point',
      sanitizer.bypassSecurityTrustHtml(POINT_ICON)
    );
    iconRegistry.addSvgIconLiteral(
      'polygon',
      sanitizer.bypassSecurityTrustHtml(POLYGON_ICON)
    );

    this.jobs$ = surveyService
      .requireActiveSurvey$()
      .pipe(
        map(survey =>
          List(survey.jobs.valueSeq().toArray()).sortBy(l => l.index)
        )
      );
  }

  onAddJob() {
    this.navigationService.customizeJob(NavigationService.JOB_ID_NEW);
  }
}
