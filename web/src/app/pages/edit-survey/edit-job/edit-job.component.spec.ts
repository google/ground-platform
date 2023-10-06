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

import {ActivatedRoute} from '@angular/router';
import {ComponentFixture, TestBed, waitForAsync} from '@angular/core/testing';
import {EditJobComponent} from 'app/pages/edit-survey/edit-job/edit-job.component';
import {from, of} from 'rxjs';
import {MatDialogModule} from '@angular/material/dialog';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {DialogService} from 'app/services/dialog/dialog.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {Survey} from 'app/models/survey.model';
import {Role} from 'app/models/role.model';
import {Job} from 'app/models/job.model';
import {Map} from 'immutable';
import { NavigationService } from 'app/services/navigation/navigation.service';

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
        {provide: NavigationService, useValue: {
          getSurveyId$: () => of(survey.id),
        }},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EditJobComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
