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
import {EditSurveyComponent} from 'app/pages/edit-survey/edit-survey.component';
import {SurveyDetailsComponent} from 'app/pages/create-survey/survey-details/survey-details.component';
import {JobDetailsComponent} from 'app/pages/create-survey/job-details/job-details.component';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {Subject} from 'rxjs';
import {Survey} from 'app/models/survey.model';
import {Map} from 'immutable';
import {By} from '@angular/platform-browser';

describe('EditSurveyComponent', () => {
  let fixture: ComponentFixture<EditSurveyComponent>;
  let surveyId$: Subject<string | null>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let route: ActivatedRouteStub;
  let activeSurvey$: Subject<Survey>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;

  const surveyId = 'survey001';
  const survey = new Survey(
    surveyId,
    'title',
    'description',
    /* jobs= */ Map(),
    /* acl= */ Map()
  );
  beforeEach(waitForAsync(() => {
    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      ['init', 'getSurveyId$']
    );
    surveyId$ = new Subject<string | null>();
    navigationServiceSpy.getSurveyId$.and.returnValue(surveyId$);

    route = new ActivatedRouteStub();
    surveyServiceSpy = jasmine.createSpyObj<SurveyService>('SurveyService', [
      'activateSurvey',
      'getActiveSurvey$',
    ]);
    activeSurvey$ = new Subject<Survey>();
    surveyServiceSpy.getActiveSurvey$.and.returnValue(activeSurvey$);

    TestBed.configureTestingModule({
      declarations: [
        EditSurveyComponent,
        SurveyDetailsComponent,
        JobDetailsComponent,
      ],
      providers: [
        {provide: NavigationService, useValue: navigationServiceSpy},
        {provide: SurveyService, useValue: surveyServiceSpy},
        {provide: ActivatedRoute, useValue: route},
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(EditSurveyComponent);
    fixture.detectChanges();
  });

  describe('when routed in with survey ID', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      tick();
    }));

    it('activates current survey ID', () => {
      expect(surveyServiceSpy.activateSurvey).toHaveBeenCalledOnceWith(
        surveyId
      );
    });
  });

  describe('when survey activated', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      activeSurvey$.next(survey);
      tick();
      fixture.detectChanges();
    }));

    it('displays survey id in title', () => {
      const tempTitle = fixture.debugElement.query(By.css('#temp-title'))
        .nativeElement.innerText;
      expect(tempTitle).toContain(surveyId);
    });
  });
});
