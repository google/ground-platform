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
import {ActivatedRoute} from '@angular/router';
import {NEVER} from 'rxjs';

import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {ActivatedRouteStub} from 'testing/activated-route-stub';

import {MainPageComponent} from './main-page/main-page.component';
import {MainPageContainerComponent} from './main-page-container.component';
import {UrlParams} from 'app/services/navigation/url-params';
import {signal} from '@angular/core';

describe('MainPageContainerComponent', () => {
  let component: MainPageContainerComponent;
  let fixture: ComponentFixture<MainPageContainerComponent>;
  let route: ActivatedRouteStub;

  const surveyService = jasmine.createSpyObj('SurveyService', [
    'getActiveSurvey$',
    'activateSurvey',
  ]);

  beforeEach(waitForAsync(() => {
    const navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      ['getUrlParams']
    );
    navigationServiceSpy.getUrlParams.and.returnValue(
      signal(new UrlParams(null, null, null, null))
    );
    route = new ActivatedRouteStub();
    TestBed.configureTestingModule({
      declarations: [MainPageContainerComponent, MainPageComponent],
      providers: [
        {provide: ActivatedRoute, useValue: route},
        {provide: NavigationService, useValue: navigationServiceSpy},
        {provide: SurveyService, useValue: surveyService},
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(MainPageContainerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
