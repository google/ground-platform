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

import {toSignal} from '@angular/core/rxjs-interop';
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
import {Observable, Subject, map, of, NEVER} from 'rxjs';

import {ShareSurveyComponent} from '../../components/share-survey/share-survey.component';
import {Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {DataSharingType, Survey, SurveyState} from 'app/models/survey.model';
import {User} from 'app/models/user.model';
import {Task, TaskType} from 'app/models/task/task.model';
import {
  CreateSurveyComponent,
  CreateSurveyPhase,
} from 'app/pages/create-survey/create-survey.component';
import {DataSharingTermsComponent} from 'app/pages/create-survey/data-sharing-terms/data-sharing-terms.component';
import {JobDetailsComponent} from 'app/pages/create-survey/job-details/job-details.component';
import {SurveyDetailsComponent} from 'app/pages/create-survey/survey-details/survey-details.component';
import {DraftSurveyService} from 'app/services/draft-survey/draft-survey.service';
import {JobService} from 'app/services/job/job.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {UrlParams} from 'app/services/navigation/url-params';
import {SurveyService} from 'app/services/survey/survey.service';
import {TaskService} from 'app/services/task/task.service';
import {ActivatedRouteStub} from 'testing/activated-route-stub';
import {HeaderComponent} from '../../components/header/header.component';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {StepCardComponent} from './step-card/step-card.component';
import {MatRadioModule} from '@angular/material/radio';
import {MatCardModule} from '@angular/material/card';
import {ShareListComponent} from '../../components/share-list/share-list.component';
import {GeneralAccessControlComponent} from '../../components/general-access-control/general-access-control.component';
import {DataVisibilityControlComponent} from '../../components/data-visibility-control/data-visibility-control.component';
import {CopySurveyControlsComponent} from '../../components/copy-survey-controls/copy-survey-controls.component';
import {TaskDetailsComponent} from './task-details/task-details.component';
import {SurveyLoiComponent} from './survey-loi/survey-loi.component';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {AngularFireModule} from '@angular/fire/compat';
import {AngularFireAuth} from '@angular/fire/compat/auth';
import {AuthService} from 'app/services/auth/auth.service';
import {MatMenuModule} from '@angular/material/menu';

describe('CreateSurveyComponent', () => {
  let component: CreateSurveyComponent;
  let fixture: ComponentFixture<CreateSurveyComponent>;
  let surveyId$: Subject<string | null>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let route: ActivatedRouteStub;
  let activeSurvey$: Subject<Survey>;
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
        'getUrlParams',
        'navigateToSurveyList',
        'navigateToCreateSurvey',
        'navigateToEditSurvey',
        'getSidePanelExpanded',
        'selectSurvey',
        'isEditSurveyPage',
        'isSurveyPage',
        'getSurveyAppLink',
      ]
    );
    surveyId$ = new Subject<string | null>();

    route = new ActivatedRouteStub();
    surveyServiceSpy = jasmine.createSpyObj<SurveyService>('SurveyService', [
      'activateSurvey',
      'getActiveSurvey$',
      'updateTitleAndDescription',
      'createSurvey',
      'getActiveSurvey',
      'updateDataSharingTerms',
      'canManageSurvey',
    ]);
    surveyServiceSpy.createSurvey.and.returnValue(
      new Promise(resolve => resolve(newSurveyId))
    );
    activeSurvey$ = new Subject<Survey>();
    surveyServiceSpy.getActiveSurvey$.and.returnValue(activeSurvey$);
    surveyServiceSpy.canManageSurvey.and.returnValue(true);
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
      'getTasks$',
      'updateLoiTasks',
    ]);

    const authServiceSpy = jasmine.createSpyObj('AuthService', [
      'canShare',
      'getUser$',
    ]);
    authServiceSpy.canShare.and.returnValue(of(true));
    authServiceSpy.getUser$.and.returnValue(of({} as User));

    TestBed.configureTestingModule({
      imports: [
        MatDialogModule,
        MatProgressSpinnerModule,
        MatProgressBarModule,
        MatRadioModule,
        MatCardModule,
        AngularFireModule.initializeApp({}),
        MatMenuModule,
      ],
      declarations: [
        CreateSurveyComponent,
        SurveyDetailsComponent,
        JobDetailsComponent,
        DataSharingTermsComponent,
        ShareSurveyComponent,
        HeaderComponent,
        StepCardComponent,
        ShareListComponent,
        GeneralAccessControlComponent,
        DataVisibilityControlComponent,
        CopySurveyControlsComponent,
        TaskDetailsComponent,
        SurveyLoiComponent,
      ],
      providers: [
        {provide: NavigationService, useValue: navigationServiceSpy},
        {provide: SurveyService, useValue: surveyServiceSpy},
        {provide: DraftSurveyService, useValue: draftSurveyServiceSpy},
        {provide: JobService, useValue: jobServiceSpy},
        {provide: LocationOfInterestService, useValue: loiServiceSpy},
        {provide: ActivatedRoute, useValue: route},
        {provide: TaskService, useValue: taskServiceSpy},
        {
          provide: AngularFireAuth,
          useValue: {
            authState: NEVER,
            onIdTokenChanged: (callback: Function) => callback(null),
          },
        },
        {provide: AuthService, useValue: authServiceSpy},
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CreateSurveyComponent);
    component = fixture.componentInstance;

    const urlParams$ = surveyId$.pipe(
      map(surveyId => new UrlParams(surveyId, null, null, null))
    );
    navigationServiceSpy.getUrlParams.and.returnValue(
      toSignal(urlParams$, {
        initialValue: new UrlParams(null, null, null, null),
        injector: fixture.componentRef.injector,
      })
    );

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
      component.ngOnInit();
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
              component.createSurveyPhase = CreateSurveyPhase.SURVEY_DETAILS;
              fixture.detectChanges();
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
          });  describe('when active survey has empty title', () => {
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
      expect(navigationServiceSpy.selectSurvey).toHaveBeenCalledOnceWith(
        surveyId
      );
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
      component.createSurveyPhase = CreateSurveyPhase.DEFINE_TASKS;
      fixture.detectChanges();
    }));

    describe('clicking back', () => {
      it('navigates to LOI selection', fakeAsync(() => {
        component.skipLoiSelection = false;
        clickBackButton(fixture);
        flush();
        fixture.detectChanges();

        expect(component.createSurveyPhase).toBe(CreateSurveyPhase.DEFINE_LOIS);
      }));
    });
  });

  describe('Data Sharing Terms', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveyWithJob);
      tick();
      // Forcibly set phase to DEFINE_DATA_SHARING_TERMS
      component.createSurveyPhase = CreateSurveyPhase.DEFINE_DATA_SHARING_TERMS;
      fixture.detectChanges();
    }));

    it('updates data sharing agreement after clicking continue', fakeAsync(() => {
      clickContinueButton(fixture);
      tick();

      expect(surveyServiceSpy.updateDataSharingTerms).toHaveBeenCalledOnceWith(
        DataSharingType.CUSTOM,
        'Good day, sir'
      );
    }));

    it('goes back to task definition component after back button is clicked', () => {
      clickBackButton(fixture);

      expect(component.createSurveyPhase).toBe(CreateSurveyPhase.DEFINE_TASKS);
    });
  });

  describe('Review', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveyWithJob);
      tick();
      // Forcibly set phase to SHARE_SURVEY for now since other steps are not ready yet
      component.createSurveyPhase = CreateSurveyPhase.SHARE_SURVEY;
      fixture.detectChanges();
    }));

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
