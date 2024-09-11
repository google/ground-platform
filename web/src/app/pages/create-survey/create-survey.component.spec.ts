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

import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  flush,
  tick,
  waitForAsync,
} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {By} from '@angular/platform-browser';
import {ActivatedRoute} from '@angular/router';
import {List, Map} from 'immutable';
import {Observable, Subject} from 'rxjs';

import {Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {DataSharingType, Survey, SurveyState} from 'app/models/survey.model';
import {Task, TaskType} from 'app/models/task/task.model';
import {
  CreateSurveyComponent,
  SetupPhase,
} from 'app/pages/create-survey/create-survey.component';
import {DataSharingTermsComponent} from 'app/pages/create-survey/data-sharing-terms/data-sharing-terms.component';
import {JobDetailsComponent} from 'app/pages/create-survey/job-details/job-details.component';
import {SurveyDetailsComponent} from 'app/pages/create-survey/survey-details/survey-details.component';
import {JobService} from 'app/services/job/job.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {TaskService} from 'app/services/task/task.service';
import {ActivatedRouteStub} from 'testing/activated-route-stub';

import {SurveyReviewComponent} from './survey-review/survey-review.component';

describe('CreateSurveyComponent', () => {
  let component: CreateSurveyComponent;
  let fixture: ComponentFixture<CreateSurveyComponent>;
  let surveyId$: Subject<string | null>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let route: ActivatedRouteStub;
  let activeSurvey$: Subject<Survey>;
  let lois: List<LocationOfInterest>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;
  let jobServiceSpy: jasmine.SpyObj<JobService>;
  let loiServiceSpy: jasmine.SpyObj<LocationOfInterestService>;
  let taskServiceSpy: jasmine.SpyObj<TaskService>;

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
    /* acl= */ Map(),
    /* ownerId= */ '',
    {type: DataSharingType.PRIVATE}
  );
  const surveyWithoutJob = new Survey(
    surveyId,
    title,
    description,
    /* jobs= */ Map(),
    /* acl= */ Map(),
    /* ownerId= */ '',
    {type: DataSharingType.PRIVATE}
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
    /* acl= */ Map(),
    /* ownerId= */ '',
    {type: DataSharingType.CUSTOM, customText: 'Good day, sir'}
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
    /* acl= */ Map(),
    /* ownerId= */ '',
    {type: DataSharingType.PRIVATE},
    SurveyState.READY
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
        'getSidePanelExpanded',
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
      'getActiveSurvey',
      'updateDataSharingTerms',
    ]);
    surveyServiceSpy.createSurvey.and.returnValue(
      new Promise(resolve => resolve(newSurveyId))
    );
    activeSurvey$ = new Subject<Survey>();
    surveyServiceSpy.getActiveSurvey$.and.returnValue(activeSurvey$);
    surveyServiceSpy.updateDataSharingTerms.and.returnValue(
      new Promise(resolve => resolve(undefined))
    );

    jobServiceSpy = jasmine.createSpyObj<JobService>('JobService', [
      'addOrUpdateJob',
      'createNewJob',
      'getNextColor',
    ]);
    jobServiceSpy.createNewJob.and.returnValue(newJob);

    loiServiceSpy = jasmine.createSpyObj<LocationOfInterestService>(
      'LocationOfInterestService',
      ['getLocationsOfInterest$']
    );
    lois = List();
    loiServiceSpy.getLocationsOfInterest$.and.returnValue(
      new Observable(observer => {
        observer.next(lois);
        observer.complete();
      })
    );

    taskServiceSpy = jasmine.createSpyObj<TaskService>('TaskService', [
      'getTasks$',
      'updateLoiTasks',
    ]);

    TestBed.configureTestingModule({
      imports: [MatDialogModule],
      declarations: [
        CreateSurveyComponent,
        SurveyDetailsComponent,
        JobDetailsComponent,
        DataSharingTermsComponent,
        SurveyReviewComponent,
      ],
      providers: [
        {provide: NavigationService, useValue: navigationServiceSpy},
        {provide: SurveyService, useValue: surveyServiceSpy},
        {provide: JobService, useValue: jobServiceSpy},
        {provide: LocationOfInterestService, useValue: loiServiceSpy},
        {provide: ActivatedRoute, useValue: route},
        {provide: TaskService, useValue: taskServiceSpy},
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CreateSurveyComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows spinner when survey not loaded', () => {
    const spinner = fixture.debugElement.query(By.css('#loading-spinner'))
      .nativeElement as HTMLElement;
    // TODO(#1170): Extract the spinner into a component
    expect(spinner.innerText).toContain('Loading survey...');
  });

  describe('when routed in with survey ID', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      tick();
    }));

    it('activates survey ID', () => {
      expect(surveyServiceSpy.activateSurvey).toHaveBeenCalledOnceWith(
        surveyId
      );
    });
  });

  describe('when no survey', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(NavigationService.SURVEY_ID_NEW);
      activeSurvey$.next(Survey.UNSAVED_NEW);
      tick();
      fixture.detectChanges();
    }));

    it('displays survey details component', () => {
      expect(component.surveyDetails).toBeDefined();
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
      expect(component.surveyDetails).toBeDefined();
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
      expect(component.jobDetails).toBeDefined();
    });
  });

  describe('when active survey has at least one job', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveyWithJob);
      tick();
      fixture.detectChanges();
    }));

    it('displays LOI editor component', () => {
      expect(component.surveyLoi).toBeDefined();
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
    describe('when no survey', () => {
      beforeEach(fakeAsync(() => {
        surveyId$.next(NavigationService.SURVEY_ID_NEW);
        activeSurvey$.next(Survey.UNSAVED_NEW);
        tick();
        fixture.detectChanges();
      }));

      it('creates new survey with title and description after clicking continue', fakeAsync(() => {
        const newName = 'newName';
        const newDescription = 'newDescription';
        const surveyDetailsComponent = component.surveyDetails!;
        surveyDetailsComponent.formGroup.controls[
          surveyDetailsComponent.titleControlKey
        ].setValue(newName);
        surveyDetailsComponent.formGroup.controls[
          surveyDetailsComponent.descriptionControlKey
        ].setValue(newDescription);
        fixture.detectChanges();
        clickContinueButton(fixture);
        flush();

        expect(surveyServiceSpy.createSurvey).toHaveBeenCalledOnceWith(
          newName,
          newDescription
        );
        expect(
          navigationServiceSpy.navigateToCreateSurvey
        ).toHaveBeenCalledOnceWith(newSurveyId, true);
      }));
    });

    describe('when given survey', () => {
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
        fixture.detectChanges();
        clickContinueButton(fixture);
        flush();

        expect(
          surveyServiceSpy.updateTitleAndDescription
        ).toHaveBeenCalledOnceWith(surveyId, newTitle, newDescription);
      }));

      it('navigates to survey list page after back button is clicked', () => {
        clickBackButton(fixture);

        expect(navigationServiceSpy.navigateToSurveyList).toHaveBeenCalled();
      });
    });
  });

  describe('Job Details', () => {
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
        fixture.detectChanges();
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
        // If survey has a job, we navigate to the next section, so we need to
        // go back to the job form.
        surveyServiceSpy.getActiveSurvey.and.returnValue(surveyWithJob);
        clickBackButton(fixture);
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

      it('goes back to survey details component after back button is clicked', () => {
        clickBackButton(fixture);
        fixture.detectChanges();

        expect(component.surveyDetails).toBeDefined();
        expect(component.jobDetails).toBeUndefined();
      });
    });
  });

  describe('Task Definition', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveyWithJob);
      tick();
      component.setupPhase = SetupPhase.DEFINE_TASKS;
      fixture.detectChanges();
    }));

    describe('clicking back', () => {
      it('navigates to LOI selection', fakeAsync(() => {
        component.skipLoiSelection = false;
        clickBackButton(fixture);
        flush();
        fixture.detectChanges();

        expect(component.setupPhase).toBe(SetupPhase.DEFINE_LOIS);
      }));
    });
  });

  describe('Data Sharing Terms', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveyWithJob);
      tick();
      // Forcibly set phase to DEFINE_DATA_SHARING_TERMS
      component.setupPhase = SetupPhase.DEFINE_DATA_SHARING_TERMS;
      fixture.detectChanges();
    }));

    it('updates data sharing agreement after clicking continue', () => {
      clickContinueButton(fixture);

      expect(surveyServiceSpy.updateDataSharingTerms).toHaveBeenCalledOnceWith(
        surveyWithJob,
        DataSharingType.CUSTOM,
        'Good day, sir'
      );
    });

    it('goes back to task definition component after back button is clicked', () => {
      clickBackButton(fixture);

      expect(component.setupPhase).toBe(SetupPhase.DEFINE_TASKS);
    });
  });

  describe('Review', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveyWithJob);
      tick();
      // Forcibly set phase to REVIEW for now since other steps are not ready yet
      component.setupPhase = SetupPhase.REVIEW;
      fixture.detectChanges();
    }));

    it('goes back to data sharing component after back button is clicked', () => {
      clickBackButton(fixture);

      expect(component.setupPhase).toBe(SetupPhase.DEFINE_DATA_SHARING_TERMS);
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
