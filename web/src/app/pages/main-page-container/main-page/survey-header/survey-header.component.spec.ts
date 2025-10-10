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

import {ComponentFixture, TestBed, waitForAsync} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {Router} from '@angular/router';
import {NEVER, of} from 'rxjs';

import {SurveyService} from 'app/services/survey/survey.service';

import {SurveyHeaderComponent} from './survey-header.component';

describe('SurveyHeaderComponent', () => {
  let component: SurveyHeaderComponent;
  let fixture: ComponentFixture<SurveyHeaderComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      imports: [MatIconModule, MatDialogModule],
      declarations: [SurveyHeaderComponent],
      providers: [
        {
          provide: SurveyService,
          useValue: {
            getActiveSurvey$: () => NEVER,
            getCurrentSurvey: () => {},
            canManageSurvey: () => {},
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
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SurveyHeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
