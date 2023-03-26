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
import {SurveyDetailsComponent} from 'app/pages/create-survey/survey-details/survey-details.component';
import {JobDetailsComponent} from 'app/pages/create-survey/job-details/job-details.component';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {JobService} from 'app/services/job/job.service';
import {Subject} from 'rxjs';
import {Survey} from 'app/models/survey.model';
import {Job} from 'app/models/job.model';
import {Map} from 'immutable';
import {By} from '@angular/platform-browser';
import {ShareSurveyComponent} from './share-survey/share-survey.component';
import {Task, TaskType} from 'app/models/task/task.model';

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
  const jobWithTask = new Job(
    jobId,
    /* index */ 0,
    'red',
    name,
    /* tasks= */ Map({
      task001: new Task(
        'task001',
        TaskType.TEXT,
        'Text Field',
        /*required=*/ true,
        0
      ),
    })
  );
  const surveySetupFinished = new Survey(
    surveyId,
    title,
    description,
    /* jobs= */ Map({
      job001: jobWithTask,
    }),
    /* acl= */ Map()
  );
  beforeEach(waitForAsync(() => {
    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      [
        'init',
        'getSurveyId$',
        'navigateToSurveyList',
        'navigateToCreateSurvey',
        'navigateToEditSurvey',
      ]
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
      imports: [MatDialogModule],
      declarations: [
        CreateSurveyComponent,
        SurveyDetailsComponent,
        JobDetailsComponent,
        ShareSurveyComponent,
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

  describe('when routed in with survey ID', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      tick();
    }));

    it('activates current survey ID', () => {
      expect(surveyServiceSpy.activateSurvey).toHaveBeenCalledOnceWith(
        surveyId
      );
    });
  });

  describe('when no current survey', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(null);
      tick();
      fixture.detectChanges();
    }));

    it('displays survey details component', () => {
      expect(component.surveyDetails).not.toBeUndefined();
    });
  });

  describe('when active survey has empty title', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveyWithoutTitle);
      tick();
      fixture.detectChanges();
    }));

    it('displays survey details component', () => {
      expect(component.surveyDetails).not.toBeUndefined();
    });
  });

  describe('when active survey no job', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveyWithoutJob);
      tick();
      fixture.detectChanges();
    }));

    it('displays job details component', () => {
      expect(component.jobDetails).not.toBeUndefined();
    });
  });

  describe('when active survey has finished setup', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveySetupFinished);
      tick();
      fixture.detectChanges();
    }));

    it('navigates to edit survey page', () => {
      expect(
        navigationServiceSpy.navigateToEditSurvey
      ).toHaveBeenCalledOnceWith(surveyId);
    });
  });

  describe('when active survey has finished setup', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveySetupFinished);
      tick();
      fixture.detectChanges();
    }));

    it('navigates to edit survey page', () => {
      expect(
        navigationServiceSpy.navigateToEditSurvey
      ).toHaveBeenCalledOnceWith(surveyId);
    });
  });

  describe('when active survey has finished setup', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveySetupFinished);
      tick();
      fixture.detectChanges();
    }));

    it('navigates to edit survey page', () => {
      expect(
        navigationServiceSpy.navigateToEditSurvey
      ).toHaveBeenCalledOnceWith(surveyId);
    });
  });

  describe('Survey Details', () => {
    beforeEach(() => {
      component.setupPhase = SetupPhase.SURVEY_DETAILS;
    });

    describe('when no current survey', () => {
      beforeEach(fakeAsync(() => {
        surveyId$.next(null);
        tick();
        fixture.detectChanges();
      }));

      it('creates new survey with title and description after clicking continue', fakeAsync(() => {
        const newTitle = 'newTitle';
        const newDescription = 'newDescription';
        const jobDetailsComponent = component.surveyDetails!;
        jobDetailsComponent.formGroup.controls[
          jobDetailsComponent.titleControlKey
        ].setValue(newTitle);
        jobDetailsComponent.formGroup.controls[
          jobDetailsComponent.descriptionControlKey
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
    });

    describe('when given current survey', () => {
      beforeEach(fakeAsync(() => {
        surveyId$.next(surveyId);
        activeSurvey$.next(surveyWithoutTitle);
        tick();
        fixture.detectChanges();
      }));

      it('updates title and description after clicking continue', fakeAsync(() => {
        const newTitle = 'newTitle';
        const newDescription = 'newDescription';
        const jobDetailsComponent = component.surveyDetails!;
        jobDetailsComponent.formGroup.controls[
          jobDetailsComponent.titleControlKey
        ].setValue(newTitle);
        jobDetailsComponent.formGroup.controls[
          jobDetailsComponent.descriptionControlKey
        ].setValue(newDescription);
        clickContinueButton(fixture);
        flush();

        expect(
          surveyServiceSpy.updateTitleAndDescription
        ).toHaveBeenCalledOnceWith(surveyId, newTitle, newDescription);
      }));
    });

    it('navigates to survey list page after back button is clicked', () => {
      clickBackButton(fixture);

      expect(navigationServiceSpy.navigateToSurveyList).toHaveBeenCalled();
    });
  });

  describe('Job Details', () => {
    beforeEach(() => {
      component.setupPhase = SetupPhase.JOB_DETAILS;
    });

    describe('when active survey has no job', () => {
      beforeEach(fakeAsync(() => {
        surveyId$.next(surveyId);
        activeSurvey$.next(surveyWithoutJob);
        tick();
        fixture.detectChanges();
      }));

      it('creates new job with name after clicking continue', fakeAsync(() => {
        const name = 'new job name';
        const jobDetailsComponent = component.jobDetails!;
        jobDetailsComponent.formGroup.controls[
          jobDetailsComponent.nameControlKey
        ].setValue(name);
        clickContinueButton(fixture);
        flush();

        expect(jobServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
          surveyId,
          newJob.copyWith({name})
        );
      }));
    });

    describe('when active survey has a job', () => {
      beforeEach(fakeAsync(() => {
        surveyId$.next(surveyId);
        activeSurvey$.next(surveyWithJob);
        tick();
        fixture.detectChanges();
      }));

      it('updates the first job after clicking continue', fakeAsync(() => {
        const name = 'new job name';
        const jobDetailsComponent = component.jobDetails!;
        jobDetailsComponent.formGroup.controls[
          jobDetailsComponent.nameControlKey
        ].setValue(name);
        clickContinueButton(fixture);
        flush();

        expect(jobServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
          surveyId,
          job.copyWith({name})
        );
      }));
    });

    it('goes back to survey details component after back button is clicked', () => {
      clickBackButton(fixture);

      expect(component.surveyDetails).not.toBeUndefined();
      expect(component.jobDetails).toBeUndefined();
    });
  });

  describe('Review', () => {
    beforeEach(fakeAsync(() => {
      component.setupPhase = SetupPhase.REVIEW;
      tick();
      fixture.detectChanges();
    }));

    it('goes back to job details component after back button is clicked', () => {
      clickBackButton(fixture);

      expect(component.setupPhase).toBe(SetupPhase.DEFINE_LOIS);
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
