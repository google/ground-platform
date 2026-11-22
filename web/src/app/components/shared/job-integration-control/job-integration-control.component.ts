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

import { Component, effect, input } from '@angular/core';
import { MatSlideToggleChange } from '@angular/material/slide-toggle';
import { Integration, Job } from 'app/models/job.model';

import { AuthService } from 'app/services/auth/auth.service';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';

@Component({
  selector: 'ground-job-integration-control',
  templateUrl: './job-integration-control.component.html',
  standalone: false,
})
export class JobIntegrationControlComponent {
  surveyId = input<string>();
  integrationId = input<string>();
  job = input<Job>();

  integrationEnabled!: boolean;

  constructor(
    readonly authService: AuthService,
    readonly draftSurveyService: DraftSurveyService
  ) {
    effect(() => {
      const surveyId = this.surveyId();
      const integrationId = this.integrationId();
      const job = this.job();
      if (surveyId && integrationId && job) {
        this.integrationEnabled = job.enabledIntegrations.has(integrationId) || false;
      }
    });
  }

  onIntegrationToggle(event: MatSlideToggleChange) {
    const integrationId = this.integrationId();
    const job = this.job();
    if (integrationId && job) {
      const updatedIntegrations = event.checked
        ? job.enabledIntegrations.set(integrationId, {
            id: integrationId,
          } as Integration)
        : job.enabledIntegrations.delete(integrationId);

      this.draftSurveyService.addOrUpdateJob(
        job.copyWith({ enabledIntegrations: updatedIntegrations })
      );
    }
  }
}
