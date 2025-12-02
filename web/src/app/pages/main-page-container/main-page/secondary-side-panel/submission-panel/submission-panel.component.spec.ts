/**
 * Copyright 2023 The Ground Authors.
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

import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { AngularFireStorage } from '@angular/fire/compat/storage';
import { Map } from 'immutable';
import { of } from 'rxjs';

import { AuditInfo } from 'app/models/audit-info.model';
import { Submission } from 'app/models/submission/submission.model';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SubmissionService } from 'app/services/submission/submission.service';

import { SubmissionPanelComponent } from './submission-panel.component';

describe('SubmissionPanelComponent', () => {
  let component: SubmissionPanelComponent;
  let fixture: ComponentFixture<SubmissionPanelComponent>;
  let submissionServiceSpy: jasmine.SpyObj<SubmissionService>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let storageSpy: jasmine.SpyObj<AngularFireStorage>;

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

  const mockSubmission = new Submission(
    'sub1',
    'loi1',
    { id: 'job1' } as any,
    mockAuditInfo,
    mockAuditInfo,
    Map()
  );

  beforeEach(waitForAsync(() => {
    submissionServiceSpy = jasmine.createSpyObj('SubmissionService', [
      'getSelectedSubmission$',
    ]);
    navigationServiceSpy = jasmine.createSpyObj('NavigationService', [
      'getTaskId$',
      'selectLocationOfInterest',
      'showSubmissionDetailWithHighlightedTask',
    ]);
    storageSpy = jasmine.createSpyObj('AngularFireStorage', ['ref']);

    submissionServiceSpy.getSelectedSubmission$.and.returnValue(
      of(mockSubmission)
    );
    navigationServiceSpy.getTaskId$.and.returnValue(of(null));

    TestBed.configureTestingModule({
      declarations: [SubmissionPanelComponent],
      providers: [
        { provide: SubmissionService, useValue: submissionServiceSpy },
        { provide: NavigationService, useValue: navigationServiceSpy },
        { provide: AngularFireStorage, useValue: storageSpy },
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SubmissionPanelComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('activeSurvey', mockSurvey);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should navigate back to submission list', () => {
    component.submission = mockSubmission;
    component.navigateToSubmissionList();

    expect(navigationServiceSpy.selectLocationOfInterest).toHaveBeenCalledWith(
      mockSurvey.id,
      mockSubmission.loiId
    );
  });
});
