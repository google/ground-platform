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
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { TitleDialogComponent } from './title-dialog.component';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ProjectService } from '../../services/project/project.service';
import { NavigationService } from '../../services/navigation/navigation.service';

describe('TitleDialogComponent', () => {
  let component: TitleDialogComponent;
  let fixture: ComponentFixture<TitleDialogComponent>;
  const dialogRef: Partial<MatDialogRef<TitleDialogComponent>> = {};
  const projectService = jasmine.createSpyObj('ProjectService', [
    'createProject',
  ]);
  const navigationService = jasmine.createSpyObj('NavigationService', [
    'selectProject',
  ]);

  beforeEach(
    waitForAsync(() => {
      TestBed.configureTestingModule({
        declarations: [TitleDialogComponent],
        imports: [MatDialogModule],
        providers: [
          { provide: MatDialogRef, useValue: dialogRef },
          { provide: ProjectService, useValue: projectService },
          { provide: NavigationService, useValue: navigationService },
        ],
        schemas: [NO_ERRORS_SCHEMA],
      }).compileComponents();
    })
  );

  beforeEach(() => {
    fixture = TestBed.createComponent(TitleDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
