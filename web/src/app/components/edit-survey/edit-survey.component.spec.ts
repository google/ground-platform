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

import { NO_ERRORS_SCHEMA, WritableSignal, signal } from '@angular/core';
import {
  ComponentFixture,
  TestBed,
  discardPeriodicTasks,
  fakeAsync,
  flush,
  tick,
  waitForAsync,
} from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { By } from '@angular/platform-browser';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { List, Map } from 'immutable';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { delay } from 'rxjs/operators';

import { EditSurveyComponent } from 'app/components/edit-survey/edit-survey.component';
import { Job } from 'app/models/job.model';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { Task } from 'app/models/task/task.model';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';
import { JobService } from 'app/services/job/job.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SurveyService } from 'app/services/survey/survey.service';
import { ActivatedRouteStub } from 'testing/activated-route-stub';

import {
  DialogData,
  DialogType,
  JobDialogComponent,
} from './job-dialog/job-dialog.component';

xdescribe('EditSurveyComponent', () => {
  let fixture: ComponentFixture<EditSurveyComponent>;
  let surveyId$: Subject<string | null>;
  let surveyIdSignal: WritableSignal<string | null>;
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
    /* ownerId= */ '',
    { type: DataSharingType.PRIVATE }
  );

  beforeEach(waitForAsync(() => {
    surveyIdSignal = signal<string | null>(null);
    surveyId$ = new Subject<string | null>();

    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      [
        'isShareSurveyPage',
        'getEditSurveyPageSignal',
        'getSurveyId$',
        'getSurveyId',
        'navigateToEditJob',
        'navigateToEditSurvey',
      ]
    );
    navigationServiceSpy.getSurveyId$.and.returnValue(surveyId$);
    navigationServiceSpy.getSurveyId.and.returnValue(surveyIdSignal);
    navigationServiceSpy.getEditSurveyPageSignal.and.returnValue(signal(''));

    route = new ActivatedRouteStub();
    surveyServiceSpy = jasmine.createSpyObj<SurveyService>('SurveyService', [
      'activateSurvey',
      'getActiveSurvey$',
    ]);
    activeSurvey$ = new Subject<Survey>();
    surveyServiceSpy.getActiveSurvey$.and.returnValue(activeSurvey$);

    draftSurveyServiceSpy = jasmine.createSpyObj<DraftSurveyService>(
      'DraftSurveyService',
      ['init', 'getSurvey$', 'addOrUpdateJob', 'deleteJob', 'getSurvey']
    );
    draftSurveyServiceSpy.getSurvey$.and.returnValue(
      of(survey).pipe(delay(1))
    );
    draftSurveyServiceSpy.getSurvey.and.returnValue(survey);
    draftSurveyServiceSpy.init.and.returnValue(Promise.resolve());

    jobServiceSpy = jasmine.createSpyObj<JobService>('JobService', [
      'createNewJob',
      'duplicateJob',
      'getNextColor',
    ]);
    jobServiceSpy.createNewJob.and.returnValue(newJob);
    jobServiceSpy.duplicateJob.and.returnValue(newJob);
    jobServiceSpy.getNextColor.and.returnValue(undefined);

    dataStoreServiceSpy = jasmine.createSpyObj<DataStoreService>(
      'DataStoreService',
      ['loadSurvey$', 'tasks$']
    );
    dataStoreServiceSpy.loadSurvey$.and.returnValue(activeSurvey$);
    dataStoreServiceSpy.tasks$.and.returnValue(new Subject<List<Task>>());

    dialogRefSpy = jasmine.createSpyObj<
      MatDialogRef<JobDialogComponent, DialogData>
    >('MatDialogRef', ['afterClosed', 'close']);
    dialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    dialogSpy.open.and.returnValue(dialogRefSpy);

    TestBed.configureTestingModule({
      imports: [
        RouterTestingModule,
        MatDividerModule,
        MatMenuModule,
        MatProgressSpinnerModule,
        NoopAnimationsModule,
      ],
      declarations: [EditSurveyComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: NavigationService, useValue: navigationServiceSpy },
        { provide: SurveyService, useValue: surveyServiceSpy },
        { provide: DraftSurveyService, useValue: draftSurveyServiceSpy },
        { provide: JobService, useValue: jobServiceSpy },
        { provide: DataStoreService, useValue: dataStoreServiceSpy },
        { provide: ActivatedRoute, useValue: route },
        { provide: MatDialog, useValue: dialogSpy },
      ],
    }).compileComponents();
  }));

  beforeEach(fakeAsync(() => {
    fixture = TestBed.createComponent(EditSurveyComponent);
  }));

  it('shows spinner when survey not loaded', () => {
    fixture.detectChanges();
    const spinner = fixture.debugElement.query(By.css('#loading-spinner'))
      .nativeElement as HTMLElement;
    // TODO(#1170): Extract the spinner into a component
    expect(spinner.innerText).toContain('Loading survey...');
  });

  describe('when routed in with survey ID', () => {
    beforeEach(fakeAsync(() => {
      surveyIdSignal.set(surveyId);
      surveyId$.next(surveyId);
      tick(1);
      fixture.detectChanges();
    }));

    it('activates survey ID', () => {
      expect(surveyServiceSpy.activateSurvey).toHaveBeenCalledOnceWith(
        surveyId
      );
    });
  });

  describe('when survey activated', () => {
    beforeEach(fakeAsync(() => {
      surveyIdSignal.set(surveyId);
      surveyId$.next(surveyId);
      // activeSurvey$.next(survey); // Removed to prevent NG0100
      fixture.detectChanges();
      tick(1);
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
      ].forEach(({ buttonSelector, expectedLabel, expectedRouterLink }) => {
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
      it('add a job', fakeAsync(() => {
        const addButton = fixture.debugElement.query(By.css('#add-button'))
          .nativeElement as HTMLElement;
        const newJobName = 'new job name';
        dialogRefSpy.afterClosed.and.returnValue(
          of({ dialogType: DialogType.AddJob, jobName: newJobName })
        );

        addButton.click();

        expect(draftSurveyServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
          newJob.copyWith({ name: newJobName })
        );
        flush();
        discardPeriodicTasks();
      }));

      it('rename a job', fakeAsync(() => {
        const menuButton = fixture.debugElement.query(By.css('#menu-button-0'))
          .nativeElement as HTMLElement;
        const newJobName = 'new job name';
        dialogRefSpy.afterClosed.and.returnValue(
          of({ dialogType: DialogType.RenameJob, jobName: newJobName })
        );

        menuButton.click();
        fixture.detectChanges();
        tick();

        const renameButton = document.querySelector(
          '#rename-button-0'
        ) as HTMLElement;
        renameButton.click();

        expect(draftSurveyServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
          job1.copyWith({ name: newJobName })
        );
        flush();
        discardPeriodicTasks();
      }));

      it('duplicate a job', fakeAsync(() => {
        const menuButton = fixture.debugElement.query(By.css('#menu-button-0'))
          .nativeElement as HTMLElement;

        menuButton.click();
        fixture.detectChanges();
        tick();

        const duplicateButton = document.querySelector(
          '#duplicate-button-0'
        ) as HTMLElement;
        duplicateButton.click();

        expect(draftSurveyServiceSpy.addOrUpdateJob).toHaveBeenCalledOnceWith(
          jobServiceSpy.duplicateJob(
            job1,
            jobServiceSpy.getNextColor(survey.jobs)
          ),
          true
        );
        flush();
        discardPeriodicTasks();
      }));

      it('delete a job', fakeAsync(() => {
        const menuButton = fixture.debugElement.query(By.css('#menu-button-0'))
          .nativeElement as HTMLElement;
        dialogRefSpy.afterClosed.and.returnValue(
          of({ dialogType: DialogType.DeleteJob, jobName: '' })
        );

        menuButton.click();
        fixture.detectChanges();
        tick();

        const deleteButton = document.querySelector(
          '#delete-button-0'
        ) as HTMLElement;
        deleteButton.click();

        expect(draftSurveyServiceSpy.deleteJob).toHaveBeenCalledOnceWith(job1);
        flush();
        discardPeriodicTasks();
      }));
    });
  });
});
