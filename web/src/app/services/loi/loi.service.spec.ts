/**
 * Copyright 2020 The Ground Authors.
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
import { Map as ImmutableMap, List, Map } from 'immutable';
import { of } from 'rxjs';

import { Coordinate } from 'app/models/geometry/coordinate';
import { MultiPolygon } from 'app/models/geometry/multi-polygon';
import { Point } from 'app/models/geometry/point';
import { LocationOfInterest } from 'app/models/loi.model';
import { DataSharingType, Survey, SurveyState } from 'app/models/survey.model';
import { User } from 'app/models/user.model';
import { AuthService } from 'app/services/auth/auth.service';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { LocationOfInterestService } from 'app/services/loi/loi.service';

import { SurveyService } from 'app/services/survey/survey.service';
import { polygonShellCoordsToPolygon } from 'testing/helpers';

describe('LocationOfInterestService', () => {
  let service: LocationOfInterestService;
  let dataStoreServiceSpy: jasmine.SpyObj<DataStoreService>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;

  const user = new User('user001', 'user@test.com', true, 'User 1', 'photoUrl');
  const survey = new Survey(
    'survey001',
    'title',
    'description',
    ImmutableMap(),
    ImmutableMap(),
    'ownerId',
    { type: DataSharingType.PRIVATE }
  );
  const loi1 = new LocationOfInterest(
    'loi001',
    'job001',
    new Point(new Coordinate(0.0, 0.0)),
    ImmutableMap([['name', 'LOI 1 (Point)']]),
    'custom001'
  );
  const loi2 = new LocationOfInterest(
    'loi002',
    'job001',
    new Point(new Coordinate(10.0, 10.0)),
    ImmutableMap([['name', 'LOI 2 (Point)']]),
    'custom002'
  );

  beforeEach(() => {
    dataStoreServiceSpy = jasmine.createSpyObj<DataStoreService>(
      'DataStoreService',
      ['getAccessibleLois$']
    );
    surveyServiceSpy = jasmine.createSpyObj<SurveyService>('SurveyService', [
      'canManageSurvey',
    ]);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { getUser$: () => of(user) } },
        { provide: DataStoreService, useValue: dataStoreServiceSpy },
        { provide: SurveyService, useValue: surveyServiceSpy },
      ],
    });

    service = TestBed.inject(LocationOfInterestService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getLocationsOfInterest$', () => {
    it('should return empty list if survey is unsaved', done => {
      const unsavedSurvey = new Survey(
        '',
        'title',
        'description',
        ImmutableMap(),
        ImmutableMap(),
        'ownerId',
        { type: DataSharingType.PRIVATE },
        SurveyState.UNSAVED
      );

      service.getLocationsOfInterest$(unsavedSurvey).subscribe(lois => {
        expect(lois.size).toBe(0);
        done();
      });
    });

    it('should return accessible LOIs sorted by display name', done => {
      dataStoreServiceSpy.getAccessibleLois$.and.returnValue(
        of(List([loi2, loi1]))
      );
      surveyServiceSpy.canManageSurvey.and.returnValue(true);

      service.getLocationsOfInterest$(survey).subscribe(lois => {
        expect(lois.size).toBe(2);
        expect(lois.get(0)).toEqual(loi1);
        expect(lois.get(1)).toEqual(loi2);
        done();
      });
    });
  });

  describe('getPredefinedLoisByJobId$', () => {
    it('should filter predefined LOIs by job ID', done => {
      const predefinedLoi = new LocationOfInterest(
        'pre001',
        'job001',
        new Point(new Coordinate(0, 0)),
        ImmutableMap(),
        '',
        true
      );
      // Explicitly set predefined (though it defaults to true/undefined usually implies existing).
      // Ideally we would set checks based on implementation.
      // Loi model usually assumes 'predefined' unless explicitly ad-hoc.
      // Let's create one that is explicitly NOT predefined if possible, or another job.
      const adHocLoi = new LocationOfInterest(
        'adhoc001',
        'job001',
        new Point(new Coordinate(0, 0)),
        ImmutableMap(),
        '',
        false
      );

      const otherJobLoi = new LocationOfInterest(
        'other001',
        'job002',
        new Point(new Coordinate(0, 0)),
        ImmutableMap(),
        '',
        true
      );

      dataStoreServiceSpy.getAccessibleLois$.and.returnValue(
        of(List([predefinedLoi, adHocLoi, otherJobLoi]))
      );
      surveyServiceSpy.canManageSurvey.and.returnValue(true);

      service.getPredefinedLoisByJobId$(survey, 'job001').subscribe(lois => {
        expect(lois.size).toBe(1);
        expect(lois.first()).toEqual(predefinedLoi);
        done();
      });
    });
  });

  describe('getDisplayName', () => {
    it('should return default if name and id empty', () => {
      expect(LocationOfInterestService.getDisplayName(getMockLoi())).toBe(
        'Unnamed point'
      );
    });

    it('should return name and id if present', () => {
      // Two separate possible names exist, choose one with higher priority.
      const properties = ImmutableMap([['name', 'Foo']]);
      expect(
        LocationOfInterestService.getDisplayName(getMockLoi(properties, '123'))
      ).toBe('Foo (123)');
    });
  });

  describe('getLatLngBoundsFromLois', () => {
    const jobId1 = 'job001';
    const poi1 = new LocationOfInterest(
      'poi001',
      jobId1,
      new Point(new Coordinate(30, 30)),
      Map()
    );
    const polygon1ShellCoordinates = [
      [0, 0],
      [15, 0],
      [10, 40],
      [0, 10],
      [0, 0],
    ];
    const polygon2ShellCoordinates = [
      [-10, -10],
      [-10, 20],
      [20, 20],
      [20, -10],
      [-10, -10],
    ];
    const polygonLoi1 = new LocationOfInterest(
      'polygon_loi001',
      jobId1,
      polygonShellCoordsToPolygon(polygon1ShellCoordinates),
      Map()
    );
    const multipolygonLoi1 = new LocationOfInterest(
      'multipolygon_loi001',
      jobId1,
      new MultiPolygon(
        List([
          polygonShellCoordsToPolygon(polygon1ShellCoordinates),
          polygonShellCoordsToPolygon(polygon2ShellCoordinates),
        ])
      ),
      Map()
    );

    it('gets a single-point bounding box for a point of interest', () => {
      const boundingBox = LocationOfInterestService.getLatLngBoundsFromLois([
        poi1,
      ]);
      expect(boundingBox).toEqual(
        new google.maps.LatLngBounds(
          new google.maps.LatLng(30, 30),
          new google.maps.LatLng(30, 30)
        )
      );
    });

    it('get a bounding box for a single polygon', () => {
      const boundingBox = LocationOfInterestService.getLatLngBoundsFromLois([
        polygonLoi1,
      ]);
      expect(boundingBox).toEqual(
        new google.maps.LatLngBounds(
          new google.maps.LatLng(0, 0),
          new google.maps.LatLng(40, 15)
        )
      );
    });

    it('get a bounding box accounting for multiple polygons', () => {
      const boundingBox = LocationOfInterestService.getLatLngBoundsFromLois([
        multipolygonLoi1,
      ]);
      expect(boundingBox).toEqual(
        new google.maps.LatLngBounds(
          new google.maps.LatLng(-10, -10),
          new google.maps.LatLng(40, 20)
        )
      );
    });

    it('get a bounding box around multiple LOI types', () => {
      const boundingBox = LocationOfInterestService.getLatLngBoundsFromLois([
        poi1,
        multipolygonLoi1,
      ]);
      expect(boundingBox).toEqual(
        new google.maps.LatLngBounds(
          new google.maps.LatLng(-10, -10),
          new google.maps.LatLng(40, 30)
        )
      );
    });
  });
});

const getMockLoi = (
  properties: ImmutableMap<string, string | number> = ImmutableMap(),
  customId = ''
) => {
  return new LocationOfInterest(
    'loi001',
    'job001',
    new Point(new Coordinate(0.0, 0.0)),
    properties,
    customId
  );
};
