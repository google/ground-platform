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

// import {ActivatedRoute} from '@angular/router';
// import {ActivatedRouteStub} from 'testing/activated-route-stub';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {GoogleMapsModule} from '@angular/google-maps';
import {Coordinate} from 'app/models/geometry/coordinate';
import {Point} from 'app/models/geometry/point';
import {
  GenericLocationOfInterest,
  LocationOfInterest,
} from 'app/models/loi.model';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {List, Map} from 'immutable';
import {BehaviorSubject, of} from 'rxjs';
import {LoiSelectionComponent} from './loi-selection.component';
import {SurveyService} from 'app/services/survey/survey.service';
import {Job} from 'app/models/job.model';
import {Survey} from 'app/models/survey.model';
import {MatLegacyDialog as MatDialog} from '@angular/material/legacy-dialog';
import {ImportDialogComponent} from 'app/components/import-dialog/import-dialog.component';
import {DataStoreService} from 'app/services/data-store/data-store.service';

describe('LoiSelectionFormComponent', () => {
  let fixture: ComponentFixture<LoiSelectionComponent>;
  let mockLois$: BehaviorSubject<List<LocationOfInterest>>;

  let dataStoreService: jasmine.SpyObj<DataStoreService>;
  let matDialogSpy: jasmine.SpyObj<MatDialog>;
  let loiServiceSpy: jasmine.SpyObj<LocationOfInterestService>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;

  const poiId1 = 'poi001';
  const poiId2 = 'poi002';
  const jobId1 = 'job001';
  const jobId2 = 'job002';
  const poi1 = new GenericLocationOfInterest(
    poiId1,
    jobId1,
    new Point(new Coordinate(1.23, 4.56)),
    Map()
  );
  const poi2 = new GenericLocationOfInterest(
    poiId2,
    jobId2,
    new Point(new Coordinate(12.3, 45.6)),
    Map()
  );
  const job = new Job(jobId1, /* index */ 0);
  const survey = new Survey(
    'survey1',
    'title1',
    'description1',
    /* jobs= */ Map({
      [jobId1]: job,
    }),
    /* acl= */ Map()
  );

  beforeEach(() => {
    dataStoreService = jasmine.createSpyObj<DataStoreService>(
      'DataStoreService',
      ['deleteLocationOfInterest']
    );
    matDialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    loiServiceSpy = jasmine.createSpyObj<LocationOfInterestService>(
      'LocationOfInterestService',
      ['getLocationsOfInterest$', 'updatePoint', 'addPoint']
    );
    surveyServiceSpy = jasmine.createSpyObj<SurveyService>('SurveyService', [
      'canManageSurvey',
      'getActiveSurvey$',
    ]);
    mockLois$ = new BehaviorSubject<List<LocationOfInterest>>(
      List<LocationOfInterest>([poi1, poi2])
    );
    loiServiceSpy.getLocationsOfInterest$.and.returnValue(mockLois$);
    surveyServiceSpy.getActiveSurvey$.and.returnValue(of(survey));
    surveyServiceSpy.canManageSurvey.and.returnValue(true);

    TestBed.configureTestingModule({
      imports: [GoogleMapsModule],
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
        {
          provide: LocationOfInterestService,
          useValue: loiServiceSpy,
        },
        {
          provide: SurveyService,
          useValue: surveyServiceSpy,
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(LoiSelectionComponent);
    fixture.detectChanges();
  });

  it('loads h1 header when standalone page', () => {
    expect(
      fixture.debugElement.nativeElement.querySelector('h1').textContent
    ).toBe('Where should data be collected?');
  });

  it('loads h2 header when not standalone page', () => {
    fixture.componentInstance.isStandalonePage = false;
    fixture.detectChanges();
    expect(
      fixture.debugElement.nativeElement.querySelector('h2').textContent
    ).toBe('Where should data be collected?');
  });

  it('loads map component', () => {
    expect(
      fixture.debugElement.nativeElement.querySelector('ground-map')
    ).toBeDefined();
  });

  it('shows list of LOIs', () => {
    const loiList: HTMLElement =
      fixture.debugElement.nativeElement.querySelector('.loi-list');
    const loiListValues = Array.from(
      loiList.querySelectorAll('.loi-list-item')
    ).map((element: Element) => element.textContent);
    expect(loiListValues).toEqual(['Unnamed point 1', 'Unnamed point 2']);
  });

  describe('when the import button is clicked', () => {
    let importButton: HTMLElement;

    beforeEach(() => {
      importButton = fixture.debugElement.nativeElement.querySelector(
        '.import-lois-button'
      );
    });

    it('opens the import dialog with survey and job data', () => {
      importButton.click();

      expect(matDialogSpy.open).toHaveBeenCalledWith(ImportDialogComponent, {
        data: {surveyId: survey.id, jobId: job.id},
        width: '350px',
        maxHeight: '800px',
      });
    });
  });

  describe('the "Clear all" button', () => {
    it('makes a deleteLocationOfInterest call per LOI when clicked', () => {
      const clearAllButton =
        fixture.debugElement.nativeElement.querySelector('.clear-all-lois');
      const loiList = fixture.debugElement.nativeElement
        .querySelector('.loi-list')
        .querySelectorAll('.loi-list-item');
      clearAllButton.click();

      expect(dataStoreService.deleteLocationOfInterest).toHaveBeenCalledTimes(
        loiList.length
      );
    });

    it('does not show when there are no LOIs', () => {
      mockLois$.next(List([]));
      fixture.detectChanges();

      const clearAllButton =
        fixture.debugElement.nativeElement.querySelector('.clear-all-lois');
      expect(clearAllButton).toBe(null);
    });
  });
});
