/**
 * Copyright 2023 The Ground Authors.
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

import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {By} from '@angular/platform-browser';
import {ActivatedRoute} from '@angular/router';
import {Map} from 'immutable';
import {from, of} from 'rxjs';

import {Job} from 'app/models/job.model';
import {Role} from 'app/models/role.model';
import {Survey} from 'app/models/survey.model';
import {EditJobComponent} from 'app/pages/edit-survey/edit-job/edit-job.component';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {DialogService} from 'app/services/dialog/dialog.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';

describe('EditJobComponent', () => {
  let component: EditJobComponent;
  let fixture: ComponentFixture<EditJobComponent>;
  const survey = new Survey(
    '123',
    'title',
    'description',
    Map<string, Job>(),
    Map<string, Role>()
  );
  const jobId = 'job-123';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [EditJobComponent],
      imports: [MatDialogModule],
      providers: [
        {provide: DataStoreService, useValue: {generateId: () => '123'}},
        {provide: DialogService, useValue: {}},
        {
          provide: SurveyService,
          useValue: {
            getActiveSurvey$: () => of(survey),
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            params: from([{id: jobId}]),
          },
        },
        {
          provide: NavigationService,
          useValue: {
            getSurveyId$: () => of(survey.id),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EditJobComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('displays the loi selection component', () => {
    const loiButton = fixture.debugElement.query(
      By.css('.section-selector button:nth-child(2)')
    ).nativeElement as HTMLElement;
    console.log(loiButton);
    loiButton.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.loiSelection).toBeDefined();
  });
});
