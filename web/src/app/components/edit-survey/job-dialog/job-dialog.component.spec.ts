/**
 * Copyright 2025 The Ground Authors.
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

import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {By} from '@angular/platform-browser';

import {
  DialogData,
  DialogType,
  JobDialogComponent,
  dialogConfigs,
} from './job-dialog.component';

describe('JobDialogComponent', () => {
  let component: JobDialogComponent;
  let fixture: ComponentFixture<JobDialogComponent>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<JobDialogComponent>>;

  const mockDialogData: DialogData = {
    dialogType: DialogType.UndoJobs,
  };

  beforeEach(async () => {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      declarations: [JobDialogComponent],
      imports: [MatDialogModule],
      providers: [
        {provide: MatDialogRef, useValue: dialogRefSpy},
        {provide: MAT_DIALOG_DATA, useValue: mockDialogData},
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(JobDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should close dialog when back button is clicked', () => {
    const backButton = fixture.debugElement.query(
      By.css('.mat-mdc-dialog-actions button:first-child')
    );
    backButton.nativeElement.click();
    expect(dialogRefSpy.close).toHaveBeenCalled();
  });

  it('should close dialog when continue button is clicked', () => {
    const continueButton = fixture.debugElement.query(
      By.css('.mat-mdc-dialog-actions button:last-child')
    );
    continueButton.nativeElement.click();
    expect(dialogRefSpy.close).toHaveBeenCalled();
  });

  it('should display the correct title in the template', () => {
    const titleElement = fixture.debugElement.query(
      By.css('.mat-mdc-dialog-title')
    );
    expect(titleElement.nativeElement.textContent).toContain(
      dialogConfigs[DialogType.UndoJobs].title
    );
  });

  it('should display the correct content in the template', () => {
    const contentElement = fixture.debugElement.query(
      By.css('.mat-mdc-dialog-content')
    );
    expect(contentElement.nativeElement.textContent).toContain(
      dialogConfigs[DialogType.UndoJobs].content
    );
  });

  it('should display the back button with the correct label', () => {
    const backButton = fixture.debugElement.query(
      By.css('.mat-mdc-dialog-actions button:first-child')
    );
    expect(backButton.nativeElement.textContent).toContain(
      dialogConfigs[DialogType.UndoJobs].backButtonLabel
    );
  });

  it('should display the continue button with the correct label', () => {
    const continueButton = fixture.debugElement.query(
      By.css('.mat-mdc-dialog-actions button:last-child')
    );
    expect(continueButton.nativeElement.textContent).toContain(
      dialogConfigs[DialogType.UndoJobs].continueButtonLabel
    );
  });
});
