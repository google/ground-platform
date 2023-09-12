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
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {Subject} from 'rxjs';
import {Survey} from 'app/models/survey.model';
import {Map} from 'immutable';
import {By} from '@angular/platform-browser';
import {RouterTestingModule} from '@angular/router/testing';
import {Job} from 'app/models/job.model';

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

    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      declarations: [EditSurveyComponent],
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

  it('shows spinner when survey not loaded', () => {
    const spinner = fixture.debugElement.query(By.css('#loading-spinner'))
      .nativeElement as HTMLElement;
    // TODO(daoyu): replace it with a spinner component
    expect(spinner.innerHTML).toEqual('Nothing');
  });

  describe('when routed in with survey ID', () => {
    beforeEach(fakeAsync(() => {
      surveyId$.next(surveyId);
      tick();
    }));

    it('activates survey ID', () => {
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

    describe('menu item tests', () => {
      [
        {
          buttonSelector: '#survey-button',
          expectedLabel: 'Survey',
          expectedRouterLink: './survey',
        },
        {
          buttonSelector: '#share-button',
          expectedLabel: 'Sharing',
          expectedRouterLink: './share',
        },
        {
          buttonSelector: '#job-0',
          expectedLabel: jobName1,
          expectedRouterLink: `./job/${jobId1}`,
        },
        {
          buttonSelector: '#job-1',
          expectedLabel: jobName2,
          expectedRouterLink: `./job/${jobId2}`,
        },
      ].forEach(({buttonSelector, expectedLabel, expectedRouterLink}) => {
        it('displays button with correct label and router link', () => {
          const button = fixture.debugElement.query(By.css(buttonSelector))
            .nativeElement as HTMLElement;

          expect(button.textContent).toContain(expectedLabel);
          expect(button.getAttribute('ng-reflect-router-link')).toEqual(
            expectedRouterLink
          );
        });
      });
    });
  });
});
