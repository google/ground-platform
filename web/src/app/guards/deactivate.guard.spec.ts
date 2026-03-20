/**
 * Copyright 2026 The Ground Authors.
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

import { TestBed } from '@angular/core/testing';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { dirtyCheckGuard } from './dirty-check.guard';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { of } from 'rxjs';
import { DialogType } from 'app/components/edit-survey/job-dialog/job-dialog.component';

describe('dirtyCheckGuard', () => {
  let draftSurveyServiceSpy: jasmine.SpyObj<DraftSurveyService>;
  let matDialogSpy: jasmine.SpyObj<MatDialog>;

  beforeEach(() => {
    draftSurveyServiceSpy = jasmine.createSpyObj('DraftSurveyService', [], {
      dirty: false,
    });
    matDialogSpy = jasmine.createSpyObj('MatDialog', ['open']);

    TestBed.configureTestingModule({
      providers: [
        { provide: DraftSurveyService, useValue: draftSurveyServiceSpy },
        { provide: MatDialog, useValue: matDialogSpy },
      ],
    });
  });

  function setDirty(isDirty: boolean) {
    (
      Object.getOwnPropertyDescriptor(draftSurveyServiceSpy, 'dirty')!
        .get as jasmine.Spy
    ).and.returnValue(isDirty);
  }

  it('should return true if service is not dirty', async () => {
    setDirty(false);
    const result = await TestBed.runInInjectionContext(() =>
      dirtyCheckGuard(
        {} as Record<string, unknown>,
        {} as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot,
        {} as RouterStateSnapshot
      )
    );
    expect(result).toBe(true);
    expect(matDialogSpy.open).not.toHaveBeenCalled();
  });

  it('should open dialog if service is dirty', async () => {
    setDirty(true);
    matDialogSpy.open.and.returnValue({
      afterClosed: () => of({ dialogType: DialogType.UndoJobs }),
    } as MatDialogRef<unknown, unknown>);
    const result = await TestBed.runInInjectionContext(() =>
      dirtyCheckGuard(
        {} as Record<string, unknown>,
        {} as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot,
        {} as RouterStateSnapshot
      )
    );
    expect(matDialogSpy.open).toHaveBeenCalled();
    expect(result).toBe(true);
  });

  it('should return false if dialog is cancelled', async () => {
    setDirty(true);
    matDialogSpy.open.and.returnValue({
      afterClosed: () => of(null),
    } as MatDialogRef<unknown, unknown>);
    const result = await TestBed.runInInjectionContext(() =>
      dirtyCheckGuard(
        {} as Record<string, unknown>,
        {} as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot,
        {} as RouterStateSnapshot
      )
    );
    expect(result).toBe(false);
  });
});
