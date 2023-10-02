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

import {ActivatedRouteStub} from 'testing/activated-route-stub';
import {ActivatedRoute} from '@angular/router';
import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
  waitForAsync,
} from '@angular/core/testing';
import {SurveyPageContainerComponent} from './survey-page-container.component';
import {MainPageComponent} from '../../pages/main-page/main-page.component';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {NEVER, Subject} from 'rxjs';
import {SurveyService} from 'app/services/survey/survey.service';
import {Component, NO_ERRORS_SCHEMA} from '@angular/core';
import {By} from '@angular/platform-browser';
import {Survey} from 'app/models/survey.model';

@Component({
  template: ` <ground-survey-page-container>
    Page body
  </ground-survey-page-container>`,
})
class TestHostComponent {}

const navigationService = {
  init: () => {},
  getSurveyId$: () => NEVER,
};

const surveyService = jasmine.createSpyObj('SurveyService', [
  'getActiveSurvey$',
  'requireActiveSurvey$',
  'activateSurvey',
]);

describe('SurveyPageContainerComponent', () => {
  let component: TestHostComponent;
  let fixture: ComponentFixture<TestHostComponent>;
  let route: ActivatedRouteStub;
  const activeSurvey$ = new Subject<Survey | null>();

  beforeEach(waitForAsync(() => {
    surveyService.getActiveSurvey$.and.returnValue(activeSurvey$);
    route = new ActivatedRouteStub();
    TestBed.configureTestingModule({
      declarations: [
        SurveyPageContainerComponent,
        MainPageComponent,
        TestHostComponent,
      ],
      providers: [
        {provide: ActivatedRoute, useValue: route},
        {provide: NavigationService, useValue: navigationService},
        {provide: SurveyService, useValue: surveyService},
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows spinner while loading', fakeAsync(() => {
    activeSurvey$.next(null);
    fixture.detectChanges();
    tick();
    const spinner = fixture.debugElement.query(By.css('.loading-spinner'));
    expect(spinner).not.toBeNull();
  }));

  it('shows content when loaded', fakeAsync(() => {
    activeSurvey$.next(Survey.UNSAVED_NEW);
    fixture.detectChanges();
    tick();
    const div = fixture.debugElement.query(
      By.css('ground-survey-page-container')
    );
    expect(div.nativeElement.textContent.trim()).toEqual('Page body');
  }));
});
