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

import {Component, Input} from '@angular/core';
import {LocationOfInterest} from 'app/models/loi.model';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {List} from 'immutable';
import {Observable, map} from 'rxjs';

@Component({
  selector: 'survey-loi',
  templateUrl: './survey-loi.component.html',
  styleUrls: ['./survey-loi.component.scss'],
})
export class SurveyLoiComponent {
  lois$!: Observable<List<LocationOfInterest>>;

  constructor(
    readonly loiService: LocationOfInterestService,
    readonly surveyService: SurveyService
  ) {}

  ngOnInit() {
    this.lois$ = this.loiService
      .getLocationsOfInterest$()
      .pipe(map(lois => LocationOfInterestService.getLoisWithNames(lois)));
  }
}
