/**
 * Copyright 2020 The Ground Authors.
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

import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { CdkTreeModule } from '@angular/cdk/tree';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Auth } from '@angular/fire/auth';
import { Firestore } from '@angular/fire/firestore';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatDialogModule } from '@angular/material/dialog';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatTreeModule } from '@angular/material/tree';
import { MatTreeHarness } from '@angular/material/tree/testing';
import { Router } from '@angular/router';
import { List, Map } from 'immutable';
import { Subject, of } from 'rxjs';

import { Coordinate } from 'app/models/geometry/coordinate';
import { Point } from 'app/models/geometry/point';
import { Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { Submission } from 'app/models/submission/submission.model';

import { GroundIconModule } from 'app/modules/ground-icon.module';
import { AuthService } from 'app/services/auth/auth.service';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { UrlParams } from 'app/services/navigation/url-params';
import { SubmissionService } from 'app/services/submission/submission.service';
import { SurveyService } from 'app/services/survey/survey.service';

import { JobListItemComponent } from './job-list-item.component';

const authState = {
  displayName: null,
  isAnonymous: true,
  uid: '',
};

const mockAuth = {
  currentUser: authState,
};

describe('JobListItemComponent', () => {
  let component: JobListItemComponent;
  let fixture: ComponentFixture<JobListItemComponent>;
  let loader: HarnessLoader;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;
  let submissionServiceSpy: jasmine.SpyObj<SubmissionService>;
  let loiServiceSpy: jasmine.SpyObj<LocationOfInterestService>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let submissions$: Subject<List<Submission>>;
  let surveyId$: Subject<string | null>;
  let locationOfInterestId$: Subject<string | null>;
  let urlParamsSignal: WritableSignal<UrlParams>;

  const job = new Job(
    /* id= */ 'job001',
    /* index= */ 0,
    /* color= */ '#fff',
    /* name= */ 'job 1'
  );

  const surveyId = 'survey1';


  function createLois(count: number): List<LocationOfInterest> {
    const lois: LocationOfInterest[] = [];
    for (let i = 0; i < count; i++) {
      lois.push(
        new LocationOfInterest(
          /* id= */ 'loi' + i,
          /* jobId= */ job.id,
          /* geometry= */ new Point(new Coordinate(1.23, 4.56)),
          /* properties= */ Map()
        )
      );
    }
    return List(lois);
  }

  beforeEach(async () => {
    surveyServiceSpy = jasmine.createSpyObj<SurveyService>('SurveyService', [
      'canManageSurvey',
    ]);

    loiServiceSpy = jasmine.createSpyObj<LocationOfInterestService>(
      'LocationOfInterestService',
      ['getLocationsOfInterest$']
    );

    submissionServiceSpy = jasmine.createSpyObj<SubmissionService>(
      'SubmissionService',
      ['getSubmissions$']
    );

    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      [
        'getSurveyId$',
        'getLocationOfInterestId$',
        'selectLocationOfInterest',
        'getSurveyId',
        'getLoiId',
        'getUrlParams',
        'getSidePanelExpanded',
        'isEditSurveyPage',
      ]
    );

    submissions$ = new Subject<List<Submission>>();
    surveyId$ = new Subject<string | null>();
    locationOfInterestId$ = new Subject<string | null>();
    urlParamsSignal = signal<UrlParams>(new UrlParams(null, null, null, null));

    spyOn(LocationOfInterestService, 'getDisplayName').and.returnValue('');
    submissionServiceSpy.getSubmissions$.and.returnValue(submissions$);
    navigationServiceSpy.getSurveyId$.and.returnValue(surveyId$);
    navigationServiceSpy.getLocationOfInterestId$.and.returnValue(
      locationOfInterestId$
    );
    navigationServiceSpy.getUrlParams.and.returnValue(urlParamsSignal);

    await TestBed.configureTestingModule({
      declarations: [JobListItemComponent],
      imports: [
        GroundIconModule,
        MatDialogModule,
        MatListModule,
        MatMenuModule,
        MatTreeModule,
        CdkTreeModule,
      ],
      providers: [
        { provide: DataStoreService, useValue: { user$: () => of() } },
        { provide: NavigationService, useValue: navigationServiceSpy },
        { provide: Router, useValue: {} },
        { provide: SurveyService, useValue: surveyServiceSpy },
        { provide: LocationOfInterestService, useValue: loiServiceSpy },
        { provide: SubmissionService, useValue: submissionServiceSpy },
        { provide: Firestore, useValue: {} },
        { provide: AuthService, useValue: {} },
        {
          provide: Auth,
          useValue: mockAuth,
        },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(JobListItemComponent);
    component = fixture.componentInstance;
    component.job = job;
    component.lois = List();
    fixture.detectChanges();
    loader = TestbedHarnessEnvironment.loader(fixture);

    surveyId$.next(surveyId);
    urlParamsSignal.set(new UrlParams(surveyId, null, null, null));
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render job tree', async () => {
    const jobTree = await loader.getHarness(MatTreeHarness);
    expect((await jobTree.getNodes()).length).toBe(1);
  });

  it('should render lois for a job', async () => {
    fixture.componentRef.setInput('lois', createLois(2));
    fixture.detectChanges();

    const jobTree = await loader.getHarness(MatTreeHarness);
    const jobNode = (await jobTree.getNodes())[0];
    await jobNode.expand();

    // One node for the job, 2 nodes for the lois
    expect((await jobTree.getNodes()).length).toBe(3);
  });

  it('should dynamically render lois for a job', async () => {
    const jobTree = await loader.getHarness(MatTreeHarness);
    const jobNode = (await jobTree.getNodes())[0];
    await jobNode.expand();

    fixture.componentRef.setInput('lois', createLois(3));
    fixture.detectChanges();

    // One node for the job, three nodes for the loi
    expect((await jobTree.getNodes()).length).toBe(4);
  });

  it('should select LOI when LOI is clicked', async () => {
    const jobTree = await loader.getHarness(MatTreeHarness);
    const jobNode = (await jobTree.getNodes())[0];
    await jobNode.expand();

    const lois = createLois(1);
    const loiId = lois.first()!.id;

    fixture.componentRef.setInput('lois', lois);
    fixture.detectChanges();

    const selectLoiButton = await loader.getHarness(
      MatButtonHarness.with({ selector: '.loi-tree-node' })
    );
    await selectLoiButton.click();

    expect(
      navigationServiceSpy.selectLocationOfInterest
    ).toHaveBeenCalledOnceWith(surveyId, loiId);
  });
});
