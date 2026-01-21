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
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { List, Map } from 'immutable';
import { of } from 'rxjs';

import { Coordinate } from 'app/models/geometry/coordinate';

import { Point } from 'app/models/geometry/point';
import { Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { Submission } from 'app/models/submission/submission.model';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { GroundIconModule } from 'app/modules/ground-icon.module';
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
    Map([['job1', new Job('job1', 0, '#000')]]),
    Map(),
    'owner1',
    { type: DataSharingType.PRIVATE }
  );

  const mockLoi = new LocationOfInterest(
    'loi1',
    'job1',
    new Point(new Coordinate(0, 0)),
    Map()
  );

  beforeEach(async () => {
    loiServiceSpy = jasmine.createSpyObj<LocationOfInterestService>(
      'LocationOfInterestService',
      ['getLocationsOfInterest$']
    );
    submissionServiceSpy = jasmine.createSpyObj<SubmissionService>(
      'SubmissionService',
      ['getSubmissions$']
    );
    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      [
        'getSurveyId$',
        'getLocationOfInterestId$',
        'showSubmissionDetail',
        'clearLocationOfInterestId',
      ]
    );
    dialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    loiServiceSpy.getLocationsOfInterest$.and.returnValue(of(List([mockLoi])));

    submissionServiceSpy.getSubmissions$.and.returnValue(
      of(List<Submission>([]))
    );
    navigationServiceSpy.getSurveyId$.and.returnValue(of(mockSurvey.id));
    navigationServiceSpy.getLocationOfInterestId$.and.returnValue(
      of(mockLoi.id)
    );

    await TestBed.configureTestingModule({
      declarations: [LocationOfInterestPanelComponent],
      imports: [
        GroundIconModule,
        MatDialogModule,
        MatListModule,
        MatIconModule,
      ],
      providers: [
        { provide: LocationOfInterestService, useValue: loiServiceSpy },
        { provide: SubmissionService, useValue: submissionServiceSpy },
        { provide: NavigationService, useValue: navigationServiceSpy },
        { provide: MatDialog, useValue: dialogSpy },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(LocationOfInterestPanelComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('activeSurvey', mockSurvey);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize component state on init', () => {
    fixture.componentRef.setInput('lois', List([mockLoi]));
    fixture.detectChanges();

    expect(component.loi).toEqual(mockLoi);
    expect(component.submissions).toBeDefined();
    expect(component.isLoading).toBe(false);
    expect(component.iconColor).toBe('#000');
    expect(component.name).toBe('Unnamed point'); // Default name for mockLoi
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
