/**
 * Copyright 2019 The Ground Authors.
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

import {Component} from '@angular/core';
import {ComponentFixture, TestBed, waitForAsync} from '@angular/core/testing';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {Router} from '@angular/router';
import {NEVER, of} from 'rxjs';

import {InlineEditorModule} from 'app/components/inline-editor/inline-editor.module';
import {AuthService} from 'app/services/auth/auth.service';
import {DataStoreService} from 'app/services/data-store/data-store.service';

import {EditStyleButtonModule} from './edit-style-button/edit-style-button.module';
import {JobDialogComponent} from './job-dialog.component';
import {TaskEditorModule} from './task-editor/task-editor.module';

@Component({selector: 'mat-dialog-content', template: ''})
class MatDialogContent {}

@Component({selector: 'mat-dialog-actions', template: ''})
class MatDialogActions {}

describe('JobDialogComponent', () => {
  let component: JobDialogComponent;
  let fixture: ComponentFixture<JobDialogComponent>;
  const dialogRef: Partial<MatDialogRef<JobDialogComponent>> = {
    keydownEvents: () => NEVER,
  };

  beforeEach(waitForAsync(() => {
    const routerSpy = createRouterSpy();
    TestBed.configureTestingModule({
      declarations: [JobDialogComponent, MatDialogContent, MatDialogActions],
      imports: [
        EditStyleButtonModule,
        FormsModule,
        TaskEditorModule,
        InlineEditorModule,
        ReactiveFormsModule,
        BrowserAnimationsModule,
        MatFormFieldModule,
        MatInputModule,
        MatDialogModule,
        MatIconModule,
        MatCheckboxModule,
      ],
      providers: [
        {provide: DataStoreService, useValue: {generateId: () => '123'}},
        {provide: MAT_DIALOG_DATA, useValue: {createJob: true}},
        {provide: MatDialogRef, useValue: dialogRef},
        {provide: Router, useValue: routerSpy},
        {
          provide: AuthService,
          useValue: {getUser$: () => NEVER},
        },
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(JobDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

function createRouterSpy() {
  return jasmine.createSpyObj('Router', ['navigate'], {events: of()});
}
