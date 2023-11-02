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
import {MatMenuModule} from '@angular/material/menu';
import {Map} from 'immutable';
import {of} from 'rxjs';

import {Job} from 'app/models/job.model';
import {Role} from 'app/models/role.model';
import {Survey} from 'app/models/survey.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {DialogService} from 'app/services/dialog/dialog.service';
import {SurveyService} from 'app/services/survey/survey.service';

import {TaskInputComponent} from './task-input.component';

describe('TaskInputComponent', () => {
  let component: TaskInputComponent;
  let fixture: ComponentFixture<TaskInputComponent>;
  const survey = new Survey(
    '123',
    'title',
    'description',
    Map<string, Job>(),
    Map<string, Role>()
  );

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [TaskInputComponent],
      imports: [MatMenuModule],
      providers: [
        {provide: DataStoreService, useValue: {generateId: () => '123'}},
        {provide: DialogService, useValue: {}},
        {
          provide: SurveyService,
          useValue: {
            getActiveSurvey$: () => of(survey),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TaskInputComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
