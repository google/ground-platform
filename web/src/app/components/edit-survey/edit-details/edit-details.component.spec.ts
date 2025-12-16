/**
 * Copyright 2025 The Ground Authors.
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

import {CommonModule} from '@angular/common';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  flushMicrotasks,
} from '@angular/core/testing';
import {MatCardModule} from '@angular/material/card';
import {
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {By} from '@angular/platform-browser';
import {Map} from 'immutable';
import {of} from 'rxjs';

import {EditDetailsComponent} from 'app/components/edit-survey/edit-details/edit-details.component';
import {Job} from 'app/models/job.model';
import {Role} from 'app/models/role.model';
import {DataSharingType, Survey} from 'app/models/survey.model';
import {DraftSurveyService} from 'app/services/draft-survey/draft-survey.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';

import {
  DialogData,
  DialogType,
  JobDialogComponent,
} from '../job-dialog/job-dialog.component';

describe('EditDetailsComponent', () => {
  let component: EditDetailsComponent;
  let fixture: ComponentFixture<EditDetailsComponent>;
  const survey = new Survey(
    '123',
    'title',
    'description',
    Map<string, Job>(),
    Map<string, Role>(),
    '',
    {type: DataSharingType.PRIVATE}
  );
  const newSurveyId = 'newSurveyId';
  let dialogRefSpy: jasmine.SpyObj<
    MatDialogRef<JobDialogComponent, DialogData>
  >;
  let dialogSpy: jasmine.SpyObj<MatDialog>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;

  beforeEach(async () => {
    dialogRefSpy = jasmine.createSpyObj<
      MatDialogRef<JobDialogComponent, DialogData>
    >('MatDialogRef', ['afterClosed', 'close']);
    dialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    dialogSpy.open.and.returnValue(dialogRefSpy);

    navigationServiceSpy = jasmine.createSpyObj('NavigationService', [
      'navigateToSurveyDashboard',
    ]);

    surveyServiceSpy = jasmine.createSpyObj<SurveyService>('SurveyService', [
      'copySurvey',
    ]);
    surveyServiceSpy.copySurvey.and.returnValue(Promise.resolve(newSurveyId));

    await TestBed.configureTestingModule({
      declarations: [EditDetailsComponent],
      imports: [CommonModule, MatDialogModule, MatCardModule, MatIconModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        {provide: MatDialog, useValue: dialogSpy},
        {
          provide: DraftSurveyService,
          useValue: {getSurvey$: () => of(survey)},
        },
        {
          provide: NavigationService,
          useValue: navigationServiceSpy,
        },
        {provide: SurveyService, useValue: surveyServiceSpy},
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    dialogRefSpy.afterClosed.and.returnValue(
      of({dialogType: DialogType.CopySurvey} as DialogData)
    );

    fixture = TestBed.createComponent(EditDetailsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should copy the survey', fakeAsync(() => {
    const copyButton = fixture.debugElement.query(By.css('#copy-survey-button'))
      .nativeElement as HTMLElement;

    copyButton.click();

    expect(surveyServiceSpy.copySurvey).toHaveBeenCalledWith(survey.id);

    flushMicrotasks();

    expect(navigationServiceSpy.navigateToSurveyDashboard).toHaveBeenCalledWith(
      newSurveyId
    );
  }));
});
