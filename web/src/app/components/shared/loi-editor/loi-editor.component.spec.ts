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
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { List, Map } from 'immutable';
import { of } from 'rxjs';

import {
  DialogType,
  JobDialogComponent,
} from 'app/components/edit-survey/job-dialog/job-dialog.component';
import { ImportDialogComponent } from 'app/components/shared/import-dialog/import-dialog.component';
import { DataCollectionStrategy, Job } from 'app/models/job.model';
import { Coordinate } from 'app/models/geometry/coordinate';
import { Point } from 'app/models/geometry/point';
import { LocationOfInterest } from 'app/models/loi.model';
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

  const loi1 = new LocationOfInterest(
    'loi1',
    jobId1,
    new Point(new Coordinate(0, 0)),
    Map()
  );
  const loi2 = new LocationOfInterest(
    'loi2',
    jobId1,
    new Point(new Coordinate(1, 1)),
    Map()
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
    fixture.componentInstance.job = job1;
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
    it('does not show when there are no LOIs', () => {
      fixture.componentInstance.lois = List([]);
      fixture.detectChanges();

      const clearAllButton =
        fixture.debugElement.nativeElement.querySelector('.clear-all-lois');

      expect(clearAllButton).toBe(null);
    });

    it('shows when there are LOIs', () => {
      fixture.componentInstance.lois = List([loi1, loi2]);
      fixture.detectChanges();

      const clearAllButton =
        fixture.debugElement.nativeElement.querySelector('.clear-all-lois');

      expect(clearAllButton).not.toBe(null);
    });

    it('hides the import button when there are LOIs', () => {
      fixture.componentInstance.lois = List([loi1]);
      fixture.detectChanges();

      const importButton =
        fixture.debugElement.nativeElement.querySelector('.import-lois-button');

      expect(importButton).toBe(null);
    });

    describe('when clicked and confirmed', () => {
      it('calls deleteLocationOfInterest once per LOI', () => {
        fixture.componentInstance.lois = List([loi1, loi2]);
        fixture.detectChanges();

        matDialogSpy.open.and.returnValue({
          afterClosed: () => of({ confirmed: true }),
        } as any);

        fixture.debugElement.nativeElement
          .querySelector('.clear-all-lois')
          .click();

        expect(
          dataStoreService.deleteLocationOfInterest
        ).toHaveBeenCalledTimes(2);
        expect(
          dataStoreService.deleteLocationOfInterest
        ).toHaveBeenCalledWith(survey.id, loi1.id);
        expect(
          dataStoreService.deleteLocationOfInterest
        ).toHaveBeenCalledWith(survey.id, loi2.id);
      });

      it('opens the DeleteLois dialog with survey and job ids', () => {
        fixture.componentInstance.lois = List([loi1]);
        fixture.detectChanges();

        matDialogSpy.open.and.returnValue({
          afterClosed: () => of({ confirmed: true }),
        } as any);

        fixture.debugElement.nativeElement
          .querySelector('.clear-all-lois')
          .click();

        expect(matDialogSpy.open).toHaveBeenCalledWith(
          JobDialogComponent,
          jasmine.objectContaining({
            data: jasmine.objectContaining({
              dialogType: DialogType.DeleteLois,
              surveyId: survey.id,
              jobId: jobId1,
            }),
          })
        );
      });
    });

    describe('when clicked and cancelled', () => {
      it('does not call deleteLocationOfInterest', () => {
        fixture.componentInstance.lois = List([loi1, loi2]);
        fixture.detectChanges();

        matDialogSpy.open.and.returnValue({
          afterClosed: () => of(undefined),
        } as any);

        fixture.debugElement.nativeElement
          .querySelector('.clear-all-lois')
          .click();

        expect(
          dataStoreService.deleteLocationOfInterest
        ).not.toHaveBeenCalled();
      });
    });
  });

  describe('toggleDataCollectorsCanAddLois', () => {
    describe('when turned on', () => {
      it('emits MIXED strategy', () => {
        const emitted: DataCollectionStrategy[] = [];
        fixture.componentInstance.updateStrategy.subscribe(
          (s: DataCollectionStrategy) => emitted.push(s)
        );

        fixture.componentInstance.toggleDataCollectorsCanAddLois({
          checked: true,
        } as MatSlideToggleChange);

        expect(emitted).toEqual([DataCollectionStrategy.MIXED]);
      });
    });

    describe('when turned off', () => {
      it('opens the DisableFreeForm dialog', () => {
        matDialogSpy.open.and.returnValue({
          afterClosed: () => of(undefined),
        } as any);

        fixture.componentInstance.toggleDataCollectorsCanAddLois({
          checked: false,
          source: { checked: false } as any,
        } as MatSlideToggleChange);

        expect(matDialogSpy.open).toHaveBeenCalledWith(
          JobDialogComponent,
          jasmine.objectContaining({
            data: jasmine.objectContaining({
              dialogType: DialogType.DisableFreeForm,
            }),
          })
        );
      });

      describe('when confirmed', () => {
        it('emits PREDEFINED strategy', () => {
          matDialogSpy.open.and.returnValue({
            afterClosed: () => of({ confirmed: true }),
          } as any);

          const emitted: DataCollectionStrategy[] = [];
          fixture.componentInstance.updateStrategy.subscribe(
            (s: DataCollectionStrategy) => emitted.push(s)
          );

          fixture.componentInstance.toggleDataCollectorsCanAddLois({
            checked: false,
            source: { checked: false } as any,
          } as MatSlideToggleChange);

          expect(emitted).toEqual([DataCollectionStrategy.PREDEFINED]);
        });
      });

      describe('when cancelled', () => {
        it('resets the toggle back to checked', () => {
          matDialogSpy.open.and.returnValue({
            afterClosed: () => of(undefined),
          } as any);

          const source = { checked: false } as any;

          fixture.componentInstance.toggleDataCollectorsCanAddLois({
            checked: false,
            source,
          } as MatSlideToggleChange);

          expect(source.checked).toBeTrue();
        });

        it('does not emit any strategy', () => {
          matDialogSpy.open.and.returnValue({
            afterClosed: () => of(undefined),
          } as any);

          const emitted: DataCollectionStrategy[] = [];
          fixture.componentInstance.updateStrategy.subscribe(
            (s: DataCollectionStrategy) => emitted.push(s)
          );

          fixture.componentInstance.toggleDataCollectorsCanAddLois({
            checked: false,
            source: { checked: false } as any,
          } as MatSlideToggleChange);

          expect(emitted).toEqual([]);
        });
      });
    });
  });

  describe('when canImport is false', () => {
    it('does not show the import card', () => {
      fixture.componentInstance.canImport = false;
      fixture.detectChanges();

      const importButton =
        fixture.debugElement.nativeElement.querySelector('.import-lois-button');

      expect(importButton).toBe(null);
    });
  });
});
