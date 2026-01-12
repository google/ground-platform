/**
 * Copyright 2019 The Ground Authors.
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

import { Component, NO_ERRORS_SCHEMA, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Auth } from '@angular/fire/auth';
import { Firestore } from '@angular/fire/firestore';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { List } from 'immutable';
import { NEVER, of } from 'rxjs';

import { Survey } from 'app/models/survey.model';
import { AuthService } from 'app/services/auth/auth.service';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SubmissionService } from 'app/services/submission/submission.service';
import { SurveyService } from 'app/services/survey/survey.service';
import { ActivatedRouteStub } from 'testing/activated-route-stub';

import { MainPageComponent } from './main-page.component';

@Component({
  selector: 'ground-map',
  template: '',
  standalone: false,
})
class MapComponent {}

@Component({
  selector: 'mat-sidenav',
  template: '',
  standalone: false,
})
class MatSideNavComponent {
  opened = false;
}

describe('MainPageComponent', () => {
  let component: MainPageComponent;
  let fixture: ComponentFixture<MainPageComponent>;
  let route: ActivatedRouteStub;
  const dialog: Partial<MatDialog> = {};

  beforeEach(async () => {
    route = new ActivatedRouteStub();

    const surveyService = jasmine.createSpyObj('SurveyService', [
      'getActiveSurvey$',
      'activateSurvey',
    ]);

    const loiService = jasmine.createSpyObj('LocationOfInterestService', [
      'getLocationsOfInterest$',
    ]);

    const submissionService = jasmine.createSpyObj('SubmissionService', [
      'getSubmissions$',
    ]);

    const navigationService = {
      getSurveyId$: () => NEVER,
      getLocationOfInterestId$: () => NEVER,
      getSubmissionId$: () => NEVER,
      getUrlParams: () => signal({}),
    };

    loiService.getLocationsOfInterest$.and.returnValue(of(List([])));

    await TestBed.configureTestingModule({
      declarations: [MainPageComponent, MapComponent, MatSideNavComponent],
      providers: [
        { provide: ActivatedRoute, useValue: route },
        { provide: MatDialog, useValue: dialog },
        {
          provide: LocationOfInterestService,
          useValue: loiService,
        },
        { provide: SubmissionService, useValue: submissionService },
        { provide: SurveyService, useValue: surveyService },
        { provide: NavigationService, useValue: navigationService },
        { provide: Firestore, useValue: {} },
        { provide: Auth, useValue: {} },
        { provide: Router, useValue: {} },
        {
          provide: AuthService,
          useValue: { getUser$: () => NEVER, isAuthenticated$: () => NEVER },
        },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(MainPageComponent);

    // Create a minimal mock survey for the input
    const mockSurvey = {
      id: 'survey1',
      title: 'Survey Title',
      description: 'Description',
      jobs: {},
      acl: {},
      ownerId: 'owner1',
      dataSharingTerms: {},
    } as Survey;

    fixture.componentRef.setInput('activeSurvey', mockSurvey);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
