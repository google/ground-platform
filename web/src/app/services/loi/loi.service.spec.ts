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

import {TestBed} from '@angular/core/testing';
import {Map as ImmutableMap, List, Map} from 'immutable';
import {Subject, of} from 'rxjs';

import {Coordinate} from 'app/models/geometry/coordinate';
import {MultiPolygon} from 'app/models/geometry/multi-polygon';
import {Point} from 'app/models/geometry/point';
import {GenericLocationOfInterest} from 'app/models/loi.model';
import {Survey} from 'app/models/survey.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {polygonShellCoordsToPolygon} from 'testing/helpers';

describe('LocationOfInterestService', () => {
  const activeSurvey$ = new Subject<Survey | null>();

  const getMockLoi = (
    properties: ImmutableMap<string, string | number> = ImmutableMap()
  ) => {
    return new GenericLocationOfInterest(
      'loi001',
      'job001',
      new Point(new Coordinate(0.0, 0.0)),
      properties
    );
  };

  beforeEach(() => {
    const navigationService = {
      getSurveyId$: () => of(''),
      getLocationOfInterestId$: () => of(''),
    };
    TestBed.configureTestingModule({
      providers: [
        {provide: DataStoreService, useValue: {}},
        {
          provide: SurveyService,
          useValue: {
            getActiveSurvey$: () => activeSurvey$,
          },
        },
        {provide: NavigationService, useValue: navigationService},
      ],
    });
  });

  it('should be created', () => {
    const service: LocationOfInterestService = TestBed.inject(
      LocationOfInterestService
    );
    expect(service).toBeTruthy();
  });

  describe('getUserDefinedName', () => {
    it('should not return an inferred loi name if empty or non applicable properties', () => {
      expect(LocationOfInterestService.getUserDefinedName(getMockLoi())).toBe(
        null
      );
    });

    it('should return inferred loi name for the loi from the properties', () => {
      const properties = ImmutableMap([['id', 'loi1']]);
      expect(
        LocationOfInterestService.getUserDefinedName(getMockLoi(properties))
      ).toBe('loi1');
    });

    it('should return correct inferred loi name if multiple options exist from the properties', () => {
      // Two separate possible names exist, choose one with higher priority.
      const properties = ImmutableMap([
        ['id', 'loi1'],
        ['name', 'loi 1'],
      ]);
      expect(
        LocationOfInterestService.getUserDefinedName(getMockLoi(properties))
      ).toBe('loi 1');
    });
  });

  describe('getLoisWithDisplayName', () => {
    const unnamedLoi1 = getMockLoi();
    const unnamedLoi2 = getMockLoi();
    const namedLoi1 = getMockLoi(ImmutableMap([['name', 'coolio']]));
    const namedLoi2 = getMockLoi(ImmutableMap([['name', 'juno']]));

    it('should use LOI order in labels when they are not named', () => {
      const mockLois = List([unnamedLoi1, unnamedLoi2]);
      const [loi1, loi2] =
        LocationOfInterestService.getLoisWithDisplayName(mockLois);
      expect(loi1).toEqual({
        ...unnamedLoi1,
        name: 'Unnamed point',
      });
      expect(loi2).toEqual({
        ...unnamedLoi1,
        name: 'Unnamed point',
      });
    });

    it('should use LOI names when LOIs are named', () => {
      const mockLois = List([namedLoi1, namedLoi2]);
      const [loi1, loi2] =
        LocationOfInterestService.getLoisWithDisplayName(mockLois);
      expect(loi1).toEqual({
        ...namedLoi1,
        name: 'coolio',
      });
      expect(loi2).toEqual({
        ...namedLoi2,
        name: 'juno',
      });
    });

    it('should use a mix of order and names when some LOIs are named', () => {
      const mockLois = List([unnamedLoi1, namedLoi1, unnamedLoi2]);
      const [loi1, loi2, loi3] =
        LocationOfInterestService.getLoisWithDisplayName(mockLois);
      expect(loi1).toEqual({
        ...unnamedLoi1,
        name: 'Unnamed point',
      });
      expect(loi2).toEqual({
        ...namedLoi1,
        name: 'coolio',
      });
      expect(loi3).toEqual({
        ...unnamedLoi2,
        name: 'Unnamed point',
      });
    });
  });

  describe('getLatLngBoundsFromLois', () => {
    const jobId1 = 'job001';
    const poi1 = new GenericLocationOfInterest(
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
    const polygonLoi1 = new GenericLocationOfInterest(
      'polygon_loi001',
      jobId1,
      polygonShellCoordsToPolygon(polygon1ShellCoordinates),
      Map()
    );
    const multipolygonLoi1 = new GenericLocationOfInterest(
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
