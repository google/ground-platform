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

import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogModule } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { By } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { List, Map } from 'immutable';
import { Observable, ReplaySubject, Subject } from 'rxjs';

import {
  CreateSurveyComponent,
  CreateSurveyPhase,
} from 'app/components/create-survey/create-survey.component';
import { DataSharingTermsComponent } from 'app/components/create-survey/data-sharing-terms/data-sharing-terms.component';
import { JobDetailsComponent } from 'app/components/create-survey/job-details/job-details.component';
import { SurveyDetailsComponent } from 'app/components/create-survey/survey-details/survey-details.component';
import { ShareSurveyComponent } from 'app/components/shared/share-survey/share-survey.component';
import { Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { DataSharingType, Survey, SurveyState } from 'app/models/survey.model';
import { Task, TaskType } from 'app/models/task/task.model';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';
import { JobService } from 'app/services/job/job.service';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { SURVEY_ID_NEW } from 'app/services/navigation/navigation.constants';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SurveyService } from 'app/services/survey/survey.service';
import { TaskService } from 'app/services/task/task.service';
import { ActivatedRouteStub } from 'testing/activated-route-stub';

describe('CreateSurveyComponent', () => {
  let component: CreateSurveyComponent;
  let fixture: ComponentFixture<CreateSurveyComponent>;
  let surveyId$: ReplaySubject<string | null>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let route: ActivatedRouteStub;
  let activeSurvey$: ReplaySubject<Survey>;
  let lois: List<LocationOfInterest>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;
  let draftSurvey$: Subject<Survey>;
  let draftSurveyServiceSpy: jasmine.SpyObj<DraftSurveyService>;
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
    { type: DataSharingType.PRIVATE }
  );
  const surveyWithoutJob = new Survey(
    surveyId,
    title,
    description,
    /* jobs= */ Map(),
    /* acl= */ Map(),
    /* ownerId= */ '',
    { type: DataSharingType.PRIVATE }
  );
  const unsavedSurvey = new Survey(SURVEY_ID_NEW, '', '', Map(), Map(), '', {
    type: DataSharingType.PRIVATE,
  });
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
    { type: DataSharingType.CUSTOM, customText: 'Good day, sir' }
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
    { type: DataSharingType.PRIVATE },
    SurveyState.READY
  );
  beforeEach(async () => {
    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      [
        'getSurveyId$',
        'navigateToSurveyList',
        'navigateToCreateSurvey',
        'navigateToEditSurvey',
        'getSidePanelExpanded',
        'selectSurvey',
      ]
    );
    surveyId$ = new ReplaySubject<string | null>(1);
    navigationServiceSpy.getSurveyId$.and.returnValue(surveyId$);

    route = new ActivatedRouteStub();
    surveyServiceSpy = jasmine.createSpyObj<SurveyService>('SurveyService', [
      'getActiveSurvey$',
      'updateTitleAndDescription',
      'createSurvey',
      'getActiveSurvey',
      'updateDataSharingTerms',
    ]);
    surveyServiceSpy.createSurvey.and.returnValue(
      new Promise(resolve => resolve(newSurveyId))
    );
    activeSurvey$ = new ReplaySubject<Survey>(1);
    surveyServiceSpy.getActiveSurvey$.and.returnValue(activeSurvey$);
    surveyServiceSpy.updateDataSharingTerms.and.returnValue(
      new Promise(resolve => resolve(undefined))
    );

    draftSurvey$ = new Subject<Survey>();
    draftSurveyServiceSpy = jasmine.createSpyObj<DraftSurveyService>(
      'DraftSurveyService',
      [
        'init',
        'getSurvey$',
        'updateDataSharingTerms',
        'updateState',
        'updateSurvey',
      ]
    );
    draftSurveyServiceSpy.getSurvey$.and.returnValue(draftSurvey$);

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
      'updateLoiTasks',
    ]);

    await TestBed.configureTestingModule({
      imports: [MatDialogModule, MatProgressSpinnerModule],
      declarations: [
        CreateSurveyComponent,
        SurveyDetailsComponent,
        JobDetailsComponent,
        DataSharingTermsComponent,
        ShareSurveyComponent,
      ],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: NavigationService, useValue: navigationServiceSpy },
        { provide: SurveyService, useValue: surveyServiceSpy },
        { provide: DraftSurveyService, useValue: draftSurveyServiceSpy },
        { provide: JobService, useValue: jobServiceSpy },
        { provide: LocationOfInterestService, useValue: loiServiceSpy },
        { provide: ActivatedRoute, useValue: route },
        { provide: TaskService, useValue: taskServiceSpy },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    // fixture = TestBed.createComponent(CreateSurveyComponent);
    // component = fixture.componentInstance;
    // fixture.detectChanges();
  });

  function createComponent(shouldDetectChanges = true) {
    fixture = TestBed.createComponent(CreateSurveyComponent);
    component = fixture.componentInstance;
    if (shouldDetectChanges) {
      fixture.detectChanges();
    }
  }

  it('shows spinner when survey not loaded', () => {
    createComponent();
    const spinner = fixture.debugElement.query(By.css('#loading-spinner'))
      .nativeElement as HTMLElement;
    // TODO(#1170): Extract the spinner into a component
    expect(spinner.innerText).toContain('Loading survey...');
  });

  describe('when routed in with survey ID', () => {
    beforeEach(async () => {
      surveyId$.next(surveyId);
      createComponent();
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('initializes draft survey', async () => {
      expect(draftSurveyServiceSpy.init).toHaveBeenCalledWith(surveyId);
    });
  });

  describe('when no survey', () => {
    beforeEach(async () => {
      createComponent(false);
      component.createSurveyPhase = CreateSurveyPhase.SURVEY_DETAILS;
      component.survey = unsavedSurvey;
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('displays survey details component', () => {
      expect(component.surveyDetails).toBeDefined();
    });
  });

  describe('when active survey has empty title', () => {
    beforeEach(async () => {
      createComponent(false);
      component.createSurveyPhase = CreateSurveyPhase.SURVEY_DETAILS;
      component.survey = surveyWithoutTitle;
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('displays survey details component', () => {
      expect(component.surveyDetails).toBeDefined();
    });
  });

  describe('when active survey no job', () => {
    beforeEach(async () => {
      createComponent(false);
      component.createSurveyPhase = CreateSurveyPhase.JOB_DETAILS;
      component.survey = surveyWithoutJob;
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('displays job details component', () => {
      expect(component.jobDetails).toBeDefined();
    });
  });

  describe('when active survey has at least one job', () => {
    beforeEach(async () => {
      createComponent(false);
      component.createSurveyPhase = CreateSurveyPhase.DEFINE_LOIS;
      component.survey = surveyWithJob;
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('displays LOI editor component', () => {
      expect(component.surveyLoi).toBeDefined();
    });
  });

  describe('when active survey has finished setup', () => {
    beforeEach(async () => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveySetupFinished);
      createComponent();
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('navigates to edit survey page', () => {
      expect(navigationServiceSpy.selectSurvey).toHaveBeenCalledOnceWith(
        surveyId
      );
    });
  });

  describe('Survey Details', () => {
    describe('when no survey', () => {
      beforeEach(async () => {
        createComponent(false);
        spyOn(component, 'ngOnInit').and.stub();

        component.createSurveyPhase = CreateSurveyPhase.SURVEY_DETAILS;
        component.survey = unsavedSurvey;
        // Mock surveyId to match
        component.createSurveyPhase = CreateSurveyPhase.SURVEY_DETAILS;
        component.survey = unsavedSurvey;
        // Mock surveyId to match
        component.surveyId = SURVEY_ID_NEW;
        component.lois = lois;
        component.createSurveyPhase = CreateSurveyPhase.SURVEY_DETAILS;
        component.canContinue = false;

        fixture.detectChanges();
        await fixture.whenStable();
      });

      it('creates new survey with title and description after clicking continue', async () => {
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
        await fixture.whenStable();
        fixture.detectChanges();
        await component.continue();

        expect(surveyServiceSpy.createSurvey).toHaveBeenCalledOnceWith(
          newName,
          newDescription
        );
        expect(
          navigationServiceSpy.navigateToCreateSurvey
        ).toHaveBeenCalledOnceWith(newSurveyId, true);
      });
    });

    describe('when given survey', () => {
      beforeEach(async () => {
        createComponent(false);
        spyOn(component, 'ngOnInit').and.stub();

        component.survey = surveyWithoutTitle;
        component.surveyId = surveyId;
        component.lois = lois;
        component.createSurveyPhase = CreateSurveyPhase.SURVEY_DETAILS;
        component.canContinue = true; // Assuming existing survey is valid? Or false? Title empty -> false?
        // SurveyWithoutTitle has empty title. So validity is false?
        // Let's set false.
        component.canContinue = false;

        fixture.detectChanges();
        await fixture.whenStable();
      });

      it('updates title and description after clicking continue', async () => {
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
        await fixture.whenStable();
        fixture.detectChanges();
        component.continue();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(
          surveyServiceSpy.updateTitleAndDescription
        ).toHaveBeenCalledOnceWith(surveyId, newTitle, newDescription);
      });

      it('navigates to survey list page after back button is clicked', () => {
        clickBackButton(fixture);

        expect(navigationServiceSpy.navigateToSurveyList).toHaveBeenCalled();
      });
    });
  });

  describe('Job Details', () => {
    describe('when active survey has no job', () => {
      beforeEach(async () => {
        createComponent(false);
        spyOn(component, 'ngOnInit').and.stub();
        component.survey = surveyWithoutJob;
        component.surveyId = surveyId;
        component.lois = lois;
        component.createSurveyPhase = CreateSurveyPhase.JOB_DETAILS;
        component.canContinue = false;
        fixture.detectChanges();
        await fixture.whenStable();
      });

      it('creates new job with name after clicking continue', async () => {
        const name = 'new job name';
        const jobDetailsComponent = component.jobDetails!;
        jobDetailsComponent.formGroup.controls[
          jobDetailsComponent.nameControlKey
        ].setValue(name);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        component.continue();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(jobServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
          surveyId,
          newJob.copyWith({ name })
        );
      });
    });

    describe('when active survey has a job', () => {
      beforeEach(async () => {
        createComponent(false);
        spyOn(component, 'ngOnInit').and.stub();

        component.survey = surveyWithJob;
        component.surveyId = surveyId;
        component.lois = lois;
        component.createSurveyPhase = CreateSurveyPhase.JOB_DETAILS;
        component.canContinue = true;

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        // If survey has a job, we navigate to the next section, so we need to
        // go back to the job form. (This logic in test setup is a bit circular, but simulating manual hydration allows us to bypass the auto-phase logic if we want, OR we just trust auto-phase)
        // Actually, SurveyWithJob -> DEFINE_LOIS. The test forcefully goes back?
        // Let's hydrate as DEFINE_LOIS first if that's what natural flow does, then back.
        // Or just hydrate as JOB_DETAILS directly since we want to test JOB_DETAILS.

        surveyServiceSpy.getActiveSurvey.and.returnValue(surveyWithJob);
        // We simulate the "Back" action manually or just start there.
        // Let's stick to the original test intent but hydrate.
      });

      it('updates the first job after clicking continue', async () => {
        const name = 'new job name';
        const jobDetailsComponent = fixture.debugElement.query(
          By.directive(JobDetailsComponent)
        ).componentInstance;
        jobDetailsComponent.formGroup.controls[
          jobDetailsComponent.nameControlKey
        ].setValue(name);
        clickContinueButton(fixture);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(jobServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
          surveyId,
          job.copyWith({ name })
        );
      });

      it('goes back to survey details component after back button is clicked', () => {
        clickBackButton(fixture);
        fixture.detectChanges();

        expect(component.surveyDetails).toBeDefined();
        expect(component.jobDetails).toBeUndefined();
      });
    });
  });

  describe('Task Definition', () => {
    beforeEach(async () => {
      surveyId$.next(surveyId);
      surveyId$.next(surveyId);
      // activeSurvey$.next(surveyWithJob);

      // Identity Hydration

      createComponent(false);
      spyOn(component, 'ngOnInit').and.stub();

      component.survey = surveyWithJob;
      component.surveyId = surveyId;
      component.lois = lois;
      component.createSurveyPhase = CreateSurveyPhase.DEFINE_TASKS;
      component.canContinue = true;

      fixture.detectChanges();
      await fixture.whenStable();
    });

    describe('clicking back', () => {
      it('navigates to LOI selection', async () => {
        component.skipLoiSelection = false;
        clickBackButton(fixture);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.createSurveyPhase).toBe(CreateSurveyPhase.DEFINE_LOIS);
      });
    });
  });

  describe('Data Sharing Terms', () => {
    beforeEach(async () => {
      surveyId$.next(surveyId);
      surveyId$.next(surveyId);
      // activeSurvey$.next(surveyWithJob);

      // Identity Hydration

      createComponent(false);
      spyOn(component, 'ngOnInit').and.stub();

      component.survey = surveyWithJob;
      component.surveyId = surveyId;
      component.lois = lois;
      component.createSurveyPhase = CreateSurveyPhase.DEFINE_DATA_SHARING_TERMS;
      component.canContinue = true;

      fixture.detectChanges();
      await fixture.whenStable();
      // Forcibly set phase to DEFINE_DATA_SHARING_TERMS

      fixture.detectChanges();
    });

    it('updates data sharing agreement after clicking continue', async () => {
      clickContinueButton(fixture);
      fixture.detectChanges();
      await fixture.whenStable();

      expect(surveyServiceSpy.updateDataSharingTerms).toHaveBeenCalledOnceWith(
        DataSharingType.CUSTOM,
        'Good day, sir'
      );
    });

    it('goes back to task definition component after back button is clicked', () => {
      clickBackButton(fixture);

      expect(component.createSurveyPhase).toBe(CreateSurveyPhase.DEFINE_TASKS);
    });
  });

  describe('Review', () => {
    beforeEach(async () => {
      surveyId$.next(surveyId);
      surveyId$.next(surveyId);
      // activeSurvey$.next(surveyWithJob);

      // Identity Hydration

      createComponent(false);
      spyOn(component, 'ngOnInit').and.stub();

      component.survey = surveyWithJob;
      component.surveyId = surveyId;
      component.lois = lois;
      component.createSurveyPhase = CreateSurveyPhase.SHARE_SURVEY;
      component.canContinue = true;

      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    });

    it('goes back to data sharing component after back button is clicked', () => {
      clickBackButton(fixture);

      expect(component.createSurveyPhase).toBe(
        CreateSurveyPhase.DEFINE_DATA_SHARING_TERMS
      );
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
