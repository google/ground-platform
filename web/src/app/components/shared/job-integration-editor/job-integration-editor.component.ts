/**
 * Copyright 2026 The Ground Authors.
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

import { Component, input } from '@angular/core';
import { Observable } from 'rxjs';

import { Survey } from 'app/models/survey.model';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';

interface IntegrationMetadata {
  id: string;
  title: string;
  description: string;
}

@Component({
  selector: 'job-integration-editor',
  templateUrl: './job-integration-editor.component.html',
  styleUrls: ['./job-integration-editor.component.scss'],
  standalone: false,
})
export class JobIntegrationEditorComponent {
  jobId = input<string>();

  readonly integrations: IntegrationMetadata[] = [
    {
      id: 'whisp',
      title: $localize`:@@app.cards.whispIntegration.title:Whisp integration`,
      description: $localize`:@@app.cards.whispIntegration.description:Enable Whisp integration for this job.`,
    },
    {
      id: 'geoid',
      title: $localize`:@@app.cards.geoidIntegration.title:GeoID integration`,
      description: $localize`:@@app.cards.geoidIntegration.description:Enable GeoID integration for this job.`,
    },
  ];

  survey$: Observable<Survey>;

  constructor(private draftSurveyService: DraftSurveyService) {
    this.survey$ = this.draftSurveyService.getSurvey$();
  }
}
