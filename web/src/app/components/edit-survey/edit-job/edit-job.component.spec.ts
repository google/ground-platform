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

import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  MatButtonToggle,
  MatButtonToggleGroup,
} from '@angular/material/button-toggle';
import { MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { By } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { User } from 'firebase/auth';
import { List, Map } from 'immutable';
import { Subject, from, of } from 'rxjs';

import { EditJobComponent } from 'app/components/edit-survey/edit-job/edit-job.component';
import { LoiEditorComponent } from 'app/components/shared/loi-editor/loi-editor.component';
import { TasksEditorModule } from 'app/components/shared/tasks-editor/tasks-editor.module';
import { DataCollectionStrategy, Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { Role } from 'app/models/role.model';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { AuthService } from 'app/services/auth/auth.service';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { DialogService } from 'app/services/dialog/dialog.service';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SurveyService } from 'app/services/survey/survey.service';

@Component({
  selector: 'loi-editor',
  template: '',
  providers: [
    {
      provide: LoiEditorComponent,
      useExisting: MockLoiEditorComponent,
    },
  ],
  standalone: false,
})
class MockLoiEditorComponent {
  @Input() canImport!: boolean;
  @Input() survey!: Survey;
  @Input() job!: Job;
  @Input() lois!: List<LocationOfInterest>;
}

describe('EditJobComponent', () => {
  let component: EditJobComponent;
  let fixture: ComponentFixture<EditJobComponent>;
  const survey = new Survey(
    '123',
    'title',
    'description',
    Map<string, Job>(),
    Map<string, Role>(),
    '',
    { type: DataSharingType.PRIVATE }
  );
  const jobId = 'job-123';
  const user$ = new Subject<User | null>();

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [EditJobComponent, MockLoiEditorComponent],
      imports: [
        MatButtonToggleGroup,
        MatButtonToggle,
        MatDialogModule,
        MatIconModule,
        TasksEditorModule,
      ],
      providers: [
        { provide: AuthService, useValue: { getUser$: () => user$ } },
        { provide: DataStoreService, useValue: { generateId: () => '123' } },
        { provide: DialogService, useValue: {} },
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
            getSurvey: () => survey,
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            params: from([{ id: jobId }]),
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
