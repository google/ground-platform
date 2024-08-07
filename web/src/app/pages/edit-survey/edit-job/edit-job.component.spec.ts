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
import {
  MatButtonToggle,
  MatButtonToggleGroup,
} from '@angular/material/button-toggle';
import {MatDialogModule} from '@angular/material/dialog';
import {By} from '@angular/platform-browser';
import {ActivatedRoute} from '@angular/router';
import {User} from 'firebase/auth';
import {Map} from 'immutable';
import {Subject, from, of} from 'rxjs';

import {TasksEditorModule} from 'app/components/tasks-editor/tasks-editor.module';
import {Job} from 'app/models/job.model';
import {Role} from 'app/models/role.model';
import {Survey} from 'app/models/survey.model';
import {EditJobComponent} from 'app/pages/edit-survey/edit-job/edit-job.component';
import {AuthService} from 'app/services/auth/auth.service';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {DialogService} from 'app/services/dialog/dialog.service';
import {DraftSurveyService} from 'app/services/draft-survey/draft-survey.service';
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
  const user$ = new Subject<User | null>();

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [EditJobComponent],
      imports: [
        MatButtonToggleGroup,
        MatButtonToggle,
        MatDialogModule,
        TasksEditorModule,
      ],
      providers: [
        {provide: AuthService, useValue: {getUser$: () => user$}},
        {provide: DataStoreService, useValue: {generateId: () => '123'}},
        {provide: DialogService, useValue: {}},
        {
          provide: SurveyService,
          useValue: {
            getActiveSurvey$: () => of(survey),
            canManageSurvey: () => true,
          },
        },
        {
          provide: DraftSurveyService,
          useValue: {
            getSurvey$: () => of(survey),
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

  it('displays the loi editor component', () => {
    const sitesButton = fixture.debugElement.query(
      By.css('.edit-job-toggler :nth-child(2) button')
    ).nativeElement as HTMLElement;
    sitesButton.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.loiEditor).toBeDefined();
  });
});
