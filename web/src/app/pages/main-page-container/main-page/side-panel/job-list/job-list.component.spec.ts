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
import {ComponentFixture, TestBed, waitForAsync} from '@angular/core/testing';
import {JobListComponent} from './job-list.component';
import {SurveyService} from 'app/services/survey/survey.service';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {Survey} from 'app/models/survey.model';
import {of} from 'rxjs';
import {Map} from 'immutable';
import {Job} from 'app/models/job.model';
import {MatLegacyListModule as MatListModule} from '@angular/material/legacy-list';
import {Router} from '@angular/router';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {DataStoreService} from 'app/services/data-store/data-store.service';

const mockSurvey = new Survey(
  'survey001',
  'title',
  'description',
  /* jobs= */ Map({
    job001: new Job(
      'job001',
      /* index */ -1,
      'red',
      'name',
      /* tasks= */ Map()
    ),
  }),
  /* acl= */ Map()
);

const authState = {
  displayName: null,
  isAnonymous: true,
  uid: '',
};

const mockAngularFireAuth = {
  authState: of(authState),
};

class MockSurveyService {
  getActiveSurvey$() {
    return of<Survey>(mockSurvey);
  }
  getCurrentSurvey() {}
  getCurrentSurveyAcl() {}
  canManageSurvey() {}
}

const surveyService = new MockSurveyService();

describe('JobListComponent', () => {
  let component: JobListComponent;
  let fixture: ComponentFixture<JobListComponent>;

  beforeEach(waitForAsync(() => {
    const navigationService = {
      getSurveyId$: () => of(''),
      getLocationOfInterestId$: () => of(''),
    };
    const routerSpy = createRouterSpy();
    TestBed.configureTestingModule({
      declarations: [JobListComponent],
      imports: [MatListModule],
      providers: [
        {provide: SurveyService, useValue: surveyService},
        {
          provide: Router,
          useValue: routerSpy,
        },
        {provide: NavigationService, useValue: navigationService},
        {provide: AngularFirestore, useValue: {}},
        {
          provide: AngularFireAuth,
          useValue: mockAngularFireAuth,
        },
        {provide: DataStoreService, useValue: {user$: () => of()}},
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(JobListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

function createRouterSpy() {
  return jasmine.createSpyObj('Router', ['navigate']);
}
