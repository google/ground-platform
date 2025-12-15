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

import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { List, Map } from 'immutable';
import { of } from 'rxjs';

import { LocationOfInterest } from 'app/models/loi.model';
import { Submission } from 'app/models/submission/submission.model';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SubmissionService } from 'app/services/submission/submission.service';

import { LocationOfInterestPanelComponent } from './loi-panel.component';

describe('LocationOfInterestPanelComponent', () => {
  let component: LocationOfInterestPanelComponent;
  let fixture: ComponentFixture<LocationOfInterestPanelComponent>;
  let loiServiceSpy: jasmine.SpyObj<LocationOfInterestService>;
  let submissionServiceSpy: jasmine.SpyObj<SubmissionService>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  const mockSurvey = new Survey(
    'survey1',
    'Survey Title',
    'Description',
    Map(),
    Map(),
    'owner1',
    { type: DataSharingType.PRIVATE }
  );

  const mockLoi = new LocationOfInterest(
    'loi1',
    'job1',
    { chainId: 'point1' } as any,
    Map()
  );

  beforeEach(waitForAsync(() => {
    loiServiceSpy = jasmine.createSpyObj('LocationOfInterestService', [
      'getSelectedLocationOfInterest$',
    ]);
    submissionServiceSpy = jasmine.createSpyObj('SubmissionService', [
      'getSubmissions$',
    ]);
    navigationServiceSpy = jasmine.createSpyObj('NavigationService', [
      'showSubmissionDetail',
      'clearLocationOfInterestId',
    ]);
    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);

    loiServiceSpy.getSelectedLocationOfInterest$.and.returnValue(of(mockLoi));
    submissionServiceSpy.getSubmissions$.and.returnValue(
      of(List<Submission>())
    );

    TestBed.configureTestingModule({
      declarations: [LocationOfInterestPanelComponent],
      providers: [
        { provide: LocationOfInterestService, useValue: loiServiceSpy },
        { provide: SubmissionService, useValue: submissionServiceSpy },
        { provide: NavigationService, useValue: navigationServiceSpy },
        { provide: MatDialog, useValue: dialogSpy },
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(LocationOfInterestPanelComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('activeSurvey', mockSurvey);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should navigate to submission detail on selection', () => {
    component.loi = mockLoi;
    const submissionId = 'sub1';
    component.onSelectSubmission(submissionId);

    expect(navigationServiceSpy.showSubmissionDetail).toHaveBeenCalledWith(
      mockSurvey.id,
      mockLoi.id,
      submissionId
    );
  });

  it('should clear LOI on close', () => {
    component.onClosePanel();
    expect(navigationServiceSpy.clearLocationOfInterestId).toHaveBeenCalled();
  });
});
