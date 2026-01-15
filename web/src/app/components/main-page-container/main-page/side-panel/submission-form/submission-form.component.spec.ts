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
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Firestore } from '@angular/fire/firestore';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatRadioModule } from '@angular/material/radio';
import { By } from '@angular/platform-browser';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { List, Map } from 'immutable';
import { of } from 'rxjs';

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
  getLocationsOfInterest$() {
    return of(List([MockModel.loi001]));
  }
}

class MockSubmissionService {
  getSubmission$() {
    return of<Submission>(MockModel.submission001);
  }
}

const surveyService = new MockSurveyService();
const loiService = new MockLocationOfInterestService();
const submissionService = new MockSubmissionService();
const dataStoreServiceSpy = jasmine.createSpyObj('DataStoreService', [
  'getServerTimestamp',
  'updateSubmission',
]);
dataStoreServiceSpy.getServerTimestamp.and.returnValue(new Date());
dataStoreServiceSpy.updateSubmission.and.returnValue(Promise.resolve());

const authServiceSpy = jasmine.createSpyObj('AuthService', ['getUser$']);
authServiceSpy.getUser$.and.returnValue(of(MockModel.user001));

// Stub for Firestore if needed, but likely unused in component if DataStore is mocked
const firestoreStub = {};

describe('SubmissionFormComponent', () => {
  let component: SubmissionFormComponent;
  let fixture: ComponentFixture<SubmissionFormComponent>;

  beforeEach(async () => {
    const navigationService = {
      getSurveyId$: () => of(''),
      getUrlParams: () => signal(new UrlParams(null, null, null, null)),
      getLocationOfInterestId$: () => of(MockModel.loi001.id),
      getSubmissionId$: () => of(MockModel.submission001.id),
      getSidePanelExpanded: () => false,
      clearSubmissionId: () => {},
    };
    TestBed.configureTestingModule({
      declarations: [SubmissionFormComponent],
      imports: [
        FormsModule,
        ReactiveFormsModule,
        NoopAnimationsModule,
        MatButtonModule,
        MatCheckboxModule,
        MatFormFieldModule,
        MatInputModule,
        MatListModule,
        MatRadioModule,
      ],
      providers: [
        { provide: SurveyService, useValue: surveyService },
        { provide: LocationOfInterestService, useValue: loiService },
        { provide: SubmissionService, useValue: submissionService },
        { provide: NavigationService, useValue: navigationService },
        { provide: DataStoreService, useValue: dataStoreServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Firestore, useValue: firestoreStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });

    await TestBed.compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SubmissionFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize form with submission values', () => {
    expect(component.submissionForm).toBeDefined();
    expect(component.submissionForm?.get('task001')?.value).toBe('result');
    // task003 is select multiple, so it uses option IDs as keys
    expect(component.submissionForm?.get('option001')?.value).toBe(true);
  });

  it('should save submission when valid', async () => {
    spyOn(component as any, 'navigateToLocationOfInterest');
    component.submissionForm?.get('task001')?.setValue('new value');

    component.onSave();

    expect(dataStoreServiceSpy.updateSubmission).toHaveBeenCalled();
    const args = dataStoreServiceSpy.updateSubmission.calls.mostRecent().args;
    expect(args[1].data.get('task001').value).toBe('new value');
  });

  it('should navigate away on cancel', () => {
    const navSpy = TestBed.inject(NavigationService) as unknown as {
      clearSubmissionId: jasmine.Spy;
    };
    spyOn(navSpy, 'clearSubmissionId');

    component.onCancel();

    expect(navSpy.clearSubmissionId).toHaveBeenCalled();
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
