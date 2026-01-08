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

import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GoogleMapsModule } from '@angular/google-maps';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { List, Map } from 'immutable';
import { of } from 'rxjs';

import { Coordinate } from 'app/models/geometry/coordinate';
import { Point } from 'app/models/geometry/point';
import { Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { Task } from 'app/models/task/task.model';
import { GroundIconModule } from 'app/modules/ground-icon.module';
import { DataStoreService } from 'app/services/data-store/data-store.service';

import { LoiSelectionComponent } from './loi-selection.component';

describe('LoiSelectionComponent', () => {
  let fixture: ComponentFixture<LoiSelectionComponent>;

  let dataStoreService: jasmine.SpyObj<DataStoreService>;
  let matDialogSpy: jasmine.SpyObj<MatDialog>;

  const poiId1 = 'poi001';
  const poiId2 = 'poi002';
  const jobId1 = 'job001';
  const jobId2 = 'job002';
  const poi1 = new LocationOfInterest(
    poiId1,
    jobId1,
    new Point(new Coordinate(1.23, 4.56)),
    Map()
  );
  const poi2 = new LocationOfInterest(
    poiId2,
    jobId2,
    new Point(new Coordinate(12.3, 45.6)),
    Map()
  );
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
      imports: [
        GoogleMapsModule,
        GroundIconModule,
        MatListModule,
        MatIconModule,
      ],
      declarations: [LoiSelectionComponent],
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
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(LoiSelectionComponent);
    fixture.componentInstance.lois = List([poi1, poi2]);
    fixture.componentInstance.survey = survey;
    // component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads map component', () => {
    expect(
      fixture.debugElement.nativeElement.querySelector('ground-map')
    ).toBeDefined();
  });

  it('shows import button when there are no LOIs', () => {
    fixture.componentRef.setInput('lois', List());
    fixture.detectChanges();

    const componentElement = fixture.debugElement.nativeElement;
    expect(componentElement.querySelectorAll('.loi-list-item').length).toBe(0);
    expect(componentElement.querySelector('.import-lois-button')).toBeDefined();
  });

  it('shows list of LOIs', () => {
    const loiList: HTMLElement =
      fixture.debugElement.nativeElement.querySelector('.loi-list');
    const loiListValues = Array.from(
      loiList.querySelectorAll('.loi-list-item')
    ).map((element: Element) => element.textContent?.trim());
    expect(loiListValues).toEqual(['Unnamed point', 'Unnamed point']);
  });

  it('shows correct icon associated with LOI', () => {
    const loiList: HTMLElement =
      fixture.debugElement.nativeElement.querySelector('.loi-list');
    const firstLoiIcon = loiList.querySelector('.loi-list-item mat-icon');
    expect(firstLoiIcon?.getAttribute('data-mat-icon-name')).toBe('point');
  });

  it('shows updated list of LOIs', () => {
    fixture.componentRef.setInput(
      'lois',
      List([{ ...poi1, properties: Map({ name: 'Test 1' }) }, poi2])
    );
    fixture.detectChanges();

    const loiList: HTMLElement =
      fixture.debugElement.nativeElement.querySelector('.loi-list');
    const loiListValues = Array.from(
      loiList.querySelectorAll('.loi-list-item')
    ).map((element: Element) => element.textContent?.trim());
    expect(loiListValues).toEqual(['Test 1', 'Unnamed point']);
  });
});
