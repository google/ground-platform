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

import { CUSTOM_ELEMENTS_SCHEMA, WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Map } from 'immutable';

import { DataSharingType, Survey } from 'app/models/survey.model';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SideNavMode } from 'app/services/navigation/url-params';

import { SecondarySidePanelComponent } from './secondary-side-panel.component';
import { By } from '@angular/platform-browser';

describe('SecondarySidePanelComponent', () => {
  let component: SecondarySidePanelComponent;
  let fixture: ComponentFixture<SecondarySidePanelComponent>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;

  let sideNavModeSignal: WritableSignal<SideNavMode>;
  let loiIdSignal: WritableSignal<string | null>;
  let submissionIdSignal: WritableSignal<string | null>;

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
    navigationServiceSpy = jasmine.createSpyObj('NavigationService', [
      'getSideNavMode',
      'getLoiId',
      'getSubmissionId',
    ]);

    sideNavModeSignal = signal(SideNavMode.JOB_LIST);
    loiIdSignal = signal(null);
    submissionIdSignal = signal(null);

    navigationServiceSpy.getSideNavMode.and.returnValue(sideNavModeSignal);
    navigationServiceSpy.getLoiId.and.returnValue(loiIdSignal);
    navigationServiceSpy.getSubmissionId.and.returnValue(submissionIdSignal);

    await TestBed.configureTestingModule({
      declarations: [SecondarySidePanelComponent],
      providers: [
        { provide: NavigationService, useValue: navigationServiceSpy },
      ],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SecondarySidePanelComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('activeSurvey', mockSurvey);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show ground-loi-panel when SideNavMode is JOB_LIST and loiId is present', () => {
    sideNavModeSignal.set(SideNavMode.JOB_LIST);
    loiIdSignal.set('loi1');
    submissionIdSignal.set(null);
    fixture.detectChanges();

    const loiPanel = fixture.debugElement.query(By.css('ground-loi-panel'));
    const submissionPanel = fixture.debugElement.query(
      By.css('submission-panel')
    );

    expect(loiPanel).toBeTruthy();
    expect(submissionPanel).toBeFalsy();
  });

  it('should show submission-panel when SideNavMode is SUBMISSION and submissionId is present', () => {
    sideNavModeSignal.set(SideNavMode.SUBMISSION);
    loiIdSignal.set('loi1');
    submissionIdSignal.set('submission1');
    fixture.detectChanges();

    const loiPanel = fixture.debugElement.query(By.css('ground-loi-panel'));
    const submissionPanel = fixture.debugElement.query(
      By.css('submission-panel')
    );

    expect(submissionPanel).toBeTruthy();
    expect(loiPanel).toBeFalsy();
  });

  it('should show nothing when SideNavMode is JOB_LIST but no loiId', () => {
    sideNavModeSignal.set(SideNavMode.JOB_LIST);
    loiIdSignal.set(null);
    submissionIdSignal.set(null);
    fixture.detectChanges();

    const loiPanel = fixture.debugElement.query(By.css('ground-loi-panel'));
    const submissionPanel = fixture.debugElement.query(
      By.css('submission-panel')
    );

    expect(loiPanel).toBeFalsy();
    expect(submissionPanel).toBeFalsy();
  });

  it('should not show submission-panel if submissionId is missing in SUBMISSION mode', () => {
    sideNavModeSignal.set(SideNavMode.SUBMISSION);
    loiIdSignal.set('loi1');
    submissionIdSignal.set(null);
    fixture.detectChanges();

    const submissionPanel = fixture.debugElement.query(
      By.css('submission-panel')
    );
    expect(submissionPanel).toBeFalsy();
  });
});
