/**
 * Copyright 2023 Google LLC
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

import {ActivatedRoute} from '@angular/router';
import {ActivatedRouteStub} from 'testing/activated-route-stub';
import {
  ComponentFixture,
  TestBed,
  waitForAsync,
  tick,
  fakeAsync,
  flush,
} from '@angular/core/testing';
import {CreateJobComponent} from 'app/pages/create-job/create-job.component';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {JobService} from 'app/services/job/job.service';
import {Subject} from 'rxjs';
import {Survey} from 'app/models/survey.model';
import {Map} from 'immutable';
import {By} from '@angular/platform-browser';
import {Job} from 'app/models/job.model';

describe('CreateJobComponent', () => {
  let component: CreateJobComponent;
  let fixture: ComponentFixture<CreateJobComponent>;
  let surveyId$: Subject<string | null>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let route: ActivatedRouteStub;
  let activeSurvey$: Subject<Survey>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;
  let jobServiceSpy: jasmine.SpyObj<JobService>;

  const surveyId = 'survey001';
  const jobId = 'job001';
  const name = 'jobX';
  const mockSurveyNoJob = new Survey(
    surveyId,
    'title',
    'description',
    /* jobs= */ Map(),
    /* acl= */ Map()
  );
  const mockJob = new Job(
    jobId,
    /* index */ 0,
    'red',
    name,
    /* tasks= */ Map()
  );
  const newJob = new Job(jobId, -1);
  const mockSurveyWithJob = new Survey(
    surveyId,
    'title',
    'description',
    /* jobs= */ Map({
      job001: mockJob,
    }),
    /* acl= */ Map()
  );
  beforeEach(waitForAsync(() => {
    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      ['init', 'getSurveyId$', 'navigateToCreateSurvey', 'navigateToCreateJob']
    );
    surveyId$ = new Subject<string | null>();
    navigationServiceSpy.getSurveyId$.and.returnValue(surveyId$);

    route = new ActivatedRouteStub();
    surveyServiceSpy = jasmine.createSpyObj<SurveyService>('SurveyService', [
      'activateSurvey',
      'getActiveSurvey$',
    ]);
    activeSurvey$ = new Subject<Survey>();
    surveyServiceSpy.getActiveSurvey$.and.returnValue(activeSurvey$);

    jobServiceSpy = jasmine.createSpyObj<JobService>('JobService', [
      'addOrUpdateJob',
      'createNewJob',
    ]);
    jobServiceSpy.createNewJob.and.returnValue(newJob);

    TestBed.configureTestingModule({
      declarations: [CreateJobComponent],
      providers: [
        {provide: NavigationService, useValue: navigationServiceSpy},
        {provide: SurveyService, useValue: surveyServiceSpy},
        {provide: JobService, useValue: jobServiceSpy},
        {provide: ActivatedRoute, useValue: route},
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CreateJobComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('name input is empty when active survey does not have any jobs', fakeAsync(() => {
    surveyId$.next(surveyId);
    activeSurvey$.next(mockSurveyNoJob);
    tick();

    expect(component.formGroup.controls[component.nameControlKey].value).toBe(
      ''
    );
  }));

  it('name input is loaded when active survey has a job', fakeAsync(() => {
    surveyId$.next(surveyId);
    activeSurvey$.next(mockSurveyWithJob);
    tick();

    expect(component.formGroup.controls[component.nameControlKey].value).toBe(
      name
    );
  }));

  it('creates a new job when active survey does not have any jobs and continue button is clicked', fakeAsync(() => {
    surveyId$.next(surveyId);
    activeSurvey$.next(mockSurveyNoJob);
    tick();

    const name = 'jobY';
    component.formGroup.controls[component.nameControlKey].setValue(name);
    const continueButton = fixture.debugElement.query(
      By.css('#continue-button')
    ).nativeElement as HTMLElement;
    continueButton.click();
    flush();

    expect(jobServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
      surveyId,
      newJob.copyWith({name})
    );
    expect(navigationServiceSpy.navigateToCreateJob).toHaveBeenCalledOnceWith(
      surveyId
    );
  }));

  it('updates the first job when active survey has a job and continue button is clicked', fakeAsync(() => {
    surveyId$.next(surveyId);
    activeSurvey$.next(mockSurveyWithJob);
    tick();

    const name = 'jobY';
    component.formGroup.controls[component.nameControlKey].setValue(name);
    const continueButton = fixture.debugElement.query(
      By.css('#continue-button')
    ).nativeElement as HTMLElement;
    continueButton.click();
    flush();

    expect(jobServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
      surveyId,
      mockJob.copyWith({name})
    );
    expect(navigationServiceSpy.navigateToCreateJob).toHaveBeenCalledOnceWith(
      surveyId
    );
  }));

  it('activates current survey ID when routed in with survey ID', fakeAsync(() => {
    surveyId$.next(surveyId);
    tick();

    expect(surveyServiceSpy.activateSurvey).toHaveBeenCalledOnceWith(surveyId);
  }));

  it('navigates to create survey page when back button is clicked', fakeAsync(() => {
    surveyId$.next(surveyId);
    tick();

    const backButton = fixture.debugElement.query(By.css('#back-button'))
      .nativeElement as HTMLElement;
    backButton.click();

    expect(
      navigationServiceSpy.navigateToCreateSurvey
    ).toHaveBeenCalledOnceWith(surveyId);
  }));
});
