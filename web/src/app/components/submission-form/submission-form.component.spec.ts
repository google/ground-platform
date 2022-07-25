/**
 * Copyright 2020 Google LLC
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

import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { DataStoreService } from './../../services/data-store/data-store.service';
import { SubmissionFormComponent } from './submission-form.component';
import {
  LocationOfInterest,
  PointOfInterest,
} from '../../shared/models/loi.model';
import { NEVER, of } from 'rxjs';
import { Survey } from '../../shared/models/survey.model';
import { List, Map } from 'immutable';
import { Submission } from '../../shared/models/submission/submission.model';
import { Result } from '../../shared/models/submission/result.model';
import firebase from 'firebase/app';
import { Job } from '../../shared/models/job.model';
import { Option } from '../../shared/models/task/option.model';
import {
  MultipleChoice,
  Cardinality,
} from '../../shared/models/task/multiple-choice.model';
import { StepType, Step } from '../../shared/models/task/step.model';
import { AuditInfo } from '../../shared/models/audit-info.model';
import { LocationOfInterestService } from '../../services/loi/loi.service';
import { SurveyService } from '../../services/survey/survey.service';
import { SubmissionService } from '../../services/submission/submission.service';
import { Router } from '@angular/router';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatRadioModule } from '@angular/material/radio';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { JobListItemModule } from '../job-list-item/job-list-item.module';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { AuthService } from '../../services/auth/auth.service';
import { NavigationService } from '../../services/navigation/navigation.service';
import { By } from '@angular/platform-browser';

class MockModel {
  static step001: Step = new Step(
    'step001',
    StepType.TEXT,
    'Text Field',
    /*required=*/ true,
    0
  );

  static step002: Step = new Step(
    'step002',
    StepType.TEXT,
    'Text Field',
    /*required=*/ false,
    0
  );

  static option001 = new Option('option001', 'code001', 'option 1', 1);

  static option002 = new Option('option002', 'code002', 'option 2', 2);

  static step003: Step = new Step(
    'step003',
    StepType.MULTIPLE_CHOICE,
    'Multiple Select',
    /*required=*/ true,
    0,
    new MultipleChoice(
      Cardinality.SELECT_MULTIPLE,
      List([MockModel.option001, MockModel.option002])
    )
  );

  static job001 = new Job(
    'job001',
    1,
    'red',
    'name',
    Map({
      step001: MockModel.step001,
      step002: MockModel.step002,
      step003: MockModel.step003,
    })
  );

  static survey001 = new Survey(
    'survey001',
    'title',
    'description',
    Map({ job001: MockModel.job001 }),
    /*acl=*/ Map({})
  );

  static loi001 = new PointOfInterest(
    'loi001',
    MockModel.job001.id,
    new firebase.firestore.GeoPoint(0.0, 0.0)
  );

  static user001 = {
    id: 'user001',
    email: 'email@gmail.com',
    isAuthenticated: false,
  };

  static submission001 = new Submission(
    'submission001',
    MockModel.loi001.id,
    MockModel.loi001.jobId,
    MockModel.form001,
    new AuditInfo(MockModel.user001, new Date(), new Date()),
    new AuditInfo(MockModel.user001, new Date(), new Date()),
    Map({
      step001: new Result('result'),
      step003: new Result(List([MockModel.option001])),
    })
  );
}

class MockSurveyService {
  getActiveSurvey$() {
    return of<Survey>(MockModel.survey001);
  }
  getSurveyAcl() {}
  getCurrentSurvey() {}
}

class MockLocationOfInterestService {
  getSelectedLocationOfInterest$() {
    return of<LocationOfInterest>(MockModel.loi001);
  }
}

class MockSubmissionService {
  getSelectedSubmission$() {
    return of<Submission>(MockModel.submission001);
  }
}

const surveyService = new MockSurveyService();
const loiService = new MockLocationOfInterestService();
const submissionService = new MockSubmissionService();

describe('SubmissionFormComponent', () => {
  let component: SubmissionFormComponent;
  let fixture: ComponentFixture<SubmissionFormComponent>;

  beforeEach(
    waitForAsync(() => {
      const navigationService = {
        getSurveyId$: () => of(''),
        getLocationOfInterestId$: () => NEVER,
      };
      const routerSpy = createRouterSpy();
      TestBed.configureTestingModule({
        declarations: [SubmissionFormComponent],
        imports: [
          BrowserAnimationsModule,
          FormsModule,
          ReactiveFormsModule,
          MatFormFieldModule,
          MatButtonModule,
          MatFormFieldModule,
          MatInputModule,
          MatRadioModule,
          MatCheckboxModule,
          MatIconModule,
          MatListModule,
          JobListItemModule,
        ],
        providers: [
          { provide: DataStoreService, useValue: {} },
          {
            provide: LocationOfInterestService,
            useValue: loiService,
          },
          { provide: SurveyService, useValue: surveyService },
          { provide: SubmissionService, useValue: submissionService },
          { provide: Router, useValue: routerSpy },
          { provide: NavigationService, useValue: navigationService },
          { provide: AuthService, useValue: { getUser$: () => NEVER } },
        ],
        schemas: [NO_ERRORS_SCHEMA],
      }).compileComponents();
    })
  );

  beforeEach(() => {
    fixture = TestBed.createComponent(SubmissionFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should create text steps with right "required" option', () => {
    for (const el of fixture.debugElement.queryAll(
      By.css('.step-result div mat-form-field input')
    )) {
      if (!component.submissionSteps) {
        break;
      }
      const indexEl = component.submissionSteps.findIndex(
        step => step.id === el.nativeElement.id
      );

      expect(indexEl).toBeGreaterThanOrEqual(0);

      const want = component.submissionSteps.get(indexEl)?.required;

      const got = el.nativeElement.required as boolean | undefined;

      expect(want).toBe(got);
    }
  });

  it('should create radio button steps with right "asterix" class', () => {
    for (const el of fixture.debugElement.queryAll(
      By.css('.step-result .multiple-choice-step mat-label')
    )) {
      if (!component.submissionSteps) {
        break;
      }
      const indexEl = component.submissionSteps.findIndex(
        step => step.id === el.nativeElement.id
      );

      expect(indexEl).toBeGreaterThanOrEqual(0);

      const want = component.submissionSteps.get(indexEl)?.required;

      const got = el.classes['asterix--after'] as boolean | undefined;

      expect(want).toBe(got);
    }
  });
});

function createRouterSpy() {
  return jasmine.createSpyObj('Router', ['navigate']);
}
