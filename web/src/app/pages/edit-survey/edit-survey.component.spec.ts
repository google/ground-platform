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
import {RouterTestingModule} from '@angular/router/testing';
import {Job} from 'app/models/job.model';
import {EditJobComponent} from 'app/pages/edit-survey/edit-job/edit-job.component';

describe('EditSurveyComponent', () => {
  let fixture: ComponentFixture<EditSurveyComponent>;
  let surveyId$: Subject<string | null>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let route: ActivatedRouteStub;
  let activeSurvey$: Subject<Survey>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;

  const surveyId = 'survey001';
  const jobId1 = 'job001';
  const jobName1 = 'Job Name 1';
  const jobId2 = 'job002';
  const jobName2 = 'Job Name 2';
  const job1 = new Job(
    jobId1,
    /* index */ -1,
    'red',
    jobName1,
    /* tasks= */ Map()
  );
  const job2 = new Job(
    jobId2,
    /* index */ -1,
    'blue',
    jobName2,
    /* tasks= */ Map()
  );
  const survey = new Survey(
    surveyId,
    'title',
    'description',
    /* jobs= */ Map([
      [jobId1, job1],
      [jobId2, job2],
    ]),
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

    const routes = [
      {path: `job/${jobId1}`, component: EditJobComponent},
      {path: `job/${jobId2}`, component: EditJobComponent},
      {path: 'survey', component: SurveyDetailsComponent},
    ];
    TestBed.configureTestingModule({
      imports: [RouterTestingModule.withRoutes(routes)],
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

    it('displays left menu items with router link', () => {
      const surveyButton = fixture.debugElement.query(By.css('#survey-button'))
        .nativeElement as Element;
      const shareButton = fixture.debugElement.query(By.css('#share-button'))
        .nativeElement as Element;
      const jobButton1 = fixture.debugElement.query(By.css('#job-0'))
        .nativeElement as Element;
      const jobButton2 = fixture.debugElement.query(By.css('#job-1'))
        .nativeElement as Element;

      expect(surveyButton.innerHTML).toContain('Survey');
      expect(surveyButton.getAttribute('ng-reflect-router-link')).toContain(
        './survey'
      );
      expect(shareButton.innerHTML).toContain('Sharing');
      expect(shareButton.getAttribute('ng-reflect-router-link')).toContain(
        './survey'
      );
      expect(jobButton1.innerHTML).toContain(jobName1);
      expect(jobButton1.getAttribute('ng-reflect-router-link')).toContain(
        `./job/${jobId1}`
      );
      expect(jobButton2.innerHTML).toContain(jobName2);
      expect(jobButton2.getAttribute('ng-reflect-router-link')).toContain(
        `./job/${jobId2}`
      );
    });
  });
});
