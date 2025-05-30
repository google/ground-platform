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

import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';

import {NavigationService} from 'app/services/navigation/navigation.service';
import {NotificationService} from 'app/services/notification/notification.service';

import {CopySurveyControlsComponent} from './copy-survey-controls.component';

describe('ShareButtonsComponent', () => {
  let component: CopySurveyControlsComponent;
  let fixture: ComponentFixture<CopySurveyControlsComponent>;

  let navigationService: jasmine.SpyObj<NavigationService>;
  let notificationService: jasmine.SpyObj<NotificationService>;

  beforeEach(() => {
    navigationService = jasmine.createSpyObj('NavigationService', [
      'getSurveyAppLink',
    ]);

    notificationService = jasmine.createSpyObj('NotificationService', [
      'success',
      'error',
    ]);

    TestBed.configureTestingModule({
      declarations: [CopySurveyControlsComponent],
      imports: [MatIconModule, MatButtonModule],
      providers: [
        {provide: NavigationService, useValue: navigationService},
        {provide: NotificationService, useValue: notificationService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CopySurveyControlsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should copy link to clipboard and show success notification', fakeAsync(() => {
    const surveyId = 'testSurveyId';
    const surveyAppLink = navigationService.getSurveyAppLink('testSurveyId');

    component.surveyId = surveyId;
    spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.resolve());

    component.copyLinkToClipboard();
    tick();

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(surveyAppLink);
    expect(notificationService.success).toHaveBeenCalledWith(
      'Survey link copied to clipboard'
    );
    expect(notificationService.error).not.toHaveBeenCalled();
  }));

  it('should show error notification if copying link to clipboard fails', fakeAsync(() => {
    const surveyId = 'testSurveyId';
    const surveyAppLink = navigationService.getSurveyAppLink('testSurveyId');

    component.surveyId = surveyId;
    spyOn(navigator.clipboard, 'writeText').and.returnValue(
      Promise.reject('Copy failed')
    );

    component.copyLinkToClipboard();
    tick();

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(surveyAppLink);
    expect(notificationService.success).not.toHaveBeenCalled();
    expect(notificationService.error).toHaveBeenCalledWith(
      'Impossible to copy Survey link to clipboard'
    );
  }));
});
