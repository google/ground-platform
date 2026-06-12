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

import { Component, inject, input } from '@angular/core';

import { EditSurveySession } from 'app/services/edit-survey-session/edit-survey-session';

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

  private editSurveySession = inject(EditSurveySession);

  survey = this.editSurveySession.survey;
}
