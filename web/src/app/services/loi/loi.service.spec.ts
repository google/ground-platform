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

import {TestBed} from '@angular/core/testing';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {Subject, of} from 'rxjs';
import {Survey} from 'app/models/survey.model';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {Map as ImmutableMap} from 'immutable';
import {GenericLocationOfInterest} from 'app/models/loi.model';
import {Point} from 'app/models/geometry/point';
import {Coordinate} from 'app/models/geometry/coordinate';

describe('LocationOfInterestService', () => {
  const activeSurvey$ = new Subject<Survey | null>();
  let service: LocationOfInterestService;

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
    service = TestBed.inject(LocationOfInterestService);
  });

  it('should be created', () => {
    const service: LocationOfInterestService = TestBed.inject(
      LocationOfInterestService
    );
    expect(service).toBeTruthy();
  });

  describe('getLoiNameFromProperties', () => {
    const getMockLoi = (properties: ImmutableMap<string, string | number>) => {
      return new GenericLocationOfInterest(
        'loi001',
        'job001',
        new Point(new Coordinate(0.0, 0.0)),
        properties
      );
    };

    it('should not return an inferred loi name if empty or non applicable properties', () => {
      const properties: ImmutableMap<string, string | number> = ImmutableMap();
      expect(service.getLoiNameFromProperties(getMockLoi(properties))).toBe(
        null
      );
    });

    it('should return inferred loi name for the loi from the properties', () => {
      const properties = ImmutableMap([['id', 'loi1']]);
      expect(service.getLoiNameFromProperties(getMockLoi(properties))).toBe(
        'loi1'
      );
    });

    it('should return correct inferred loi name if multiple options exist from the properties', () => {
      // Two separate possible names exist, choose one with higher priority.
      const properties = ImmutableMap([
        ['id', 'loi1'],
        ['name', 'loi 1'],
      ]);
      expect(service.getLoiNameFromProperties(getMockLoi(properties))).toBe(
        'loi 1'
      );
    });
  });
});
