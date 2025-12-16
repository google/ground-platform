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

import { NO_ERRORS_SCHEMA, signal } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatRadioModule } from '@angular/material/radio';
import { By } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { List, Map } from 'immutable';
import { NEVER, of } from 'rxjs';

import { JobListItemModule } from 'app/components/shared/job-list-item/job-list-item.module';
import { AuditInfo } from 'app/models/audit-info.model';
import { Coordinate } from 'app/models/geometry/coordinate';
import { Point } from 'app/models/geometry/point';
import { Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { MultipleSelection } from 'app/models/submission/multiple-selection';
import { Result } from 'app/models/submission/result.model';
import { Submission } from 'app/models/submission/submission.model';
import { DataSharingType, Survey } from 'app/models/survey.model';
import {
  Cardinality,
  MultipleChoice,
} from 'app/models/task/multiple-choice.model';
import { Option } from 'app/models/task/option.model';
import { Task, TaskType } from 'app/models/task/task.model';
import { AuthService } from 'app/services/auth/auth.service';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { UrlParams } from 'app/services/navigation/url-params';
import { SubmissionService } from 'app/services/submission/submission.service';
import { SurveyService } from 'app/services/survey/survey.service';

import { SubmissionFormComponent } from './submission-form.component';

class MockModel {
  static task001: Task = new Task(
    'task001',
    TaskType.TEXT,
    'Text Field',
    /*required=*/ true,
    0
  );

  static task002: Task = new Task(
    'task002',
    TaskType.TEXT,
    'Text Field',
    /*required=*/ false,
    0
  );

  static option001 = new Option('option001', 'code001', 'option 1', 1);

  static option002 = new Option('option002', 'code002', 'option 2', 2);

  static task003: Task = new Task(
    'task003',
    TaskType.MULTIPLE_CHOICE,
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
      task001: MockModel.task001,
      task002: MockModel.task002,
      task003: MockModel.task003,
    })
  );

  static survey001 = new Survey(
    'survey001',
    'title',
    'description',
    Map({ job001: MockModel.job001 }),
    /*acl=*/ Map({}),
    /* ownerId= */ '',
    { type: DataSharingType.PRIVATE }
  );

  static loi001 = new LocationOfInterest(
    'loi001',
    MockModel.job001.id,
    new Point(new Coordinate(0.0, 0.0)),
    Map()
  );

  static user001 = {
    id: 'user001',
    email: 'email@gmail.com',
    isAuthenticated: false,
  };

  static submission001 = new Submission(
    'submission001',
    MockModel.loi001.id,
    MockModel.job001,
    new AuditInfo(MockModel.user001, new Date(), new Date()),
    new AuditInfo(MockModel.user001, new Date(), new Date()),
    Map({
      task001: new Result('result'),
      task003: new Result(
        new MultipleSelection(List([MockModel.option001.id]))
      ),
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
  getLocationsOfInterest$() {
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

  beforeEach(waitForAsync(() => {
    const navigationService = {
      getSurveyId$: () => of(''),
      getUrlParams: () => signal(new UrlParams(null, null, null, null)),
      getLocationOfInterestId$: () => NEVER,
      getSidePanelExpanded: () => false,
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
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SubmissionFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should create text tasks with right "required" option', () => {
    for (const el of fixture.debugElement.queryAll(
      By.css('.task-result div mat-form-field input')
    )) {
      if (!component.submissionTasks) {
        break;
      }
      const indexEl = component.submissionTasks.findIndex(
        task => task.id === el.nativeElement.id
      );

      expect(indexEl).toBeGreaterThanOrEqual(0);

      const want = component.submissionTasks.get(indexEl)?.required;

      const got = el.nativeElement.required as boolean | undefined;

      expect(want).toBe(got);
    }
  });

  it('should create radio button tasks with right "asterix" class', () => {
    for (const el of fixture.debugElement.queryAll(
      By.css('.task-result .multiple-choice-task mat-label')
    )) {
      if (!component.submissionTasks) {
        break;
      }
      const indexEl = component.submissionTasks.findIndex(
        task => task.id === el.nativeElement.id
      );

      expect(indexEl).toBeGreaterThanOrEqual(0);

      const want = component.submissionTasks.get(indexEl)?.required;

      const got = el.classes['asterix--after'] as boolean | undefined;

      expect(want).toBe(got);
    }
  });
});

function createRouterSpy() {
  return jasmine.createSpyObj('Router', ['navigate']);
}
