/**
 * Copyright 2023 Google LLC
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

import {ActivatedRoute} from '@angular/router';
import {ActivatedRouteStub} from 'testing/activated-route-stub';
import {
  ComponentFixture,
  TestBed,
  waitForAsync,
  tick,
  fakeAsync,
} from '@angular/core/testing';
import {CreateSurveyComponent} from 'app/pages/create-survey/create-survey.component';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {Subject} from 'rxjs';
import {Survey} from 'app/models/survey.model';
import {Map} from 'immutable';
import {By} from '@angular/platform-browser';

describe('CreateSurveyComponent', () => {
  let component: CreateSurveyComponent;
  let fixture: ComponentFixture<CreateSurveyComponent>;
  let surveyId$: Subject<string | null>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let route: ActivatedRouteStub;
  let activeSurvey$: Subject<Survey>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;

  const surveyId = 'survey001';
  const title = 'title1';
  const description = 'description1';
  const mockSurvey = new Survey(
    surveyId,
    title,
    description,
    /* jobs= */ Map(),
    /* acl= */ Map()
  );
  beforeEach(waitForAsync(() => {
    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      ['init', 'getSurveyId$', 'navigateToSurveyList', 'navigateToCreateSurvey']
    );
    surveyId$ = new Subject<string | null>();
    navigationServiceSpy.getSurveyId$.and.returnValue(surveyId$);

    route = new ActivatedRouteStub();
    surveyServiceSpy = jasmine.createSpyObj<SurveyService>('SurveyService', [
      'activateSurvey',
      'getActiveSurvey$',
      'updateTitleAndDescription',
      'createSurvey',
    ]);
    activeSurvey$ = new Subject<Survey>();
    surveyServiceSpy.getActiveSurvey$.and.returnValue(activeSurvey$);

    TestBed.configureTestingModule({
      declarations: [CreateSurveyComponent],
      providers: [
        {provide: NavigationService, useValue: navigationServiceSpy},
        {provide: SurveyService, useValue: surveyServiceSpy},
        {provide: ActivatedRoute, useValue: route},
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CreateSurveyComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('title and description inputs are empty when routed in without survey ID', fakeAsync(() => {
    surveyId$.next(null);
    tick();

    expect(component.formGroup.controls[component.titleControlKey].value).toBe(
      ''
    );
    expect(
      component.formGroup.controls[component.descriptionControlKey].value
    ).toBe('');
  }));

  it('title and description inputs are loaded when routed in with survey ID', fakeAsync(() => {
    surveyId$.next(surveyId);
    activeSurvey$.next(mockSurvey);
    tick();

    expect(component.formGroup.controls[component.titleControlKey].value).toBe(
      title
    );
    expect(
      component.formGroup.controls[component.descriptionControlKey].value
    ).toBe(description);
  }));

  it('creates a new survey when routed in without survey ID and continue button is clicked', fakeAsync(() => {
    surveyId$.next(null);
    tick();

    const newTitle = 'newTitle';
    const newDescription = 'newDescription';
    component.formGroup.controls[component.titleControlKey].setValue(newTitle);
    component.formGroup.controls[component.descriptionControlKey].setValue(
      newDescription
    );
    const continueButton = fixture.debugElement.query(
      By.css('#continue-button')
    ).nativeElement as HTMLElement;
    continueButton.click();

    expect(surveyServiceSpy.createSurvey).toHaveBeenCalledOnceWith(
      newTitle,
      newDescription
    );
  }));

  it('updates the existing survey when routed in with survey ID and continue button is clicked', fakeAsync(() => {
    surveyId$.next(surveyId);
    activeSurvey$.next(mockSurvey);
    tick();

    const newTitle = 'newTitle';
    const newDescription = 'newDescription';
    component.formGroup.controls[component.titleControlKey].setValue(newTitle);
    component.formGroup.controls[component.descriptionControlKey].setValue(
      newDescription
    );
    const continueButton = fixture.debugElement.query(
      By.css('#continue-button')
    ).nativeElement as HTMLElement;
    continueButton.click();

    expect(surveyServiceSpy.updateTitleAndDescription).toHaveBeenCalledOnceWith(
      surveyId,
      newTitle,
      newDescription
    );
  }));

  it('activates current survey ID when routed in with survey ID', fakeAsync(() => {
    surveyId$.next(surveyId);
    tick();

    expect(surveyServiceSpy.activateSurvey).toHaveBeenCalledOnceWith(surveyId);
  }));

  it('navigates to survey list page when back button is clicked', () => {
    const backButton = fixture.debugElement.query(By.css('#back-button'))
      .nativeElement as HTMLElement;
    backButton.click();

    expect(navigationServiceSpy.navigateToSurveyList).toHaveBeenCalled();
  });
});
