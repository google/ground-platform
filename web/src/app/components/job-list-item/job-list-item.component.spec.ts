/**
 * Copyright 2020 Google LLC
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

import {AngularFireAuth} from '@angular/fire/compat/auth';
import {AngularFirestore} from '@angular/fire/compat/firestore';
import {HarnessLoader} from '@angular/cdk/testing';
import {TestbedHarnessEnvironment} from '@angular/cdk/testing/testbed';
import {ComponentFixture, TestBed, waitForAsync} from '@angular/core/testing';
import {
  MatTreeHarness,
  MatTreeNodeHarness,
} from '@angular/material/tree/testing';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {JobListItemComponent} from './job-list-item.component';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatListModule} from '@angular/material/list';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {of, Subject} from 'rxjs';
import {MatDialogModule} from '@angular/material/dialog';
import {Router} from '@angular/router';
import {MatTreeModule} from '@angular/material/tree';
import {CdkTreeModule} from '@angular/cdk/tree';
import {SubmissionService} from 'app/services/submission/submission.service';
import {AuditInfo} from 'app/models/audit-info.model';
import {Submission} from 'app/models/submission/submission.model';
import {List, Map} from 'immutable';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {
  GenericLocationOfInterest,
  LocationOfInterest,
} from 'app/models/loi.model';
import {Point} from 'app/models/geometry/point';
import {Coordinate} from 'app/models/geometry/coordinate';
import {Job} from 'app/models/job.model';
import {SurveyService} from 'app/services/survey/survey.service';
import {Survey} from 'app/models/survey.model';
const authState = {
  displayName: null,
  isAnonymous: true,
  uid: '',
};

const mockAngularFireAuth = {
  authState: of(authState),
};

describe('JobListItemComponent', () => {
  let component: JobListItemComponent;
  let fixture: ComponentFixture<JobListItemComponent>;
  let loader: HarnessLoader;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;
  let submissionServiceSpy: jasmine.SpyObj<SubmissionService>;
  let loiServiceSpy: jasmine.SpyObj<LocationOfInterestService>;
  let lois$: Subject<List<LocationOfInterest>>;
  let submissions$: Subject<List<Submission>>;

  const user = {
    id: 'user001',
    email: 'email@gmail.com',
    isAuthenticated: false,
  };

  const job = new Job('job001', /* index */ 0);

  const survey = new Survey(
    'survey1',
    'title1',
    'description1',
    /* jobs= */ Map({
      job001: job,
    }),
    /* acl= */ Map()
  );

  function createLois(count: number): List<LocationOfInterest> {
    const lois: LocationOfInterest[] = [];
    for (let i = 0; i < count; i++) {
      lois.push(
        new GenericLocationOfInterest(
          'loi' + i,
          job.id,
          new Point(new Coordinate(1.23, 4.56)),
          Map()
        )
      );
    }
    return List(lois);
  }

  function createSubmissions(
    loi: LocationOfInterest,
    count: number
  ): List<Submission> {
    const submissions: Submission[] = [];
    for (let i = 0; i < count; i++) {
      submissions.push(
        new Submission(
          'submission' + i,
          loi.id,
          job,
          new AuditInfo(user, new Date(), new Date()),
          new AuditInfo(user, new Date(), new Date()),
          /* results= */ Map()
        )
      );
    }
    return List(submissions);
  }

  beforeEach(waitForAsync(() => {
    surveyServiceSpy = jasmine.createSpyObj<SurveyService>('SurveyService', [
      'canManageSurvey',
      'getActiveSurvey$',
    ]);

    loiServiceSpy = jasmine.createSpyObj<LocationOfInterestService>(
      'LocationOfInterestService',
      ['getLocationsOfInterest$', 'getLoiNameFromProperties']
    );

    submissionServiceSpy = jasmine.createSpyObj<SubmissionService>(
      'SubmissionService',
      ['submissions$']
    );

    lois$ = new Subject<List<LocationOfInterest>>();
    submissions$ = new Subject<List<Submission>>();

    surveyServiceSpy.getActiveSurvey$.and.returnValue(of(survey));
    loiServiceSpy.getLoiNameFromProperties.and.returnValue(null);
    loiServiceSpy.getLocationsOfInterest$.and.returnValue(lois$);
    submissionServiceSpy.submissions$.and.returnValue(submissions$);

    const navigationService = {
      getSurveyId$: () => of(''),
      getLocationOfInterestId$: () => of(''),
    };

    TestBed.configureTestingModule({
      declarations: [JobListItemComponent],
      imports: [
        MatIconModule,
        MatListModule,
        MatMenuModule,
        MatDialogModule,
        MatTreeModule,
        CdkTreeModule,
      ],
      providers: [
        {provide: DataStoreService, useValue: {user$: () => of()}},
        {provide: NavigationService, useValue: navigationService},
        {provide: Router, useValue: {}},
        {provide: SurveyService, useValue: surveyServiceSpy},
        {provide: LocationOfInterestService, useValue: loiServiceSpy},
        {provide: SubmissionService, useValue: submissionServiceSpy},
        {provide: AngularFirestore, useValue: {}},
        {
          provide: AngularFireAuth,
          useValue: mockAngularFireAuth,
        },
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(JobListItemComponent);
    component = fixture.componentInstance;
    component.job = job;
    fixture.detectChanges();
    loader = TestbedHarnessEnvironment.loader(fixture);
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render lois for a job', async () => {
    lois$.next(createLois(2));

    const job = await loader.getHarness(MatTreeHarness);
    const loiNodes = await job.getNodes();
    expect(loiNodes.length).toBe(2);
  });

  it('should dynamically render lois for a job', async () => {
    lois$.next(createLois(1));

    const job = await loader.getHarness(MatTreeHarness);
    expect((await job.getNodes()).length).toBe(1);

    lois$.next(createLois(4));
    expect((await job.getNodes()).length).toBe(4);
  });

  it('should render submissions for an loi', async () => {
    const lois = createLois(1);
    lois$.next(lois);

    const loiNode = await loader.getHarness(MatTreeNodeHarness);
    await loiNode.toggle();

    submissions$.next(createSubmissions(lois.first(), 3));

    const job = await loader.getHarness(MatTreeHarness);
    // Expect 4 nodes since one is for the loi and 3 for submissions
    expect((await job.getNodes()).length).toBe(4);
  });

  it('should dynamically render submissions for an loi', async () => {
    const lois = createLois(1);
    lois$.next(lois);

    const loiNode = await loader.getHarness(MatTreeNodeHarness);
    await loiNode.toggle();

    submissions$.next(createSubmissions(lois.first(), 3));
    submissions$.next(createSubmissions(lois.first(), 9));

    const job = await loader.getHarness(MatTreeHarness);
    // Expect 10 nodes since one is for the loi and 9 for submissions
    expect((await job.getNodes()).length).toBe(10);

    submissions$.next(createSubmissions(lois.first(), 5));
    // Expect 6 nodes since one is for the loi and 5 for submissions
    expect((await job.getNodes()).length).toBe(6);
  });
});
