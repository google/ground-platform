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
import {MatLegacyDialogModule as MatDialogModule} from '@angular/material/legacy-dialog';
import {By} from '@angular/platform-browser';
import {ActivatedRoute} from '@angular/router';
import {List, Map} from 'immutable';
import {Observable, Subject} from 'rxjs';

import {DataCollectionStrategy, Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {Survey} from 'app/models/survey.model';
import {Task, TaskType} from 'app/models/task/task.model';
import {
  CreateSurveyComponent,
  SetupPhase,
} from 'app/pages/create-survey/create-survey.component';
import {JobDetailsComponent} from 'app/pages/create-survey/job-details/job-details.component';
import {
  LoiPermissionsComponent,
  LoiPermissionsOption,
} from 'app/pages/create-survey/loi-permissions/loi-permissions.component';
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
      'getActiveSurvey',
    ]);
    surveyServiceSpy.createSurvey.and.returnValue(
      new Promise(resolve => resolve(newSurveyId))
    );
    activeSurvey$ = new Subject<Survey>();
    surveyServiceSpy.getActiveSurvey$.and.returnValue(activeSurvey$);

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
    ]);

    TestBed.configureTestingModule({
      imports: [MatDialogModule],
      declarations: [
        CreateSurveyComponent,
        SurveyDetailsComponent,
        JobDetailsComponent,
        LoiPermissionsComponent,
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
    expect(spinner.innerHTML).toContain('Loading survey...');
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

    it('displays LOI permissions component', () => {
      expect(component.loiPermissions).toBeDefined();
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
        const newTitle = 'newTitle';
        const newDescription = 'newDescription';
        const surveyDetailsComponent = component.surveyDetails!;
        surveyDetailsComponent.formGroup.controls[
          surveyDetailsComponent.titleControlKey
        ].setValue(newTitle);
        surveyDetailsComponent.formGroup.controls[
          surveyDetailsComponent.descriptionControlKey
        ].setValue(newDescription);
        fixture.detectChanges();
        clickContinueButton(fixture);
        flush();

        expect(surveyServiceSpy.createSurvey).toHaveBeenCalledOnceWith(
          newTitle,
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

  describe('LOI Permissions', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(surveyWithJob);
      tick();
      fixture.detectChanges();
      surveyServiceSpy.getActiveSurvey.and.returnValue(surveyWithJob);
      fixture.detectChanges();
    }));

    describe('clicking continue', () => {
      describe('when "Survey organizers" is selected', () => {
        beforeEach(fakeAsync(() => {
          const loiPermissions = component.loiPermissions!;
          loiPermissions.formGroup.controls[
            loiPermissions.loiPermissionsControlKey
          ].setValue(LoiPermissionsOption.SURVEY_ORGANIZERS);
          clickContinueButton(fixture);
          flush();
          fixture.detectChanges();
        }));

        it('calls addOrUpdateJob with an empty array for dataCollectorsCanAdd', () => {
          const dataCollectorsCanAdd: string[] = [];
          const strategy = DataCollectionStrategy.PREDEFINED;

          expect(jobServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
            surveyId,
            job.copyWith({dataCollectorsCanAdd, strategy})
          );
        });

        it('navigates to the LOI selection component', () => {
          expect(component.surveyLoi).toBeDefined();
          expect(component.loiPermissions).toBeUndefined();
        });
      });

      describe('when "Data Collectors" is selected', () => {
        beforeEach(fakeAsync(() => {
          const loiPermissions = component.loiPermissions!;
          loiPermissions.formGroup.controls[
            loiPermissions.loiPermissionsControlKey
          ].setValue(LoiPermissionsOption.DATA_COLLECTORS);
          clickContinueButton(fixture);
          flush();
          fixture.detectChanges();
        }));

        it('calls addOrUpdateJob with a filled array for dataCollectorsCanAdd', () => {
          const dataCollectorsCanAdd = ['points', 'polygons'];
          const strategy = DataCollectionStrategy.AD_HOC;

          expect(jobServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
            surveyId,
            job.copyWith({dataCollectorsCanAdd, strategy})
          );
        });

        it('navigates to the task definition component', () => {
          expect(component.taskDetails).toBeDefined();
          expect(component.loiPermissions).toBeUndefined();
        });
      });

      describe('when "Both" is selected', () => {
        beforeEach(fakeAsync(() => {
          const loiPermissions = component.loiPermissions!;
          loiPermissions.formGroup.controls[
            loiPermissions.loiPermissionsControlKey
          ].setValue(LoiPermissionsOption.ORGANIZERS_AND_COLLECTORS);
          clickContinueButton(fixture);
          flush();
          fixture.detectChanges();
        }));

        it('calls addOrUpdateJob with filled array for dataCollectorsCanAdd', () => {
          const dataCollectorsCanAdd = ['points', 'polygons'];
          const strategy = DataCollectionStrategy.BOTH;

          expect(jobServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
            surveyId,
            job.copyWith({dataCollectorsCanAdd, strategy})
          );
        });

        it('navigates to the LOI selection component', () => {
          expect(component.surveyLoi).toBeDefined();
          expect(component.loiPermissions).toBeUndefined();
        });
      });
    });

    describe('clicking back', () => {
      it('navigates to the job details component', fakeAsync(() => {
        clickBackButton(fixture);
        flush();
        fixture.detectChanges();

        expect(component.jobDetails).toBeDefined();
        expect(component.loiPermissions).toBeUndefined();
      }));
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
      it('navigates to LOI selection when skipLoiSelection=false', fakeAsync(() => {
        component.skipLoiSelection = false;
        clickBackButton(fixture);
        flush();
        fixture.detectChanges();

        expect(component.setupPhase).toBe(SetupPhase.DEFINE_LOIS);
      }));

      it('navigates to LOI permissions when skipLoiSelection=true', fakeAsync(() => {
        component.skipLoiSelection = true;
        clickBackButton(fixture);
        flush();
        fixture.detectChanges();

        expect(component.setupPhase).toBe(SetupPhase.DEFINE_LOI_PERMISSIONS);
      }));
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

    it('goes back to task definition component after back button is clicked', () => {
      clickBackButton(fixture);

      expect(component.setupPhase).toBe(SetupPhase.DEFINE_TASKS);
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
