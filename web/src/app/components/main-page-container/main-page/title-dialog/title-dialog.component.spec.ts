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

import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

import { NavigationService } from 'app/services/navigation/navigation.service';
import { SurveyService } from 'app/services/survey/survey.service';

import { TitleDialogComponent } from './title-dialog.component';

describe('TitleDialogComponent', () => {
  let component: TitleDialogComponent;
  let fixture: ComponentFixture<TitleDialogComponent>;
  const dialogRefSpy: Partial<MatDialogRef<TitleDialogComponent>> = {};
  const surveyService = jasmine.createSpyObj('SurveyService', ['createSurvey']);
  const navigationService = jasmine.createSpyObj('NavigationService', [
    'selectSurvey',
  ]);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [TitleDialogComponent],
      imports: [MatDialogModule],
      providers: [
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: { title: 'Title' } },
        { provide: SurveyService, useValue: surveyService },
        { provide: NavigationService, useValue: navigationService },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(TitleDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
