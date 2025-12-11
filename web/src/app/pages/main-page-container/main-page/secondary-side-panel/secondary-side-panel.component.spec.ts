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

import {CUSTOM_ELEMENTS_SCHEMA} from '@angular/core';
import {ComponentFixture, TestBed, waitForAsync} from '@angular/core/testing';
import {Map} from 'immutable';
import {of} from 'rxjs';

import {DataSharingType, Survey} from 'app/models/survey.model';
import {
  NavigationService,
  SideNavMode,
} from 'app/services/navigation/navigation.service';

import {SecondarySidePanelComponent} from './secondary-side-panel.component';

describe('SecondarySidePanelComponent', () => {
  let component: SecondarySidePanelComponent;
  let fixture: ComponentFixture<SecondarySidePanelComponent>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;

  const mockSurvey = new Survey(
    'survey1',
    'Survey Title',
    'Description',
    Map(),
    Map(),
    'owner1',
    {type: DataSharingType.PRIVATE}
  );

  beforeEach(waitForAsync(() => {
    navigationServiceSpy = jasmine.createSpyObj('NavigationService', [
      'getSideNavMode$',
      'getLoiId',
      'getSubmissionId',
      'getSideNavMode',
    ]);

    navigationServiceSpy.getSideNavMode$.and.returnValue(
      of(SideNavMode.JOB_LIST)
    );
    // Mock signal functions
    (navigationServiceSpy.getLoiId as jasmine.Spy).and.returnValue(() => null);
    (navigationServiceSpy.getSubmissionId as jasmine.Spy).and.returnValue(
      () => null
    );
    (navigationServiceSpy.getSideNavMode as jasmine.Spy).and.returnValue(
      () => SideNavMode.JOB_LIST
    );

    TestBed.configureTestingModule({
      declarations: [SecondarySidePanelComponent],
      providers: [{provide: NavigationService, useValue: navigationServiceSpy}],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SecondarySidePanelComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('activeSurvey', mockSurvey);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
