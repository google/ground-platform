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

import firebase from 'firebase/app';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { DataStoreService } from './../../services/data-store/data-store.service';
import { LocationOfInterestPanelComponent } from './loi-panel.component';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { of } from 'rxjs';
import { Map, List } from 'immutable';
import {
  LocationOfInterest,
  PointOfInterest,
} from '../../shared/models/loi.model';
import { Layer } from '../../shared/models/layer.model';
import { Observation } from '../../shared/models/observation/observation.model';
import { Survey } from '../../shared/models/survey.model';
import { StringMap } from '../../shared/models/string-map.model';
import { SurveyService } from '../../services/survey/survey.service';
import { LocationOfInterestService } from '../../services/loi/loi.service';
import { ObservationService } from '../../services/observation/observation.service';
import { Router } from '@angular/router';
import { MatDialogModule } from '@angular/material/dialog';
import { NavigationService } from '../../services/navigation/navigation.service';

const mockSurvey = new Survey(
  'survey001',
  StringMap({ en: 'title' }),
  StringMap({ en: 'description' }),
  /* layers= */ Map({
    layer001: new Layer(
      'layer001',
      /* index */ -1,
      'red',
      StringMap({ en: 'name' }),
      /* forms= */ Map()
    ),
  }),
  /* acl= */ Map()
);

const mockLocationOfInterest = new PointOfInterest(
  'loi001',
  'layer001',
  new firebase.firestore.GeoPoint(0.0, 0.0)
);

const mockObservations = List<Observation>([]);

class MockSurveyService {
  getActiveSurvey$() {
    return of<Survey>(mockSurvey);
  }
}

class MockLocationOfInterestService {
  getSelectedLocationOfInterest$() {
    return of<LocationOfInterest>(mockLocationOfInterest);
  }
}

class MockObservationService {
  observations$() {
    return of<List<Observation>>(mockObservations);
  }
}

const surveyService = new MockSurveyService();
const loiService = new MockLocationOfInterestService();
const observationService = new MockObservationService();
const navigationService = {
  getSurveyId$: () => of(''),
  getObservationId$: () => of(''),
};

describe('LocationOfInterestPanelComponent', () => {
  let component: LocationOfInterestPanelComponent;
  let fixture: ComponentFixture<LocationOfInterestPanelComponent>;

  beforeEach(
    waitForAsync(() => {
      const routerSpy = createRouterSpy();
      TestBed.configureTestingModule({
        declarations: [LocationOfInterestPanelComponent],
        imports: [MatDialogModule],
        providers: [
          { provide: DataStoreService, useValue: {} },
          {
            provide: LocationOfInterestService,
            useValue: loiService,
          },
          { provide: SurveyService, useValue: surveyService },
          { provide: ObservationService, useValue: observationService },
          { provide: Router, useValue: routerSpy },
          { provide: NavigationService, useValue: navigationService },
        ],
        schemas: [NO_ERRORS_SCHEMA],
      }).compileComponents();
    })
  );

  beforeEach(() => {
    fixture = TestBed.createComponent(LocationOfInterestPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

function createRouterSpy() {
  return jasmine.createSpyObj('Router', ['navigate']);
}
