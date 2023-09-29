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

import { Component } from '@angular/core';
import { Survey } from 'app/models/survey.model';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SurveyService } from 'app/services/survey/survey.service';
import { Observable, switchMap, tap } from 'rxjs';

@Component({
  selector: 'ground-survey-container',
  templateUrl: './survey-container.component.html',
  styleUrls: ['./survey-container.component.css']
})
export class SurveyContainerComponent {
  private survey$: Observable<Survey | undefined>;

  constructor(surveyService: SurveyService, navigationService: NavigationService) {
    this.survey$ = navigationService.getSurveyId$().pipe(
      tap(x => console.log("before: ", x)),
      switchMap(id => surveyService.getSurvey$(id || undefined)),
      tap(x => console.log("after: ", x))
    );
  }
}
