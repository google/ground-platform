/**
 * Copyright 2026 The Ground Authors.
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

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Map } from 'immutable';

import { DataSharingType, Survey, SurveyDataVisibility } from 'app/models/survey.model';
import { AuthService } from 'app/services/auth/auth.service';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';

import { DataVisibilityControlComponent } from './data-visibility-control.component';

describe('DataVisibilityControlComponent', () => {
  let component: DataVisibilityControlComponent;
  let fixture: ComponentFixture<DataVisibilityControlComponent>;

  let draftSurveyService: jasmine.SpyObj<DraftSurveyService>;

  const mockSurveyBase = new Survey(
    'survey1',
    'Test Survey',
    'A test survey',
    Map(),
    Map(),
    'owner1',
    { type: DataSharingType.PRIVATE }
  );

  beforeEach(async () => {
    draftSurveyService = jasmine.createSpyObj('DraftSurveyService', [
      'updateDataVisibility',
    ]);

    await TestBed.configureTestingModule({
      declarations: [DataVisibilityControlComponent],
      imports: [MatSlideToggleModule],
      providers: [
        { provide: AuthService, useValue: {} },
        { provide: DraftSurveyService, useValue: draftSurveyService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DataVisibilityControlComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('defaults to CONTRIBUTOR_AND_ORGANIZERS when survey has no dataVisibility', () => {
    fixture.componentRef.setInput('survey', mockSurveyBase);
    fixture.detectChanges();

    expect(component.selectedDataVisibility).toBe(
      SurveyDataVisibility.CONTRIBUTOR_AND_ORGANIZERS
    );
  });

  it('reflects ALL_SURVEY_PARTICIPANTS when survey has that visibility set', () => {
    const survey = mockSurveyBase.copyWith({
      dataVisibility: SurveyDataVisibility.ALL_SURVEY_PARTICIPANTS,
    });
    fixture.componentRef.setInput('survey', survey);
    fixture.detectChanges();

    expect(component.selectedDataVisibility).toBe(
      SurveyDataVisibility.ALL_SURVEY_PARTICIPANTS
    );
  });

  it('reflects CONTRIBUTOR_AND_ORGANIZERS when survey has that visibility set', () => {
    const survey = mockSurveyBase.copyWith({
      dataVisibility: SurveyDataVisibility.CONTRIBUTOR_AND_ORGANIZERS,
    });
    fixture.componentRef.setInput('survey', survey);
    fixture.detectChanges();

    expect(component.selectedDataVisibility).toBe(
      SurveyDataVisibility.CONTRIBUTOR_AND_ORGANIZERS
    );
  });

  it('updates to ALL_SURVEY_PARTICIPANTS and calls service when toggle is turned on', () => {
    fixture.componentRef.setInput('survey', mockSurveyBase);
    fixture.detectChanges();

    component.onDataVisibilityChange({ checked: true } as MatSlideToggleChange);

    expect(component.selectedDataVisibility).toBe(
      SurveyDataVisibility.ALL_SURVEY_PARTICIPANTS
    );
    expect(draftSurveyService.updateDataVisibility).toHaveBeenCalledWith(
      SurveyDataVisibility.ALL_SURVEY_PARTICIPANTS
    );
  });

  it('updates to CONTRIBUTOR_AND_ORGANIZERS and calls service when toggle is turned off', () => {
    const survey = mockSurveyBase.copyWith({
      dataVisibility: SurveyDataVisibility.ALL_SURVEY_PARTICIPANTS,
    });
    fixture.componentRef.setInput('survey', survey);
    fixture.detectChanges();

    component.onDataVisibilityChange({ checked: false } as MatSlideToggleChange);

    expect(component.selectedDataVisibility).toBe(
      SurveyDataVisibility.CONTRIBUTOR_AND_ORGANIZERS
    );
    expect(draftSurveyService.updateDataVisibility).toHaveBeenCalledWith(
      SurveyDataVisibility.CONTRIBUTOR_AND_ORGANIZERS
    );
  });
});
