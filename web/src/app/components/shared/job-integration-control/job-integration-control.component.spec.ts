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

import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  MatSlideToggleChange,
  MatSlideToggleModule,
} from '@angular/material/slide-toggle';
import { Map } from 'immutable';

import { Job } from 'app/models/job.model';
import { AuthService } from 'app/services/auth/auth.service';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';

import { JobIntegrationControlComponent } from './job-integration-control.component';

describe('JobIntegrationControlComponent', () => {
  let component: JobIntegrationControlComponent;
  let fixture: ComponentFixture<JobIntegrationControlComponent>;

  let draftSurveyService: jasmine.SpyObj<DraftSurveyService>;

  const integrationId = 'integration1';

  const mockJobBase = new Job('job1', 0, '#000', 'Test Job');

  beforeEach(async () => {
    draftSurveyService = jasmine.createSpyObj('DraftSurveyService', [
      'addOrUpdateJob',
    ]);

    await TestBed.configureTestingModule({
      declarations: [JobIntegrationControlComponent],
      imports: [MatSlideToggleModule],
      providers: [
        { provide: AuthService, useValue: {} },
        { provide: DraftSurveyService, useValue: draftSurveyService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(JobIntegrationControlComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('reflects false when job has no enabled integrations', () => {
    fixture.componentRef.setInput('surveyId', 'survey1');
    fixture.componentRef.setInput('integrationId', integrationId);
    fixture.componentRef.setInput('job', mockJobBase);
    fixture.detectChanges();

    expect(component.integrationEnabled).toBeFalse();
  });

  it('reflects true when job has the integration enabled', () => {
    const job = mockJobBase.copyWith({
      enabledIntegrations: Map([[integrationId, { id: integrationId }]]),
    });
    fixture.componentRef.setInput('surveyId', 'survey1');
    fixture.componentRef.setInput('integrationId', integrationId);
    fixture.componentRef.setInput('job', job);
    fixture.detectChanges();

    expect(component.integrationEnabled).toBeTrue();
  });

  it('adds integration and calls service when toggle is turned on', () => {
    fixture.componentRef.setInput('surveyId', 'survey1');
    fixture.componentRef.setInput('integrationId', integrationId);
    fixture.componentRef.setInput('job', mockJobBase);
    fixture.detectChanges();

    component.onIntegrationToggle({ checked: true } as MatSlideToggleChange);

    const updatedJob: Job =
      draftSurveyService.addOrUpdateJob.calls.mostRecent().args[0];
    expect(updatedJob.enabledIntegrations.has(integrationId)).toBeTrue();
    expect(updatedJob.enabledIntegrations.get(integrationId)).toEqual({
      id: integrationId,
    });
  });

  it('removes integration and calls service when toggle is turned off', () => {
    const job = mockJobBase.copyWith({
      enabledIntegrations: Map([[integrationId, { id: integrationId }]]),
    });
    fixture.componentRef.setInput('surveyId', 'survey1');
    fixture.componentRef.setInput('integrationId', integrationId);
    fixture.componentRef.setInput('job', job);
    fixture.detectChanges();

    component.onIntegrationToggle({ checked: false } as MatSlideToggleChange);

    const updatedJob: Job =
      draftSurveyService.addOrUpdateJob.calls.mostRecent().args[0];
    expect(updatedJob.enabledIntegrations.has(integrationId)).toBeFalse();
  });
});
