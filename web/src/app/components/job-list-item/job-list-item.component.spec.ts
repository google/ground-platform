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

import {AngularFireAuth} from '@angular/fire/compat/auth';
import {AngularFirestore} from '@angular/fire/compat/firestore';
import {HarnessLoader} from '@angular/cdk/testing';
import {TestbedHarnessEnvironment} from '@angular/cdk/testing/testbed';
import {ComponentFixture, TestBed, waitForAsync} from '@angular/core/testing';
import {
  MatTreeHarness,
  MatTreeNodeHarness,
} from '@angular/material/tree/testing';
import {MatLegacyButtonHarness as MatButtonHarness} from '@angular/material/legacy-button/testing';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {JobListItemComponent} from './job-list-item.component';
import {MatIconModule} from '@angular/material/icon';
import {MatLegacyMenuModule as MatMenuModule} from '@angular/material/legacy-menu';
import {MatLegacyListModule as MatListModule} from '@angular/material/legacy-list';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {of, Subject} from 'rxjs';
import {MatLegacyDialogModule as MatDialogModule} from '@angular/material/legacy-dialog';
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
import {AuthService} from 'app/services/auth/auth.service';
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
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let lois$: Subject<List<LocationOfInterest>>;
  let submissions$: Subject<List<Submission>>;
  let surveyId$: Subject<string | null>;
  let locationOfInterestId$: Subject<string | null>;

  const user = {
    id: 'user001',
    email: 'email@gmail.com',
    isAuthenticated: false,
  };

  const job = new Job(
    /* id= */ 'job001',
    /* index= */ 0,
    /* color= */ '#fff',
    /* name= */ 'job 1'
  );

  const surveyId = 'survey1';
  const survey = new Survey(
    /* id= */ surveyId,
    /* title= */ 'title1',
    /* description= */ 'description1',
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
          /* id= */ 'loi' + i,
          /* jobId= */ job.id,
          /* geometry= */ new Point(new Coordinate(1.23, 4.56)),
          /* properties= */ Map()
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
      'requireActiveSurvey$',
    ]);

    loiServiceSpy = jasmine.createSpyObj<LocationOfInterestService>(
      'LocationOfInterestService',
      ['getLocationsOfInterest$']
    );

    submissionServiceSpy = jasmine.createSpyObj<SubmissionService>(
      'SubmissionService',
      ['submissions$']
    );

    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      ['getSurveyId$', 'getLocationOfInterestId$', 'selectLocationOfInterest']
    );

    lois$ = new Subject<List<LocationOfInterest>>();
    submissions$ = new Subject<List<Submission>>();
    surveyId$ = new Subject<string | null>();
    locationOfInterestId$ = new Subject<string | null>();

    surveyServiceSpy.requireActiveSurvey$.and.returnValue(of(survey));
    spyOn(
      LocationOfInterestService,
      'getLoiNameFromProperties'
    ).and.returnValue(null);
    loiServiceSpy.getLocationsOfInterest$.and.returnValue(lois$);
    submissionServiceSpy.submissions$.and.returnValue(submissions$);
    navigationServiceSpy.getSurveyId$.and.returnValue(surveyId$);
    navigationServiceSpy.getLocationOfInterestId$.and.returnValue(
      locationOfInterestId$
    );

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
        {provide: NavigationService, useValue: navigationServiceSpy},
        {provide: Router, useValue: {}},
        {provide: SurveyService, useValue: surveyServiceSpy},
        {provide: LocationOfInterestService, useValue: loiServiceSpy},
        {provide: SubmissionService, useValue: submissionServiceSpy},
        {provide: AngularFirestore, useValue: {}},
        {provide: AuthService, useValue: {}},
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

    surveyId$.next(surveyId);
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render job tree', async () => {
    const jobTree = await loader.getHarness(MatTreeHarness);
    expect((await jobTree.getNodes()).length).toBe(1);
  });

  it('should render lois for a job', async () => {
    const jobTree = await loader.getHarness(MatTreeHarness);
    const jobNode = (await jobTree.getNodes())[0];
    await jobNode.expand();

    lois$.next(createLois(2));

    // One node for the job, 2 nodes for the lois
    expect((await jobTree.getNodes()).length).toBe(3);
  });

  it('should dynamically render lois for a job', async () => {
    const jobTree = await loader.getHarness(MatTreeHarness);
    const jobNode = (await jobTree.getNodes())[0];
    await jobNode.expand();

    lois$.next(createLois(2));
    lois$.next(createLois(1));

    // One node for the job, one node for the loi
    expect((await jobTree.getNodes()).length).toBe(2);
  });

  it('should select LOI when LOI is clicked', async () => {
    const jobTree = await loader.getHarness(MatTreeHarness);
    const jobNode = (await jobTree.getNodes())[0];
    await jobNode.expand();

    const lois = createLois(1);
    lois$.next(lois);
    const loiId = lois.first()!.id;

    const selectLoiButton = await loader.getHarness(
      MatButtonHarness.with({selector: '#select-loi'})
    );
    await selectLoiButton.click();

    expect(
      navigationServiceSpy.selectLocationOfInterest
    ).toHaveBeenCalledOnceWith(loiId);
  });
});
