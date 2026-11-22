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

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { By } from '@angular/platform-browser';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import {
  DialogComponent,
  DialogData,
  DialogType,
  dialogConfigs,
} from './dialog.component';

describe('DialogComponent', () => {
  let component: DialogComponent;
  let fixture: ComponentFixture<DialogComponent>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<DialogComponent>>;

  async function setup(data: DialogData): Promise<void> {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      declarations: [DialogComponent],
      imports: [
        MatDialogModule,
        MatFormFieldModule,
        MatInputModule,
        MatButtonModule,
        FormsModule,
        NoopAnimationsModule,
      ],
      providers: [
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  const buttons = () =>
    fixture.debugElement.queryAll(By.css('.mat-mdc-dialog-actions button'));
  const backButton = () => buttons()[0];
  const continueButton = () => buttons()[buttons().length - 1];
  const titleText = () =>
    fixture.debugElement.query(By.css('.mat-mdc-dialog-title')).nativeElement
      .textContent;

  describe('confirmation dialog (UndoJobs)', () => {
    beforeEach(async () => {
      await setup({ dialogType: DialogType.UndoJobs });
    });

    it('creates', () => {
      expect(component).toBeTruthy();
    });

    it('renders the title, content, and both buttons', () => {
      expect(titleText()).toContain('Unpublished changes');
      expect(
        fixture.debugElement.query(By.css('.mat-mdc-dialog-content p'))
          .nativeElement.textContent
      ).toContain('If you leave this page');
      expect(buttons().length).toBe(2);
      expect(backButton().nativeElement.textContent).toContain('Go back');
      expect(continueButton().nativeElement.textContent).toContain('Continue');
    });

    it('does not render the job-name input', () => {
      expect(fixture.debugElement.query(By.css('#job-name'))).toBeNull();
    });

    it('closes without returning the dialog data when back is clicked', () => {
      backButton().nativeElement.click();
      expect(dialogRefSpy.close).toHaveBeenCalled();
      expect(dialogRefSpy.close).not.toHaveBeenCalledWith(component.data);
    });

    it('closes with the dialog data when the continue button is clicked', () => {
      continueButton().nativeElement.click();
      expect(dialogRefSpy.close).toHaveBeenCalledWith(component.data);
    });
  });

  describe('input dialog (AddJob)', () => {
    beforeEach(async () => {
      await setup({ dialogType: DialogType.AddJob, jobName: 'Initial' });
    });

    it('renders the job-name field and its label, but no content', () => {
      const input = fixture.debugElement.query(By.css('#job-name'));
      expect(input).toBeTruthy();
      expect(input.nativeElement.id).toBe(DialogComponent.JOB_NAME_FIELD_ID);
      expect(
        fixture.debugElement.query(By.css('mat-label')).nativeElement.textContent
      ).toContain('Job name');
      expect(
        fixture.debugElement.query(By.css('.mat-mdc-dialog-content p'))
      ).toBeNull();
    });

    it('seeds the input from data.jobName', () => {
      const input = fixture.debugElement.query(By.css('#job-name'))
        .nativeElement as HTMLInputElement;
      expect(input.value).toBe('Initial');
    });

    it('writes edits back to data.jobName (ngModel)', async () => {
      const input = fixture.debugElement.query(By.css('#job-name'))
        .nativeElement as HTMLInputElement;
      input.value = 'Trees';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      await fixture.whenStable();
      expect(component.data.jobName).toBe('Trees');
    });

    it('renders only the AddJob buttons (Cancel/Create)', () => {
      expect(buttons().length).toBe(2);
      expect(backButton().nativeElement.textContent).toContain('Cancel');
      expect(continueButton().nativeElement.textContent).toContain('Create');
    });
  });

  describe('back-only dialog (InvalidSurvey)', () => {
    beforeEach(async () => {
      await setup({ dialogType: DialogType.InvalidSurvey });
    });

    it('renders only the back button (no continue)', () => {
      expect(buttons().length).toBe(1);
      expect(backButton().nativeElement.textContent).toContain('Go back');
    });

    it('closes without returning the dialog data when back is clicked', () => {
      backButton().nativeElement.click();
      expect(dialogRefSpy.close).toHaveBeenCalled();
      expect(dialogRefSpy.close).not.toHaveBeenCalledWith(component.data);
    });
  });

  describe('every DialogType', () => {
    const allTypes = Object.values(DialogType).filter(
      (v): v is DialogType => typeof v === 'number'
    );

    allTypes.forEach(type => {
      it(`renders the configured title and buttons for ${DialogType[type]}`, async () => {
        await setup({ dialogType: type });

        const config = dialogConfigs[type];
        const expectedButtons =
          (config.backButtonLabel ? 1 : 0) +
          (config.continueButtonLabel ? 1 : 0);

        expect(titleText()).toContain(config.title);
        expect(buttons().length).toBe(expectedButtons);
      });
    });
  });
});
