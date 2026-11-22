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

import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Map } from 'immutable';
import { of } from 'rxjs';

import { Job } from 'app/models/job.model';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';

import { JobIntegrationEditorComponent } from './job-integration-editor.component';

describe('JobIntegrationEditorComponent', () => {
  let fixture: ComponentFixture<JobIntegrationEditorComponent>;
  let draftSurveyService: jasmine.SpyObj<DraftSurveyService>;

  const jobId = 'job001';
  const job = new Job(jobId, 0);
  const survey = new Survey(
    'survey1',
    'title1',
    'description1',
    /* jobs= */ Map({ [jobId]: job }),
    /* acl= */ Map(),
    /* ownerId= */ '',
    { type: DataSharingType.PRIVATE }
  );

  beforeEach(async () => {
    draftSurveyService = jasmine.createSpyObj<DraftSurveyService>(
      'DraftSurveyService',
      ['getSurvey$']
    );
    draftSurveyService.getSurvey$.and.returnValue(of(survey));

    await TestBed.configureTestingModule({
      declarations: [JobIntegrationEditorComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: DraftSurveyService, useValue: draftSurveyService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(JobIntegrationEditorComponent);
    fixture.componentRef.setInput('jobId', jobId);
    fixture.detectChanges();
  });

  it('renders one control per integration', () => {
    const controls = fixture.nativeElement.querySelectorAll(
      'ground-job-integration-control'
    );
    expect(controls.length).toBe(fixture.componentInstance.integrations.length);
  });

  it('passes the correct integrationId to each control', () => {
    const controls = fixture.debugElement.queryAll(
      el => el.name === 'ground-job-integration-control'
    );
    const ids = controls.map(c => c.properties['integrationId']);
    expect(ids).toEqual(
      fixture.componentInstance.integrations.map(i => i.id)
    );
  });

  it('passes the correct surveyId to each control', () => {
    const controls = fixture.debugElement.queryAll(
      el => el.name === 'ground-job-integration-control'
    );
    controls.forEach(control => {
      expect(control.properties['surveyId']).toBe(survey.id);
    });
  });

  it('passes the job matching jobId to each control', () => {
    const controls = fixture.debugElement.queryAll(
      el => el.name === 'ground-job-integration-control'
    );
    controls.forEach(control => {
      expect(control.properties['job']).toBe(job);
    });
  });
});
