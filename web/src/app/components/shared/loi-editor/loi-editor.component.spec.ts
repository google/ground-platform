/**
 * Copyright 2024 The Ground Authors.
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

import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GoogleMapsModule } from '@angular/google-maps';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { List, Map } from 'immutable';
import { of } from 'rxjs';

import { ImportDialogComponent } from 'app/components/shared/import-dialog/import-dialog.component';
import { Job } from 'app/models/job.model';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { Task } from 'app/models/task/task.model';
import { DataStoreService } from 'app/services/data-store/data-store.service';

import { LoiEditorComponent } from './loi-editor.component';

describe('LoiEditorComponent', () => {
  let fixture: ComponentFixture<LoiEditorComponent>;

  let dataStoreService: jasmine.SpyObj<DataStoreService>;
  let matDialogSpy: jasmine.SpyObj<MatDialog>;

  const jobId1 = 'job001';
  const jobId2 = 'job002';
  const job1 = new Job(jobId1, /* index */ 0);
  const job2 = new Job(jobId2, /* index */ 1);
  const survey = new Survey(
    'survey1',
    'title1',
    'description1',
    /* jobs= */ Map({
      [jobId1]: job1,
      [jobId2]: job2,
    }),
    /* acl= */ Map(),
    /* ownerId= */ '',
    { type: DataSharingType.PRIVATE }
  );

  beforeEach(() => {
    dataStoreService = jasmine.createSpyObj<DataStoreService>(
      'DataStoreService',
      ['deleteLocationOfInterest', 'tasks$']
    );
    dataStoreService.tasks$.and.returnValue(of(List<Task>([])));
    matDialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    TestBed.configureTestingModule({
      imports: [GoogleMapsModule, MatSlideToggleModule, MatIconModule],
      declarations: [LoiEditorComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        {
          provide: DataStoreService,
          useValue: dataStoreService,
        },
        {
          provide: MatDialog,
          useValue: matDialogSpy,
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoiEditorComponent);
    fixture.componentInstance.lois = List([]);
    fixture.componentInstance.survey = survey;
    fixture.componentInstance.canImport = true;
    fixture.detectChanges();
  });

  describe('when the import button is clicked', () => {
    let importButton: HTMLElement;

    beforeEach(() => {
      fixture.componentInstance.lois = List([]);
      fixture.detectChanges();

      importButton = fixture.debugElement.nativeElement.querySelector(
        '.import-lois-button'
      );
    });

    describe('when job is passed in as input', () => {
      it('opens the import dialog with survey and inputted job', () => {
        fixture.componentInstance.job = job2;
        fixture.detectChanges();

        importButton.click();

        expect(matDialogSpy.open).toHaveBeenCalledWith(ImportDialogComponent, {
          data: { surveyId: survey.id, jobId: jobId2 },
          width: '350px',
          maxHeight: '800px',
        });
      });
    });
  });

  describe('the "Clear all" button', () => {
    // it('makes a deleteLocationOfInterest call per LOI when clicked', () => {
    //   fixture.componentInstance.lois = List([poi1, poi2]);
    //   fixture.detectChanges();

    //   const clearAllButton =
    //     fixture.debugElement.nativeElement.querySelector('.clear-all-lois');

    //   clearAllButton.click();

    //   expect(dataStoreService.deleteLocationOfInterest).toHaveBeenCalledTimes(
    //     fixture.componentInstance.lois.size
    //   );
    // });

    it('does not show when there are no LOIs', () => {
      fixture.componentInstance.lois = List([]);
      fixture.detectChanges();

      const clearAllButton =
        fixture.debugElement.nativeElement.querySelector('.clear-all-lois');

      expect(clearAllButton).toBe(null);
    });
  });
});
