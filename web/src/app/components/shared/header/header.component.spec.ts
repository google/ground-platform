/**
 * Copyright 2022 The Ground Authors.
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
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { Router } from '@angular/router';
import { Map } from 'immutable';
import { of } from 'rxjs';
import {
  DialogType,
  JobDialogComponent,
} from 'app/components/edit-survey/job-dialog/job-dialog.component';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { AuthService } from 'app/services/auth/auth.service';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SurveyService } from 'app/services/survey/survey.service';

import { HeaderComponent, HeaderState } from './header.component';

describe('HeaderComponent', () => {
  let component: HeaderComponent;
  let fixture: ComponentFixture<HeaderComponent>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;
  let draftSurveyServiceSpy: jasmine.SpyObj<DraftSurveyService>;
  let matDialogSpy: jasmine.SpyObj<MatDialog>;

  const mockSurvey = new Survey(
    '123',
    'title',
    'description',
    Map(),
    Map(),
    '',
    { type: DataSharingType.PRIVATE }
  );

  beforeEach(async () => {
    navigationServiceSpy = jasmine.createSpyObj('NavigationService', [
      'isEditSurveyPage',
      'isSurveyPage',
      'navigateToSurveyList',
      'navigateToEditSurvey',
      'navigateToAboutPage',
      'navigateToTermsOfService',
      'selectSurvey',
    ]);
    surveyServiceSpy = jasmine.createSpyObj('SurveyService', [
      'canManageSurvey',
    ]);
    draftSurveyServiceSpy = jasmine.createSpyObj('DraftSurveyService', [], {
      dirty: false,
    });
    matDialogSpy = jasmine.createSpyObj('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [MatMenuModule],
      declarations: [HeaderComponent],
      providers: [
        {
          provide: DataStoreService,
          useValue: { getAccessDeniedMessage: () => '' },
        },
        { provide: MatDialog, useValue: matDialogSpy },
        { provide: AuthService, useValue: { getUser$: () => of() } },
        { provide: DraftSurveyService, useValue: draftSurveyServiceSpy },
        { provide: Router, useValue: { events: of(), url: '' } },
        { provide: NavigationService, useValue: navigationServiceSpy },
        { provide: SurveyService, useValue: surveyServiceSpy },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
    component.survey = mockSurvey;
    component.ngOnChanges();
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('ngOnChanges', () => {
    it('should set state to MAP_VIEW when is survey page', () => {
      navigationServiceSpy.isSurveyPage.and.returnValue(true);
      component.ngOnChanges();
      expect(component.state).toBe(HeaderState.MAP_VIEW);
    });

    it('should set state to EDIT_SURVEY when is edit survey page', () => {
      navigationServiceSpy.isEditSurveyPage.and.returnValue(true);
      component.ngOnChanges();
      expect(component.state).toBe(HeaderState.EDIT_SURVEY);
    });

    it('should set state to DEFAULT when no survey', () => {
      component.survey = null;
      component.ngOnChanges();
      expect(component.state).toBe(HeaderState.DEFAULT);
    });

    it('should check canManageSurvey', () => {
      surveyServiceSpy.canManageSurvey.and.returnValue(true);
      component.ngOnChanges();
      expect(component.canManage).toBe(true);
    });
  });

  describe('onCancelEditSurveyClick', () => {
    it('should navigate to survey if not dirty', () => {
      (
        Object.getOwnPropertyDescriptor(draftSurveyServiceSpy, 'dirty')!
          .get as jasmine.Spy
      ).and.returnValue(false);

      component.onCancelEditSurveyClick();

      expect(navigationServiceSpy.selectSurvey).toHaveBeenCalledWith(
        mockSurvey.id
      );
    });

    // ... (in describe block)

    it('should open dialog if dirty', () => {
      (
        Object.getOwnPropertyDescriptor(draftSurveyServiceSpy, 'dirty')!
          .get as jasmine.Spy
      ).and.returnValue(true);
      matDialogSpy.open.and.returnValue({
        afterClosed: () => of({ dialogType: DialogType.UndoJobs }),
      } as MatDialogRef<unknown, unknown>);

      component.onCancelEditSurveyClick();

      expect(matDialogSpy.open).toHaveBeenCalledWith(JobDialogComponent, {
        data: {
          dialogType: DialogType.UndoJobs,
        },
        panelClass: 'small-width-dialog',
      });
      expect(navigationServiceSpy.selectSurvey).toHaveBeenCalledWith(
        mockSurvey.id
      );
    });

    it('should not navigate if dialog cancelled', () => {
      (
        Object.getOwnPropertyDescriptor(draftSurveyServiceSpy, 'dirty')!
          .get as jasmine.Spy
      ).and.returnValue(true);
      matDialogSpy.open.and.returnValue({
        afterClosed: () => of(null),
      } as MatDialogRef<unknown, unknown>);

      component.onCancelEditSurveyClick();

      expect(navigationServiceSpy.selectSurvey).not.toHaveBeenCalled();
    });
  });
});
