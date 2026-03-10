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
import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import { Auth } from '@angular/fire/auth';
import { Firestore } from '@angular/fire/firestore';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { List, Map } from 'immutable';
import { BehaviorSubject, NEVER, of } from 'rxjs';

import { DataSharingType, Survey } from 'app/models/survey.model';
import { AuthService } from 'app/services/auth/auth.service';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { UrlParams } from 'app/services/navigation/url-params';
import { SubmissionService } from 'app/services/submission/submission.service';
import { SurveyService } from 'app/services/survey/survey.service';
import { JOB_ID_NEW } from 'app/services/navigation/navigation.constants';
import { ActivatedRouteStub } from 'testing/activated-route-stub';
import { LocationOfInterest } from 'app/models/loi.model';
import { Point } from 'app/models/geometry/point';
import { Coordinate } from 'app/models/geometry/coordinate';

import { MainPageComponent } from './main-page.component';
import { TitleDialogComponent } from './title-dialog/title-dialog.component';

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
  let dialogSpy: jasmine.SpyObj<MatDialog>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let loiServiceSpy: jasmine.SpyObj<LocationOfInterestService>;
  const surveyId$ = new BehaviorSubject<string>('');
  const isAuthenticated$ = new BehaviorSubject<boolean>(true);

  beforeEach(async () => {
    route = new ActivatedRouteStub();
    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);
    navigationServiceSpy = jasmine.createSpyObj('NavigationService', [
      'getSurveyId$',
      'getLocationOfInterestId$',
      'getSubmissionId$',
      'getUrlParams',
      'signIn',
    ]);
    authServiceSpy = jasmine.createSpyObj('AuthService', [
      'getUser$',
      'isAuthenticated$',
    ]);
    loiServiceSpy = jasmine.createSpyObj('LocationOfInterestService', [
      'getLocationsOfInterest$',
    ]);

    navigationServiceSpy.getSurveyId$.and.returnValue(surveyId$);
    navigationServiceSpy.getLocationOfInterestId$.and.returnValue(NEVER);
    navigationServiceSpy.getSubmissionId$.and.returnValue(NEVER);
    navigationServiceSpy.getUrlParams.and.returnValue(
      signal(new UrlParams('survey1', 'loi1', 'submission1', 'task1'))
    );

    authServiceSpy.getUser$.and.returnValue(NEVER);
    authServiceSpy.isAuthenticated$.and.returnValue(isAuthenticated$);

    loiServiceSpy.getLocationsOfInterest$.and.returnValue(of(List([])));

    await TestBed.configureTestingModule({
      declarations: [MainPageComponent, MapComponent, MatSideNavComponent],
      providers: [
        { provide: ActivatedRoute, useValue: route },
        { provide: MatDialog, useValue: dialogSpy },
        { provide: LocationOfInterestService, useValue: loiServiceSpy },
        {
          provide: SubmissionService,
          useValue: { getSubmissions$: () => NEVER },
        },
        { provide: SurveyService, useValue: {} },
        { provide: NavigationService, useValue: navigationServiceSpy },
        { provide: Firestore, useValue: {} },
        { provide: Auth, useValue: {} },
        { provide: Router, useValue: {} },
        { provide: AuthService, useValue: authServiceSpy },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(MainPageComponent);
    component = fixture.componentInstance;

    const mockSurvey = new Survey(
      'survey1',
      'Title',
      'Description',
      Map(),
      Map(),
      'owner1',
      { type: DataSharingType.PRIVATE }
    );
    fixture.componentRef.setInput('activeSurvey', mockSurvey);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should open title dialog when survey ID is NEW', () => {
    surveyId$.next(JOB_ID_NEW);
    expect(dialogSpy.open).toHaveBeenCalledWith(TitleDialogComponent, {
      width: '500px',
      disableClose: true,
    });
  });

  it('should redirect to sign in if not authenticated', () => {
    isAuthenticated$.next(false);
    expect(navigationServiceSpy.signIn).toHaveBeenCalled();
  });

  it('should update LOIs when active survey changes', fakeAsync(() => {
    const mockLois = List([
      new LocationOfInterest(
        'loi1',
        'job1',
        new Point(new Coordinate(0, 0)),
        Map(),
        '',
        true
      ),
    ]);
    loiServiceSpy.getLocationsOfInterest$.and.returnValue(of(mockLois));

    const newSurvey = new Survey(
      'survey2',
      'Title 2',
      'Description',
      Map(),
      Map(),
      'owner1',
      { type: DataSharingType.PRIVATE }
    );
    fixture.componentRef.setInput('activeSurvey', newSurvey);
    fixture.detectChanges();
    tick();

    expect(loiServiceSpy.getLocationsOfInterest$).toHaveBeenCalledWith(
      newSurvey
    );
    expect(component.lois()).toBe(mockLois);
  }));
});
