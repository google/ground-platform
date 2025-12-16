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
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from 'app/services/auth/auth.service';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';
import { SurveyService } from 'app/services/survey/survey.service';

import { HeaderComponent } from './header.component';

describe('HeaderComponent', () => {
  let component: HeaderComponent;
  let fixture: ComponentFixture<HeaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MatMenuModule],
      declarations: [HeaderComponent],
      providers: [
        {
          provide: DataStoreService,
          useValue: { getAccessDeniedMessage: () => '' },
        },
        { provide: MatDialog, useValue: {} },
        { provide: AuthService, useValue: { getUser$: () => of() } },
        { provide: DraftSurveyService, useValue: {} },
        { provide: Router, useValue: { events: of() } },
        { provide: SurveyService, useValue: { canManageSurvey: () => false } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
