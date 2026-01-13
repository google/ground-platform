/**
 * Copyright 2025 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Storage } from '@angular/fire/storage';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { List, Map } from 'immutable';
import { of } from 'rxjs';

import { AuditInfo } from 'app/models/audit-info.model';
import { Job } from 'app/models/job.model';
import { Submission } from 'app/models/submission/submission.model';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { Task, TaskType } from 'app/models/task/task.model';
import { GroundIconModule } from 'app/modules/ground-icon.module';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SubmissionService } from 'app/services/submission/submission.service';

import { Coordinate } from 'app/models/geometry/coordinate';
import { Point } from 'app/models/geometry/point';
import { Result } from 'app/models/submission/result.model';
import { SubmissionPanelComponent } from './submission-panel.component';

describe('SubmissionPanelComponent', () => {
  let component: SubmissionPanelComponent;
  let fixture: ComponentFixture<SubmissionPanelComponent>;
  let submissionService: jasmine.SpyObj<SubmissionService>;
  let navigationService: jasmine.SpyObj<NavigationService>;
  let storageSpy: jasmine.SpyObj<Storage>;

  const mockSurvey = new Survey(
    'survey1',
    'Survey Title',
    'Description',
    Map(),
    Map(),
    'owner1',
    { type: DataSharingType.PRIVATE }
  );

  const mockUser = {
    id: 'user001',
    email: 'email@gmail.com',
    displayName: 'User 1',
    isAuthenticated: true,
  };

  const mockAuditInfo = new AuditInfo(mockUser, new Date(), new Date());

  const job1 = new Job('job1', 0, undefined, 'Job 1');

  const mockSubmission = new Submission(
    'sub1',
    'loi1',
    { id: 'job1', getTasksSorted: () => List<Task>() } as unknown as Job,
    mockAuditInfo,
    mockAuditInfo,
    Map()
  );

  beforeEach(async () => {
    submissionService = jasmine.createSpyObj('SubmissionService', [
      'getSubmission$',
    ]);
    navigationService = jasmine.createSpyObj('NavigationService', [
      'getTaskId$',
      'getLocationOfInterestId$',
      'selectLocationOfInterest',
      'showSubmissionDetailWithHighlightedTask',
    ]);
    storageSpy = jasmine.createSpyObj('Storage', ['ref']);

    submissionService.getSubmission$.and.returnValue(of(mockSubmission));
    navigationService.getLocationOfInterestId$.and.returnValue(
      of(mockSubmission.loiId)
    );
    navigationService.getTaskId$.and.returnValue(of(null));

    await TestBed.configureTestingModule({
      declarations: [SubmissionPanelComponent],
      imports: [
        MatButtonModule,
        MatIconModule,
        MatListModule,
        MatMenuModule,
        BrowserAnimationsModule,
        GroundIconModule,
      ],
      providers: [
        { provide: NavigationService, useValue: navigationService },
        { provide: SubmissionService, useValue: submissionService },
        { provide: Storage, useValue: storageSpy },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SubmissionPanelComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('activeSurvey', mockSurvey);
    fixture.componentRef.setInput('submissionId', mockSubmission.id);
    fixture.componentRef.setInput('lois', List());
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should format capture location coordinates', () => {
    const task = new Task(
      'task1',
      TaskType.CAPTURE_LOCATION,
      'Capture Location',
      true,
      1
    );
    const point = new Point(new Coordinate(10, 20), 10, 100);
    component.submission = new Submission(
      'sub1',
      'loi1',
      job1,
      mockAuditInfo,
      mockAuditInfo,
      Map({
        task1: new Result(point),
      })
    );

    const result = component.getCaptureLocationCoord(task);
    expect(result).toContain('20° N, 10° E');
    expect(result).toContain('Altitude: 100m');
    expect(result).toContain('Accuracy: 10m');
  });

  it('should format date', () => {
    const task = new Task('task1', TaskType.DATE, 'Date', true, 1);
    const date = new Date('2023-01-01T12:00:00');
    component.submission = new Submission(
      'sub1',
      'loi1',
      job1,
      mockAuditInfo,
      mockAuditInfo,
      Map({
        task1: new Result(date),
      })
    );

    const result = component.getDate(task);
    expect(result).toBe(date.toLocaleDateString());
  });

  it('should format time', () => {
    const task = new Task('task1', TaskType.TIME, 'Time', true, 1);
    const date = new Date('2023-01-01T12:00:00');
    component.submission = new Submission(
      'sub1',
      'loi1',
      job1,
      mockAuditInfo,
      mockAuditInfo,
      Map({
        task1: new Result(date),
      })
    );

    const result = component.getTime(task);
    expect(result).toBe(
      date.toLocaleTimeString([], { hour: 'numeric', minute: 'numeric' })
    );
  });

  it('should select geometry', () => {
    const task = new Task('task1', TaskType.DRAW_AREA, 'Draw Area', true, 1);
    component.submission = mockSubmission;
    component.selectGeometry(task);

    expect(
      navigationService.showSubmissionDetailWithHighlightedTask
    ).toHaveBeenCalledWith(
      mockSurvey.id,
      mockSubmission.loiId,
      mockSubmission.id,
      task.id
    );
  });
});
