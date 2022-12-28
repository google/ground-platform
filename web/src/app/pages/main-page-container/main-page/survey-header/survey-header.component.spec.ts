/**
 * Copyright 2020 Google LLC
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

import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { InlineEditorModule } from 'app/components/inline-editor/inline-editor.module';
import { SurveyHeaderComponent } from './survey-header.component';
import { SurveyService } from 'app/services/survey/survey.service';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule } from '@angular/material/dialog';
import { NEVER } from 'rxjs';
import { Router } from '@angular/router';

describe('SurveyHeaderComponent', () => {
  let component: SurveyHeaderComponent;
  let fixture: ComponentFixture<SurveyHeaderComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      imports: [InlineEditorModule, MatIconModule, MatDialogModule],
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
        { provide: Router, useValue: {} },
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
