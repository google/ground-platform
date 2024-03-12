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

import {CommonModule} from '@angular/common';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  waitForAsync,
} from '@angular/core/testing';
import {AngularFireAuth} from '@angular/fire/compat/auth';
import {AngularFirestore} from '@angular/fire/compat/firestore';
import {MatDialog} from '@angular/material/dialog';
import {List, Map} from 'immutable';
import {BehaviorSubject, of} from 'rxjs';

import {LoiSelectionModule} from 'app/components/loi-selection/loi-selection.module';
import {LocationOfInterest} from 'app/models/loi.model';
import {Survey} from 'app/models/survey.model';
import {AuthService} from 'app/services/auth/auth.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';

import {SurveyLoiComponent} from './survey-loi.component';

describe('SurveyLoiComponent', () => {
  let fixture: ComponentFixture<SurveyLoiComponent>;
  let component: SurveyLoiComponent;

  let loiServiceSpy: jasmine.SpyObj<LocationOfInterestService>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;

  const mockLois$ = new BehaviorSubject<List<LocationOfInterest>>(
    List<LocationOfInterest>([
      {id: 'id1', jobId: 'jobId1'} as LocationOfInterest,
    ])
  );

  const mockSurvey = new Survey('id1', 'title1', 'description1', Map(), Map());

  const mockSurvey$ = of(mockSurvey);

  beforeEach(waitForAsync(() => {
    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      ['getLocationOfInterestId$', 'getSubmissionId$', 'getSidePanelExpanded']
    );

    surveyServiceSpy = jasmine.createSpyObj<SurveyService>('SurveyService', [
      'getActiveSurvey',
      'getActiveSurvey$',
      'canManageSurvey',
    ]);

    navigationServiceSpy.getSubmissionId$.and.returnValue(
      of<string | null>(null)
    );

    surveyServiceSpy.canManageSurvey.and.returnValue(true);
    surveyServiceSpy.getActiveSurvey.and.returnValue(mockSurvey);
    surveyServiceSpy.getActiveSurvey$.and.returnValue(mockSurvey$);

    TestBed.configureTestingModule({
      declarations: [SurveyLoiComponent],
      imports: [LoiSelectionModule, CommonModule],
      providers: [
        {provide: AngularFirestore, useValue: {}},
        {provide: AngularFireAuth, useValue: {}},
        {provide: AuthService, useValue: {}},
        {provide: LocationOfInterestService, useValue: loiServiceSpy},
        {provide: NavigationService, useValue: navigationServiceSpy},
        {provide: SurveyService, useValue: surveyServiceSpy},
        {provide: MatDialog, useValue: {}},
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SurveyLoiComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });
});
