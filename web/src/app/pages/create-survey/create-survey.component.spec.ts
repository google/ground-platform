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
import {
  CreateSurveyComponent,
  SetupPhase,
} from 'app/pages/create-survey/create-survey.component';
import {NameSurveyComponent} from 'app/pages/create-survey/name-survey/name-survey.component';
import {NameJobComponent} from 'app/pages/create-survey/name-job/name-job.component';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {JobService} from 'app/services/job/job.service';
import {Subject} from 'rxjs';
import {Survey} from 'app/models/survey.model';
import {Job} from 'app/models/job.model';
import {Map} from 'immutable';
import {By} from '@angular/platform-browser';

describe('CreateSurveyComponent', () => {
  let component: CreateSurveyComponent;
  let fixture: ComponentFixture<CreateSurveyComponent>;
  let surveyId$: Subject<string | null>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let route: ActivatedRouteStub;
  let activeSurvey$: Subject<Survey>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;
  let jobServiceSpy: jasmine.SpyObj<JobService>;

  const surveyId = 'survey001';
  const newSurveyId = 'survey002';
  const title = 'title';
  const description = 'description';
  const jobId = 'job001';
  const name = 'job name';
  const surveyWithoutTitle = new Survey(
    surveyId,
    '',
    '',
    /* jobs= */ Map(),
    /* acl= */ Map()
  );
  const surveyWithoutJob = new Survey(
    surveyId,
    title,
    description,
    /* jobs= */ Map(),
    /* acl= */ Map()
  );
  const job = new Job(jobId, /* index */ 0, 'red', name, /* tasks= */ Map());
  const newJob = new Job(jobId, -1);
  const surveyWithJob = new Survey(
    surveyId,
    title,
    description,
    /* jobs= */ Map({
      job001: job,
    }),
    /* acl= */ Map()
  );
  beforeEach(waitForAsync(() => {
    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      ['init', 'getSurveyId$', 'navigateToSurveyList', 'navigateToCreateSurvey']
    );
    surveyId$ = new Subject<string | null>();
    navigationServiceSpy.getSurveyId$.and.returnValue(surveyId$);

    route = new ActivatedRouteStub();
    surveyServiceSpy = jasmine.createSpyObj<SurveyService>('SurveyService', [
      'activateSurvey',
      'getActiveSurvey$',
      'updateTitleAndDescription',
      'createSurvey',
    ]);
    surveyServiceSpy.createSurvey.and.returnValue(
      new Promise(resolve => resolve(newSurveyId))
    );
    activeSurvey$ = new Subject<Survey>();
    surveyServiceSpy.getActiveSurvey$.and.returnValue(activeSurvey$);

    jobServiceSpy = jasmine.createSpyObj<JobService>('JobService', [
      'addOrUpdateJob',
      'createNewJob',
    ]);
    jobServiceSpy.createNewJob.and.returnValue(newJob);

    TestBed.configureTestingModule({
      declarations: [
        CreateSurveyComponent,
        NameSurveyComponent,
        NameJobComponent,
      ],
      providers: [
        {provide: NavigationService, useValue: navigationServiceSpy},
        {provide: SurveyService, useValue: surveyServiceSpy},
        {provide: JobService, useValue: jobServiceSpy},
        {provide: ActivatedRoute, useValue: route},
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CreateSurveyComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('activates current survey ID when routed in with survey ID', fakeAsync(() => {
    surveyId$.next(surveyId);
    tick();

    expect(surveyServiceSpy.activateSurvey).toHaveBeenCalledOnceWith(surveyId);
  }));

  it('displays name survey component when no current survey', fakeAsync(() => {
    surveyId$.next(null);
    tick();
    fixture.detectChanges();

    expect(component.nameSurvey).not.toBeUndefined();
  }));

  it('displays name survey component when active survey has empty title', fakeAsync(() => {
    surveyId$.next(surveyId);
    activeSurvey$.next(surveyWithoutTitle);
    tick();
    fixture.detectChanges();

    expect(component.nameSurvey).not.toBeUndefined();
  }));

  it('displays name job component when active survey no job', fakeAsync(() => {
    surveyId$.next(surveyId);
    activeSurvey$.next(surveyWithoutJob);
    tick();
    fixture.detectChanges();

    expect(component.nameJob).not.toBeUndefined();
  }));

  describe('Name Survey', () => {
    beforeEach(() => {
      component.setupPhase = SetupPhase.NAME_SURVEY;
    });

    it('creates new survey with title and description after clicking continue when no current survey', fakeAsync(() => {
      surveyId$.next(null);
      tick();
      fixture.detectChanges();

      const newTitle = 'newTitle';
      const newDescription = 'newDescription';
      const nameSurveyComponent = component.nameSurvey!;
      nameSurveyComponent.formGroup.controls[
        nameSurveyComponent.titleControlKey
      ].setValue(newTitle);
      nameSurveyComponent.formGroup.controls[
        nameSurveyComponent.descriptionControlKey
      ].setValue(newDescription);
      clickContinueButton(fixture);
      flush();

      expect(surveyServiceSpy.createSurvey).toHaveBeenCalledOnceWith(
        newTitle,
        newDescription
      );
      expect(
        navigationServiceSpy.navigateToCreateSurvey
      ).toHaveBeenCalledOnceWith(newSurveyId);
    }));

    it('updates title and description after clicking continue when given current survey', fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveyWithoutTitle);
      tick();
      fixture.detectChanges();

      const newTitle = 'newTitle';
      const newDescription = 'newDescription';
      const nameSurveyComponent = component.nameSurvey!;
      nameSurveyComponent.formGroup.controls[
        nameSurveyComponent.titleControlKey
      ].setValue(newTitle);
      nameSurveyComponent.formGroup.controls[
        nameSurveyComponent.descriptionControlKey
      ].setValue(newDescription);
      clickContinueButton(fixture);
      flush();

      expect(
        surveyServiceSpy.updateTitleAndDescription
      ).toHaveBeenCalledOnceWith(surveyId, newTitle, newDescription);
    }));

    it('navigates to survey list page when back button is clicked', () => {
      clickBackButton(fixture);

      expect(navigationServiceSpy.navigateToSurveyList).toHaveBeenCalled();
    });
  });

  describe('Name Job', () => {
    beforeEach(() => {
      component.setupPhase = SetupPhase.NAME_JOB;
    });

    it('creates new job with name after clicking continue when active survey has no job', fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveyWithoutJob);
      tick();
      fixture.detectChanges();

      const name = 'new job name';
      const nameJobComponent = component.nameJob!;
      nameJobComponent.formGroup.controls[
        nameJobComponent.nameControlKey
      ].setValue(name);
      clickContinueButton(fixture);
      flush();

      expect(jobServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
        surveyId,
        newJob.copyWith({name})
      );
    }));

    it('updates the first job after clicking continue when active survey has a job', fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveyWithJob);
      tick();
      fixture.detectChanges();

      const name = 'new job name';
      const nameJobComponent = component.nameJob!;
      nameJobComponent.formGroup.controls[
        nameJobComponent.nameControlKey
      ].setValue(name);
      clickContinueButton(fixture);
      flush();

      expect(jobServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
        surveyId,
        job.copyWith({name})
      );
    }));

    it('goes back to name survey component when back button is clicked', () => {
      clickBackButton(fixture);

      expect(component.nameSurvey).not.toBeUndefined();
      expect(component.nameJob).toBeUndefined();
    });
  });
});

function clickBackButton(
  fixture: ComponentFixture<CreateSurveyComponent>
): void {
  const backButton = fixture.debugElement.query(By.css('#back-button'))
    .nativeElement as HTMLElement;
  backButton.click();
}

function clickContinueButton(
  fixture: ComponentFixture<CreateSurveyComponent>
): void {
  const continueButton = fixture.debugElement.query(By.css('#continue-button'))
    .nativeElement as HTMLElement;
  continueButton.click();
}
