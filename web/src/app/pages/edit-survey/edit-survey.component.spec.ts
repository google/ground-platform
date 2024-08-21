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
  tick,
  waitForAsync,
} from '@angular/core/testing';
import {MatDialog, MatDialogRef} from '@angular/material/dialog';
import {By} from '@angular/platform-browser';
import {ActivatedRoute} from '@angular/router';
import {RouterTestingModule} from '@angular/router/testing';
import {Map} from 'immutable';
import {BehaviorSubject, Subject, of} from 'rxjs';

import {Job} from 'app/models/job.model';
import {DataSharingType, Survey} from 'app/models/survey.model';
import {EditSurveyComponent} from 'app/pages/edit-survey/edit-survey.component';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {DraftSurveyService} from 'app/services/draft-survey/draft-survey.service';
import {JobService} from 'app/services/job/job.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {ActivatedRouteStub} from 'testing/activated-route-stub';

import {
  DialogData,
  DialogType,
  JobDialogComponent,
} from './job-dialog/job-dialog.component';

describe('EditSurveyComponent', () => {
  let fixture: ComponentFixture<EditSurveyComponent>;
  let surveyId$: Subject<string | null>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let route: ActivatedRouteStub;
  let activeSurvey$: Subject<Survey>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;
  let draftSurveyServiceSpy: jasmine.SpyObj<DraftSurveyService>;
  let jobServiceSpy: jasmine.SpyObj<JobService>;
  let dataStoreServiceSpy: jasmine.SpyObj<DataStoreService>;
  let dialogRefSpy: jasmine.SpyObj<
    MatDialogRef<JobDialogComponent, DialogData>
  >;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  const surveyId = 'survey001';
  const jobId1 = 'job001';
  const jobName1 = 'Job Name 1';
  const jobId2 = 'job002';
  const jobName2 = 'Job Name 2';
  const job1 = new Job(
    jobId1,
    /* index */ -1,
    'red',
    jobName1,
    /* tasks= */ Map()
  );
  const job2 = new Job(
    jobId2,
    /* index */ -1,
    'blue',
    jobName2,
    /* tasks= */ Map()
  );
  const newJobId = 'job999';
  const newJob = new Job(newJobId, /* index */ -1);
  const survey = new Survey(
    surveyId,
    'title',
    'description',
    /* jobs= */ Map([
      [jobId1, job1],
      [jobId2, job2],
    ]),
    /* acl= */ Map(),
    '',
    {type: DataSharingType.PRIVATE}
  );
  beforeEach(waitForAsync(() => {
    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      ['init', 'getSurveyId$']
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

    draftSurveyServiceSpy = jasmine.createSpyObj<DraftSurveyService>(
      'DraftSurveyService',
      ['init', 'getSurvey$', 'addOrUpdateJob', 'deleteJob']
    );
    draftSurveyServiceSpy.getSurvey$.and.returnValue(
      new BehaviorSubject<Survey>(survey)
    );

    jobServiceSpy = jasmine.createSpyObj<JobService>('JobService', [
      'createNewJob',
      'duplicateJob',
      'getNextColor',
    ]);
    jobServiceSpy.createNewJob.and.returnValue(newJob);
    jobServiceSpy.getNextColor.and.returnValue(undefined);

    dataStoreServiceSpy = jasmine.createSpyObj<DataStoreService>(
      'DataStoreService',
      ['loadSurvey$']
    );
    dataStoreServiceSpy.loadSurvey$.and.returnValue(activeSurvey$);

    dialogRefSpy = jasmine.createSpyObj<
      MatDialogRef<JobDialogComponent, DialogData>
    >('MatDialogRef', ['afterClosed', 'close']);
    dialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    dialogSpy.open.and.returnValue(dialogRefSpy);

    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      declarations: [EditSurveyComponent],
      providers: [
        {provide: NavigationService, useValue: navigationServiceSpy},
        {provide: SurveyService, useValue: surveyServiceSpy},
        {provide: DraftSurveyService, useValue: draftSurveyServiceSpy},
        {provide: JobService, useValue: jobServiceSpy},
        {provide: DataStoreService, useValue: dataStoreServiceSpy},
        {provide: ActivatedRoute, useValue: route},
        {provide: MatDialog, useValue: dialogSpy},
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(EditSurveyComponent);
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

  describe('when survey activated', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(survey);
      tick();
      fixture.detectChanges();
    }));

    describe('menu item tests', () => {
      [
        {
          buttonSelector: '#survey-button',
          expectedLabel: 'Survey',
          expectedRouterLink: './survey',
        },
        {
          buttonSelector: '#share-button',
          expectedLabel: 'Sharing',
          expectedRouterLink: './share',
        },
        {
          buttonSelector: '#job-0',
          expectedLabel: jobName1,
          expectedRouterLink: `./job/${jobId1}`,
        },
        {
          buttonSelector: '#job-1',
          expectedLabel: jobName2,
          expectedRouterLink: `./job/${jobId2}`,
        },
      ].forEach(({buttonSelector, expectedLabel, expectedRouterLink}) => {
        it('displays button with correct label and router link', () => {
          const button = fixture.debugElement.query(By.css(buttonSelector))
            .nativeElement as HTMLElement;

          expect(button.textContent).toContain(expectedLabel);
          expect(button.getAttribute('ng-reflect-router-link')).toEqual(
            expectedRouterLink
          );
        });
      });
    });

    describe('add/rename/duplicate/delete a job', () => {
      it('add a job', () => {
        const addButton = fixture.debugElement.query(By.css('#add-button'))
          .nativeElement as HTMLElement;
        const newJobName = 'new job name';
        dialogRefSpy.afterClosed.and.returnValue(
          of({dialogType: DialogType.AddJob, jobName: newJobName})
        );

        addButton.click();

        expect(draftSurveyServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
          newJob.copyWith({name: newJobName})
        );
      });

      it('rename a job', () => {
        const menuButton = fixture.debugElement.query(By.css('#menu-button-0'))
          .nativeElement as HTMLElement;
        const renameButton = fixture.debugElement.query(
          By.css('#rename-button-0')
        ).nativeElement as HTMLElement;
        const newJobName = 'new job name';
        dialogRefSpy.afterClosed.and.returnValue(
          of({dialogType: DialogType.RenameJob, jobName: newJobName})
        );

        menuButton.click();
        renameButton.click();

        expect(draftSurveyServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
          job1.copyWith({name: newJobName})
        );
      });

      it('duplicate a job', () => {
        const menuButton = fixture.debugElement.query(By.css('#menu-button-0'))
          .nativeElement as HTMLElement;
        const duplicateButton = fixture.debugElement.query(
          By.css('#duplicate-button-0')
        ).nativeElement as HTMLElement;

        menuButton.click();
        duplicateButton.click();

        expect(draftSurveyServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
          jobServiceSpy.duplicateJob(
            job1,
            jobServiceSpy.getNextColor(survey.jobs)
          ),
          true
        );
      });

      it('delete a job', () => {
        const menuButton = fixture.debugElement.query(By.css('#menu-button-0'))
          .nativeElement as HTMLElement;
        const deleteButton = fixture.debugElement.query(
          By.css('#delete-button-0')
        ).nativeElement as HTMLElement;
        dialogRefSpy.afterClosed.and.returnValue(
          of({dialogType: DialogType.DeleteJob, jobName: ''})
        );

        menuButton.click();
        deleteButton.click();

        expect(draftSurveyServiceSpy.deleteJob).toHaveBeenCalledOnceWith(job1);
      });
    });
  });
});
