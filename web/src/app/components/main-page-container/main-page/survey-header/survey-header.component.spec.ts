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

import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { Map } from 'immutable';
import { NEVER, of } from 'rxjs';

import { DataSharingType, Survey } from 'app/models/survey.model';
import { GroundIconModule } from 'app/modules/ground-icon.module';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SurveyService } from 'app/services/survey/survey.service';

import { SurveyHeaderComponent } from './survey-header.component';

describe('SurveyHeaderComponent', () => {
  let component: SurveyHeaderComponent;
  let fixture: ComponentFixture<SurveyHeaderComponent>;

  const mockSurvey = new Survey(
    'survey1',
    'Survey Title',
    'Description',
    Map(),
    Map(),
    'owner1',
    { type: DataSharingType.PRIVATE }
  );

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MatIconModule, MatDialogModule, GroundIconModule],
      declarations: [SurveyHeaderComponent],
      providers: [
        {
          provide: DataStoreService,
          useValue: { getAccessDeniedMessage: () => '' },
        },
        {
          provide: SurveyService,
          useValue: {
            getCurrentSurvey: () => {},
            canManageSurvey: () => {},
            updateTitle: () => Promise.resolve(),
          },
        },
        {
          provide: NavigationService,
          useValue: {
            navigateToSurveyList: () => {},
            isEditSurveyPage: () => false,
            onClickSidePanelButton: () => {},
          },
        },
        {
          provide: Router,
          useValue: {
            events: of(),
            isActive: () => true,
          },
        },
      ],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SurveyHeaderComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('activeSurvey', mockSurvey);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
