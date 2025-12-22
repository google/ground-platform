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

import { DataStoreService } from 'app/services/data-store/data-store.service';
import { SurveyService } from 'app/services/survey/survey.service';

@Component({
    selector: 'survey-json',
    templateUrl: './survey-json.component.html',
    styleUrls: ['./survey-json.component.scss'],
    standalone: false
})
export class SurveyJsonComponent {
  surveyId?: string;
  json = '';

  constructor(
    private dataStoreService: DataStoreService,
    private surveyService: SurveyService
  ) {
    this.surveyService.getActiveSurvey$().subscribe(async survey => {
      this.surveyId = survey.id;

      this.json = JSON.stringify(
        await this.dataStoreService.loadRawSurvey(this.surveyId),
        null,
        2
      );
    });
  }

  async onSave() {
    this.dataStoreService.saveRawSurvey(this.surveyId!, JSON.parse(this.json));
  }
}
