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

import {NO_ERRORS_SCHEMA} from '@angular/core';
import {ComponentFixture, TestBed, waitForAsync} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {Router} from '@angular/router';
import {List, Map} from 'immutable';
import {of} from 'rxjs';

import {Coordinate} from 'app/models/geometry/coordinate';
import {Point} from 'app/models/geometry/point';
import {Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {Submission} from 'app/models/submission/submission.model';
import {DataSharingType, Survey} from 'app/models/survey.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SubmissionService} from 'app/services/submission/submission.service';
import {SurveyService} from 'app/services/survey/survey.service';

import {LocationOfInterestPanelComponent} from './loi-panel.component';

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
  /* acl= */ Map(),
  /* ownerId= */ '',
  {type: DataSharingType.PRIVATE}
);

const mockLocationOfInterest = new LocationOfInterest(
  'loi001',
  'job001',
  new Point(new Coordinate(0.0, 0.0)),
  Map()
);

const mockSubmissions = List<Submission>([]);

class MockSurveyService {
  getActiveSurvey$() {
    return of<Survey>(mockSurvey);
  }
}

class MockLocationOfInterestService {
  getSelectedLocationOfInterest$() {
    return of<LocationOfInterest>(mockLocationOfInterest);
  }
}

class MockSubmissionService {
  getSubmissions$() {
    return of<List<Submission>>(mockSubmissions);
  }
}

const surveyService = new MockSurveyService();
const loiService = new MockLocationOfInterestService();
const submissionService = new MockSubmissionService();
const navigationService = {
  getSurveyId$: () => of(''),
  getSubmissionId$: () => of(''),
};

describe('LocationOfInterestPanelComponent', () => {
  let component: LocationOfInterestPanelComponent;
  let fixture: ComponentFixture<LocationOfInterestPanelComponent>;

  beforeEach(waitForAsync(() => {
    const routerSpy = createRouterSpy();
    TestBed.configureTestingModule({
      declarations: [LocationOfInterestPanelComponent],
      imports: [MatDialogModule],
      providers: [
        {provide: DataStoreService, useValue: {}},
        {
          provide: LocationOfInterestService,
          useValue: loiService,
        },
        {provide: SurveyService, useValue: surveyService},
        {provide: SubmissionService, useValue: submissionService},
        {provide: Router, useValue: routerSpy},
        {provide: NavigationService, useValue: navigationService},
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(LocationOfInterestPanelComponent);
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
